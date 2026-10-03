package dev.magnitude.interaction;

import dev.magnitude.Magnitude;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Rules;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded, server-owned terrain changes. Never creates debris entities. */
public final class Impact {
    public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK, Magnitude.id("protected"));
    private static final Rules.Budget CHECKS = new Rules.Budget();
    private static final Rules.Budget BLOCKS = new Rules.Budget();
    private static final List<BlockPos> OFFSETS = offsets();
    private Impact() {}
    public static void beginTick() { CHECKS.reset(Magnitude.settings.checksPerTick); BLOCKS.reset(Magnitude.settings.blocksPerTick); }
    public static int remaining() { return BLOCKS.remaining(); }
    public static int checksRemaining() { return CHECKS.remaining(); }
    private static boolean allowed(ServerPlayer actor) {
        var contacts = EntityState.of(actor).contacts;
        if (!Magnitude.settings.terrainDamage) { contacts.reason = "server terrain disabled"; return false; }
        if (!EntityState.of(actor).terrainEnabled) { contacts.reason = "player terrain disabled"; return false; }
        if (!actor.isAlive() || !actor.getAbilities().mayBuild || actor.isSpectator()) { contacts.reason = "actor cannot build"; return false; }
        return true;
    }
    public static int breakAround(ServerPlayer actor, Vec3 center, double radius, double height) {
        if (!allowed(actor)) return 0;
        radius = Math.min(radius, Magnitude.settings.impactRadius);
        height = Math.min(height, Magnitude.settings.impactRadius);
        int changed = 0;
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos offset : OFFSETS) {
            if (changed >= Magnitude.settings.blocksPerImpact || BLOCKS.remaining() == 0) break;
            if (!Rules.insideEllipsoid(offset.getX(), offset.getY(), offset.getZ(), radius, height)) continue;
            BlockPos pos = origin.offset(offset);
            if (CHECKS.remaining() == 0) break;
            if (breakBlock(actor, pos, Float.MAX_VALUE)) changed++;
        }
        return changed;
    }
    /** Compatibility entry; callers use the same exact contact collection as movement. */
    public static int kick(ServerPlayer actor, Vec3 movement) {
        var contacts=dev.magnitude.physics.ObstacleContacts.capture(actor,movement);
        return obstacles(actor,contacts.blocks(),movement);
    }
    /** Continuous material strength response; bounds work without a visual-size radius. */
    public static int obstacles(ServerPlayer actor, List<BlockPos> contacts, Vec3 movement) {
        if (!allowed(actor) || (!actor.onGround() && movement.y>=0) || movement.lengthSqr()<1e-8) return 0;
        double size=dev.magnitude.core.Dimensions.snapshot(actor).base();
        float strength=(float)Math.min(128,Math.max(0,size-1)*0.75
            * (0.75+Math.min(1,Math.max(movement.horizontalDistance(),Math.max(0,-movement.y)))*0.25));
        if(strength<=0)return 0;
        int changed=0;
        for(var pos:contacts) {
            if(changed>=Magnitude.settings.blocksPerImpact || BLOCKS.remaining()==0 || CHECKS.remaining()==0)break;
            if(breakBlock(actor,pos,strength))changed++;
        }
        return changed;
    }
    /** Real oriented soles, with bounded cursors retained for later ticks. */
    public static int feet(ServerPlayer actor, int side, boolean pressure) {
        if (!allowed(actor) || !actor.onGround() || actor.isPassenger() || actor.getAbilities().flying || actor.isNoGravity()) return 0;
        var snapshot = dev.magnitude.core.Dimensions.snapshot(actor);
        if (snapshot.base() <= 1) return 0;
        var history=EntityState.of(actor).contacts;
        if(pressure && Double.isFinite(history.excavationY) && actor.getY()<history.excavationY-.1
            && history.excavationRoot!=null
            && actor.position().subtract(history.excavationRoot).horizontalDistance()<snapshot.stride()) {
            history.reason="self-created support loss; deeper pressure suppressed";return 0;
        }
        float hardness = pressure ? (float)Math.min(128, snapshot.base()/Magnitude.settings.pressureHardnessFactor)
            : (float)Math.min(128,Math.max(0,snapshot.base()-1)*0.75);
        var pending=EntityState.of(actor).contacts.footprints;
        if(pending.size()>=4) return continueFeet(actor);
        var work=new FootprintWork(actor,side,pressure,hardness);
        pending.addLast(work);
        return continueFeet(actor);
    }
    public static int landing(ServerPlayer actor,double descent,double fallHeight) {
        if(!Double.isFinite(descent) || !Double.isFinite(fallHeight) || !allowed(actor) || !actor.onGround())return 0;
        double size=dev.magnitude.core.Dimensions.snapshot(actor).base();
        double speed=Math.max(Math.max(0,descent),Math.sqrt(0.16*Math.max(0,fallHeight)));
        if(size<=1 || speed<=0.1)return 0;
        float hardness=(float)Math.min(128,Math.max(0,size-1)*0.75*Math.max(1,speed/0.42));
        int depth=(int)Math.clamp(1+Math.log1p(size*speed*speed)*0.5,1,4);
        var pending=EntityState.of(actor).contacts.footprints;
        // A landing supersedes queued surface impressions at the same actor's feet.
        pending.clear();pending.addLast(new FootprintWork(actor,0,false,hardness,depth));
        return continueFeet(actor);
    }
    public static int continueFeet(ServerPlayer actor) {
        var pending=EntityState.of(actor).contacts.footprints;
        if(pending.isEmpty())return 0;
        if(!allowed(actor) || actor.getAbilities().flying || actor.isNoGravity() || actor.isPassenger()) {
            pending.clear();return 0;
        }
        int changed=0,visited=0,deferred=0;
        while(!pending.isEmpty() && changed<Magnitude.settings.blocksPerImpact
            && BLOCKS.remaining()>0 && CHECKS.remaining()>0 && visited<2048) {
            var work=pending.peekFirst();
            if(!work.valid(actor) || work.complete()) {pending.removeFirst();continue;}
            if(work.occupied(actor)) {
                pending.removeFirst();pending.addLast(work);
                EntityState.of(actor).contacts.reason="footprint waiting for foot release";
                if(++deferred>=pending.size())break;
                continue;
            }
            deferred=0;
            var pos=work.next(); visited++;
            // Every cursor visit is charged, including empty space and retained support.
            if(!CHECKS.take())break;
            if(pos!=null && breakBlockChecked(actor,pos,work.hardness))changed++;
        }
        return changed;
    }
    /** Shared protected mutation path for non-destructive soil state changes. */
    public static boolean compactSoil(ServerPlayer actor,BlockPos pos,net.minecraft.world.level.block.state.BlockState expected,
                                       net.minecraft.world.level.block.state.BlockState replacement) {
        if(!Magnitude.settings.shallowDeformation||!allowed(actor)||!CHECKS.take())return false;
        var level=actor.level();
        if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||!actor.mayInteract(level,pos))return false;
        // Initial backend does not remove attached plants or supporting structures by neighbor updates.
        if(!level.getBlockState(pos.above()).isAir())return false;
        var current=level.getBlockState(pos);
        if(!replacement.getFluidState().isEmpty()||replacement.hasBlockEntity()||replacement.getBlock()!=dev.magnitude.terrain.SoilMaterials.compacted(current)
            ||dev.magnitude.terrain.SoilMaterials.height(replacement)>=dev.magnitude.terrain.SoilMaterials.height(current))return false;
        if(current!=expected||current.hasBlockEntity()||current.is(PROTECTED)||!current.getFluidState().isEmpty()
            ||current.getDestroySpeed(level,pos)<0)return false;
        if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,actor,pos,current,null))return false;
        // Re-read after protection callbacks; they may have changed the world.
        if(!Magnitude.settings.shallowDeformation||!allowed(actor)||!actor.mayInteract(level,pos)
            ||level.getBlockState(pos)!=expected||!level.getBlockState(pos.above()).isAir()||!BLOCKS.take())return false;
        return level.setBlock(pos,replacement,Block.UPDATE_ALL);
    }
    private static boolean breakBlock(ServerPlayer actor, BlockPos pos, float maximumHardness) {
        if (!CHECKS.take()) return false;
        return breakBlockChecked(actor,pos,maximumHardness);
    }
    private static boolean breakBlockChecked(ServerPlayer actor, BlockPos pos, float maximumHardness) {
        var level = actor.level();
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !actor.mayInteract(level, pos)) return false;
        var block = level.getBlockState(pos);
        float hardness = block.getDestroySpeed(level, pos);
        if (block.isAir() || !block.getFluidState().isEmpty() || block.hasBlockEntity() || block.is(PROTECTED) || hardness < 0 || hardness > maximumHardness + 1.0e-5f) return false;
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, actor, pos, block, null) || !BLOCKS.take()) return false;
        if (!level.destroyBlock(pos, false, actor)) return false;
        if(actor.onGround() && pos.getY()<actor.getY()) {
            var contacts=EntityState.of(actor).contacts;
            if(!contacts.selfTerrainFall || contacts.excavationRoot==null
                || actor.position().subtract(contacts.excavationRoot).horizontalDistance()>=dev.magnitude.core.Dimensions.snapshot(actor).stride()) {
                contacts.excavationY=actor.getY();contacts.excavationRoot=actor.position();
            }
            contacts.selfTerrainFall=true;
        }
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, actor, pos, block, null);
        return true;
    }
    private static List<BlockPos> offsets() {
        List<BlockPos> result = new ArrayList<>();
        for (int x=-8;x<=8;x++) for (int y=-8;y<=8;y++) for (int z=-8;z<=8;z++) if (x*x+y*y+z*z<=64) result.add(new BlockPos(x,y,z));
        result.sort(Comparator.comparingInt(p -> p.getX()*p.getX()+p.getY()*p.getY()+p.getZ()*p.getZ()));
        return List.copyOf(result);
    }
}
