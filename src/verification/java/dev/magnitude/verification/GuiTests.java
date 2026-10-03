package dev.magnitude.verification;

import dev.magnitude.client.settings.*;
import dev.magnitude.config.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

/** Actual client widget/render verification, enabled only in an isolated verification run. */
public final class GuiTests implements ClientModInitializer {
    private final java.util.List<String> passed=new ArrayList<>();
    private int frame,stage;
    private SettingsScreen screen;
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed.add(label);}
    private void click(String key){
        String text=Component.translatable(key).getString();
        var button=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(text)).map(x->(Button)x).findFirst().orElseThrow();
        check(button.active,"enabled widget "+key);button.onPress(null);
    }
    private ConfigView fixture(boolean admin){
        var settings=new dev.magnitude.core.Settings();var values=new java.util.LinkedHashMap<String,Double>();
        for(var field:ConfigField.values())values.put(field.id,field.read(settings));
        return new ConfigView(1,1,admin,values,Map.of("terrain",1d,"pressure",1d,"resize",0d,"carry",0d),"ready","",50,false,false,"serverOff","serverOff",12.3,.2,.8,1,128,2,"budget exhausted","footprint waiting for foot release");
    }
    private void screenshot(Minecraft client,String name){
        net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{
            try{image.writeToFile(Path.of(name+".png"));}catch(Exception error){throw new RuntimeException(error);}finally{image.close();}
        });
    }
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("magnitude.guiVerification"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++frame%20!=0)return;
            try {
                switch(stage){
                    case 0 -> {if(!(client.gui.screen() instanceof TitleScreen)||client.gui.overlay()!=null)return;screen=new SettingsScreen(client.gui.screen());client.gui.setScreen(screen);}
                    case 1 -> {check(screen.children().size()>=7,"offline overview native widgets initialized");screenshot(client,"overview-offline");click("gui.magnitude.tab.client");}
                    case 2 -> {
                        var box=screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().getString().equals(Component.translatable("config.magnitude.field.magnification").getString())).map(x->(EditBox)x).findFirst().orElseThrow();
                        box.setValue("6.25");click("gui.magnitude.apply");
                        check(dev.magnitude.client.MagnitudeClient.magnification==6.25&&Files.readString(ClientPreferences.path()).contains("6.25"),"client widget edit persists acknowledged local preference");
                    }
                    case 3 -> {screenshot(client,"view-settings");click("gui.magnitude.tab.player");check(screen.children().stream().noneMatch(x->x instanceof Button b&&b.active&&b.getMessage().getString().equals(Component.translatable("gui.magnitude.apply").getString())),"offline personal editing disabled");SettingsConnection.view=fixture(false);screen=new SettingsScreen(null);client.gui.setScreen(screen);click("gui.magnitude.tab.server");}
                    case 4 -> {check(screen.children().stream().filter(x->x instanceof EditBox).allMatch(x->!((EditBox)x).active),"ordinary-player server numeric controls read only");check(screen.children().stream().filter(x->x instanceof Button b&&(b.getMessage().getString().equals(Component.translatable("options.on").getString())||b.getMessage().getString().equals(Component.translatable("options.off").getString()))).allMatch(x->!((Button)x).active),"ordinary-player server toggles read only");screenshot(client,"server-readonly");SettingsConnection.view=fixture(true);screen=new SettingsScreen(null);client.gui.setScreen(screen);click("gui.magnitude.tab.server");}
                    case 5 -> {check(screen.children().stream().filter(x->x instanceof EditBox).allMatch(x->((EditBox)x).active),"administrator server fields editable");screenshot(client,"server-editable");clickLiteral(">");}
                    case 6 -> {screenshot(client,"server-next-page");click("gui.magnitude.tab.diagnostics");}
                    case 7 -> {screenshot(client,"diagnostics");screen.init(320,240);check(screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).allMatch(x->{var w=(net.minecraft.client.gui.components.AbstractWidget)x;return w.getX()>=0&&w.getY()>=0&&w.getRight()<=320&&w.getBottom()<=240;}),"all widgets remain within 320x240 scaled screen");click("gui.magnitude.tab.client");var box=screen.children().stream().filter(x->x instanceof EditBox).map(x->(EditBox)x).findFirst().orElseThrow();box.setValue("NaN");click("gui.magnitude.apply");check(dev.magnitude.client.MagnitudeClient.magnification==6.25,"invalid GUI number cannot change local preference");screen.onClose();check(client.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen,"closing a dirty draft asks before discarding");}
                    case 8 -> {
                        var options=new net.minecraft.client.gui.screens.options.OptionsScreen(new TitleScreen(),client.options);client.gui.setScreen(options);
                        var entry=options.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals("Magnitude")).map(x->(Button)x).findFirst().orElseThrow();
                        check(entry.active,"options menu exposes offline settings entry");entry.onPress(null);check(client.gui.screen() instanceof SettingsScreen,"options entry opens native settings screen");
                    }
                    case 9 -> {Files.writeString(Path.of("gui-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",true,"passed",passed)));client.stop();}
                    default -> {return;}
                }
                stage++;
            }catch(Throwable error){
                error.printStackTrace();try{Files.writeString(Path.of("gui-results.json"),new com.google.gson.Gson().toJson(Map.of("success",false,"failure",error.toString(),"passed",passed)));}catch(Exception ignored){}client.stop();
            }
        });
    }
    private void clickLiteral(String label){var button=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(label)).map(x->(Button)x).findFirst().orElseThrow();check(button.active,"pagination enabled");button.onPress(null);}
}
