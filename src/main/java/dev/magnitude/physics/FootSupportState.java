package dev.magnitude.physics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Transient world anchors; never a substitute for a current surface query. */
public final class FootSupportState {
    public record Anchor(Vec3 point,long surfaceRevision){}
    public Anchor left,right;
    public Identifier dimension;
    public Vec3 root;
    public double width,height,yaw;
    public String reason="uninitialized";
    public void clear(String reason){left=right=null;root=null;this.reason=reason;}
}
