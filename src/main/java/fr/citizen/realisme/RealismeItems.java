package fr.citizen.realisme;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Objets du mod. Ce sont des objets vanilla avec des composants (nom, marque, modèle) : aucun objet
 * n'est ajouté au registre, donc les joueurs console (Geyser) les voient sans conversion.
 * Les recettes (data/citizenrealisme/recipe) produisent exactement les mêmes objets.
 */
public final class RealismeItems {
    public static final String TOOL_KEY = "citizenrealisme_outil";
    public static final String BAG_KEY = "citizenrealisme_sac";
    public static final String LOCK_KEY = "citizenrealisme_verrou";

    public static final String MARTEAU = "marteau";
    public static final String SCIE = "scie";
    public static final String AIGUILLE = "aiguille";
    public static final String SACOCHE = "sacoche";
    public static final String SAC_A_DOS = "sacados";

    private RealismeItems() {
    }

    public static String toolName(String id) {
        return switch (id) {
            case MARTEAU -> "Marteau de forgeron";
            case SCIE -> "Scie de menuisier";
            case AIGUILLE -> "Aiguille de couturier";
            default -> id;
        };
    }

    public static String bagName(String id) {
        return SAC_A_DOS.equals(id) ? "Sac à dos" : "Sacoche";
    }

    @Nullable
    public static ItemStack create(String id) {
        return switch (id) {
            case MARTEAU -> tool(MARTEAU, Items.IRON_SHOVEL, 7101, 250, "Forge, armures, armes et objets en métal");
            case SCIE -> tool(SCIE, Items.SHEARS, 7102, 200, "Portes, coffres, barrières, bateaux, panneaux");
            case AIGUILLE -> tool(AIGUILLE, Items.FLINT_AND_STEEL, 7103, 150, "Cuir, lits, bannières, tapis, sacs");
            case SACOCHE -> bag(SACOCHE, Items.LEATHER_HORSE_ARMOR, 7104);
            case SAC_A_DOS -> bag(SAC_A_DOS, Items.SADDLE, 7105);
            default -> null;
        };
    }

    private static ItemStack tool(String id, Item base, int model, int durability, String usage) {
        ItemStack stack = new ItemStack(base);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(toolName(id))
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Outil d'artisan").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal(usage).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_GRAY)),
                Component.literal("À garder dans l'inventaire pour fabriquer").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.DARK_GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TOOL_KEY, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        stack.set(DataComponents.MAX_DAMAGE, durability);
        stack.set(DataComponents.TOOL, new Tool(List.of(), 1.0F, 0));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY.withTooltip(false));
        return stack;
    }

    private static ItemStack bag(String id, Item base, int model) {
        ItemStack stack = new ItemStack(base);
        boolean big = SAC_A_DOS.equals(id);
        RealismeConfig cfg = RealismeConfig.get();
        int rows = big ? cfg.inventaire.rangeesSacADos : cfg.inventaire.rangeesSacoche;
        double kg = big ? cfg.poids.bonusSacADos : cfg.poids.bonusSacoche;
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(bagName(id))
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("+" + (rows * 9) + " cases, +" + Math.round(kg) + " kg portés").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal("Clic droit pour l'équiper").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.DARK_GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(BAG_KEY, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        if (base == Items.LEATHER_HORSE_ARMOR) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFFFFFF, false));
        }
        return stack;
    }

    /** Objet posé dans une case verrouillée (barrière : icône « interdit », lisible sur console). */
    public static ItemStack lock() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Case verrouillée")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_RED)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Équipe une sacoche ou un sac à dos").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal("pour porter plus d'affaires.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(LOCK_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(7110));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }

    @Nullable
    private static String string(ItemStack stack, String key) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(key)) return null;
        @SuppressWarnings("deprecation")
        CompoundTag tag = data.getUnsafe();
        return tag.getString(key);
    }

    @Nullable
    public static String toolId(ItemStack stack) {
        return string(stack, TOOL_KEY);
    }

    @Nullable
    public static String bagId(ItemStack stack) {
        return string(stack, BAG_KEY);
    }

    public static boolean isLock(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.BARRIER)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(LOCK_KEY);
    }

    public static boolean isOurs(ItemStack stack) {
        return toolId(stack) != null || bagId(stack) != null || isLock(stack);
    }
}
