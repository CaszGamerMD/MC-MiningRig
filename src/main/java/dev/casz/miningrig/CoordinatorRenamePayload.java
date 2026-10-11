package dev.casz.miningrig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CoordinatorRenamePayload(int face,String name) implements CustomPacketPayload {
    public static final Type<CoordinatorRenamePayload> TYPE=new Type<>(MiningRig.id("cc_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CoordinatorRenamePayload> CODEC=
        StreamCodec.composite(ByteBufCodecs.INT,CoordinatorRenamePayload::face,
            ByteBufCodecs.STRING_UTF8,CoordinatorRenamePayload::name,CoordinatorRenamePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
