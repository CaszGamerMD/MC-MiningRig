package dev.casz.miningrig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ComputerSettingsPayload(int slot,int radius,int depth,int fillTries)
    implements CustomPacketPayload {
    public static final Type<ComputerSettingsPayload> TYPE=new Type<>(MiningRig.id("computer_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ComputerSettingsPayload> CODEC=
        StreamCodec.composite(ByteBufCodecs.INT,ComputerSettingsPayload::slot,
            ByteBufCodecs.INT,ComputerSettingsPayload::radius,
            ByteBufCodecs.INT,ComputerSettingsPayload::depth,
            ByteBufCodecs.INT,ComputerSettingsPayload::fillTries,
            ComputerSettingsPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
