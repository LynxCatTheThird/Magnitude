package dev.magnitude.client.settings;

import java.util.Map;

/** Measured local surface cache limits; does not change visibility, distance or world rules. */
public enum VisualPreset {
    LOW("low",512),BALANCED("balanced",2048),EXTENDED("extended",4096);
    public final String id;
    public final int cells;
    VisualPreset(String id,int cells){this.id=id;this.cells=cells;}
    public static VisualPreset matching(Map<String,Double> values){
        for(var option:values())if(Double.valueOf(option.cells).equals(values.get("footprintCache")))return option;
        return null;
    }
    public VisualPreset next(){return values()[(ordinal()+1)%values().length];}
}
