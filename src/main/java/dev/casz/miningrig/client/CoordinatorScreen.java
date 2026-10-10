package dev.casz.miningrig.client;

import java.util.EnumMap;
import dev.casz.miningrig.CoordinatorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Six live connection indicators and a dedicated external storage editor. */
public final class CoordinatorScreen extends AbstractContainerScreen<CoordinatorMenu> {
    private static final Direction[] SIDES={
        Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private final EnumMap<Direction,Button> buttons=new EnumMap<>(Direction.class);
    private Direction selected=Direction.UP;
    private Button typeButton,emptyButton;
    public CoordinatorScreen(CoordinatorMenu menu,Inventory player,Component title){
        super(menu,player,title,176,258);
        inventoryLabelY=158;titleLabelY=7;
    }
    private void click(int id){
        if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override protected void init(){
        super.init();buttons.clear();
        for(int i=0;i<6;i++){
            final Direction side=SIDES[i];
            Button button=Button.builder(Component.literal("?"),b->{selected=side;})
                .bounds(leftPos+5+i*28,topPos+22,27,20).build();
            buttons.put(side,addRenderableWidget(button));
        }
        typeButton=addRenderableWidget(Button.builder(Component.literal("Type"),b->click(selected.get3DDataValue()))
            .bounds(leftPos+7,topPos+57,100,18).build());
        emptyButton=addRenderableWidget(Button.builder(Component.literal("Pause"),b->click(8+selected.get3DDataValue()))
            .bounds(leftPos+110,topPos+57,58,18).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->click(6))
            .bounds(leftPos+128,topPos+145,19,16).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->click(7))
            .bounds(leftPos+150,topPos+145,19,16).build());
    }
    private String code(int mode){
        return switch(mode){
            case 1->"F";case 2,3->"W";case 4->"O";case 5->"C";default->"-";
        };
    }
    @Override protected void containerTick(){
        super.containerTick();
        for(Direction side:SIDES){
            Button button=buttons.get(side);
            int type=menu.mode(side);
            String letter=switch(side){case UP->"U";case DOWN->"D";case NORTH->"N";
                case SOUTH->"S";case EAST->"E";case WEST->"W";};
            button.setMessage(Component.literal(letter+":"+code(type)));
            button.active=true;
        }
        int mode=menu.mode(selected);
        boolean external=menu.containerSide(selected)&&mode!=5;
        typeButton.active=external;
        emptyButton.active=external&&(mode==2||mode==3);
        typeButton.setMessage(Component.literal(switch(mode){
            case 1->"Filler input";case 2,3->"Wall input";case 4->"Output";
            case 5->"Auto network";default->"Not assigned";
        }));
        emptyButton.setMessage(Component.literal(mode==3?"Continue":"Pause"));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff1b2530);
        g.fill(leftPos+4,topPos+16,leftPos+172,topPos+80,0xff32434b);
        g.fill(leftPos+4,topPos+82,leftPos+172,topPos+145,0xff32434b);
        for(int i=0;i<27;i++)g.fill(leftPos+7+(i%9)*18,topPos+87+(i/9)*18,
            leftPos+25+(i%9)*18,topPos+105+(i/9)*18,0xff637887);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"STORAGE "+menu.stored()+"/192",leftPos+7,topPos+148,0xff9fe5ff,false);
        g.text(font,"PG "+(menu.page()+1)+"/8",leftPos+89,topPos+148,0xffffffff,false);
        for(int i=0;i<6;i++){
            Direction side=SIDES[i];
            int mode=menu.mode(side);
            int color=switch(mode){
                case 1->0xffff4f4f;      // filler input red
                case 2,3->0xffffd34f;    // wall input yellow
                case 4->0xff45df72;      // output green
                case 5->0xff39e8dd;      // CC/drill teal
                default->0xff5e6c78;
            };
            int x=leftPos+5+i*28;
            g.fill(x,topPos+43,x+26,topPos+47,color);
            if(selected==side)g.fill(x,topPos+48,x+26,topPos+49,0xffffffff);
        }
        g.text(font,"Selected: "+selected.getName().toUpperCase(),leftPos+7,topPos+49,0xffc1d4dd,false);
        if(menu.drillSide(Direction.DOWN))
            g.text(font,"DRILL LINK",leftPos+113,topPos+8,0xff39e8dd,false);
    }
}
