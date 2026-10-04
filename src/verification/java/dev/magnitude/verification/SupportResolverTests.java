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
    public static int poseChecks;
    public static boolean exhaustAfterSettlement;
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
                "shared ankle geometry reaches both different support heights; root="+p.position()+" pose="+state.pose+" left="+PlayerBody.foot(p,-1)+" right="+PlayerBody.foot(p,1)+" leftAnchor="+state.contacts.feet.left+" rightAnchor="+state.contacts.feet.right,passed);
            check(state.pose.rightKnee()<-.1&&state.pose.leftKnee()>-1e-5,"higher support bends knee while lower leg retains fixed segment lengths",passed);
            var preserved=state.pose.withSupport(2);
            check(preserved.rightLeg()==state.pose.rightLeg()&&preserved.rightKnee()==state.pose.rightKnee(),
                "changing support mask preserves solved hip and knee geometry",passed);
            Vec3 anchor=state.contacts.feet.right.point();double initial=p.getY();
            for(int i=0;i<40;i++){ready(p);state.pose=BodyPoses.update(p,0,false);p.move(MoverType.SELF,new Vec3(0,-.05,0));}
            check(Math.abs(p.getY()-initial)<1e-6&&state.contacts.feet.right.point().distanceToSqr(anchor)<1e-10,
                "standing anchors revalidate and remain stable over repeated gravity/contact cycles",passed);
            ready(p);p.setXRot(10);poseChecks=0;BodyPoses.update(p,0,false);
            check(poseChecks==1,"verified support pose consumes exactly one collision validation before publication; count="+poseChecks,passed);
            p.setXRot(0);ready(p);var walking=BodyPoses.update(p,.5,false);
            check(state.contacts.feet.left==null||state.contacts.feet.right==null,"walking unloads the swing foot world anchor",passed);
            ready(p);state.pose=BodyPoses.update(p,0,false);
            Vec3 planted=PlayerBody.foot(p,1);state.jumpSequence=0;
            ready(p);var airborne=BodyPoses.update(p,0,true);state.pose=airborne;
            check(airborne.support()==2&&PlayerBody.foot(p,1).distanceToSqr(planted)<1e-10,"takeoff retains planted bent foot geometry until support release",passed);
            check(state.contacts.feet.left==null&&state.contacts.feet.right==null&&airborne.action()==2,"takeoff releases anchors instead of pinning feet to terrain",passed);
            p.setOnGround(true);ready(p);state.pose=BodyPoses.update(p,0,false);
            var safePose=state.pose;level.setBlock(root.offset(-1,0,-2),Blocks.STONE.defaultBlockState(),2);state.jumpSequence=0;
            ready(p);var obstructed=BodyPoses.update(p,0,true);
            check(obstructed.valid()&&obstructed.support()==2&&Math.abs(obstructed.leftLeg()-safePose.leftLeg())<1e-8
                &&Math.abs(obstructed.rightKnee()-safePose.rightKnee())<1e-8,"blocked swing retains collision-verified geometry and requested bent support; old="+safePose+" new="+obstructed+" root="+p.position()+" reason="+state.contacts.feet.reason,passed);
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
            check(new BodyPose(1,0,0,1,.12,.4,0,0,0).valid(),
                "numeric pose validation permits an inclined straight planted leg; support is verified against terrain",passed);
            // The same relative root travel has an analytic pelvis drop at every scale.
            for(var pos:BlockPos.betweenClosed(root.offset(-14,0,-8),root.offset(14,12,8)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            for(var pos:BlockPos.betweenClosed(root.offset(-14,-1,-8),root.offset(14,-1,8)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            for(double size:new double[]{.375,4.625,5,49.875,50.125}){
                Dimensions.set(p,size,0);p.setPos(16000.5,200,16000.5);p.setOnGround(true);
                state.pose=BodyPose.IDLE;state.posePhase=0;state.contacts.feet.clear("planted stride fixture");ready(p);
                BodyPoses.update(p,0,false);Vec3 plantedStride=state.contacts.feet.left.point();
                double travel=.06*size,length=(LegKinematics.THIGH+LegKinematics.SHIN)*PlayerBody.stanceHeight(p);
                double expected=travel*travel/(length+Math.sqrt(length*length-travel*travel));
                p.setPos(p.position().add(0,0,travel));ready(p);var stridePose=BodyPoses.update(p,travel,false);
                check(stridePose.support()==1&&p.onGround()&&Math.abs(p.getY()-(200-expected))<1e-6
                    &&PlayerBody.foot(p,-1).add(0,.01,0).distanceToSqr(plantedStride)<1e-8,
                    "continuous planted stride matches rigid leg sphere and grounded sole at "+size+"; pose="+stridePose+", y="+p.getY()+", expected="+expected+", reason="+state.contacts.feet.reason,passed);
                double settled=p.getY();
                for(int i=0;i<8;i++){ready(p);BodyPoses.update(p,0,false);p.move(MoverType.SELF,new Vec3(0,-.05/Dimensions.snapshot(p).motionFactor(),0));}
                check(Math.abs(p.getY()-settled)<1e-6&&state.pose.support()==3&&p.onGround(),
                    "planted-to-standing remains stable without extra settlement at "+size,passed);
            }
            ready(p);
            check(BodyEscape.probe(p,root)==BodyEscape.Occupancy.CLEAR,
                "lowered giant pelvis does not trigger native escape from ground in the real leg gap",passed);
            var embedded=root.above(45);level.setBlock(embedded,Blocks.STONE.defaultBlockState(),2);ready(p);
            check(BodyEscape.probe(p,root)==BodyEscape.Occupancy.BLOCKED,
                "native escape still detects suffocating blocks intersecting the actual giant torso above proxy height",passed);
            level.setBlock(embedded,Blocks.AIR.defaultBlockState(),2);ready(p);PhysicsWork.cells(PhysicsWork.cellsRemaining());
            check(BodyEscape.probe(p,root)==BodyEscape.Occupancy.UNKNOWN,"escape query budget exhaustion cannot prove an occupied column is clear",passed);
            Vec3 savedRoot=p.position();p.setPos(28000.5,200,28000.5);ready(p);
            check(!level.hasChunk(1750,1750)&&BodyEscape.probe(p,new BlockPos(28000,200,28000))==BodyEscape.Occupancy.UNKNOWN&&!level.hasChunk(1750,1750),
                "escape probes reject unknown occupied space without loading chunks",passed);
            p.setPos(savedRoot);
            Dimensions.set(p,5,0);p.setPos(16000.5,200,16000.5);p.setOnGround(true);
            state.pose=BodyPose.IDLE;state.posePhase=0;state.contacts.feet.clear("post-move exhaustion");ready(p);BodyPoses.update(p,0,false);
            p.setPos(p.position().add(0,0,.3));ready(p);exhaustAfterSettlement=true;
            var exhaustedPose=BodyPoses.update(p,.3,false);
            check(!exhaustAfterSettlement&&p.getY()<200&&exhaustedPose.valid()&&exhaustedPose.support()==0
                &&PlayerBody.foot(p,-1).y+.01>=200-1e-6&&state.contacts.feet.left==null&&state.contacts.feet.right==null,
                "post-settlement query exhaustion retains swept geometry without inventing contact or reverting into soil",passed);
            // Unequal supports: the high sole's bearing block must not become a leg kick.
            for(var pos:BlockPos.betweenClosed(root.offset(-5,0,-5),root.offset(5,3,5)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            for(var pos:BlockPos.betweenClosed(root.offset(-5,-1,-5),root.offset(5,-1,5)))level.setBlock(pos,(pos.getX()<root.getX()?Blocks.STONE_SLAB:Blocks.STONE).defaultBlockState(),2);
            for(var pos:BlockPos.betweenClosed(root.offset(1,0,-5),root.offset(5,0,5)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            Dimensions.set(p,5,0);p.setPos(16000.25,201,16000.5);p.setOnGround(true);state.pose=BodyPose.IDLE;state.contacts.feet.clear("unequal sole protection");ready(p);
            BodyPoses.update(p,0,false);
            check(state.pose.support()==3&&Math.abs(PlayerBody.foot(p,1).y+.01-201)<1e-6,
                "unequal protection fixture has an actually planted high sole above the pelvis",passed);
            ready(p);var protectedSweep=ObstacleContacts.capture(p,new Vec3(0,-.05,.15));
            check(protectedSweep.complete()&&protectedSweep.blocks().stream().noneMatch(pos->pos.getY()==200&&pos.getX()>=root.getX()+1),
                "leg sweep preserves each planted sole's real bearing surface on unequal levels; complete="+protectedSweep.complete()+", candidates="+protectedSweep.blocks(),passed);
            check(!LegKinematics.solve(10,0,1).reachable()&&!LegKinematics.solve(0,0,1).reachable(),"unreachable leg targets are rejected without stretching",passed);
        }finally{exhaustAfterSettlement=false;Magnitude.settings=old;p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);PhysicsWork.beginTick();}
    }
}
