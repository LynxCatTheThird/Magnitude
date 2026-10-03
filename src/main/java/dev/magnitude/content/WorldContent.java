package dev.magnitude.content;

import dev.magnitude.Magnitude;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import java.util.function.Function;

public final class WorldContent {
    public static FlowingFluid AMBER_SOURCE, AMBER_FLOW, AZURE_SOURCE, AZURE_FLOW;
    public static Block AMBER_POOL, AZURE_POOL, AMBER_BASIN, AZURE_BASIN, FIELD, GENERATOR;
    public static Block COMPACTED_DIRT, COMPACTED_GRASS;
    public static Item AMBER_BUCKET, AZURE_BUCKET;
    private WorldContent() {}
    private static Block block(String name, Block template, Function<BlockBehaviour.Properties, Block> factory, boolean item) {
        Block block = Registry.register(BuiltInRegistries.BLOCK, Magnitude.id(name), factory.apply(BlockBehaviour.Properties.ofFullCopy(template).setId(ResourceKey.create(Registries.BLOCK, Magnitude.id(name)))));
        if (item) Content.register(name, p -> new BlockItem(block, p.useBlockDescriptionPrefix()));
        return block;
    }
    public static void register() {
        COMPACTED_DIRT=block("compacted_dirt",Blocks.DIRT,p->new CompactedSoilBlock(p.noOcclusion()),false);
        COMPACTED_GRASS=block("compacted_grass",Blocks.GRASS_BLOCK,p->new CompactedSoilBlock(p.noOcclusion()),false);
        AMBER_SOURCE = Registry.register(BuiltInRegistries.FLUID, Magnitude.id("amber_source"), new EssenceFluid(true, true));
        AMBER_FLOW = Registry.register(BuiltInRegistries.FLUID, Magnitude.id("amber_flow"), new EssenceFluid(false, true));
        AZURE_SOURCE = Registry.register(BuiltInRegistries.FLUID, Magnitude.id("azure_source"), new EssenceFluid(true, false));
        AZURE_FLOW = Registry.register(BuiltInRegistries.FLUID, Magnitude.id("azure_flow"), new EssenceFluid(false, false));
        AMBER_POOL = block("amber_pool", Blocks.WATER, p -> new ReactiveLiquidBlock(AMBER_SOURCE, p, true), false);
        AZURE_POOL = block("azure_pool", Blocks.WATER, p -> new ReactiveLiquidBlock(AZURE_SOURCE, p, false), false);
        AMBER_BUCKET = Content.register("amber_bucket", p -> new BucketItem(AMBER_SOURCE, p.stacksTo(1).craftRemainder(Items.BUCKET)));
        AZURE_BUCKET = Content.register("azure_bucket", p -> new BucketItem(AZURE_SOURCE, p.stacksTo(1).craftRemainder(Items.BUCKET)));
        AMBER_BASIN = block("amber_basin", Blocks.CAULDRON, p -> new EssenceBasinBlock(p, true), true);
        AZURE_BASIN = block("azure_basin", Blocks.CAULDRON, p -> new EssenceBasinBlock(p, false), true);
        FIELD = block("attunement_plate", Blocks.IRON_BLOCK, p -> new FieldBlock(p, false), true);
        GENERATOR = block("kinetic_pad", Blocks.IRON_BLOCK, p -> new FieldBlock(p, true), true);
    }
}
