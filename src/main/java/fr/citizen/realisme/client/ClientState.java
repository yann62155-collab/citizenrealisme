package fr.citizen.realisme.client;

import fr.citizen.realisme.net.CraftPayload;
import fr.citizen.realisme.net.EnchantPayload;
import fr.citizen.realisme.net.HudPayload;

/** Dernières infos reçues du serveur (joueurs PC). Aucune classe client ici : sûr à charger côté serveur. */
public final class ClientState {
    public static volatile boolean hasHud;
    public static volatile float weight;
    public static volatile float capacity;
    public static volatile int level;
    public static volatile CraftPayload craft = CraftPayload.HIDDEN;
    public static volatile EnchantPayload enchant;

    private ClientState() {
    }

    public static void onHud(HudPayload p) {
        hasHud = true;
        weight = p.weight();
        capacity = p.capacity();
        level = p.level();
    }

    public static void onCraft(CraftPayload p) {
        craft = p;
    }

    public static void onEnchant(EnchantPayload p) {
        enchant = p;
    }

    public static void reset() {
        hasHud = false;
        craft = CraftPayload.HIDDEN;
        enchant = null;
    }
}
