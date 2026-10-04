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
        return new ConfigView(1,1,admin,values,Map.of("terrain",1d,"pressure",1d,"resize",0d,"carry",0d),"ready","",50,false,false,"serverOff","serverOff",12.3,.2,.8,1,128,2,"budget exhausted","footprint waiting for foot release",new dev.magnitude.physics.TimingWindow(1).summary(),new dev.magnitude.physics.TimingWindow(1).summary(),0,0,0,0,2);
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
                    case 4 -> {click("gui.magnitude.group.response");check(screen.children().stream().anyMatch(x->x instanceof EditBox),"response group exposes numeric server controls");check(screen.children().stream().filter(x->x instanceof EditBox box&&!box.getMessage().equals(Component.translatable("gui.magnitude.search"))).allMatch(x->!((EditBox)x).active),"ordinary-player server numeric controls read only");check(screen.children().stream().filter(x->x instanceof Button b&&(b.getMessage().getString().equals(Component.translatable("options.on").getString())||b.getMessage().getString().equals(Component.translatable("options.off").getString()))).allMatch(x->!((Button)x).active),"ordinary-player server toggles read only");screenshot(client,"server-readonly");SettingsConnection.view=fixture(true);screen=new SettingsScreen(null);client.gui.setScreen(screen);click("gui.magnitude.tab.server");}
                    case 5 -> {check(screen.children().stream().filter(x->x instanceof EditBox box&&!box.getMessage().equals(Component.translatable("gui.magnitude.search"))).allMatch(x->((EditBox)x).active),"administrator server fields editable");click("gui.magnitude.group.response");check(screen.children().stream().filter(x->x instanceof EditBox box&&!box.getMessage().equals(Component.translatable("gui.magnitude.search"))).allMatch(x->((EditBox)x).active),"administrator numeric response controls editable");screenshot(client,"server-editable");screen.init(320,240);clickLiteral(">");}
                    case 6 -> {screenshot(client,"server-next-page");click("gui.magnitude.tab.diagnostics");}
                    case 7 -> {screenshot(client,"diagnostics");screen.init(320,240);check(screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).allMatch(x->{var w=(net.minecraft.client.gui.components.AbstractWidget)x;return w.getX()>=0&&w.getY()>=0&&w.getRight()<=320&&w.getBottom()<=240;}),"all widgets remain within 320x240 scaled screen");click("gui.magnitude.tab.client");var box=screen.children().stream().filter(x->x instanceof EditBox candidate&&!candidate.getMessage().equals(Component.translatable("gui.magnitude.search"))).map(x->(EditBox)x).findFirst().orElseThrow();box.setValue("NaN");click("gui.magnitude.group.footprints");click("gui.magnitude.apply");check(dev.magnitude.client.MagnitudeClient.magnification==6.25,"invalid GUI number cannot change local preference");screen.onClose();check(client.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen,"closing a dirty draft asks before discarding");}
                    case 8 -> {
                        screen=new SettingsScreen(null);client.gui.setScreen(screen);click("gui.magnitude.tab.commands");
                        var copy=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(Component.translatable("gui.magnitude.copy").getString())).map(x->(Button)x).findFirst().orElseThrow();copy.onPress(null);
                        check(client.keyboardHandler.getClipboard().equals("/magnitude config show"),"command reference copies canonical syntax without execution");
                    }
                    case 9 -> {
                        screenshot(client,"command-reference");
                        click("gui.magnitude.tab.client");click("gui.magnitude.group.footprints");
                        check(screen.children().stream().anyMatch(x->x instanceof EditBox b&&b.getMessage().getString().equals(Component.translatable("config.magnitude.field.footprintDistance").getString())),"footprint group exposes only local visual settings");
                        var distance=screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().getString().equals(Component.translatable("config.magnitude.field.footprintDistance").getString())).map(x->(EditBox)x).findFirst().orElseThrow();distance.setValue("96");
                        click("gui.magnitude.group.camera");click("gui.magnitude.apply");
                        check(ClientPreferences.values().get("footprintDistance")==96&&ClientPreferences.values().get("magnification")==6.25,"applying another group preserves and commits hidden local drafts");
                        var options=new net.minecraft.client.gui.screens.options.OptionsScreen(new TitleScreen(),client.options);client.gui.setScreen(options);
                        var entry=options.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals("Magnitude")).map(x->(Button)x).findFirst().orElseThrow();
                        check(entry.active,"options menu exposes offline settings entry");entry.onPress(null);check(client.gui.screen() instanceof SettingsScreen,"options entry opens native settings screen");
                    }
                    case 10 -> {
                        screen=new SettingsScreen(null);SettingsConnection.view=fixture(true);client.gui.setScreen(screen);click("gui.magnitude.tab.server");
                        click("gui.magnitude.group.budget");
                        var label=Component.translatable("gui.magnitude.workPreset",Component.translatable("gui.magnitude.preset.standard")).getString();
                        var preset=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(label)).map(x->(Button)x).findFirst().orElseThrow();preset.onPress(null);
                        check(SettingsConnection.view.server().get("blocksPerTick")==256,"preset selection stays a draft before acknowledgement");
                        var writes=screen.children().stream().filter(x->x instanceof EditBox b&&b.getMessage().getString().equals(Component.translatable("config.magnitude.field.blocksPerTick").getString())).map(x->(EditBox)x).findFirst().orElseThrow();
                        check(Double.parseDouble(writes.getValue())==32,"preset updates grouped numeric drafts together");screen.init(320,240);
                        check(screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).allMatch(x->{var w=(net.minecraft.client.gui.components.AbstractWidget)x;return w.getX()>=0&&w.getY()>=0&&w.getRight()<=320&&w.getBottom()<=240;}),"preset controls remain within small scaled screen");
                        var widgets=screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).map(x->(net.minecraft.client.gui.components.AbstractWidget)x).toList();
                        boolean overlap=false;for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);overlap|=a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY();}
                        check(!overlap,"small scaled preset screen has no overlapping interactive controls");
                    }
                    case 11 -> {
                        screenshot(client,"work-presets");click("gui.magnitude.tab.commands");
                        click("gui.magnitude.commandGroup.server");
                        var copy=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(Component.translatable("gui.magnitude.copy").getString())).map(x->(Button)x).findFirst().orElseThrow();copy.onPress(null);
                        check(client.keyboardHandler.getClipboard().equals("/magnitude config server terrainDamage true"),"server command group copies explicit administrator scope");
                        screen.init(320,240);check(screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).allMatch(x->{var w=(net.minecraft.client.gui.components.AbstractWidget)x;return w.getX()>=0&&w.getY()>=0&&w.getRight()<=320&&w.getBottom()<=240;}),"grouped command controls fit small scaled screen");
                        clickLiteral(">");click("gui.magnitude.commandGroup.common");
                        copy=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(Component.translatable("gui.magnitude.copy").getString())).map(x->(Button)x).findFirst().orElseThrow();copy.onPress(null);
                        check(client.keyboardHandler.getClipboard().equals("/magnitude config show"),"changing command group resets pagination to first page");
                    }
                    case 12 -> {screenshot(client,"grouped-commands");}
                    case 13 -> {
                        click("gui.magnitude.tab.client");click("gui.magnitude.group.footprints");
                        var before=ClientPreferences.values();var selected=VisualPreset.matching(before);
                        var label=Component.translatable("gui.magnitude.visualPreset",Component.translatable("gui.magnitude.visualPreset."+(selected==null?"custom":selected.id))).getString();
                        var preset=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(label)).map(x->(Button)x).findFirst().orElseThrow();preset.onPress(null);
                        check(ClientPreferences.values().equals(before),"visual preset selection preserves live preferences before Apply");
                        click("gui.magnitude.group.camera");click("gui.magnitude.apply");
                        var after=ClientPreferences.values();var next=selected==null?VisualPreset.LOW:selected.next();
                        check(after.get("footprintCache")==next.cells,"visual cache preset commits from another group");
                        check(after.entrySet().stream().filter(e->!e.getKey().equals("footprintCache")).allMatch(e->e.getValue().equals(before.get(e.getKey()))),"visual preset preserves switch distance and every camera field");
                        click("gui.magnitude.group.footprints");screen.init(320,240);
                        var widgets=screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).map(x->(net.minecraft.client.gui.components.AbstractWidget)x).toList();
                        check(widgets.stream().allMatch(w->w.getX()>=0&&w.getY()>=0&&w.getRight()<=320&&w.getBottom()<=240),"visual preset controls fit small scaled screen");
                        boolean overlap=false;for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);overlap|=a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY();}
                        check(!overlap,"visual preset has no overlapping controls");
                    }
                    case 14 -> {screenshot(client,"visual-presets");}
                    case 15 -> {
                        screen=new SettingsScreen(null);client.gui.setScreen(screen);click("gui.magnitude.tab.commands");
                        search("tool mode");var copy=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().equals(Component.translatable("gui.magnitude.copy"))).map(x->(Button)x).findFirst().orElseThrow();copy.onPress(null);
                        check(client.keyboardHandler.getClipboard().equals("/magnitude tool set mode multiply"),"search matches multiple syntax words across command groups");
                        check(screen.getFocused() instanceof EditBox box&&box.getMessage().equals(Component.translatable("gui.magnitude.search")),"search preserves keyboard focus after rebuilding filtered results");
                        search("no such result 123");check(screen.children().stream().noneMatch(x->x instanceof Button b&&b.getMessage().equals(Component.translatable("gui.magnitude.copy"))),"empty command search has no stale copy controls");
                        click("gui.magnitude.clearSearch");click("gui.magnitude.commandGroup.actions");
                        check(screen.children().stream().anyMatch(x->x instanceof Button b&&b.getMessage().equals(Component.translatable("gui.magnitude.copy"))),"direct group selection restores command list");
                    }
                    case 16 -> {
                        click("gui.magnitude.tab.client");search("footprintDistance");
                        var distance=screen.children().stream().filter(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.footprintDistance"))).map(x->(EditBox)x).findFirst().orElseThrow();distance.setValue("80");
                        search("magnification");click("gui.magnitude.apply");
                        check(ClientPreferences.values().get("footprintDistance")==80,"filtered apply commits hidden drafts across groups");
                        search("magnification");var mag=screen.children().stream().filter(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification"))).map(x->(EditBox)x).findFirst().orElseThrow();mag.setValue("NaN");
                        search("footprintCache");click("gui.magnitude.apply");
                        check(screen.children().stream().anyMatch(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification"))&&box.getValue().equals("NaN")),"invalid hidden field reveals its original group and draft");
                        check(searchBox().getValue().isEmpty(),"invalid hidden field clears filtering for correction");
                        check(screen.getFocused() instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification")),"invalid hidden field receives keyboard focus for immediate correction");
                        check(((EditBox)screen.getFocused()).getHighlighted().equals("NaN"),"invalid draft is selected so typing replaces it");
                        var invalid=screen.children().stream().filter(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification"))).map(x->(EditBox)x).findFirst().orElseThrow();invalid.setValue("33");
                        search("footprintDistance");click("gui.magnitude.apply");
                        check(searchBox().getValue().isEmpty()&&ClientPreferences.values().get("magnification")==6.25,"finite out-of-range hidden draft is rejected and revealed without saving");
                        check(screen.getFocused() instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification")),"range failure restores focus to its original numeric input");
                        click("gui.magnitude.discard");screen.init(320,240);
                        checkLayout();
                    }
                    case 17 -> {screenshot(client,"search-settings");click("gui.magnitude.tab.commands");search("tool mode");screen.init(320,240);checkLayout();}
                    case 18 -> {screenshot(client,"search-commands");}
                    case 19 -> {
                        click("gui.magnitude.tab.client");search("magnification");
                        var magnification=screen.children().stream().filter(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification"))).map(x->(EditBox)x).findFirst().orElseThrow();magnification.setValue("7.5");
                        var confirmed=SettingsConnection.view;click("gui.magnitude.tab.overview");click("gui.magnitude.openDiagnostics");click("gui.magnitude.copyReport");
                        var report=com.google.gson.JsonParser.parseString(client.keyboardHandler.getClipboard()).getAsJsonObject();
                        check(report.getAsJsonObject("client").getAsJsonObject("preferences").get("magnification").getAsDouble()==6.25
                            &&SettingsConnection.view==confirmed,"diagnostic copy uses saved preferences and preserves the confirmed server snapshot");
                        check(report.getAsJsonObject("server").get("lastFailure").getAsString().equals("budget exhausted")
                            &&report.getAsJsonObject("client").has("visualExtraction"),"report includes unshown diagnostic pages and local sample windows");
                        screen.init(320,240);checkLayout();clickLiteral(">");click("gui.magnitude.copyReport");
                        check(com.google.gson.JsonParser.parseString(client.keyboardHandler.getClipboard()).getAsJsonObject().getAsJsonObject("server").equals(report.getAsJsonObject("server")),"report is complete regardless of diagnostic pagination");
                        click("gui.magnitude.tab.client");
                        check(screen.children().stream().anyMatch(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("config.magnitude.field.magnification"))&&box.getValue().equals("7.5")),"diagnostic navigation and copying preserve unapplied drafts");
                        click("gui.magnitude.discard");click("gui.magnitude.tab.overview");click("gui.magnitude.openDiagnostics");
                    }
                    case 20 -> {
                        screenshot(client,"diagnostic-report");SettingsConnection.view=null;screen.init(320,240);click("gui.magnitude.copyReport");
                        var offline=com.google.gson.JsonParser.parseString(client.keyboardHandler.getClipboard()).getAsJsonObject();
                        check(offline.get("server").isJsonNull()&&offline.get("serverSource").getAsString().equals("unavailable")&&offline.get("serverSnapshotAgeMillis").isJsonNull()
                            &&offline.getAsJsonObject("client").has("frameIntervals"),"offline report keeps local diagnostics and explicitly marks unavailable server state");
                    }
                    case 21 -> {
                        ClientMetrics.FRAMES.add(2_000_000);
                        dev.magnitude.client.visual.FootprintRenderer.TICK_TIMES.add(1_000_000);
                        click("gui.magnitude.refresh");
                        var details=SettingsScreen.class.getDeclaredMethod("details");details.setAccessible(true);
                        var lines=(java.util.List<?>)details.invoke(screen);
                        check(lines.size()==6&&lines.stream().anyMatch(line->((Component)line).getString().contains(Component.translatable("gui.magnitude.frame").getString()))
                            &&lines.stream().anyMatch(line->((Component)line).getString().contains("2.00")),"offline refresh displays newly sampled local diagnostics without a server snapshot");
                        check(SettingsConnection.view==null&&!SettingsConnection.busy(),"offline diagnostics never fabricate server state or send a request");
                        checkLayout();clickLiteral(">");checkLayout();
                    }
                    case 22 -> {
                        screenshot(client,"diagnostics-offline");
                        String[] expected=client.options.languageCode.equals("zh_cn")?new String[]{"乘法","加法","设定","交换","转移"}:new String[]{"Multiply","Add","Set","Swap","Transfer"};
                        for(var mode:dev.magnitude.content.ToolMode.values()){
                            check(mode.label().getString().equals(expected[mode.storedId]),"tool mode has a localized readable name: "+mode.command);
                            String feedback=Component.translatable("message.magnitude.tool_mode",mode.label()).getString();
                            check(feedback.contains(expected[mode.storedId])&&!feedback.matches(".*[0-4].*"),"localized mode feedback names the operation without numeric mapping: "+mode.command);
                        }
                    }
                    case 23 -> {Files.writeString(Path.of("gui-results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",true,"passed",passed)));client.stop();}
                    default -> {return;}
                }
                stage++;
            }catch(Throwable error){
                error.printStackTrace();try{Files.writeString(Path.of("gui-results.json"),new com.google.gson.Gson().toJson(Map.of("success",false,"failure",error.toString(),"passed",passed)));}catch(Exception ignored){}client.stop();
            }
        });
    }
    private EditBox searchBox(){return screen.children().stream().filter(x->x instanceof EditBox box&&box.getMessage().equals(Component.translatable("gui.magnitude.search"))).map(x->(EditBox)x).findFirst().orElseThrow();}
    private void search(String text){searchBox().setValue(text);}
    private void checkLayout(){
        var widgets=screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.AbstractWidget).map(x->(net.minecraft.client.gui.components.AbstractWidget)x).toList();
        check(widgets.stream().allMatch(w->w.getX()>=0&&w.getY()>=0&&w.getRight()<=320&&w.getBottom()<=240),"search controls fit 320x240");
        boolean overlap=false;for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);overlap|=a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY();}
        check(!overlap,"search controls and content do not overlap");
    }
    private void clickLiteral(String label){var button=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().equals(label)).map(x->(Button)x).findFirst().orElseThrow();check(button.active,"pagination enabled");button.onPress(null);}
}
