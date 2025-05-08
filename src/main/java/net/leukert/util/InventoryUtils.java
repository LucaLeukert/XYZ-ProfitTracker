package net.leukert.util;

import net.leukert.Main;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.StringUtils;

public class InventoryUtils {
    public static int getAmountOfItemInInventory(String item) {
        int amount = 0;
        for (Slot slot : Main.mc.thePlayer.inventoryContainer.inventorySlots) {
            if (slot.getHasStack()) {
                String itemName = StringUtils.stripControlCodes(slot.getStack().getDisplayName());
                if (itemName.equals(item)) {
                    amount += slot.getStack().stackSize;
                }
            }
        }
        return amount;
    }

    public static boolean isInventoryEmpty(EntityPlayer player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (player.inventory.getStackInSlot(i) != null) {
                return false;
            }
        }
        return true;
    }

    public enum ClickType {
        LEFT,
        RIGHT
    }

    public enum ClickMode {
        PICKUP,
        QUICK_MOVE,
        SWAP
    }
}
