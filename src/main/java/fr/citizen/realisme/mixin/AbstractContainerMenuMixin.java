package fr.citizen.realisme.mixin;

import fr.citizen.realisme.CraftTimers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Un clic sur la case résultat d'un établi / de l'inventaire lance la fabrication avec durée. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void citizenrealisme$clicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        Object self = this;
        if (slotId == 0 && player instanceof ServerPlayer sp && (self instanceof CraftingMenu || self instanceof InventoryMenu)) {
            CraftTimers.onResultClick(sp, (AbstractContainerMenu) self, clickType);
        }
    }
}
