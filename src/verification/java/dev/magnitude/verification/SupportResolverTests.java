package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.physics.*;
import dev.magnitude.interaction.EntityQueries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

public final class SupportResolverTests {
    private static void check(boolean value,String label,Consumer<String> passed){if(!value)throw new AssertionError(label);passed.accept(label);}
    private static void ready(ServerPlayer p){PhysicsWork.beginTick();EntityQueries.beginTick();EntityState.of(p).physicsTick=Long.MIN_VALUE;}
    public static void run(MinecraftServer server,Consumer<String> passed){
        var old=Magnitude.settings;Magnitude.settings=new Settings();Magnitude.settings.maximum=1000;
        var level=server.overworld();var root=new BlockPos(16000,200,16000);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(root.offset(x*16,0,z*16));
        for(var pos:BlockPos.betweenClosed(root.offset(-5,-3,-5),root.offset(5,12,5)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        for(var pos:BlockPos.betweenClosed(root.offset(-5,-1,-5),root.offset(5,-1,5)))level.setBlock(pos,(pos.getX()<root.getX()?Blocks.STONE_SLAB:Blocks.STONE).defaultBlockState(),2);
        var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"SupportResolver"),ClientInformation.createDefault());
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        p.setPos(16000.25,200,16000.5);p.setYRot(0);p.setXRot(0);p.setOnGround(true);Dimensions.set(p,5,0);
        var state=EntityState.of(p);
        try {
            ready(p);state.pose=BodyPoses.update(p,0,false);
            check(Math.abs(p.getY()-199.5)<1e-6&&state.pose.support()==3,"real body movement settles onto unequal left/right ground without hidden floor",passed);
            check(Math.abs(PlayerBody.foot(p,-1).y+.01-199.5)<1e-6&&Math.abs(PlayerBody.foot(p,1).y+.01-200)<1e-6,
                "shared ankle geometry reaches both different support heights",passed);
            check(state.pose.rightKnee()<-.1&&state.pose.leftKnee()>-1e-5,"higher support bends knee while lower leg retains fixed segment lengths",passed);
            Vec3 anchor=state.contacts.feet.right.point();double initial=p.getY();
            for(int i=0;i<40;i++){ready(p);state.pose=BodyPoses.update(p,0,false);p.move(MoverType.SELF,new Vec3(0,-.05,0));}
            check(Math.abs(p.getY()-initial)<1e-6&&state.contacts.feet.right.point().distanceToSqr(anchor)<1e-10,
                "standing anchors revalidate and remain stable over repeated gravity/contact cycles",passed);
            ready(p);var walking=BodyPoses.update(p,.5,false);
            check(state.contacts.feet.left==null||state.contacts.feet.right==null,"walking unloads the swing foot world anchor",passed);
            ready(p);state.pose=BodyPoses.update(p,0,false);
            Vec3 planted=PlayerBody.foot(p,1);state.jumpSequence=0;
            ready(p);var airborne=BodyPoses.update(p,0,true);state.pose=airborne;
            check(airborne.support()==2&&PlayerBody.foot(p,1).distanceToSqr(planted)<1e-10,"takeoff retains planted bent foot geometry until support release",passed);
            check(state.contacts.feet.left==null&&state.contacts.feet.right==null&&airborne.action()==2,"takeoff releases anchors instead of pinning feet to terrain",passed);
            p.setOnGround(true);ready(p);state.pose=BodyPoses.update(p,0,false);
            var safePose=state.pose;level.setBlock(root.offset(-1,0,-2),Blocks.STONE.defaultBlockState(),2);state.jumpSequence=0;
            ready(p);var obstructed=BodyPoses.update(p,0,true);
            check(obstructed.valid()&&obstructed.support()==1&&Math.abs(obstructed.leftLeg()-safePose.leftLeg())<1e-8
                &&Math.abs(obstructed.rightKnee()-safePose.rightKnee())<1e-8,"blocked swing retains collision-verified geometry and alternate support",passed);
            level.setBlock(root.offset(-1,0,-2),Blocks.AIR.defaultBlockState(),2);
            state.pose=BodyPose.IDLE;p.setPos(16000.25,200,16000.5);p.setOnGround(true);
            p.setPose(net.minecraft.world.entity.Pose.CROUCHING);ready(p);state.pose=BodyPoses.update(p,0,false);
            check(Math.abs(PlayerBody.foot(p,1).y+.01-200)<1e-6&&state.pose.support()==3,"crouching contact and collision share one stance height",passed);
            p.setPose(net.minecraft.world.entity.Pose.STANDING);ready(p);state.pose=BodyPoses.update(p,0,false);
            for(var pos:BlockPos.betweenClosed(root.offset(-5,-1,-5),root.offset(5,-1,5)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            ready(p);state.pose=BodyPoses.update(p,0,false);p.move(MoverType.SELF,new Vec3(0,-.1,0));
            check(state.pose.support()==0&&p.getY()<199.5&&state.contacts.feet.left==null&&state.contacts.feet.right==null,
                "external removal clears world anchors and actual movement falls",passed);
            ready(p);PhysicsWork.cells(PhysicsWork.cellsRemaining());p.setOnGround(true);
            state.pose=BodyPoses.update(p,0,false);
            check(state.contacts.feet.left==null&&state.contacts.feet.right==null&&state.contacts.feet.reason.equals("unknown surface"),
                "incomplete contact query never establishes a foot anchor",passed);
            for(double h:new double[]{Math.scalb(1,-20),.01,.375,4.625,49.75,100}) {
                double drop=(LegKinematics.THIGH+LegKinematics.SHIN)*h*.8;
                var joint=LegKinematics.solve(drop,0,h);
                check(joint.reachable()&&Math.abs(LegKinematics.drop(joint.hip(),joint.knee(),h)-drop)<h*1e-8
                    &&Math.abs(LegKinematics.forward(joint.hip(),joint.knee(),h))<h*1e-8,
                    "constrained two-segment solve preserves lengths and continuous scale "+h,passed);
            }
            check(!LegKinematics.solve(10,0,1).reachable()&&!LegKinematics.solve(0,0,1).reachable(),"unreachable leg targets are rejected without stretching",passed);
        }finally{Magnitude.settings=old;p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);PhysicsWork.beginTick();}
    }
}
