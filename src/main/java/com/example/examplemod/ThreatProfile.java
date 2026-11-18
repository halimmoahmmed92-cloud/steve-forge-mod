package com.example.examplemod;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record ThreatProfile(boolean waterBucket, boolean elytra, boolean totem, boolean armed) {

    public static ThreatProfile of(Player player) {
        boolean waterBucket = false;
        boolean elytra = false;
        boolean totem = false;
        boolean armed = false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.WATER_BUCKET)) {
                waterBucket = true;
            } else if (stack.is(Items.ELYTRA)) {
                elytra = true;
            } else if (stack.is(Items.TOTEM_OF_UNDYING)) {
                totem = true;
            } else if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
                armed = true;
            }
        }
        return new ThreatProfile(waterBucket, elytra, totem, armed);
    }

    public boolean canSurviveFall() {
        return this.waterBucket || this.elytra || this.totem;
    }
}
