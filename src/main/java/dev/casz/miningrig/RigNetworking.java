package dev.casz.miningrig;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Register codecs on both sides; validate all client-requested edits on the server. */
public final class RigNetworking {
    private RigNetworking(){}
    public static void initialize(){
        PayloadTypeRegistry.clientboundPlay().register(ComputerStatePayload.TYPE,ComputerStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ComputerRenamePayload.TYPE,ComputerRenamePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ComputerRenamePayload.TYPE,(payload,ctx)->{
            if(payload.name().length()>24 || payload.name().isBlank())return;
            if(ctx.player().containerMenu instanceof ComputerMenu menu && menu.computer()!=null
                && menu.computer().selected()==payload.slot()) {
                menu.computer().rename(payload.slot(),payload.name());
            }
        });
    }
}
