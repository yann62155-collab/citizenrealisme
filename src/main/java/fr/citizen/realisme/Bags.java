package fr.citizen.realisme;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Sac équipé (sacoche ou sac à dos) et cases de l'inventaire qu'il ouvre.
 * Le sac est rangé dans les données persistantes du joueur (copiées à la mort par NeoForge).
 */
public final class Bags {
    private static final String ROOT = "citizenrealisme";
    private static final String BAG = "sac";

    private Bags() {
    }

    private static CompoundTag data(Player player) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag kept = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag mine = kept.getCompound(ROOT);
        kept.put(ROOT, mine);
        persistent.put(Player.PERSISTED_NBT_TAG, kept);
        return mine;
    }

    @Nullable
    public static String equipped(Player player) {
        String bag = data(player).getString(BAG);
        return bag.isEmpty() ? null : bag;
    }

    public static void setEquipped(Player player, @Nullable String bag) {
        if (bag == null) data(player).remove(BAG);
        else data(player).putString(BAG, bag);
    }

    public static int rowsFor(@Nullable String bag) {
        RealismeConfig.Inventaire cfg = RealismeConfig.get().inventaire;
        if (RealismeItems.SAC_A_DOS.equals(bag)) return cfg.rangeesSacADos;
        if (RealismeItems.SACOCHE.equals(bag)) return cfg.rangeesSacoche;
        return 0;
    }

    /** Nombre de rangées de l'inventaire principal ouvertes (0 à 3). */
    public static int openRows(Player player) {
        int rows = RealismeConfig.get().inventaire.rangeesDeBase + rowsFor(equipped(player));
        return Math.max(0, Math.min(3, rows));
    }

    /** Vrai si une case d'objets de la rangée [fromRow, toRow[ contient un vrai objet. */
    private static boolean rowsHaveItems(Player player, int fromRow, int toRow) {
        Inventory inv = player.getInventory();
        for (int row = fromRow; row < toRow; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack s = inv.items.get(9 + row * 9 + col);
                if (!s.isEmpty() && !RealismeItems.isLock(s)) return true;
            }
        }
        return false;
    }

    /** Clic droit avec un sac : on l'équipe (l'ancien sac revient dans l'inventaire). */
    public static void equip(ServerPlayer player, ItemStack stack) {
        String bag = RealismeItems.bagId(stack);
        if (bag == null) return;
        String old = equipped(player);
        if (bag.equals(old)) {
            player.displayClientMessage(Component.literal("Tu portes déjà une " + RealismeItems.bagName(bag).toLowerCase() + ".").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int base = RealismeConfig.get().inventaire.rangeesDeBase;
        int oldRows = Math.min(3, base + rowsFor(old));
        int newRows = Math.min(3, base + rowsFor(bag));
        if (newRows < oldRows && rowsHaveItems(player, newRows, oldRows)) {
            player.sendSystemMessage(Component.literal("Vide d'abord la dernière rangée de ton " + RealismeItems.bagName(old).toLowerCase() + " avant de changer de sac.").withStyle(ChatFormatting.RED));
            return;
        }
        stack.shrink(1);
        setEquipped(player, bag);
        if (old != null) give(player, RealismeItems.create(old));
        InventoryLocks.apply(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("Tu as équipé : " + RealismeItems.bagName(bag) + " (+" + (rowsFor(bag) * 9) + " cases). /realisme sac retirer pour l'enlever.").withStyle(ChatFormatting.GOLD));
        Encumbrance.forceRefresh(player);
    }

    /** Retire le sac : seulement si les cases qu'il ouvrait sont vides. */
    public static boolean unequip(ServerPlayer player) {
        String bag = equipped(player);
        if (bag == null) {
            player.sendSystemMessage(Component.literal("Tu ne portes aucun sac.").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        int base = RealismeConfig.get().inventaire.rangeesDeBase;
        int rows = Math.min(3, base + rowsFor(bag));
        if (rowsHaveItems(player, Math.min(3, base), rows)) {
            player.sendSystemMessage(Component.literal("Vide d'abord ton " + RealismeItems.bagName(bag).toLowerCase() + " (les rangées qu'il ouvre) avant de le retirer.").withStyle(ChatFormatting.RED));
            return false;
        }
        setEquipped(player, null);
        InventoryLocks.apply(player);
        give(player, RealismeItems.create(bag));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.literal("Tu as retiré ton " + RealismeItems.bagName(bag).toLowerCase() + ".").withStyle(ChatFormatting.GOLD));
        Encumbrance.forceRefresh(player);
        return true;
    }

    public static void give(ServerPlayer player, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
