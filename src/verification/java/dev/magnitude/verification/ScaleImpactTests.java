package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.interaction.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import virtuoel.pehkui.api.ScaleTypes;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class ScaleImpactTests {
    private static final BlockPos CENTER = new BlockPos(5000,220,5000);
    private static boolean veto;
    private static void require(boolean ok,String label,Consumer<String> passed) { if(!ok)throw new AssertionError(label);passed.accept(label); }
    private static void floor(ServerPlayer actor, net.minecraft.world.level.block.Block block) {
        for(var pos:BlockPos.betweenClosed(CENTER.offset(-6,-1,-6),CENTER.offset(6,-1,6)))actor.level().setBlock(pos,block.defaultBlockState(),3);
        EntityState.of(actor).contacts.footprints.clear();
        Impact.beginTick();
    }
    private static void tick(MinecraftServer server,ServerPlayer actor) {
        dev.magnitude.physics.PhysicsWork.beginTick();
        EntityState.of(actor).physicsTick = Long.MIN_VALUE;
        EntityState.of(actor).contacts.sampleTick = Long.MIN_VALUE;
        server.getPlayerList().getPlayers().add(actor);
        try {Interactions.tick(server);} finally {server.getPlayerList().getPlayers().remove(actor);}
    }
    public static void run(MinecraftServer server, Consumer<String> passed) {
        var original=Magnitude.settings;Magnitude.settings=new Settings();var level=server.overworld();
        for(var pos:BlockPos.betweenClosed(CENTER.offset(-8,0,-8),CENTER.offset(8,0,8)))level.getChunk(pos);
        for(var pos:BlockPos.betweenClosed(CENTER.offset(-7,-2,-7),CENTER.offset(7,16,7)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        var actor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"ScaleImpact"),ClientInformation.createDefault());
        actor.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),actor,CommonListenerCookie.createInitial(actor.getGameProfile(),false));
        actor.setPos(5000.5,220,5000.5);actor.getAbilities().mayBuild=true;Dimensions.set(actor,1,0);
        boolean attached=false;
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity)->player!=actor||!veto);
        try {
            Magnitude.settings.minimum=Double.NaN;Magnitude.settings.maximum=Double.POSITIVE_INFINITY;Magnitude.settings.validate();
            require(Magnitude.settings.minimum==1.0/64&&Magnitude.settings.maximum==32,"nonfinite configuration bounds recover safely",passed);
            Magnitude.settings.minimum=ScaleSafety.MINIMUM;Magnitude.settings.maximum=ScaleSafety.MAXIMUM;Magnitude.settings.validate();
            require(Magnitude.settings.minimum==ScaleSafety.MINIMUM&&Magnitude.settings.maximum==ScaleSafety.MAXIMUM,"configuration accepts extreme finite range",passed);
            Dimensions.set(actor,ScaleSafety.MINIMUM,0);
            require(Dimensions.size(actor)==ScaleSafety.MINIMUM&&actor.getBbHeight()>0&&actor.getBbHeight()<0.000002,"tiny scale keeps positive real dimensions",passed);
            Dimensions.set(actor,ScaleSafety.MAXIMUM,0);
            require(Dimensions.size(actor)==ScaleSafety.MAXIMUM,"original default maximum 2^32 is accepted",passed);
            require(ScaleTypes.JUMP_HEIGHT.getScaleData(actor).getScale()<=8&&ScaleTypes.JUMP_HEIGHT.getScaleData(actor).getScale()>1,"base scale immediately affects jump height",passed);
            require(ScaleTypes.ATTACK.getScaleData(actor).getScale()<=64&&ScaleTypes.ATTACK.getScaleData(actor).getScale()>1,"base scale immediately affects attack power",passed);
            require(actor.getBbWidth()<=4.801&&actor.getBbHeight()<=14.401,"extreme player hitbox bounded independently of BASE",passed);
            require(ScaleTypes.MODEL_HEIGHT.getScaleData(actor).getScale()==ScaleSafety.MAXIMUM&&ScaleTypes.EYE_HEIGHT.getScaleData(actor).getScale()==ScaleSafety.MAXIMUM,"visual scale and eye height retain extreme value",passed);
            require(ScaleTypes.MOTION.getScaleData(actor).getScale()<=8&&ScaleTypes.STEP_HEIGHT.getScaleData(actor).getScale()<=8,"extreme movement and stepping capped",passed);
            require(ScaleTypes.BLOCK_REACH.getScaleData(actor).getScale()<=16&&ScaleTypes.ENTITY_REACH.getScaleData(actor).getScale()<=16,"extreme vanilla reach factors capped",passed);
            var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());actor.saveWithoutId(out);
            var restored=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),out.buildResult()));
            require(Dimensions.size(restored)==ScaleSafety.MAXIMUM&&restored.getBbHeight()<=8*0.9+0.001,"extreme scale and collision limits survive save/load",passed);
            Dimensions.set(restored,1,0);
            require(PlayerMounts.start(restored,actor),"extreme carrier can maintain validated player mount",passed);
            EntityState.of(actor).carrying=true;
            require(actor.getPassengerRidingPosition(restored).distanceTo(actor.position())<32,"extreme carry offset stays within physical proxy",passed);
            Interactions.release(actor,false);
            Dimensions.set(actor,0.000001,0);Dimensions.set(actor,0.0000015,20);
            require(!Dimensions.settled(actor),"tiny transition is not prematurely considered settled",passed);
            @SuppressWarnings("unchecked") var folding=(virtuoel.kanos_config.api.MutableConfigEntry<Boolean>)virtuoel.pehkui.util.ConfigSyncUtils.CONFIGS.get("applyVanillaScale");
            boolean originalFolding=folding.getValue();folding.setValue(true);
            var attribute=actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);attribute.setBaseValue(2);
            Dimensions.set(actor,3,0);
            require(Dimensions.size(actor)==3&&Dimensions.target(actor)==3&&Math.abs(actor.getBbHeight()-5.4)<0.001,"vanilla scale attribute does not multiply requested effective size twice",passed);
            Dimensions.set(actor,4,20);for(int i=0;i<25;i++)ScaleTypes.BASE.getScaleData(actor).tick();
            require(Dimensions.size(actor)==4&&Dimensions.settled(actor),"transition target accounts for vanilla scale attribute",passed);
            attribute.setBaseValue(3);Dimensions.set(actor,1,0);
            require(Dimensions.size(actor)==Dimensions.target(actor)&&Dimensions.representable(actor,1)==1,"folded target preview uses runtime float multiplication precision",passed);
            attribute.setBaseValue(1);folding.setValue(originalFolding);
            attribute.setBaseValue(16);Dimensions.set(actor,ScaleSafety.MAXIMUM,0);
            require(actor.getBbHeight()<=14.401&&actor.getBbWidth()<=4.801,"independent vanilla scale cannot bypass physical collision limits",passed);
            attribute.setBaseValue(1);
            Dimensions.set(actor,1073741824,0);
            var reservoir=new net.minecraft.world.item.ItemStack(dev.magnitude.content.Content.RESERVOIR);
            var charge=dev.magnitude.content.ToolItem.data(reservoir);charge.putDouble("charge",1);dev.magnitude.content.ToolItem.data(reservoir,charge);
            EntityState.of(actor).nextAction=0;
            require(!((dev.magnitude.content.ToolItem)dev.magnitude.content.Content.RESERVOIR).apply(reservoir,actor,actor)&&Dimensions.target(actor)==1073741824&&dev.magnitude.content.ToolItem.data(reservoir).getDoubleOr("charge",0)==1,"sub-ULP injection preserves reservoir charge instead of losing it",passed);
            reservoir=new net.minecraft.world.item.ItemStack(dev.magnitude.content.Content.RESERVOIR);EntityState.of(actor).nextAction=0;
            require(((dev.magnitude.content.ToolItem)dev.magnitude.content.Content.RESERVOIR).apply(reservoir,actor,actor)&&Dimensions.target(actor)+dev.magnitude.content.ToolItem.data(reservoir).getDoubleOr("charge",0)==1073741824,"giant extraction stores actual representable scale delta",passed);
            var donor=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);donor.setPos(5002.5,220,5000.5);Dimensions.set(donor,1073741824,0);
            Dimensions.set(actor,1,0);EntityState.of(actor).nextAction=0;
            require(Dimensions.canChange(actor,donor),"nearby giant target uses physical proxy for line of sight",passed);
            var tuner=new net.minecraft.world.item.ItemStack(dev.magnitude.content.Content.TUNER);var tuning=dev.magnitude.content.ToolItem.data(tuner);tuning.putInt("operation",4);tuning.putDouble("value",0.5);dev.magnitude.content.ToolItem.data(tuner,tuning);
            require(!((dev.magnitude.content.ToolItem)dev.magnitude.content.Content.TUNER).apply(tuner,actor,donor)&&Dimensions.target(actor)==1&&Dimensions.target(donor)==1073741824,"giant-to-small transfer refuses asymmetric float rounding atomically",passed);
            Dimensions.set(donor,1024,0);EntityState.of(actor).nextAction=0;tuning.putDouble("value",0.49999);dev.magnitude.content.ToolItem.data(tuner,tuning);
            require(((dev.magnitude.content.ToolItem)dev.magnitude.content.Content.TUNER).apply(tuner,actor,donor)&&Dimensions.target(actor)+Dimensions.target(donor)==1025,"representable giant-to-small transfer remains conserved",passed);
            Dimensions.set(actor,ScaleSafety.MINIMUM,0);Dimensions.set(actor,256,20);
            for(int i=0;i<25;i++)ScaleTypes.BASE.getScaleData(actor).tick();
            require(Dimensions.size(actor)==256&&actor.getBbHeight()<=14.401,"micro-to-giant transition completes with bounded collision",passed);
            actor.createCommandSourceStack().getServer().getCommands().getDispatcher().execute("magnitude random 0.000001 256 20",actor.createCommandSourceStack());
            require(EntityState.of(actor).randomLow==0.000001&&EntityState.of(actor).randomHigh==256,"random command accepts configured extreme range",passed);
            var randomOut=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());actor.saveWithoutId(randomOut);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),randomOut.buildResult()));
            require(EntityState.of(restored).randomLow==0.000001&&EntityState.of(restored).randomHigh==256,"extreme random range survives persistence",passed);
            EntityState.of(actor).randomPeriod=0;
            Dimensions.set(actor,8,0);actor.setPos(5000.5,220,5000.5);actor.setYRot(0);actor.setOnGround(true);EntityState.of(actor).terrainEnabled=true;
            Magnitude.settings.terrainDamage=true;Magnitude.settings.standingPressure=false;
            floor(actor,Blocks.STONE);
            int changed=Impact.feet(actor,0,false);
            require(changed>0&&level.getBlockState(CENTER.offset(-2,-1,0)).isAir()&&level.getBlockState(CENTER.offset(2,-1,0)).isAir(),"two boot contacts carve distinct footprints",passed);
            require(level.getBlockState(CENTER.offset(0,-1,0)).is(Blocks.STONE)&&level.getBlockState(CENTER.offset(3,-1,3)).is(Blocks.STONE),"footprints preserve gap and square corners",passed);
            floor(actor,Blocks.STONE);actor.setYRot(90);Impact.feet(actor,0,false);
            require(level.getBlockState(CENTER.offset(0,-1,2)).isAir()&&level.getBlockState(CENTER.offset(0,-1,-2)).isAir()&&level.getBlockState(CENTER.offset(2,-1,0)).is(Blocks.STONE),"footprints rotate with player orientation",passed);
            actor.setYRot(0);floor(actor,Blocks.STONE);
            var state=EntityState.of(actor);state.initialized=true;state.grounded=true;state.previousSize=8;state.previousPosition=actor.position();state.strideDistance=0;actor.tickCount=1;
            tick(server,actor);require(Impact.remaining()==Magnitude.settings.blocksPerTick,"no walking footprint while stationary with pressure disabled",passed);
            actor.setPos(5000.5,220,5004.5);tick(server,actor);
            require(Impact.remaining()<Magnitude.settings.blocksPerTick,"walking uses actual displacement rather than server velocity",passed);
            actor.setPos(5000.5,220,5000.5);state.previousPosition=actor.position();floor(actor,Blocks.DIRT);Magnitude.settings.standingPressure=true;actor.tickCount=20;
            tick(server,actor);require(level.getBlockState(CENTER.offset(2,-1,0)).isAir(),"stationary scale 8 compresses soft terrain",passed);
            floor(actor,Blocks.STONE);tick(server,actor);require(level.getBlockState(CENTER.offset(2,-1,0)).is(Blocks.STONE),"moderate static load preserves stronger stone",passed);
            Dimensions.set(actor,32,0);state.previousSize=32;floor(actor,Blocks.STONE);tick(server,actor);
            for(int i=0;i<20 && !state.contacts.footprints.isEmpty();i++){Impact.beginTick();Impact.continueFeet(actor);}
            require(level.getBlockState(CENTER.offset(2,-1,0)).isAir(),"greater stationary load breaks stone",passed);
            floor(actor,Blocks.DIRT);actor.getAbilities().flying=true;tick(server,actor);
            require(Impact.remaining()==Magnitude.settings.blocksPerTick,"flying player does not apply standing pressure",passed);actor.getAbilities().flying=false;
            Dimensions.set(actor,8,0);state.previousSize=8;Magnitude.settings.standingPressure=false;floor(actor,Blocks.STONE);state.nextJumpImpact=0;
            actor.jumpFromGround();require(state.jumpImpact&&level.getBlockState(CENTER.offset(2,-1,0)).isAir(),"ordinary vanilla jump hook breaks ground on takeoff",passed);
            floor(actor,Blocks.STONE);actor.jumpFromGround();require(Impact.remaining()==Magnitude.settings.blocksPerTick,"repeated same-tick jump hooks share cooldown",passed);
            actor.setOnGround(false);actor.setDeltaMovement(new Vec3(0,-0.42,0));tick(server,actor);actor.setOnGround(true);floor(actor,Blocks.STONE);tick(server,actor);
            require(!state.jumpImpact&&level.getBlockState(BlockPos.containing(dev.magnitude.physics.PlayerBody.foot(actor,1))).isAir(),"normal jump landing produces impact without manual stomp",passed);
            state.contacts.takeoffTick = Long.MIN_VALUE;floor(actor,Blocks.STONE);state.nextJumpImpact=0;state.jumpImpact=false;actor.setOnGround(true);actor.setDeltaMovement(Vec3.ZERO);
            actor.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            actor.connection.resetPosition();
            level.addNewPlayer(actor);attached=true;
            actor.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(actor.getX(),actor.getY()+0.42,actor.getZ(),false,false));
            require(state.jumpImpact&&Impact.remaining()<Magnitude.settings.blocksPerTick,"ordinary movement packet invokes jump terrain effect",passed);
            actor.setPos(5000.5,220,5000.5);actor.setOnGround(true);state.jumpImpact=false;
            floor(actor,Blocks.BEDROCK);require(Impact.feet(actor,0,false)==0,"foot contacts protect unbreakable terrain",passed);
            floor(actor,Blocks.CHEST);require(Impact.feet(actor,0,false)==0,"foot contacts preserve containers",passed);
            floor(actor,Blocks.STONE);veto=true;require(Impact.feet(actor,0,false)==0,"foot contacts respect Fabric protection veto",passed);veto=false;
            floor(actor,Blocks.STONE);EntityState.of(actor).terrainEnabled=false;require(Impact.feet(actor,0,false)==0,"foot contacts require personal terrain opt-in",passed);EntityState.of(actor).terrainEnabled=true;
            Magnitude.settings.blocksPerTick=3;Magnitude.settings.blocksPerImpact=2;Magnitude.settings.checksPerTick=16;floor(actor,Blocks.STONE);
            int first=Impact.feet(actor,0,false),second=Impact.feet(actor,0,false);
            require(first<=2&&first+second<=3&&Impact.remaining()==0,"feet share global and per-impact mutation budgets",passed);
            Magnitude.settings.blocksPerTick=256;Magnitude.settings.blocksPerImpact=64;Magnitude.settings.checksPerTick=1;floor(actor,Blocks.STONE);Impact.feet(actor,0,false);
            require(Impact.checksRemaining()==0&&Impact.remaining()>=255,"foot inspection stops at exhausted global check budget",passed);
            Magnitude.settings.checksPerTick=2048;Magnitude.settings.blocksPerTick=256;floor(actor,Blocks.STONE);Dimensions.set(actor,ScaleSafety.MAXIMUM,0);
            AABB region=new AABB(4992,218,4992,5009,238,5009);int before=level.getEntitiesOfClass(Entity.class,region).size();
            for(int i=0;i<1000;i++){Impact.feet(actor,0,false);Impact.breakAround(actor,actor.position().add(0,-0.5,0),8,2);}
            require(level.getEntitiesOfClass(Entity.class,region).size()==before,"thousands of giant impacts create no debris or item entities",passed);
            require(Impact.remaining()>=0&&Impact.checksRemaining()>=0,"giant impact stress cannot underflow shared quotas",passed);
            Dimensions.reset(actor);require(actor.getBbHeight()<2&&Dimensions.size(actor)==1,"reset removes extreme dimensions",passed);
        } catch(Exception error) {throw new RuntimeException(error);} finally {veto=false;actor.stopRiding();if(attached)level.removePlayerImmediately(actor,Entity.RemovalReason.DISCARDED);Magnitude.settings=original;Impact.beginTick();}
    }
}
