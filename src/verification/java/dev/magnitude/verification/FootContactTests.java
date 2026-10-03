package dev.magnitude.verification;

import dev.magnitude.core.*;
import dev.magnitude.physics.*;
import dev.magnitude.Magnitude;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.function.Consumer;

/** Actual voxel shapes and unloaded/denied queries, not mocks of the traversal implementation. */
public final class FootContactTests {
    private static void require(boolean value,String label,Consumer<String> passed){if(!value)throw new AssertionError(label);passed.accept(label);}
    public static void run(MinecraftServer server,Consumer<String> passed){
        var level=server.overworld();var root=new BlockPos(14000,200,14000);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(root.offset(x*16,0,z*16));
        for(var pos:BlockPos.betweenClosed(root.offset(-5,-3,-5),root.offset(5,4,5)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        var actor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"FootContacts"),ClientInformation.createDefault());
        var sole=new Vec3(root.getX()+.5,200,root.getZ()+.5);actor.setPos(sole);
        try {
            level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),2);PhysicsWork.beginTick();
            var full=FootContacts.query(actor,sole,.4,.4,0,.0625,.0625);
            require(full.supported()&&Math.abs(full.area()-.64)<1e-8&&full.height()==200,"surface query measures rectangular area on full block",passed);
            PhysicsWork.beginTick();
            var tiny=FootContacts.query(actor,sole,1e-7,1e-7,0,.0625,.0625);
            require(tiny.supported()&&Math.abs(tiny.area()/4e-14-1)<1e-7,"tiny sole retains positive contact area without fixed world-area cutoff",passed);
            level.setBlock(root.below(),Blocks.STONE_SLAB.defaultBlockState(),2);PhysicsWork.beginTick();
            var slab=FootContacts.query(actor,sole.add(0,-.5,0),.4,.4,.4,.0625,.0625);
            require(slab.supported()&&slab.height()==199.5,"surface query uses partial slab collision height",passed);
            PhysicsWork.beginTick();
            require(!FootContacts.query(actor,sole,.4,.4,0,.0625,.0625).supported(),"lower slab is not a hidden support at old root height",passed);
            level.setBlock(root.below(),Blocks.OAK_FENCE.defaultBlockState(),2);PhysicsWork.beginTick();
            var fence=FootContacts.query(actor,sole.add(0,.5,0),.4,.4,0,.0625,.0625);
            require(fence.supported()&&fence.height()==200.5&&fence.area()>0&&fence.area()<.64,"surface query includes fences extending above their block cell",passed);
            level.setBlock(root.below(),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(root.offset(1,-1,0),Blocks.STONE.defaultBlockState(),2);PhysicsWork.beginTick();
            var edge=FootContacts.query(actor,sole,.8,.4,0,.0625,.0625);
            require(edge.supported()&&Math.abs(edge.area()-.24)<1e-8&&level.getBlockState(root.below()).isAir(),"contact area detects sole edge support when center is over air",passed);
            level.setBlock(root.offset(1,-1,0),Blocks.AIR.defaultBlockState(),2);PhysicsWork.beginTick();
            var empty=FootContacts.query(actor,sole,.8,.4,0,.0625,.0625);
            require(empty.complete()&&!empty.supported()&&empty.area()==0,"loaded cavity is known empty and cannot manufacture floor",passed);
            PhysicsWork.beginTick();PhysicsWork.cells(PhysicsWork.cellsRemaining());
            var denied=FootContacts.query(actor,sole,.4,.4,0,.0625,.0625);
            require(!denied.complete()&&!denied.supported()&&denied.reason().equals("surface traversal budget"),"exhausted surface budget reports unknown rather than empty support",passed);
            PhysicsWork.beginTick();
            var far=new Vec3(1200000.5,200,1200000.5);
            var unknown=FootContacts.query(actor,far,.4,.4,0,.0625,.0625);
            require(!unknown.complete()&&!unknown.supported()&&!level.hasChunkAt(BlockPos.containing(far)),"unloaded sole does not force chunks or prove empty support",passed);
            PhysicsWork.beginTick();
            require(!FootContacts.query(actor,sole,1000000,1000000,0,1,1).complete(),"extreme sole preserves real size and rejects oversized query envelope",passed);
            PhysicsWork.beginTick();
            require(!FootContacts.query(actor,new Vec3(0,1e30,0),.4,.4,0,1,1).complete(),"out-of-world surface height rejected before integer traversal",passed);
            level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),2);PhysicsWork.beginTick();
            var before=FootContacts.query(actor,sole,.4,.4,0,.0625,.0625);
            level.setBlock(root.below(),Blocks.AIR.defaultBlockState(),2);PhysicsWork.beginTick();
            var after=FootContacts.query(actor,sole,.4,.4,0,.0625,.0625);
            require(before.revision()!=after.revision()&&!after.supported(),"external support removal changes contact revision and loses actual support",passed);
            for(double width:new double[]{.251,.499,.75,1.031}) {
                PhysicsWork.beginTick();
                require(FootContacts.query(actor,sole,width,width,.37,.0625,.0625).halfWidth()==width,"surface query preserves continuous fractional sole geometry",passed);
            }
        }finally{actor.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);PhysicsWork.beginTick();}
    }
}
