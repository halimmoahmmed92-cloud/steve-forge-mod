package com.example.examplemod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The logic behind Steve's tablet: his inventory (top row), your inventory (below),
 * and the two task buttons. The server owns the real data, the client only mirrors it.
 */
public class SteveMenu extends AbstractContainerMenu {
    public static final int BUTTON_FOLLOW = 0;
    public static final int BUTTON_CHOP = 1;

    private static final int STEVE_SLOTS = SteveEntity.INVENTORY_SIZE;

    /** Null on the client. */
    private final SteveEntity steve;
    /** [0] = current task id, [1] = 1 if Steve has an axe. Synced to the client automatically. */
    private final ContainerData data;

    /** Client side: Forge calls this when the server opens the tablet. */
    public SteveMenu(int windowId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(windowId, playerInv, new SimpleContainer(STEVE_SLOTS), null, new SimpleContainerData(2));
    }

    /** Server side: built when you right-click Steve. */
    public SteveMenu(int windowId, Inventory playerInv, SteveEntity steve) {
        this(windowId, playerInv, steve.getInventory(), steve, new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? steve.getTask().ordinal() : (steve.hasAxe() ? 1 : 0);
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 2;
            }
        });
    }

    private SteveMenu(int windowId, Inventory playerInv, Container steveInventory, SteveEntity steve, ContainerData data) {
        super(ExampleMod.STEVE_MENU.get(), windowId);
        this.steve = steve;
        this.data = data;

        // Steve's inventory: one row of 9 slots
        for (int col = 0; col < STEVE_SLOTS; col++) {
            this.addSlot(new Slot(steveInventory, col, 8 + col * 18, 18));
        }
        // Your inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Your hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    public SteveTask getTask() {
        return SteveTask.byId(this.data.get(0));
    }

    public boolean hasAxe() {
        return this.data.get(1) == 1;
    }

    /** Called on the server when a button of the screen is pressed. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.steve == null || !this.steve.isAlive()) {
            return false;
        }
        if (id == BUTTON_FOLLOW) {
            this.steve.setTask(SteveTask.FOLLOW, this.steve.blockPosition());
            return true;
        }
        if (id == BUTTON_CHOP && this.steve.hasAxe()) {
            // He will work around the spot where YOU are standing when you give the order
            this.steve.setTask(SteveTask.CHOP_WOOD, player.blockPosition());
            return true;
        }
        return false;
    }

    /** Shift-click: move items between Steve's inventory and yours. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            if (index < STEVE_SLOTS) {
                if (!this.moveItemStackTo(stack, STEVE_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, STEVE_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return copy;
    }

    /** The tablet closes if Steve dies or you walk more than 8 blocks away. */
    @Override
    public boolean stillValid(Player player) {
        return this.steve == null || (this.steve.isAlive() && this.steve.distanceToSqr(player) < 64.0);
    }
}
