package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

public final class ContactTests {
    private static void require(boolean ok, String label, Consumer<String> passed) {
        if (!ok) throw new AssertionError(label);
        passed.accept(label);
    }
    private static void sample(ServerPlayer player) {
        EntityState.of(player).contacts.sampleTick = Long.MIN_VALUE;
        PhysicsWork.beginTick(); Impact.beginTick(); EntityQueries.beginTick();
        ContactEvents.sample(player);
    }
    public static void run(MinecraftServer server, Consumer<String> passed) throws Exception {
        var original = Magnitude.settings;
        Magnitude.settings = new Settings();
        Magnitude.settings.maximum = ScaleSafety.MAXIMUM;
        Magnitude.settings.terrainDamage = true;
        var level = server.overworld();
        var root = new BlockPos(7000, 220, 7000);
        for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) level.getChunk(root.offset(x*16,0,z*16));
        for (var pos : BlockPos.betweenClosed(root.offset(-8,-3,-8),root.offset(8,20,8)))
            level.setBlock(pos, pos.getY()<220 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        var player = new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"Contacts"),ClientInformation.createDefault());
        player.setPos(7000.5,220,7000.5); player.setOnGround(true);
        var state = EntityState.of(player); state.terrainEnabled = true;
        try {
            Dimensions.set(player,32,0); sample(player);
            long events = state.contacts.sequence;
            ContactEvents.sample(player);
            require(state.contacts.sequence==events,"same tick contact sampling cannot replay effects",passed);
            var event = state.contacts.last;
            int blocks = Impact.remaining();
            require(!ContactEventBus.publish(event) && Impact.remaining()==blocks,"event replay is claimed before terrain effects",passed);
            for(int i=0;i<40;i++) sample(player);
            require(state.contacts.sequence==events && level.getBlockState(root.offset(2,-2,0)).is(Blocks.STONE),"stationary pressure never mines successive layers",passed);
            state.pressureEnabled=false; sample(player);
            state.pressureEnabled=true; sample(player);
            require(state.contacts.sequence==events+2,"personal pressure toggle creates a new load transition",passed);
            level.setBlock(root.below(),Blocks.DIRT.defaultBlockState(),2); sample(player);
            require(state.contacts.last.type()==ContactEvent.Type.SUPPORT_CHANGED,"external support material change is observed",passed);
            player.setOnGround(false);player.setDeltaMovement(new Vec3(0,-0.6,0));sample(player);
            state.jumpImpact=false; player.setOnGround(true); sample(player);
            require(state.contacts.counts[ContactEvent.Type.LANDING.ordinal()]==1,"falling without a jump flag generates one landing fact",passed);
            sample(player);
            require(state.contacts.counts[ContactEvent.Type.LANDING.ordinal()]==1,"stable support cannot replay landing",passed);
            player.setOnGround(false); sample(player);
            long sizeEvents = state.contacts.counts[ContactEvent.Type.SIZE_CHANGED.ordinal()];
            Dimensions.set(player, 5, 0); sample(player);
            require(state.contacts.counts[ContactEvent.Type.SIZE_CHANGED.ordinal()]==sizeEvents+1,
                "airborne size changes remain authoritative contact facts", passed);
            state.terrainEnabled=false; player.setOnGround(true);
            Dimensions.set(player,5,0); player.setPos(7000.5,220,7000.5);
            for(int y=0;y<2;y++)level.setBlock(root.offset(2,y,0),Blocks.OAK_LOG.defaultBlockState(),2);
            PhysicsWork.beginTick();EntityQueries.beginTick();state.physicsTick=Long.MIN_VALUE;
            state.contacts.obstacleTick=Long.MIN_VALUE;
            var stopped=BodyCollision.move(player,new Vec3(2,0,0));
            long obstacles=state.contacts.counts[ContactEvent.Type.OBSTACLE.ordinal()];
            PhysicsWork.beginTick();EntityQueries.beginTick();state.physicsTick=Long.MIN_VALUE;
            BodyCollision.move(player,new Vec3(2,0,0));
            require(state.contacts.counts[ContactEvent.Type.OBSTACLE.ordinal()]==obstacles,
                "repeated blocked movement shares one obstacle event per tick",passed);
            state.terrainEnabled=true;state.contacts.obstacleTick=Long.MIN_VALUE;
            PhysicsWork.beginTick();EntityQueries.beginTick();Impact.beginTick();state.physicsTick=Long.MIN_VALUE;
            var cleared=BodyCollision.move(player,new Vec3(2,0,0));
            require(state.contacts.changedBlocks>0 && cleared.x>=stopped.x && cleared.y<stopped.y,
                "obstacle mutation re-queries collision and advances the same movement",passed);
            for(int y=0;y<8;y++)level.setBlock(root.offset(2,y,0),Blocks.AIR.defaultBlockState(),2);
            Dimensions.set(player,1,0);player.setPos(7000.5,220,7000.5);player.setOnGround(true);
            level.setBlock(root.above(2),Blocks.STONE.defaultBlockState(),2);
            PhysicsWork.beginTick(); EntityQueries.beginTick();
            Vec3 clipped=BodyCollision.move(player,new Vec3(0,1,0));
            require(clipped.y<0.3,"verified upward escape still clips against a ceiling",passed);
            PhysicsWork.beginTick();PhysicsWork.cells(PhysicsWork.cellsRemaining());EntityQueries.beginTick();
            require(BodyCollision.move(player,new Vec3(0,1,0)).equals(Vec3.ZERO) && state.movementDenied,"exhausted collision budget cannot allow upward ceiling penetration",passed);
            player.setPos(550000.5,220,550000.5);var old=player.position();
            player.move(MoverType.SELF,new Vec3(0,1,0));
            require(player.position().equals(old) && !level.hasChunkAt(player.blockPosition()),"upward movement into unknown chunks never loads or crosses them",passed);
            var packet = new dev.magnitude.network.PhysicsPayload(player.getId(),7,8,false,0,BodyPose.IDLE);
            require(!packet.newerThan(7) && !packet.newerThan(8) && packet.newerThan(6), "duplicate and reordered physics revisions cannot replay interpolation", passed);
            var dispatcher=server.getCommands().getDispatcher();
            var source=player.createCommandSourceStack();
            require(!dispatcher.getRoot().getChild("magnitude").getChild("config").getChild("server").canUse(source),"server configuration requires administrator",passed);
            require(dispatcher.getRoot().getChild("magnitude").getChild("diagnostics").getChild("physics").canUse(source),"physical diagnosis is available to ordinary players",passed);
            state.pressureEnabled=false;
            var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());
            state.save(output,player);
            var restored=new EntityState();restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()),player);
            require(!restored.pressureEnabled && restored.contacts.last==null,"personal pressure persists while contact history resets",passed);
        } finally {Magnitude.settings=original;PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();}
    }
}
