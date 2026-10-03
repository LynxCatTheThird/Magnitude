package dev.magnitude.config;

import dev.magnitude.core.Settings;
import java.util.function.ToDoubleFunction;
import java.util.function.ObjDoubleConsumer;

/** Shared server field schema for commands, validation and the settings screen. */
public enum ConfigField {
    MINIMUM("minimum",false,false,dev.magnitude.core.ScaleSafety.MINIMUM,1,s->s.minimum,(s,v)->s.minimum=v),
    MAXIMUM("maximum",false,false,1,dev.magnitude.core.ScaleSafety.MAXIMUM,s->s.maximum,(s,v)->s.maximum=v),
    SELF_CHANGE("allowSelfChange",true,false,0,1,s->s.allowSelfChange?1:0,(s,v)->s.allowSelfChange=v==1),
    TERRAIN("terrainDamage",true,false,0,1,s->s.terrainDamage?1:0,(s,v)->s.terrainDamage=v==1),
    SHALLOW("shallowDeformation",true,false,0,1,s->s.shallowDeformation?1:0,(s,v)->s.shallowDeformation=v==1),
    PRESSURE("standingPressure",true,false,0,1,s->s.standingPressure?1:0,(s,v)->s.standingPressure=v==1),
    BODY_DAMAGE("bodyDamage",true,false,0,1,s->s.bodyDamage?1:0,(s,v)->s.bodyDamage=v==1),
    KEEP_SIZE("keepSizeAfterDeath",true,false,0,1,s->s.keepSizeAfterDeath?1:0,(s,v)->s.keepSizeAfterDeath=v==1),
    WALK_DAMAGE("walkDamageFactor",false,false,0,40,s->s.walkDamageFactor,(s,v)->s.walkDamageFactor=v),
    LANDING_DAMAGE("landingDamageFactor",false,false,0,80,s->s.landingDamageFactor,(s,v)->s.landingDamageFactor=v),
    PRESSURE_HARDNESS("pressureHardnessFactor",false,false,1,64,s->s.pressureHardnessFactor,(s,v)->s.pressureHardnessFactor=v),
    IMPACT_SCALE("impactScaleFactor",false,false,.05,2,s->s.impactScaleFactor,(s,v)->s.impactScaleFactor=v),
    BLOCKS("blocksPerTick",false,true,0,1024,s->s.blocksPerTick,(s,v)->s.blocksPerTick=(int)v),
    CHECKS("checksPerTick",false,true,16,8192,s->s.checksPerTick,(s,v)->s.checksPerTick=(int)v),
    IMPACT_BLOCKS("blocksPerImpact",false,true,0,256,s->s.blocksPerImpact,(s,v)->s.blocksPerImpact=(int)v),
    IMPACT_RADIUS("impactRadius",false,false,.5,8,s->s.impactRadius,(s,v)->s.impactRadius=v);

    public final String id;
    public final boolean bool, integer;
    public final double minimum, maximum;
    private final ToDoubleFunction<Settings> reader;
    private final ObjDoubleConsumer<Settings> writer;
    ConfigField(String id,boolean bool,boolean integer,double minimum,double maximum,ToDoubleFunction<Settings> reader,ObjDoubleConsumer<Settings> writer) {
        this.id=id;this.bool=bool;this.integer=integer;this.minimum=minimum;this.maximum=maximum;this.reader=reader;this.writer=writer;
    }
    public double read(Settings settings){return reader.applyAsDouble(settings);}
    public boolean valid(double value){return Double.isFinite(value)&&value>=minimum&&value<=maximum&&(!bool||value==0||value==1)&&(!integer||value==Math.rint(value));}
    public void write(Settings settings,double value){if(!valid(value))throw new IllegalArgumentException(id);writer.accept(settings,value);}
    public static ConfigField find(String id){for(var field:values())if(field.id.equals(id))return field;return null;}
}
