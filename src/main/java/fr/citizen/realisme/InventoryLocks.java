package fr.citizen.realisme;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Cases verrouillées : sans sac, seules la barre rapide et la première rangée sont utilisables.
 * Une case verrouillée qui contient encore un objet (joueurs déjà équipés avant l'installation)
 * reste vidable : rien n'est jamais supprimé, on ne peut simplement plus y ranger d'objet.
 */
public final class InventoryLocks {
    private InventoryLocks() {
    }

    public static boolean active(Player player) {
        RealismeConfig.Inventaire cfg = RealismeConfig.get().inventaire;
        if (!cfg.actif || !(player instanceof ServerPlayer sp)) return false;
        GameType mode = sp.gameMode.getGameModeForPlayer();
        if (mode == GameType.SPECTATOR) return false;
        return !(cfg.ignorerCreatif && mode == GameType.CREATIVE);
    }

    /** index : numéro de case de l'inventaire du joueur (0-8 barre rapide, 9-35 inventaire principal). */
    public static boolean isLocked(Player player, int index) {
        if (index < 9 || index > 35) return false;
        if (!active(player)) return false;
        int row = (index - 9) / 9;
        return row >= Bags.openRows(player);
    }

    /** Appelé par le mixin de Slot : interdit de poser un objet dans une case verrouillée. */
    public static boolean denyPlace(Slot slot, ItemStack stack) {
        Container c = slot.container;
        if (!(c instanceof Inventory inv)) return false;
        if (RealismeItems.isLock(stack)) return true;
        return isLocked(inv.player, slot.getContainerSlot());
    }

    /** Appelé par le mixin de Slot : le cadenas ne peut jamais être pris. */
    public static boolean denyPickup(Slot slot) {
        if (!(slot.container instanceof Inventory inv) || !(inv.player instanceof ServerPlayer)) return false;
        return RealismeItems.isLock(slot.getItem());
    }

    /** Remet les cadenas à jour (cases vides verrouillées) et retire ceux qui traînent ailleurs. */
    public static void apply(ServerPlayer player) {
        Inventory inv = player.getInventory();
        boolean active = active(player);
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            boolean locked = active && isLocked(player, i);
            if (RealismeItems.isLock(s)) {
                if (!locked) inv.items.set(i, ItemStack.EMPTY);
            } else if (locked && s.isEmpty()) {
                inv.items.set(i, RealismeItems.lock());
            }
        }
        for (int i = 0; i < inv.armor.size(); i++) {
            if (RealismeItems.isLock(inv.armor.get(i))) inv.armor.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            if (RealismeItems.isLock(inv.offhand.get(i))) inv.offhand.set(i, ItemStack.EMPTY);
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (RealismeItems.isLock(menu.getCarried())) menu.setCarried(ItemStack.EMPTY);
    }

    /** À la fermeture d'un coffre : un cadenas glissé dedans par un client trop rapide est supprimé. */
    public static void purgeContainer(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            if (RealismeItems.isLock(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
    }

    public static void clear(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (RealismeItems.isLock(inv.items.get(i))) inv.items.set(i, ItemStack.EMPTY);
        }
    }
}
