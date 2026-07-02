package com.radiologistics.create.gui;

import com.radiologistics.create.node.nodes.CustomNode;
import com.radiologistics.create.node.NodeGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public class CustomNodeEditorScreen extends Screen {
    private final NodeEditorScreen parentScreen;
    private final CustomNode node;

    private EditBox inputsBox;
    private EditBox outputsBox;
    private MultiLineEditBox codeBox;
    private boolean isUa = false;

    public CustomNodeEditorScreen(NodeEditorScreen parentScreen, CustomNode node) {
        super(Component.literal("Custom Node Editor"));
        this.parentScreen = parentScreen;
        this.node = node;
    }

    @Override
    protected void init() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        isUa = lang.toLowerCase().contains("uk_") || lang.toLowerCase().contains("ukr");

        int w = 320;
        int h = 240;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        String insStr = (inputsBox != null) ? inputsBox.getValue() : String.join(", ", node.getInputPorts());
        String outsStr = (outputsBox != null) ? outputsBox.getValue() : String.join(", ", node.getOutputPorts());
        String scriptStr = (codeBox != null) ? codeBox.getValue() : node.getCodeScript();

        if (inputsBox != null) removeWidget(inputsBox);
        if (outputsBox != null) removeWidget(outputsBox);
        if (codeBox != null) removeWidget(codeBox);

        inputsBox = new EditBox(this.font, x + 10, y + 38, 300, 14, Component.empty());
        inputsBox.setValue(insStr);
        inputsBox.setMaxLength(100);
        this.addRenderableWidget(inputsBox);

        outputsBox = new EditBox(this.font, x + 10, y + 68, 300, 14, Component.empty());
        outputsBox.setValue(outsStr);
        outputsBox.setMaxLength(100);
        this.addRenderableWidget(outputsBox);

        codeBox = new MultiLineEditBox(this.font, x + 10, y + 98, 300, 110, Component.empty(), Component.empty());
        codeBox.setValue(scriptStr);
        this.addRenderableWidget(codeBox);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {

        g.fill(0, 0, this.width, this.height, 0x80101010);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);

        int w = 320;
        int h = 240;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        g.fill(x, y, x + w, y + h, 0xFF141312);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A2C2D);

        g.fill(x + 3, y + 3, x + w - 3, y + 22, 0xFFC8963E);
        g.fill(x + 3, y + 21, x + w - 3, y + 22, 0x40000000);

        drawRivet(g, x + 5, y + 5);
        drawRivet(g, x + w - 7, y + 5);
        drawRivet(g, x + 5, y + 19);
        drawRivet(g, x + w - 7, y + 19);

        String title = isUa ? "РЕДАКТОР КАСТОМНОЇ НОДИ (JAVA)" : "CUSTOM NODE EDITOR (JAVA)";
        int tw = this.font.width(title);
        g.drawString(this.font, title, x + (w - tw) / 2, y + 7, 0xFFFFEAA0);

        String inputsLabel = isUa ? "Входи (через кому):" : "Inputs (comma-separated):";
        g.drawString(this.font, inputsLabel, x + 10, y + 28, 0xFFACAFB0);

        String outputsLabel = isUa ? "Виходи (через кому):" : "Outputs (comma-separated):";
        g.drawString(this.font, outputsLabel, x + 10, y + 58, 0xFFACAFB0);

        String codeLabel = isUa ? "Код джава для обчислення:" : "Java code for computation:";
        g.drawString(this.font, codeLabel, x + 10, y + 88, 0xFFACAFB0);

        drawCreateButton(g, isUa ? "Зберегти" : "Save", x + 190, y + 215, 54, 18, mx, my, true);
        drawCreateButton(g, isUa ? "Скасувати" : "Cancel", x + 250, y + 215, 54, 18, mx, my, false);

        super.render(g, mx, my, pt);
    }

    private void drawRivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0xFFE9C583);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF8C5F1C);
    }

    private void drawCreateButton(GuiGraphics g, String label, int x, int y, int w, int h, double mx, double my, boolean brassStyle) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;

        g.fill(x, y, x + w, y + h, 0xFF141312);

        int bg = brassStyle ? (hover ? 0xFFD8A64E : 0xFFC8963E) : (hover ? 0xFF6A6D6E : 0xFF5A5D5E);
        int highlight = brassStyle ? 0xFFE9C583 : 0xFF808284;
        int shadow = brassStyle ? 0xFF8C5F1C : 0xFF383A3B;

        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, highlight);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shadow);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shadow);

        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF141312);
        int faceBg = brassStyle ? (hover ? 0xFFB8860B : 0xFF996515) : (hover ? 0xFF4E5152 : 0xFF3A3D3E);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, faceBg);

        int tw = this.font.width(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 9) / 2;
        g.drawString(this.font, label, tx + 1, ty + 1, 0x80000000);
        int textColor = hover ? 0xFFFFFFFF : (brassStyle ? 0xFFFFEAA0 : 0xFFE0E0E0);
        g.drawString(this.font, label, tx, ty, textColor);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int w = 320;
        int h = 240;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        if (mx >= x + 190 && mx < x + 190 + 54 && my >= y + 215 && my < y + 215 + 18) {
            handleSave();
            playClickSound();
            return true;
        }

        if (mx >= x + 250 && mx < x + 250 + 54 && my >= y + 215 && my < y + 215 + 18) {
            Minecraft.getInstance().setScreen(parentScreen);
            playClickSound();
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private void handleSave() {

        String[] inParts = inputsBox.getValue().split(",");
        List<String> inputsList = new ArrayList<>();
        for (String p : inParts) {
            String clean = p.trim();
            if (!clean.isEmpty()) inputsList.add(clean);
        }

        String[] outParts = outputsBox.getValue().split(",");
        List<String> outputsList = new ArrayList<>();
        for (String p : outParts) {
            String clean = p.trim();
            if (!clean.isEmpty()) outputsList.add(clean);
        }

        String script = codeBox.getValue();

        parentScreen.pushUndoState();

        node.setSchema(inputsList, outputsList, script);

        cleanUpInvalidLinks(inputsList, outputsList);

        Minecraft.getInstance().setScreen(parentScreen);
    }

    private void cleanUpInvalidLinks(List<String> validInputs, List<String> validOutputs) {
        NodeGraph graph = parentScreen.graph;
        if (graph == null) return;

        String nodeId = node.getId();
        graph.getLinks().removeIf(link -> {
            if (link.toNode().equals(nodeId)) {
                return !validInputs.contains(link.toPort());
            }
            if (link.fromNode().equals(nodeId)) {
                return !validOutputs.contains(link.fromPort());
            }
            return false;
        });
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }
}
