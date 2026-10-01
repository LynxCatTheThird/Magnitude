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
    public static int breakAround(ServerPlayer actor, Vec3 center, double radius, double height) {
        if (!Magnitude.settings.terrainDamage || !EntityState.of(actor).terrainEnabled || !actor.getAbilities().mayBuild || actor.isSpectator()) return 0;
        var level = actor.level();
        radius = Math.min(radius, Magnitude.settings.impactRadius);
        height = Math.min(height, Magnitude.settings.impactRadius);
        int changed = 0;
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos offset : OFFSETS) {
            if (changed >= Magnitude.settings.blocksPerImpact || BLOCKS.remaining() == 0) break;
            if (!Rules.insideEllipsoid(offset.getX(), offset.getY(), offset.getZ(), radius, height)) continue;
            if (!CHECKS.take()) break;
            BlockPos pos = origin.offset(offset);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !actor.mayInteract(level, pos)) continue;
            var block = level.getBlockState(pos);
            if (block.isAir() || !block.getFluidState().isEmpty() || block.hasBlockEntity() || block.is(PROTECTED) || block.getDestroySpeed(level, pos) < 0) continue;
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, actor, pos, block, null)) continue;
            if (!BLOCKS.take()) break;
            if (level.destroyBlock(pos, false, actor)) {
                changed++;
                PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, actor, pos, block, null);
            }
        }
        return changed;
    }
    private static List<BlockPos> offsets() {
        List<BlockPos> result = new ArrayList<>();
        for (int x=-8;x<=8;x++) for (int y=-8;y<=8;y++) for (int z=-8;z<=8;z++) if (x*x+y*y+z*z<=64) result.add(new BlockPos(x,y,z));
        result.sort(Comparator.comparingInt(p -> p.getX()*p.getX()+p.getY()*p.getY()+p.getZ()*p.getZ()));
        return List.copyOf(result);
    }
}
