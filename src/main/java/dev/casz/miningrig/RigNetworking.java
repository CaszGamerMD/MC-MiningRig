package dev.casz.miningrig;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Register codecs on both sides; validate all client-requested edits on the server. */
public final class RigNetworking {
    private RigNetworking(){}
    public static void initialize(){
        PayloadTypeRegistry.clientboundPlay().register(ComputerStatePayload.TYPE,ComputerStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CoordinatorStatePayload.TYPE,CoordinatorStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CoordinatorRenamePayload.TYPE,CoordinatorRenamePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ComputerSettingsPayload.TYPE,ComputerSettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CoordinatorRenamePayload.TYPE,(payload,ctx)->{
            if(payload.face()<0||payload.face()>5||payload.name().length()>32)return;
            if(ctx.player().containerMenu instanceof CoordinatorMenu menu && menu.coordinator()!=null)
                menu.coordinator().nameContainer(net.minecraft.core.Direction.from3DDataValue(payload.face()),payload.name());
        });
        ServerPlayNetworking.registerGlobalReceiver(ComputerSettingsPayload.TYPE,(payload,ctx)->{
            if(ctx.player().containerMenu instanceof ComputerMenu menu && menu.computer()!=null
                && menu.computer().selected()==payload.slot())
                menu.computer().applySettings(payload.radius(),payload.depth(),payload.fillTries());
        });
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
