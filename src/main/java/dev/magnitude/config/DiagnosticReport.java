package dev.magnitude.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Shared, read-only report from confirmed settings; contains no player or connection identifiers. */
public final class DiagnosticReport {
    private static final com.google.gson.Gson JSON=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private DiagnosticReport() {}
    public static JsonObject create(ConfigView view) {
        var report=new JsonObject();
        report.addProperty("formatVersion",1);
        report.addProperty("mod","Magnitude");
        report.addProperty("version",FabricLoader.getInstance().getModContainer("magnitude").orElseThrow().getMetadata().getVersion().getFriendlyString());
        report.addProperty("timingUnit","milliseconds");
        report.addProperty("serverSource",view==null?"unavailable":"currentServerSnapshot");
        report.add("server",JSON.toJsonTree(view));
        report.addProperty("interpretation","Recent sample windows are not a long-term performance test. Rejection counts are events, not stalled ticks. A past failure may not describe the current move.");
        return report;
    }
    public static String json(JsonObject report) {return JSON.toJson(report);}
    public static Component copyMessage(JsonObject report) {
        return Component.translatable("message.magnitude.reportReady").withStyle(style->style
            .withClickEvent(new ClickEvent.CopyToClipboard(json(report)))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("gui.magnitude.copyReportHint"))));
    }
}
