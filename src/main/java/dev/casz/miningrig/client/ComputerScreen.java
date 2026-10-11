package dev.casz.miningrig.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import dev.casz.miningrig.ComputerMenu;
import dev.casz.miningrig.ComputerRenamePayload;
import dev.casz.miningrig.ComputerSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Full-screen black/green console, shared by placed computer and tablet. */
public final class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final int GREEN=0xff72ff9b,MUTED=0xff9bbbad,WHITE=0xffe5fff0;
    private static final int DARK=0xff060f0b,PANEL=0xff122319;
    private final String[][] rows=new String[4][11];
    private final List<String[]> storage=new ArrayList<>();
    private final List<Button> drillButtons=new ArrayList<>();
    private int selected,tab,storagePage;
    private boolean initial=true,alert;
    private EditBox rename,radius,depth,tries;
    private Button drillTab,networkTab,prev,next;
    private int bodyTop,editorTop,uiWidth,uiHeight;
    private Button saveName,applySettings;
    public ComputerScreen(ComputerMenu menu,Inventory player,Component title){
        super(menu,player,title,460,330);
        for(int i=0;i<4;i++)
            rows[i]=new String[]{"0","Drill "+(i+1),"Not placed","Empty","-1","2","12","O","0","0","24"};
        titleLabelY=7;inventoryLabelY=9000;
    }
    private void click(int id){
        if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    private Button add(String name,int x,int y,int w,int h,int id,boolean drillOnly){
        Button button=addRenderableWidget(Button.builder(Component.literal(name),b->click(id))
            .bounds(x,y,w,h).build());
        if(drillOnly)drillButtons.add(button);
        return button;
    }
    @Override protected void init(){
        super.init();
        uiWidth=Math.max(460,width-24);
        uiHeight=Math.max(310,height-24);
        leftPos=Math.max(0,(width-uiWidth)/2);
        topPos=Math.max(0,(height-uiHeight)/2);
        bodyTop=topPos+80;
        editorTop=topPos+uiHeight-112;
        drillButtons.clear();
        drillTab=addRenderableWidget(Button.builder(Component.literal("DRILLS"),b->{
            tab=0;refreshTabs();
        }).bounds(leftPos+12,topPos+32,96,22).build());
        networkTab=addRenderableWidget(Button.builder(Component.literal("NETWORK STORAGE"),b->{
            tab=1;refreshTabs();
        }).bounds(leftPos+112,topPos+32,142,22).build());
        add("START ALL",leftPos+uiWidth-352,topPos+32,108,22,42,true);
        add("STOP ALL",leftPos+uiWidth-240,topPos+32,108,22,43,true);
        add("MUTE ALERT",leftPos+uiWidth-128,topPos+32,116,22,41,false);
        int rowHeight=Math.max(36,Math.min(57,(editorTop-bodyTop-12)/4));
        int x=leftPos+uiWidth-290;
        for(int i=0;i<4;i++){
            int n=i,y=bodyTop+i*rowHeight;
            add("SELECT",x,y+2,66,21,n,true);
            add("START/STOP",x+70,y+2,95,21,10+n,true);
            add("RESET",x+169,y+2,58,21,50+n,true);
            add("REMOVE",x+231,y+2,58,21,20+n,true);
        }
        // RESET is handled through the selected-drill control, never by unlink.
        add("RESET TASK",leftPos+14,editorTop+79,104,22,40,true);
        add("RESCAN",leftPos+122,editorTop+79,85,22,44,true);
        add("SHAPE",leftPos+211,editorTop+79,68,22,34,true);
        add("WALL MODE",leftPos+283,editorTop+79,103,22,35,true);
        prev=addRenderableWidget(Button.builder(Component.literal("PREVIOUS"),b->{
            storagePage=Math.max(0,storagePage-1);
        }).bounds(leftPos+uiWidth-205,topPos+58,93,19).build());
        next=addRenderableWidget(Button.builder(Component.literal("NEXT"),b->{
            storagePage++;
        }).bounds(leftPos+uiWidth-108,topPos+58,93,19).build());
        int fieldY=editorTop+30;
        rename=new EditBox(font,leftPos+14,fieldY,Math.max(96,(uiWidth-375)/2),20,
            Component.literal("Drill nickname"));
        rename.setMaxLength(24);
        rename.setValue(rows[selected][1]);
        addRenderableWidget(rename);
        saveName=addRenderableWidget(Button.builder(Component.literal("SAVE NAME"),b->{
            ClientPlayNetworking.send(new ComputerRenamePayload(selected,rename.getValue()));
        }).bounds(leftPos+18+rename.getWidth(),fieldY,83,20).build());
        int first=leftPos+Math.max(235,uiWidth/2);
        radius=new EditBox(font,first,fieldY,48,20,Component.literal("Radius"));
        depth=new EditBox(font,first+78,fieldY,48,20,Component.literal("Depth"));
        tries=new EditBox(font,first+156,fieldY,65,20,Component.literal("Fill tries"));
        for(EditBox box:new EditBox[]{radius,depth,tries}){
            box.setMaxLength(5);addRenderableWidget(box);
        }
        applySettings=addRenderableWidget(Button.builder(Component.literal("APPLY"),b->{
            try{
                ClientPlayNetworking.send(new ComputerSettingsPayload(selected,
                    Integer.parseInt(radius.getValue().strip()),
                    Integer.parseInt(depth.getValue().strip()),
                    Integer.parseInt(tries.getValue().strip())));
            }catch(NumberFormatException ignored){}
        }).bounds(first+225,fieldY,62,20).build());
        updateFields();
        refreshTabs();
    }
    private void updateFields(){
        if(rename!=null)rename.setValue(rows[selected][1]);
        if(radius!=null)radius.setValue(rows[selected][5]);
        if(depth!=null)depth.setValue(rows[selected][6]);
        if(tries!=null)tries.setValue(rows[selected][10]);
    }
    private void refreshTabs(){
        boolean drilling=tab==0;
        for(Button button:drillButtons)button.visible=drilling;
        prev.visible=!drilling;next.visible=!drilling;
        if(saveName!=null)saveName.visible=drilling;
        if(applySettings!=null)applySettings.visible=drilling;
        for(EditBox input:new EditBox[]{rename,radius,depth,tries})
            if(input!=null){input.visible=drilling;input.setFocused(false);}
        // Name and APPLY widgets are rendered only on the Drill page via their layout region.
    }
    @Override public boolean keyPressed(KeyEvent event){
        for(EditBox box:new EditBox[]{rename,radius,depth,tries})
            if(box!=null&&box.isFocused()&&!event.isEscape()){
                box.keyPressed(event);
                return true; // suppress E/inventory and other gameplay bindings
            }
        return super.keyPressed(event);
    }
    public void updateState(String raw){
        String[] lines=raw.split("\n",-1);
        if(lines.length<5)return;
        try{
            int nextSelected=Integer.parseInt(lines[0]);
            if(nextSelected<0||nextSelected>=4)return;
            boolean changed=nextSelected!=selected||initial;
            selected=nextSelected;initial=false;
            for(int i=0;i<4;i++){
                String[] values=lines[i+1].split("\t",-1);
                if(values.length>=11)rows[i]=Arrays.copyOf(values,11);
            }
            storage.clear();
            for(int i=5;i<lines.length;i++){
                String[] parts=lines[i].split("\t",-1);
                if(parts.length>=2&&parts[0].equals("ALERT"))alert=parts[1].equals("1");
                if(parts.length>=6&&parts[0].equals("STORAGE"))
                    storage.add(Arrays.copyOf(parts,6));
            }
            if(changed)updateFields();
            storagePage=Math.min(storagePage,Math.max(0,(storage.size()-1)/10));
        }catch(NumberFormatException ignored){}
    }
    @Override public void containerTick(){
        super.containerTick();
        // The text boxes remain editable until APPLY; server snapshots don't stomp focus.
        refreshButtonLabels();
    }
    private void refreshButtonLabels(){
        drillTab.setMessage(Component.literal(tab==0?"[ DRILLS ]":"DRILLS"));
        networkTab.setMessage(Component.literal(tab==1?"[ NETWORK STORAGE ]":"NETWORK STORAGE"));
    }
    private static String eta(String value){
        try{
            int seconds=Integer.parseInt(value);
            if(seconds<0)return "--";
            if(seconds==0)return "Complete";
            if(seconds>=3600)return (seconds/3600)+"h "+((seconds%3600)/60)+"m";
            if(seconds>=60)return (seconds/60)+"m "+(seconds%60)+"s";
            return seconds+"s";
        }catch(NumberFormatException e){return "--";}
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xff020604);
        g.fill(leftPos,topPos,leftPos+uiWidth,topPos+uiHeight,DARK);
        g.fill(leftPos+5,topPos+5,leftPos+uiWidth-5,topPos+27,PANEL);
        g.fill(leftPos+8,topPos+28,leftPos+uiWidth-8,topPos+78,0xff0c1e13);
        if(tab==0){
            int rowHeight=Math.max(36,Math.min(57,(editorTop-bodyTop-12)/4));
            for(int i=0;i<4;i++){
                int y=bodyTop+i*rowHeight;
                g.fill(leftPos+8,y,leftPos+uiWidth-8,y+rowHeight-3,
                    i==selected?0xff1c4230:0xff11271b);
                g.fill(leftPos+8,y,leftPos+11,y+rowHeight-3,
                    i==selected?GREEN:0xff365d45);
            }
            g.fill(leftPos+8,editorTop,leftPos+uiWidth-8,
                topPos+uiHeight-6,PANEL);
        }else{
            g.fill(leftPos+8,bodyTop,leftPos+uiWidth-8,
                topPos+uiHeight-8,PANEL);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"CASZUAL MINING COMPUTER",leftPos+14,topPos+12,GREEN,false);
        if(alert)g.text(font,"! ACTIVE ALERT !",leftPos+uiWidth-126,topPos+14,
            0xffff7777,false);
        if(tab==0){
            int rowHeight=Math.max(36,Math.min(57,(editorTop-bodyTop-12)/4));
            for(int i=0;i<4;i++){
                String[] r=rows[i];
                int y=bodyTop+i*rowHeight;
                String name=r[1].length()>26?r[1].substring(0,26):r[1];
                g.text(font,(i+1)+". "+name,leftPos+16,y+4,WHITE,false);
                g.text(font,r[3],leftPos+16,y+16,GREEN,false);
                g.text(font,"XYZ "+r[2]+"   |   "+r[9]+"/"+r[6]+" layers   |   ETA "+eta(r[4]),
                    leftPos+16,y+29,MUTED,false);
            }
            g.text(font,"SELECTED DRILL SETTINGS",leftPos+16,editorTop+6,GREEN,false);
            g.text(font,"Nickname",leftPos+14,editorTop+20,MUTED,false);
            int first=leftPos+Math.max(235,uiWidth/2);
            g.text(font,"Radius",first,editorTop+19,MUTED,false);
            g.text(font,"Depth",first+78,editorTop+19,MUTED,false);
            g.text(font,"Fill tries",first+156,editorTop+19,MUTED,false);
            g.text(font,"Footprint: "+(rows[selected][7].equals("O")?"Circle":"Square")
                +"   Wall: "+switch(rows[selected][8]){
                    case "1"->"Air";case "2"->"Replace";default->"Off";
                },leftPos+14,editorTop+60,WHITE,false);
        }else{
            g.text(font,"CONNECTED CONTAINER VOLUMES    "+storage.size()+" total",
                leftPos+16,topPos+63,GREEN,false);
            g.text(font,"NAME",leftPos+18,bodyTop+12,GREEN,false);
            g.text(font,"TYPE",leftPos+uiWidth/3,bodyTop+12,GREEN,false);
            g.text(font,"COORDINATES",leftPos+uiWidth*2/3-56,bodyTop+12,GREEN,false);
            g.text(font,"ITEMS",leftPos+uiWidth-88,bodyTop+12,GREEN,false);
            int last=Math.min(storage.size(),(storagePage+1)*10);
            for(int i=storagePage*10;i<last;i++){
                String[] record=storage.get(i);
                int y=bodyTop+31+(i-storagePage*10)*19;
                g.fill(leftPos+14,y-2,leftPos+uiWidth-14,y+15,
                    (i%2==0)?0xff1a3222:0xff12281b);
                g.text(font,record[1],leftPos+18,y,WHITE,false);
                g.text(font,record[2],leftPos+uiWidth/3,y,MUTED,false);
                g.text(font,record[3],leftPos+uiWidth*2/3-56,y,MUTED,false);
                g.text(font,record[4]+"/"+record[5],leftPos+uiWidth-88,y,GREEN,false);
            }
            g.text(font,"Page "+(storagePage+1)+"/"+Math.max(1,(storage.size()+9)/10),
                leftPos+uiWidth-191,topPos+uiHeight-18,MUTED,false);
        }
    }
}
