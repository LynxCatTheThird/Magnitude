package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.physics.LocalProxy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import virtuoel.pehkui.api.ScaleTypes;
import java.util.UUID;
import java.util.function.Consumer;

public final class ScaleSnapshotTests {
    private static void require(boolean ok, String label, Consumer<String> passed) {
        if (!ok) throw new AssertionError(label);
        passed.accept(label);
    }
    public static void run(MinecraftServer server, Consumer<String> passed) {
        var original = Magnitude.settings;
        Magnitude.settings = new Settings();
        Magnitude.settings.minimum = ScaleSafety.MINIMUM;
        Magnitude.settings.maximum = ScaleSafety.MAXIMUM;
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "ScaleView"), ClientInformation.createDefault());
        try {
            double attack = 0, motion = 0;
            for (double size : new double[]{ScaleSafety.MINIMUM, 1, 2, 4.5, 5, 5.5, 10, 32, 50, 1024, ScaleSafety.MAXIMUM}) {
                Dimensions.set(player, size, 0);
                var view = Dimensions.snapshot(player);
                require(view.base() == size && view.width() == player.getBbWidth() && view.height() == player.getBbHeight(), "snapshot retains effective dimensions at " + size, passed);
                require(view.attackFactor() == ScaleTypes.ATTACK.getScaleData(player).getScale()
                    && view.motionFactor() == ScaleTypes.MOTION.getScaleData(player).getScale()
                    && Double.isFinite(view.jumpVelocityLimit()) && view.jumpVelocityLimit() <= 1.5,
                    "snapshot uses runtime attributes once at " + size, passed);
                require(view.attackFactor() >= attack && view.motionFactor() >= motion, "snapshot attribute coefficients remain monotonic at " + size, passed);
                attack = view.attackFactor(); motion = view.motionFactor();
                require(Dimensions.snapshot(player) == view, "same tick consumers share immutable snapshot at " + size, passed);
            }
            Dimensions.set(player, 1, 0);
            var before = Dimensions.snapshot(player);
            Dimensions.set(player, 2, 0);
            var after = Dimensions.snapshot(player);
            require(after.revision() > before.revision() && before.base() == 1 && after.base() == 2, "direct size write refreshes snapshot without mutating previous view", passed);
            ScaleService.invalidate(player);
            require(Dimensions.snapshot(player).revision() == after.revision(), "unchanged refresh preserves scale revision", passed);
            Dimensions.set(player, 32, 0);
            before = Dimensions.snapshot(player);
            LocalProxy.apply(player, 9);
            after = Dimensions.snapshot(player);
            require(after.revision() > before.revision() && after.width() > before.width(), "proxy expansion refreshes physical snapshot in same tick", passed);
            Dimensions.set(player, 1, 0);
            before = Dimensions.snapshot(player);
            player.setPose(Pose.CROUCHING);
            after = Dimensions.snapshot(player);
            require(after.height() < before.height() && after.revision() > before.revision(), "pose change refreshes world-unit dimensions", passed);
            player.setPose(Pose.STANDING);
            var attribute=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            attribute.setBaseValue(2);Dimensions.set(player,3,0);
            double full=Dimensions.snapshot(player).modelHeight();
            require(Math.abs(dev.magnitude.physics.PlayerBody.parts(player,player.position()).get(0).bounds().maxY
                -player.getY()-1.8*full)<1e-5,"full body does not multiply vanilla size attribute twice",passed);
            player.setPose(Pose.CROUCHING);
            require(Math.abs(dev.magnitude.physics.PlayerBody.parts(player,player.position()).get(0).bounds().maxY
                -player.getY()-1.5*full)<1e-5,"full body applies crouching stance once",passed);
            player.setPose(Pose.STANDING);attribute.setBaseValue(1);
            Dimensions.set(player, 1, 0);
            Dimensions.set(player, 5, 10);
            before = Dimensions.snapshot(player);
            ScaleTypes.BASE.getScaleData(player).tick();
            after = Dimensions.snapshot(player);
            require(after.base() > before.base() && after.revision() > before.revision(), "transition tick cannot retain old snapshot", passed);
        } finally { Magnitude.settings = original; }
    }
}
