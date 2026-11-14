package fr.citizen.realisme;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** /realisme : état du joueur, retrait du sac, aide craft / rituel ; /realisme admin pour les ops. */
public final class RealismeCommand {
    private static final List<String> GIVEABLE = List.of(RealismeItems.MARTEAU, RealismeItems.SCIE, RealismeItems.AIGUILLE,
            RealismeItems.SACOCHE, RealismeItems.SAC_A_DOS);

    private RealismeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("realisme")
                .executes(c -> status(c.getSource().getPlayerOrException()))
                .then(Commands.literal("sac")
                        .then(Commands.literal("retirer").executes(c -> Bags.unequip(c.getSource().getPlayerOrException()) ? 1 : 0)))
                .then(Commands.literal("craft").executes(c -> craftHelp(c.getSource().getPlayerOrException())))
                .then(Commands.literal("rituel").executes(c -> ritualHelp(c.getSource().getPlayerOrException())))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("recharger").executes(c -> {
                            String error = RealismeConfig.load();
                            c.getSource().sendSuccess(() -> Component.literal(error == null
                                    ? "Réglages de Citizen Réalisme rechargés."
                                    : "Erreur dans realisme.json : " + error).withStyle(error == null ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                            return error == null ? 1 : 0;
                        }))
                        .then(Commands.literal("donner")
                                .then(Commands.argument("objet", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(GIVEABLE, b))
                                        .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "objet"), c.getSource().getPlayerOrException()))
                                        .then(Commands.argument("joueur", EntityArgument.player())
                                                .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "objet"), EntityArgument.getPlayer(c, "joueur"))))))));
    }

    private static int give(CommandSourceStack source, String id, ServerPlayer target) {
        ItemStack stack = RealismeItems.create(id);
        if (stack == null) {
            source.sendFailure(Component.literal("Objet inconnu. Choix : " + String.join(", ", GIVEABLE)));
            return 0;
        }
        String name = stack.getHoverName().getString();
        Bags.give(target, stack);
        source.sendSuccess(() -> Component.literal(name + " donné à " + target.getGameProfile().getName()), true);
        return 1;
    }

    public static int status(ServerPlayer player) throws CommandSyntaxException {
        Encumbrance.State state = Encumbrance.compute(player);
        String bag = Bags.equipped(player);
        int rows = Bags.openRows(player);
        int slots = 9 + rows * 9;
        String bagText = bag == null ? "aucun" : RealismeItems.bagName(bag);

        if (Bedrock.isBedrock(player)) {
            String content = "§7Poids porté : §f" + Weights.format(state.weight()) + " / " + Weights.format(state.capacity()) + " kg"
                    + "\n§7Sac : §f" + bagText + "\n§7Cases utilisables : §f" + slots + " / 36"
                    + "\n\n§7Plus tu portes, plus tu ralentis. Au-delà de la limite : plus de sprint, puis plus de saut.";
            List<String> buttons = bag == null
                    ? List.of("Comment fabriquer ?", "Rituel d'enchantement", "Fermer")
                    : List.of("Retirer mon sac", "Comment fabriquer ?", "Rituel d'enchantement", "Fermer");
            boolean sent = Bedrock.menu(player, "Mon équipement", content, buttons, index -> {
                String choice = index >= 0 && index < buttons.size() ? buttons.get(index) : "";
                switch (choice) {
                    case "Retirer mon sac" -> Bags.unequip(player);
                    case "Comment fabriquer ?" -> craftHelp(player);
                    case "Rituel d'enchantement" -> ritualHelp(player);
                    default -> {
                    }
                }
            });
            if (sent) return 1;
        }

        player.sendSystemMessage(Component.literal("— Mon équipement —").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Encumbrance.bar(state));
        MutableComponent bagLine = Component.literal("Sac : " + bagText + " · cases : " + slots + " / 36").withStyle(ChatFormatting.GRAY);
        if (bag != null) {
            bagLine.append(Component.literal("  [Retirer]").withStyle(s -> s.withColor(ChatFormatting.RED)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/realisme sac retirer"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Retirer le sac (il doit être vide)")))));
        }
        player.sendSystemMessage(bagLine);
        player.sendSystemMessage(Component.literal("[Aide craft]").withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/realisme craft")))
                .append(Component.literal("  "))
                .append(Component.literal("[Rituel d'enchantement]").withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/realisme rituel")))));
        return 1;
    }

    private static int craftHelp(ServerPlayer player) {
        RealismeConfig.Craft cfg = RealismeConfig.get().craft;
        player.sendSystemMessage(Component.literal(
                "§6— Fabrication —\n"
                        + "§7• Les crafts simples (" + cfg.casesInstantanees + " cases ou moins, sans outil) sont instantanés.\n"
                        + "§7• Les autres prennent du temps : clique sur le résultat puis ne bouge pas.\n"
                        + "§7• Chaque craft a un §ftaux de réussite§7 affiché : un échec fait perdre une partie des ingrédients.\n"
                        + "§7• Outils d'artisan à garder dans l'inventaire (ils s'usent) :\n"
                        + "§f  Marteau§7 : armes, armures, objets en métal\n"
                        + "§f  Scie§7 : portes, coffres, barrières, bateaux, panneaux (demande un marteau)\n"
                        + "§f  Aiguille§7 : cuir, lits, bannières, tapis, livres, sacs\n"
                        + "§7• Un outil en bon état donne §a+" + cfg.bonusOutilEnBonEtat + " %§7 de réussite."));
        return 1;
    }

    private static int ritualHelp(ServerPlayer player) {
        StringBuilder sb = new StringBuilder("§5— Rituel d'enchantement —\n");
        sb.append("§7En plus du lapis et des niveaux, chaque option demande des catalyseurs.\n");
        sb.append("§7Les bibliothèques autour de la table augmentent le taux de réussite.\n");
        for (int i = 0; i < 3; i++) {
            sb.append("§fOption ").append(i + 1).append(" §7(").append(EnchantRitual.rate(i, 0)).append(" à ")
                    .append(EnchantRitual.rate(i, 15)).append(" %) : ").append(EnchantRitual.catalystText(i)).append("\n");
        }
        sb.append("§cÉchec : niveaux, lapis et catalyseurs perdus, l'objet peut être maudit.");
        player.sendSystemMessage(Component.literal(sb.toString()));
        return 1;
    }
}
