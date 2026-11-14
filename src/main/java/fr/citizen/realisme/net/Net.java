package fr.citizen.realisme.net;

import fr.citizen.realisme.Bedrock;
import fr.citizen.realisme.client.ClientState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Messages vers le client PC. Ils sont « optionnels » : un client sans le mod (console via Geyser,
 * Java vanilla) peut se connecter, et reçoit les mêmes informations en barre d'action / formulaires.
 */
public final class Net {
    private Net() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(HudPayload.TYPE, HudPayload.CODEC, (payload, context) -> ClientState.onHud(payload));
        registrar.playToClient(CraftPayload.TYPE, CraftPayload.CODEC, (payload, context) -> ClientState.onCraft(payload));
        registrar.playToClient(EnchantPayload.TYPE, EnchantPayload.CODEC, (payload, context) -> ClientState.onEnchant(payload));
    }

    /** Vrai si le joueur a le mod sur son PC (sinon : console ou client vanilla). */
    public static boolean modded(ServerPlayer player) {
        if (player instanceof FakePlayer || Bedrock.isBedrock(player)) return false;
        return player.connection != null && player.connection.hasChannel(HudPayload.TYPE);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
