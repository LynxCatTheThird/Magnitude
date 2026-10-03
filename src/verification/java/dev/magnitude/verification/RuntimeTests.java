package dev.magnitude.verification;

import dev.magnitude.Magnitude;
import dev.magnitude.content.Content;
import dev.magnitude.content.ToolItem;
import dev.magnitude.content.WorldContent;
import dev.magnitude.content.EssenceFluid;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.interaction.Impact;
import dev.magnitude.interaction.Interactions;
import dev.magnitude.network.ActionPayload;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import virtuoel.pehkui.api.ScaleTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public final class RuntimeTests implements ModInitializer {
    private int countdown=-1;
    private final List<String> passed=new ArrayList<>();
    private void check(boolean value,String label) {if(!value)throw new AssertionError(label);passed.add(label);System.out.println("RUNTIME PASS: "+label);}
    private void close(MinecraftServer server,Throwable failure) {
        try {
            String body=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("passed",passed,"success",failure==null,"failure",failure==null?"":failure.toString()));
            Files.writeString(Path.of("results.json"),body);
        }catch(Exception error){throw new RuntimeException(error);}
        server.halt(false);
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> countdown=5);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(countdown<0||--countdown>0)return;
            if(server.overworld().getGameTime()%10!=0){countdown=1;return;}
            countdown=-1;
            try{CollisionPerformanceTests.run(label->check(true,label));RegressionTests.run(server,label->check(true,label));ScaleSnapshotTests.run(server,label->check(true,label));ScaleImpactTests.run(server,label->check(true,label));BodyPhysicsTests.run(server,label->check(true,label));ContactTests.run(server,label->check(true,label));run(server);if(Boolean.getBoolean("magnitude.verification.fail"))throw new AssertionError("Requested failure propagation probe");System.out.println("RUNTIME TESTS PASSED: "+passed.size());close(server,null);}
            catch(Throwable error){error.printStackTrace();close(server,error);throw new RuntimeException("Runtime verification failed",error);}
        });
    }
    private void run(MinecraftServer server)throws Exception {
        var level=server.overworld();
        Magnitude.settings=new dev.magnitude.core.Settings();
        BlockPos center=new BlockPos(1000,160,1000);level.getChunk(center);
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-6,-3,-6),center.offset(6,8,6)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        var pig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);pig.setPos(center.getX()+0.5,center.getY()+1,center.getZ()+0.5);pig.setNoAi(true);level.addFreshEntity(pig);
        check(Content.ITEMS.size()==19,"all 19 items registered");
        check(Content.POTIONS.size()==8,"8 potions registered");
        check(BuiltInRegistries.BLOCK.getKey(WorldContent.FIELD).equals(Magnitude.id("attunement_plate")),"field registry");
        double original=pig.getBbHeight();
        check(Dimensions.set(pig,2,0),"set server entity size");
        check(Math.abs(Dimensions.size(pig)-2)<1e-6,"effective size 2");
        check(Math.abs(pig.getBbHeight()-original*2)<0.0001,"real hitbox doubles");
        check(!Dimensions.set(pig,Double.NaN,0)&&!Dimensions.set(pig,Double.POSITIVE_INFINITY,0),"nonfinite runtime inputs rejected");
        Dimensions.set(pig,-20,0);check(Math.abs(Dimensions.size(pig)-1.0/64)<1e-8,"runtime minimum positive");
        Dimensions.set(pig,1000000,0);check(Dimensions.size(pig)==32,"runtime maximum capped");
        Dimensions.set(pig,1,0);Dimensions.set(pig,3,20);check(Dimensions.target(pig)==3,"transition target");
        for(int i=0;i<25;i++)ScaleTypes.BASE.getScaleData(pig).tick();
        check(Math.abs(Dimensions.size(pig)-3)<1e-5,"transition finishes");
        EntityState state=EntityState.of(pig);state.acceptResize=true;state.acceptCarry=true;state.randomPeriod=100;state.randomLow=0.25;state.randomHigh=4;
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());pig.saveWithoutId(output);
        var restored=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);restored.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
        check(Math.abs(Dimensions.size(restored)-3)<1e-5,"size survives entity save/load");
        check(EntityState.of(restored).acceptResize&&EntityState.of(restored).acceptCarry&&EntityState.of(restored).randomPeriod==100,"consent and random state survive save/load");
        Content.ENLARGE.value().applyInstantaneousEffect(level,null,null,pig,0,1);check(Dimensions.target(pig)==6,"enlargement factor");
        Dimensions.set(pig,6,0);Content.REDUCE.value().applyInstantaneousEffect(level,null,null,pig,0,1);check(Dimensions.target(pig)==3,"reduction inverse");
        Dimensions.set(pig,2,0);Content.ASCENT.value().applyEffectTick(level,pig,0);check(Dimensions.target(pig)>2,"continuous expansion");
        Dimensions.set(pig,2,0);Content.DESCENT.value().applyEffectTick(level,pig,0);check(Dimensions.target(pig)<2,"continuous contraction");
        var actor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"Verifier"),ClientInformation.createDefault());actor.setPos(center.getX()+0.5,center.getY()+1,center.getZ()-2);actor.getAbilities().mayBuild=true;
        Dimensions.set(actor,1,0);Dimensions.set(pig,2,0);
        ItemStack reservoir=new ItemStack(Content.RESERVOIR);
        check(((ToolItem)Content.RESERVOIR).apply(reservoir,actor,pig),"reservoir extraction");check(ToolItem.data(reservoir).getDoubleOr("charge",0)==0.9921875,"reservoir actual conserved charge");
        check(Math.abs(Dimensions.target(pig)+ToolItem.data(reservoir).getDoubleOr("charge",0)-2)<1e-6,"extract conservation");
        Dimensions.set(pig,Dimensions.target(pig),0);EntityState.of(actor).nextAction=0;
        check(((ToolItem)Content.RESERVOIR).apply(reservoir,actor,pig),"reservoir injection");check(Math.abs(Dimensions.target(pig)-2)<1e-6,"extract/inject roundtrip");
        var victim=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"Recipient"),ClientInformation.createDefault());victim.setPos(center.getX()+0.5,center.getY()+1,center.getZ()+2);
        check(!Dimensions.canChange(actor,victim),"player resize denied by default");EntityState.of(victim).acceptResize=true;check(Dimensions.canChange(actor,victim),"explicit consent enables resizing");
        victim.setPos(center.getX()+100,center.getY()+1,center.getZ()+100);check(!Dimensions.canChange(actor,victim),"remote target denied");
        Dimensions.set(pig,1,0);EntityState.of(pig).lastFluidTick=Long.MIN_VALUE;
        EssenceFluid.contact(level,pig,true);double target=Dimensions.target(pig);EssenceFluid.contact(level,pig,true);
        check(target>1&&target==Dimensions.target(pig),"multiple fluid cells apply once");
        check(WorldContent.AMBER_SOURCE.getSource()==WorldContent.AMBER_SOURCE&&WorldContent.AMBER_SOURCE.getFlowing()==WorldContent.AMBER_FLOW,"fluid source/flow linkage");
        check(WorldContent.AZURE_SOURCE.getBucket()==WorldContent.AZURE_BUCKET,"fluid bucket linkage");
        check(WorldContent.AMBER_SOURCE.defaultFluidState().createLegacyBlock().is(WorldContent.AMBER_POOL),"fluid legacy block");
        Magnitude.settings.terrainDamage=true;Magnitude.settings.blocksPerTick=8;Magnitude.settings.blocksPerImpact=64;Magnitude.settings.checksPerTick=2048;EntityState.of(actor).terrainEnabled=true;
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-4,-2,-4),center.offset(4,2,4)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(center,Blocks.BEDROCK.defaultBlockState(),3);level.setBlock(center.above(),Blocks.CHEST.defaultBlockState(),3);
        Impact.beginTick();int changed=Impact.breakAround(actor,Vec3.atCenterOf(center),3,2);check(changed<=8&&changed>0,"impact obeys global block quota");
        check(level.getBlockState(center).is(Blocks.BEDROCK),"protected bedrock preserved");check(level.getBlockState(center.above()).is(Blocks.CHEST),"container preserved");
        check(Impact.breakAround(actor,Vec3.atCenterOf(center),3,2)==0,"quota cannot be reused in same tick");
        check(level.getBlockState(center.offset(3,0,3)).is(Blocks.STONE),"square corner outside ellipsoid preserved");
        // The event veto must reject every terrain path.
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,block,entity)->false);Impact.beginTick();check(Impact.breakAround(actor,Vec3.atCenterOf(center),3,2)==0,"protection event veto honored");
        check(server.getCommands().getDispatcher().parse("magnitude admin set @e[type=minecraft:pig,limit=1] 2",server.createCommandSourceStack()).getExceptions().isEmpty(),"admin selector command parses");
        check(server.getCommands().getDispatcher().parse("magnitude consent carry true",actor.createCommandSourceStack()).getExceptions().isEmpty(),"separate carrying consent command");
        pig.discard();restored.discard();
    }
}
