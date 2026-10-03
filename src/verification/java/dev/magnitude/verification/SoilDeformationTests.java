package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.content.*;
import dev.magnitude.physics.*;
import dev.magnitude.terrain.*;
import dev.magnitude.interaction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

public final class SoilDeformationTests {
    private static boolean veto,withdrawConsent;
    private static void check(boolean value,String label,Consumer<String> passed){if(!value)throw new AssertionError(label);passed.accept(label);}
    private static void ready(ServerPlayer p){PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();EntityState.of(p).physicsTick=Long.MIN_VALUE;EntityState.of(p).contacts.sampleTick=Long.MIN_VALUE;}
    public static void run(MinecraftServer server,Consumer<String> passed){
        var old=Magnitude.settings;Magnitude.settings=new Settings();Magnitude.settings.maximum=1000;
        Magnitude.settings.terrainDamage=true;Magnitude.settings.shallowDeformation=true;
        var level=server.overworld();var root=new BlockPos(18000,200,18000);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(root.offset(x*16,0,z*16));
        for(var pos:BlockPos.betweenClosed(root.offset(-8,-3,-8),root.offset(8,12,8)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        for(var pos:BlockPos.betweenClosed(root.offset(-8,-1,-8),root.offset(8,-1,8)))level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
        var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"SoilDeformation"),ClientInformation.createDefault());
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        p.setPos(18000.25,200,18000.5);p.setYRot(0);p.setOnGround(true);Dimensions.set(p,5,0);
        var state=EntityState.of(p);state.terrainEnabled=true;
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,actor,pos,block,entity)->{if(actor==p&&withdrawConsent)state.terrainEnabled=false;return actor!=p||!veto;});
        try {
            int height=SoilMaterials.equilibrium(5,1,0);ready(p);ContactEvents.sample(p);
            var left=BlockPos.containing(PlayerBody.foot(p,-1));var right=BlockPos.containing(PlayerBody.foot(p,1));
            check(level.getBlockState(left).is(WorldContent.COMPACTED_DIRT)&&level.getBlockState(right).is(WorldContent.COMPACTED_DIRT),"authoritative standing event compacts both soil soles",passed);
            check(SoilMaterials.height(level.getBlockState(left))==height&&Math.abs(p.getY()-(199+height/16d))<1e-6,"soil state height and actual body settlement share collision",passed);
            var surface=level.getBlockState(left);double settled=p.getY();
            for(int i=0;i<60;i++){ready(p);SoilDeformation.continueWork(p);ContactEvents.sample(p);p.move(MoverType.SELF,new Vec3(0,-.05,0));}
            check(level.getBlockState(left)==surface&&Math.abs(p.getY()-settled)<1e-6&&state.contacts.soil.isEmpty(),"long standing reaches finite equilibrium without successive excavation",passed);
            ready(p);ContactEvents.emit(p,ContactEvent.Type.LANDING,Vec3.ZERO,1,SupportSnapshot.capture(p));
            check(!level.getBlockState(left).isAir()&&SoilMaterials.height(level.getBlockState(left))<=height,"repeated landing retains material history and finite capacity",passed);
            Magnitude.settings.shallowDeformation=false;ready(p);SoilDeformation.continueWork(p);
            check(level.getBlockState(left)==surface,"disabling deformation preserves saved terrain collision state",passed);
            Magnitude.settings.shallowDeformation=true;
            var serialized=net.minecraft.nbt.NbtUtils.writeBlockState(surface);
            var restored=net.minecraft.nbt.NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),serialized);
            check(restored==surface,"native block-state persistence round trip retains compacted height",passed);
            var drops=net.minecraft.world.level.block.Block.getDrops(surface,level,left,null,p,new ItemStack(Items.IRON_SHOVEL));
            check(drops.size()==1&&drops.getFirst().is(Items.DIRT)&&drops.getFirst().getCount()==1,"compacted dirt keeps one original material drop",passed);
            var grass=WorldContent.COMPACTED_GRASS.defaultBlockState().setValue(CompactedSoilBlock.HEIGHT,10);
            drops=net.minecraft.world.level.block.Block.getDrops(grass,level,left,null,p,new ItemStack(Items.IRON_SHOVEL));
            check(drops.size()==1&&drops.getFirst().is(Items.DIRT),"compacted grass uses original ordinary dirt drop",passed);
            check(grass.getCollisionShape(level,left).max(net.minecraft.core.Direction.Axis.Y)==10/16d,"native shape cache supplies exact partial-height collision",passed);
            check(!surface.hasBlockEntity()&&surface.getBlock().getStateDefinition().getPossibleStates().size()==18,"height backend uses finite native states without block entities",passed);
            var protectedPos=root.offset(4,-1,0);level.setBlock(protectedPos,Blocks.DIRT.defaultBlockState(),2);ready(p);veto=true;
            check(!Impact.compactSoil(p,protectedPos,Blocks.DIRT.defaultBlockState(),surface)&&level.getBlockState(protectedPos).is(Blocks.DIRT),"soil replacement obeys third-party protection veto",passed);veto=false;
            withdrawConsent=true;ready(p);
            check(!Impact.compactSoil(p,protectedPos,Blocks.DIRT.defaultBlockState(),surface)&&level.getBlockState(protectedPos).is(Blocks.DIRT),"permission withdrawn by protection callback prevents soil write",passed);
            withdrawConsent=false;state.terrainEnabled=false;ready(p);
            check(!Impact.compactSoil(p,protectedPos,Blocks.DIRT.defaultBlockState(),surface),"soil replacement requires personal terrain permission",passed);state.terrainEnabled=true;
            ready(p);check(!Impact.compactSoil(p,protectedPos,Blocks.GRASS_BLOCK.defaultBlockState(),surface),"soil queue refuses stale surface state",passed);
            check(SoilMaterials.compacted(Blocks.CHEST.defaultBlockState())==null&&SoilMaterials.compacted(Blocks.STONE.defaultBlockState())==null,"unknown hard material and containers are not replaced by soil",passed);
            level.setBlock(protectedPos,surface,2);
            check(((CompactedSoilBlock)surface.getBlock()).placeLiquid(level,protectedPos,surface,net.minecraft.world.level.material.Fluids.WATER.getSource(false))
                &&!level.getBlockState(protectedPos).getFluidState().isEmpty(),"actual water placement fills shallow native soil depression",passed);
            check(!((CompactedSoilBlock)surface.getBlock()).placeLiquid(level,protectedPos,surface.setValue(CompactedSoilBlock.HEIGHT,16),net.minecraft.world.level.material.Fluids.WATER.getSource(false)),
                "full-height soil cannot accept water without a depression",passed);
            var water=surface.setValue(CompactedSoilBlock.WATERLOGGED,true);level.setBlock(protectedPos,water,2);ready(p);
            check(!Impact.compactSoil(p,protectedPos,water,surface)&&!level.getBlockState(protectedPos).getFluidState().isEmpty(),"waterlogged depression retains water and forbids compaction writes",passed);
            level.setBlock(protectedPos,Blocks.DIRT.defaultBlockState(),2);level.setBlock(protectedPos.above(),Blocks.DANDELION.defaultBlockState(),2);ready(p);
            check(!Impact.compactSoil(p,protectedPos,Blocks.DIRT.defaultBlockState(),surface)&&level.getBlockState(protectedPos.above()).is(Blocks.DANDELION),
                "soil compaction cannot indirectly remove attached plants by neighbor updates",passed);
            level.setBlock(protectedPos.above(),Blocks.AIR.defaultBlockState(),2);ready(p);
            check(!Impact.compactSoil(p,protectedPos,Blocks.DIRT.defaultBlockState(),water),"soil mutation cannot manufacture water through replacement state",passed);
            var budgetPos=root.offset(5,-1,0);level.setBlock(budgetPos,Blocks.DIRT.defaultBlockState(),2);Magnitude.settings.blocksPerTick=0;ready(p);
            check(!Impact.compactSoil(p,budgetPos,Blocks.DIRT.defaultBlockState(),surface)&&level.getBlockState(budgetPos).is(Blocks.DIRT),"soil state changes share global write budget",passed);
            Magnitude.settings.blocksPerTick=2;Magnitude.settings.blocksPerImpact=2;
            for(var pos:BlockPos.betweenClosed(root.offset(-8,-1,-8),root.offset(8,-1,8)))level.setBlock(pos,Blocks.DIRT.defaultBlockState(),2);
            p.setPos(18000.25,200,18000.5);p.setOnGround(true);state.pose=BodyPose.IDLE;state.contacts=new ContactState();state.terrainEnabled=true;
            ready(p);ContactEvents.sample(p);
            check(state.contacts.soil.size()>0&&Impact.remaining()==0,"small write budget retains unfinished soil surface work",passed);
            for(int i=0;i<30;i++){ready(p);SoilDeformation.continueWork(p);}
            check(state.contacts.soil.isEmpty(),"bounded soil queue finishes valid surface cells across later ticks",passed);
            var leftArea=FootContacts.capture(p,-1);var rightArea=FootContacts.capture(p,1);
            check(leftArea.supported()&&rightArea.supported()&&Math.abs(leftArea.height()-rightArea.height())<1e-8,
                "completed sole compaction has uniform depth across both supporting surfaces",passed);
            Magnitude.settings.blocksPerTick=256;Magnitude.settings.blocksPerImpact=64;
            var second=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"SecondSoilLoad"),ClientInformation.createDefault());
            second.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),second,CommonListenerCookie.createInitial(second.getGameProfile(),false));
            second.setPos(18000.25,200,18000.5);second.setYRot(0);second.setOnGround(true);Dimensions.set(second,16,0);EntityState.of(second).terrainEnabled=true;
            try {
                ready(second);ContactEvents.sample(second);
                check(SoilMaterials.height(level.getBlockState(left))==SoilMaterials.equilibrium(16,1,0),"second larger player adds finite compression to shared world material",passed);
                var shared=level.getBlockState(left);p.setOnGround(true);ready(p);ContactEvents.sample(p);
                check(level.getBlockState(left)==shared,"smaller player cannot reset or refill shared compaction history",passed);
            }finally{second.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}
            check(SoilMaterials.equilibrium(49.75,1,0)==SoilMaterials.equilibrium(50.125,1,0)&&SoilMaterials.equilibrium(1,1,0)==16,"continuous pressure quantizes height without size categories",passed);
        }finally{veto=withdrawConsent=false;Magnitude.settings=old;p.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);PhysicsWork.beginTick();}
    }
}
