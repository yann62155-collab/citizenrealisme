package fr.citizen.realisme;

import fr.citizen.realisme.net.CraftPayload;
import fr.citizen.realisme.net.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabrication avec durée : un clic sur le résultat lance la fabrication (barre de progression),
 * puis le serveur tire la réussite selon le taux affiché. Réussite : l'objet va dans l'inventaire.
 * Échec : une partie des ingrédients est perdue. Bouger, fermer ou changer la grille annule.
 */
public final class CraftTimers {
    private static final class Job {
        final int containerId;
        final long hash;
        final CraftRules.Plan plan;
        final double x, y, z;
        int elapsed;
        boolean ready;

        Job(AbstractContainerMenu menu, CraftRules.Plan plan, ServerPlayer p) {
            this.containerId = menu.containerId;
            this.hash = plan.hash();
            this.plan = plan;
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
        }
    }

    private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_PREVIEW = new ConcurrentHashMap<>();

    private CraftTimers() {
    }

    public static boolean active() {
        return RealismeConfig.get().craft.actif;
    }

    public static boolean isBusy(ServerPlayer player) {
        return JOBS.containsKey(player.getUUID()) || LAST_PREVIEW.containsKey(player.getUUID());
    }

    public static void forget(UUID id) {
        JOBS.remove(id);
        LAST_PREVIEW.remove(id);
    }

    // ------------------------------------------------------------------ appelé par les mixins

    /** Clic sur la case résultat (avant traitement vanilla). */
    public static void onResultClick(ServerPlayer player, AbstractContainerMenu menu, ClickType type) {
        if (!active()) return;
        if (type != ClickType.PICKUP && type != ClickType.QUICK_MOVE && type != ClickType.THROW && type != ClickType.SWAP) return;
        CraftRules.Plan plan = CraftRules.plan(player, menu);
        if (plan == null || plan.instant()) return;
        Job job = JOBS.get(player.getUUID());
        if (job != null && job.containerId == menu.containerId && job.hash == plan.hash()) {
            if (!job.ready) {
                player.displayClientMessage(Component.literal("Fabrication en cours… ne bouge pas.").withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }
        if (plan.missingTool()) {
            player.displayClientMessage(Component.literal("Il te faut un outil : " + RealismeItems.toolName(plan.tool())
                    + " (dans ton inventaire).").withStyle(ChatFormatting.RED), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.0F);
            return;
        }
        JOBS.put(player.getUUID(), new Job(menu, plan, player));
        sound(player, plan, 0.7F);
        if (!Net.modded(player)) {
            player.displayClientMessage(Component.literal("Fabrication : " + plan.result().getHoverName().getString()
                    + " — " + plan.summary()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** La case résultat peut-elle être prise maintenant ? */
    public static boolean canTake(ServerPlayer player, AbstractContainerMenu menu) {
        if (!active()) return true;
        CraftRules.Plan plan = CraftRules.plan(player, menu);
        if (plan == null || plan.instant()) return true;
        Job job = JOBS.get(player.getUUID());
        return job != null && job.ready && job.containerId == menu.containerId && job.hash == plan.hash();
    }

    /** Un objet a été fabriqué (PlayerEvent.ItemCraftedEvent) : la fabrication prête est consommée. */
    public static void onCrafted(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        if (job != null && job.ready) JOBS.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ chaque tick

    public static void tick(ServerPlayer player) {
        if (!active()) return;
        AbstractContainerMenu menu = player.containerMenu;
        Job job = JOBS.get(player.getUUID());
        if (job == null) {
            if (player.tickCount % 5 == 0) preview(player, menu);
            return;
        }
        CraftRules.Plan plan = menu.containerId == job.containerId ? CraftRules.plan(player, menu) : null;
        if (plan == null || plan.hash() != job.hash) {
            cancel(player, job.ready ? null : "La grille a changé : fabrication annulée.");
            return;
        }
        if (job.ready) return;
        RealismeConfig.Craft cfg = RealismeConfig.get().craft;
        if (cfg.annulerSiBouge && player.distanceToSqr(job.x, job.y, job.z) > cfg.distanceMax * cfg.distanceMax) {
            cancel(player, "Tu as bougé : fabrication interrompue.");
            return;
        }
        job.elapsed++;
        int total = job.plan.ticks();
        if (job.elapsed % 10 == 0 && job.elapsed < total) {
            sound(player, job.plan, 0.5F);
        }
        if (job.elapsed >= total) {
            finish(player, menu, job);
            return;
        }
        if (job.elapsed % 3 == 0) {
            float progress = job.elapsed / (float) total;
            if (Net.modded(player)) {
                Net.send(player, new CraftPayload(true, true, progress, job.plan.rate(), job.plan.summary()));
            } else if (job.elapsed % 6 == 0) {
                player.displayClientMessage(progressBar(progress, job.plan), true);
            }
        }
    }

    private static void preview(ServerPlayer player, AbstractContainerMenu menu) {
        CraftRules.Plan plan = CraftRules.plan(player, menu);
        String key = plan == null || plan.instant() ? null : plan.hash() + plan.summary();
        String last = LAST_PREVIEW.get(player.getUUID());
        if (key == null) {
            if (last != null) {
                LAST_PREVIEW.remove(player.getUUID());
                if (Net.modded(player)) Net.send(player, CraftPayload.HIDDEN);
            }
            return;
        }
        boolean modded = Net.modded(player);
        if (!key.equals(last)) {
            LAST_PREVIEW.put(player.getUUID(), key);
            if (modded) Net.send(player, new CraftPayload(true, false, 0, plan.rate(), plan.summary()));
        }
        if (!modded && (player.tickCount % 20 == 0 || !key.equals(last))) {
            player.displayClientMessage(Component.literal(plan.result().getHoverName().getString() + " — " + plan.summary()
                    + " — clique sur le résultat").withStyle(plan.missingTool() ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        }
    }

    private static Component progressBar(float progress, CraftRules.Plan plan) {
        int full = Math.round(progress * 10);
        String bar = "▮".repeat(full) + "▯".repeat(10 - full);
        return Component.literal("Fabrication " + bar + " " + Math.round(progress * 100) + " % — réussite " + plan.rate() + " %")
                .withStyle(ChatFormatting.GOLD);
    }

    private static void cancel(ServerPlayer player, String message) {
        JOBS.remove(player.getUUID());
        LAST_PREVIEW.remove(player.getUUID());
        if (Net.modded(player)) Net.send(player, CraftPayload.HIDDEN);
        if (message != null) {
            player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
        }
    }

    private static void finish(ServerPlayer player, AbstractContainerMenu menu, Job job) {
        CraftRules.Plan plan = job.plan;
        ServerLevel level = player.serverLevel();
        boolean success = level.random.nextInt(100) < plan.rate();
        damageTool(player, plan);
        LAST_PREVIEW.remove(player.getUUID());
        if (Net.modded(player)) Net.send(player, CraftPayload.HIDDEN);

        if (!success) {
            JOBS.remove(player.getUUID());
            List<Slot> grid = CraftRules.grid(menu);
            int lost = 0;
            if (grid != null) {
                int chance = RealismeConfig.get().craft.pertePourcent;
                Slot firstFilled = null;
                for (Slot s : grid) {
                    if (!s.hasItem()) continue;
                    if (firstFilled == null) firstFilled = s;
                    if (level.random.nextInt(100) < chance) {
                        s.remove(1);
                        lost++;
                    }
                }
                if (lost == 0 && firstFilled != null) {
                    firstFilled.remove(1);
                    lost = 1;
                }
            }
            menu.broadcastChanges();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
            level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.2, 0.3, 0.01);
            player.displayClientMessage(Component.literal("Raté ! (" + plan.rate() + " % de réussite) — " + lost
                    + " ingrédient" + (lost > 1 ? "s" : "") + " perdu" + (lost > 1 ? "s" : "") + ".").withStyle(ChatFormatting.RED), true);
            return;
        }

        job.ready = true;
        String name = plan.result().getHoverName().getString();
        // Le résultat part directement dans l'inventaire, comme un Maj+clic (un seul objet).
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        menu.broadcastChanges();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 1.6F);
        if (JOBS.get(player.getUUID()) == job) {
            player.displayClientMessage(Component.literal(name + " est prêt : clique sur le résultat pour le prendre (inventaire plein).")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.literal("Réussi : " + name + " fabriqué.").withStyle(ChatFormatting.GREEN), true);
        }
    }

    private static void damageTool(ServerPlayer player, CraftRules.Plan plan) {
        if (plan.tool() == null) return;
        ItemStack tool = CraftRules.findTool(player, plan.tool());
        if (tool == null) return;
        String name = RealismeItems.toolName(plan.tool());
        tool.hurtAndBreak(1, player.serverLevel(), player, item ->
                player.sendSystemMessage(Component.literal("Ton outil s'est brisé : " + name).withStyle(ChatFormatting.RED)));
    }

    private static void sound(ServerPlayer player, CraftRules.Plan plan, float volume) {
        SoundEvent sound;
        String tool = plan.tool();
        if (RealismeItems.MARTEAU.equals(tool)) sound = SoundEvents.ANVIL_USE;
        else if (RealismeItems.SCIE.equals(tool)) sound = SoundEvents.WOOD_HIT;
        else if (RealismeItems.AIGUILLE.equals(tool)) sound = SoundEvents.WOOL_PLACE;
        else sound = SoundEvents.WOOD_PLACE;
        float pitch = RealismeItems.MARTEAU.equals(tool) ? 1.4F : 1.0F;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume * (sound == SoundEvents.ANVIL_USE ? 0.4F : 1F), pitch);
    }
}
