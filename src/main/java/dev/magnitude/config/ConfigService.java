package dev.magnitude.config;

import dev.magnitude.Magnitude;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Settings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/** All entry points apply candidates only after validation and successful persistence. Server thread only. */
public final class ConfigService {
    public static final ConfigService INSTANCE=new ConfigService(Settings::save);
    private final Consumer<Settings> saver;
    private long revision;
    public record Result(String code,String detail) { public boolean success(){return code.equals("applied");} }
    public ConfigService(Consumer<Settings> saver){this.saver=saver;}
    public long revision(){return revision;}
    public static boolean administrator(CommandSourceStack source){return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);}
    public Map<String,Double> values(){var values=new LinkedHashMap<String,Double>();for(var field:ConfigField.values())values.put(field.id,field.read(Magnitude.settings));return values;}
    public Result server(CommandSourceStack source,long expected,Map<String,Double> changes){
        if(!administrator(source))return new Result("permission","");
        if(expected>=0&&expected!=revision)return new Result("conflict","");
        if(changes.isEmpty()||changes.size()>ConfigField.values().length)return new Result("invalid","");
        var next=Magnitude.settings.copy();
        for(var entry:changes.entrySet()) {
            var field=ConfigField.find(entry.getKey());
            if(field==null||entry.getValue()==null||!field.valid(entry.getValue()))return new Result("invalid",entry.getKey());
            field.write(next,entry.getValue());
        }
        return commit(next);
    }
    private Result commit(Settings next){
        try{saver.accept(next);}catch(RuntimeException error){return new Result("saveFailed","");}
        Magnitude.settings=next;revision++;return new Result("applied","");
    }
    public Result food(CommandSourceStack source,String item,double factor){
        if(!administrator(source))return new Result("permission","");
        if(!Double.isFinite(factor)||factor<.015625||factor>4)return new Result("invalid","factor");
        var id=net.minecraft.resources.Identifier.tryParse(item);
        if(id==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return new Result("invalid","item");
        var next=Magnitude.settings.copy();next.foodFactors.put(id.toString(),factor);return commit(next);
    }
    public Result reload(CommandSourceStack source){
        if(!administrator(source))return new Result("permission","");
        try{var next=Settings.load();Magnitude.settings=next;revision++;return new Result("applied","");}
        catch(RuntimeException error){return new Result("saveFailed","");}
    }
    public static Map<String,Double> personalValues(ServerPlayer player){
        var state=EntityState.of(player);
        return Map.of("terrain",state.terrainEnabled?1d:0d,"pressure",state.pressureEnabled?1d:0d,"resize",state.acceptResize?1d:0d,"carry",state.acceptCarry?1d:0d);
    }
    public Result personal(ServerPlayer player,long expected,Map<String,Double> changes){
        var state=EntityState.of(player);
        if(expected>=0&&expected!=state.configRevision)return new Result("conflict","");
        if(changes.isEmpty()||changes.size()>4)return new Result("invalid","");
        for(var entry:changes.entrySet())if(!personalValues(player).containsKey(entry.getKey())||entry.getValue()==null||(entry.getValue()!=0&&entry.getValue()!=1))return new Result("invalid",entry.getKey());
        for(var entry:changes.entrySet()) {
            boolean value=entry.getValue()==1;
            switch(entry.getKey()){case "terrain"->state.terrainEnabled=value;case "pressure"->state.pressureEnabled=value;case "resize"->state.acceptResize=value;case "carry"->state.acceptCarry=value;default->throw new AssertionError();}
        }
        state.configRevision++;
        if(!state.terrainEnabled)state.contacts.footprints.clear();
        return new Result("applied","");
    }
}
