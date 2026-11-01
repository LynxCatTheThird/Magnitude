package dev.magnitude.test;

import dev.magnitude.api.scale.ScaleSnapshot;
import dev.magnitude.fabric.api.MagnitudeScales;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * A real client and integrated-server test of native attribute synchronization.
 * This is not a two-client, movement-stability, animation or persistence acceptance test.
 */
public final class NativeScaleClientTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeScaleClientTest.class);
    private static final double EPSILON = 1.0E-5;
    private static final int SYNC_TIMEOUT_TICKS = 200;

    @Override
    public void runTest(ClientGameTestContext context) {
        // This creates a new framework-owned test world; no pre-existing user save is opened.
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            var serverContext = singleplayer.getServer();
            var connection = singleplayer.getConnection();
            connection.waitForChunksDownload();

            Site site = serverContext.computeOnServer(server -> {
                var player = connection.getServerPlayer();
                var level = connection.getServerLevel();
                BlockPos origin = player.blockPosition();
                int y = Math.max(level.getMinY() + 2, Math.min(origin.getY() + 4, level.getMaxY() - 12));
                return new Site(origin.getX(), y, origin.getZ());
            });

            // Only a 13x13 test platform and its 10-block clearance are altered.
            // Commands run through the framework's server console on the server thread.
            serverContext.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(
                    site.x() - 6, site.y(), site.z() - 6,
                    site.x() + 6, site.y() + 10, site.z() + 6));
            serverContext.runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(
                    site.x() - 6, site.y() - 1, site.z() - 6,
                    site.x() + 6, site.y() - 1, site.z() + 6));
            serverContext.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f 180 0",
                    site.x() + 0.5, site.y(), site.z() + 0.5));
            connection.waitForClientboundPackets();
            context.waitFor(client -> client.player != null
                    && client.player.getPose() == Pose.STANDING
                    && client.player.onGround(), SYNC_TIMEOUT_TICKS);
            connection.waitForChunksRender();
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));

            ScaleSnapshot baseline = serverContext.computeOnServer(server ->
                    MagnitudeScales.forServer(server).observe(connection.getServerPlayer()));
            requireClose(baseline.effectiveScale(), 1.0, "fresh-world baseline scale");
            context.waitFor(client -> client.player != null && matches(observe(client.player), baseline),
                    SYNC_TIMEOUT_TICKS);

            double[] scales = {0.5, 1.0, 2.0};
            String[] screenshotNames = {"magnitude_native_scale_0_5", "magnitude_native_scale_1", "magnitude_native_scale_2"};
            for (int i = 0; i < scales.length; i++) {
                double scale = scales[i];
                ScaleSnapshot confirmed = serverContext.computeOnServer(server -> {
                    var player = connection.getServerPlayer();
                    var service = MagnitudeScales.forServer(server);
                    var before = service.observe(player);
                    var result = service.setScale(server.createCommandSourceStack(), player, scale, before.revision());
                    if (!result.succeeded()) {
                        throw new AssertionError("Native resize rejected: " + result.code() + ": " + result.detail());
                    }
                    return result.snapshot().orElseThrow();
                });
                requireClose(confirmed.effectiveScale(), scale, "server effective scale");
                requireClose(confirmed.observedWidth(), baseline.observedWidth() * scale, "server width");
                requireClose(confirmed.observedHeight(), baseline.observedHeight() * scale, "server height");
                requireClose(confirmed.observedEyeHeight(), baseline.observedEyeHeight() * scale, "server eye height");

                // No client-side attribute writes: convergence must come from the real server connection.
                int waited = context.waitFor(client -> client.player != null
                        && client.player.getPose() == Pose.STANDING
                        && matches(observe(client.player), confirmed), SYNC_TIMEOUT_TICKS);
                ClientObservation actual = context.computeOnClient(client -> observe(client.player));
                requireClose(actual.scale(), scale, "client scale");
                requireClose(actual.width(), confirmed.observedWidth(), "client width");
                requireClose(actual.height(), confirmed.observedHeight(), "client height");
                requireClose(actual.eyeHeight(), confirmed.observedEyeHeight(), "client eye height");
                context.waitTicks(3); // allow confirmed state to reach a rendered frame
                var screenshot = context.takeScreenshot(screenshotNames[i]);
                LOGGER.info("MAGNITUDE_CLIENT_SCALE scale={} revision={} width={} height={} eye={} waitTicks={} screenshot={}",
                        scale, confirmed.revision(), actual.width(), actual.height(), actual.eyeHeight(), waited, screenshot);
            }
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        }
        // try-with-resources closes this test world and returns control to the client test runner.
        LOGGER.info("MAGNITUDE_CLIENT_SCALE_PASS single-client integrated-server scales=[0.5,1.0,2.0]");
    }

    private static ClientObservation observe(net.minecraft.world.entity.LivingEntity player) {
        return new ClientObservation(player.getScale(), player.getBbWidth(), player.getBbHeight(), player.getEyeHeight());
    }

    private static boolean matches(ClientObservation client, ScaleSnapshot server) {
        return close(client.scale(), server.effectiveScale())
                && close(client.width(), server.observedWidth())
                && close(client.height(), server.observedHeight())
                && close(client.eyeHeight(), server.observedEyeHeight());
    }

    private static boolean close(double actual, double expected) {
        return Double.isFinite(actual) && Double.isFinite(expected) && Math.abs(actual - expected) < EPSILON;
    }

    private static void requireClose(double actual, double expected, String field) {
        if (!close(actual, expected)) {
            throw new AssertionError(field + " expected=" + expected + " actual=" + actual);
        }
    }

    private record Site(int x, int y, int z) {}
    private record ClientObservation(double scale, double width, double height, double eyeHeight) {}
}
