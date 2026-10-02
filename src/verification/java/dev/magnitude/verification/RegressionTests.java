package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.content.*;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Behavioral regressions use real transformed entities in a dedicated server. */
public final class RegressionTests {
    private static ServerPlayer player(MinecraftServer server, String name, double x) {
        var p=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        p.setPos(x,220,3000.5);p.getAbilities().mayBuild=true;Dimensions.set(p,1,0);
        return p;
    }
    private static void ready(ServerPlayer player) { EntityState.of(player).nextAction=0;EntityState.of(player).nextRequest=0; }
    private static void tick(MinecraftServer server, ServerPlayer... players) {
        server.getPlayerList().getPlayers().addAll(List.of(players));
        try {Interactions.tick(server);} finally {server.getPlayerList().getPlayers().removeAll(List.of(players));}
    }
    public static void run(MinecraftServer server, Consumer<String> passed) {
        var level=server.overworld();var originalSettings=Magnitude.settings;Magnitude.settings=new Settings();
        BlockPos center=new BlockPos(3000,220,3000);level.getChunk(center);
        for(var pos:BlockPos.betweenClosed(center.offset(-4,-2,-4),center.offset(10,8,4)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        var actor=player(server,"TestActor",3000.5);var victim=player(server,"TestRecipient",3002.5);
        var pig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);
        pig.setPos(3002.5,220,3000.5);pig.setNoAi(true);level.addFreshEntity(pig);
        try {
            Content.ENLARGE.value().applyInstantaneousEffect(level,actor,actor,victim,0,1);
            require(Dimensions.target(victim)==1,"foreign instant potion respects resize consent",passed);
            require(!victim.addEffect(new MobEffectInstance(Content.ASCENT,40),actor),"foreign continuous potion rejected before installation",passed);
            victim.forceAddEffect(new MobEffectInstance(Content.ASCENT,40),actor);
            require(!victim.hasEffect(Content.ASCENT),"forced effect respects resize consent",passed);
            EntityState.of(victim).acceptResize=true;
            Content.ENLARGE.value().applyInstantaneousEffect(level,actor,actor,victim,0,1);
            require(Dimensions.target(victim)==2,"consenting instant potion applies",passed);
            Dimensions.set(victim,1,0);
            require(victim.addEffect(new MobEffectInstance(Content.ASCENT,40),actor),"consenting continuous potion installs",passed);
            Content.ASCENT.value().applyEffectTick(level,victim,0);
            require(Dimensions.target(victim)>1,"consenting continuous potion changes size",passed);
            var effectSave=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());victim.saveWithoutId(effectSave);
            var loaded=player(server,"LoadedEffect",3004.5);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),effectSave.buildResult()));
            EntityState.of(loaded).acceptResize=false;double before=Dimensions.target(loaded);
            Content.ASCENT.value().applyEffectTick(level,loaded,0);
            require(Dimensions.target(loaded)==before,"external continuous provenance survives save/load",passed);
            EntityState.of(victim).acceptResize=false;before=Dimensions.target(victim);
            require(!Content.ASCENT.value().applyEffectTick(level,victim,0)&&Dimensions.target(victim)==before,"revocation stops existing foreign continuous effect",passed);
            victim.removeEffect(Content.ASCENT);Dimensions.set(victim,1,0);
            require(victim.addEffect(new MobEffectInstance(Content.ASCENT,40),victim),"self consumed effect does not need external consent",passed);
            Content.ASCENT.value().applyEffectTick(level,victim,0);
            require(Dimensions.target(victim)>1,"self consumed continuous effect applies",passed);
            victim.removeEffect(Content.ASCENT);
            Dimensions.set(victim,1,0);
            require(victim.addEffect(new MobEffectInstance(Content.ENLARGE,1),victim),"self consumed instant effect installs",passed);
            Content.ENLARGE.value().applyEffectTick(level,victim,0);
            require(Dimensions.target(victim)==2,"self consumed instant effect uses its own effect holder",passed);
            victim.removeEffect(Content.ENLARGE);
            EntityState.of(victim).acceptResize=true;
            victim.addEffect(new MobEffectInstance(Content.ASCENT,40),actor);
            victim.addEffect(new MobEffectInstance(Content.ENLARGE,1),victim);
            EntityState.of(victim).acceptResize=false;before=Dimensions.target(victim);
            Content.ASCENT.value().applyEffectTick(level,victim,0);
            require(Dimensions.target(victim)==before,"self instant effect cannot erase foreign continuous provenance",passed);
            victim.removeEffect(Content.ASCENT);victim.removeEffect(Content.ENLARGE);
            EntityState.of(victim).acceptResize=true;
            victim.addEffect(new MobEffectInstance(Content.ASCENT,40),actor);
            victim.addEffect(new MobEffectInstance(Content.ASCENT,40,1),victim);
            EntityState.of(victim).acceptResize=false;before=Dimensions.target(victim);
            Content.ASCENT.value().applyEffectTick(level,victim,1);
            require(Dimensions.target(victim)==before,"hidden mixed effect chain retains external consent restriction",passed);
            victim.removeEffect(Content.ASCENT);

            Magnitude.settings.allowSelfChange=false;Dimensions.set(actor,1,0);Dimensions.set(pig,2,0);
            ItemStack tuner=new ItemStack(Content.TUNER);var tag=ToolItem.data(tuner);tag.putDouble("value",0.5);
            for(int mode:new int[]{3,4}) {
                ready(actor);tag.putInt("operation",mode);ToolItem.data(tuner,tag);
                require(!((ToolItem)Content.TUNER).apply(tuner,actor,pig)&&Dimensions.target(actor)==1&&Dimensions.target(pig)==2,"tuner mode "+mode+" respects self change policy atomically",passed);
            }
            require(!actor.addEffect(new MobEffectInstance(Content.ASCENT,40),actor),"self effect respects self change policy",passed);
            Content.ENLARGE.value().applyInstantaneousEffect(level,actor,actor,actor,0,1);
            require(Dimensions.target(actor)==1,"instant self effect respects self change policy",passed);
            Magnitude.settings.allowSelfChange=true;ready(actor);
            require(((ToolItem)Content.TUNER).apply(tuner,actor,pig),"permitted transfer applies",passed);
            require(Math.abs(Dimensions.target(actor)+Dimensions.target(pig)-3)<1e-6,"permitted transfer conserves both targets",passed);
            Dimensions.set(actor,1,0);var randomState=EntityState.of(actor);
            randomState.randomPeriod=20;randomState.nextRandom=3;randomState.randomLow=2;randomState.randomHigh=2;
            Magnitude.settings.allowSelfChange=false;tick(server,actor);
            require(randomState.nextRandom==3&&Dimensions.target(actor)==1,"disabled self change pauses random countdown",passed);
            Magnitude.settings.allowSelfChange=true;randomState.nextRandom=1;tick(server,actor);
            require(Dimensions.target(actor)==2&&randomState.nextRandom==20,"reenabled random change resumes normally",passed);
            randomState.randomPeriod=0;
            Dimensions.set(actor,16,0);Dimensions.set(pig,16,0);Magnitude.settings.maximum=8;
            ItemStack reservoir=new ItemStack(Content.RESERVOIR);ready(actor);
            require(!((ToolItem)Content.RESERVOIR).apply(reservoir,actor,pig)&&Dimensions.target(pig)==16&&ToolItem.data(reservoir).getDoubleOr("charge",0)==0,"tightened config cannot destroy reservoir charge through clamping",passed);
            for(int mode:new int[]{3,4}) {
                ready(actor);tag.putInt("operation",mode);ToolItem.data(tuner,tag);
                require(!((ToolItem)Content.TUNER).apply(tuner,actor,pig)&&Dimensions.target(actor)==16&&Dimensions.target(pig)==16,"tightened config rejects out of range conserved mode "+mode,passed);
            }
            Magnitude.settings.maximum=32;

            var state=EntityState.of(pig);state.nextAction=level.getGameTime()+17;state.randomPeriod=100;state.nextRandom=31;
            var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());pig.saveWithoutId(saved);
            var restored=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),saved.buildResult()));
            require(EntityState.of(restored).nextAction-level.getGameTime()==17,"save/load preserves remaining cooldown",passed);
            require(EntityState.of(restored).nextRandom==31,"save/load preserves random countdown",passed);
            require(!EntityState.of(restored).carrying,"temporary carry relationships are not restored from disk",passed);

            Dimensions.set(actor,4,0);Dimensions.set(victim,1,0);EntityState.of(victim).acceptCarry=true;ready(actor);
            require(Interactions.carry(actor,victim),"player carries consenting smaller player",passed);
            actor.positionRider(victim);
            require(victim.getVehicle()==actor&&victim.getY()>actor.getY()+4,"carried player uses scaled riding position",passed);
            EntityState.of(victim).acceptCarry=false;tick(server,actor);
            require(!victim.isPassenger(),"revoking carrying consent releases passenger",passed);
            victim.setPos(3002.5,220,3000.5);EntityState.of(victim).acceptCarry=true;ready(actor);
            require(Interactions.carry(actor,victim),"carry can be reestablished",passed);
            Interactions.release(actor,true);
            require(!victim.isPassenger()&&victim.getDeltaMovement().lengthSqr()>0,"throw detaches and applies bounded velocity",passed);
            Dimensions.set(pig,1,0);pig.setPos(3002.5,220,3000.5);ready(actor);
            require(Interactions.carry(actor,pig),"player carries smaller mob",passed);
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(actor.connection,server);
            require(!pig.isPassenger()&&!EntityState.of(actor).carrying,"disconnect releases carried mob",passed);
            pig.setPos(3002.5,220,3000.5);ready(actor);require(Interactions.carry(actor,pig),"carry before unload",passed);
            ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(actor,level);
            require(!pig.isPassenger(),"entity unload releases carried mob",passed);
            require(!pig.startRiding(actor,true,true),"raw vanilla mount cannot bypass player vehicle guard",passed);
            pig.setPos(3002.5,220,3000.5);ready(actor);require(Interactions.carry(actor,pig),"carry before reset",passed);
            Dimensions.reset(actor);require(!pig.isPassenger(),"reset safely releases carry relationship",passed);

            Dimensions.set(actor,1,0);Dimensions.set(victim,4,0);victim.setPos(3002.5,220,3000.5);EntityState.of(victim).acceptCarry=true;ready(actor);
            require(Interactions.ride(actor,victim),"small player rides consenting larger player",passed);
            tick(server,victim);
            require(actor.getVehicle()==victim,"voluntary rider does not need passenger carry opt in",passed);
            EntityState.of(victim).acceptCarry=false;tick(server,victim);
            require(!actor.isPassenger(),"revoking player vehicle consent dismounts rider",passed);
            Dimensions.set(pig,4,0);pig.setPos(3002.5,220,3000.5);actor.setPos(3000.5,220,3000.5);ready(actor);
            require(Interactions.ride(actor,pig),"small player rides larger mob",passed);actor.stopRiding();

            var old=EntityState.of(actor);old.offsetForward=1.7;old.offsetSide=-1.2;old.offsetUp=0.9;old.carryPosition=2;
            old.terrainEnabled=true;old.randomPeriod=100;old.randomLow=0.2;old.randomHigh=3;old.nextRandom=41;
            Dimensions.set(actor,1,0);Dimensions.set(actor,3,20);virtuoel.pehkui.api.ScaleTypes.BASE.getScaleData(actor).tick();
            var replacement=player(server,"AliveReplacement",3004.5);
            ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(actor,replacement,true);
            var copied=EntityState.of(replacement);
            require(copied.offsetForward==1.7&&copied.offsetSide==-1.2&&copied.offsetUp==0.9,"alive respawn preserves custom carry offsets",passed);
            require(copied.randomPeriod==100&&copied.nextRandom==41&&copied.terrainEnabled,"alive respawn preserves random and terrain preferences",passed);
            require(Dimensions.target(replacement)==3&&Math.abs(Dimensions.size(replacement)-Dimensions.size(actor))<1e-6,"alive respawn preserves transition progress and target",passed);
            replacement=player(server,"DeadReplacement",3004.5);
            ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(actor,replacement,false);
            require(Dimensions.size(replacement)==1&&EntityState.of(replacement).randomPeriod==0,"death clears size and random rule by default",passed);
            require(EntityState.of(replacement).offsetForward==1.7&&EntityState.of(replacement).terrainEnabled,"death preserves user preferences",passed);
            Magnitude.settings.keepSizeAfterDeath=true;replacement=player(server,"KeptReplacement",3004.5);
            ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(actor,replacement,false);
            require(Dimensions.target(replacement)==3,"configured death preservation keeps transition target",passed);

            ready(actor);require(Interactions.request(actor),"first action request admitted",passed);
            int candidateBudget=EntityQueries.remaining();
            boolean floodDenied=true;for(int i=0;i<1000;i++)floodDenied&=!Interactions.request(actor);
            require(floodDenied,"1000 repeated requests are rejected in the same tick",passed);
            for(int i=0;i<1000;i++)Interactions.action(actor,5);
            require(EntityQueries.remaining()==candidateBudget,"rejected action flood cannot consume query candidates",passed);
            ready(actor);EntityState.of(actor).nextAction=level.getGameTime()+20;
            require(!Interactions.action(actor,5)&&EntityState.of(actor).nextRequest==0,"cooldown rejects ride before query admission",passed);
            ready(actor);require(!Interactions.action(actor,999)&&EntityState.of(actor).nextRequest==0,"unknown action cannot consume or mutate state",passed);

            Dimensions.set(victim,1,0);EntityState.of(victim).acceptResize=false;EntityState.of(victim).lastFluidTick=Long.MIN_VALUE;
            EssenceFluid.contact(level,victim,true);require(Dimensions.target(victim)==1,"environment fluid respects resize consent",passed);
            EntityState.of(victim).acceptResize=true;EssenceFluid.contact(level,victim,true);
            require(Dimensions.target(victim)>1,"consenting fluid contact changes size",passed);

            BlockPos fieldPos=center.below();var field=WorldContent.FIELD.defaultBlockState().setValue(FieldBlock.MODE,1);
            level.setBlock(fieldPos,field,3);level.setBlock(fieldPos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            pig.setPos(3000.5,220,3000.5);Dimensions.set(pig,1,0);field.tick(level,fieldPos,level.getRandom());
            require(Dimensions.target(pig)>1,"powered field changes size",passed);
            level.setBlock(fieldPos.east(),Blocks.AIR.defaultBlockState(),3);Dimensions.set(pig,1,0);field.tick(level,fieldPos,level.getRandom());
            require(Dimensions.target(pig)==1,"unpowered field leaves size unchanged",passed);
            var generator=WorldContent.GENERATOR.defaultBlockState();level.setBlock(fieldPos,generator,3);
            Dimensions.set(pig,0.125,0);pig.setDeltaMovement(0.01,0,0);generator.tick(level,fieldPos,level.getRandom());
            require(level.getBlockState(fieldPos).getValue(FieldBlock.POWER)==8,"small moving mob produces redstone",passed);
            pig.setDeltaMovement(Vec3.ZERO);level.getBlockState(fieldPos).tick(level,fieldPos,level.getRandom());
            require(level.getBlockState(fieldPos).getValue(FieldBlock.POWER)==0,"stationary mob clears redstone",passed);
            level.setBlock(fieldPos,Blocks.AIR.defaultBlockState(),3);

            var deathPig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);
            deathPig.setPos(3002.5,220,3000.5);deathPig.setNoAi(true);level.addFreshEntity(deathPig);
            Dimensions.set(actor,4,0);actor.setPos(3000.5,220,3000.5);ready(actor);
            require(Interactions.carry(actor,deathPig),"carry before passenger death",passed);
            deathPig.hurtServer(level,deathPig.damageSources().genericKill(),10000);
            require(!deathPig.isPassenger()&&!EntityState.of(actor).carrying,"actual passenger death releases carry relationship",passed);
            deathPig.discard();

            var transfer=player(server,"TransferCarrier",3000.5);Dimensions.set(transfer,4,0);
            pig.setPos(3002.5,220,3000.5);Dimensions.set(pig,1,0);ready(transfer);
            require(Interactions.carry(transfer,pig),"carry before dimension transfer",passed);
            transfer.teleport(new TeleportTransition(server.getLevel(net.minecraft.world.level.Level.NETHER),new Vec3(0,220,0),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));
            require(!pig.isPassenger()&&!EntityState.of(transfer).carrying,"actual dimension transfer releases before changing level",passed);
            require(pig.level()==level,"released passenger remains in origin dimension",passed);

            List<net.minecraft.world.entity.Entity> crowd=new java.util.ArrayList<>();
            try {
                for(int i=0;i<80;i++) {
                    var member=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);
                    member.setPos(3004.5,220,3000.5);member.setNoAi(true);level.addFreshEntity(member);crowd.add(member);
                }
                AABB box=new AABB(3003,219,2999,3006,223,3002);EntityQueries.beginTick();
                require(EntityQueries.nearby(level,box,null,7).size()==7&&EntityQueries.remaining()==2041,"spatial query aborts at candidate limit",passed);
                for(int i=0;i<40;i++)EntityQueries.nearby(level,box,null,256);
                require(EntityQueries.remaining()==0&&EntityQueries.nearby(level,box,null,256).isEmpty(),"dense queries cannot exceed global tick budget",passed);
            } finally {crowd.forEach(net.minecraft.world.entity.Entity::discard);EntityQueries.beginTick();}
        } finally {Interactions.detach(actor);Interactions.detach(victim);pig.discard();Magnitude.settings=originalSettings;}
    }
    private static void require(boolean ok, String label, Consumer<String> passed) {
        if(!ok)throw new AssertionError(label);passed.accept(label);
    }
}
