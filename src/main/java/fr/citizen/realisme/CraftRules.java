package fr.citizen.realisme;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Règles d'un craft : outil requis, durée et taux de réussite. */
public final class CraftRules {
    private static final List<TagKey<Item>> SCIE_TAGS = List.of(
            ItemTags.WOODEN_DOORS, ItemTags.WOODEN_TRAPDOORS, ItemTags.WOODEN_FENCES, ItemTags.FENCE_GATES,
            ItemTags.BOATS, ItemTags.CHEST_BOATS, ItemTags.SIGNS, ItemTags.HANGING_SIGNS,
            ItemTags.WOODEN_STAIRS, ItemTags.WOODEN_SLABS);
    private static final Set<Item> SCIE_ITEMS = Set.of(Items.CHEST, Items.BARREL, Items.BOOKSHELF, Items.CHISELED_BOOKSHELF,
            Items.LADDER, Items.LECTERN, Items.LOOM, Items.CARTOGRAPHY_TABLE, Items.FLETCHING_TABLE, Items.SMITHING_TABLE,
            Items.COMPOSTER, Items.ARMOR_STAND, Items.ITEM_FRAME, Items.PAINTING, Items.JUKEBOX, Items.NOTE_BLOCK);
    private static final Set<Item> AIGUILLE_ITEMS = Set.of(Items.BOOK, Items.WRITABLE_BOOK, Items.LEAD);

    /** Plan du craft actuellement dans la grille. */
    public record Plan(ItemStack result, int filled, int distinct, @Nullable String tool, @Nullable ItemStack toolStack,
                       int rate, int ticks, boolean instant, long hash) {
        public boolean missingTool() {
            return tool != null && toolStack == null;
        }

        public String toolLabel() {
            if (tool == null) return "";
            return RealismeItems.toolName(tool) + (toolStack == null ? " ✘" : " ✔");
        }

        public String summary() {
            StringBuilder sb = new StringBuilder("Réussite " + rate + " % · " + seconds(ticks));
            if (tool != null) sb.append(" · ").append(toolLabel());
            return sb.toString();
        }
    }

    private CraftRules() {
    }

    public static String seconds(int ticks) {
        double s = ticks / 20.0;
        return (s == Math.floor(s) ? String.valueOf((int) s) : String.format(java.util.Locale.FRANCE, "%.1f", s)) + " s";
    }

    /** Cases de la grille (hors case résultat) pour les menus gérés, sinon null. */
    @Nullable
    public static List<Slot> grid(AbstractContainerMenu menu) {
        int count;
        if (menu instanceof CraftingMenu) count = 9;
        else if (menu instanceof InventoryMenu) count = 4;
        else return null;
        if (menu.slots.size() <= count || !(menu.getSlot(0) instanceof ResultSlot)) return null;
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) slots.add(menu.getSlot(i));
        return slots;
    }

    @Nullable
    public static Plan plan(ServerPlayer player, AbstractContainerMenu menu) {
        List<Slot> grid = grid(menu);
        if (grid == null) return null;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.isEmpty()) return null;
        RealismeConfig.Craft cfg = RealismeConfig.get().craft;

        List<ItemStack> stacks = new ArrayList<>();
        Set<Item> kinds = new HashSet<>();
        long hash = 17;
        for (Slot s : grid) {
            ItemStack st = s.getItem();
            hash = hash * 31 + (st.isEmpty() ? 0 : ItemStack.hashItemAndComponents(st));
            if (st.isEmpty()) continue;
            stacks.add(st);
            kinds.add(st.getItem());
        }
        hash = hash * 31 + ItemStack.hashItemAndComponents(result);
        int filled = stacks.size();
        int distinct = kinds.size();

        String tool = requiredTool(result, stacks);
        ItemStack toolStack = tool == null ? null : findTool(player, tool);
        boolean instant = tool == null && filled <= cfg.casesInstantanees;
        if (instant) return new Plan(result, filled, distinct, null, null, 100, 0, true, hash);

        int rate = 100 - Math.max(0, filled - cfg.casesInstantanees) * cfg.penaliteParCase - Math.max(0, distinct - 1) * cfg.penaliteParIngredient;
        if (toolStack != null && toolStack.getMaxDamage() > 0 && toolStack.getDamageValue() < toolStack.getMaxDamage() * 0.75) {
            rate += cfg.bonusOutilEnBonEtat;
        }
        rate = Math.max(cfg.reussiteMin, Math.min(cfg.reussiteMax, rate));
        int ticks = Math.max(10, filled * cfg.ticksParCase + (tool != null ? cfg.ticksAvecOutil : 0));
        return new Plan(result, filled, distinct, tool, toolStack, rate, ticks, false, hash);
    }

    @Nullable
    public static String requiredTool(ItemStack result, List<ItemStack> ingredients) {
        RealismeConfig.Craft cfg = RealismeConfig.get().craft;
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        String own = RealismeItems.toolId(result);
        if (own != null) return RealismeItems.SCIE.equals(own) ? RealismeItems.MARTEAU : null;
        if (RealismeItems.bagId(result) != null) return RealismeItems.AIGUILLE;
        if (cfg.sansOutil.contains(id)) return null;
        String forced = cfg.outilParObjet.get(id);
        if (forced != null) return forced.isBlank() || forced.equals("aucun") ? null : forced;

        Item item = result.getItem();
        if (item instanceof ArmorItem armor) {
            String material = armor.getMaterial().unwrapKey().map(k -> k.location().getPath()).orElse("");
            if (material.equals("leather")) return RealismeItems.AIGUILLE;
            if (material.equals("turtle") || material.equals("armadillo")) return null;
            return RealismeItems.MARTEAU;
        }
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            return tier == Tiers.WOOD || tier == Tiers.STONE ? null : RealismeItems.MARTEAU;
        }
        if (result.is(ItemTags.BEDS) || result.is(ItemTags.BANNERS) || result.is(ItemTags.WOOL_CARPETS) || AIGUILLE_ITEMS.contains(item)) {
            return RealismeItems.AIGUILLE;
        }
        if (SCIE_ITEMS.contains(item)) return RealismeItems.SCIE;
        for (TagKey<Item> tag : SCIE_TAGS) {
            if (result.is(tag)) return RealismeItems.SCIE;
        }
        boolean metal = false;
        for (ItemStack s : ingredients) {
            if (s.is(Tags.Items.INGOTS)) {
                metal = true;
                break;
            }
        }
        if (metal && !result.is(Tags.Items.INGOTS) && !result.is(Tags.Items.NUGGETS) && !result.is(Tags.Items.STORAGE_BLOCKS)) {
            return RealismeItems.MARTEAU;
        }
        return null;
    }

    /** Premier outil de ce type dans l'inventaire du joueur (barre rapide comprise). */
    @Nullable
    public static ItemStack findTool(ServerPlayer player, String tool) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (tool.equals(RealismeItems.toolId(s))) return s;
        }
        for (ItemStack s : inv.offhand) {
            if (tool.equals(RealismeItems.toolId(s))) return s;
        }
        return null;
    }
}
