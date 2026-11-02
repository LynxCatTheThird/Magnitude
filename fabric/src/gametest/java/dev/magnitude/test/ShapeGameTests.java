package dev.magnitude.test;

import dev.magnitude.api.scale.ShapeFactors;
import dev.magnitude.fabric.api.MagnitudeScales;
import dev.magnitude.fabric.api.ScaleChange;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class ShapeGameTests {
    @GameTest
    public void savedShapeRejectsNonFiniteAndUnknownSchema(GameTestHelper h) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("schema", 1); json.addProperty("width", Double.NaN); json.addProperty("height", 1);
        h.assertTrue(dev.magnitude.fabric.internal.scale.ShapeState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).error().isPresent(), "NaN saved factor accepted");
        json.addProperty("width", 1); json.addProperty("schema", 2);
        h.assertTrue(dev.magnitude.fabric.internal.scale.ShapeState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).error().isPresent(), "unknown saved schema accepted");
        h.succeed();
    }
    private static Pig pig(GameTestHelper h) {
        Pig p=h.spawn(EntityTypes.PIG,new BlockPos(4,1,4));p.setNoAi(true);p.setNoGravity(true);return p;
    }
    private static void close(GameTestHelper h,double actual,double expected,String field) {
        h.assertTrue(Math.abs(actual-expected)<1e-5,field+" expected="+expected+" actual="+actual);
    }
    @GameTest
    public void axesComposeWithBaseOnce(GameTestHelper h) {
        var p=pig(h);var api=MagnitudeScales.forServer(h.getLevel().getServer());var source=h.getLevel().getServer().createCommandSourceStack();
        double width=p.getBbWidth(),height=p.getBbHeight(),eye=p.getEyeHeight();var pos=p.position();
        h.assertTrue(api.setShape(source,p,2,.5,0).succeeded(),"shape failed");
        close(h,p.getBbWidth(),width*2,"wide body");close(h,p.getBbHeight(),height*.5,"short body");
        close(h,p.getEyeHeight(),eye*.5,"eye follows height");
        h.assertTrue(api.setScale(source,p,1.5,api.observe(p).revision()).succeeded(),"base failed");
        close(h,p.getBbWidth(),width*3,"combined width");close(h,p.getBbHeight(),height*.75,"combined height");
        close(h,p.getEyeHeight(),eye*.75,"combined eye");h.assertTrue(p.position().equals(pos),"root moved");
        h.succeed();
    }
    @GameTest
    public void blockedShapeDoesNotCommit(GameTestHelper h) {
        var p=pig(h);var api=MagnitudeScales.forServer(h.getLevel().getServer());var pos=p.position();
        h.setBlock(5,1,4,Blocks.STONE);
        var r=api.setShape(h.getLevel().getServer().createCommandSourceStack(),p,4,1,0);
        h.assertTrue(r.code()==ScaleChange.Code.BLOCKED,"wall was not rejected: "+r);
        h.assertTrue(api.shape(p).equals(ShapeFactors.IDENTITY)&&api.observe(p).revision()==0,"rejection committed state");
        h.assertTrue(pos.equals(p.position())&&!p.noPhysics,"rejection leaked position/physics change");h.succeed();
    }
    @GameTest
    public void invalidUnauthorizedAndStaleShapeAreRejected(GameTestHelper h) {
        var p=pig(h);var api=MagnitudeScales.forServer(h.getLevel().getServer());var source=h.getLevel().getServer().createCommandSourceStack();
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,4.01}) {
            h.assertTrue(api.setShape(source,p,invalid,1,0).code()==ScaleChange.Code.INVALID_SCALE,"invalid width accepted");
        }
        h.assertTrue(api.setShape(source.withPermission(PermissionSet.NO_PERMISSIONS),p,2,1,0).code()==ScaleChange.Code.DENIED,"permission bypass");
        h.assertTrue(api.setShape(source,p,.5,1,0).succeeded(),"initial shape rejected");
        h.assertTrue(api.setShape(source,p,2,1,0).code()==ScaleChange.Code.CONFLICT,"stale write accepted");
        close(h,api.shape(p).width(),.5,"shape after failed writes");h.succeed();
    }
    @GameTest
    public void shapeRoundTripsThroughEntityNbt(GameTestHelper h) {
        var p=pig(h);var api=MagnitudeScales.forServer(h.getLevel().getServer());
        h.assertTrue(api.setShape(h.getLevel().getServer().createCommandSourceStack(),p,.5,2,0).succeeded(),"initial shape rejected");
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,p.registryAccess());p.saveWithoutId(out);
        var loaded=new Pig(EntityTypes.PIG,h.getLevel());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),out.buildResult()));
        h.assertTrue(api.shape(loaded).equals(api.shape(p)),"persistent shape mismatch");
        close(h,loaded.getBbWidth(),p.getBbWidth(),"loaded width");close(h,loaded.getBbHeight(),p.getBbHeight(),"loaded height");
        close(h,loaded.getEyeHeight(),p.getEyeHeight(),"loaded eye");h.succeed();
    }
    @GameTest
    public void combinedAxesRespectTheVerifiedDomain(GameTestHelper h) {
        var p=pig(h);var api=MagnitudeScales.forServer(h.getLevel().getServer());var source=h.getLevel().getServer().createCommandSourceStack();
        h.assertTrue(api.setShape(source,p,2,1,0).succeeded(),"initial shape failed");
        var r=api.setScale(source,p,3,api.observe(p).revision());
        h.assertTrue(r.code()==ScaleChange.Code.INVALID_SCALE,"combined width escaped supported domain");
        close(h,p.getScale(),1,"base after rejection");h.succeed();
    }
}
