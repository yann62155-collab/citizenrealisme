package fr.citizen.realisme;

import fr.citizen.realisme.mixin.EnchantmentMenuAccessor;
import fr.citizen.realisme.net.EnchantPayload;
import fr.citizen.realisme.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.EnchantingTableBlock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rituel d'enchantement : chaque option de la table affiche un taux de réussite (bibliothèques comprises)
 * et demande des catalyseurs en plus du lapis et des niveaux. La chance de rater = 100 % - taux affiché.
 * Échec : niveaux, lapis et catalyseurs consommés, objet non enchanté, parfois maudit.
 */
public final class EnchantRitual {
    public enum Outcome { VANILLA, MISSING, SUCCESS, FAILURE }

    private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();

    private EnchantRitual() {
    }

    public static boolean active() {
        return RealismeConfig.get().enchantement.actif;
    }

    public static boolean isShowing(ServerPlayer player) {
        return LAST.containsKey(player.getUUID());
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }

    public static int shelves(ContainerLevelAccess access) {
        return access.evaluate((level, pos) -> {
            int n = 0;
            for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
                if (EnchantingTableBlock.isValidBookShelf(level, pos, offset)) n++;
            }
            return n;
        }, 0);
    }

    public static int rate(int option, int shelves) {
        RealismeConfig.Enchantement cfg = RealismeConfig.get().enchantement;
        int base = cfg.reussiteBase[Math.max(0, Math.min(2, option))];
        int r = (int) Math.round(base + Math.min(15, shelves) * cfg.bonusParBibliotheque);
        return Math.max(cfg.reussiteMin, Math.min(cfg.reussiteMax, r));
    }

    /** Catalyseurs demandés pour l'option (objet -> quantité). */
    public static Map<Item, Integer> catalysts(int option) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : RealismeConfig.get().enchantement.catalysts(option).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || e.getValue() == null || e.getValue() <= 0) continue;
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            item.ifPresent(i -> out.merge(i, e.getValue(), Integer::sum));
        }
        return out;
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : inv.offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    public static boolean hasCatalysts(ServerPlayer player, int option) {
        if (player.getAbilities().instabuild) return true;
        for (Map.Entry<Item, Integer> e : catalysts(option).entrySet()) {
            if (count(player, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public static void consumeCatalysts(ServerPlayer player, int option) {
        if (player.getAbilities().instabuild) return;
        Inventory inv = player.getInventory();
        for (Map.Entry<Item, Integer> e : catalysts(option).entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(e.getKey())) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        inv.setChanged();
    }

    public static String catalystText(int option) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Item, Integer> e : catalysts(option).entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(e.getValue()).append(" ").append(new ItemStack(e.getKey()).getHoverName().getString());
        }
        return sb.isEmpty() ? "aucun catalyseur" : sb.toString();
    }

    /** Tirage du rituel, appelé par le mixin avant l'enchantement vanilla. */
    public static Outcome attempt(ServerPlayer player, int option, ContainerLevelAccess access) {
        if (!active()) return Outcome.VANILLA;
        if (!hasCatalysts(player, option)) {
            player.displayClientMessage(Component.literal("Rituel impossible : il manque " + catalystText(option) + ".").withStyle(ChatFormatting.RED), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
            return Outcome.MISSING;
        }
        int rate = rate(option, shelves(access));
        boolean ok = player.getRandom().nextInt(100) < rate;
        return ok ? Outcome.SUCCESS : Outcome.FAILURE;
    }

    /** Après un enchantement vanilla réussi. */
    public static void onSuccess(ServerPlayer player, int option, ContainerLevelAccess access) {
        consumeCatalysts(player, option);
        access.execute((level, pos) -> {
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.5);
            }
        });
        player.displayClientMessage(Component.literal("Le rituel a réussi !").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        LAST.remove(player.getUUID());
    }

    /** Rituel raté : renvoie le message affiché. L'objet peut être maudit. */
    public static void onFailure(ServerPlayer player, int option, ItemStack item, ContainerLevelAccess access) {
        consumeCatalysts(player, option);
        RealismeConfig.Enchantement cfg = RealismeConfig.get().enchantement;
        boolean cursed = false;
        if (!item.is(Items.BOOK) && player.getRandom().nextInt(100) < cfg.chanceMalediction) {
            ResourceKey<Enchantment> curse = item.getItem() instanceof ArmorItem ? Enchantments.BINDING_CURSE : Enchantments.VANISHING_CURSE;
            Optional<Holder.Reference<Enchantment>> holder = player.level().registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT).getHolder(curse);
            if (holder.isPresent()) {
                item.enchant(holder.get(), 1);
                cursed = true;
            }
        }
        boolean finalCursed = cursed;
        access.execute((level, pos) -> {
            level.playSound(null, pos, finalCursed ? SoundEvents.WITHER_AMBIENT : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(finalCursed ? ParticleTypes.SOUL : ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
            }
        });
        int rate = rate(option, shelves(access));
        player.displayClientMessage(Component.literal(finalCursed
                ? "Le rituel a échoué et l'objet est maudit ! (" + rate + " % de réussite)"
                : "Le rituel a échoué (" + rate + " % de réussite) : niveaux, lapis et catalyseurs perdus.").withStyle(ChatFormatting.RED), true);
        LAST.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ affichage avant de cliquer

    public static void tick(ServerPlayer player) {
        if (!active()) return;
        if (!(player.containerMenu instanceof EnchantmentMenu menu)) {
            if (LAST.remove(player.getUUID()) != null && Net.modded(player)) {
                Net.send(player, new EnchantPayload(new int[]{-1, -1, -1}, new boolean[3], new String[]{"", "", ""}));
            }
            return;
        }
        if (player.tickCount % 10 != 0) return;
        ContainerLevelAccess access = ((EnchantmentMenuAccessor) menu).citizenrealisme$access();
        int shelves = shelves(access);
        int[] rates = new int[3];
        boolean[] ok = new boolean[3];
        String[] texts = new String[3];
        boolean any = false;
        for (int i = 0; i < 3; i++) {
            if (menu.costs[i] > 0) {
                rates[i] = rate(i, shelves);
                ok[i] = hasCatalysts(player, i);
                texts[i] = catalystText(i);
                any = true;
            } else {
                rates[i] = -1;
                texts[i] = "";
            }
        }
        EnchantPayload payload = new EnchantPayload(rates, ok, texts);
        String key = payload.key();
        String last = LAST.put(player.getUUID(), key);
        if (Net.modded(player)) {
            if (!key.equals(last)) Net.send(player, payload);
            return;
        }
        if (!any) return;
        if (!key.equals(last)) {
            // Joueurs console : détail dans le tchat à chaque nouvel objet, résumé en barre d'action.
            StringBuilder sb = new StringBuilder("§5Rituel d'enchantement §7(" + shelves + " bibliothèque" + (shelves > 1 ? "s" : "") + ")");
            for (int i = 0; i < 3; i++) {
                if (rates[i] < 0) continue;
                sb.append("\n").append(ok[i] ? "§a" : "§c").append("Option ").append(i + 1).append(" : ")
                        .append(rates[i]).append(" % de réussite §7— ").append(texts[i]);
            }
            player.sendSystemMessage(Component.literal(sb.toString()));
        }
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (rates[i] < 0) continue;
            if (!bar.isEmpty()) bar.append("  ");
            bar.append(ok[i] ? "§a" : "§c").append(roman(i)).append(" ").append(rates[i]).append("%");
        }
        player.displayClientMessage(Component.literal("§5Réussite : " + bar), true);
    }

    public static String roman(int option) {
        return switch (option) {
            case 0 -> "I";
            case 1 -> "II";
            default -> "III";
        };
    }
}
