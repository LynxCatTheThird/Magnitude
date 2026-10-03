package dev.magnitude.core;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.Entity;

public final class EntityState {
    public boolean acceptResize;
    public boolean acceptCarry;
    public boolean terrainEnabled;
    public boolean carrying;
    public boolean riderInitiated;
    public int carryPosition;
    public double offsetForward = 0.4;
    public double offsetSide = 0;
    public double offsetUp = 1.25;
    public double randomLow = 0.5;
    public double randomHigh = 2;
    public int randomPeriod;
    public int nextRandom;
    public long nextAction;
    public long nextRequest;
    public boolean ascentExternal = true;
    public boolean descentExternal = true;
    public boolean enlargeExternal = true;
    public boolean reduceExternal = true;
    public boolean initialized;
    public boolean grounded;
    public double downward;
    public double previousSize = 1;
    public net.minecraft.world.phys.Vec3 previousPosition;
    public double strideDistance;
    public boolean leftFoot;
    public long nextJumpImpact;
    public boolean jumpImpact;
    public double proxyLimit = 8;
    public boolean proxyFallback;
    public boolean movementDenied;
    public double posePhase;
    public int jumpSequence;
    public long poseStart;
    public dev.magnitude.physics.BodyPose pose = dev.magnitude.physics.BodyPose.IDLE;
    public dev.magnitude.physics.BodyPose previousPose = dev.magnitude.physics.BodyPose.IDLE;
    public net.minecraft.resources.Identifier poseAdapter;
    public long physicsRevision;
    public long physicsTick = Long.MIN_VALUE;
    public long nextProxyGrowth = Long.MIN_VALUE;
    public int physicsCells;
    public int physicsPairs;
    public long lastFluidTick = Long.MIN_VALUE;
    public ScaleSnapshot scaleSnapshot;
    public long scaleTick = Long.MIN_VALUE, scaleRevision;
    public Settings scaleSettings;
    public String scaleWarning = "none";
    /** Last support location/size that received a static-load check. */
    public double pressureX = Double.NaN, pressureZ = Double.NaN, pressureSize = -1;

    public static EntityState of(Entity entity) { return ((StateAccess)entity).magnitudeState(); }
    public void load(ValueInput input, Entity entity) {
        ValueInput data = input.childOrEmpty("magnitude");
        acceptResize = data.getBooleanOr("resizeConsent", false);
        acceptCarry = data.getBooleanOr("carryConsent", false);
        terrainEnabled = data.getBooleanOr("terrainEnabled", false);
        carryPosition = Math.clamp(data.getIntOr("carryPosition", 0), 0, 2);
        offsetForward = safe(data.getDoubleOr("offsetForward", 0.4), -2, 2, 0.4);
        offsetSide = safe(data.getDoubleOr("offsetSide", 0), -2, 2, 0);
        offsetUp = safe(data.getDoubleOr("offsetUp", 1.25), 0, 2, 1.25);
        randomLow = safe(data.getDoubleOr("randomLow", 0.5), ScaleSafety.MINIMUM, ScaleSafety.MAXIMUM, 0.5);
        randomHigh = safe(data.getDoubleOr("randomHigh", 2), randomLow, ScaleSafety.MAXIMUM, Math.max(randomLow, 2));
        randomPeriod = Math.clamp(data.getIntOr("randomPeriod", 0), 0, 72000);
        nextRandom = Math.clamp(data.getIntOr("nextRandom", randomPeriod), 0, randomPeriod);
        nextAction = entity.level().getGameTime() + Math.clamp(data.getIntOr("actionCooldown", 0), 0, 1200);
        ascentExternal = data.getBooleanOr("ascentExternal", true);
        descentExternal = data.getBooleanOr("descentExternal", true);
        enlargeExternal = data.getBooleanOr("enlargeExternal", true);
        reduceExternal = data.getBooleanOr("reduceExternal", true);
        carrying = false;
        initialized = false;
        previousPosition = null;
        strideDistance = 0;
        jumpImpact = false;
        proxyLimit = 8;
        proxyFallback = false;
        pose = dev.magnitude.physics.BodyPose.IDLE;
        poseAdapter = null;
        posePhase = 0;
        jumpSequence = 0;
        physicsTick = Long.MIN_VALUE;
        nextProxyGrowth = Long.MIN_VALUE;
        physicsRevision = 0;
        scaleSnapshot = null;
        scaleTick = Long.MIN_VALUE;
        scaleRevision = 0;
        scaleSettings = null;
        pressureX = pressureZ = Double.NaN; pressureSize = -1;
    }
    public void save(ValueOutput output, Entity entity) {
        ValueOutput data = output.child("magnitude");
        data.putBoolean("resizeConsent", acceptResize);
        data.putBoolean("carryConsent", acceptCarry);
        data.putBoolean("terrainEnabled", terrainEnabled);
        data.putInt("carryPosition", carryPosition);
        data.putDouble("offsetForward", offsetForward);
        data.putDouble("offsetSide", offsetSide);
        data.putDouble("offsetUp", offsetUp);
        data.putDouble("randomLow", randomLow);
        data.putDouble("randomHigh", randomHigh);
        data.putInt("randomPeriod", randomPeriod);
        data.putInt("nextRandom", Math.clamp(nextRandom, 0, randomPeriod));
        data.putInt("actionCooldown", (int)Math.clamp(nextAction - entity.level().getGameTime(), 0, 1200));
        data.putBoolean("ascentExternal", ascentExternal);
        data.putBoolean("descentExternal", descentExternal);
        data.putBoolean("enlargeExternal", enlargeExternal);
        data.putBoolean("reduceExternal", reduceExternal);
    }
    public void copyPersistentFrom(EntityState old) {
        acceptResize = old.acceptResize;
        acceptCarry = old.acceptCarry;
        terrainEnabled = old.terrainEnabled;
        carryPosition = old.carryPosition;
        offsetForward = old.offsetForward;
        offsetSide = old.offsetSide;
        offsetUp = old.offsetUp;
        randomLow = old.randomLow;
        randomHigh = old.randomHigh;
        randomPeriod = old.randomPeriod;
        nextRandom = old.nextRandom;
        nextAction = old.nextAction;
        ascentExternal = old.ascentExternal;
        descentExternal = old.descentExternal;
        enlargeExternal = old.enlargeExternal;
        reduceExternal = old.reduceExternal;
    }
    public boolean externalEffect(boolean instant, boolean ascending) {
        return instant ? ascending ? enlargeExternal : reduceExternal : ascending ? ascentExternal : descentExternal;
    }
    public void externalEffect(boolean instant, boolean ascending, boolean external) {
        if (instant) {if(ascending)enlargeExternal=external;else reduceExternal=external;}
        else {if(ascending)ascentExternal=external;else descentExternal=external;}
    }
    private static double safe(double number, double min, double max, double fallback) {
        return Double.isFinite(number) ? Math.clamp(number, min, max) : fallback;
    }
}
