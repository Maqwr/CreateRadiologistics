package com.radiologistics.create.gui;

import com.radiologistics.create.network.SaveTransmitterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class TransmitterScreen extends Screen {
    private final BlockPos pos;
    private final String initialChannel;
    private final String initialMessage;
    private final int range;

    private EditBox channelEdit;
    private EditBox messageEdit;

    public TransmitterScreen(BlockPos pos, String channel, String message, int range) {
        super(Component.literal("Radio Transmitter Configuration"));
        this.pos = pos;
        this.initialChannel = channel;
        this.initialMessage = message;
        this.range = range;
    }

    @Override
    protected void init() {
        super.init();
        
        int boxWidth = 200;
        int x = (this.width - boxWidth) / 2;
        int y = this.height / 2 - 50;

        // Channel EditBox
        this.channelEdit = new EditBox(this.font, x, y, boxWidth, 20, Component.literal("Channel"));
        this.channelEdit.setFilter(t -> t.matches("\\d*") && (t.isEmpty() || Integer.parseInt(t) <= 255));
        this.channelEdit.setMaxLength(3);
        
        String sanitizedInitial = com.radiologistics.create.radio.RadioNetworkManager.sanitizeChannel(this.initialChannel);
        this.channelEdit.setValue(sanitizedInitial);
        this.addWidget(this.channelEdit);

        // Message EditBox
        this.messageEdit = new EditBox(this.font, x, y + 40, boxWidth, 20, Component.literal("Message"));
        this.messageEdit.setMaxLength(128);
        this.messageEdit.setValue(this.initialMessage);
        this.addWidget(this.messageEdit);

        // Save Button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            saveAndClose();
        }).bounds(x, y + 80, boxWidth, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw a clean semi-transparent background without the blur shader
        graphics.fill(0, 0, this.width, this.height, 0x80101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 80, 0xFFFFFF);
        
        graphics.drawString(this.font, "Channel (0-255):", this.width / 2 - 100, this.height / 2 - 62, 0xA0A0A0);
        graphics.drawString(this.font, "Message (Redstone Trigger):", this.width / 2 - 100, this.height / 2 - 22, 0xA0A0A0);

        graphics.drawCenteredString(this.font, Component.translatable("gui.radiologistics.signal_range", this.range), this.width / 2, this.height / 2 + 15, 0x80FF80);

        this.channelEdit.render(graphics, mouseX, mouseY, partialTick);
        this.messageEdit.render(graphics, mouseX, mouseY, partialTick);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter Key
            saveAndClose();
            return true;
        }
        return false;
    }

    private void saveAndClose() {
        String ch = com.radiologistics.create.radio.RadioNetworkManager.sanitizeChannel(this.channelEdit.getValue().trim());
        String msg = this.messageEdit.getValue();
        PacketDistributor.sendToServer(new SaveTransmitterPacket(pos, ch, msg));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
