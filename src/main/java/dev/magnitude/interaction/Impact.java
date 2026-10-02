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
        return Magnitude.settings.terrainDamage && EntityState.of(actor).terrainEnabled && actor.isAlive() && actor.getAbilities().mayBuild && !actor.isSpectator();
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
    /** Break the obstacle directly in a walking direction, preserving vanilla step-up behavior. */
    public static int kick(ServerPlayer actor, Vec3 movement) {
        if (!allowed(actor) || !actor.onGround() || movement.horizontalDistanceSqr() < 1.0e-8) return 0;
        double size = Math.clamp(dev.magnitude.core.Dimensions.size(actor), 1, 32);
        Vec3 direction = new Vec3(movement.x, 0, movement.z).normalize();
        Vec3 center = actor.position().add(direction.scale(Math.max(0.6, Math.min(2.5, size * 0.3))));
        return breakAround(actor, center.add(0, Math.min(1.2, actor.getBbHeight() * 0.45), 0), Math.min(1.5, 0.45 + size * 0.08), 1.2);
    }
    /** Two oriented boot contacts; side -1 or +1 selects one alternating step, 0 both. */
    public static int feet(ServerPlayer actor, int side, boolean pressure) {
        if (!allowed(actor) || !actor.onGround() || actor.isPassenger() || actor.getAbilities().flying || actor.isNoGravity()) return 0;
        double scale = Math.min(EntityState.of(actor).proxyLimit, dev.magnitude.core.Dimensions.size(actor));
        if (scale < (pressure ? 8 : 4)) return 0;
        double yaw = Math.toRadians(actor.getYRot());
        double halfWidth = scale * 0.09 + 0.35, halfLength = scale * 0.22 + 0.35;
        float hardness = pressure ? (float)Math.min(Float.MAX_VALUE / 2, dev.magnitude.core.Dimensions.size(actor)/Magnitude.settings.pressureHardnessFactor) : Float.MAX_VALUE;
        int changed = 0;
        for (int foot : new int[]{-1, 1}) {
            if (side != 0 && side != foot) continue;
            if ((dev.magnitude.physics.PlayerBody.support(actor) & (foot<0?1:2))==0) continue;
            Vec3 center = dev.magnitude.physics.PlayerBody.foot(actor,foot);
            BlockPos origin = BlockPos.containing(center);
            for (BlockPos offset : OFFSETS) {
                if (offset.getY() != 0) continue;
                if (changed >= Magnitude.settings.blocksPerImpact || BLOCKS.remaining() == 0 || CHECKS.remaining() == 0) return changed;
                BlockPos pos = origin.offset(offset);
                double x = pos.getX()+0.5-center.x, z = pos.getZ()+0.5-center.z;
                if (!Rules.insideFootprint(x,z,yaw,halfWidth,halfLength)) continue;
                double dx = pos.getX()+0.5-actor.getX(), dz = pos.getZ()+0.5-actor.getZ();
                if (dx*dx+dz*dz > Magnitude.settings.impactRadius*Magnitude.settings.impactRadius) continue;
                // Keep a small central support column so static load does not instantly make the player fall through its footprint.
                if (pressure && dx*dx + dz*dz < 0.75 * 0.75) continue;
                if (breakBlock(actor, pos, hardness)) changed++;
            }
        }
        return changed;
    }
    private static boolean breakBlock(ServerPlayer actor, BlockPos pos, float maximumHardness) {
        if (!CHECKS.take()) return false;
        var level = actor.level();
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !actor.mayInteract(level, pos)) return false;
        var block = level.getBlockState(pos);
        float hardness = block.getDestroySpeed(level, pos);
        if (block.isAir() || !block.getFluidState().isEmpty() || block.hasBlockEntity() || block.is(PROTECTED) || hardness < 0 || hardness > maximumHardness + 1.0e-5f) return false;
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, actor, pos, block, null) || !BLOCKS.take()) return false;
        if (!level.destroyBlock(pos, false, actor)) return false;
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
