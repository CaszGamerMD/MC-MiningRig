package dev.casz.miningrig.client;

import java.util.Arrays;
import dev.casz.miningrig.ComputerMenu;
import dev.casz.miningrig.ComputerRenamePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Four readable drill status cards, with controls and a text box to rename selected drill. */
public final class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private final String[][] rows=new String[4][11];
    private int selected;
    private EditBox rename;
    private boolean initial=true;
    public ComputerScreen(ComputerMenu menu,Inventory player,Component title){
        super(menu,player,title,360,330);
        titleLabelY=8;inventoryLabelY=326;
        for(int i=0;i<4;i++){
            rows[i]=new String[]{"0","Drill "+(i+1),"Not placed","Empty","-1","2","12","O","0","0","24"};
        }
    }
    private void click(int id){
        if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override protected void init(){
        super.init();
        for(int i=0;i<4;i++){
            final int n=i;
            addRenderableWidget(Button.builder(Component.literal("Select"),btn->click(n))
                .bounds(leftPos+260,topPos+20+i*48,48,19).build());
            addRenderableWidget(Button.builder(Component.literal("Start/Stop"),btn->click(10+n))
                .bounds(leftPos+260,topPos+41+i*48,86,17).build());
        }
        addRenderableWidget(Button.builder(Component.literal("R-"),b->click(30)).bounds(leftPos+8,topPos+223,36,18).build());
        addRenderableWidget(Button.builder(Component.literal("R+"),b->click(31)).bounds(leftPos+46,topPos+223,36,18).build());
        addRenderableWidget(Button.builder(Component.literal("D-"),b->click(32)).bounds(leftPos+91,topPos+223,36,18).build());
        addRenderableWidget(Button.builder(Component.literal("D+"),b->click(33)).bounds(leftPos+129,topPos+223,36,18).build());
        addRenderableWidget(Button.builder(Component.literal("Shape"),b->click(34)).bounds(leftPos+171,topPos+223,43,18).build());
        addRenderableWidget(Button.builder(Component.literal("Wall"),b->click(35))
            .bounds(leftPos+216,topPos+223,55,18).build());
        addRenderableWidget(Button.builder(Component.literal("Unlink"),b->click(20+selected))
            .bounds(leftPos+274,topPos+223,74,18).build());
        // Safety control is shared by the local block UI and remote tablet.
        // The limit applies separately at EVERY fill coordinate, not to
        // the total number of blocks in the hole.
        addRenderableWidget(Button.builder(Component.literal("-25"),b->click(38))
            .bounds(leftPos+156,topPos+253,42,18).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),b->click(36))
            .bounds(leftPos+201,topPos+253,42,18).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),b->click(37))
            .bounds(leftPos+246,topPos+253,42,18).build());
        addRenderableWidget(Button.builder(Component.literal("+25"),b->click(39))
            .bounds(leftPos+291,topPos+253,58,18).build());
        rename=new EditBox(font,leftPos+8,topPos+296,236,19,Component.literal("Drill nickname"));
        rename.setMaxLength(24);rename.setValue(rows[selected][1]);
        addRenderableWidget(rename);
        addRenderableWidget(Button.builder(Component.literal("Save name"),b->{
            ClientPlayNetworking.send(new ComputerRenamePayload(selected,rename.getValue()));
        }).bounds(leftPos+250,topPos+296,99,19).build());
    }
    public void updateState(String raw){
        String[] lines=raw.split("\n",-1);
        if(lines.length<5)return;
        try {
            int next=Integer.parseInt(lines[0]);
            if(next<0||next>3)return;
            boolean changed=next!=selected || initial;
            selected=next;initial=false;
            for(int i=0;i<4;i++){
                String[] entry=lines[i+1].split("\t",-1);
                if(entry.length>=11)rows[i]=Arrays.copyOf(entry,11);
            }
            if(changed && rename!=null)rename.setValue(rows[selected][1]);
        }catch(NumberFormatException ignored){}
    }
    private static String eta(String s){
        try{
            int seconds=Integer.parseInt(s);
            if(seconds<0)return "ETA: --";
            if(seconds==0)return "ETA: Complete";
            if(seconds>=3600)return "ETA ~"+(seconds/3600)+"h "+((seconds%3600)/60)+"m";
            if(seconds>=60)return "ETA ~"+(seconds/60)+"m "+(seconds%60)+"s";
            return "ETA ~"+seconds+"s";
        }catch(NumberFormatException e){return "ETA: --";}
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff13202b);
        for(int i=0;i<4;i++){
            int y=topPos+18+i*48;
            g.fill(leftPos+5,y,leftPos+354,y+46,i==selected?0xff315970:0xff243742);
            g.fill(leftPos+5,y,leftPos+8,y+46,i==selected?0xff64cbff:0xff647885);
        }
        g.fill(leftPos+5,topPos+218,leftPos+354,topPos+246,0xff263946);
        g.fill(leftPos+5,topPos+250,leftPos+354,topPos+276,0xff263946);
        g.fill(leftPos+5,topPos+285,leftPos+354,topPos+322,0xff263946);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        for(int i=0;i<4;i++){
            String[] r=rows[i];
            int y=topPos+22+i*48;
            String name=r[1].length()>21?r[1].substring(0,21):r[1];
            g.text(font,(i+1)+". "+name,leftPos+13,y,0xffd5edff,false);
            g.text(font,"XYZ: "+r[2],leftPos+13,y+12,0xffa0c4d5,false);
            g.text(font,"Layers: "+r[9]+"/"+r[6],leftPos+160,y+12,0xffffeab3,false);
            String status=r[3].length()>23?r[3].substring(0,23):r[3];
            g.text(font,status,leftPos+13,y+24,0xfffee0a4,false);
            g.text(font,eta(r[4]),leftPos+140,y+24,0xff8bffae,false);
        }
        g.text(font,"Selected: R "+rows[selected][5]+" | D "+rows[selected][6]
            +" | "+(rows[selected][7].equals("O")?"Circle":"Square")+" | Wall: "+switch(rows[selected][8]){case "1"->"Air";case "2"->"Replace";default->"Off";},leftPos+9,topPos+207,0xffffffff,false);
        g.text(font,"Fill tries / block: "+rows[selected][10],
            leftPos+9,topPos+257,0xffbde4f5,false);
        g.text(font,"Nickname for selected drill:",leftPos+9,topPos+286,0xffbde4f5,false);
    }
}
