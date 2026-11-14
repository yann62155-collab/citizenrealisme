package fr.citizen.realisme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Équipement visible : le sac dans le dos, l'arme longue en bandoulière, l'épée et l'outil à la ceinture.
 * Ce sont des entités d'affichage d'objet (vanilla) qui suivent le joueur : rien à installer côté client.
 */
public final class GearDisplay {
    public static final String TAG = "citizenrealisme_gear";

    private enum Spot {
        BAG(new float[]{0f, 1.05f, -0.3f}, null, null, 0.6f),
        BACK(new float[]{0f, 1.15f, -0.27f}, null, null, 0.85f),
        LEFT_HIP(new float[]{0.3f, 0.72f, 0.02f}, Q_Y90, Q_ZM90, 0.6f),
        RIGHT_HIP(new float[]{-0.3f, 0.72f, 0.02f}, Q_Y90, Q_ZM90, 0.55f);

        final float[] translation;
        final float[] left;
        final float[] right;
        final float scale;

        Spot(float[] translation, float[] left, float[] right, float scale) {
            this.translation = translation;
            this.left = left;
            this.right = right;
            this.scale = scale;
        }
    }

    private static final float S = 0.70710677f;
    private static final float[] Q_Y90 = {0f, S, 0f, S};
    private static final float[] Q_ZM90 = {0f, 0f, -S, S};
    private static final float[] Q_ID = {0f, 0f, 0f, 1f};

    private static final class Worn {
        final EnumMap<Spot, Display.ItemDisplay> entities = new EnumMap<>(Spot.class);
        final EnumMap<Spot, ItemStack> stacks = new EnumMap<>(Spot.class);
    }

    private static final Map<UUID, Worn> WORN = new ConcurrentHashMap<>();

    private GearDisplay() {
    }

    public static void tick(ServerPlayer player) {
        RealismeConfig.Affichage cfg = RealismeConfig.get().equipementVisible;
        boolean hidden = !cfg.actif || player.isInvisible() || player.isSleeping() || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
        if (hidden) {
            remove(player.getUUID());
            return;
        }
        Worn worn = WORN.computeIfAbsent(player.getUUID(), k -> new Worn());
        if (player.tickCount % 10 == 0) {
            EnumMap<Spot, ItemStack> wanted = wanted(player, cfg);
            for (Spot spot : Spot.values()) {
                ItemStack want = wanted.getOrDefault(spot, ItemStack.EMPTY);
                ItemStack have = worn.stacks.getOrDefault(spot, ItemStack.EMPTY);
                Display.ItemDisplay e = worn.entities.get(spot);
                boolean entityGone = e != null && (e.isRemoved() || e.level() != player.level());
                if (!ItemStack.isSameItemSameComponents(want, have) || entityGone) {
                    if (e != null) e.discard();
                    worn.entities.remove(spot);
                    worn.stacks.remove(spot);
                    if (!want.isEmpty()) {
                        Display.ItemDisplay spawned = spawn(player, spot, want);
                        if (spawned != null) {
                            worn.entities.put(spot, spawned);
                            worn.stacks.put(spot, want.copyWithCount(1));
                        }
                    }
                }
            }
        }
        float yaw = player.yBodyRot;
        double dy = player.isCrouching() ? -0.25 : 0;
        for (Display.ItemDisplay e : worn.entities.values()) {
            if (!e.isRemoved()) e.moveTo(player.getX(), player.getY() + dy, player.getZ(), yaw, 0f);
        }
    }

    private static EnumMap<Spot, ItemStack> wanted(ServerPlayer player, RealismeConfig.Affichage cfg) {
        EnumMap<Spot, ItemStack> out = new EnumMap<>(Spot.class);
        if (cfg.sac) {
            String bag = Bags.equipped(player);
            if (bag != null) {
                ItemStack stack = RealismeItems.create(bag);
                if (stack != null) out.put(Spot.BAG, stack);
            }
        }
        int selected = player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            if (i == selected) continue;
            ItemStack s = player.getInventory().items.get(i);
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            if (cfg.armes && !out.containsKey(Spot.BACK) && (item instanceof BowItem || item instanceof CrossbowItem
                    || item instanceof TridentItem || item instanceof MaceItem)) {
                out.put(Spot.BACK, s);
            } else if (cfg.armes && !out.containsKey(Spot.LEFT_HIP) && item instanceof SwordItem) {
                out.put(Spot.LEFT_HIP, s);
            } else if (cfg.outils && !out.containsKey(Spot.RIGHT_HIP) && (item instanceof DiggerItem || RealismeItems.toolId(s) != null)) {
                out.put(Spot.RIGHT_HIP, s);
            }
        }
        return out;
    }

    @Nullable
    private static Display.ItemDisplay spawn(ServerPlayer player, Spot spot, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:item_display");
        tag.put("item", stack.copyWithCount(1).save(level.registryAccess()));
        tag.putString("item_display", "none");
        tag.putInt("teleport_duration", 2);
        tag.putFloat("view_range", 0.6f);
        CompoundTag tr = new CompoundTag();
        float[] translation = spot.translation.clone();
        if (spot == Spot.BACK && Bags.equipped(player) != null) translation[2] -= 0.12f;
        tr.put("translation", floats(translation));
        tr.put("left_rotation", floats(spot.left != null ? spot.left : Q_ID));
        tr.put("right_rotation", floats(spot.right != null ? spot.right : Q_ID));
        tr.put("scale", floats(new float[]{spot.scale, spot.scale, spot.scale}));
        tag.put("transformation", tr);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tags.add(StringTag.valueOf(TAG + "_" + player.getUUID()));
        tag.put("Tags", tags);
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(player.getX(), player.getY(), player.getZ(), player.yBodyRot, 0f);
            return e;
        });
        if (!(entity instanceof Display.ItemDisplay display)) return null;
        if (!level.addFreshEntity(display)) return null;
        return display;
    }

    private static ListTag floats(float[] values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    public static void remove(UUID player) {
        Worn worn = WORN.remove(player);
        if (worn == null) return;
        for (Display.ItemDisplay e : worn.entities.values()) e.discard();
    }

    public static void removeAll() {
        for (UUID id : WORN.keySet()) remove(id);
    }

    /** Entité d'équipement retrouvée au chargement d'un tronçon (serveur coupé brutalement) : à supprimer. */
    public static boolean isStray(Entity entity) {
        return entity instanceof Display.ItemDisplay && entity.getTags().contains(TAG);
    }
}
