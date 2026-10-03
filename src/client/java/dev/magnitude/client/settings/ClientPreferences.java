package dev.magnitude.client.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.magnitude.client.MagnitudeClient;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Persistent local view settings; never sent as physics rules. */
public final class ClientPreferences {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static String lastError="ready";
    public double magnification=4,sensitivity=1;
    public boolean smoothing,hiddenNames;
    public boolean footprints=true;
    public double footprintDistance=128;
    public int footprintCache=2048;
    public static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("magnitude-client.json");}
    public static void load(){
        try {
            var next=Files.exists(path())?JSON.fromJson(Files.readString(path()),ClientPreferences.class):new ClientPreferences();
            if(next==null)throw new IllegalArgumentException("Empty client configuration");
            next.magnification=Double.isFinite(next.magnification)?Math.clamp(next.magnification,1,32):4;
            next.sensitivity=Double.isFinite(next.sensitivity)?Math.clamp(next.sensitivity,.1,2):1;
            next.footprintDistance=Double.isFinite(next.footprintDistance)?Math.clamp(next.footprintDistance,16,256):128;
            next.footprintCache=Math.clamp(next.footprintCache,128,4096);
            next.apply();
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("Magnitude").warn("Cannot read client preferences; using defaults",error);new ClientPreferences().apply();}
    }
    private void apply(){MagnitudeClient.magnification=magnification;MagnitudeClient.sensitivity=sensitivity;MagnitudeClient.smoothing=smoothing;MagnitudeClient.hiddenNames=hiddenNames;dev.magnitude.client.visual.FootprintRenderer.enabled=footprints;dev.magnitude.client.visual.FootprintRenderer.distance=footprintDistance;dev.magnitude.client.visual.FootprintRenderer.cacheLimit=footprintCache;}
    public static Map<String,Double> values(){return Map.of("magnification",MagnitudeClient.magnification,"sensitivity",MagnitudeClient.sensitivity,"smoothing",MagnitudeClient.smoothing?1d:0d,"hiddenNames",MagnitudeClient.hiddenNames?1d:0d,"footprints",dev.magnitude.client.visual.FootprintRenderer.enabled?1d:0d,"footprintDistance",dev.magnitude.client.visual.FootprintRenderer.distance,"footprintCache",(double)dev.magnitude.client.visual.FootprintRenderer.cacheLimit);}
    public static void restore(Map<String,Double> values){
        MagnitudeClient.magnification=values.get("magnification");MagnitudeClient.sensitivity=values.get("sensitivity");
        MagnitudeClient.smoothing=values.get("smoothing")==1;MagnitudeClient.hiddenNames=values.get("hiddenNames")==1;
        dev.magnitude.client.visual.FootprintRenderer.enabled=values.get("footprints")==1;dev.magnitude.client.visual.FootprintRenderer.distance=values.get("footprintDistance");dev.magnitude.client.visual.FootprintRenderer.cacheLimit=values.get("footprintCache").intValue();
    }
    public static boolean save(Map<String,Double> values){
        var next=new ClientPreferences();
        lastError="invalid";
        try {
            next.magnification=values.get("magnification");next.sensitivity=values.get("sensitivity");
            double smooth=values.get("smoothing"),names=values.get("hiddenNames");
            if(!Double.isFinite(next.magnification)||next.magnification<1||next.magnification>32||!Double.isFinite(next.sensitivity)||next.sensitivity<.1||next.sensitivity>2||(smooth!=0&&smooth!=1)||(names!=0&&names!=1))return false;
            next.smoothing=smooth==1;next.hiddenNames=names==1;
            double footprints=values.get("footprints"),cache=values.get("footprintCache");next.footprintDistance=values.get("footprintDistance");
            if((footprints!=0&&footprints!=1)||!Double.isFinite(next.footprintDistance)||next.footprintDistance<16||next.footprintDistance>256||!Double.isFinite(cache)||cache<128||cache>4096||cache!=Math.rint(cache))return false;
            next.footprints=footprints==1;next.footprintCache=(int)cache;
            lastError="saveFailed";
            dev.magnitude.config.ConfigFiles.write(path(),JSON.toJson(next));next.apply();lastError="applied";return true;
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("Magnitude").warn("Cannot save client preferences",error);return false;}
    }
}
