package fr.citizen.realisme;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Pont facultatif vers Citizen Bedrock (sans dépendance de compilation) :
 * détection des joueurs console et menus natifs Bedrock (formulaires).
 */
public final class Bedrock {
    private static Boolean present;
    private static Method isBedrock;
    private static Method simple;

    private Bedrock() {
    }

    private static boolean init() {
        if (present != null) return present;
        present = false;
        if (!ModList.get().isLoaded("citizenbedrock")) return false;
        try {
            Class<?> players = Class.forName("fr.citizen.bedrock.BedrockPlayers");
            isBedrock = players.getMethod("isBedrock", ServerPlayer.class);
            Class<?> forms = Class.forName("fr.citizen.bedrock.forms.BedrockForms");
            simple = forms.getMethod("simple", String.class, String.class);
            present = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            CitizenRealisme.LOGGER.warn("[Citizen Réalisme] Citizen Bedrock présent mais API introuvable : menus console désactivés", e);
        }
        return present;
    }

    public static boolean isBedrock(ServerPlayer player) {
        if (!init()) return false;
        try {
            return (boolean) isBedrock.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Envoie un menu à boutons Bedrock. Renvoie false si impossible (le joueur reçoit alors le texte). */
    public static boolean menu(ServerPlayer player, String title, String content, List<String> buttons, IntConsumer onClick) {
        if (!init() || !isBedrock(player)) return false;
        try {
            Object form = simple.invoke(null, title, content);
            Method button = form.getClass().getMethod("button", String.class);
            for (String b : buttons) form = button.invoke(form, b);
            form = form.getClass().getMethod("onClick", IntConsumer.class).invoke(form, onClick);
            return (boolean) form.getClass().getMethod("send", ServerPlayer.class).invoke(form, player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CitizenRealisme.LOGGER.warn("[Citizen Réalisme] Menu console impossible", e);
            return false;
        }
    }
}
