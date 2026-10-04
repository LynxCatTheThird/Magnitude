package dev.magnitude.verification;

import dev.magnitude.client.settings.SettingsConnection;
import dev.magnitude.client.settings.SettingsScreen;
import dev.magnitude.config.ConfigField;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** End-to-end settings requests against an isolated loopback server. */
public final class GuiNetworkTests implements ClientModInitializer {
    private int frame,stage;
    private final java.util.List<String> passed=new java.util.ArrayList<>();
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);passed.add(label);}
    private void click(SettingsScreen screen,String key){
        var b=screen.children().stream().filter(x->x instanceof Button button&&button.getMessage().getString().equals(Component.translatable(key).getString())).map(x->(Button)x).findFirst().orElseThrow();
        check(b.active,"network widget enabled "+key);b.onPress(null);
    }
    private void finish(net.minecraft.client.Minecraft client)throws Exception {
        Files.writeString(Path.of("network-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",true,"passed",passed)));client.stop();
    }
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.guiNetworkVerification"))return;
        boolean admin=Boolean.getBoolean("magnitude.guiAdmin");
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++frame%20!=0)return;
            try {
                if(stage>=9&&client.player!=null)Files.writeString(Path.of("visual-progress.json"),new com.google.gson.Gson().toJson(Map.of("stage",stage,"position",client.player.position().toString(),"cache",dev.magnitude.client.visual.FootprintRenderer.cached(),"queue",dev.magnitude.client.visual.FootprintRenderer.queued(),"drawn",dev.magnitude.client.visual.FootprintRenderer.lastDrawn,"leftState",client.level.getBlockState(net.minecraft.core.BlockPos.containing(client.player.position().add(-.75,-.01,0))).toString(),"leftCached",dev.magnitude.client.visual.FootprintRenderer.hasSurface(net.minecraft.core.BlockPos.containing(client.player.position().add(-.75,-.01,0))))));
                switch(stage){
                    case 0 -> {
                        if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;
                        String address=System.getProperty("magnitude.testServer");
                        ConnectScreen.startConnecting(client.gui.screen(),client,ServerAddress.parseString(address),new ServerData("Local verification",address,ServerData.Type.OTHER),false,null);
                    }
                    case 1 -> {if(client.player==null||client.gui.overlay()!=null)return;client.player.connection.sendCommand("magnitude menu");client.player.connection.sendCommand("magnitude menu");}
                    case 2 -> {
                        if(!(client.gui.screen() instanceof SettingsScreen screen)||SettingsConnection.view==null)return;
                        check(SettingsConnection.view.administrator()==admin,"server confirms actual administrator role");click(screen,"gui.magnitude.tab.player");
                        var off=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(Component.translatable("options.off").getString())).map(x->(Button)x).findFirst().orElseThrow();
                        off.onPress(null);click(screen,"gui.magnitude.apply");
                    }
                    case 3 -> {
                        if(SettingsConnection.busy())return;
                        check(SettingsConnection.result.equals("applied")&&SettingsConnection.view.personal().get("terrain")==1,"GUI personal permission acknowledged over real network");
                        check(SettingsConnection.view.terrainReason().equals("serverOff"),"GUI shows disabled server rule after personal opt-in");
                        var screen=(SettingsScreen)client.gui.screen();click(screen,"gui.magnitude.tab.server");
                        if(!admin) {
                            check(screen.children().stream().filter(x->x instanceof EditBox b&&!b.getMessage().equals(Component.translatable("gui.magnitude.search"))).allMatch(x->!((EditBox)x).active),"real ordinary connection sees read-only server fields");
                            check(SettingsConnection.request(2,Map.of("terrainDamage",1d)),"forged edit sent for authorization test");
                        }else {
                            var off=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(Component.translatable("options.off").getString())).map(x->(Button)x).findFirst().orElseThrow();
                            off.onPress(null);
                            screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().equals(Component.translatable("gui.magnitude.search"))).map(x->(EditBox)x).findFirst().orElseThrow().setValue("maximum");
                            var maximum=screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().getString().equals(Component.translatable("config.magnitude.field.maximum").getString())).map(x->(EditBox)x).findFirst().orElseThrow();maximum.setValue("48");
                            click(screen,"gui.magnitude.group.budget");
                            var presetLabel=Component.translatable("gui.magnitude.workPreset",Component.translatable("gui.magnitude.preset.standard")).getString();
                            var preset=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(presetLabel)).map(x->(Button)x).findFirst().orElseThrow();preset.onPress(null);
                            screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().equals(Component.translatable("gui.magnitude.search"))).map(x->(EditBox)x).findFirst().orElseThrow().setValue("terrainDamage");
                            click(screen,"gui.magnitude.apply");
                        }
                    }
                    case 4 -> {
                        if(SettingsConnection.busy())return;
                        if(admin)check(SettingsConnection.result.equals("applied")&&SettingsConnection.view.server().get("terrainDamage")==1&&SettingsConnection.view.server().get("maximum")==48&&SettingsConnection.view.server().get("blocksPerTick")==32&&SettingsConnection.view.server().get("checksPerTick")==256&&SettingsConnection.view.server().get("blocksPerImpact")==16,"GUI administrator edits across groups persisted and acknowledged over network");
                        else check(SettingsConnection.result.equals("permission")&&SettingsConnection.view.server().get("terrainDamage")==0,"server rejects forged GUI edit over network");
                        var screen=(SettingsScreen)client.gui.screen();
                        if(admin){
                            screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().equals(Component.translatable("gui.magnitude.search"))).map(x->(EditBox)x).findFirst().orElseThrow().setValue("maximum");
                            screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().equals(Component.translatable("config.magnitude.field.maximum"))).map(x->(EditBox)x).findFirst().orElseThrow().setValue("64");
                        }
                        var confirmed=SettingsConnection.view;click(screen,"gui.magnitude.tab.overview");click(screen,"gui.magnitude.openDiagnostics");click(screen,"gui.magnitude.copyReport");
                        var report=com.google.gson.JsonParser.parseString(client.keyboardHandler.getClipboard()).getAsJsonObject();
                        check(report.getAsJsonObject("server").getAsJsonObject("server").get("maximum").getAsDouble()==(admin?48:32)
                            &&SettingsConnection.view==confirmed&&!SettingsConnection.busy(),"network diagnostic copy reads confirmed rules without applying a draft or sending a request");
                        check(report.get("serverSnapshotAgeMillis").getAsLong()>=0,"network diagnostic report distinguishes the age of its acknowledged snapshot");
                        if(admin){click(screen,"gui.magnitude.tab.server");check(screen.children().stream().anyMatch(x->x instanceof EditBox b&&b.getMessage().equals(Component.translatable("config.magnitude.field.maximum"))&&b.getValue().equals("64")),"network report preserves the administrator draft");click(screen,"gui.magnitude.discard");}
                        client.player.connection.sendCommand("magnitude config player pressure off");
                    }
                    case 5 -> {check(SettingsConnection.request(0,Map.of()),"network refresh requested after command edit");}
                    case 6 -> {
                        if(SettingsConnection.busy())return;
                        check(SettingsConnection.view.personal().get("pressure")==0,"GUI refresh agrees with command-modified permission");
                        client.player.connection.sendCommand("magnitude config player terrain off");
                    }
                    case 7 -> {check(SettingsConnection.request(1,Map.of("carry",1d)),"stale personal patch submitted");}
                    case 8 -> {
                        if(SettingsConnection.busy())return;
                        check(SettingsConnection.result.equals("conflict")&&SettingsConnection.view.personal().get("carry")==0,"real stale patch cannot overwrite newer command state");
                        check(SettingsConnection.request(admin?2:1,Map.of(admin?"maximum":"carry",admin?0d:Double.NaN)),"malformed settings patch submitted for field diagnostics");
                    }
                    case 9 -> {
                        if(SettingsConnection.busy())return;
                        check(SettingsConnection.result.equals("invalid")&&SettingsConnection.view.detail().equals(admin?"maximum":"carry"),"server returns the rejected field without changing confirmed values");
                        check(admin?SettingsConnection.view.server().get("maximum")==48:SettingsConnection.view.personal().get("carry")==0,"invalid patch preserves confirmed settings");
                        if(admin) {
                            client.player.connection.sendCommand("forceload add 90 90 110 110");
                            client.player.connection.sendCommand("fill 90 199 90 110 199 110 minecraft:grass_block");
                            client.player.connection.sendCommand("tp @s 100 200 100 0 75");
                            client.player.connection.sendCommand("magnitude scale set 5 0");
                            client.gui.setScreen(null);
                        }else {finish(client);return;}
                    }
                    case 10 -> {
                        if(client.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(100.5,200,100.5))>4)return;
                        if(!dev.magnitude.client.visual.FootprintRenderer.hasSurface(net.minecraft.core.BlockPos.containing(client.player.position().add(-.75,-.01,0))))return;
                        check(dev.magnitude.client.visual.FootprintRenderer.cached()>0,"actual authoritative contact builds immediate visual surface cache with terrain disabled");
                        client.player.setXRot(75);
                    }
                    case 11 -> {
                        if(dev.magnitude.client.visual.FootprintRenderer.lastDrawn==0)return;
                        check(true,"actual world renderer submits contact overlay geometry");
                        var below=client.player.blockPosition().below();
                        check(client.level.getBlockState(below).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK),"visual overlay leaves terrain block and collision unchanged");
                        net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(Path.of("footprint-overlay.png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}});
                        var payload=new dev.magnitude.network.FootprintPayload(client.player.getUUID(),999999,-1,client.level.dimension().identifier(),client.player.position().add(-.75,-.01,0),.45,.45,0);
                        check(dev.magnitude.client.visual.FootprintRenderer.accept(payload)&&!dev.magnitude.client.visual.FootprintRenderer.accept(payload),"client visual duplicate contact is ignored");
                    }
                    case 12 -> {
                        var pos=net.minecraft.core.BlockPos.containing(client.player.position().add(-.75,-.01,0));
                        int before=dev.magnitude.client.visual.FootprintRenderer.cached();
                        client.level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                        check(dev.magnitude.client.visual.FootprintRenderer.cached()<before,"block change invalidates cached surface decoration");
                        dev.magnitude.client.visual.FootprintRenderer.enabled=false;
                    }
                    case 13 -> {
                        check(dev.magnitude.client.visual.FootprintRenderer.cached()==0&&dev.magnitude.client.visual.FootprintRenderer.queued()==0&&dev.magnitude.client.visual.FootprintRenderer.lastDrawn==0,"disabling visual footprints clears pending and cached work");
                        client.player.connection.sendCommand("fill 90 199 90 99 199 110 minecraft:stone_slab");
                        client.player.connection.sendCommand("tp @s 100.25 200 100.5 0 15");
                        client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    }
                    case 14 -> {
                        var pose=dev.magnitude.core.EntityState.of(client.player).pose;
                        if(Math.abs(client.player.getY()-199.5)>1e-5||pose.rightKnee()>-.1)return;
                        check(pose.valid()&&pose.support()==3,"real client receives authoritative articulated support on unequal surfaces");
                        var velocity=client.player.getDeltaMovement();
                        var intent=new net.minecraft.world.phys.Vec3(.02,0,.03);client.player.setDeltaMovement(intent);
                        dev.magnitude.physics.PhysicsWork.beginTick();
                        dev.magnitude.core.EntityState.of(client.player).physicsTick=Long.MIN_VALUE;
                        ((dev.magnitude.verification.mixin.EscapeProbeAccessor)client.player).magnitude$escape(100.25,100.5);
                        check(client.player.getDeltaMovement().equals(intent),"native client escape preserves intent when unequal support lowers only the pelvis into proxy ground");
                        client.player.setDeltaMovement(velocity);
                        client.gui.setScreen(null);
                        client.gui.hud.toggle();
                        client.player.setXRot(15);
                    }
                    case 15 -> {
                        if(dev.magnitude.client.visual.SegmentedLegMesh.renderCalls==0)return;
                        check(true,"actual avatar renderer draws segmented knee and boot mesh");
                        net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(Path.of("articulated-support.png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}});
                        client.gui.hud.toggle();client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                        client.player.connection.sendCommand("fill 90 199 90 110 199 110 minecraft:grass_block");
                        client.player.connection.sendCommand("magnitude config server shallowDeformation true");
                        client.player.connection.sendCommand("magnitude config player pressure on");
                        client.player.connection.sendCommand("magnitude config player terrain on");
                        client.player.connection.sendCommand("tp @s 100.25 200 100.5 0 75");
                    }
                    case 16 -> {
                        var surface=client.level.getBlockState(net.minecraft.core.BlockPos.containing(99.5,199,100.5));
                        if(!surface.is(dev.magnitude.content.WorldContent.COMPACTED_GRASS))return;
                        if(Math.abs(client.player.getY()-199.875)>1e-5)return;
                        check(surface.getValue(dev.magnitude.content.CompactedSoilBlock.HEIGHT)==14,"actual client receives saved partial-height grass state");
                        check(true,"client collision settles onto server deformation height");
                        client.gui.setScreen(null);client.gui.hud.toggle();client.player.setXRot(75);
                    }
                    case 17 -> {
                        net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(Path.of("shallow-soil.png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}});
                        finish(client);
                    }
                    default -> {return;}
                }
                stage++;
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("network-results.json"),new com.google.gson.Gson().toJson(Map.of("success",false,"passed",passed,"failure",error.toString())));}catch(Exception ignored){}client.stop();}
        });
    }
}
