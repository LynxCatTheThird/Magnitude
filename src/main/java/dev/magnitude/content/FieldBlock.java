package dev.magnitude.content;

import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FieldBlock extends Block {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);
    private final boolean generator;
    public FieldBlock(Properties properties, boolean generator) {
        super(properties);
        this.generator = generator;
        registerDefaultState(stateDefinition.any().setValue(MODE, 0).setValue(POWER, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MODE, POWER); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(0, 0, 0, 16, 4, 16); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 10);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block changed, Orientation orientation, boolean moving) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 10);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (generator) {
            int power = 0;
            for (Entity entity : dev.magnitude.interaction.EntityQueries.nearby(level,new AABB(pos).inflate(0,1,0),null,32)) {
                if (!(entity instanceof LivingEntity) || entity.isSpectator() || Dimensions.size(entity) > 0.25) continue;
                if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.0000001) power = Math.max(power, Math.clamp((int)Math.ceil(Dimensions.size(entity) * 60), 1, 15));
            }
            if (state.getValue(POWER) != power) level.setBlock(pos, state.setValue(POWER, power), 3);
        } else if (level.hasNeighborSignal(pos)) {
            int count = 0;
            for (Entity entity : dev.magnitude.interaction.EntityQueries.nearby(level,new AABB(pos).inflate(3),null,32)) {
                if (!(entity instanceof LivingEntity) || entity.isSpectator() || ++count > 32) continue;
                // Players explicitly opt in; unowned fields never bypass consent.
                if (entity instanceof Player && !EntityState.of(entity).acceptResize) continue;
                double target = switch(state.getValue(MODE)) { case 0 -> Dimensions.target(entity) / 1.01; case 1 -> Dimensions.target(entity) * 1.01; default -> 1; };
                Dimensions.set(entity, target, 10);
            }
        }
        level.scheduleTick(pos, this, 10);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (generator) return InteractionResult.PASS;
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            if (!player.getAbilities().mayBuild || level.getServer().isUnderSpawnProtection((ServerLevel)level, pos, serverPlayer)) return InteractionResult.FAIL;
            int mode = (state.getValue(MODE) + 1) % 3;
            level.setBlock(pos, state.setValue(MODE, mode), 3);
            player.sendOverlayMessage(Component.translatable("message.magnitude.field_mode", mode));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected boolean isSignalSource(BlockState state) { return generator; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return generator ? state.getValue(POWER) : 0; }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return generator; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) { return state.getValue(POWER); }
}
