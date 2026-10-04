package dev.magnitude.verification;

import com.mojang.authlib.GameProfile;
import dev.magnitude.core.EntityState;
import dev.magnitude.physics.*;
import dev.magnitude.interaction.EntityQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.function.Consumer;

/** Compare actual voxel reads and full collision sets, including section boundaries. */
public final class SparseObstacleTests {
    public static boolean reading,mutateFromShape;
    public static long reads;
    public static final BlockPos ROOT=new BlockPos(128000,208,128000);
    public static BlockPos mutation;
    private static void ready(ServerPlayer player){
        PhysicsWork.beginTick();EntityQueries.beginTick();EntityState.of(player).physicsTick=Long.MIN_VALUE;
        WorldObstacles.prepare(player);
    }
    private static void check(boolean ok,String label,Consumer<String> passed){if(!ok)throw new AssertionError(label);passed.accept(label);}
    public static void run(MinecraftServer server,Consumer<String> passed){
        var level=server.overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(ROOT.offset(x*16,0,z*16));
        for(var p:BlockPos.betweenClosed(ROOT.offset(-8,-16,-8),ROOT.offset(24,120,24)))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"SparseQuery"),ClientInformation.createDefault());
        player.setPos(ROOT.getX()+.5,ROOT.getY(),ROOT.getZ()+.5);
        try{
            var air=new AABB(128001,224,128001,128009,304,128009);
            reads=0;reading=true;ready(player);
            var sparse=WorldObstacles.query(player,List.of(air),false);long sparseReads=reads;
            reads=0;for(var shape:level.getBlockCollisions(player,air))shape.toAabbs();long originalReads=reads;reading=false;
            check(sparse.complete()&&sparse.boxes().isEmpty()&&sparseReads<originalReads/10
                &&EntityState.of(player).physicsCells<LocalProxy.cells(air)/10,
                "loaded empty sections eliminate over ninety percent of actual block reads and charged work",passed);
            check(EntityState.of(player).physicsCells>0,"empty section metadata still consumes the shared world budget",passed);
            var boat=net.minecraft.world.entity.EntityTypes.OAK_BOAT.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            boat.setPos(128004,240,128004);level.addFreshEntity(boat);
            try{
                ready(player);var withEntity=WorldObstacles.query(player,List.of(air),true);
                check(player.canCollideWith(boat)&&withEntity.complete()&&withEntity.boxes().contains(boat.getBoundingBox()),
                    "empty block sections retain collidable entity obstacles",passed);
            }finally{boat.discard();}
            var border=level.getWorldBorder();double borderX=border.getCenterX(),borderZ=border.getCenterZ(),borderSize=border.getSize();
            var previous=player.position();
            try{
                border.setCenter(128004,128004);border.setSize(8);player.setPos(128007.75,240,128004);
                ready(player);var edge=new AABB(128007,239,128003,128009,242,128005);
                var expected=border.getCollisionShape().toAabbs();var actual=WorldObstacles.query(player,List.of(edge),false);
                check(!expected.isEmpty()&&actual.complete()&&actual.boxes().containsAll(expected),
                    "empty block sections retain world border collision shapes",passed);
            }finally{border.setCenter(borderX,borderZ);border.setSize(borderSize);player.setPos(previous);}
            ready(player);PhysicsWork.cells(PhysicsWork.cellsRemaining());
            check(!WorldObstacles.query(player,List.of(air),false).complete(),"empty sections cannot bypass an exhausted shared budget",passed);
            level.setBlock(ROOT,Blocks.STONE.defaultBlockState(),2);
            level.setBlock(ROOT.offset(3,7,2),Blocks.OAK_FENCE.defaultBlockState(),2);
            level.setBlock(ROOT.offset(5,15,4),Blocks.STONE_SLAB.defaultBlockState(),2);
            level.setBlock(ROOT.offset(4,32,4),Blocks.GLASS_PANE.defaultBlockState(),2);
            level.setBlock(ROOT.offset(7,47,3),Blocks.SCAFFOLDING.defaultBlockState(),2);
            level.setBlock(ROOT.offset(2,72,6),Blocks.CACTUS.defaultBlockState(),2);
            level.setBlock(new BlockPos(128003,level.getMaxY(),128003),Blocks.STONE.defaultBlockState(),2);
            var random=new Random(20261004);
            boolean equivalent=true,accounted=true;
            for(int i=0;i<120;i++){
                double x=128000+random.nextDouble()*7,z=128000+random.nextDouble()*7,y=206+random.nextDouble()*108;
                var box=new AABB(x,y,z,x+1+random.nextDouble()*7,Math.min(321,y+1+random.nextDouble()*72),z+1+random.nextDouble()*7);
                var expected=new LinkedHashSet<AABB>();for(var shape:level.getBlockCollisions(player,box))expected.addAll(shape.toAabbs());
                ready(player);reads=0;reading=true;
                var actual=WorldObstacles.query(player,List.of(box),false);reading=false;
                equivalent&=actual.complete()&&new LinkedHashSet<>(actual.boxes()).equals(expected);
                accounted&=reads<=EntityState.of(player).physicsCells;
            }
            check(equivalent,"sparse collision sets match native traversal in one hundred twenty random boundary and context-dependent fixtures",passed);
            check(accounted,"actual voxel reads remain within charged sparse work",passed);
            ready(player);
            var top=new AABB(128002,level.getMaxY(),128002,128005,level.getMaxY()+2,128005);
            check(WorldObstacles.query(player,List.of(top),false).boxes().contains(new AABB(128003,level.getMaxY(),128003,128004,level.getMaxY()+1,128004)),
                "inclusive highest world section is queried rather than treated as air",passed);
            ready(player);var updated=ROOT.offset(4,64,4);level.setBlock(updated,Blocks.STONE.defaultBlockState(),2);
            check(!WorldObstacles.query(player,List.of(air),false).boxes().isEmpty(),"a formerly empty section is re-read after external block insertion",passed);
            level.setBlock(updated,Blocks.AIR.defaultBlockState(),2);
            ready(player);mutation=ROOT.offset(2,64,2);mutateFromShape=true;
            var changing=new AABB(128000,208,128000,128008,304,128008);
            var changed=WorldObstacles.query(player,List.of(changing),false);
            check(!mutateFromShape&&!changed.complete()&&level.getBlockState(mutation).is(Blocks.STONE),
                "a shape callback changing a skipped section invalidates the complete collision query",passed);
            level.setBlock(mutation,Blocks.AIR.defaultBlockState(),2);player.setPos(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+.5);
            ready(player);check(FootContacts.supportedAt(player,net.minecraft.world.phys.Vec3.ZERO),
                "sole validation fixture has actual contact before a world mutation",passed);
            ready(player);mutateFromShape=true;
            check(!FootContacts.supportedAt(player,net.minecraft.world.phys.Vec3.ZERO)&&!mutateFromShape,
                "sole validation rejects world changes during collision shape callbacks",passed);
            ready(player);var unknown=new AABB(420000,224,420000,420008,304,420008);
            check(!WorldObstacles.query(player,List.of(unknown),false).complete()&&!level.hasChunkAt(BlockPos.containing(unknown.getCenter())),
                "unloaded empty-looking regions stay unknown without loading chunks",passed);
        }finally{
            reading=mutateFromShape=false;
            for(var position:List.of(ROOT,ROOT.offset(3,7,2),ROOT.offset(5,15,4),ROOT.offset(4,32,4),ROOT.offset(7,47,3),ROOT.offset(2,72,6),ROOT.offset(4,64,4),ROOT.offset(2,64,2),new BlockPos(128003,level.getMaxY(),128003)))
                level.setBlock(position,Blocks.AIR.defaultBlockState(),2);
            PhysicsWork.beginTick();EntityQueries.beginTick();
        }
    }
}
