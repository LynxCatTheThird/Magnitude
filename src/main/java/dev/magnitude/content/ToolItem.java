package dev.magnitude.content;

import dev.magnitude.Magnitude;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Rules;
import dev.magnitude.interaction.Interactions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.function.Consumer;

public final class ToolItem extends Item {
    public enum Kind { RESERVOIR, TUNER, BEAM, HARNESS, REST }
    private final Kind kind;
    public ToolItem(Properties properties, Kind kind) { super(properties); this.kind = kind; }
    public static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static void data(ItemStack stack, CompoundTag data) { stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); }

    @Override public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer player) || player.isSpectator()) return InteractionResult.FAIL;
        ItemStack stack = user.getItemInHand(hand);
        if (kind == Kind.HARNESS) return Interactions.action(player,user.isShiftKeyDown()?3:2)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        if (kind == Kind.REST) {
            if (Dimensions.size(player) > 0.25 || !player.onGround()) return InteractionResult.FAIL;
            var bed = player.blockPosition();
            // Use an actual bed: vanilla owns sleeping, spawn and dimension rules.
            if (!level.getBlockState(bed).is(net.minecraft.tags.BlockTags.BEDS)) bed = bed.below();
            if (!(level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock bedBlock)) return InteractionResult.FAIL;
            player.startSleepInBed(bedBlock, level.getBlockState(bed), bedBlock.getBedRule(level, bed), bed).ifLeft(problem -> player.sendOverlayMessage(Component.translatable("message.magnitude.rest_failed")));
            return InteractionResult.SUCCESS;
        }
        if (kind == Kind.TUNER && user.isShiftKeyDown()) {
            CompoundTag tag = data(stack);
            var mode=ToolMode.fromStored(tag.getIntOr("operation",0)).next();
            tag.putInt("operation",mode.storedId);
            data(stack, tag);
            player.sendOverlayMessage(Component.translatable("message.magnitude.tool_mode",mode.label()));
            return InteractionResult.SUCCESS;
        }
        LivingEntity target = player;
        if (kind == Kind.BEAM) {
            if (!Interactions.request(player)) return InteractionResult.FAIL;
            target = Interactions.aim(player, 32);
        }
        if (kind == Kind.TUNER && !data(stack).getStringOr("binding", "").isEmpty()) {
            try {
                var entity = player.level().getEntity(UUID.fromString(data(stack).getStringOr("binding", "")));
                target = entity instanceof LivingEntity living ? living : null;
            } catch (IllegalArgumentException ignored) { target = null; }
        }
        if (target == null) { player.sendOverlayMessage(Component.translatable("message.magnitude.target_missing")); return InteractionResult.FAIL; }
        return apply(stack, player, target) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        if (user.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer actor) || user.isSpectator()) return InteractionResult.FAIL;
        if (kind == Kind.TUNER) {
            if (!Interactions.request(actor)) return InteractionResult.FAIL;
            if (!Dimensions.canChange(actor, target)) return InteractionResult.FAIL;
            CompoundTag tag = data(stack);
            tag.putString("binding", target.getUUID().toString());
            data(stack, tag);
            actor.sendOverlayMessage(Component.translatable("message.magnitude.bound", target.getName()));
            return InteractionResult.SUCCESS;
        }
        if (kind == Kind.HARNESS) return Interactions.request(actor)&&Interactions.carry(actor,target)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        return apply(stack, actor, target) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
    public boolean apply(ItemStack stack, ServerPlayer actor, LivingEntity target) {
        if (EntityState.of(actor).nextAction>actor.level().getGameTime()) return false;
        if (!Dimensions.canChange(actor, target) || !Dimensions.settled(target) || !Interactions.cooldown(actor, 10)) return false;
        CompoundTag tag = data(stack);
        double current = Dimensions.size(target);
        double parameter = tag.getDoubleOr("value", 0.5);
        if (!Double.isFinite(parameter)) return false;
        if (kind == Kind.RESERVOIR) {
            if (!Dimensions.withinLimits(target)) return false;
            double stored = tag.getDoubleOr("charge", 0);
            if (!Double.isFinite(stored) || stored < 0 || stored > Magnitude.settings.maximum) return false;
            if (stored == 0) {
                double amount = Rules.transfer(current, Magnitude.settings.maximum, 0.5, Magnitude.settings.minimum);
                double next = Dimensions.representable(target,current-amount);
                amount = current-next;
                if (amount <= 0) return false;
                Dimensions.set(target, next, 20);
                tag.putDouble("charge", amount);
            } else {
                double amount = Math.min(stored, Math.max(0, Magnitude.settings.maximum - current));
                double next = Dimensions.representable(target,current+amount);
                amount = next-current;
                if (amount <= 0 || amount > stored) return false;
                Dimensions.set(target, next, 20);
                tag.putDouble("charge", stored - amount);
            }
            data(stack, tag);
            return true;
        }
        int operation = Math.clamp(tag.getIntOr("operation", 0), 0, 4);
        if (kind == Kind.BEAM) return Dimensions.set(target, current * (actor.isShiftKeyDown() ? 0.5 : 2), 20);
        if (kind != Kind.TUNER) return false;
        if (operation == 3 || operation == 4) {
            if (target == actor || !Dimensions.settled(actor) || !Dimensions.canChange(actor, actor) || !Dimensions.withinLimits(actor) || !Dimensions.withinLimits(target)) return false;
            double own = Dimensions.size(actor);
            if (operation == 3) {
                if (Dimensions.representable(actor,current)!=current || Dimensions.representable(target,own)!=own) return false;
                Dimensions.set(actor, current, 20); Dimensions.set(target, own, 20);
            }
            else {
                double amount = Rules.transfer(current, Magnitude.settings.maximum - own, parameter, Magnitude.settings.minimum);
                double nextOwn = Dimensions.representable(actor,own+amount);
                amount = nextOwn-own;
                if (amount <= 0 || amount > Rules.transfer(current,Magnitude.settings.maximum-own,parameter,Magnitude.settings.minimum)) return false;
                double nextTarget = Dimensions.representable(target,current-amount);
                if (current-nextTarget != amount) return false;
                Dimensions.set(target, nextTarget, 20);
                Dimensions.set(actor, nextOwn, 20);
            }
            return true;
        }
        return Dimensions.set(target, switch (operation) { case 0 -> current * Math.max(0, parameter); case 1 -> current + parameter; default -> parameter; }, Math.clamp(tag.getIntOr("duration", 20), 0, 1200));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("hint.magnitude." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        CompoundTag tag = data(stack);
        if (kind == Kind.RESERVOIR) tooltip.accept(Component.translatable("hint.magnitude.charge", tag.getDoubleOr("charge", 0)));
        if (kind == Kind.TUNER) tooltip.accept(Component.translatable("hint.magnitude.operation",ToolMode.fromStored(tag.getIntOr("operation",0)).label(),tag.getDoubleOr("value",0.5)));
    }
}
