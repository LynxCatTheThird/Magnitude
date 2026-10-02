package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

public final class BodyPhysicsTests {
    private static final BlockPos ROOT=new BlockPos(6000,220,6000);
    private static void require(boolean ok,String label,Consumer<String> passed){if(!ok)throw new AssertionError(label);passed.accept(label);}
    private static void ready(ServerPlayer player) {
        PhysicsWork.beginTick();EntityQueries.beginTick();Impact.beginTick();
        var state=EntityState.of(player);state.physicsTick=Long.MIN_VALUE;state.physicsCells=0;state.physicsPairs=0;state.proxyFallback=false;state.pose=BodyPose.IDLE;
        player.setDeltaMovement(Vec3.ZERO);player.setOnGround(true);player.setYRot(0);
    }
    public static void run(MinecraftServer server,Consumer<String> passed) {
        var original=Magnitude.settings;Magnitude.settings=new Settings();Magnitude.settings.maximum=ScaleSafety.MAXIMUM;
        var level=server.overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(ROOT.offset(x*16,0,z*16));
        for(var pos:BlockPos.betweenClosed(ROOT.offset(-10,-2,-10),ROOT.offset(10,65,10)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        for(var pos:BlockPos.betweenClosed(ROOT.offset(-10,-1,-10),ROOT.offset(10,-1,10)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        var actor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"BodyPhysics"),ClientInformation.createDefault());
        actor.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));
        actor.setPos(6000.5,220,6000.5);Dimensions.set(actor,1,0);ready(actor);
        boolean attached=false;
        try {
            require(PlayerBody.parts(actor,actor.position()).size()==6,"body geometry uses six internal parts",passed);
            var head=PlayerBody.parts(actor,actor.position()).getFirst();
            require(head.ray(head.center(),head.center().add(0,0,2)).orElseThrow().equals(head.center()),"ray starting inside a body part hits at its origin",passed);
            actor.move(MoverType.SELF,new Vec3(0,-.2,0));
            require(Math.abs(actor.getY()-220)<1e-6 && actor.onGround(),"real entity movement lands on skeletal feet",passed);
            level.setBlock(ROOT.offset(1,0,0),Blocks.STONE.defaultBlockState(),2);level.setBlock(ROOT.offset(1,1,0),Blocks.STONE.defaultBlockState(),2);
            ready(actor);actor.move(MoverType.SELF,new Vec3(1,0,0));
            require(actor.getX()>6000.5 && actor.getX()<6000.8,"body sweep stops before solid wall",passed);
            actor.setPos(6000.5,220,6000.5);ready(actor);
            Magnitude.settings.terrainDamage=true;EntityState.of(actor).terrainEnabled=true;
            for(int y=0;y<6;y++)level.setBlock(ROOT.offset(1,y,0),Blocks.OAK_LOG.defaultBlockState(),2);
            actor.move(MoverType.SELF,new Vec3(1,0,0));
            require(actor.getY()<220.7 && level.getBlockState(ROOT.offset(1,0,0)).isAir(),"walking into a tree trunk breaks it instead of climbing",passed);
            for(int y=0;y<6;y++)level.setBlock(ROOT.offset(1,y,0),Blocks.AIR.defaultBlockState(),2);
            Magnitude.settings.terrainDamage=false;EntityState.of(actor).terrainEnabled=false;
            actor.setPos(6000.5,220,6000.5);ready(actor);
            level.setBlock(ROOT.offset(1,0,0),Blocks.STONE.defaultBlockState(),2);level.setBlock(ROOT.offset(1,1,0),Blocks.STONE.defaultBlockState(),2);
            require(BodyCollision.newCollision(actor,actor.position(),actor.position().add(1,0,0)),"server position validation rejects new body penetration",passed);
            level.setBlock(ROOT.offset(1,0,0),Blocks.STONE_SLAB.defaultBlockState(),2);level.setBlock(ROOT.offset(1,1,0),Blocks.AIR.defaultBlockState(),2);
            ready(actor);actor.move(MoverType.SELF,new Vec3(1,0,0));
            require(actor.getX()>6001.4 && Math.abs(actor.getY()-220.5)<1e-5,"skeletal movement steps onto half slab",passed);
            level.setBlock(ROOT.offset(1,0,0),Blocks.AIR.defaultBlockState(),2);actor.setPos(6000.5,220,6000.5);ready(actor);
            level.setBlock(ROOT.offset(1,0,0),Blocks.GLASS_PANE.defaultBlockState(),2);level.setBlock(ROOT.offset(1,1,0),Blocks.GLASS_PANE.defaultBlockState(),2);
            actor.move(MoverType.SELF,new Vec3(2,0,0));
            require(actor.getX()<6001.6,"continuous body sweep cannot tunnel through glass pane",passed);
            level.setBlock(ROOT.offset(1,0,0),Blocks.AIR.defaultBlockState(),2);level.setBlock(ROOT.offset(1,1,0),Blocks.AIR.defaultBlockState(),2);
            actor.setPos(6000.5,220,6000.5);ready(actor);
            AABB corner=new AABB(6000.75,220.1,6000.73,6000.79,220.5,6000.78);
            require(actor.getBoundingBox().intersects(corner) && PlayerBody.parts(actor,actor.position()).stream().noneMatch(part->part.intersects(corner)),"body narrow phase preserves empty corners inside root box",passed);
            require(PlayerBody.parts(actor,actor.position()).stream().allMatch(part->part.ray(new Vec3(6000.5,220.3,5999),new Vec3(6000.5,220.3,6002)).isEmpty()),"model ray ignores gap between legs",passed);
            ready(actor);actor.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());actor.connection.resetPosition();level.addNewPlayer(actor);attached=true;
            actor.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(6000.6,220,6000.5,true,false));
            require(Math.abs(actor.getX()-6000.6)<1e-6,"ordinary movement packet accepts clear skeletal movement",passed);
            actor.setPos(6000.5,220,6000.5);ready(actor);
            var first=BodyPoses.update(actor,0,true);var second=BodyPoses.update(actor,0,true);
            require(first.support()==2 && second.support()==1,"canonical successive takeoffs alternate one support foot",passed);
            actor.setOnGround(false);require(BodyPoses.update(actor,0,false).support()==0,"airborne pose has no supporting feet",passed);
            actor.setOnGround(true);require(BodyPoses.update(actor,0,false).support()==3,"stationary landing restores two supports",passed);
            var invalid=Magnitude.id("verification_invalid_pose");BodyPoses.register(invalid,(p,canonical)->new BodyPose(canonical.action(),canonical.startTick(),0,3,Double.NaN,0,0,0,0));
            require(BodyPoses.select(actor,invalid),"explicit server adapter can be selected",passed);
            require(BodyPoses.update(actor,0,false).valid(),"invalid adapter angles fall back to valid server pose",passed);
            var adapted=Magnitude.id("verification_left_takeoff");BodyPoses.register(adapted,(p,c)->c.action()==2 ? c.withSupport(1) : c);
            BodyPoses.select(actor,adapted);require(BodyPoses.update(actor,0,true).support()==1,"server adapter may choose left takeoff without client claims",passed);
            actor.setOnGround(false);require(BodyPoses.update(actor,0,false).support()==0,"adapter cannot manufacture airborne support",passed);actor.setOnGround(true);BodyPoses.select(actor,null);
            Dimensions.set(actor,8,0);LocalProxy.apply(actor,8);ready(actor);EntityState.of(actor).jumpSequence=0;
            Magnitude.settings.terrainDamage=true;EntityState.of(actor).terrainEnabled=true;EntityState.of(actor).nextJumpImpact=0;
            actor.jumpFromGround();
            require(level.getBlockState(ROOT.offset(2,-1,0)).isAir() && level.getBlockState(ROOT.offset(-2,-1,0)).is(Blocks.STONE),"real jump destroys only right supporting footprint",passed);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-6,-1,-6),ROOT.offset(6,-1,6)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            ready(actor);EntityState.of(actor).nextJumpImpact=0;actor.jumpFromGround();
            require(level.getBlockState(ROOT.offset(-2,-1,0)).isAir() && level.getBlockState(ROOT.offset(2,-1,0)).is(Blocks.STONE),"next real jump destroys only left supporting footprint",passed);
            actor.setOnGround(false);Impact.beginTick();
            require(Impact.feet(actor,0,true)==0,"airborne pressure rejected even with stale support state",passed);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-6,-1,-6),ROOT.offset(6,-1,6)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            Dimensions.set(actor,32,0);ready(actor);
            for(int i=0;i<40;i++){PhysicsWork.beginTick();EntityQueries.beginTick();LocalProxy.update(actor);}
            require(EntityState.of(actor).proxyLimit>8 && actor.getBbWidth()>4.8,"loaded free space allows proxy larger than previous fixed cap",passed);
            require(LocalProxy.cells(actor.getBoundingBox())<LocalProxy.CELLS_PER_MOVE,"dynamic proxy remains bounded by voxel traversal cost",passed);
            double proxy=EntityState.of(actor).proxyLimit;
            Dimensions.set(actor,ScaleSafety.MAXIMUM,0);
            require(PlayerBody.actualParts(actor).getFirst().bounds().getYsize()>1e9 && actor.getBbHeight()<64,"implicit full body retains extreme scale with bounded local proxy",passed);
            actor.setPos(550000.5,220,550000.5);ready(actor);
            require(!level.hasChunkAt(actor.blockPosition()),"missing-region fixture begins unloaded",passed);LocalProxy.update(actor);
            require(EntityState.of(actor).proxyLimit<proxy,"proxy contracts near unverified chunks",passed);
            Vec3 old=actor.position();actor.move(MoverType.SELF,new Vec3(.1,0,0));
            require(actor.position().equals(old) && EntityState.of(actor).proxyFallback,"unloaded collision region conservatively blocks movement",passed);
            require(!level.hasChunkAt(actor.blockPosition()),"local proxy and movement never load missing terrain",passed);
            actor.setPos(6000.5,220,6000.5);Dimensions.set(actor,1,0);ready(actor);
            actor.move(MoverType.SELF,new Vec3(1e8,0,0));
            require(actor.getX()==6000.5 && EntityState.of(actor).physicsCells==0,"oversized sweep rejected before empty voxel traversal",passed);
            ready(actor);PhysicsWork.cells(131072);actor.move(MoverType.SELF,new Vec3(.1,0,0));
            require(actor.getX()==6000.5 && EntityState.of(actor).proxyFallback,"shared collision quota exhaustion blocks unknown movement",passed);
            ready(actor);for(int i=0;i<128;i++)EntityQueries.query(level,actor.getBoundingBox(),actor,1);
            actor.move(MoverType.SELF,new Vec3(.1,0,0));
            require(actor.getX()==6000.5 && EntityState.of(actor).proxyFallback,"exhausted entity query quota cannot masquerade as empty space",passed);
            ready(actor);var hugeQuery=EntityQueries.query(level,new AABB(-1e8,-1e8,-1e8,1e8,1e8,1e8),actor,1);
            require(!hugeQuery.complete() && hugeQuery.entities().isEmpty(),"empty spatial traversal is bounded before candidate enumeration",passed);
            Dimensions.set(actor,ScaleSafety.MINIMUM,0);ready(actor);
            actor.move(MoverType.SELF,new Vec3(0,-0.001,0));
            require(actor.onGround() && Math.abs(actor.getY()-220)<1e-7,"tiny skeletal player still lands without vanishing collision",passed);
            var packet=new dev.magnitude.network.PhysicsPayload(actor.getId(),7,9.25,true,90,EntityState.of(actor).pose);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try {dev.magnitude.network.PhysicsPayload.CODEC.encode(buffer,packet);require(packet.equals(dev.magnitude.network.PhysicsPayload.CODEC.decode(buffer)),"physics protocol roundtrip preserves pose and proxy state",passed);}finally{buffer.release();}
            Dimensions.set(actor,1,0);actor.setPos(6000.5,220,6000.72);ready(actor);
            level.setBlock(ROOT.offset(0,1,1),Blocks.STONE.defaultBlockState(),2);
            require(!BodyCollision.poseAllowed(actor,new BodyPose(0,0,0,3,0,0,0,0,1.2)),"new joint pose cannot penetrate nearby block",passed);
            require(EntityState.of(actor).pose.equals(BodyPose.IDLE),"pose validation restores original state after probing",passed);
            level.setBlock(ROOT.offset(0,1,1),Blocks.AIR.defaultBlockState(),2);
            require(Math.abs(actor.getBbHeight()-1.8)<1e-5,"returning from unloaded terrain preserves normal player dimensions",passed);
            actor.setPose(Pose.SWIMMING);require(!BodyCollision.active(actor),"swimming retains pose-specific vanilla movement",passed);actor.setPose(Pose.STANDING);
            Dimensions.reset(actor);require(Dimensions.size(actor)==1 && actor.getBbHeight()<2,"physics reset keeps ordinary dimensions",passed);
        } finally {if(attached)level.removePlayerImmediately(actor,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);Magnitude.settings=original;PhysicsWork.beginTick();EntityQueries.beginTick();}
    }
}
