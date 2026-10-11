package dev.casz.miningrig.client;

import dev.casz.miningrig.DrillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DrillScreen extends AbstractContainerScreen<DrillMenu> {
    private Button wallChoice;
    public DrillScreen(DrillMenu menu,Inventory player,Component title) {
        super(menu,player,title,176,236);
        inventoryLabelY=136;
        titleLabelY=7;
    }
    private void click(int id){
        if(minecraft.gameMode!=null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override protected void init(){
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Next storage"),b->click(2))
            .bounds(leftPos+8,topPos+48,84,20).build());
        wallChoice=addRenderableWidget(Button.builder(Component.literal("Wall: Order"),b->click(3))
            .bounds(leftPos+96,topPos+48,72,20).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->click(0))
            .bounds(leftPos+126,topPos+113,20,18).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->click(1))
            .bounds(leftPos+147,topPos+113,20,18).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff19242d);
        g.fill(leftPos+4,topPos+14,leftPos+172,topPos+44,0xff35434c);
        g.fill(leftPos+4,topPos+74,leftPos+172,topPos+109,0xff35434c);
        g.fill(leftPos+4,topPos+143,leftPos+172,topPos+232,0xff273640);
        for(int i=0;i<3;i++)
            g.fill(leftPos+61+i*18,topPos+18,leftPos+79+i*18,topPos+36,0xff657986);
        for(int i=0;i<9;i++)
            g.fill(leftPos+7+i*18,topPos+79,leftPos+25+i*18,topPos+97,0xff657986);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(wallChoice!=null)wallChoice.setMessage(Component.literal(
            menu.randomWalls()?"Wall: Random":"Wall: Order"));
        super.extractRenderState(g,mouseX,mouseY,delta);
        g.text(font,"3 DRILL BITS",leftPos+7,topPos+27,0xffffffff,false);
        g.text(font,menu.bankName()+" "+menu.stored()+"/64",leftPos+7,topPos+116,0xff9fe5ff,false);
        g.text(font,(menu.page()+1)+"/8",leftPos+99,topPos+116,0xffffffff,false);
    }
}
