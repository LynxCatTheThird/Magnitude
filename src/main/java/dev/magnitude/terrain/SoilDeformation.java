package dev.magnitude.terrain;

import dev.magnitude.Magnitude;
import dev.magnitude.core.EntityState;
import dev.magnitude.content.CompactedSoilBlock;
import dev.magnitude.physics.*;
import dev.magnitude.interaction.Impact;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;

/** Bounded irreversible equilibrium per world cell. Equal depth avoids unsupported central columns. */
public final class SoilDeformation {
    public record Work(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                       double size,boolean pressure,BlockState before,BlockState after,long expires){}
    private SoilDeformation(){}
    public static int apply(ContactEvent event){
        var actor=event.player();var state=EntityState.of(actor);
        if(!Magnitude.settings.shallowDeformation||!Magnitude.settings.terrainDamage||!state.terrainEnabled
            ||!actor.onGround()||actor.isPassenger()||actor.getAbilities().flying||actor.isNoGravity())return 0;
        if(event.type()==ContactEvent.Type.SUPPORT_CHANGED&&(!Magnitude.settings.standingPressure||!state.pressureEnabled))return 0;
        var snapshot=event.support();if(snapshot==null||!snapshot.complete()){state.contacts.reason="incomplete soil contact";return 0;}
        double impulse=event.type()==ContactEvent.Type.LANDING?Math.min(64,Math.max(0,-event.velocity().y)*4):0;
        var changes=state.contacts.soil;
        for(int side:new int[]{-1,1}) {
            if((event.pose().support()&(side<0?1:2))==0)continue;
            var support=side<0?snapshot.leftContact():snapshot.rightContact();if(!support.supported())continue;
            var contact=FootContacts.surfaceLayers(actor,side);if(!contact.complete())continue;
            // Pressure increases continuously with size. All touched cells share the same depth;
            // a rigid voxel sole cannot indent only a cell fraction without residual support.
            int target=SoilMaterials.equilibrium(event.scale().base(),1,impulse);
            for(var patch:contact.patches()) {
                if(changes.size()>=1024)break;
                var pos=patch.position();var before=actor.level().getBlockState(pos);
                if(net.minecraft.world.level.block.Block.getId(before)!=patch.state())continue;
                var backend=SoilMaterials.compacted(before);if(backend==null)continue;
                int previous=SoilMaterials.height(before);if(target>=previous)continue;
                var after=backend.defaultBlockState().setValue(CompactedSoilBlock.HEIGHT,target);
                changes.putIfAbsent(pos,new Work(actor.level().dimension(),event.scale().base(),event.type()==ContactEvent.Type.SUPPORT_CHANGED,before,after,actor.level().getGameTime()+100));
            }
        }
        return continueWork(actor);
    }
    public static int continueWork(net.minecraft.server.level.ServerPlayer actor){
        var state=EntityState.of(actor);var changes=state.contacts.soil;
        if(changes.isEmpty())return 0;
        if(!Magnitude.settings.shallowDeformation||!Magnitude.settings.terrainDamage||!state.terrainEnabled
            ||!actor.isAlive()||actor.isSpectator()||!actor.getAbilities().mayBuild){changes.clear();return 0;}
        int writes=0,checks=0,unknown=0;
        while(!changes.isEmpty()&&writes<Magnitude.settings.blocksPerImpact&&checks++<1024) {
            if(Impact.remaining()==0||Impact.checksRemaining()==0)break;
            var entry=changes.entrySet().iterator().next();var work=entry.getValue();
            changes.remove(entry.getKey());
            if(work.pressure&&(!Magnitude.settings.standingPressure||!state.pressureEnabled)||!work.dimension.equals(actor.level().dimension())||work.expires<actor.level().getGameTime()
                ||Math.abs(work.size-dev.magnitude.core.Dimensions.snapshot(actor).base())>Math.max(1e-9,work.size*1e-6)) {continue;}
            if(!actor.level().hasChunkAt(entry.getKey())){changes.put(entry.getKey(),work);if(++unknown>=changes.size())break;continue;}
            unknown=0;
            if(Impact.compactSoil(actor,entry.getKey(),work.before,work.after))writes++;
        }
        if(writes>0) {
            state.contacts.reason="soil reached bounded equilibrium";
            state.contacts.feet.clear("soil changed");
            if(state.pose.action()!=2)state.pose=BodyPoses.update(actor,0,false);
        }else state.contacts.reason="soil unchanged or protected";
        return writes;
    }
}
