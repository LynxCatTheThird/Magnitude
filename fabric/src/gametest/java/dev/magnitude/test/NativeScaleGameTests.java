package dev.magnitude.test;

import dev.magnitude.fabric.api.MagnitudeScales;
import dev.magnitude.fabric.api.ScaleAccess;
import dev.magnitude.fabric.api.ScaleChange;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;

/** Real GameTest-server entities/world queries; this does not substitute for real client tests. */
public final class NativeScaleGameTests {
    private static Pig pig(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(4, 1, 4));
        pig.setNoAi(true); pig.setNoGravity(true);
        return pig;
    }
    private static ScaleAccess service(GameTestHelper helper) {
        return MagnitudeScales.forServer(helper.getLevel().getServer());
    }
    private static void close(GameTestHelper helper, double actual, double expected, String field) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-5, field + " expected=" + expected + " actual=" + actual);
    }
    @GameTest(maxTicks = 60)
    public void uniformGeometryHasNoDoubleScaling(GameTestHelper helper) {
        Pig pig = pig(helper); ScaleAccess service = service(helper);
        double width = pig.getBbWidth(), height = pig.getBbHeight(), eye = pig.getEyeHeight();
        double[] scales = {0.25, 0.5, 1.0, 1.5, 2.0, 3.25, 4.0};
        for (int i = 0; i < scales.length; i++) {
            double scale = scales[i];
            helper.runAtTickTime(2L * i + 1, () -> {
                var before = service.observe(pig);
                var root = pig.position();
                boolean noPhysics = pig.noPhysics;
                var result = service.setScale(helper.getLevel().getServer().createCommandSourceStack(), pig, scale, before.revision());
                helper.assertTrue(result.succeeded(), result.code() + " " + result.detail());
                var after = result.snapshot().orElseThrow();
                helper.assertTrue(pig.position().equals(root), "resize changed verified root");
                helper.assertTrue(pig.noPhysics == noPhysics, "temporary noPhysics leaked");
                close(helper, after.effectiveScale(), scale, "effective scale");
                close(helper, after.observedWidth(), width * scale, "width");
                close(helper, after.observedHeight(), height * scale, "height");
                close(helper, after.observedEyeHeight(), eye * scale, "eye height");
            });
        }
        helper.runAtTickTime(20, helper::succeed);
    }
    @GameTest
    public void invalidScaleDoesNotMutate(GameTestHelper helper) {
        Pig pig = pig(helper); ScaleAccess service = service(helper);
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 0.249, 4.001}) {
            var result = service.setScale(helper.getLevel().getServer().createCommandSourceStack(), pig, value, 0);
            helper.assertTrue(result.code() == ScaleChange.Code.INVALID_SCALE, "invalid value accepted");
        }
        close(helper, pig.getScale(), 1, "unchanged scale");
        helper.assertTrue(service.observe(pig).revision() == 0, "invalid calls changed revision");
        helper.succeed();
    }
    @GameTest
    public void unauthorizedApiCannotBypassCommandPermission(GameTestHelper helper) {
        Pig pig = pig(helper);
        var source = helper.getLevel().getServer().createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
        var result = service(helper).setScale(source, pig, 2, 0);
        helper.assertTrue(result.code() == ScaleChange.Code.DENIED, "permission bypass");
        close(helper, pig.getScale(), 1, "unchanged scale");
        helper.succeed();
    }
    @GameTest
    public void blockedGrowthPreservesPositionAndScale(GameTestHelper helper) {
        Pig pig = pig(helper); var before = pig.position();
        helper.setBlock(5, 1, 4, Blocks.STONE);
        var result = service(helper).setScale(helper.getLevel().getServer().createCommandSourceStack(), pig, 4, 0);
        helper.assertTrue(result.code() == ScaleChange.Code.BLOCKED, "growth should be blocked: " + result.code());
        close(helper, pig.getScale(), 1, "unchanged scale");
        helper.assertTrue(before.equals(pig.position()), "rejection moved entity");
        helper.succeed();
    }
    @GameTest
    public void staleRevisionCannotOverwrite(GameTestHelper helper) {
        Pig pig = pig(helper); ScaleAccess service = service(helper);
        var source = helper.getLevel().getServer().createCommandSourceStack();
        var first = service.setScale(source, pig, 0.5, 0);
        helper.assertTrue(first.succeeded(), "initial change failed");
        var stale = service.setScale(source, pig, 2, 0);
        helper.assertTrue(stale.code() == ScaleChange.Code.CONFLICT, "stale revision accepted");
        var same = service.setScale(source, pig, 0.5, first.snapshot().orElseThrow().revision());
        helper.assertTrue(same.code() == ScaleChange.Code.UNCHANGED, "same value should be unchanged");
        close(helper, pig.getScale(), 0.5, "preserved scale");
        helper.succeed();
    }
    @GameTest
    public void externalAttributeWriteIsDetected(GameTestHelper helper) {
        Pig pig = pig(helper); ScaleAccess service = service(helper);
        var source = helper.getLevel().getServer().createCommandSourceStack();
        var first = service.setScale(source, pig, 0.5, 0);
        helper.assertTrue(first.succeeded(), "initial change failed");
        pig.getAttribute(Attributes.SCALE).setBaseValue(0.75);
        var result = service.setScale(source, pig, 2, first.snapshot().orElseThrow().revision());
        helper.assertTrue(result.code() == ScaleChange.Code.CONFLICT, "external writer was silently overwritten");
        close(helper, pig.getAttribute(Attributes.SCALE).getBaseValue(), 0.75, "external value retained");
        helper.succeed();
    }
    @GameTest(setupTicks = 100, maxTicks = 30)
    public void tickBudgetRejectsExcessAndResets(GameTestHelper helper) {
        Pig pig = pig(helper); ScaleAccess service = service(helper);
        var source = helper.getLevel().getServer().createCommandSourceStack();
        for (int i = 0; i < 8; i++) {
            var result = service.setScale(source, pig, i % 2 == 0 ? 0.5 : 1, service.observe(pig).revision());
            helper.assertTrue(result.succeeded(), "within budget failed: " + result.code());
        }
        var result = service.setScale(source, pig, 2, service.observe(pig).revision());
        helper.assertTrue(result.code() == ScaleChange.Code.BUDGET, "unbounded writes");
        close(helper, pig.getScale(), 1, "budget rejection unchanged");
        helper.runAfterDelay(2, () -> {
            var retry = service.setScale(source, pig, 0.5, service.observe(pig).revision());
            helper.assertTrue(retry.succeeded(), "budget failed to reset");
            helper.succeed();
        });
    }
    @GameTest(setupTicks = 150)
    public void denseEntityCandidatesAreRejectedBeforeAllocatingAllShapes(GameTestHelper helper) {
        Pig target = pig(helper);
        for (int i = 0; i < 130; i++) pig(helper);
        var result = service(helper).setScale(helper.getLevel().getServer().createCommandSourceStack(), target, 2, 0);
        helper.assertTrue(result.code() == ScaleChange.Code.BUDGET, "dense candidates bypassed cap: " + result.code());
        close(helper, target.getScale(), 1, "density rejection unchanged");
        helper.succeed();
    }

}
