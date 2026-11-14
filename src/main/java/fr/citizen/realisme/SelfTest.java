package fr.citizen.realisme;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Autotest (uniquement avec -Dcitizenrealisme.selftest=true, sur GitHub Actions) : vrai serveur NeoForge,
 * vérification de chaque mécanique, puis arrêt avec un code de réussite ou d'échec.
 */
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("citizenrealisme.selftest");

    private SelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("ÉCHEC : " + what);
        CitizenRealisme.LOGGER.info("[test] OK — {}", what);
    }

    public static void run(MinecraftServer server) {
        int code = 0;
        try {
            ServerLevel level = server.overworld();
            ServerPlayer p = FakePlayerFactory.getMinecraft(level);
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();

            // ---- recettes : chaque objet craftable est identique à l'objet du mod
            for (String id : List.of("marteau", "scie", "aiguille", "sacoche", "sac_a_dos")) {
                Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, id));
                check(recipe.isPresent(), "recette " + id + " chargée");
                ItemStack out = recipe.get().value().getResultItem(server.registryAccess());
                ItemStack expected = RealismeItems.create(id.equals("sac_a_dos") ? RealismeItems.SAC_A_DOS : id);
                check(expected != null && ItemStack.isSameItem(out, expected)
                        && java.util.Objects.equals(RealismeItems.toolId(out), RealismeItems.toolId(expected))
                        && java.util.Objects.equals(RealismeItems.bagId(out), RealismeItems.bagId(expected))
                        && out.has(DataComponents.CUSTOM_MODEL_DATA), "résultat de la recette " + id + " = objet du mod");
            }

            // ---- poids
            check(Weights.unit(new ItemStack(Items.IRON_BLOCK)) == 6.0, "bloc de fer : 6 kg");
            check(Weights.unit(new ItemStack(Items.IRON_INGOT)) == 0.5, "lingot de fer : 0,5 kg");
            check(Weights.unit(new ItemStack(Items.IRON_CHESTPLATE)) == 3.5, "plastron en fer : 3,5 kg");
            check(Weights.unit(RealismeItems.lock()) == 0, "cadenas : 0 kg");

            // ---- cases verrouillées
            InventoryLocks.apply(p);
            check(!RealismeItems.isLock(p.getInventory().items.get(9)), "1re rangée ouverte sans sac");
            check(RealismeItems.isLock(p.getInventory().items.get(18)) && RealismeItems.isLock(p.getInventory().items.get(35)), "2e et 3e rangées verrouillées sans sac");
            Slot locked = new Slot(p.getInventory(), 20, 0, 0);
            check(!locked.mayPlace(new ItemStack(Items.DIRT)), "mixin : impossible de poser dans une case verrouillée");
            check(!locked.mayPickup(p), "mixin : impossible de prendre le cadenas");
            check(new Slot(p.getInventory(), 10, 0, 0).mayPlace(new ItemStack(Items.DIRT)), "case ouverte utilisable");
            ItemStack sac = RealismeItems.create(RealismeItems.SAC_A_DOS);
            p.getInventory().setItem(0, sac);
            Bags.equip(p, p.getInventory().getItem(0));
            InventoryLocks.apply(p);
            check(RealismeItems.SAC_A_DOS.equals(Bags.equipped(p)), "sac à dos équipé");
            check(!RealismeItems.isLock(p.getInventory().items.get(35)), "sac à dos : toutes les rangées ouvertes");
            check(Weights.capacity(p) == RealismeConfig.get().poids.capaciteBase + RealismeConfig.get().poids.bonusSacADos, "sac à dos : capacité augmentée");
            p.getInventory().setItem(30, new ItemStack(Items.DIRT));
            check(!Bags.unequip(p), "sac non vide : impossible de le retirer");
            p.getInventory().setItem(30, ItemStack.EMPTY);
            check(Bags.unequip(p), "sac vide : retiré");
            p.getInventory().clearContent();

            // ---- encombrement
            for (int i = 0; i < 9; i++) p.getInventory().setItem(i, new ItemStack(Items.IRON_BLOCK, 64));
            Encumbrance.forceRefresh(p);
            Encumbrance.tick(p);
            check(p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Encumbrance.SLOW), "surcharge : ralentissement");
            check(p.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(Encumbrance.NO_JUMP), "surcharge : plus de saut");
            p.getInventory().clearContent();
            Encumbrance.tick(p);
            check(!p.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Encumbrance.SLOW), "inventaire vide : vitesse normale");

            // ---- règles de craft
            check(RealismeItems.MARTEAU.equals(CraftRules.requiredTool(new ItemStack(Items.IRON_CHESTPLATE), List.of(new ItemStack(Items.IRON_INGOT)))), "plastron en fer : marteau");
            check(RealismeItems.SCIE.equals(CraftRules.requiredTool(new ItemStack(Items.OAK_DOOR), List.of(new ItemStack(Items.OAK_PLANKS)))), "porte : scie");
            check(RealismeItems.AIGUILLE.equals(CraftRules.requiredTool(new ItemStack(Items.RED_BED), List.of(new ItemStack(Items.RED_WOOL)))), "lit : aiguille");
            check(CraftRules.requiredTool(new ItemStack(Items.OAK_PLANKS, 4), List.of(new ItemStack(Items.OAK_LOG))) == null, "planches : sans outil");
            check(CraftRules.requiredTool(new ItemStack(Items.IRON_BLOCK), List.of(new ItemStack(Items.IRON_INGOT))) == null, "bloc de fer : sans outil");

            // ---- craft avec durée dans un vrai établi
            BlockPos pos = p.blockPosition();
            CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(level, pos));
            // pioche en fer : 3 lingots + 2 bâtons (5 cases, marteau requis)
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
            menu.getSlot(3).set(new ItemStack(Items.IRON_INGOT));
            menu.getSlot(5).set(new ItemStack(Items.STICK));
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            menu.slotsChanged(menu.getSlot(1).container);
            check(menu.getSlot(0).getItem().is(Items.IRON_PICKAXE), "établi : la pioche en fer apparaît");
            CraftRules.Plan plan = CraftRules.plan(p, menu);
            check(plan != null && RealismeItems.MARTEAU.equals(plan.tool()) && plan.missingTool() && !plan.instant(), "pioche : marteau manquant");
            check(!CraftTimers.canTake(p, menu), "sans marteau : résultat bloqué");
            p.getInventory().setItem(0, RealismeItems.create(RealismeItems.MARTEAU));
            plan = CraftRules.plan(p, menu);
            check(plan != null && !plan.missingTool() && plan.rate() >= 70 && plan.rate() <= 99 && plan.ticks() > 0,
                    "pioche : réussite " + (plan == null ? "?" : plan.rate()) + " %, " + (plan == null ? "?" : plan.ticks()) + " ticks");
            check(!CraftTimers.canTake(p, menu), "avant la fin : résultat bloqué");

            // ---- rituel d'enchantement
            level.setBlockAndUpdate(pos.above(5), Blocks.ENCHANTING_TABLE.defaultBlockState());
            EnchantmentMenu em = new EnchantmentMenu(2, p.getInventory(), ContainerLevelAccess.create(level, pos.above(5)));
            check(EnchantRitual.rate(0, 0) == 92 && EnchantRitual.rate(2, 15) == 77, "taux du rituel (bibliothèques comprises)");
            check(!EnchantRitual.hasCatalysts(p, 0), "sans améthyste : catalyseurs manquants");
            p.getInventory().setItem(5, new ItemStack(Items.AMETHYST_SHARD, 3));
            check(EnchantRitual.hasCatalysts(p, 0), "avec améthyste : rituel possible");
            check(EnchantRitual.shelves(ContainerLevelAccess.create(level, pos.above(5))) == 0, "comptage des bibliothèques");
            em.removed(p);

            CitizenRealisme.LOGGER.info("[Citizen Réalisme] SELFTEST OK");
        } catch (Throwable t) {
            CitizenRealisme.LOGGER.error("[Citizen Réalisme] SELFTEST FAILED", t);
            code = 1;
        }
        final int exit = code;
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(exit);
        }, "citizenrealisme-selftest-exit").start();
    }
}
