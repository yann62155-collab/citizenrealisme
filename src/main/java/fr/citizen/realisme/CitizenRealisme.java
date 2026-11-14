package fr.citizen.realisme;

import fr.citizen.realisme.net.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Citizen Réalisme : inventaire (poids, sacs, cases), craft (durée, outils, taux de réussite),
 * enchantement (rituel) et équipement visible. Toute la logique est côté serveur, donc elle
 * s'applique de la même façon aux joueurs PC et aux joueurs console (Citizen Bedrock).
 */
@Mod(CitizenRealisme.MODID)
public final class CitizenRealisme {
    public static final String MODID = "citizenrealisme";
    public static final Logger LOGGER = LoggerFactory.getLogger("Citizen Réalisme");

    public CitizenRealisme(IEventBus modBus, ModContainer container) {
        RealismeConfig.load();
        modBus.addListener(Net::register);

        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onLogin);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
        NeoForge.EVENT_BUS.addListener(this::onRespawn);
        NeoForge.EVENT_BUS.addListener(this::onCrafted);
        NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(this::onDrops);
        NeoForge.EVENT_BUS.addListener(this::onToss);
        NeoForge.EVENT_BUS.addListener(this::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(this::onContainerClose);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> RealismeCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> GearDisplay.removeAll());
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            if (SelfTest.ENABLED) SelfTest.run(e.getServer());
        });
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isRemoved()) return;
        try {
            InventoryLocks.apply(player);
            Encumbrance.tickSprint(player);
            if (player.tickCount % 10 == 0) Encumbrance.tick(player);
            CraftTimers.tick(player);
            EnchantRitual.tick(player);
            GearDisplay.tick(player);
        } catch (RuntimeException e) {
            LOGGER.error("[Citizen Réalisme] Erreur pendant le tick de {}", player.getGameProfile().getName(), e);
        }
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Encumbrance.forceRefresh(player);
        }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GearDisplay.remove(player.getUUID());
            CraftTimers.forget(player.getUUID());
            EnchantRitual.forget(player.getUUID());
            Encumbrance.forget(player.getUUID());
        }
    }

    private void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GearDisplay.remove(player.getUUID());
            Encumbrance.forceRefresh(player);
        }
    }

    private void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CraftTimers.onCrafted(player);
    }

    private void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (RealismeItems.bagId(stack) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getEntity() instanceof ServerPlayer player) Bags.equip(player, stack);
        } else if (RealismeItems.isLock(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Les outils et sacs ne servent qu'à l'artisanat : pas de chemin, de feu ni de pose de barrière. */
    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (RealismeItems.isOurs(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (RealismeItems.bagId(stack) != null && event.getEntity() instanceof ServerPlayer player) {
                Bags.equip(player, stack);
            }
        }
    }

    private void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (RealismeItems.isOurs(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        event.getDrops().removeIf(e -> RealismeItems.isLock(e.getItem()));
        boolean keep = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        String bag = Bags.equipped(player);
        if (!keep && bag != null) {
            ItemStack stack = RealismeItems.create(bag);
            if (stack != null) {
                event.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack));
            }
            Bags.setEquipped(player, null);
        }
        GearDisplay.remove(player.getUUID());
    }

    private void onToss(ItemTossEvent event) {
        if (RealismeItems.isLock(event.getEntity().getItem())) event.setCanceled(true);
    }

    private void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ItemEntity item && RealismeItems.isLock(item.getItem())) {
            event.setCanceled(true);
        } else if (event.loadedFromDisk() && GearDisplay.isStray(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private void onContainerClose(PlayerContainerEvent.Close event) {
        InventoryLocks.purgeContainer(event.getContainer());
    }
}
