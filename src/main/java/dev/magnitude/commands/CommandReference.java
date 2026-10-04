package dev.magnitude.commands;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

/** Shared examples for chat help and the client reference; suggestions never execute. */
public final class CommandReference {
    private CommandReference() {}
    public static final List<String> GROUPS=List.of("common","size","actions","server");
    private static final String[][][] COMMAND_GROUPS={
        {{"status","/magnitude config show"},{"menu","/magnitude menu"},
         {"terrain","/magnitude config player terrain on"},{"pressure","/magnitude config player pressure on"},
         {"diagnostics","/magnitude diagnostics physics"},{"resizePermit","/magnitude config player resize on"},{"carryPermit","/magnitude config player carry on"}},
        {{"sizeGet","/magnitude scale get"},{"sizeSet","/magnitude scale set 5 20"},
         {"sizeHeight","/magnitude scale height 10 20"},{"sizeReset","/magnitude scale reset"},{"sizeMultiply","/magnitude scale multiply 2 20"},{"sizeAdd","/magnitude scale add 1 20"},
         {"randomStart","/magnitude random start 1 5 200"},{"randomStop","/magnitude random stop"}},
        {{"action","/magnitude action stomp"},{"pickup","/magnitude action pickup"},{"release","/magnitude action release"},
         {"carry","/magnitude carry position hand"},{"toolValue","/magnitude tool set value 2"},
         {"toolMode","/magnitude tool set mode multiply"},{"toolDuration","/magnitude tool set duration 20"},{"toolUnbind","/magnitude tool unbind"},
         {"carryOffset","/magnitude carry offset 0.5 0 1"},{"throw","/magnitude action throw"},{"ride","/magnitude action ride"},
         {"blow","/magnitude action blow"},{"ability","/magnitude action ability"}},
        {{"serverTerrain","/magnitude config server terrainDamage true"},{"serverPressure","/magnitude config server standingPressure true"},
         {"soil","/magnitude config server shallowDeformation true"},{"rules","/magnitude config server"},
         {"presetLow","/magnitude config preset low"},{"presetStandard","/magnitude config preset standard"},
         {"reload","/magnitude config reload"},{"targets","/magnitude scale targets set @e[type=minecraft:pig] 2 20"},
         {"food","/magnitude config food minecraft:apple 2"}}};
    public static String[][] examples(int group){
        return java.util.Arrays.stream(COMMAND_GROUPS[group]).map(String[]::clone).toArray(String[][]::new);
    }
    public static final List<String> DOMAINS=List.of("scale","config","action","carry","tool","random","diagnostics","menu");
    public static String entry(String domain){
        return switch(domain){case "config"->"/magnitude config show";case "diagnostics"->"/magnitude diagnostics physics";default->"/magnitude "+domain;};
    }
    public static List<String[]> examplesForDomain(String domain,boolean administrator){
        var result=new java.util.ArrayList<String[]>();
        for(int group=0;group<COMMAND_GROUPS.length;group++){
            if(group==3&&!administrator)continue;
            for(var example:COMMAND_GROUPS[group])if(example[1].equals("/magnitude "+domain)||example[1].startsWith("/magnitude "+domain+" "))result.add(example.clone());
        }
        return List.copyOf(result);
    }
    public static Component suggestion(String labelKey,String command){
        return Component.translatable(labelKey).withStyle(style->style
            .withClickEvent(new ClickEvent.SuggestCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }
}
