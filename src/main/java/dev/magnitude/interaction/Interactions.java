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
            detach(handler.player);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            detach(entity);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> detach(entity));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            EntityState old = EntityState.of(oldPlayer), state = EntityState.of(player);
            detach(oldPlayer);
            state.copyPersistentFrom(old);
            if (alive || Magnitude.settings.keepSizeAfterDeath) virtuoel.pehkui.util.ScaleUtils.loadScale(player, oldPlayer);
            else Dimensions.reset(player);
        });
    }
    public static boolean cooldown(ServerPlayer player, int ticks) {
        long now = player.level().getGameTime();
        EntityState state = EntityState.of(player);
        if (state.nextAction > now) return false;
        state.nextAction = now + Math.max(1, ticks);
        return true;
    }
    public static boolean request(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        EntityState state=EntityState.of(player);
        long now=player.level().getGameTime();
        if (state.nextRequest>now || state.nextAction>now) return false;
        state.nextRequest=now+2;
        return true;
    }
    public static boolean action(ServerPlayer player, int action) {
        if (action<0 || action>5 || !request(player)) return false;
        return switch(action) {
            case 0 -> blow(player);
            case 1 -> shock(player,Math.min(Magnitude.settings.impactRadius,Dimensions.snapshot(player).base()*Magnitude.settings.impactScaleFactor),false);
            case 2,3 -> { if(!cooldown(player,10))yield false;release(player,action==3);yield true; }
            case 4 -> ability(player);
            case 5 -> { var target=aim(player,16);yield target!=null&&ride(player,target); }
            default -> false;
        };
    }
    public static boolean pickup(ServerPlayer player) {
        if (!request(player)) return false;
        var target=aim(player,16);
        return target!=null&&carry(player,target);
    }
    public static void detach(Entity entity) {
        if (entity.getVehicle() instanceof ServerPlayer carrier && EntityState.of(carrier).carrying) release(carrier,false);
        if (entity instanceof ServerPlayer player) { release(player,false);player.stopRiding();EntityState.of(player).initialized=false;EntityState.of(player).jumpImpact=false;EntityState.of(player).contacts=new dev.magnitude.physics.ContactState(); }
    }
    public static void jump(ServerPlayer player) {
        dev.magnitude.physics.ContactEvents.takeoff(player);
        Messages.syncPhysics(player);
    }
    public static void groundContact(ServerPlayer player) {
        dev.magnitude.physics.ContactEvents.sample(player);
    }
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) { release(player, false); continue; }
            EntityState state = EntityState.of(player);
            dev.magnitude.physics.LocalProxy.update(player);
            double size = Dimensions.snapshot(player).base();
            if (!Double.isFinite(Dimensions.size(player))) { Dimensions.reset(player); continue; }
            if (state.randomPeriod >= 20 && (Magnitude.settings.allowSelfChange || Dimensions.operator(player)) && --state.nextRandom <= 0) {
                state.nextRandom = state.randomPeriod;
                double value = state.randomLow + player.getRandom().nextDouble() * (state.randomHigh - state.randomLow);
                Dimensions.set(player, value, 20);
            }
            if (state.carrying && player.getFirstPassenger() == null) { state.carrying = false; Messages.syncCarry(player); }
            if (state.carrying && player.getFirstPassenger() != null) {
                Entity passenger=player.getFirstPassenger();
                if (!passenger.isAlive() || passenger.isSpectator() || !Rules.ratio(size,Dimensions.snapshot(passenger).base(),2)
                    || (state.riderInitiated ? !state.acceptCarry : passenger instanceof Player && !EntityState.of(passenger).acceptCarry)) release(player,false);
            }
            groundContact(player);
            Messages.syncPhysics(player);
            if (player.tickCount % 10 == 0) {
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
        double nearest=Math.min(distance*distance,allowed);
        LivingEntity hit=null;
        for (Entity candidate:EntityQueries.nearby(player.level(),new AABB(start,end).inflate(1),player,256)) {
            if (!(candidate instanceof LivingEntity living) || !candidate.isAlive() || candidate.isSpectator()) continue;
            AABB box=candidate.getBoundingBox().inflate(candidate.getPickRadius());
            var intersection=box.clip(start,end);
            boolean modelled=false;
            if(candidate instanceof Player model && dev.magnitude.physics.BodyCollision.active(model)) {
                modelled=true;
                intersection=dev.magnitude.physics.PlayerBody.parts(model,model.position()).stream().map(part->part.ray(start,end)).flatMap(java.util.Optional::stream).min(java.util.Comparator.comparingDouble(start::distanceToSqr));
                if(intersection.isEmpty())continue;
            }
            if (!box.contains(start) && intersection.isEmpty()) continue;
            double separation=!modelled && box.contains(start)?0:start.distanceToSqr(intersection.orElseThrow());
            if (separation<nearest) { nearest=separation;hit=living; }
        }
        return hit;
    }
    public static boolean toolUse(ServerPlayer player, LivingEntity target, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return stack.getItem() instanceof ToolItem tool && tool.interactLivingEntity(stack, player, target, hand) == InteractionResult.SUCCESS;
    }
    private static boolean within(ServerPlayer actor, Entity target, double range) {
        return actor.level() == target.level() && target.isAlive() && !target.isSpectator() && !actor.isSpectator() && target.getBoundingBox().distanceToSqr(actor.getEyePosition()) <= range * range && Dimensions.lineOfSight(actor,target);
    }
    public static boolean carry(ServerPlayer player, LivingEntity target) {
        double range = Math.min(16, Math.max(4, Dimensions.snapshot(player).base()*2));
        if (target == player || player.isPassenger() || !within(player,target,range) || !player.getAbilities().mayBuild || target.isPassenger() || !target.getPassengers().isEmpty() || !Rules.ratio(Dimensions.snapshot(player).base(), Dimensions.snapshot(target).base(), 2)) return false;
        if (target instanceof Player && !EntityState.of(target).acceptCarry) return false;
        if (player.getFirstPassenger() != null || !cooldown(player, 10)) return false;
        boolean result = PlayerMounts.start(target,player);
        if (result) { EntityState.of(player).carrying = true;EntityState.of(player).riderInitiated=false;Messages.syncCarry(player); }
        return result;
    }
    public static boolean ride(ServerPlayer player, LivingEntity target) {
        if (!player.getAbilities().mayBuild || target.isPassenger() || !player.getPassengers().isEmpty() || !within(player,target,Math.min(16,Math.max(4,Dimensions.snapshot(target).base()*2))) || !Rules.ratio(Dimensions.snapshot(target).base(), Dimensions.snapshot(player).base(), 4) || player.isPassenger() || !target.getPassengers().isEmpty()) return false;
        if (target instanceof Player && !EntityState.of(target).acceptCarry) return false;
        if (!cooldown(player,10)) return false;
        if (target instanceof ServerPlayer carrier) {
            if (!PlayerMounts.start(player,carrier)) return false;
            EntityState.of(carrier).carrying=true;EntityState.of(carrier).riderInitiated=true;Messages.syncCarry(carrier);
            return true;
        }
        return player.startRiding(target,true,true);
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
            passenger.setDeltaMovement(player.getLookAngle().scale(Math.clamp(Dimensions.snapshot(player).base()/Math.max(0.25,Dimensions.snapshot(passenger).base()),0.5,2)));
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
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !Magnitude.settings.bodyDamage || !within(player,target,Math.min(16,Math.max(4,Dimensions.snapshot(player).base()*2))) || !Rules.ratio(Dimensions.snapshot(player).base(),Dimensions.snapshot(target).base(),4) || !canDamage(player,target) || !cooldown(player,20)) return false;
        float damage=(float)Math.clamp(Dimensions.snapshot(player).attackFactor() / Math.max(0.25,Dimensions.snapshot(target).base()),1,40);
        return target.hurtServer(player.level(),player.damageSources().playerAttack(player),damage);
    }
    public static boolean canDamage(ServerPlayer actor, LivingEntity target) {
        return actor != target && actor.getAbilities().mayBuild && !actor.isPassengerOfSameVehicle(target) && (!(target instanceof Player other) || actor.canHarmPlayer(other));
    }
    public static void damageSmall(ServerPlayer player, AABB box, float damage) {
        int count=0;
        for (Entity entity:EntityQueries.nearby(player.level(),box,player,32)) {
            if (++count>32) break;
            if(entity instanceof LivingEntity living && canDamage(player,living) && Rules.ratio(Dimensions.snapshot(player).base(),Dimensions.snapshot(living).base(),4)) living.hurtServer(player.level(),player.damageSources().playerAttack(player),damage);
        }
    }
    public static boolean shock(ServerPlayer player,double radius,boolean landing) {
        if (Dimensions.snapshot(player).base()<4 || !player.onGround()) return false;
        if (!landing && !cooldown(player,20)) return false;
        double speed = landing ? Math.clamp(Math.max(0, -EntityState.of(player).downward) / 0.42, 0.25, 4) : 1;
        double impactRadius = Math.clamp(radius * (0.5 + speed * 0.5), 0.5, Magnitude.settings.impactRadius);
        if (Magnitude.settings.bodyDamage) damageSmall(player, player.getBoundingBox().inflate(impactRadius,0.75,impactRadius), scaledImpactDamage(player,(landing ? Magnitude.settings.landingDamageFactor : Magnitude.settings.walkDamageFactor) * speed));
        Impact.breakAround(player,player.position().add(0,-0.5,0),impactRadius,1.5 * Math.clamp(speed,0.5,2));
        player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,player.getX(),player.getY()+0.1,player.getZ(),12,radius/2,0.1,radius/2,0.03);
        return true;
    }
    public static float scaledImpactDamage(ServerPlayer player,double base) {
        return (float)Math.clamp(base * Dimensions.snapshot(player).attackFactor(), 0, 100);
    }
    /** Pehkui's jump modifier is multiplicative; cap the resulting launch velocity to a
     * physically useful envelope so an extreme visual scale cannot launch hundreds of blocks. */
    public static void limitJumpVelocity(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y <= 0 || player.getAbilities().flying || player.isNoGravity()) return;
        double maximum = Dimensions.snapshot(player).jumpVelocityLimit();
        if (velocity.y > maximum) { player.setDeltaMovement(velocity.x, maximum, velocity.z); player.syncVelocity = true; }
    }
    public static boolean blow(ServerPlayer player) {
        if (Dimensions.snapshot(player).base()<2 || !cooldown(player,20)) return false;
        Vec3 forward=player.getLookAngle(); Vec3 origin=player.getEyePosition();
        double range=Math.min(24,Dimensions.snapshot(player).base()*3);
        int count=0;
        for(Entity entity:EntityQueries.nearby(player.level(),new AABB(origin,origin.add(forward.scale(range))).inflate(range*0.4),player,64)) {
            if(++count>64) break;
            Vec3 direction=entity.getBoundingBox().getCenter().subtract(origin);
            if(direction.lengthSqr()>range*range || direction.normalize().dot(forward)<0.7 || !Rules.ratio(Dimensions.snapshot(player).base(),Dimensions.snapshot(entity).base(),2) || !Dimensions.lineOfSight(player,entity)) continue;
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
