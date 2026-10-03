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
    public static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("magnitude-client.json");}
    public static void load(){
        try {
            var next=Files.exists(path())?JSON.fromJson(Files.readString(path()),ClientPreferences.class):new ClientPreferences();
            if(next==null)throw new IllegalArgumentException("Empty client configuration");
            next.magnification=Double.isFinite(next.magnification)?Math.clamp(next.magnification,1,32):4;
            next.sensitivity=Double.isFinite(next.sensitivity)?Math.clamp(next.sensitivity,.1,2):1;
            next.apply();
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("Magnitude").warn("Cannot read client preferences; using defaults",error);new ClientPreferences().apply();}
    }
    private void apply(){MagnitudeClient.magnification=magnification;MagnitudeClient.sensitivity=sensitivity;MagnitudeClient.smoothing=smoothing;MagnitudeClient.hiddenNames=hiddenNames;}
    public static Map<String,Double> values(){return Map.of("magnification",MagnitudeClient.magnification,"sensitivity",MagnitudeClient.sensitivity,"smoothing",MagnitudeClient.smoothing?1d:0d,"hiddenNames",MagnitudeClient.hiddenNames?1d:0d);}
    public static void restore(Map<String,Double> values){
        MagnitudeClient.magnification=values.get("magnification");MagnitudeClient.sensitivity=values.get("sensitivity");
        MagnitudeClient.smoothing=values.get("smoothing")==1;MagnitudeClient.hiddenNames=values.get("hiddenNames")==1;
    }
    public static boolean save(Map<String,Double> values){
        var next=new ClientPreferences();
        lastError="invalid";
        try {
            next.magnification=values.get("magnification");next.sensitivity=values.get("sensitivity");
            double smooth=values.get("smoothing"),names=values.get("hiddenNames");
            if(!Double.isFinite(next.magnification)||next.magnification<1||next.magnification>32||!Double.isFinite(next.sensitivity)||next.sensitivity<.1||next.sensitivity>2||(smooth!=0&&smooth!=1)||(names!=0&&names!=1))return false;
            next.smoothing=smooth==1;next.hiddenNames=names==1;
            lastError="saveFailed";
            dev.magnitude.config.ConfigFiles.write(path(),JSON.toJson(next));next.apply();lastError="applied";return true;
        }catch(Exception error){org.slf4j.LoggerFactory.getLogger("Magnitude").warn("Cannot save client preferences",error);return false;}
    }
}
