package dev.magnitude.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.LiquidBlock;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;

/** Water-like finite-source liquid; contact is evaluated only on the server. */
public final class EssenceFluid extends WaterFluid {
    private final boolean source;
    private final boolean ascending;
    public EssenceFluid(boolean source, boolean ascending) {
        this.source = source;
        this.ascending = ascending;
        if (!source) registerDefaultState(defaultFluidState().setValue(LEVEL, 7));
    }
    @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        super.createFluidStateDefinition(builder);
        builder.add(LEVEL);
    }
    @Override public Fluid getFlowing() { return ascending ? WorldContent.AMBER_FLOW : WorldContent.AZURE_FLOW; }
    @Override public Fluid getSource() { return ascending ? WorldContent.AMBER_SOURCE : WorldContent.AZURE_SOURCE; }
    @Override public Item getBucket() { return ascending ? WorldContent.AMBER_BUCKET : WorldContent.AZURE_BUCKET; }
    @Override public int getAmount(FluidState state) { return source ? 8 : state.getValue(LEVEL); }
    @Override public boolean isSource(FluidState state) { return source; }
    @Override public boolean isSame(Fluid fluid) { return fluid == getSource() || fluid == getFlowing(); }
    @Override protected boolean canConvertToSource(ServerLevel level) { return false; }
    @Override public BlockState createLegacyBlock(FluidState state) {
        return (ascending ? WorldContent.AMBER_POOL : WorldContent.AZURE_POOL).defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }
    @Override public ParticleOptions getDripParticle() { return ParticleTypes.DRIPPING_WATER; }
    @Override protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        super.entityInside(level, pos, entity, effects);
        contact(level, entity, ascending);
    }
    public static void contact(Level level, Entity entity, boolean ascending) {
        if (!(level instanceof ServerLevel) || !(entity instanceof net.minecraft.world.entity.LivingEntity) || entity.isSpectator()) return;
        EntityState state = EntityState.of(entity);
        if (entity instanceof net.minecraft.world.entity.player.Player && !state.acceptResize) return;
        long time = level.getGameTime();
        if (time % 10 != 0 || state.lastFluidTick == time) return;
        state.lastFluidTick = time;
        Dimensions.set(entity, Dimensions.target(entity) * (ascending ? 1.01 : 1 / 1.01), 10);
    }
}
