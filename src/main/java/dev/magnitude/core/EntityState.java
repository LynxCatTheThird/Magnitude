package dev.magnitude.core;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.Entity;

public final class EntityState {
    public boolean acceptResize;
    public boolean acceptCarry;
    public boolean terrainEnabled;
    public boolean carrying;
    public int carryPosition;
    public double offsetForward = 0.4;
    public double offsetSide = 0;
    public double offsetUp = 1.25;
    public double randomLow = 0.5;
    public double randomHigh = 2;
    public int randomPeriod;
    public int nextRandom;
    public int nextAction;
    public int nextContact;
    public boolean initialized;
    public boolean grounded;
    public double downward;
    public double previousSize = 1;
    public long lastFluidTick = Long.MIN_VALUE;

    public static EntityState of(Entity entity) { return ((StateAccess)entity).magnitudeState(); }
    public void load(ValueInput input) {
        ValueInput data = input.childOrEmpty("magnitude");
        acceptResize = data.getBooleanOr("resizeConsent", false);
        acceptCarry = data.getBooleanOr("carryConsent", false);
        terrainEnabled = data.getBooleanOr("terrainEnabled", false);
        carryPosition = Math.clamp(data.getIntOr("carryPosition", 0), 0, 2);
        offsetForward = safe(data.getDoubleOr("offsetForward", 0.4), -2, 2, 0.4);
        offsetSide = safe(data.getDoubleOr("offsetSide", 0), -2, 2, 0);
        offsetUp = safe(data.getDoubleOr("offsetUp", 1.25), 0, 2, 1.25);
        randomLow = safe(data.getDoubleOr("randomLow", 0.5), 1.0/64, 32, 0.5);
        randomHigh = safe(data.getDoubleOr("randomHigh", 2), randomLow, 32, 2);
        randomPeriod = Math.clamp(data.getIntOr("randomPeriod", 0), 0, 72000);
    }
    public void save(ValueOutput output) {
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
    }
    private static double safe(double number, double min, double max, double fallback) {
        return Double.isFinite(number) ? Math.clamp(number, min, max) : fallback;
    }
}
