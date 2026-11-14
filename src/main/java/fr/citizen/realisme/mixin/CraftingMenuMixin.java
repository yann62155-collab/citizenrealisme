package fr.citizen.realisme.mixin;

import fr.citizen.realisme.CraftTimers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Maj+clic sur le résultat : vanilla enchaîne les crafts sans revérifier, on bloque tant que ce n'est pas prêt. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void citizenrealisme$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index == 0 && player instanceof ServerPlayer sp && !CraftTimers.canTake(sp, (AbstractContainerMenu) (Object) this)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
