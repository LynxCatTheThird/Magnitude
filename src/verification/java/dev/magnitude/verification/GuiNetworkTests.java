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
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.guiNetworkVerification"))return;
        boolean admin=Boolean.getBoolean("magnitude.guiAdmin");
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++frame%20!=0)return;
            try {
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
                            check(screen.children().stream().filter(x->x instanceof EditBox).allMatch(x->!((EditBox)x).active),"real ordinary connection sees read-only server fields");
                            check(SettingsConnection.request(2,Map.of("terrainDamage",1d)),"forged edit sent for authorization test");
                        }else {
                            var off=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(Component.translatable("options.off").getString())).map(x->(Button)x).findFirst().orElseThrow();
                            off.onPress(null);click(screen,"gui.magnitude.apply");
                        }
                    }
                    case 4 -> {
                        if(SettingsConnection.busy())return;
                        if(admin)check(SettingsConnection.result.equals("applied")&&SettingsConnection.view.server().get("terrainDamage")==1,"GUI administrator edit persisted and acknowledged over network");
                        else check(SettingsConnection.result.equals("permission")&&SettingsConnection.view.server().get("terrainDamage")==0,"server rejects forged GUI edit over network");
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
                        Files.writeString(Path.of("network-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",true,"passed",passed)));client.stop();
                    }
                    default -> {return;}
                }
                stage++;
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("network-results.json"),new com.google.gson.Gson().toJson(Map.of("success",false,"passed",passed,"failure",error.toString())));}catch(Exception ignored){}client.stop();}
        });
    }
}
