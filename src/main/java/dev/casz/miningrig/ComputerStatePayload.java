package dev.casz.miningrig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One selected slot plus four tab-delimited drill summaries, updated server-side. */
public record ComputerStatePayload(String state) implements CustomPacketPayload {
    public static final Type<ComputerStatePayload> TYPE=new Type<>(MiningRig.id("computer_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ComputerStatePayload> CODEC=
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8,ComputerStatePayload::state,ComputerStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
