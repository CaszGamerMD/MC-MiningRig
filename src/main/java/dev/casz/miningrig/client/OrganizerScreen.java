package dev.casz.miningrig.client;

import dev.casz.miningrig.OrganizerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Compact screen shown when sneak-right-clicking with the CC Organizer.
 * The server saves every change to the held tool, not to a temporary menu.
 */
public final class OrganizerScreen extends AbstractContainerScreen<OrganizerMenu> {
    private static final int BG=0xff0b1711,PANEL=0xff193326,GREEN=0xff8dffaf,
        TEXT=0xfff0fff4,MUTED=0xffa5bcae;
    private Button mode,less,more,preview;
    public OrganizerScreen(OrganizerMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,292,186);
        titleLabelY=9;
        inventoryLabelY=9000;
    }
    private void click(int id){
        if(minecraft.gameMode!=null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override protected void init(){
        super.init();
        mode=addRenderableWidget(Button.builder(Component.literal("MODE"),b->click(0))
            .bounds(leftPos+136,topPos+34,142,22).build());
        less=addRenderableWidget(Button.builder(Component.literal("-"),b->click(1))
            .bounds(leftPos+136,topPos+65,35,22).build());
        more=addRenderableWidget(Button.builder(Component.literal("+"),b->click(2))
            .bounds(leftPos+243,topPos+65,35,22).build());
        preview=addRenderableWidget(Button.builder(Component.literal("PREVIEW FIRST"),b->click(3))
            .bounds(leftPos+136,topPos+96,142,22).build());
        addRenderableWidget(Button.builder(Component.literal("DONE"),b->onClose())
            .bounds(leftPos+82,topPos+150,128,22).build());
        updateButtons();
    }
    private void updateButtons(){
        if(mode==null)return;
        boolean retrieval=menu.retrieval();
        mode.setMessage(Component.literal(retrieval?"RETRIEVE":"PLACE"));
        less.visible=!retrieval;more.visible=!retrieval;preview.visible=!retrieval;
        less.active=!retrieval&&menu.distance()>1;
        more.active=!retrieval&&menu.distance()<20;
        preview.setMessage(Component.literal(menu.preview()?"ON - DOUBLE CLICK":"OFF - SINGLE CLICK"));
    }
    @Override protected void containerTick(){
        super.containerTick();
        updateButtons();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,BG);
        g.fill(leftPos+7,topPos+6,leftPos+imageWidth-7,topPos+27,PANEL);
        g.fill(leftPos+7,topPos+31,leftPos+imageWidth-7,topPos+123,PANEL);
        g.fill(leftPos+7,topPos+127,leftPos+imageWidth-7,topPos+145,0xff13251b);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"MODE",leftPos+16,topPos+41,TEXT,false);
        if(!menu.retrieval()){
            g.text(font,"DISTANCE",leftPos+16,topPos+72,TEXT,false);
            g.text(font,""+menu.distance()+" BLOCKS",leftPos+180,topPos+72,GREEN,false);
            g.text(font,"PREVIEW FIRST",leftPos+16,topPos+103,TEXT,false);
            g.text(font,menu.preview()?
                "Click CC face to preview. Double-click to place.":
                "Click CC face once to place a coordinator.",
                leftPos+16,topPos+133,MUTED,false);
        }else{
            g.text(font,"REACH",leftPos+16,topPos+72,TEXT,false);
            g.text(font,"100 BLOCKS",leftPos+180,topPos+72,GREEN,false);
            g.text(font,"Aim at a visible coordinator to retrieve.",
                leftPos+16,topPos+133,MUTED,false);
        }
    }
}
