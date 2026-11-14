package fr.citizen.realisme;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Poids (kg) des objets et charge totale d'un joueur. */
public final class Weights {
    private static final Map<Item, Double> CACHE = new ConcurrentHashMap<>();
    private static volatile List<Map.Entry<TagKey<Item>, Double>> tagRules;

    private Weights() {
    }

    public static void clearCache() {
        CACHE.clear();
        tagRules = null;
    }

    /** Poids d'un seul exemplaire de l'objet, contenu compris (boîtes de Shulker...). */
    public static double unit(ItemStack stack) {
        if (stack.isEmpty() || RealismeItems.isLock(stack)) return 0;
        String bag = RealismeItems.bagId(stack);
        if (bag != null) return RealismeItems.SAC_A_DOS.equals(bag) ? 1.5 : 0.8;
        String tool = RealismeItems.toolId(stack);
        if (tool != null) return RealismeItems.MARTEAU.equals(tool) ? 1.2 : 0.3;
        double w = CACHE.computeIfAbsent(stack.getItem(), Weights::compute);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inner : contents.nonEmptyItems()) {
                w += unit(inner) * inner.getCount();
            }
        }
        return w;
    }

    public static double of(ItemStack stack) {
        return unit(stack) * stack.getCount();
    }

    /** Charge totale portée : inventaire, armure, main secondaire et objet tenu au curseur. */
    public static double carried(Player player) {
        Inventory inv = player.getInventory();
        double total = 0;
        for (ItemStack s : inv.items) total += of(s);
        for (ItemStack s : inv.armor) total += of(s);
        for (ItemStack s : inv.offhand) total += of(s);
        total += of(player.containerMenu.getCarried());
        return total;
    }

    public static double capacity(Player player) {
        RealismeConfig.Poids cfg = RealismeConfig.get().poids;
        String bag = Bags.equipped(player);
        double cap = cfg.capaciteBase;
        if (RealismeItems.SACOCHE.equals(bag)) cap += cfg.bonusSacoche;
        if (RealismeItems.SAC_A_DOS.equals(bag)) cap += cfg.bonusSacADos;
        return cap;
    }

    private static List<Map.Entry<TagKey<Item>, Double>> tagRules() {
        List<Map.Entry<TagKey<Item>, Double>> rules = tagRules;
        if (rules == null) {
            rules = new ArrayList<>();
            for (Map.Entry<String, Double> e : RealismeConfig.get().poids.tags.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey().startsWith("#") ? e.getKey().substring(1) : e.getKey());
                if (id != null && e.getValue() != null) rules.add(Map.entry(TagKey.create(Registries.ITEM, id), e.getValue()));
            }
            tagRules = rules;
        }
        return rules;
    }

    private static double compute(Item item) {
        RealismeConfig.Poids cfg = RealismeConfig.get().poids;
        String key = BuiltInRegistries.ITEM.getKey(item).toString();
        Double fixed = cfg.objets.get(key);
        if (fixed != null) return fixed;

        if (item instanceof ArmorItem armor) {
            String material = armor.getMaterial().unwrapKey().map(k -> k.location().getPath()).orElse("iron");
            double chest = switch (material) {
                case "leather" -> 0.8;
                case "chainmail" -> 2.5;
                case "gold" -> 4.0;
                case "diamond" -> 3.0;
                case "netherite" -> 4.5;
                case "turtle", "armadillo" -> 1.5;
                default -> 3.5;
            };
            double factor = switch (armor.getType()) {
                case HELMET -> 0.5;
                case CHESTPLATE -> 1.0;
                case LEGGINGS -> 0.8;
                case BOOTS -> 0.4;
                default -> 1.0;
            };
            return round(chest * factor);
        }
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            if (tier == Tiers.WOOD) return 0.6;
            if (tier == Tiers.STONE) return 1.2;
            if (tier == Tiers.GOLD) return 1.6;
            if (tier == Tiers.DIAMOND) return 1.4;
            if (tier == Tiers.NETHERITE) return 2.0;
            return 1.5;
        }
        if (item instanceof MaceItem) return 5.0;
        if (item instanceof TridentItem) return 2.0;
        if (item instanceof ShieldItem) return 3.0;
        if (item instanceof CrossbowItem) return 1.5;
        if (item instanceof BowItem) return 0.8;
        if (item instanceof ElytraItem) return 1.5;
        if (item instanceof BucketItem) return 1.0;
        if (item instanceof MinecartItem) return 8.0;
        if (item instanceof BoatItem) return 6.0;

        ItemStack probe = new ItemStack(item);
        for (Map.Entry<TagKey<Item>, Double> rule : tagRules()) {
            if (probe.is(rule.getKey())) return rule.getValue();
        }
        if (probe.has(DataComponents.FOOD)) return 0.15;
        if (item instanceof BlockItem) return 1.0;
        return 0.1;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String format(double kg) {
        if (kg < 10) return String.format(java.util.Locale.FRANCE, "%.1f", kg);
        return String.valueOf(Math.round(kg));
    }
}
