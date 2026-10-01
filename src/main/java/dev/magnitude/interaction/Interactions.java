package dev.magnitude.interaction;

import dev.magnitude.Magnitude;
import dev.magnitude.content.Content;
import dev.magnitude.content.ToolItem;
import dev.magnitude.core.Dimensions;
import dev.magnitude.core.EntityState;
import dev.magnitude.core.Rules;
import dev.magnitude.network.Messages;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class Interactions {
    private Interactions() {}
    public static void register() {
        Messages.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            release(handler.player, false);
            handler.player.stopRiding();
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof ServerPlayer player && EntityState.of(player).carrying) release(player, false);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            EntityState old = EntityState.of(oldPlayer), state = EntityState.of(player);
            state.acceptResize = old.acceptResize;
            state.acceptCarry = old.acceptCarry;
            state.carryPosition = old.carryPosition;
            if (alive || Magnitude.settings.keepSizeAfterDeath) Dimensions.set(player, Dimensions.size(oldPlayer), 0);
            else Dimensions.reset(player);
        });
    }
    public static boolean cooldown(ServerPlayer player, int ticks) {
        int now = player.level().getServer().getTickCount();
        EntityState state = EntityState.of(player);
        if (state.nextAction > now) return false;
        state.nextAction = now + Math.max(1, ticks);
        return true;
    }
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) { release(player, false); continue; }
            EntityState state = EntityState.of(player);
            double size = Dimensions.size(player);
            if (!Double.isFinite(size)) { Dimensions.reset(player); continue; }
            if (state.randomPeriod >= 20 && --state.nextRandom <= 0 && Magnitude.settings.allowSelfChange) {
                state.nextRandom = state.randomPeriod;
                double value = state.randomLow + player.getRandom().nextDouble() * (state.randomHigh - state.randomLow);
                Dimensions.set(player, value, 20);
            }
            if (state.carrying && player.getFirstPassenger() == null) { state.carrying = false; Messages.syncCarry(player); }
            if (state.carrying && player.getFirstPassenger() != null && !Rules.ratio(size, Dimensions.size(player.getFirstPassenger()), 2)) release(player, false);
            if (state.initialized) {
                if (size >= 4 && player.onGround() && !state.grounded && state.downward < -0.1) shock(player, Math.min(6, size * 0.3), true);
                if (size > state.previousSize + 0.05 && size >= 4) Impact.breakAround(player, player.position().add(0, Math.min(3, player.getBbHeight()/2), 0), Math.min(6, player.getBbWidth()/2), Math.min(6, player.getBbHeight()/2));
            }
            state.initialized = true;
            state.grounded = player.onGround();
            state.downward = player.getDeltaMovement().y;
            state.previousSize = size;
            if (player.tickCount % 10 == 0) {
                if (size >= 4 && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.0001) {
                    Impact.breakAround(player, player.position().add(0,-0.5,0), Math.min(4, player.getBbWidth()/2), 1);
                    if (Magnitude.settings.bodyDamage) damageSmall(player, player.getBoundingBox().inflate(0.1, 0.25, 0.1), 2);
                }
                if (size <= 0.25 && !player.onGround() && player.getDeltaMovement().y < -0.06 && (player.getMainHandItem().is(Content.GLIDER) || player.getOffhandItem().is(Content.GLIDER))) {
                    player.setDeltaMovement(player.getDeltaMovement().multiply(1.02,0,1.02).add(0,-0.06,0));
                    player.syncVelocity = true;
                    player.resetFallDistance();
                }
                if (size <= 0.25 && player.getItemBySlot(EquipmentSlot.FEET).is(Content.BOOTS)) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20, 0, false, false));
                if (size <= 0.25 && !player.getItemBySlot(EquipmentSlot.CHEST).is(Content.VEST)) {
                    var block = player.level().getBlockState(player.blockPosition());
                    if (block.is(Blocks.WITHER_ROSE) || block.is(Blocks.SWEET_BERRY_BUSH)) player.hurtServer(player.level(), player.damageSources().sweetBerryBush(), 1);
                }
            }
        }
    }
    public static LivingEntity aim(ServerPlayer player, double range) {
        double distance = Math.clamp(range, 1, 64);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1).scale(distance));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double allowed = start.distanceToSqr(block.getLocation());
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, start, end, player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1), e -> e instanceof LivingEntity && e != player && e.isAlive() && !e.isSpectator(), Math.min(distance*distance, allowed));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
    public static boolean toolUse(ServerPlayer player, LivingEntity target, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return stack.getItem() instanceof ToolItem tool && tool.interactLivingEntity(stack, player, target, hand) == InteractionResult.SUCCESS;
    }
    private static boolean within(ServerPlayer actor, Entity target, double range) {
        return actor.level() == target.level() && target.isAlive() && !target.isSpectator() && !actor.isSpectator() && target.getBoundingBox().distanceToSqr(actor.getEyePosition()) <= range * range && actor.hasLineOfSight(target);
    }
    public static boolean carry(ServerPlayer player, LivingEntity target) {
        double range = Math.min(16, Math.max(4, Dimensions.size(player)*2));
        if (target == player || !within(player,target,range) || !player.getAbilities().mayBuild || target.isPassenger() || !target.getPassengers().isEmpty() || !Rules.ratio(Dimensions.size(player), Dimensions.size(target), 2)) return false;
        if (target instanceof Player && !EntityState.of(target).acceptCarry) return false;
        if (player.getFirstPassenger() != null || !cooldown(player, 10)) return false;
        boolean result = target.startRiding(player, true, true);
        if (result) { EntityState.of(player).carrying = true; Messages.syncCarry(player); }
        return result;
    }
    public static boolean ride(ServerPlayer player, LivingEntity target) {
        if (!within(player,target,Math.min(16,Math.max(4,Dimensions.size(target)*2))) || !Rules.ratio(Dimensions.size(target), Dimensions.size(player), 4) || player.isPassenger() || !target.getPassengers().isEmpty()) return false;
        if (target instanceof Player && !EntityState.of(target).acceptCarry) return false;
        return cooldown(player,10) && player.startRiding(target,true,true);
    }
    public static void release(ServerPlayer player, boolean thrown) {
        Entity passenger = player.getFirstPassenger();
        if (passenger == null || !EntityState.of(player).carrying) return;
        Vec3 release = safeRelease(player,passenger);
        passenger.stopRiding();
        passenger.teleportTo(release.x,release.y,release.z);
        EntityState.of(player).carrying = false;
        Messages.syncCarry(player);
        if (thrown && player.getAbilities().mayBuild && (!(passenger instanceof Player) || EntityState.of(passenger).acceptCarry)) {
            passenger.setDeltaMovement(player.getLookAngle().scale(Math.clamp(Dimensions.size(player)/Math.max(0.25,Dimensions.size(passenger)),0.5,2)));
            passenger.syncVelocity = true;
        }
    }
    private static Vec3 safeRelease(ServerPlayer carrier, Entity passenger) {
        double radius = Math.min(16, carrier.getBbWidth()/2 + passenger.getBbWidth()/2 + 0.5);
        for(int i=0;i<8;i++) {
            double angle = i*Math.PI/4;
            Vec3 pos = carrier.position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            AABB box = passenger.getBoundingBox().move(pos.subtract(passenger.position()));
            if (carrier.level().getWorldBorder().isWithinBounds(box) && carrier.level().noCollision(passenger,box)) return pos;
        }
        return passenger.position();
    }
    public static boolean crush(ServerPlayer player, LivingEntity target) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !Magnitude.settings.bodyDamage || !within(player,target,Math.min(16,Math.max(4,Dimensions.size(player)*2))) || !Rules.ratio(Dimensions.size(player),Dimensions.size(target),4) || !canDamage(player,target) || !cooldown(player,20)) return false;
        float damage=(float)Math.clamp(Dimensions.size(player)/Math.max(0.25,Dimensions.size(target)),1,20);
        return target.hurtServer(player.level(),player.damageSources().playerAttack(player),damage);
    }
    public static boolean canDamage(ServerPlayer actor, LivingEntity target) {
        return actor != target && actor.getAbilities().mayBuild && !actor.isPassengerOfSameVehicle(target) && (!(target instanceof Player other) || actor.canHarmPlayer(other));
    }
    private static void damageSmall(ServerPlayer player, AABB box, float damage) {
        int count=0;
        for (Entity entity:player.level().getEntities(player,box)) {
            if (++count>32) break;
            if(entity instanceof LivingEntity living && canDamage(player,living) && Rules.ratio(Dimensions.size(player),Dimensions.size(living),4)) living.hurtServer(player.level(),player.damageSources().playerAttack(player),damage);
        }
    }
    public static boolean shock(ServerPlayer player,double radius,boolean landing) {
        if (Dimensions.size(player)<4 || !player.onGround()) return false;
        if (!landing && !cooldown(player,20)) return false;
        if (Magnitude.settings.bodyDamage) damageSmall(player, player.getBoundingBox().inflate(radius,0.75,radius), landing?4:2);
        Impact.breakAround(player,player.position().add(0,-0.5,0),radius,1.5);
        player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,player.getX(),player.getY()+0.1,player.getZ(),12,radius/2,0.1,radius/2,0.03);
        return true;
    }
    public static boolean blow(ServerPlayer player) {
        if (Dimensions.size(player)<2 || !cooldown(player,20)) return false;
        Vec3 forward=player.getLookAngle(); Vec3 origin=player.getEyePosition();
        double range=Math.min(24,Dimensions.size(player)*3);
        int count=0;
        for(Entity entity:player.level().getEntities(player,new AABB(origin,origin.add(forward.scale(range))).inflate(range*0.4))) {
            if(++count>64) break;
            Vec3 direction=entity.getBoundingBox().getCenter().subtract(origin);
            if(direction.lengthSqr()>range*range || direction.normalize().dot(forward)<0.7 || !Rules.ratio(Dimensions.size(player),Dimensions.size(entity),2) || !player.hasLineOfSight(entity)) continue;
            if(entity instanceof LivingEntity living && !canDamage(player,living)) continue;
            entity.setDeltaMovement(entity.getDeltaMovement().add(forward.scale(0.7)).add(0,0.15,0)); entity.syncVelocity=true;
        }
        Impact.breakAround(player,origin.add(forward.scale(Math.min(4,range))),Math.min(3,range/4),2);
        return true;
    }
    public static boolean ability(ServerPlayer player) {
        if (!(player.getVehicle() instanceof LivingEntity mount) || !cooldown(player,40)) return false;
        if(mount instanceof AbstractSkeleton) {
            Arrow arrow=new Arrow(player.level(),player,new ItemStack(Items.ARROW),null);
            arrow.setPos(player.getEyePosition());
            Vec3 direction=player.getLookAngle().scale(1.5); arrow.setDeltaMovement(direction); Dimensions.set(arrow,1,0);
            return player.level().addFreshEntity(arrow);
        }
        if(mount instanceof Wolf) { mount.setDeltaMovement(player.getLookAngle().multiply(1,0,1).scale(0.8).add(0,0.35,0)); mount.syncVelocity=true; return true; }
        if(mount.onGround()) { mount.setDeltaMovement(mount.getDeltaMovement().add(0,0.4,0)); mount.syncVelocity=true; return true; }
        return false;
    }
}
