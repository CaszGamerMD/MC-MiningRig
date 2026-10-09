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
        super(menu,player,title);
        imageWidth=176;imageHeight=214;inventoryLabelY=120;titleLabelY=7;
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
    }
    @Override protected void containerTick(){
        super.containerTick();
        for(Direction dir:Direction.values()){
            Button b=buttons.get(dir);if(b==null)continue;
            String label=switch(dir){case DOWN->"D";case UP->"U";case NORTH->"N";case SOUTH->"S";case WEST->"W";case EAST->"E";};
            int mode=menu.mode(dir);
            b.setMessage(Component.literal(label+":"+(mode==0?"-":mode==1?"I":"O")));
            b.active=menu.connected(dir);
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
        g.text(font,"ITEMS "+menu.stored()+"/192",leftPos+7,topPos+110,0xff9fe5ff,false);
        g.text(font,"I=in  O=out  -=off",leftPos+7,topPos+44,0xffffffff,false);
    }
}
