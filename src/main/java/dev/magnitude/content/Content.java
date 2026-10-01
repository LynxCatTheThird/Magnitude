package dev.magnitude.content;

import dev.magnitude.Magnitude;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Content {
    public static final List<Item> ITEMS = new ArrayList<>();
    public static final List<Holder<Potion>> POTIONS = new ArrayList<>();
    public static Item EXPANSION, CONTRACTION, BALANCE, RESERVOIR, TUNER, BEAM, HARNESS, REST, GLIDER, BOOTS, HELMET, VEST, TROUSERS;
    public static Holder<MobEffect> ENLARGE, REDUCE, ASCENT, DESCENT;
    private Content() {}
    public static Item register(String name, Function<Item.Properties, Item> factory) {
        var id = Magnitude.id(name);
        Item item = Registry.register(BuiltInRegistries.ITEM, id, factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
        ITEMS.add(item);
        return item;
    }
    private static Holder<MobEffect> effect(String id, int direction, boolean instant, int color) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Magnitude.id(id), new SizeEffect(direction, instant, color));
    }
    private static void potion(String id, Holder<MobEffect> effect, int duration, int amplifier) {
        POTIONS.add(Registry.registerForHolder(BuiltInRegistries.POTION, Magnitude.id(id), new Potion("magnitude." + id, new MobEffectInstance(effect, duration, amplifier))));
    }
    public static void register() {
        ENLARGE = effect("enlarge", 1, true, 0xE8B54A);
        REDUCE = effect("reduce", -1, true, 0x4AB8DC);
        ASCENT = effect("ascent", 1, false, 0xD8823F);
        DESCENT = effect("descent", -1, false, 0x39A7A1);
        for (int tier = 0; tier < 2; tier++) {
            potion("expansion_" + (tier + 1), ENLARGE, 1, tier);
            potion("contraction_" + (tier + 1), REDUCE, 1, tier);
            potion("ascent_" + (tier + 1), ASCENT, 1200, tier);
            potion("descent_" + (tier + 1), DESCENT, 1200, tier);
        }
        FoodProperties food = new FoodProperties.Builder().nutrition(3).saturationModifier(0.5f).alwaysEdible().build();
        EXPANSION = register("amber_biscuit", p -> new MealItem(p.food(food), 2));
        CONTRACTION = register("azure_biscuit", p -> new MealItem(p.food(food), 0.5));
        BALANCE = register("balance_biscuit", p -> new MealItem(p.food(food), 0));
        RESERVOIR = register("essence_reservoir", p -> new ToolItem(p.stacksTo(1), ToolItem.Kind.RESERVOIR));
        TUNER = register("tuning_wand", p -> new ToolItem(p.stacksTo(1), ToolItem.Kind.TUNER));
        BEAM = register("refraction_wand", p -> new ToolItem(p.stacksTo(1), ToolItem.Kind.BEAM));
        HARNESS = register("rescue_harness", p -> new ToolItem(p.stacksTo(1), ToolItem.Kind.HARNESS));
        REST = register("travel_blanket", p -> new ToolItem(p.stacksTo(1), ToolItem.Kind.REST));
        GLIDER = register("silk_wing", p -> new Item(p.stacksTo(1)));
        HELMET = register("woven_hood", p -> new Item(p.humanoidArmor(ArmorMaterials.LEATHER, ArmorType.HELMET)));
        VEST = register("woven_vest", p -> new Item(p.humanoidArmor(ArmorMaterials.LEATHER, ArmorType.CHESTPLATE)));
        TROUSERS = register("woven_trousers", p -> new Item(p.humanoidArmor(ArmorMaterials.LEATHER, ArmorType.LEGGINGS)));
        BOOTS = register("cushioned_boots", p -> new Item(p.humanoidArmor(ArmorMaterials.LEATHER, ArmorType.BOOTS)));
        WorldContent.register();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Magnitude.id("equipment"), new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("tab.magnitude.equipment")).icon(() -> new ItemStack(TUNER))
            .displayItems((parameters, output) -> {
                ITEMS.forEach(output::accept);
                for (var potion : POTIONS) for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
                    ItemStack stack = new ItemStack(bottle);
                    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
                    output.accept(stack);
                }
            }).build());
    }
}
