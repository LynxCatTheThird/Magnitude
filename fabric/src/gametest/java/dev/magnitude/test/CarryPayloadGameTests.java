package dev.magnitude.test;

import dev.magnitude.fabric.api.carry.CarryView;
import dev.magnitude.fabric.internal.net.CarryStatePayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public final class CarryPayloadGameTests {
    @GameTest
    public void boundedSnapshotRoundTrips(GameTestHelper helper) {
        var epoch = UUID.randomUUID();
        var view = new CarryView(UUID.randomUUID(), UUID.randomUUID(), 1, 2,
                helper.getLevel().dimension().identifier().toString(), new Vec3(1.25, 65.5, -2.75), CarryView.Phase.HELD, 4);
        var payload = new CarryStatePayload(epoch, 9, List.of(view));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CarryStatePayload.CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(CarryStatePayload.CODEC.decode(buffer)), "snapshot did not round-trip");
            helper.assertTrue(!buffer.isReadable(), "unconsumed trailing bytes");
        } finally { buffer.release(); }
        helper.succeed();
    }
    @GameTest
    public void invalidSnapshotCountIsRejectedBeforeMembers(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            buffer.writeUUID(UUID.randomUUID()); buffer.writeVarLong(0); buffer.writeVarInt(CarryStatePayload.MAX_VIEWS + 1);
            boolean rejected = false;
            try { CarryStatePayload.CODEC.decode(buffer); } catch (DecoderException expected) { rejected = true; }
            helper.assertTrue(rejected, "oversized snapshot accepted");
        } finally { buffer.release(); }
        helper.succeed();
    }
    @GameTest
    public void duplicateTargetSnapshotIsRejected(GameTestHelper helper) {
        var view = new CarryView(UUID.randomUUID(), UUID.randomUUID(), 1, 2,
                "minecraft:overworld", Vec3.ZERO, CarryView.Phase.HELD, 1);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CarryStatePayload.CODEC.encode(buffer, new CarryStatePayload(UUID.randomUUID(), 1, List.of(view, view)));
            boolean rejected = false;
            try { CarryStatePayload.CODEC.decode(buffer); } catch (DecoderException expected) { rejected = true; }
            helper.assertTrue(rejected, "duplicate target accepted");
        } finally { buffer.release(); }
        helper.succeed();
    }
    @GameTest
    public void unmanagedPlayerCarrierRemainsRejected(GameTestHelper helper) {
        // A transport unit must not be a logged-in mock rejected by the client capability gate.
        var host = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "transport-host"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var target = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "transport-target"),
                net.minecraft.server.level.ClientInformation.createDefault());
        helper.assertTrue(host.isAlive() && target.isAlive() && !host.isRemoved() && !target.isRemoved(), "invalid transport fixtures");
        helper.assertTrue(!host.getType().canSerialize(), "player type became serializable");
        helper.assertTrue(!target.startRiding(host, true, true), "unmanaged player mount bypassed validation");
        helper.assertTrue(!target.isPassenger() && !host.isVehicle(), "rejected mount mutated transport");
        helper.succeed();
    }

}
