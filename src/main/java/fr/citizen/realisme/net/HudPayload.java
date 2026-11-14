package fr.citizen.realisme.net;

import fr.citizen.realisme.CitizenRealisme;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Poids porté, capacité et niveau d'encombrement (0 à 3), pour le HUD des joueurs PC. */
public record HudPayload(float weight, float capacity, byte level) implements CustomPacketPayload {
    public static final Type<HudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "hud"));
    public static final StreamCodec<FriendlyByteBuf, HudPayload> CODEC = CustomPacketPayload.codec(HudPayload::write, HudPayload::new);

    public HudPayload(FriendlyByteBuf buf) {
        this(buf.readFloat(), buf.readFloat(), buf.readByte());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(weight);
        buf.writeFloat(capacity);
        buf.writeByte(level);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
