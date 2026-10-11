package dev.casz.miningrig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CoordinatorStatePayload(String names) implements CustomPacketPayload {
    public static final Type<CoordinatorStatePayload> TYPE=new Type<>(MiningRig.id("cc_names"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CoordinatorStatePayload> CODEC=
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8,CoordinatorStatePayload::names,
            CoordinatorStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
