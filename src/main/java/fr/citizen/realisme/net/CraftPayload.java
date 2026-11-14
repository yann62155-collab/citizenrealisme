package fr.citizen.realisme.net;

import fr.citizen.realisme.CitizenRealisme;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * État du craft affiché sur l'écran de fabrication des joueurs PC.
 * visible=false : rien à afficher. running : fabrication en cours (progress 0..1).
 */
public record CraftPayload(boolean visible, boolean running, float progress, int rate, String text) implements CustomPacketPayload {
    public static final Type<CraftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "craft"));
    public static final StreamCodec<FriendlyByteBuf, CraftPayload> CODEC = CustomPacketPayload.codec(CraftPayload::write, CraftPayload::new);
    public static final CraftPayload HIDDEN = new CraftPayload(false, false, 0, 0, "");

    public CraftPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(), buf.readUtf(256));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(visible);
        buf.writeBoolean(running);
        buf.writeFloat(progress);
        buf.writeVarInt(rate);
        buf.writeUtf(text, 256);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
