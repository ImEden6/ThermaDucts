package com.mervyn.thermaducts.screen;

import com.mervyn.thermaducts.ThermaDucts;
import com.mervyn.thermaducts.menu.TransportConfigMenu;
import com.mervyn.thermaducts.network.payload.TransportRenamePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class TransportConfigScreen extends AbstractContainerScreen<TransportConfigMenu> {

  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath(ThermaDucts.MODID, "textures/gui/transport_config.png");

  private EditBox nameField;

  public TransportConfigScreen(TransportConfigMenu menu, Inventory playerInv, Component title) {
    super(menu, playerInv, title, 176, 134);
    this.inventoryLabelY = 40;
  }

  @Override
  protected void init() {
    super.init();
    nameField = addRenderableWidget(new EditBox(font, leftPos + 32, topPos + 18, 135, 10, title));
    nameField.setBordered(false);
    nameField.setMaxLength(TransportRenamePayload.MAX_NAME_LENGTH);
    nameField.setValue(menu.getEndpointName());
    nameField.setResponder(
        value ->
            ClientPacketDistributor.sendToServer(
                new TransportRenamePayload(menu.getDuctPos(), value)));
    nameField.setFocused(true);
    setInitialFocus(nameField);
  }

  @Override
  public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    if (nameField != null && nameField.isFocused()) {
      if (event.key() == InputConstants.KEY_ESCAPE) {
        return super.keyPressed(event);
      }
      nameField.keyPressed(event);
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
    if (nameField != null && nameField.charTyped(event)) {
      return true;
    }
    return super.charTyped(event);
  }

  @Override
  public boolean mouseClicked(
      net.minecraft.client.input.MouseButtonEvent event, boolean isClickInside) {
    if (nameField != null && nameField.mouseClicked(event, isClickInside)) {
      nameField.setFocused(true);
      setFocused(nameField);
      return true;
    }
    return super.mouseClicked(event, isClickInside);
  }

  @Override
  public void extractBackground(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractBackground(graphics, mouseX, mouseY, partialTick);
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        TEXTURE,
        leftPos,
        topPos,
        0f,
        0f,
        imageWidth,
        imageHeight,
        256,
        256);
  }
}
