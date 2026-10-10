package dev.casz.miningrig.client;

import java.util.EnumMap;
import dev.casz.miningrig.CoordinatorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CoordinatorScreen extends AbstractContainerScreen<CoordinatorMenu> {
    private final EnumMap<Direction,Button> buttons=new EnumMap<>(Direction.class);
    public CoordinatorScreen(CoordinatorMenu menu,Inventory player,Component title){
        super(menu,player,title,176,214);inventoryLabelY=120;titleLabelY=7;
    }
    @Override protected void init(){
        super.init();buttons.clear();
        int i=0;
        for(Direction side:Direction.values()){
            final int index=i++;
            Button b=Button.builder(Component.literal("?"),btn->{
                if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,side.get3DDataValue());
            }).bounds(leftPos+5+index*28,topPos+22,27,20).build();
            buttons.put(side,addRenderableWidget(b));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b->{
            if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,6);
        }).bounds(leftPos+128,topPos+113,19,16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b->{
            if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,7);
        }).bounds(leftPos+150,topPos+113,19,16).build());
    }
    @Override protected void containerTick(){
        super.containerTick();
        for(Direction dir:Direction.values()){
            Button b=buttons.get(dir);if(b==null)continue;
            String label=switch(dir){case DOWN->"D";case UP->"U";case NORTH->"N";case SOUTH->"S";case WEST->"W";case EAST->"E";};
            int mode=menu.mode(dir);
            b.setMessage(Component.literal(label+":"+(mode==0?"-":mode==1?"I":"O")));
            // Ports must be configurable before a receiver or drill is present.
            b.active=true;
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff1b2530);
        g.fill(leftPos+4,topPos+16,leftPos+172,topPos+53,0xff32434b);
        g.fill(leftPos+4,topPos+54,leftPos+172,topPos+115,0xff32434b);
        for(int i=0;i<27;i++)g.fill(leftPos+7+(i%9)*18,topPos+57+(i/9)*18,
                leftPos+25+(i%9)*18,topPos+75+(i/9)*18,0xff637887);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        super.extractRenderState(g,mouseX,mouseY,delta);
        g.text(font,"ITEMS "+menu.stored()+"/192",leftPos+7,topPos+117,0xff9fe5ff,false);
        g.text(font,"PG "+(menu.page()+1)+"/8",leftPos+89,topPos+117,0xffffffff,false);
        for(Direction side:Direction.values()){
            int x=leftPos+5+side.get3DDataValue()*28;
            int color=menu.drillSide(side)?0xff65b9ff:menu.containerSide(side)?0xffffb853:menu.connected(side)?0xff67e89b:0xff96555a;
            g.fill(x,topPos+42,x+26,topPos+45,color);
        }
        g.text(font,"I=in  O=out  -=off",leftPos+7,topPos+47,0xffffffff,false);
        if(menu.drillSide(Direction.DOWN))g.text(font,"DRILL",leftPos+135,topPos+7,0xff65b9ff,false);
    }
}
