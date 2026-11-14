package fr.citizen.realisme.mixin;

import fr.citizen.realisme.CraftTimers;
import fr.citizen.realisme.InventoryLocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cases verrouillées de l'inventaire et case résultat d'un craft en cours (côté serveur uniquement). */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void citizenrealisme$mayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryLocks.denyPlace((Slot) (Object) this, stack)) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void citizenrealisme$mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (InventoryLocks.denyPickup(self)) {
            cir.setReturnValue(false);
            return;
        }
        if (self instanceof ResultSlot && player instanceof ServerPlayer sp && !CraftTimers.canTake(sp, sp.containerMenu)) {
            cir.setReturnValue(false);
        }
    }
}
