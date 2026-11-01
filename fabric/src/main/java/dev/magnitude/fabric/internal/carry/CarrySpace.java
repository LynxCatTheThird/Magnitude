package dev.magnitude.fabric.internal.carry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded loaded-space checks for the first carry stage; no chunk loading or terrain writes. */
final class CarrySpace {
    enum Result { CLEAR, BLOCKED, UNKNOWN, BUDGET }
    private final MinecraftServer server;
    private int tick = Integer.MIN_VALUE;
    private long motionCells;
    private long recoveryCells;
    private final Map<ServerLevel, Boolean> entityAdmissions = new HashMap<>();
    CarrySpace(MinecraftServer server) { this.server = server; }
    private void beginTick() {
        if (tick != server.getTickCount()) {
            tick = server.getTickCount(); motionCells = 16384; recoveryCells = 16384;
            entityAdmissions.clear();
        }
    }
    static AABB boxAt(LivingEntity target, Vec3 root) {
        double half = target.getBbWidth() * .5;
        return new AABB(root.x - half, root.y, root.z - half,
                root.x + half, root.y + target.getBbHeight(), root.z + half);
    }
    Result check(ServerLevel level, LivingEntity target, Entity ignoredHost, AABB box, boolean recovery) {
        beginTick();
        if (!finite(box) || box.getXsize() > 16 || box.getYsize() > 16 || box.getZsize() > 16) return Result.BUDGET;
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)) return Result.BLOCKED;
        int minX = (int)Math.floor(box.minX - 1e-7) - 1, maxX = (int)Math.floor(box.maxX + 1e-7) + 1;
        int minY = (int)Math.floor(box.minY - 1e-7) - 1, maxY = (int)Math.floor(box.maxY + 1e-7) + 1;
        int minZ = (int)Math.floor(box.minZ - 1e-7) - 1, maxZ = (int)Math.floor(box.maxZ + 1e-7) + 1;
        long cells = ((long)maxX-minX+1)*((long)maxY-minY+1)*((long)maxZ-minZ+1);
        long cost=cells*2; // every sweep inspects collision plus fluid/non-colliding hazards
        if (cells <= 0 || cells > 2048 || cost > (recovery ? recoveryCells : motionCells)) return Result.BUDGET;
        if (recovery) recoveryCells -= cost; else motionCells -= cost;
        for (int x=minX>>4;x<=maxX>>4;x++) for(int z=minZ>>4;z<=maxZ>>4;z++) {
            if (!level.hasChunk(x,z)) return Result.UNKNOWN;
        }
        if (!entityAdmissions.computeIfAbsent(level, l -> {
            int n=0; for(Entity ignored:l.getAllEntities()) if(++n>512) return false; return true;
        })) return Result.BUDGET;
        if (level.getBlockCollisions(target,box).iterator().hasNext()) return Result.BLOCKED;
        {
            for(BlockPos pos:BlockPos.betweenClosed((int)Math.floor(box.minX+1e-7),(int)Math.floor(box.minY+1e-7),(int)Math.floor(box.minZ+1e-7),
                    (int)Math.floor(box.maxX-1e-7),(int)Math.floor(box.maxY-1e-7),(int)Math.floor(box.maxZ-1e-7))) {
                var state=level.getBlockState(pos);
                if(!state.getFluidState().isEmpty()||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)
                        ||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.WITHER_ROSE)
                        ||state.is(Blocks.CACTUS)||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CAMPFIRE)
                        ||state.is(Blocks.SOUL_CAMPFIRE))return Result.BLOCKED;
            }
        }
        var candidates=new ArrayList<Entity>();
        level.getEntities(EntityTypeTest.forClass(Entity.class),box.inflate(1e-7),e->true,candidates,129);
        if(candidates.size()>128) return Result.BUDGET;
        for(Entity other:candidates) {
            if(other!=target && other!=ignoredHost && !other.isSpectator() && target.canCollideWith(other)) return Result.BLOCKED;
        }
        return Result.CLEAR;
    }
    Result swept(ServerLevel level, LivingEntity target, Entity host, Vec3 from, Vec3 to) {
        if(from.distanceToSqr(to)>64) return Result.BLOCKED;
        return check(level,target,host,boxAt(target,from).expandTowards(to.subtract(from)),false);
    }
    Vec3 findGround(ServerLevel level, LivingEntity target, Entity ignoredHost, Vec3 around) {
        // Fixed candidate count and independent exit quota. Never searches the whole loaded world.
        int[] offsets={0,1,-1,2,-2};
        int examined=0;
        for(int dx:offsets) for(int dz:offsets) {
            if(++examined>13) return null;
            int x=(int)Math.floor(around.x)+dx,z=(int)Math.floor(around.z)+dz;
            if(!level.hasChunk(x>>4,z>>4)) continue;
            for(int dy=1;dy>=-4;dy--) {
                BlockPos floor=new BlockPos(x,(int)Math.floor(around.y)+dy-1,z);
                if(floor.getY()<level.getMinY() || floor.getY()>=level.getMaxY()) continue;
                var state=level.getBlockState(floor);
                if(!state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                        || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)) continue;
                var shape=state.getCollisionShape(level,floor);
                if(shape.isEmpty()) continue;
                double top=shape.max(net.minecraft.core.Direction.Axis.Y);
                if(!Double.isFinite(top)||top<=0||top>1)continue;
                Vec3 root=new Vec3(x+.5,floor.getY()+top,z+.5);
                AABB box=boxAt(target,root);
                Result clear=check(level,target,ignoredHost,box,true);
                if(clear==Result.BUDGET)return null;
                if(clear!=Result.CLEAR)continue;
                if(hasSupport(level,target,box)) return root;
            }
        }
        return null;
    }
    boolean groundedAt(ServerLevel level,LivingEntity target,Entity host,Vec3 root) {
        BlockPos below=BlockPos.containing(root.x,root.y-.01,root.z);
        if(!level.hasChunk(below.getX()>>4,below.getZ()>>4))return false;
        var state=level.getBlockState(below);
        if(!state.getFluidState().isEmpty()||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)
                ||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)||state.is(Blocks.POWDER_SNOW))return false;
        AABB box=boxAt(target,root);
        return check(level,target,host,box,true)==Result.CLEAR && hasSupport(level,target,box);
    }
    private boolean hasSupport(ServerLevel level,LivingEntity target,AABB box) {
        AABB lowered=box.move(0,-.03,0);
        long x=(long)Math.floor(lowered.maxX+1e-7)-(long)Math.floor(lowered.minX-1e-7)+3;
        long y=(long)Math.floor(lowered.maxY+1e-7)-(long)Math.floor(lowered.minY-1e-7)+3;
        long z=(long)Math.floor(lowered.maxZ+1e-7)-(long)Math.floor(lowered.minZ-1e-7)+3;
        long cells=x*y*z;
        if(cells<=0||cells>recoveryCells)return false;
        recoveryCells-=cells;
        return level.getBlockCollisions(target,lowered).iterator().hasNext();
    }
    private static boolean finite(AABB b) {
        return Double.isFinite(b.minX)&&Double.isFinite(b.minY)&&Double.isFinite(b.minZ)
                &&Double.isFinite(b.maxX)&&Double.isFinite(b.maxY)&&Double.isFinite(b.maxZ);
    }
}
