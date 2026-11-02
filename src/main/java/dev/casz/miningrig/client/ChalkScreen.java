package dev.casz.miningrig.client;

import dev.casz.miningrig.ChalkMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Target Chalk sketch editor. Right-click air to open without destroying the
 * draft; the selected mode and point count are synchronized from the server.
 */
public final class ChalkScreen extends AbstractContainerScreen<ChalkMenu> {
    private static final int BG=0xff101b1b,PANEL=0xff1c3535,WHITE=0xffeefbfa,
        ACCENT=0xff64ecd2,MUTED=0xffa3c7c2;
    private Button undo,finish,clear,mode;
    public ChalkScreen(ChalkMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,312,186);
        titleLabelY=10;
        inventoryLabelY=9000;
    }
    private void click(int id){
        if(minecraft.gameMode!=null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override protected void init(){
        super.init();
        mode=addRenderableWidget(Button.builder(Component.literal("MODE"),b->click(3))
            .bounds(leftPos+160,topPos+36,136,22).build());
        undo=addRenderableWidget(Button.builder(Component.literal("UNDO LAST POINT"),b->click(0))
            .bounds(leftPos+15,topPos+86,132,24).build());
        finish=addRenderableWidget(Button.builder(Component.literal("FINISH OUTLINE"),b->click(1))
            .bounds(leftPos+165,topPos+86,132,24).build());
        clear=addRenderableWidget(Button.builder(Component.literal("CLEAR DRAFT"),b->click(2))
            .bounds(leftPos+15,topPos+121,132,21).build());
        addRenderableWidget(Button.builder(Component.literal("BACK TO DRAWING"),b->onClose())
            .bounds(leftPos+165,topPos+121,132,21).build());
        updateButtons();
    }
    private void updateButtons(){
        if(undo==null)return;
        int count=menu.points();
        undo.active=count>0;
        finish.active=count>=3;
        clear.active=count>0;
        mode.active=count==0;
        mode.setMessage(Component.literal(menu.mode().name()));
    }
    @Override protected void containerTick(){
        super.containerTick();
        updateButtons();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,BG);
        g.fill(leftPos+8,topPos+7,leftPos+imageWidth-8,topPos+29,PANEL);
        g.fill(leftPos+8,topPos+32,leftPos+imageWidth-8,topPos+77,PANEL);
        g.fill(leftPos+8,topPos+80,leftPos+imageWidth-8,topPos+150,PANEL);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.text(font,"TARGET CHALK",leftPos+16,topPos+14,ACCENT,false);
        g.text(font,"DRAW MODE",leftPos+16,topPos+43,WHITE,false);
        g.text(font,"POINTS: "+menu.points()+" / "+menu.maxPoints(),
            leftPos+16,topPos+65,ACCENT,false);
        g.text(font,"Undo removes the most recent click; no world blocks change.",
            leftPos+15,topPos+156,MUTED,false);
        g.text(font,"Click the first mark or sneak-use air to finish, too.",
            leftPos+15,topPos+169,MUTED,false);
    }
}
