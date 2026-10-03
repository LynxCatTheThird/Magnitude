package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.Magnitude;
import dev.magnitude.core.*;
import dev.magnitude.content.*;
import dev.magnitude.interaction.*;
import dev.magnitude.physics.*;
import dev.magnitude.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import java.util.*;
import java.util.function.Consumer;

/** Real server players and blocks exercising the production shared-budget service order. */
public final class SharedTerrainBudgetTests {
    private static void check(boolean value,String label,Consumer<String> passed){if(!value)throw new AssertionError(label);passed.accept(label);}
    private static void ready(){PhysicsWork.beginTick();Impact.beginTick();EntityQueries.beginTick();}
    public static void run(MinecraftServer server,Consumer<String> passed){
        var level=server.overworld();var data=(net.minecraft.world.level.storage.ServerLevelData)level.getLevelData();
        long originalTime=level.getGameTime();var old=Magnitude.settings;var players=new ArrayList<ServerPlayer>();
        Magnitude.settings=new Settings();Magnitude.settings.maximum=100;Magnitude.settings.terrainDamage=true;
        Magnitude.settings.shallowDeformation=true;Magnitude.settings.standingPressure=false;
        Magnitude.settings.blocksPerTick=2;Magnitude.settings.blocksPerImpact=2;Magnitude.settings.checksPerTick=3;
        var before=Blocks.DIRT.defaultBlockState();var after=WorldContent.COMPACTED_DIRT.defaultBlockState().setValue(CompactedSoilBlock.HEIGHT,12);
        var root=new BlockPos(27000,199,27000);
        try{
            for(int n=0;n<3;n++){
                var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"BudgetWriter"+n),ClientInformation.createDefault());
                p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
                p.getAbilities().mayBuild=true;p.setNoGravity(true);p.setPos(root.getX()+n*32+.5,220,root.getZ()+.5);
                Dimensions.set(p,new double[]{5,49.875,50}[n],0);players.add(p);
                var state=EntityState.of(p);state.terrainEnabled=true;
                for(int k=0;k<12;k++){
                    var pos=root.offset(n*32,0,k);level.getChunk(pos);level.setBlock(pos,before,2);level.setBlock(pos.above(),Blocks.SHORT_GRASS.defaultBlockState(),2);
                    state.contacts.soil.put(pos,new SoilDeformation.Work(level.dimension(),Dimensions.snapshot(p).base(),false,before,after,originalTime+100));
                }
            }
            server.getPlayerList().getPlayers().addAll(players);
            for(int tick=0;tick<36;tick++){
                data.setGameTime(originalTime+tick);ready();for(var p:players)EntityState.of(p).contacts.sampleTick=level.getGameTime();
                Interactions.tick(server);
                int writes=Magnitude.settings.blocksPerTick-Impact.remaining();
                if(writes!=2)throw new AssertionError("shared compound write budget: "+writes+" at "+tick);
                for(int n=0;n<3;n++)for(int k=0;k<12;k++){
                    var pos=root.offset(n*32,0,k);boolean compacted=level.getBlockState(pos)==after;
                    if(compacted!=level.getBlockState(pos.above()).isAir())throw new AssertionError("partially committed plant and soil in shared service order");
                }
                if(tick==2)check(players.stream().allMatch(p->EntityState.of(p).contacts.soil.size()==11),"three competing writers each make progress within one rotation at two shared writes per tick",passed);
            }
            check(players.stream().allMatch(p->EntityState.of(p).contacts.soil.isEmpty()),"all shared-budget vegetation queues complete without starvation or compound half-writes",passed);
            server.getPlayerList().getPlayers().removeAll(players);
            var p=players.getFirst();var state=EntityState.of(p);var pos=root.offset(0,0,20);level.setBlock(pos,before,2);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
            var work=new SoilDeformation.Work(level.dimension(),Dimensions.snapshot(p).base(),false,before,after,level.getGameTime()+100);
            state.contacts.soil.put(pos,work);ready();PhysicsWork.cells(PhysicsWork.cellsRemaining());
            check(SoilDeformation.continueWork(p)==0&&state.contacts.soil.get(pos)==work&&level.getBlockState(pos)==before,"exhausted shared world-query budget preserves loaded soil work without a write",passed);
            ready();check(SoilDeformation.continueWork(p)==1&&state.contacts.soil.isEmpty()&&level.getBlockState(pos)==after,"retained soil work resumes after the shared query budget resets",passed);
            var unknown=new ArrayList<BlockPos>();
            for(int n=0;n<8;n++){
                var unloaded=new BlockPos(310000+n*32,199,310000);if(level.hasChunkAt(unloaded))throw new AssertionError("unknown-space fixture unexpectedly loaded");
                unknown.add(unloaded);state.contacts.soil.put(unloaded,work);
            }
            ready();int cells=PhysicsWork.cellsRemaining();
            check(SoilDeformation.continueWork(p)==0&&state.contacts.soil.size()==8&&cells-PhysicsWork.cellsRemaining()==8,
                "unloaded soil queues visit each candidate at most once and charge the shared world-query budget",passed);
            ready();PhysicsWork.cells(PhysicsWork.cellsRemaining()-3);
            check(SoilDeformation.continueWork(p)==0&&state.contacts.soil.size()==8&&PhysicsWork.cellsRemaining()==0,
                "unknown-space queue stops at the remaining shared query quota without dropping candidates",passed);
            check(unknown.stream().noneMatch(level::hasChunkAt),"retrying unknown soil work never force-loads its chunks",passed);
            state.contacts.soil.clear();
            level.setBlock(pos,before,2);state.contacts.soil.put(pos,new SoilDeformation.Work(level.dimension(),Dimensions.snapshot(p).base(),false,before,after,level.getGameTime()-1));ready();
            check(SoilDeformation.continueWork(p)==0&&state.contacts.soil.isEmpty()&&level.getBlockState(pos)==before,"expired queued compaction is discarded without world mutation",passed);
            state.contacts.soil.put(pos,work);Dimensions.set(p,4.625,0);ready();
            check(SoilDeformation.continueWork(p)==0&&state.contacts.soil.isEmpty()&&level.getBlockState(pos)==before,"continuous resize invalidates queued work from the old load",passed);
            Dimensions.set(p,5,0);state.contacts.soil.put(pos,work);state.terrainEnabled=false;ready();
            check(SoilDeformation.continueWork(p)==0&&state.contacts.soil.isEmpty()&&level.getBlockState(pos)==before,"revoking terrain permission clears pending work despite restored budgets",passed);
        }finally{
            server.getPlayerList().getPlayers().removeAll(players);for(var p:players)p.remove(Entity.RemovalReason.DISCARDED);
            data.setGameTime(originalTime);Magnitude.settings=old;ready();
        }
    }
}
