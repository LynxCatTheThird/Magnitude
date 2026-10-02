package dev.magnitude.core;

import dev.magnitude.Magnitude;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleModifier;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;

/** Keep physical work bounded independently of visual BASE scale, on both sides. */
public final class ScaleSafety {
    public static final double MINIMUM = 0x1p-20;
    public static final double MAXIMUM = 0x1p32;
    private ScaleSafety() {}

    public static void register() {
        cap("collision", 8, ScaleTypes.HITBOX_WIDTH, ScaleTypes.HITBOX_HEIGHT);
        cap("motion", 8, ScaleTypes.MOTION, ScaleTypes.STEP_HEIGHT);
        inherit("jump_height", 8, ScaleTypes.JUMP_HEIGHT);
        inherit("attack", 64, ScaleTypes.ATTACK, ScaleTypes.ATTACK_SPEED, ScaleTypes.KNOCKBACK);
        inherit("combat", 16, ScaleTypes.DEFENSE, ScaleTypes.HEALTH);
        cap("reach", 16, ScaleTypes.REACH, ScaleTypes.BLOCK_REACH, ScaleTypes.ENTITY_REACH);
        cap("secondary", 8, ScaleTypes.DROPS, ScaleTypes.PROJECTILES, ScaleTypes.EXPLOSIONS);
        cap("camera_distance", 32, ScaleTypes.THIRD_PERSON);
    }

    private static void inherit(String name, float maximum, ScaleType... types) {
        var modifier = ScaleRegistries.register(ScaleRegistries.SCALE_MODIFIERS,
            Magnitude.id(name + "_base_inherit"), new ScaleModifier(-1024) {
                @Override public float modifyScale(ScaleData data, float scale, float delta) {
                    float base = ScaleTypes.BASE.getScaleData(data.getEntity()).getScale(delta);
                    return Float.isFinite(scale * base) ? Math.clamp(scale * base, (float)MINIMUM, maximum) : 1;
                }
                @Override public float modifyPrevScale(ScaleData data, float scale) {
                    float base = ScaleTypes.BASE.getScaleData(data.getEntity()).getPrevScale();
                    return Float.isFinite(scale * base) ? Math.clamp(scale * base, (float)MINIMUM, maximum) : 1;
                }
            });
        for (var type : types) type.getDefaultBaseValueModifiers().add(modifier);
    }

    private static void cap(String name, float maximum, ScaleType... types) {
        var modifier = ScaleRegistries.register(ScaleRegistries.SCALE_MODIFIERS, Magnitude.id(name + "_limit"), new ScaleModifier(-1024) {
            @Override public float modifyScale(ScaleData data, float scale, float delta) { return bounded(data,scale); }
            @Override public float modifyPrevScale(ScaleData data, float scale) { return bounded(data,scale); }
            private float bounded(ScaleData data, float scale) {
                float limit=name.equals("collision") && data.getEntity() instanceof net.minecraft.world.entity.player.Player ? (float)EntityState.of(data.getEntity()).proxyLimit : maximum;
                if (name.equals("collision") && data.getEntity() instanceof net.minecraft.world.entity.LivingEntity living && !virtuoel.pehkui.api.PehkuiConfig.COMMON.applyVanillaScale.get()) limit/=living.getScale();
                return Float.isFinite(scale) ? Math.clamp(scale,(float)MINIMUM,limit) : 1;
            }
        });
        for (var type : types) type.getDefaultBaseValueModifiers().add(modifier);
    }
}
