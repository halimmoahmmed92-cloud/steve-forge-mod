package com.example.examplemod.client;

import com.example.examplemod.SteveMenu;
import com.example.examplemod.SteveTask;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Steve's tablet screen: his inventory, your inventory, and the task buttons. */
public class SteveScreen extends AbstractContainerScreen<SteveMenu> {
    // Built-in game sprites, so the mod needs no texture files
    private static final Identifier PANEL = Identifier.withDefaultNamespace("tooltip/background");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");

    private Button followButton;
    private Button chopButton;

    public SteveScreen(SteveMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.followButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.examplemod.tablet.follow"), button -> this.sendButton(SteveMenu.BUTTON_FOLLOW))
                .bounds(this.leftPos + 8, this.topPos + 50, 76, 20)
                .build());
        this.chopButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.examplemod.tablet.chop"), button -> this.sendButton(SteveMenu.BUTTON_CHOP))
                .bounds(this.leftPos + 92, this.topPos + 50, 76, 20)
                .build());
    }

    /** Tells the server which button was pressed (the vanilla way, no custom network code). */
    private void sendButton(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.getMenu().containerId, buttonId);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // The chop button only works when Steve holds an axe
        if (this.chopButton != null) {
            this.chopButton.active = this.getMenu().hasAxe();
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 0xFFFFFFFF);
        for (Slot slot : this.getMenu().slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, 18, 18, 0xFFFFFFFF);
        }

        // Status line
        Component status;
        if (this.getMenu().getTask() == SteveTask.CHOP_WOOD) {
            status = Component.translatable("gui.examplemod.tablet.task.chop");
        } else if (!this.getMenu().hasAxe()) {
            status = Component.translatable("gui.examplemod.tablet.need_axe");
        } else {
            status = Component.translatable("gui.examplemod.tablet.task.follow");
        }
        graphics.text(this.font, status, this.leftPos + 8, this.topPos + 39, 0xFFFFFFFF);
    }
}
