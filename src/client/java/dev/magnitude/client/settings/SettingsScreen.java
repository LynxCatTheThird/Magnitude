package dev.magnitude.client.settings;

import dev.magnitude.config.ConfigField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

/** Native paginated settings screen; server edits are drafts until acknowledged. */
public final class SettingsScreen extends Screen {
    private static final String[] TABS={"overview","client","player","server","diagnostics"};
    private final Screen parent;
    private final Map<String,String> drafts=new LinkedHashMap<>();
    private int tab,page,rows,submitted=-1;
    private String localResult="ready";
    private boolean wasBusy;
    private long connectionEpoch=SettingsConnection.epoch;
    private dev.magnitude.config.ConfigView seenView;
    private record Row(String id,boolean bool,double value){}
    public SettingsScreen(Screen parent){super(Component.translatable("gui.magnitude.title"));this.parent=parent;}
    private Component tr(String key,Object...args){return Component.translatable("gui.magnitude."+key,args);}
    private Button button(Component label,int x,int y,int w,Button.OnPress press){return addRenderableWidget(Button.builder(label,press).bounds(x,y,w,20).build());}
    private boolean local(){return tab==1;}
    private boolean editable(){return local()||SettingsConnection.view!=null&&(tab==2||tab==3&&SettingsConnection.view.administrator());}
    private String draftKey(String id){return tab+":"+id;}
    private List<Row> fields(){
        var list=new ArrayList<Row>();
        if(local())ClientPreferences.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->list.add(new Row(e.getKey(),e.getKey().equals("smoothing")||e.getKey().equals("hiddenNames"),e.getValue())));
        else if(SettingsConnection.view!=null) {
            if(tab==2)for(String id:new String[]{"terrain","pressure","resize","carry"})list.add(new Row(id,true,SettingsConnection.view.personal().get(id)));
            if(tab==3)for(var field:ConfigField.values())list.add(new Row(field.id,field.bool,SettingsConnection.view.server().get(field.id)));
        }
        return list;
    }
    private List<Component> details(){
        var lines=new ArrayList<Component>();var view=SettingsConnection.view;
        if(view==null){lines.add(tr(SettingsConnection.supported()?"loading":"offline"));return lines;}
        if(tab==0) {
            lines.add(tr("size",String.format(java.util.Locale.ROOT,"%.3f",view.size())));
            lines.add(tr("terrainState",Component.translatable("config.magnitude.reason."+view.terrainReason())));
            lines.add(tr("pressureState",Component.translatable("config.magnitude.reason."+view.pressureReason())));
            lines.add(tr("currentBackend"));lines.add(tr("scopeHint"));lines.add(tr("protectionHint"));
        }else {
            lines.add(tr("serverMs",String.format(java.util.Locale.ROOT,"%.2f",view.serverTickMs())));
            lines.add(tr("moveMs",String.format(java.util.Locale.ROOT,"%.2f / %.2f",view.moveAvgMs(),view.moveMaxMs())));
            lines.add(tr("denied",view.denied(),view.samples()));lines.add(tr("pending",view.pending()));
            lines.add(tr("failure",view.lastFailure()));lines.add(tr("reason",view.reason()));lines.add(tr("diagnosticHint"));
        }
        return lines;
    }
    @Override protected void init(){
        rows=Math.max(1,(height-158)/26);int left=Math.max(8,(width-460)/2),w=Math.min(460,width-16);
        int tabW=w/5;
        for(int i=0;i<TABS.length;i++){final int target=i;var b=button(tr("tab."+TABS[i]),left+i*tabW,30,tabW-2,ignored->{tab=target;page=0;localResult="ready";rebuildWidgets();});b.active=tab!=i;}
        var fields=fields();var details=details();int total=tab==0||tab==4?details.size():fields.size();int pages=Math.max(1,(total+rows-1)/rows);page=Math.clamp(page,0,pages-1);
        for(int index=page*rows;index<Math.min(total,(page+1)*rows);index++) {
            int y=76+(index-page*rows)*26;
            if(tab==0||tab==4)continue;
            Row row=fields.get(index);String key=draftKey(row.id);String value=drafts.getOrDefault(key,Double.toString(row.value));
            Component label=Component.translatable("config.magnitude.field."+row.id);
            Component hint=Component.translatable("config.magnitude.hint."+row.id);
            if(row.bool) {
                var b=button(Component.translatable(Double.parseDouble(value)==1?"options.on":"options.off"),left+w-100,y,100,pressed->{
                    double changed=Double.parseDouble(drafts.getOrDefault(key,Double.toString(row.value)))==1?0:1;
                    if(changed==row.value)drafts.remove(key);else drafts.put(key,Double.toString(changed));
                    pressed.setMessage(Component.translatable(changed==1?"options.on":"options.off"));
                });b.setTooltip(Tooltip.create(label.copy().append("\n").append(hint)));b.active=editable()&&!SettingsConnection.busy();
            }else {
                var edit=new EditBox(font,left+w-100,y,100,20,label);edit.setMaxLength(32);edit.setValue(value);edit.setResponder(text->{
                    try{if(Double.parseDouble(text)==row.value){drafts.remove(key);return;}}catch(NumberFormatException ignored){}
                    drafts.put(key,text);
                });edit.setTooltip(Tooltip.create(hint));edit.setEditable(editable());edit.active=editable()&&!SettingsConnection.busy();addRenderableWidget(edit);
            }
        }
        if(pages>1){button(Component.literal("<"),left,height-76,35,b->{page--;rebuildWidgets();}).active=page>0;button(Component.literal(">"),left+40,height-76,35,b->{page++;rebuildWidgets();}).active=page+1<pages;}
        if(tab==1||tab==2||tab==3) {
            var apply=button(tr("apply"),left+90,height-48,90,b->apply());apply.active=editable()&&!SettingsConnection.busy();
            button(tr("discard"),left+185,height-48,90,b->{drafts.keySet().removeIf(key->key.startsWith(tab+":"));localResult="ready";rebuildWidgets();});
        }
        var refresh=button(tr("refresh"),left+w-90,height-76,90,b->SettingsConnection.request(0,Map.of()));refresh.active=SettingsConnection.supported()&&!SettingsConnection.busy();
        button(tr("close"),left+w-90,height-48,90,b->onClose());wasBusy=SettingsConnection.busy();seenView=SettingsConnection.view;
    }
    @Override public void added(){if(SettingsConnection.view==null&&SettingsConnection.supported())SettingsConnection.request(0,Map.of());}
    public void received(){
        if(submitted>=0&&SettingsConnection.result.equals("applied")){int applied=submitted;drafts.keySet().removeIf(key->key.startsWith(applied+":"));}
        submitted=-1;rebuildWidgets();
    }
    private void apply(){
        if(!editable()||!local()&&SettingsConnection.busy())return;
        localResult="ready";
        var patch=new LinkedHashMap<String,Double>();
        try {
            for(var row:fields()) {
                double value=Double.parseDouble(drafts.getOrDefault(draftKey(row.id),Double.toString(row.value)));
                if(!Double.isFinite(value))throw new IllegalArgumentException();
                if(tab==3&&!ConfigField.find(row.id).valid(value))throw new IllegalArgumentException();
                if(local()||value!=row.value)patch.put(row.id,value);
            }
            if(patch.isEmpty()){localResult="unchanged";return;}
            if(local()){localResult=ClientPreferences.save(patch)?"applied":ClientPreferences.lastError;if(localResult.equals("applied"))drafts.keySet().removeIf(key->key.startsWith("1:"));}
            else if(SettingsConnection.request(tab==2?1:2,patch))submitted=tab;
            rebuildWidgets();
        }catch(RuntimeException error){localResult="invalid";}
    }
    @Override public void tick(){
        if(connectionEpoch!=SettingsConnection.epoch){connectionEpoch=SettingsConnection.epoch;drafts.keySet().removeIf(key->!key.startsWith("1:"));submitted=-1;rebuildWidgets();}
        else if(wasBusy!=SettingsConnection.busy()||seenView!=SettingsConnection.view)rebuildWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        g.centeredText(font,title,width/2,10,0xffffffff);
        int left=Math.max(8,(width-460)/2),w=Math.min(460,width-16);
        g.text(font,tr("scope."+TABS[tab]),left,58,0xffbbbbbb);
        var fields=fields();var details=details();int total=tab==0||tab==4?details.size():fields.size();
        for(int index=page*rows;index<Math.min(total,(page+1)*rows);index++) {
            int y=76+(index-page*rows)*26;
            if(tab==0||tab==4) {
                var wrapped=font.split(details.get(index),w);
                for(int line=0;line<Math.min(2,wrapped.size());line++)g.text(font,wrapped.get(line),left,y+2+line*10,0xffeeeeee);
                if(mouseX>=left&&mouseX<left+w&&mouseY>=y&&mouseY<y+26)g.setTooltipForNextFrame(font,details.get(index),mouseX,mouseY);
            }
            else g.text(font,font.plainSubstrByWidth(Component.translatable("config.magnitude.field."+fields.get(index).id).getString(),w-110),left,y+5,0xffeeeeee);
        }
        boolean dirty=drafts.keySet().stream().anyMatch(key->key.startsWith(tab+":"));
        int pages=Math.max(1,(total+rows-1)/rows);
        if(pages>1)g.text(font,Component.literal((page+1)+" / "+pages),left+85,height-70,0xffbbbbbb);
        if(dirty)g.text(font,tr("unsaved"),left+(pages>1?145:90),height-70,0xffffcc77);
        String result=local()||!localResult.equals("ready")?localResult:SettingsConnection.result;
        g.text(font,font.plainSubstrByWidth(Component.translatable("config.magnitude.result."+result).getString(),w),left,height-20,result.equals("invalid")||result.equals("saveFailed")?0xffff8888:0xffbbbbbb);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        if(!drafts.isEmpty()) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{if(confirmed){drafts.clear();minecraft.gui.setScreen(parent);}else minecraft.gui.setScreen(this);},tr("discardTitle"),tr("discardBody")));
        }else minecraft.gui.setScreen(parent);
    }
}
