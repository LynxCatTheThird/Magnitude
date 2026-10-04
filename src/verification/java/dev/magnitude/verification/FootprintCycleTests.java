package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

/** Coupled movement, gravity, pose and queued excavation; fixed-position sampling misses this loop. */
public final class FootprintCycleTests {
    public static void run(MinecraftServer server,Consumer<String> passed) {
        var original=Magnitude.settings;Magnitude.settings=new Settings();Magnitude.settings.maximum=100;
        Magnitude.settings.terrainDamage=true;
        var level=server.overworld();var root=new BlockPos(9000,200,9000);
        for(int x=-2;x<=2;x++)for(int z=-1;z<=7;z++)level.getChunk(root.offset(x*16,0,z*16));
        for(var pos:BlockPos.betweenClosed(root.offset(-16,-4,-8),root.offset(16,-1,100)))
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"FootprintCycle"),ClientInformation.createDefault());
        try {
            player.setPos(9000.5,200,9000.5);player.setOnGround(true);player.setYRot(0);
            Dimensions.set(player,50,0);var state=EntityState.of(player);state.terrainEnabled=true;
            state.initialized=true;state.previousPosition=player.position();state.previousSize=50;state.grounded=true;
            for(double size:new double[]{5,17.25,50}) {
                for(var pos:BlockPos.betweenClosed(root.offset(-16,-1,-8),root.offset(16,-1,100)))
                    level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                player.setPos(9000.5,200,9000.5);player.setOnGround(true);Dimensions.set(player,size,0);
                state.pose=BodyPose.IDLE;state.posePhase=0;state.strideDistance=0;
                state.contacts=new ContactState();state.previousPosition=player.position();state.previousSize=size;
                state.initialized=true;state.grounded=true;
            double low=200,high=200,lowestSole=Double.POSITIVE_INFINITY;int climbs=0,denials=0,supportTicks=0;
            for(int frame=0;frame<120;frame++) {
                PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();
                state.physicsTick=Long.MIN_VALUE;state.contacts.sampleTick=Long.MIN_VALUE;state.contacts.obstacleTick=Long.MIN_VALUE;
                double before=player.getY();
                player.move(MoverType.SELF,new Vec3(0,-.08/Dimensions.snapshot(player).motionFactor(),.5/Dimensions.snapshot(player).motionFactor()));
                denials+=state.movementDenied?1:0;
                ContactEvents.sample(player);Impact.continueFeet(player);
                low=Math.min(low,player.getY());high=Math.max(high,player.getY());
                if(state.pose.support()!=0)supportTicks++;
                for(int side:new int[]{-1,1})if((state.pose.support()&(side<0?1:2))!=0)lowestSole=Math.min(lowestSole,PlayerBody.foot(player,side).y+.01);
                if(player.getY()>before+.05)climbs++;
            }
            System.out.println("FOOTPRINT CYCLE: scale="+size+" y="+low+".."+high+" climbs="+climbs+" denials="+denials+" forward="+(player.getZ()-9000.5));
            if(low<200-LegKinematics.lift(LegKinematics.WALK_SWING,0,PlayerBody.stanceHeight(player))-1e-5 || Math.abs(high-200)>1e-5 || lowestSole<200-1e-5 || supportTicks==0 || player.getZ()-9000.5<=55 || climbs!=0 || denials!=0)
                throw new AssertionError("walking impressions must retain real sole support and forward progress without uphill oscillations or rejection; scale="+size+", lowestSole="+lowestSole+", supports="+supportTicks+", low="+low);
            passed.accept("coupled footprint soles stay above intact support while the pelvis settles without stairs or movement rejection at "+size);
            }
            state.contacts.footprints.clear();state.pose=BodyPose.IDLE;state.contacts.selfTerrainFall=false;
            state.contacts.excavationY=Double.NaN;
            player.setPos(9000.5,200,9010.5);player.setOnGround(true);
            for(var pos:BlockPos.betweenClosed(root.offset(-16,-1,5),root.offset(16,-1,16)))
                level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            Impact.beginTick();int queued=Impact.feet(player,0,true);
            if(queued!=0 || state.contacts.footprints.isEmpty())throw new AssertionError("loaded impression waits for release");
            for(int frame=0;frame<20;frame++) {
                PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();state.physicsTick=Long.MIN_VALUE;
                player.move(MoverType.SELF,new Vec3(0,-.08/Dimensions.snapshot(player).motionFactor(),0));
                Impact.continueFeet(player);
            }
            if(player.getY()!=200 || !level.getBlockState(root.offset(8,-1,10)).is(Blocks.STONE)
                || !level.getBlockState(root.offset(4,-1,10)).is(Blocks.STONE))throw new AssertionError("whole sole remains supported");
            passed.accept("occupied footprint preserves the whole support surface without a central pillar");
            for(int frame=0;frame<30;frame++) {
                PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();state.physicsTick=Long.MIN_VALUE;
                player.move(MoverType.SELF,new Vec3(0,-.08/Dimensions.snapshot(player).motionFactor(),.5/Dimensions.snapshot(player).motionFactor()));
                Impact.continueFeet(player);
            }
            for(int batch=0;batch<10;batch++){Impact.beginTick();Impact.continueFeet(player);}
            for(int x=4;x<=11;x++)for(int z=7;z<=14;z++) {
                if(!level.getBlockState(root.offset(x,-1,z)).isAir() || !level.getBlockState(root.offset(x,-2,z)).is(Blocks.STONE))
                    throw new AssertionError("released rectangular impression has uniform one-block depth including center");
            }
            if(Math.abs(player.getY()-200)>1e-5)throw new AssertionError("release does not lower current support");
            passed.accept("released footprint finishes at uniform depth including its center without lowering the walker");
        } finally {Magnitude.settings=original;PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();}
    }
}
