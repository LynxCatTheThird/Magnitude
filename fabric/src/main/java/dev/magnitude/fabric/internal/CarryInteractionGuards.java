package dev.magnitude.fabric.internal;

import dev.magnitude.fabric.internal.carry.CarryRuntime;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;

/** Held/recovering targets retain chat and escape commands, but cannot affect the world through these actions. */
public final class CarryInteractionGuards {
    private CarryInteractionGuards() {}
    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> CarryRuntime.isControlled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> CarryRuntime.isControlled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> CarryRuntime.isControlled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> CarryRuntime.isControlled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> CarryRuntime.isControlled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !CarryRuntime.isControlled(player));
    }
}
