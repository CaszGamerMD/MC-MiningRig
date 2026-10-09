package dev.casz.miningrig.client;

import dev.casz.miningrig.DrillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DrillScreen extends AbstractContainerScreen<DrillMenu> {
    public DrillScreen(DrillMenu menu,Inventory player,Component title) {
        super(menu,player,title); imageWidth=176;imageHeight=184;
        inventoryLabelY=90;titleLabelY=7;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff19242d);
        g.fill(leftPos+4,topPos+14,leftPos+172,topPos+44,0xff35434c);
        g.fill(leftPos+4,topPos+47,leftPos+172,topPos+77,0xff35434c);
        for(int i=0;i<3;i++)g.fill(leftPos+61+i*18,topPos+18,leftPos+79+i*18,topPos+36,0xff657986);
        for(int i=0;i<9;i++)g.fill(leftPos+7+i*18,topPos+51,leftPos+25+i*18,topPos+69,0xff657986);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        super.extractRenderState(g,mouseX,mouseY,delta);
        g.text(font,"3 DRILL BITS",leftPos+7,topPos+27,0xffffffff,false);
        g.text(font,"STORAGE "+menu.stored()+"/64",leftPos+7,topPos+78,0xff9fe5ff,false);
    }
}
