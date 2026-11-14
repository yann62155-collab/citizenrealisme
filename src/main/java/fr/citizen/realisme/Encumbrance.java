package fr.citizen.realisme;

import fr.citizen.realisme.net.HudPayload;
import fr.citizen.realisme.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encombrement : au-delà de la capacité on ralentit, puis on ne peut plus sprinter ni sauter.
 * Les effets passent par les attributs de vitesse et de saut : le serveur décide, donc c'est
 * identique pour les joueurs PC et console.
 */
public final class Encumbrance {
    public static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "surcharge");
    public static final ResourceLocation NO_SPRINT = ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "sans_sprint");
    public static final ResourceLocation NO_JUMP = ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "sans_saut");

    /** Le sprint vanilla ajoute +30 % : ce modificateur le compense exactement. */
    private static final double CANCEL_SPRINT = 1.0 / 1.3 - 1.0;

    public record State(double weight, double capacity, int level) {
        public double ratio() {
            return capacity <= 0 ? 0 : weight / capacity;
        }
    }

    private static final Map<UUID, State> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_BAR = new ConcurrentHashMap<>();

    private Encumbrance() {
    }

    public static State compute(ServerPlayer player) {
        RealismeConfig.Poids cfg = RealismeConfig.get().poids;
        double w = Weights.carried(player);
        double cap = Weights.capacity(player);
        double r = cap <= 0 ? 0 : w / cap;
        int level = r <= 1.0 ? 0 : r < cfg.seuilSansSprint ? 1 : r < cfg.seuilSansSaut ? 2 : 3;
        return new State(w, cap, level);
    }

    public static void forceRefresh(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }

    public static void forget(UUID id) {
        LAST.remove(id);
        LAST_BAR.remove(id);
    }

    /** Toutes les 10 ticks. */
    public static void tick(ServerPlayer player) {
        RealismeConfig.Poids cfg = RealismeConfig.get().poids;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (!cfg.actif || mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
            clearModifiers(speed, jump);
            LAST.remove(player.getUUID());
            return;
        }
        State state = compute(player);
        double r = state.ratio();
        if (speed != null) {
            if (r > 1.0) {
                double slow = Math.min(cfg.ralentissementMax, (r - 1.0) * cfg.ralentissementParSurcharge);
                speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW, -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else {
                speed.removeModifier(SLOW);
            }
        }
        if (jump != null) {
            if (state.level() >= 3) {
                jump.addOrUpdateTransientModifier(new AttributeModifier(NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else {
                jump.removeModifier(NO_JUMP);
            }
        }

        State last = LAST.put(player.getUUID(), state);
        boolean changed = last == null || last.level() != state.level()
                || Math.abs(last.weight() - state.weight()) >= 0.05 || last.capacity() != state.capacity();
        if (Net.modded(player)) {
            if (changed) Net.send(player, new HudPayload((float) state.weight(), (float) state.capacity(), (byte) state.level()));
        } else {
            // Joueurs console / vanilla : barre d'action quand le poids change, ou régulièrement si surchargé.
            int now = player.tickCount;
            int lastBar = LAST_BAR.getOrDefault(player.getUUID(), -1000);
            boolean busy = CraftTimers.isBusy(player) || EnchantRitual.isShowing(player);
            if (!busy && (changed && (last == null || Math.abs(last.weight() - state.weight()) >= 0.5 || last.level() != state.level())
                    || state.level() > 0 && now - lastBar >= 60)) {
                LAST_BAR.put(player.getUUID(), now);
                player.displayClientMessage(bar(state), true);
            }
        }
    }

    /** Chaque tick : compense le sprint quand on est trop chargé (le client garde la main sur le sprint). */
    public static void tickSprint(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        State state = LAST.get(player.getUUID());
        boolean block = state != null && state.level() >= 2 && player.isSprinting();
        if (block) {
            if (!speed.hasModifier(NO_SPRINT)) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(NO_SPRINT, CANCEL_SPRINT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (speed.hasModifier(NO_SPRINT)) {
            speed.removeModifier(NO_SPRINT);
        }
    }

    private static void clearModifiers(AttributeInstance speed, AttributeInstance jump) {
        if (speed != null) {
            speed.removeModifier(SLOW);
            speed.removeModifier(NO_SPRINT);
        }
        if (jump != null) jump.removeModifier(NO_JUMP);
    }

    public static Component bar(State state) {
        ChatFormatting color = switch (state.level()) {
            case 0 -> state.ratio() > 0.85 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            case 1 -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
        String suffix = switch (state.level()) {
            case 1 -> " — chargé : tu ralentis";
            case 2 -> " — trop chargé : pas de sprint";
            case 3 -> " — surchargé : ni sprint ni saut";
            default -> "";
        };
        return Component.literal("Poids : " + Weights.format(state.weight()) + " / " + Weights.format(state.capacity()) + " kg" + suffix).withStyle(color);
    }

    public static State last(ServerPlayer player) {
        State s = LAST.get(player.getUUID());
        return s != null ? s : compute(player);
    }
}
