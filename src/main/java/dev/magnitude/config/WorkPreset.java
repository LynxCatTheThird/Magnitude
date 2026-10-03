package dev.magnitude.config;

import java.util.Map;

/** Measured terrain work limits. Does not select a backend or change material/geometry rules. */
public enum WorkPreset {
    LOW_WRITES("low",32,256,16),STANDARD("standard",256,2048,64);
    public final String id;
    private final Map<String,Double> values;
    WorkPreset(String id,int writes,int checks,int batch){this.id=id;values=Map.of("blocksPerTick",(double)writes,"checksPerTick",(double)checks,"blocksPerImpact",(double)batch);}
    public Map<String,Double> patch(){return values;}
    public static WorkPreset find(String id){for(var preset:values())if(preset.id.equals(id))return preset;return null;}
    public static WorkPreset matching(Map<String,Double> values){for(var preset:values())if(preset.values.entrySet().stream().allMatch(e->e.getValue().equals(values.get(e.getKey()))))return preset;return null;}
}
