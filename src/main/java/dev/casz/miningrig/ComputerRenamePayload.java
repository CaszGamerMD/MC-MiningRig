package dev.casz.miningrig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A rename is accepted only while the sender actually has this computer menu open. */
public record ComputerRenamePayload(int slot,String name) implements CustomPacketPayload {
    public static final Type<ComputerRenamePayload> TYPE=new Type<>(MiningRig.id("computer_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ComputerRenamePayload> CODEC=
        StreamCodec.composite(ByteBufCodecs.INT,ComputerRenamePayload::slot,
            ByteBufCodecs.STRING_UTF8,ComputerRenamePayload::name,ComputerRenamePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
