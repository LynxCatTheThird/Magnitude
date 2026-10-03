package dev.magnitude.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import dev.magnitude.visual.SoleGeometry;
import java.util.List;

/** Verified horizontal sole contact; partial observations never prove empty support. */
public record FootContact(Identifier dimension, Vec3 sole, double halfWidth, double halfLength,
                          double yaw, double height, double area, List<Patch> patches,
                          boolean complete, String reason, long revision) {
    public record Patch(BlockPos position, int state, double height, double area,
                        List<SoleGeometry.Point> polygon) {}
    public boolean supported(){return complete && area>1e-10 && Double.isFinite(height);}
    public String summary(){return "complete="+complete+", height="+height+", area="+area+", patches="+patches.size()+", reason="+reason;}
    public Vec3 normal(){return new Vec3(0,1,0);}
}
