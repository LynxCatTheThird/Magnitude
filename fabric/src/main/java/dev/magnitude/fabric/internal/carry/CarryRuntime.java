package dev.magnitude.fabric.internal.carry;

import dev.magnitude.api.relation.*;
import dev.magnitude.api.scale.ShapeFactors;
import dev.magnitude.fabric.internal.scale.ShapeState;
import dev.magnitude.fabric.api.carry.*;
import dev.magnitude.fabric.internal.carry.persistence.CarryRecoveryStore;
import dev.magnitude.fabric.internal.carry.persistence.RecoveryToken;
import dev.magnitude.fabric.internal.net.CarryNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** First playable carry stage: one hand anchor, explicit player consent, bounded safe exit. */
public final class CarryRuntime implements CarryAccess {
    private static final Logger LOG=LoggerFactory.getLogger(CarryRuntime.class);
    private static final Map<MinecraftServer,CarryRuntime> SERVERS=new ConcurrentHashMap<>();
    private static final int MAX_ACTIVE=16, MAX_PENDING=32, MAX_RECOVERY=64;
    private final MinecraftServer server;
    private final CarrySpace space;
    private final CarryRecoveryStore store;
    private RelationService ledger;
    private final Map<UUID,Long> sequences=new HashMap<>();
    private final Map<UUID,Active> active=new LinkedHashMap<>();
    private final Map<UUID,Pending> pending=new HashMap<>();
    private final Map<UUID,Recovering> recovering=new LinkedHashMap<>();
    private long viewRevision;
    private boolean stopping;
    private record Pending(UUID host,int requestedAt) {}
    private static final class Active {
        final ServerPlayer host; final LivingEntity target; final RecoveryToken token;
        final float hostScale,targetScale; final ShapeFactors hostShape,targetShape; Vec3 anchor; AABB verifiedBody; boolean installingPassenger;
        Active(ServerPlayer host,LivingEntity target,RecoveryToken token,Vec3 anchor) {
            this.host=host;this.target=target;this.token=token;this.anchor=anchor;
            hostScale=host.getScale();targetScale=target.getScale();
            hostShape=ShapeState.confirmed(host);targetShape=ShapeState.confirmed(target);verifiedBody=CarrySpace.boxAt(target,anchor);
        }
    }
    private static final class Recovering {
        final RecoveryToken token; LivingEntity entity; int startedAt; boolean placed;
        Recovering(RecoveryToken token,LivingEntity entity,int tick) {
            this.token=token;this.entity=entity;startedAt=tick;
        }
    }
    private CarryRuntime(MinecraftServer server,CarryRecoveryStore store) {
        this.server=server;this.store=store;space=new CarrySpace(server);
        resetLedger();
    }
    private void resetLedger() {
        ledger=Relations.create(UUID.randomUUID(),new RelationLimits(1,MAX_ACTIVE,512,1,512,1024));
        sequences.clear();
    }
    public static void start(MinecraftServer server) {
        if(SERVERS.containsKey(server))return;
        CarryRecoveryStore.initAttachments();
        CarryRecoveryStore store;
        try { store=CarryRecoveryStore.open(server.getWorldPath(LevelResource.ROOT),128); }
        catch(IOException e) {
            LOG.error("Capture disabled: recovery journal unavailable; restoring entity markers only",e);
            store=CarryRecoveryStore.openRecoveryOnly(server.getWorldPath(LevelResource.ROOT),128);
        }
        SERVERS.put(server,new CarryRuntime(server,store));
    }
    public static void stop(MinecraftServer server) {
        CarryRuntime runtime=SERVERS.get(server);
        if(runtime!=null) {
            runtime.stopping=true;
            for(Active entry:List.copyOf(runtime.active.values())) runtime.end(entry,"Server stopping");
            runtime.pending.clear();
        }
    }
    public static void close(MinecraftServer server) { SERVERS.remove(server); }
    public static CarryAccess forServer(MinecraftServer server) {
        CarryRuntime runtime=SERVERS.get(Objects.requireNonNull(server));
        if(runtime==null)throw new IllegalStateException("Carry recovery service is not ready");
        return runtime;
    }
    public static void tick(MinecraftServer server) {
        CarryRuntime runtime=SERVERS.get(server);if(runtime!=null)runtime.tick();
    }
    public static void onPlayerLeave(ServerPlayer player) {
        CarryRuntime runtime=SERVERS.get(player.level().getServer());
        if(runtime==null)return;
        for(Active entry:List.copyOf(runtime.active.values())) {
            if(entry.host==player || entry.target==player) runtime.end(entry,"Player disconnected");
        }
        runtime.pending.remove(player.getUUID());
        runtime.pending.entrySet().removeIf(e->e.getValue().host().equals(player.getUUID()));
    }
    public static void onEntityLoad(Entity entity,ServerLevel level) {
        CarryRuntime runtime=SERVERS.get(level.getServer());
        if(runtime!=null && entity instanceof LivingEntity living)runtime.restoreLoaded(living);
    }
    public static void onEntityUnload(Entity entity,ServerLevel level) {
        CarryRuntime runtime=SERVERS.get(level.getServer());if(runtime==null)return;
        for(Active entry:List.copyOf(runtime.active.values())) {
            if(entry.target==entity || entry.host==entity)runtime.end(entry,"Entity unloaded");
        }
        Recovering record=runtime.recovering.get(entity.getUUID());
        if(record!=null)record.entity=null;
    }
    public static boolean isInvolved(Entity entity) {
        MinecraftServer owner=entity.level().getServer();
        if(owner==null)return false;
        var runtime=SERVERS.get(owner);
        return runtime!=null && (runtime.active.containsKey(entity.getUUID()) || runtime.hostEntry(entity.getUUID())!=null
                || runtime.recovering.containsKey(entity.getUUID()) || CarryRecoveryStore.token(entity).isPresent());
    }
    public static boolean isControlled(Entity entity) {
        MinecraftServer owner=entity.level().getServer();
        if(owner==null)return false;
        var runtime=SERVERS.get(owner);if(runtime==null)return false;
        Recovering recovery=runtime.recovering.get(entity.getUUID());
        return runtime.active.containsKey(entity.getUUID()) || (recovery!=null && !recovery.placed);
    }
    /** Server mixin hook. It deliberately does not recompute an unverified host-relative position. */
    public static Vec3 controlledPassengerPosition(Entity vehicle,Entity passenger) {
        MinecraftServer owner=vehicle.level().getServer();if(owner==null)return null;
        var runtime=SERVERS.get(owner);if(runtime==null)return null;
        Active entry=runtime.active.get(passenger.getUUID());
        return entry!=null && entry.host==vehicle && entry.target instanceof ServerPlayer ? entry.anchor : null;
    }
    /**
     * Narrow transport-installation grant for the canSerialize check inside startRiding.
     * It never changes Player serialization and cannot authorize arbitrary passenger calls.
     */
    public static boolean permitsPlayerTransport(Entity passenger,Entity vehicle) {
        if(!(passenger instanceof ServerPlayer target) || !(vehicle instanceof ServerPlayer host)
                || target.level()!=host.level())return false;
        MinecraftServer owner=host.level().getServer();
        if(owner==null || target.level().getServer()!=owner)return false;
        CarryRuntime runtime=SERVERS.get(owner);
        if(runtime==null || runtime.stopping)return false;
        Active entry=runtime.active.get(target.getUUID());
        return entry!=null && entry.target==target && entry.host==host && entry.installingPassenger;
    }
    /** Desired root only: callers must validate the sweep before publishing this position. */
    public static Vec3 handAnchor(LivingEntity host,LivingEntity target) {
        double radians=Math.toRadians(host.getYRot());
        double distance=host.getBbWidth()*.5+target.getBbWidth()*.5+.25*host.getScale()*ShapeState.confirmed(host).width();
        return new Vec3(host.getX()-Math.sin(radians)*distance,
                host.getY()+host.getBbHeight()*.60-target.getBbHeight()*.5,
                host.getZ()+Math.cos(radians)*distance);
    }
    private void thread() {
        if(!server.isSameThread())throw new IllegalStateException("Carry API requires the server thread");
    }
    private CarryResult result(CarryResult.Code code,String detail,boolean changed) {
        return new CarryResult(code,detail,changed);
    }
    private Active hostEntry(UUID host) {
        for(Active entry:active.values())if(entry.host.getUUID().equals(host))return entry;
        return null;
    }
    private String dimension(LivingEntity target) { return target.level().dimension().identifier().toString(); }
    private CarryResult eligibility(ServerPlayer host,LivingEntity target) {
        if(stopping||SERVERS.get(server)!=this)return result(CarryResult.Code.NOT_READY,"Carry session is stopping",false);
        if(!store.writable())return result(CarryResult.Code.IO_FAILURE,"New captures disabled until the recovery journal is repaired",false);
        if(!CarryNetworking.supports(host) || (target instanceof ServerPlayer player && !CarryNetworking.supports(player)))
            return result(CarryResult.Code.DENIED,"Both players need the matching Magnitude client",false);
        if(host==target || target.level().getServer()!=server || host.level()!=target.level()
                || !(target instanceof ServerPlayer || target.getType()==EntityTypes.PIG))
            return result(CarryResult.Code.INVALID_TARGET,"Choose another player or pig in the same dimension",false);
        if(!host.isAlive() || !target.isAlive() || host.isSpectator() || target.isSpectator()
                || host.isRemoved() || target.isRemoved() || host.getPose()==Pose.SLEEPING || target.getPose()==Pose.SLEEPING)
            return result(CarryResult.Code.INVALID_TARGET,"This entity state cannot be carried",false);
        if(target.getRemainingFireTicks()>0 || target.getTicksFrozen()>0)
            return result(CarryResult.Code.INVALID_TARGET,"Burning or freezing targets cannot be carried; existing effects are not cleared",false);
        if(isInvolved(host)||isInvolved(target)||host.isPassenger()||host.isVehicle()||target.isPassenger()||target.isVehicle())
            return result(CarryResult.Code.BUSY,"One carrier and one target only; nesting is not enabled",false);
        float hs=host.getScale(),ts=target.getScale();
        var hshape=ShapeState.confirmed(host);var tshape=ShapeState.confirmed(target);
        if(!Float.isFinite(hs)||!Float.isFinite(ts)||hs<.25||hs>4||ts<.25||ts>4||hs/ts<2
                ||hs*hshape.width()/(ts*tshape.width())<2||hs*hshape.height()/(ts*tshape.height())<2)
            return result(CarryResult.Code.SIZE_MISMATCH,"Carrier must be at least twice target scale; range 0.25 to 4",false);
        double range=Math.min(6,2*hs);
        if(host.position().distanceToSqr(target.position())>range*range)
            return result(CarryResult.Code.OUT_OF_RANGE,"Target is too far away",false);
        if(active.size()>=MAX_ACTIVE||active.size()+recovering.size()>=MAX_RECOVERY)
            return result(CarryResult.Code.BUDGET,"Carry service is at its bounded capacity",false);
        return null;
    }
    @Override public CarryResult request(ServerPlayer host,LivingEntity target) {
        thread();CarryResult failure=eligibility(host,target);if(failure!=null)return failure;
        if(target instanceof ServerPlayer player) {
            Pending previous=pending.get(target.getUUID());
            if(previous!=null && server.getTickCount()-previous.requestedAt<200) {
                return previous.host.equals(host.getUUID())
                        ?result(CarryResult.Code.REQUESTED,"Request is already waiting for consent",false)
                        :result(CarryResult.Code.BUSY,"Target already has a pending request",false);
            }
            if(previous==null && pending.size()>=MAX_PENDING)return result(CarryResult.Code.BUDGET,"Too many pending requests",false);
            pending.put(target.getUUID(),new Pending(host.getUUID(),server.getTickCount()));
            player.sendSystemMessage(Component.translatable("magnitude.carry.invitation", host.getName()));
            return result(CarryResult.Code.REQUESTED,"Carry request sent; target must accept",false);
        }
        return attach(host,target);
    }
    @Override public CarryResult accept(ServerPlayer target) {
        thread();Pending request=pending.remove(target.getUUID());
        if(request==null || server.getTickCount()-request.requestedAt>=200)return result(CarryResult.Code.NO_REQUEST,"No current carry request",false);
        ServerPlayer host=server.getPlayerList().getPlayer(request.host);
        if(host==null)return result(CarryResult.Code.INVALID_TARGET,"Carrier is no longer online",false);
        CarryResult failure=eligibility(host,target);return failure!=null?failure:attach(host,target);
    }
    @Override public CarryResult decline(ServerPlayer target) {
        thread();return pending.remove(target.getUUID())!=null?result(CarryResult.Code.DECLINED,"Carry request declined",false)
                :result(CarryResult.Code.NO_REQUEST,"No current carry request",false);
    }
    private RelationResult submit(RelationRequest request,boolean safe) {
        var grant=new RelationAuthorization(request.actor(),request.target(),request.host(),Set.of(request.operation()),
                safe?Set.of(PlacementStatus.SAFE):Set.of(PlacementStatus.NOT_APPLICABLE,PlacementStatus.PENDING));
        return ledger.submit(request,grant);
    }
    private long next(UUID actor) { return sequences.merge(actor,1L,Math::addExact); }
    private CarryResult attach(ServerPlayer host,LivingEntity target) {
        // Epoch rotation is only safe when no active/recovering relation exists.
        if(active.isEmpty()&&recovering.isEmpty()&&ledger.snapshotAll().size()>256)resetLedger();
        ServerLevel level=(ServerLevel)target.level();
        Vec3 anchor=handAnchor(host,target);
        if(space.swept(level,target,host,target.position(),anchor)!=CarrySpace.Result.CLEAR)
            return result(CarryResult.Code.BLOCKED,"Path to the hand anchor is blocked or unavailable",false);
        Vec3 safe=space.findGround(level,target,host,target.position());
        if(safe==null)return result(CarryResult.Code.BLOCKED,"No verified initial recovery position nearby",false);
        RecoveryToken token;
        try { token=store.prepare(host.getUUID(),target,dimension(target),safe); }
        catch(IOException|RuntimeException e) { LOG.error("Carry preparation failed",e);return result(CarryResult.Code.IO_FAILURE,"Recovery state could not be saved; target unchanged",false); }
        UUID actor=host.getUUID();
        var decision=submit(RelationRequest.hold(ledger.epoch(),UUID.randomUUID(),next(actor),actor,actor,target.getUUID(),ledger.snapshot(target.getUUID()).revision()),false);
        if(!decision.changed()) {
            try { store.complete(target); } catch(IOException e) {
                Recovering cleanup=new Recovering(token,target,server.getTickCount());cleanup.placed=true;
                recovering.put(target.getUUID(),cleanup);
            }
            return result(CarryResult.Code.BUSY,"Relation rejected: "+decision.reason(),false);
        }
        Active entry=new Active(host,target,token,anchor);active.put(target.getUUID(),entry);viewRevision++;
        try {
            target.setPermanentlyInvulnerable(true);
            if(target instanceof ServerPlayer) {
                boolean installed;
                entry.installingPassenger=true;
                try { installed=target.startRiding(host,true,true); }
                finally { entry.installingPassenger=false; }
                if(!installed) {
                    CarryResult cleanup=end(entry,"Native passenger transport rejected");
                    return result(CarryResult.Code.BLOCKED,"Native passenger transport rejected; "+cleanup.detail(),cleanup.changed());
                }
                // The carrier is not one of its own entity-tracking recipients. The
                // vanilla passenger packet must also reach this one local observer.
                sendPassengerStateToHost(host);
            } else {
                target.setNoGravity(true);
                if(target instanceof Mob mob)mob.setNoAi(true);
            }
            target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
            target.setPos(anchor);
            return result(CarryResult.Code.HELD,"Target held at the verified front anchor",true);
        } catch(RuntimeException e) {
            LOG.error("Carry attachment failed; entering safe recovery",e);
            CarryResult cleanup=end(entry,"Attachment failed");
            return result(CarryResult.Code.BLOCKED,"Attachment failed; "+cleanup.detail(),cleanup.changed());
        }
    }
    @Override public CarryResult release(ServerPlayer actor) {
        thread();Active entry=active.get(actor.getUUID());if(entry==null)entry=hostEntry(actor.getUUID());
        if(entry!=null)return end(entry,"Released by participant");
        return recover(actor);
    }
    @Override public CarryResult recover(ServerPlayer actor) {
        thread();Recovering entry=recovering.get(actor.getUUID());
        if(entry==null && CarryRecoveryStore.token(actor).isPresent()) {
            restoreLoaded(actor);entry=recovering.get(actor.getUUID());
        }
        if(entry==null)for(Recovering candidate:recovering.values())if(candidate.token.host().equals(actor.getUUID())) { entry=candidate;break; }
        if(entry==null)return result(CarryResult.Code.NO_RELATION,"No carried target or recovery entry",false);
        entry.startedAt=server.getTickCount();
        if(place(entry))return result(CarryResult.Code.RELEASED,"Safely released",true);
        return pendingResult(entry,"Host control removed; no safe loaded exit yet. Retry /magnitude carry recover");
    }
    @Override public CarryResult recover(CommandSourceStack source,LivingEntity target) {
        thread();
        if(source.getServer()!=server || !source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            return result(CarryResult.Code.DENIED,"Administrator permission required",false);
        if(target.level().getServer()!=server)return result(CarryResult.Code.INVALID_TARGET,"Target is not in this server",false);
        Active held=active.get(target.getUUID());
        if(held!=null)return end(held,"Released by administrator");
        if(!recovering.containsKey(target.getUUID()))restoreLoaded(target);
        Recovering recovery=recovering.get(target.getUUID());
        if(recovery==null)return CarryRecoveryStore.token(target).isPresent()
                ?result(CarryResult.Code.IO_FAILURE,"Recovery marker needs journal inspection",false)
                :result(CarryResult.Code.NO_RELATION,"Target has no recovery record",false);
        recovery.startedAt=server.getTickCount();
        if(place(recovery))return result(CarryResult.Code.RELEASED,"Safely released",true);
        return pendingResult(recovery,"Target protected; no safe loaded exit found");
    }
    private CarryResult end(Active entry,String reason) {
        if(active.remove(entry.target.getUUID())==null)return result(CarryResult.Code.NO_RELATION,"No active carry",false);
        var snapshot=ledger.snapshot(entry.target.getUUID());UUID actor=entry.target.getUUID();
        submit(RelationRequest.release(ledger.epoch(),UUID.randomUUID(),next(actor),actor,entry.host.getUUID(),actor,snapshot.revision(),PlacementStatus.PENDING),false);
        detachForRecovery(entry.target,entry.token.host());entry.target.setDeltaMovement(Vec3.ZERO);entry.target.fallDistance=0;
        Recovering recovery=new Recovering(entry.token,entry.target,server.getTickCount());recovering.put(actor,recovery);viewRevision++;
        if(!entry.target.isAlive()) {
            restoreFlags(entry.target,entry.token);recovery.placed=true;finish(recovery);return result(CarryResult.Code.RELEASED,reason,true);
        }
        entry.target.setNoGravity(true);entry.target.setPermanentlyInvulnerable(true);
        if(place(recovery))return result(CarryResult.Code.RELEASED,reason+"; safe exit completed",true);
        return pendingResult(recovery,reason+"; target protected pending a safe exit");
    }
    private CarryResult pendingResult(Recovering recovery,String noExit) {
        return recovery.placed?result(CarryResult.Code.IO_FAILURE,"Target safely placed; journal cleanup pending",true)
                :result(CarryResult.Code.RECOVERY,noExit,true);
    }
    private boolean place(Recovering recovery) {
        LivingEntity target=recovery.entity;
        if(target==null||target.isRemoved())return false;
        if(recovery.placed)return finish(recovery);
        if(!(target.level() instanceof ServerLevel level))return false;
        Vec3 destination=space.groundedAt(level,target,null,target.position())?target.position()
                :space.findGround(level,target,null,target.position());
        if(destination==null && dimension(target).equals(recovery.token.dimension()))
            destination=space.findGround(level,target,null,recovery.token.safePosition());
        if(destination==null)return false;
        detachForRecovery(target,recovery.token.host());
        if(target instanceof ServerPlayer player)player.connection.teleport(destination.x,destination.y,destination.z,player.getYRot(),player.getXRot());
        else target.setPos(destination);
        target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
        // prepare() only admits targets with zero initial fire/frozen state. After
        // actual placement in a verified safe destination, remove only the thermal
        // residue acquired during this carry before restoring its original protection.
        // Unrelated potion effects and other external state are deliberately untouched.
        target.clearFire();target.setTicksFrozen(0);
        restoreFlags(target,recovery.token);recovery.placed=true;
        return finish(recovery);
    }
    private boolean finish(Recovering recovery) {
        LivingEntity target=recovery.entity;if(target==null)return false;
        try { store.complete(target); }
        catch(IOException e) {
            LOG.warn("Safe exit completed but recovery cleanup is pending for {}",target.getUUID());
            if(recovery.placed) {
                // The persistent entity marker remains the source for retry. Do not let
                // cleanup-only records consume slots needed by genuinely stranded targets.
                recovering.remove(target.getUUID());viewRevision++;
            }
            return false;
        }
        var snapshot=ledger.snapshot(target.getUUID());
        if(snapshot.state()==RelationState.RECOVERY) {
            UUID actor=target.getUUID();
            submit(RelationRequest.completeRecovery(ledger.epoch(),UUID.randomUUID(),next(actor),actor,actor,snapshot.revision()),true);
        }
        recovering.remove(target.getUUID());viewRevision++;return true;
    }
    private void detachForRecovery(LivingEntity target,UUID authorizedHost) {
        target.removeVehicle();
        // This also covers native self-dismount: transport may already be detached
        // when the carry tick notices it, but the carrier still needs the new list.
        ServerPlayer host=server.getPlayerList().getPlayer(authorizedHost);
        if(host!=null)sendPassengerStateToHost(host);
    }
    private static void sendPassengerStateToHost(ServerPlayer host) {
        if(host.connection!=null && !host.hasDisconnected() && host.connection.isAcceptingMessages()) {
            host.connection.send(new ClientboundSetPassengersPacket(host));
        }
    }
    private static void restoreFlags(LivingEntity target,RecoveryToken token) {
        target.setNoGravity(token.originalNoGravity());target.setPermanentlyInvulnerable(token.originalInvulnerable());
        if(target instanceof Mob mob)mob.setNoAi(token.originalNoAi());
    }
    private void restoreLoaded(LivingEntity target) {
        if(active.containsKey(target.getUUID()))return;
        Optional<RecoveryToken> marker;
        try { marker=store.recoverableToken(target); }
        catch(IOException e) { LOG.error("Recovery identity mismatch; preserving marker for manual inspection",e);return; }
        if(marker.isEmpty()) {
            try { store.reconcileUnmarked(target); }catch(IOException e) { LOG.warn("Carry stale journal cleanup pending",e); }
            return;
        }
        RecoveryToken token=marker.orElseThrow();
        if(active.size()+recovering.size()>=MAX_RECOVERY&&!recovering.containsKey(target.getUUID())) {
            restoreFlags(target,token);LOG.error("Carry recovery capacity exceeded; marker retained for {}",target.getUUID());return;
        }
        detachForRecovery(target,token.host());target.setNoGravity(true);target.setPermanentlyInvulnerable(true);
        recovering.put(target.getUUID(),new Recovering(token,target,server.getTickCount()));viewRevision++;
    }
    private void tick() {
        thread();pending.entrySet().removeIf(e->server.getTickCount()-e.getValue().requestedAt>=200);
        for(Active entry:List.copyOf(active.values())) {
            LivingEntity target=entry.target;ServerPlayer host=entry.host;
            if(!target.isAlive()||!host.isAlive()||target.isRemoved()||host.isRemoved()||host.level()!=target.level()
                    ||host.getScale()!=entry.hostScale||target.getScale()!=entry.targetScale
                    ||!ShapeState.confirmed(host).equals(entry.hostShape)||!ShapeState.confirmed(target).equals(entry.targetShape)
                    ||(target instanceof ServerPlayer && target.getVehicle()!=host)) {
                end(entry,"Carry state changed");continue;
            }
            Vec3 desired=handAnchor(host,target);
            AABB currentBody=CarrySpace.boxAt(target,desired);
            // Include both last-verified and current pose dimensions. A pose change is
            // not a pure translation, so the old occupied volume must remain in the sweep.
            AABB conservativeSweep=entry.verifiedBody.minmax(currentBody);
            if(entry.anchor.distanceToSqr(desired)>64
                    || space.check((ServerLevel)host.level(),target,host,conservativeSweep,false)!=CarrySpace.Result.CLEAR) {
                end(entry,"Hand path blocked or unavailable");continue;
            }
            entry.anchor=desired;entry.verifiedBody=currentBody;target.setPos(desired);target.setDeltaMovement(Vec3.ZERO);target.fallDistance=0;
        }
        for(Recovering entry:List.copyOf(recovering.values())) {
            if(server.getTickCount()-entry.startedAt<=100)place(entry);
        }
    }
    @Override public List<CarryView> views() {
        thread();var result=new ArrayList<CarryView>();
        for(Active entry:active.values())result.add(new CarryView(entry.host.getUUID(),entry.target.getUUID(),entry.host.getId(),entry.target.getId(),dimension(entry.target),entry.anchor,CarryView.Phase.HELD,viewRevision));
        for(Recovering entry:recovering.values()) {
            var target=entry.entity;if(!entry.placed&&target!=null&&!target.isRemoved())result.add(new CarryView(entry.token.host(),target.getUUID(),-1,target.getId(),dimension(target),target.position(),CarryView.Phase.RECOVERY,viewRevision));
        }
        return List.copyOf(result);
    }
}
