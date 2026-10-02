package dev.magnitude.client;

import dev.magnitude.Magnitude;
import dev.magnitude.content.WorldContent;
import dev.magnitude.core.EntityState;
import dev.magnitude.network.ActionPayload;
import dev.magnitude.network.CarryPayload;
import dev.magnitude.network.PhysicsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

public final class MagnitudeClient implements ClientModInitializer {
    public static double magnification=4;
    public static double sensitivity=1;
    public static boolean zoom;
    public static boolean hiddenNames;
    private static boolean toggleZoom;
    private static boolean smoothing;
    private static Boolean oldSmooth;
    private static KeyMapping hold, toggle, increase, decrease, reset, smooth, names, precisionUp, precisionDown;
    private static final KeyMapping[] ACTIONS=new KeyMapping[6];
    @Override public void onInitializeClient() {
        KeyMapping.Category category=KeyMapping.Category.register(Magnitude.id("controls"));
        hold=key("observe",InputConstants.KEY_Z,category);toggle=key("observe_toggle",InputConstants.KEY_N,category);
        increase=key("magnify",InputConstants.KEY_EQUALS,category);decrease=key("reduce_zoom",InputConstants.KEY_MINUS,category);
        reset=key("reset_view",InputConstants.KEY_R,category);smooth=key("smooth",InputConstants.KEY_G,category);names=key("names",InputConstants.KEY_J,category);
        precisionUp=key("precision_up",InputConstants.KEY_RBRACKET,category);precisionDown=key("precision_down",InputConstants.KEY_LBRACKET,category);
        String[] labels={"blow","stomp","release","throw","ability","ride"};int[] keys={InputConstants.KEY_B,InputConstants.KEY_V,InputConstants.KEY_X,InputConstants.KEY_H,InputConstants.KEY_K,InputConstants.KEY_M};
        for(int i=0;i<ACTIONS.length;i++)ACTIONS[i]=key(labels[i],keys[i],category);
        ClientTickEvents.START_CLIENT_TICK.register(client -> {dev.magnitude.interaction.EntityQueries.beginTick();dev.magnitude.physics.PhysicsWork.beginTick();});
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(client.player==null||client.level==null){restoreSmooth(client);zoom=false;toggleZoom=false;return;}
            if(client.gui.screen()!=null){zoom=false;restoreSmooth(client);return;}
            while(toggle.consumeClick())toggleZoom=!toggleZoom;
            while(increase.consumeClick())magnification=Math.clamp(magnification*1.25,1,32);
            while(decrease.consumeClick())magnification=Math.clamp(magnification/1.25,1,32);
            while(reset.consumeClick()){magnification=4;sensitivity=1;toggleZoom=false;}
            while(precisionUp.consumeClick())sensitivity=Math.clamp(sensitivity*1.1,0.1,2);
            while(precisionDown.consumeClick())sensitivity=Math.clamp(sensitivity/1.1,0.1,2);
            while(smooth.consumeClick())smoothing=!smoothing;
            while(names.consumeClick())hiddenNames=!hiddenNames;
            zoom=toggleZoom||hold.isDown();
            if(zoom&&smoothing){if(oldSmooth==null)oldSmooth=client.options.smoothCamera;client.options.smoothCamera=true;}
            else restoreSmooth(client);
            for(int i=0;i<ACTIONS.length;i++)while(ACTIONS[i].consumeClick())if(ClientPlayNetworking.canSend(ActionPayload.TYPE))ClientPlayNetworking.send(new ActionPayload(i));
        });
        ClientPlayNetworking.registerGlobalReceiver(CarryPayload.TYPE,(payload,context) -> {
            if(context.client().level==null)return;
            var carrier=context.client().level.getEntity(payload.carrier());if(carrier==null)return;
            EntityState state=EntityState.of(carrier);state.carrying=payload.active();state.carryPosition=Math.clamp(payload.position(),0,2);
            state.offsetForward=safe(payload.forward(),-2,2,0.4);state.offsetSide=safe(payload.side(),-2,2,0);state.offsetUp=safe(payload.up(),0,2,1.25);
        });
        ClientPlayNetworking.registerGlobalReceiver(PhysicsPayload.TYPE,(payload,context)-> {
            if(context.client().level==null || !payload.pose().valid() || !Double.isFinite(payload.limit()) || payload.limit()<1 || payload.limit()>32 || !Float.isFinite(payload.yaw()))return;
            if(!(context.client().level.getEntity(payload.entity()) instanceof net.minecraft.world.entity.player.Player player))return;
            var state=EntityState.of(player);
            if(payload.revision()<state.physicsRevision)return;
            state.previousPose=state.pose;state.physicsRevision=payload.revision();state.posePhase=payload.pose().phase();state.pose=payload.pose();state.proxyFallback=payload.fallback();
            if(Math.abs(state.proxyLimit-payload.limit())>0.001)dev.magnitude.physics.LocalProxy.apply(player,payload.limit());
        });
        FluidRenderingRegistry.register(WorldContent.AMBER_SOURCE,WorldContent.AMBER_FLOW,fluid(0xE8B54A));
        FluidRenderingRegistry.register(WorldContent.AZURE_SOURCE,WorldContent.AZURE_FLOW,fluid(0x4AB8DC));
        FluidRenderingRegistry.setBlockTransparency(WorldContent.AMBER_POOL,true);
        FluidRenderingRegistry.setBlockTransparency(WorldContent.AZURE_POOL,true);
    }
    private static FluidModel.Unbaked fluid(int color){
        return new FluidModel.Unbaked(new Material(Identifier.withDefaultNamespace("block/water_still")),new Material(Identifier.withDefaultNamespace("block/water_flow")),new Material(Identifier.withDefaultNamespace("block/water_overlay")),BlockTintSources.constant(color));
    }
    private static KeyMapping key(String label,int key,KeyMapping.Category category){return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magnitude."+label,key,category));}
    private static double safe(double value,double minimum,double maximum,double fallback){return Double.isFinite(value)?Math.clamp(value,minimum,maximum):fallback;}
    private static void restoreSmooth(Minecraft client){if(oldSmooth!=null){client.options.smoothCamera=oldSmooth;oldSmooth=null;}}
}
