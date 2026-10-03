package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.content.WorldContent;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import dev.magnitude.terrain.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

/** Actual surface, movement and protected vegetation tests; not fixed-root sampling. */
public final class TerrainIntegrationTests {
    private static void check(boolean value,String label,Consumer<String> passed){if(!value)throw new AssertionError(label);passed.accept(label);}
    private static void ready(ServerPlayer p){PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();var s=EntityState.of(p);s.physicsTick=Long.MIN_VALUE;s.contacts.sampleTick=Long.MIN_VALUE;s.contacts.obstacleTick=Long.MIN_VALUE;}
    public static void run(MinecraftServer server,Consumer<String> passed){
        var old=Magnitude.settings;Magnitude.settings=new Settings();Magnitude.settings.maximum=100;
        Magnitude.settings.terrainDamage=true;Magnitude.settings.shallowDeformation=true;
        Magnitude.settings.blocksPerTick=256;Magnitude.settings.blocksPerImpact=64;
        var level=server.overworld();var root=new BlockPos(21000,200,21000);
        for(int x=-2;x<=2;x++)for(int z=-1;z<=7;z++)level.getChunk(root.offset(x*16,0,z*16));
        var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"IntegratedTerrain"),ClientInformation.createDefault());
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        p.getAbilities().mayBuild=true;var state=EntityState.of(p);state.terrainEnabled=true;
        var plantCell=root.offset(19,0,0);boolean[] veto={false},mutate={false};int[] after={0};
        PlayerBlockBreakEvents.BEFORE.register((world,actor,pos,block,entity)->{
            if(actor!=p||!pos.equals(plantCell))return true;
            if(mutate[0])world.setBlock(pos.below(),Blocks.COARSE_DIRT.defaultBlockState(),2);
            return !veto[0];
        });
        PlayerBlockBreakEvents.AFTER.register((world,actor,pos,block,entity)->{
            if(actor==p&&pos.equals(plantCell)){if(!world.getBlockState(pos.below()).is(WorldContent.COMPACTED_DIRT))throw new AssertionError("plant observer saw uncommitted soil");after[0]++;}
        });
        try {
            var soilCell=plantCell.below();var target=WorldContent.COMPACTED_DIRT.defaultBlockState().setValue(dev.magnitude.content.CompactedSoilBlock.HEIGHT,12);
            level.setBlock(soilCell,Blocks.DIRT.defaultBlockState(),2);level.setBlock(plantCell,Blocks.SHORT_GRASS.defaultBlockState(),2);veto[0]=true;ready(p);
            check(!Impact.compactSoil(p,soilCell,Blocks.DIRT.defaultBlockState(),target)&&level.getBlockState(soilCell).is(Blocks.DIRT)&&level.getBlockState(plantCell).is(Blocks.SHORT_GRASS),"plant-specific veto preserves both vegetation and its soil",passed);
            veto[0]=false;mutate[0]=true;ready(p);
            check(!Impact.compactSoil(p,soilCell,Blocks.DIRT.defaultBlockState(),target)&&level.getBlockState(plantCell).is(Blocks.SHORT_GRASS),"plant protection callback changing soil invalidates both planned writes",passed);mutate[0]=false;
            level.setBlock(soilCell,Blocks.DIRT.defaultBlockState(),2);Magnitude.settings.blocksPerTick=1;ready(p);
            var result=Impact.compactSoil(p,soilCell,Blocks.DIRT.defaultBlockState(),target,64);
            check(result.retry()&&result.writes()==0&&level.getBlockState(plantCell).is(Blocks.SHORT_GRASS)&&level.getBlockState(soilCell).is(Blocks.DIRT),"one remaining write cannot partially remove a two-cell soil and plant operation",passed);
            p.setPos(root.getX()+.5,200,root.getZ()+.5);Dimensions.set(p,5,0);
            state.contacts.soil.put(soilCell,new SoilDeformation.Work(level.dimension(),5,false,Blocks.DIRT.defaultBlockState(),target,level.getGameTime()+100));
            ready(p);SoilDeformation.continueWork(p);check(state.contacts.soil.containsKey(soilCell),"insufficient compound write budget retains soil work for revalidation",passed);
            Magnitude.settings.blocksPerTick=2;Magnitude.settings.blocksPerImpact=2;ready(p);
            check(SoilDeformation.continueWork(p)==2&&state.contacts.soil.isEmpty()&&level.getBlockState(plantCell).isAir()&&level.getBlockState(soilCell)==target&&after[0]==1,"authorized plant and soil share two writes and notify only after the soil is committed",passed);
            Magnitude.settings.blocksPerTick=256;Magnitude.settings.blocksPerImpact=64;
            for(double size:new double[]{5,50,4.75,16.125,49.875}){
                for(var pos:BlockPos.betweenClosed(root.offset(-16,-2,-8),root.offset(16,-2,100)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                for(var pos:BlockPos.betweenClosed(root.offset(-16,-1,-8),root.offset(16,-1,100))){level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),2);level.setBlock(pos.above(),Blocks.SHORT_GRASS.defaultBlockState(),2);}
                state.contacts=new ContactState();state.pose=BodyPose.IDLE;state.posePhase=state.strideDistance=0;state.initialized=false;state.terrainEnabled=true;
                p.setPos(root.getX()+.5,200,root.getZ()+.5);p.setYRot(0);p.setOnGround(true);Dimensions.set(p,size,0);ready(p);ContactEvents.sample(p);
                double low=p.getY(),high=p.getY();int denied=0,writes=0;long started=System.nanoTime();
                for(int tick=0;tick<120;tick++){
                    ready(p);double factor=Dimensions.snapshot(p).motionFactor();
                    p.move(MoverType.SELF,new Vec3(0,-.08/factor,.5/factor));
                    denied+=state.movementDenied?1:0;
                    SoilDeformation.continueWork(p);ContactEvents.sample(p);
                    writes+=Magnitude.settings.blocksPerTick-Impact.remaining();low=Math.min(low,p.getY());high=Math.max(high,p.getY());
                }
                double forward=p.getZ()-root.getZ()-.5;
                System.out.println("TERRAIN INTEGRATION scale="+size+" y="+low+".."+high+" forward="+forward+" denied="+denied+" writes="+writes+" ms="+(System.nanoTime()-started)/1e6);
                check(denied==0&&forward>55&&low>=199.5-1e-5&&high<=200+1e-5,"natural grass walking makes forward progress within shallow depth without denied movement at "+size,passed);
                check(writes>0,"supported natural vegetation no longer suppresses walking compaction at "+size,passed);
                p.setOnGround(true);ready(p);ContactEvents.sample(p);for(int i=0;i<30;i++){ready(p);SoilDeformation.continueWork(p);ContactEvents.sample(p);}
                double settled=p.getY();int atRest=0;
                for(int i=0;i<60;i++){ready(p);p.move(MoverType.SELF,new Vec3(0,-.08/Dimensions.snapshot(p).motionFactor(),0));SoilDeformation.continueWork(p);ContactEvents.sample(p);atRest+=Magnitude.settings.blocksPerTick-Impact.remaining();}
                check(Math.abs(p.getY()-settled)<1e-6&&atRest==0,"walking-to-standing freezes settled soil without a new excavation loop at "+size,passed);
            }
        }finally{veto[0]=mutate[0]=false;Magnitude.settings=old;p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);PhysicsWork.beginTick();Impact.beginTick();}
    }
}
