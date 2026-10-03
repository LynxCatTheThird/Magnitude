package dev.magnitude.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Settings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public int schemaVersion = 1;
    public double minimum = 1.0 / 64;
    public double maximum = 32;
    public boolean allowSelfChange = true;
    public boolean terrainDamage = false;
    public boolean standingPressure = true;
    public boolean bodyDamage = true;
    public double walkDamageFactor = 2;
    public double landingDamageFactor = 4;
    public double pressureHardnessFactor = 16;
    public double impactScaleFactor = 0.3;
    public boolean keepSizeAfterDeath = false;
    public int blocksPerTick = 256;
    public int checksPerTick = 2048;
    public int blocksPerImpact = 64;
    public double impactRadius = 6;
    public Map<String, Double> foodFactors = new LinkedHashMap<>();

    public Settings copy() { return JSON.fromJson(JSON.toJson(this), Settings.class); }
    public void validate() {
        if (schemaVersion > 1) throw new IllegalArgumentException("Unsupported configuration version");
        schemaVersion = 1;
        minimum = Double.isFinite(minimum) ? Rules.bounded(minimum, ScaleSafety.MINIMUM, 1) : 1.0/64;
        maximum = Double.isFinite(maximum) ? Rules.bounded(maximum, 1, ScaleSafety.MAXIMUM) : 32;
        blocksPerTick = Math.clamp(blocksPerTick, 0, 1024);
        checksPerTick = Math.clamp(checksPerTick, 16, 8192);
        blocksPerImpact = Math.clamp(blocksPerImpact, 0, 256);
        impactRadius = Rules.bounded(impactRadius, 0.5, 8);
        walkDamageFactor = Rules.bounded(walkDamageFactor, 0, 40);
        landingDamageFactor = Rules.bounded(landingDamageFactor, 0, 80);
        pressureHardnessFactor = Rules.bounded(pressureHardnessFactor, 1, 64);
        impactScaleFactor = Rules.bounded(impactScaleFactor, 0.05, 2);
        if (foodFactors == null) foodFactors = new LinkedHashMap<>();
        foodFactors.entrySet().removeIf(e -> e.getValue() == null || !Double.isFinite(e.getValue()) || e.getValue() <= 0 || e.getValue() > 4);
    }
    public void save() { save(FabricLoader.getInstance().getConfigDir().resolve("magnitude.json")); }
    /** Persist before publishing live settings; a failed write leaves the previous file intact. */
    public void save(Path path) {
        dev.magnitude.config.ConfigFiles.write(path, JSON.toJson(this));
    }
    public static Settings load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("magnitude.json");
        try {
            Settings settings = Files.exists(path) ? JSON.fromJson(Files.readString(path), Settings.class) : new Settings();
            if (settings == null) throw new IllegalArgumentException("Empty configuration");
            settings.validate();
            settings.save(path);
            return settings;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load magnitude.json: " + error.getMessage(), error);
        }
    }
}
