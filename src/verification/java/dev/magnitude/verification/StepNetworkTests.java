package dev.magnitude.verification;

import dev.magnitude.Magnitude;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.physics.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Actual input/network gate against a twenty-block fixture, not a synthetic movement loop. */
public final class StepNetworkTests implements ModInitializer {
    private final List<String> passed=new ArrayList<>();
    private final double[] sizes={48.625,50.125};
    private int phase=-1,waiting,walking,standing;
    private boolean ready,climbed;
    private double lastY;
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed.add(label);}
    private void write(boolean success,String failure){
        try{Files.writeString(Path.of("step-server-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",success,"failure",failure,"passed",passed)));}
        catch(java.io.IOException error){throw new RuntimeException(error);}
    }
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("magnitude.stepNetworkVerification"))return;
        NaturalTerrainProtocol.register();
        ServerPlayNetworking.registerGlobalReceiver(NaturalTerrainProtocol.Ready.TYPE,(packet,context)->{
            if(packet.index()==phase&&context.player().getName().getString().equals("StepWalker"))ready=true;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(phase>=sizes.length)return;
            var player=server.getPlayerList().getPlayerByName("StepWalker");
            if(player==null){if(phase>=0){write(false,"walking client disconnected before the step gate finished");phase=sizes.length;}return;}
            try{
                if(phase<0||standing>=160){
                    if(++phase>=sizes.length){write(true,"");return;}
                    var level=player.level();
                    for(int x=64;x<=144;x+=16)for(int z=64;z<=144;z+=16)level.getChunk(new BlockPos(x,200,z));
                    for(var pos:BlockPos.betweenClosed(new BlockPos(70,199,72),new BlockPos(132,199,144)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                    for(var pos:BlockPos.betweenClosed(new BlockPos(84,200,106),new BlockPos(116,219,142)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                    player.teleportTo(level,100.5,200,100.5,Set.of(),0,0,true);
                    Dimensions.set(player,sizes[phase],0);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.getAbilities().invulnerable=true;
                    Magnitude.settings.maximum=100;Magnitude.settings.terrainDamage=false;Magnitude.settings.bodyDamage=false;
                    EntityState.of(player).pose=BodyPose.IDLE;EntityState.of(player).posePhase=0;EntityState.of(player).contacts.feet.clear("network fixture reset");
                    player.setOnGround(true);ready=climbed=false;waiting=walking=standing=0;
                    ServerPlayNetworking.send(player,new NaturalTerrainProtocol.Phase(phase,sizes[phase],false));
                }
                if(!ready){if(++waiting>800)throw new AssertionError("step client not ready");return;}
                if(walking++==0)ServerPlayNetworking.send(player,new NaturalTerrainProtocol.Phase(phase,sizes[phase],true));
                if(Boolean.getBoolean("magnitude.queryTrace")){WorldQueryTrace.ROWS.clear();WorldQueryTrace.MOVES.clear();WorldQueryTrace.POSES.clear();}
                boolean contact=false;
                for(int side:new int[]{-1,1}){
                    var sole=FootContacts.capture(player,side);
                    contact|=sole.supported()&&sole.height()>=219.999&&Math.abs(sole.height()-sole.sole().y)<1e-6;
                }
                if(!climbed){
                    if(contact&&player.getZ()>108){climbed=true;lastY=player.getY();check(true,"real forward input climbs twenty-block platform at "+sizes[phase]);ServerPlayNetworking.send(player,new NaturalTerrainProtocol.Phase(phase,sizes[phase],false));}
                    else if(walking>240)throw new AssertionError("forward input did not climb: "+player.position()+" pose="+EntityState.of(player).pose+" reason="+EntityState.of(player).contacts.diagnostics.failure);
                }else{
                    if(!contact)throw new AssertionError("actual sole lost platform during rest");
                    if(++standing>40&&Math.abs(player.getY()-lastY)>1e-5)throw new AssertionError("resting pelvis did not converge");
                    lastY=player.getY();
                    if(((dev.magnitude.verification.mixin.FloatingProbeAccessor)player.connection).magnitude$isFloating())throw new AssertionError("native floating after verified step");
                    if(standing==160)check(true,"160 real network rest ticks retain actual soles and avoid floating at "+sizes[phase]);
                }
            }catch(Throwable error){error.printStackTrace();write(false,error.toString());phase=sizes.length;}
        });
    }
}
