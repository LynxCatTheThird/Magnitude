package dev.magnitude.client.settings;

import com.google.gson.Gson;
import dev.magnitude.client.visual.FootprintRenderer;
import dev.magnitude.config.DiagnosticReport;
import java.util.Map;

/** On-demand local observations plus the last acknowledged server snapshot; drafts are excluded. */
public final class SettingsReport {
    private SettingsReport() {}
    public static String create() {
        var report=DiagnosticReport.create(SettingsConnection.view);
        report.addProperty("serverSource",SettingsConnection.view==null?"unavailable":"lastConfirmedSnapshot");
        long age=SettingsConnection.snapshotAgeMillis();
        report.add("serverSnapshotAgeMillis",age<0?com.google.gson.JsonNull.INSTANCE:new com.google.gson.JsonPrimitive(age));
        var json=new Gson();
        report.add("client",json.toJsonTree(Map.of(
            "source","currentLocalSamplesAndEffectivePreferences",
            "preferences",ClientPreferences.values(),
            "frameIntervals",ClientMetrics.FRAMES.summary(),
            "visualTick",FootprintRenderer.TICK_TIMES.summary(),
            "visualExtraction",FootprintRenderer.EXTRACTION_TIMES.summary(),
            "visualWork",Map.of("cached",FootprintRenderer.cached(),"queued",FootprintRenderer.queued(),
                "checks",FootprintRenderer.lastChecks,"drawn",FootprintRenderer.lastDrawn),
            "interpretation","Frame intervals include frame limits and scheduling; they are not GPU render time. Local settings cannot change server collision.")));
        return DiagnosticReport.json(report);
    }
}
