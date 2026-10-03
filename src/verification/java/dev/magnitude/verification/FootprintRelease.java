package dev.magnitude.verification;
import dev.magnitude.interaction.Impact;
import net.minecraft.server.level.ServerPlayer;
/** Test-only release of contact, retaining production permission and protection checks. */
public final class FootprintRelease {
    private FootprintRelease() {}
    public static int drain(ServerPlayer actor) {
        var root=actor.position();boolean grounded=actor.onGround();actor.setOnGround(false);actor.setPos(root.x,root.y+Math.max(.125,dev.magnitude.core.Dimensions.snapshot(actor).modelHeight()*.2),root.z);
        try {return Impact.continueFeet(actor);} finally {actor.setPos(root);actor.setOnGround(grounded);}
    }
    public static int feet(ServerPlayer actor,int side,boolean pressure) {
        return Impact.feet(actor,side,pressure)+drain(actor);
    }
}
