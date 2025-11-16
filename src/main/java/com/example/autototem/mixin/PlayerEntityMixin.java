package com.example.autototem.mixin;

import com.example.autototem.AutoTotemMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(
            method = "getStackInHand",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onGetStackInHand(Hand hand, CallbackInfoReturnable<ItemStack> cir) {
        if (!AutoTotemMod.isModEnabled()) {
            return;
        }

        LivingEntity entity = (LivingEntity) (Object) this;

        // Hanya untuk player
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }

        // Hanya handle offhand
        if (hand != Hand.OFF_HAND) {
            return;
        }

        // Cek apakah player health rendah (di bawah 1 heart)
        if (player.getHealth() <= 2.0F) {
            PlayerInventory inventory = player.getInventory();
            ItemStack offhandItem = inventory.offHand.get(0);

            // Jika belum ada totem di offhand
            if (!offhandItem.isOf(Items.TOTEM_OF_UNDYING)) {
                int totemSlot = findTotemInInventory(inventory);

                if (totemSlot != -1) {
                    ItemStack totem = inventory.getStack(totemSlot);
                    ItemStack previousOffhand = offhandItem.copy();

                    // Swap items
                    inventory.offHand.set(0, totem.copy());
                    inventory.setStack(totemSlot, previousOffhand);

                    // Return totem untuk mencegah kematian
                    cir.setReturnValue(totem.copy());
                }
            }
        }
    }

    private int findTotemInInventory(PlayerInventory inventory) {
        // Check hotbar
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        // Check main inventory
        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        return -1;
    }
}