package fr.citizen.realisme.net;

import fr.citizen.realisme.CitizenRealisme;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Taux de réussite (-1 = option indisponible) et catalyseurs des 3 options de la table d'enchantement. */
public record EnchantPayload(int[] rates, boolean[] ok, String[] texts) implements CustomPacketPayload {
    public static final Type<EnchantPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "enchant"));
    public static final StreamCodec<FriendlyByteBuf, EnchantPayload> CODEC = CustomPacketPayload.codec(EnchantPayload::write, EnchantPayload::read);

    public static EnchantPayload read(FriendlyByteBuf buf) {
        int[] rates = new int[3];
        boolean[] ok = new boolean[3];
        String[] texts = new String[3];
        for (int i = 0; i < 3; i++) {
            rates[i] = buf.readVarInt() - 1;
            ok[i] = buf.readBoolean();
            texts[i] = buf.readUtf(256);
        }
        return new EnchantPayload(rates, ok, texts);
    }

    public void write(FriendlyByteBuf buf) {
        for (int i = 0; i < 3; i++) {
            buf.writeVarInt(rates[i] + 1);
            buf.writeBoolean(ok[i]);
            buf.writeUtf(texts[i] == null ? "" : texts[i], 256);
        }
    }

    public String key() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) sb.append(rates[i]).append(ok[i]).append(texts[i]).append('|');
        return sb.toString();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
