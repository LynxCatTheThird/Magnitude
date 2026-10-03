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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

public final class GameplayPhysicsTests {
    private static final BlockPos ROOT=new BlockPos(8000,200,8000);
    private static void require(boolean ok,String label,Consumer<String> passed) {
        if(!ok)throw new AssertionError(label);passed.accept(label);
    }
    private static void ready(ServerPlayer p) {
        p.setPos(8000.5,200,8000.5);p.setOnGround(true);p.setYRot(0);
        var state=EntityState.of(p);state.pose=BodyPose.IDLE;state.physicsTick=Long.MIN_VALUE;
        state.contacts.obstacleTick=Long.MIN_VALUE;state.contacts.footprints.clear();
        PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();
    }
    public static void run(MinecraftServer server,Consumer<String> passed) {
        var original=Magnitude.settings;Magnitude.settings=new Settings();
        Magnitude.settings.maximum=ScaleSafety.MAXIMUM;
        var level=server.overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(ROOT.offset(x*16,0,z*16));
        for(var pos:BlockPos.betweenClosed(ROOT.offset(-10,-1,-10),ROOT.offset(10,24,10)))
            level.setBlock(pos,pos.getY()<200?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"GameplayPhysics"),ClientInformation.createDefault());
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
            new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false));
        try {
            // A two-block platform extends across both feet; no terrain mutation needed to climb.
            for(int z=-4;z<=4;z++)for(int y=0;y<2;y++)level.setBlock(ROOT.offset(2,y,z),Blocks.STONE.defaultBlockState(),2);
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(0);
            double previous=0;
            for(double size:new double[]{4.5,5,5.5}) {
                Dimensions.set(player,size,0);ready(player);
                double step=StepPolicy.height(player);
                require(step>previous && Math.abs(step-size*0.6)<1e-5,"step reach varies continuously at "+size,passed);previous=step;
                var motion=BodyCollision.move(player,new Vec3(2,0,0));
                require(motion.x>1.99 && Math.abs(motion.y-2)<1e-5,"scaled player climbs despite overwritten step attribute at "+size,passed);
            }
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(.6);
            Dimensions.set(player,1,0);ready(player);
            require(BodyCollision.move(player,new Vec3(2,0,0)).x<2,"ordinary player cannot automatically climb two-block platform",passed);
            for(int z=-4;z<=4;z++)for(int y=0;y<2;y++)level.setBlock(ROOT.offset(2,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int y=0;y<12;y++)level.setBlock(ROOT.offset(2,y,0),Blocks.OAK_LOG.defaultBlockState(),2);
            Dimensions.set(player,4,0);ready(player);
            Magnitude.settings.terrainDamage=true;EntityState.of(player).terrainEnabled=true;
            var contacts=ObstacleContacts.capture(player,new Vec3(2,0,0));
            require(contacts.complete() && contacts.blocks().contains(ROOT.offset(2,0,0)),"leg sweep reaches tree missed by former center probe",passed);
            PhysicsWork.beginTick();ready(player);
            var motion=BodyCollision.move(player,new Vec3(2,0,0));
            require(level.getBlockState(ROOT.offset(2,0,0)).isAir() && motion.y<2.41,"contact-driven kick breaks a tree without climbing its top",passed);
            level.setBlock(ROOT.offset(2,0,0),Blocks.OAK_LOG.defaultBlockState(),2);
            level.setBlock(ROOT.offset(2,0,3),Blocks.OAK_LOG.defaultBlockState(),2);
            ready(player);BodyCollision.move(player,new Vec3(2,0,0));
            require(level.getBlockState(ROOT.offset(2,0,3)).is(Blocks.OAK_LOG),"walking impact preserves blocks outside leg sweep",passed);
            level.setBlock(ROOT.offset(2,0,0),Blocks.OBSIDIAN.defaultBlockState(),2);
            ready(player);BodyCollision.move(player,new Vec3(2,0,0));
            require(level.getBlockState(ROOT.offset(2,0,0)).is(Blocks.OBSIDIAN),"moderate contact force preserves harder material",passed);
            level.setBlock(ROOT.offset(2,0,0),Blocks.CHEST.defaultBlockState(),2);
            ready(player);BodyCollision.move(player,new Vec3(2,0,0));
            require(level.getBlockState(ROOT.offset(2,0,0)).is(Blocks.CHEST),"contact-driven obstacle writes preserve containers",passed);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-6,-1,-6),ROOT.offset(6,-1,6)))
                level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            Dimensions.set(player,2,0);ready(player);
            require(Impact.feet(player,0,false)>0,"soft ground contact begins below former four-times cutoff",passed);
            Dimensions.set(player,8,0);ready(player);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-6,-1,-6),ROOT.offset(6,-1,6)))
                level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            var support=BlockPos.containing(PlayerBody.foot(player,1));
            require(Impact.feet(player,0,true)>0 && level.getBlockState(support).is(Blocks.DIRT),
                "static pressure preserves actual boot support while deforming surrounding ground",passed);
            // Arbitrary fractional scales share true geometry; a lamp stays between the legs.
            for(int y=0;y<12;y++)level.setBlock(ROOT.offset(2,y,0),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(ROOT.offset(2,0,3),Blocks.AIR.defaultBlockState(),2);
            for(int y=0;y<3;y++)level.setBlock(ROOT.offset(0,y,1),Blocks.STONE.defaultBlockState(),2);
            for(double size:new double[]{49.5,50}) {
                Dimensions.set(player,size,0);ready(player);
                require(Math.abs(PlayerBody.parts(player,player.position()).get(3).center().x-player.getX()-size*.15)<1e-5,
                    "true leg position remains continuous at "+size,passed);
                var free=BodyCollision.move(player,new Vec3(0,0,2));
                require(free.z>1.99 && Math.abs(free.y)<1e-5,
                    "three-block lamp passes between giant legs at "+size,passed);
                require(level.getBlockState(ROOT.offset(0,0,1)).is(Blocks.STONE),
                    "gap obstacle is not falsely destroyed at "+size,passed);
                ready(player);player.setDeltaMovement(Vec3.ZERO);player.jumpFromGround();
                require(player.getDeltaMovement().y*Dimensions.snapshot(player).motionFactor()<=4.2+1e-5,
                    "jump world displacement is bounded after motion scaling at "+size,passed);
            }
            Dimensions.set(player,50,0);ready(player);
            for(int y=0;y<5;y++)level.setBlock(ROOT.offset(7,y,2),Blocks.OAK_LOG.defaultBlockState(),2);
            var giantMove=BodyCollision.move(player,new Vec3(0,0,2));
            require(level.getBlockState(ROOT.offset(7,0,2)).isAir() && giantMove.z>0,
                "giant leg contact destroys a small structure and rechecks movement",passed);
            // Loaded soles grow past the former center-radius limit and continue under quotas.
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-12,-4,-12),ROOT.offset(12,-1,12)))
                level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            Dimensions.set(player,49.5,0);ready(player);
            int first=Impact.feet(player,0,false);
            require(first<=Magnitude.settings.blocksPerImpact && !EntityState.of(player).contacts.footprints.isEmpty(),
                "large footprint retains bounded unfinished work",passed);
            for(int i=0;i<40 && !EntityState.of(player).contacts.footprints.isEmpty();i++) {
                Impact.beginTick();Impact.continueFeet(player);
            }
            require(level.getBlockState(ROOT.offset(7,-1,8)).isAir(),
                "fractional giant sole deforms ground beyond old fixed radius",passed);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-12,-4,-12),ROOT.offset(12,-1,12)))
                level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            ready(player);int landed=Impact.landing(player,2,12);
            for(int i=0;i<40 && !EntityState.of(player).contacts.footprints.isEmpty();i++) {
                Impact.beginTick();Impact.continueFeet(player);
            }
            require(landed>0 && level.getBlockState(ROOT.offset(7,-2,0)).isAir()
                && level.getBlockState(ROOT.offset(0,-1,0)).is(Blocks.STONE),
                "giant landing uses actual soles and bounded depth while preserving leg gap",passed);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-12,-1,-12),ROOT.offset(12,-1,12)))
                level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            ready(player);Impact.feet(player,0,false);
            var protectedLater=ROOT.offset(7,-1,8);
            level.setBlock(protectedLater,Blocks.CHEST.defaultBlockState(),2);
            for(int i=0;i<40 && !EntityState.of(player).contacts.footprints.isEmpty();i++) {
                Impact.beginTick();Impact.continueFeet(player);
            }
            require(level.getBlockState(protectedLater).is(Blocks.CHEST),
                "queued footprint rechecks container protection at write time",passed);
            for(var pos:BlockPos.betweenClosed(ROOT.offset(-12,-1,-12),ROOT.offset(12,-1,12)))
                level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            ready(player);Impact.feet(player,0,false);
            require(!EntityState.of(player).contacts.footprints.isEmpty(),"permission cancellation fixture has pending work",passed);
            EntityState.of(player).terrainEnabled=false;
            int budget=Impact.remaining();Impact.continueFeet(player);
            require(EntityState.of(player).contacts.footprints.isEmpty() && Impact.remaining()==budget,
                "revoked terrain permission cancels pending footprint writes",passed);
            EntityState.of(player).terrainEnabled=true;
            ready(player);PhysicsWork.cells(PhysicsWork.cellsRemaining());
            var missing=ObstacleContacts.capture(player,new Vec3(2,0,0));
            require(!missing.complete() && missing.blocks().isEmpty(),"truncated leg scan never supplies partial destructive candidates",passed);
            Magnitude.settings.terrainDamage=false;Magnitude.settings.standingPressure=false;
            try {
                server.getCommands().getDispatcher().execute("magnitude physics enable",player.createCommandSourceStack());
                require(EntityState.of(player).terrainEnabled && EntityState.of(player).pressureEnabled
                    && !Magnitude.settings.terrainDamage && !Magnitude.settings.standingPressure,
                    "ordinary one-command opt-in cannot change server permissions",passed);
                server.getCommands().getDispatcher().execute("magnitude physics disable",player.createCommandSourceStack());
                require(!EntityState.of(player).terrainEnabled && !EntityState.of(player).pressureEnabled,
                    "one-command opt-out disables both personal terrain modes",passed);
            } catch(com.mojang.brigadier.exceptions.CommandSyntaxException error) {throw new AssertionError(error);}
            Dimensions.set(player,ScaleSafety.MAXIMUM,0);ready(player);
            require(StepPolicy.height(player)<=4.8,"extreme visual size cannot create unbounded automatic steps",passed);
        } finally {Magnitude.settings=original;PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();}
    }
}
