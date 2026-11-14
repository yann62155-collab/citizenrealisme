package fr.citizen.realisme;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Compatibilité facultative (sans dépendance de compilation) avec :
 * - Sophisticated Backpacks : le contenu d'un sac est rangé à part sur le serveur, on le lit pour le peser ;
 * - Curios et Accessories : un sac porté dans ces emplacements compte aussi dans le poids.
 */
public final class BackpackCompat {
    private static final String SB = "sophisticatedbackpacks";
    private static Boolean sbReady;
    private static Method fromExistingData;
    private static Method getInventoryHandler;
    private static DataComponentType<?> storageUuid;

    private static Boolean curiosReady;
    private static Method curiosInventory;
    private static Method curiosEquipped;

    private static Boolean accessoriesReady;
    private static Method accessoriesGet;
    private static Method accessoriesAll;
    private static Method entryStack;

    private BackpackCompat() {
    }

    // ------------------------------------------------------------------ Sophisticated Backpacks

    public static boolean isSophisticated(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SB.equals(id.getNamespace()) && id.getPath().contains("backpack");
    }

    private static boolean sbInit() {
        if (sbReady != null) return sbReady;
        sbReady = false;
        if (!ModList.get().isLoaded(SB)) return false;
        try {
            Class<?> wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            Class<?> iface = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper");
            fromExistingData = wrapper.getMethod("fromExistingData", ItemStack.class);
            getInventoryHandler = iface.getMethod("getInventoryHandler");
            storageUuid = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "storage_uuid"));
            sbReady = true;
            CitizenRealisme.LOGGER.info("[Citizen Réalisme] Sophisticated Backpacks détecté : le contenu des sacs compte dans le poids");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            CitizenRealisme.LOGGER.warn("[Citizen Réalisme] Sophisticated Backpacks présent mais API introuvable : contenu des sacs non pesé", e);
        }
        return sbReady;
    }

    /** Objets rangés dans un sac Sophisticated Backpacks (vide si inconnu, ex. côté client). */
    public static List<ItemStack> contents(ItemStack backpack) {
        if (!isSophisticated(backpack) || !sbInit()) return Collections.emptyList();
        try {
            Optional<?> wrapper = (Optional<?>) fromExistingData.invoke(null, backpack);
            if (wrapper.isEmpty()) return Collections.emptyList();
            Object handler = getInventoryHandler.invoke(wrapper.get());
            if (!(handler instanceof IItemHandler items)) return Collections.emptyList();
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack s = items.getStackInSlot(i);
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Collections.emptyList();
        }
    }

    /** Identifiant du stockage d'un sac (pour ne pas compter deux fois le même sac). */
    @Nullable
    public static UUID storageId(ItemStack stack) {
        if (!isSophisticated(stack) || !sbInit() || storageUuid == null) return null;
        Object value = stack.get(storageUuid);
        return value instanceof UUID uuid ? uuid : null;
    }

    // ------------------------------------------------------------------ Curios / Accessories

    private static boolean curiosInit() {
        if (curiosReady != null) return curiosReady;
        curiosReady = false;
        if (!ModList.get().isLoaded("curios")) return false;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            curiosInventory = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            curiosEquipped = handler.getMethod("getEquippedCurios");
            curiosReady = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            CitizenRealisme.LOGGER.warn("[Citizen Réalisme] Curios présent mais API introuvable", e);
        }
        return curiosReady;
    }

    private static boolean accessoriesInit() {
        if (accessoriesReady != null) return accessoriesReady;
        accessoriesReady = false;
        if (!ModList.get().isLoaded("accessories")) return false;
        try {
            Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> entry = Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference");
            accessoriesGet = cap.getMethod("getOptionally", net.minecraft.world.entity.LivingEntity.class);
            accessoriesAll = cap.getMethod("getAllEquipped");
            entryStack = entry.getMethod("stack");
            accessoriesReady = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            CitizenRealisme.LOGGER.warn("[Citizen Réalisme] Accessories présent mais API introuvable", e);
        }
        return accessoriesReady;
    }

    /** Objets portés dans les emplacements Curios / Accessories (sans doublon). */
    public static List<ItemStack> worn(Player player) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> out = new ArrayList<>();
        if (curiosInit()) {
            try {
                Optional<?> inv = (Optional<?>) curiosInventory.invoke(null, player);
                if (inv.isPresent() && curiosEquipped.invoke(inv.get()) instanceof IItemHandler items) {
                    for (int i = 0; i < items.getSlots(); i++) {
                        ItemStack s = items.getStackInSlot(i);
                        if (!s.isEmpty() && seen.add(s)) out.add(s);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        if (accessoriesInit()) {
            try {
                Optional<?> cap = (Optional<?>) accessoriesGet.invoke(null, player);
                if (cap.isPresent() && accessoriesAll.invoke(cap.get()) instanceof List<?> entries) {
                    for (Object e : entries) {
                        if (entryStack.invoke(e) instanceof ItemStack s && !s.isEmpty() && seen.add(s)) out.add(s);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return out;
    }
}
