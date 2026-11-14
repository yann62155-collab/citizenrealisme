package fr.citizen.realisme.mixin;

import fr.citizen.realisme.EnchantRitual;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rituel : catalyseurs requis et taux de réussite avant chaque enchantement. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin extends AbstractContainerMenu {
    @Shadow @Final private Container enchantSlots;
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private DataSlot enchantmentSeed;
    @Shadow @Final public int[] costs;

    @Unique
    private int citizenrealisme$pendingOption = -1;

    protected EnchantmentMenuMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void citizenrealisme$before(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        citizenrealisme$pendingOption = -1;
        if (!(player instanceof ServerPlayer sp) || id < 0 || id >= costs.length) return;
        ItemStack item = enchantSlots.getItem(0);
        ItemStack lapis = enchantSlots.getItem(1);
        int needed = id + 1;
        boolean creative = player.getAbilities().instabuild;
        // Mêmes conditions que vanilla : si vanilla refuse, on le laisse refuser.
        if ((lapis.isEmpty() || lapis.getCount() < needed) && !creative) return;
        if (costs[id] <= 0 || item.isEmpty() || (player.experienceLevel < needed || player.experienceLevel < costs[id]) && !creative) return;

        switch (EnchantRitual.attempt(sp, id, access)) {
            case MISSING -> cir.setReturnValue(false);
            case SUCCESS -> citizenrealisme$pendingOption = id;
            case FAILURE -> {
                if (!creative) {
                    lapis.shrink(needed);
                    if (lapis.isEmpty()) enchantSlots.setItem(1, ItemStack.EMPTY);
                }
                player.onEnchantmentPerformed(item, needed);
                EnchantRitual.onFailure(sp, id, item, access);
                enchantSlots.setChanged();
                enchantmentSeed.set(player.getEnchantmentSeed());
                this.slotsChanged(enchantSlots);
                cir.setReturnValue(true);
            }
            default -> {
            }
        }
    }

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void citizenrealisme$after(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        int option = citizenrealisme$pendingOption;
        citizenrealisme$pendingOption = -1;
        if (option >= 0 && Boolean.TRUE.equals(cir.getReturnValue()) && player instanceof ServerPlayer sp) {
            EnchantRitual.onSuccess(sp, option, access);
        }
    }
}
