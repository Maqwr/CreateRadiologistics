package com.radiologistics.create.gui;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.NodeGraph;
import com.radiologistics.create.node.NodeGraph.NodeLink;
import com.radiologistics.create.node.nodes.*;
import com.radiologistics.create.network.SaveComputerGraphPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Node editor screen — all rendering is done in screen-space coordinates
 * (no g.pose().scale()) to avoid NeoForge fill-rendering issues with pose transforms.
 */
public class NodeEditorScreen extends Screen {

    // ─── Layout ───────────────────────────────────────────────────────────────
    private static final int SIDEBAR_W  = 128;
    private static final int NODE_W     = 130; // canvas-space width of a node
    private static final int HDR_H      = 16;  // canvas-space header height
    private static final int ROW_H      = 14;  // canvas-space port row height
    private static final int PROP_H     = 18;  // canvas-space property row height

    // ─── Material Themes (Create Mod Style) ──────────────────────────────────
    private static class MaterialTheme {
        final int primary;
        final int highlight;
        final int shadow;
        final int bodyBg;
        final int headerBg;
        final int insetBg;

        MaterialTheme(int primary, int highlight, int shadow, int bodyBg, int headerBg, int insetBg) {
            this.primary = primary;
            this.highlight = highlight;
            this.shadow = shadow;
            this.bodyBg = bodyBg;
            this.headerBg = headerBg;
            this.insetBg = insetBg;
        }
    }

    private static final MaterialTheme THEME_ANDESITE = new MaterialTheme(0xFF7F8485, 0xFFACAFB0, 0xFF4A4D4E, 0xFF2A2C2D, 0xFF3E4041, 0xFF181A1B);
    private static final MaterialTheme THEME_COPPER   = new MaterialTheme(0xFFB76D55, 0xFFDCA28E, 0xFF85412A, 0xFF35201B, 0xFF512F25, 0xFF1D100D);
    private static final MaterialTheme THEME_BRASS    = new MaterialTheme(0xFFC8963E, 0xFFE9C583, 0xFF8C5F1C, 0xFF332617, 0xFF533F25, 0xFF1D150B);
    private static final MaterialTheme THEME_ZINC     = new MaterialTheme(0xFF4F6E80, 0xFF7CA0B5, 0xFF2C4452, 0xFF1A262E, 0xFF2B3E4B, 0xFF0E161B);
    private static final MaterialTheme THEME_OBSIDIAN = new MaterialTheme(0xFF5C3C6B, 0xFF8E659E, 0xFF3D214A, 0xFF1A111F, 0xFF2B1B34, 0xFF0F0914);
    private static final MaterialTheme THEME_ROSE     = new MaterialTheme(0xFFC97C8E, 0xFFECACBC, 0xFF964E5E, 0xFF3B2127, 0xFF58333D, 0xFF1F1014);
    private static final MaterialTheme THEME_REDSTONE = new MaterialTheme(0xFF9E2A2B, 0xFFC94A4C, 0xFF6B1516, 0xFF2D1415, 0xFF4A1E20, 0xFF1A0A0A);
    private static final MaterialTheme THEME_COMMENT  = new MaterialTheme(0xFF5F5A57, 0xFF85807D, 0xFF3C3836, 0xFF22201F, 0xFF3A3634, 0xFF141312);

    private MaterialTheme getThemeForType(String type) {
        return switch (type) {
            case "signal"                   -> THEME_ZINC;
            case "bool"                     -> THEME_COPPER;
            case "number"                   -> THEME_ANDESITE;
            case "text"                     -> THEME_BRASS;
            case "comment"                  -> THEME_COMMENT;
            case "equal","not_equal"      -> THEME_COPPER;
            case "greater_than","less_than" -> THEME_COPPER;
            case "or"                     -> THEME_COPPER;
            case "add","subtract","multiply","divide" -> THEME_ANDESITE;
            case "floor","sqrt","square"  -> THEME_ANDESITE;
            case "atan2"                  -> THEME_ANDESITE;
            case "invert","abs"           -> THEME_ANDESITE;
            case "sin","cos","tan","ctg"  -> THEME_ANDESITE;
            case "if"                     -> THEME_COPPER;
            case "redstone_output"        -> THEME_REDSTONE;
            case "jammer"                 -> THEME_REDSTONE;
            case "link_input","link_output" -> THEME_BRASS;
            case "gyroscope","gyroscope_position" -> THEME_OBSIDIAN;
            case "antenna_input","antenna_output" -> THEME_ZINC;
            case "variable","set_variable"-> THEME_ROSE;
            case "active_target"          -> THEME_OBSIDIAN;
            case "bool_viewer"            -> THEME_COPPER;
            case "number_viewer"          -> THEME_ANDESITE;
            case "text_viewer"            -> THEME_BRASS;
            case "display_link"           -> THEME_BRASS;
            case "text_split","text_join" -> THEME_BRASS;
            case "text_speak","sound_play" -> THEME_BRASS;
            default                       -> THEME_COMMENT;
        };
    }


    // ─── Sidebar card catalogue ───────────────────────────────────────────────
    private static final String[][] CONSTANT_CARDS = {
        {"Signal",     "signal",          "≈"},
        {"Bool",       "bool",            "T/F"},
        {"Number",     "number",          "#"},
        {"Text",       "text",            "\"\""},
        {"Comment",    "comment",         "//"},
    };

    private static final String[][] OPERATOR_CARDS = {
        {"If",         "if",              "?"},
        {"Or",         "or",              "||"},
        {"Equal",      "equal",           "="},
        {"Not Equal",  "not_equal",       "≠"},
        {">",          "greater_than",    ">"},
        {"<",          "less_than",       "<"},
        {"Add",        "add",             "+"},
        {"Subtract",   "subtract",        "−"},
        {"Multiply",   "multiply",        "×"},
        {"Divide",     "divide",          "÷"},
        {"Floor",      "floor",           "⌊⌋"},
        {"Sqrt",       "sqrt",            "√"},
        {"Square",     "square",          "x²"},
        {"Atan2",      "atan2",           "atan2"},
        {"Invert",     "invert",          "−x"},
        {"Abs",        "abs",             "|x|"},
        {"Sin",        "sin",             "sin"},
        {"Cos",        "cos",             "cos"},
        {"Tan",        "tan",             "tan"},
        {"Ctg",        "ctg",             "ctg"},
        {"Text Split", "text_split",      "✂"},
        {"Text Join",  "text_join",       "⧉"},
    };

    private static final String[][] IO_CARDS = {
        {"RS Output",  "redstone_output", "RS"},
        {"Display Link", "display_link",  "DL"},
    };

    private static final String[][] VIEWER_CARDS = {
        {"Bool View",  "bool_viewer",     "👁B"},
        {"Num View",   "number_viewer",   "👁#"},
        {"Text View",  "text_viewer",     "👁T"},
    };


    // ─── Data ────────────────────────────────────────────────────────────────
    private final BlockPos   pos;
    private final NodeGraph  graph;
    private final Set<String> connectedModules = new HashSet<>();
    private final List<String> nodeOrder = new ArrayList<>();

    // ─── Viewport ─────────────────────────────────────────────────────────────
    private double panX = 0, panY = 0, zoom = 1.0;

    private static final net.minecraft.resources.ResourceLocation BACKGROUND_TEX = net.minecraft.resources.ResourceLocation.parse("radiologistics:textures/gui/background.png");

    private String errorMessage = "";
    private long errorMessageExpiry = 0;

    private void showErrorMessage(String msg) {
        this.errorMessage = msg;
        this.errorMessageExpiry = System.currentTimeMillis() + 3000;
    }

    private void clampPan() {
        int viewportW = this.width - SIDEBAR_W;
        int viewportH = this.height - 28;
        double bgW = 5120.0;
        double bgH = 3200.0;

        double minPanX = -bgW * zoom + viewportW;
        double maxPanX = 0;
        if (minPanX > maxPanX) {
            panX = (viewportW - bgW * zoom) / 2.0;
        } else {
            panX = Math.max(minPanX, Math.min(maxPanX, panX));
        }

        double minPanY = -bgH * zoom + viewportH;
        double maxPanY = 0;
        if (minPanY > maxPanY) {
            panY = (viewportH - bgH * zoom) / 2.0;
        } else {
            panY = Math.max(minPanY, Math.min(maxPanY, panY));
        }
    }

    // ─── Selection ────────────────────────────────────────────────────────────
    private AlgoNode selectedNode = null;
    private NodeLink  selectedLink = null;
    private String    selectedVar  = null;
    private final Set<String> selectedNodeIds = new HashSet<>();

    // ─── Undo/Redo History ────────────────────────────────────────────────────
    private final List<CompoundTag> undoHistory = new ArrayList<>();
    private static final int MAX_UNDO_STATES = 50;

    // ─── Node dragging ────────────────────────────────────────────────────────
    private AlgoNode dragging   = null;
    private double   dragOffX, dragOffY;
    private CompoundTag dragStartState = null;

    // ─── Canvas panning ───────────────────────────────────────────────────────
    private boolean isPanning  = false;
    private double  panStartX, panStartY;

    // ─── Wire dragging ────────────────────────────────────────────────────────
    private String  wireSrcId  = null;
    private String  wireSrcPort = null;
    private boolean wireSrcOut = false;
    private double  wireDragCX, wireDragCY; // canvas-space drag tip

    // ─── Sidebar ──────────────────────────────────────────────────────────────
    private double leftScrollY = 0;

    // ─── Inline editing ───────────────────────────────────────────────────────
    private EditBox inlineBox;
    private String  inlineNodeId = null;
    private String  inlineField  = null;
    private String  inlineVarName = null;
    private boolean inlineActive = false;
    private com.radiologistics.create.block.MainComputerBlockEntity activeComputer = null;

    // ─── File Dialog ──────────────────────────────────────────────────────────
    private boolean showFileDialog = false;
    private boolean fileDialogExport = true;
    private String fileDialogError = "";
    private final List<String> availableSchemes = new ArrayList<>();
    private int selectedSchemeIndex = -1;
    private double schemeScrollOffset = 0;
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;
    private boolean draggingSchemeScrollbar = false;
    private boolean draggingSidebarScrollbar = false;

    // ─── Hotbar ───────────────────────────────────────────────────────────────
    private int activeFreqSlot = 0;
    private boolean initializedPan = false;

    // ─────────────────────────────────────────────────────────────────────────
    public NodeEditorScreen(BlockPos pos, NodeGraph graph, CompoundTag graphNBT) {
        super(Component.literal("Node Editor"));
        this.pos   = pos;
        this.graph = graph;
        if (graphNBT.contains("connectedModules")) {
            var list = graphNBT.getList("connectedModules", 8);
            for (int i = 0; i < list.size(); i++) connectedModules.add(list.getString(i));
        }
        
        // Shift old top-left graphs to the center of the 5120x3200 canvas
        double maxX = 0;
        double maxY = 0;
        for (AlgoNode node : graph.getNodes().values()) {
            if (node.getX() > maxX) maxX = node.getX();
            if (node.getY() > maxY) maxY = node.getY();
        }
        if (!graph.getNodes().isEmpty() && maxX < 2000 && maxY < 1500) {
            for (AlgoNode node : graph.getNodes().values()) {
                node.setPos(node.getX() + 2560, node.getY() + 1600);
            }
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();
        if (nodeOrder.isEmpty()) {
            nodeOrder.addAll(graph.getNodes().keySet());
        }
        if (!initializedPan) {
            int viewportW = this.width - SIDEBAR_W;
            int viewportH = this.height - 28;
            panX = viewportW / 2.0 - 2560.0 * zoom;
            panY = viewportH / 2.0 - 1600.0 * zoom;
            initializedPan = true;
        }
        clampPan();

        // Save / Cancel / Export / Import buttons are drawn manually in Create style

        // Inline EditBox — starts off-screen; repositioned when a property is clicked
        inlineBox = new EditBox(this.font, -600, -600, 110, 12, Component.empty());
        inlineBox.setMaxLength(256);
        inlineBox.visible = false;
        inlineBox.setBordered(false);
        inlineBox.setResponder(this::onInlineChange);
        this.addRenderableWidget(inlineBox);
    }

    // ─── Screen background ────────────────────────────────────────────────────
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // Do nothing to prevent super.render from overdrawing our custom UI
    }

    // ─── Main render ──────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Draw the background manually at the start
        g.fill(0, 0, this.width, this.height, 0xFF0C0C0C);

        // Real-time client-side graph evaluation for viewer nodes
        try {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            net.minecraft.world.level.Level level = player != null ? player.level() : net.minecraft.client.Minecraft.getInstance().level;
            
            // Try to find the computer block entity on the client
            net.minecraft.world.level.block.entity.BlockEntity be = null;
            if (level != null && this.pos != null) {
                be = level.getBlockEntity(this.pos);
                if (be == null) {
                    // Check if it's inside a sublevel using Sable Companion
                    try {
                        Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                        Object companion = companionClass.getField("INSTANCE").get(null);
                        java.lang.reflect.Method getContainingMethod = companionClass.getMethod("getContaining", net.minecraft.world.level.Level.class, net.minecraft.core.Vec3i.class);
                        Object subLevelAccess = getContainingMethod.invoke(companion, level, this.pos);
                        if (subLevelAccess instanceof net.minecraft.world.level.Level subLevel) {
                            be = subLevel.getBlockEntity(this.pos);
                            level = subLevel;
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            com.radiologistics.create.block.MainComputerBlockEntity computer = 
                (be instanceof com.radiologistics.create.block.MainComputerBlockEntity mc) ? mc : null;
            this.activeComputer = computer;
                
            this.graph.evaluate(new com.radiologistics.create.node.EvaluationContext(
                level,
                this.pos,
                computer,
                this.graph
            ));
        } catch (Exception ignored) {}

        // ── Canvas background (right side of sidebar) ─────────────────────────
        int cx0 = SIDEBAR_W, cy0 = 0, cx1 = this.width, cy1 = this.height;
        g.fill(cx0, cy0, cx1, cy1, 0xFF0D141C); // Very dark navy behind canvas viewport
        g.flush();

        // Viewport frustum culling boundaries in canvas-space
        double minCX = -panX / zoom;
        double maxCX = (this.width - SIDEBAR_W - panX) / zoom;
        double minCY = -panY / zoom;
        double maxCY = (this.height - 28 - panY) / zoom;

        // ── Canvas area (under scaled pose stack) ─────────────────────────────
        g.pose().pushPose();
        g.pose().translate(panX + SIDEBAR_W, panY, 0);
        g.pose().scale((float)zoom, (float)zoom, 1.0f);

        // Create-style warm andesite grid background
        g.pose().pushPose();
        g.pose().translate(0, 0, 0.5f);
        g.fill(0, 0, 5120, 3200, 0xFF2A2826); // Warm dark andesite gray
        drawCreateGridAndRivetPatterns(g);
        g.flush();
        g.pose().popPose();

        // Wires (drawn UNDER nodes)
        g.pose().pushPose();
        g.pose().translate(0, 0, 5.0f);
        for (NodeLink link : graph.getLinks()) {
            AlgoNode from = graph.getNodes().get(link.fromNode());
            AlgoNode to   = graph.getNodes().get(link.toNode());
            if (from == null || to == null) continue;
            double[] op = portCanvasPos(from, link.fromPort(), true);
            double[] ip = portCanvasPos(to,   link.toPort(),   false);
            
            // Cull off-screen wires with a 10px buffer
            double wx1 = Math.min(op[0], ip[0]) - 10;
            double wx2 = Math.max(op[0], ip[0]) + 10;
            double wy1 = Math.min(op[1], ip[1]) - 10;
            double wy2 = Math.max(op[1], ip[1]) + 10;
            if (wx2 >= minCX && wx1 <= maxCX && wy2 >= minCY && wy1 <= maxCY) {
                boolean sel = link.equals(selectedLink);
                if (sel) drawBezierCanvas(g, op[0], op[1], ip[0], ip[1], 0xFFFFA500);
                else     drawRsWireCanvas(g, op[0], op[1], ip[0], ip[1]);
            }
        }
        g.flush();
        g.pose().popPose();

        // Nodes (render in Z-order)
        float z = 10.0f;
        for (String id : nodeOrder) {
            AlgoNode node = graph.getNodes().get(id);
            if (node != null) {
                int nh = nodeHeight(node);
                // Cull off-screen nodes
                if (node.getX() + NODE_W >= minCX && node.getX() <= maxCX &&
                    node.getY() + nh >= minCY && node.getY() <= maxCY) {
                    renderNode(g, node, z);
                }
                z += 0.1f;
            }
        }

        // Wire-drag preview (drawn ABOVE nodes)
        if (wireSrcId != null) {
            AlgoNode src = graph.getNodes().get(wireSrcId);
            if (src != null) {
                g.pose().pushPose();
                g.pose().translate(0, 0, z + 2.0f);
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                double[] sp = portCanvasPos(src, wireSrcPort, wireSrcOut);
                if (wireSrcOut) {
                    drawBezierCanvas(g, sp[0], sp[1], wireDragCX, wireDragCY, 0xFFFFA500);
                } else {
                    drawBezierCanvas(g, wireDragCX, wireDragCY, sp[0], sp[1], 0xFFFFA500);
                }
                g.flush();
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                g.pose().popPose();
            }
        }

        // Flush rendering before popping the pose stack
        g.flush();
        g.pose().popPose();

        // ── Left Sidebar ──────────────────────────────────────────────────────
        g.pose().pushPose();
        g.pose().translate(0, 0, 4000.0f);
        g.fill(0, 0, SIDEBAR_W - 4, this.height, 0xFF2E2A28); // Copper/Zinc background
        g.fill(SIDEBAR_W - 3, 0, SIDEBAR_W, this.height, 0xFFC8963E); // vertical Brass border strip
        g.fill(SIDEBAR_W - 4, 0, SIDEBAR_W - 3, this.height, 0xFFE5B869); // light highlight edge
        g.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, this.height, 0xFF84581C); // shadow edge
        renderSidebarCards(g, mx, my);
        g.flush();
        g.pose().popPose();

        // Fixed header overlay — rendered LAST so it's always on top of scrolled cards
        g.pose().pushPose();
        g.pose().translate(0, 0, 4500.0f);
        g.fill(0, 0, SIDEBAR_W - 3, 22, 0xFFC8963E); // Brass plate
        g.fill(0, 0, SIDEBAR_W - 3, 1, 0xFFE5B869); // top highlight
        g.fill(0, 21, SIDEBAR_W - 3, 22, 0xFF84581C); // bottom shadow
        g.drawString(this.font, "§6§lBLOCKS", 8, 7, 0xFFFFEAA0); // Golden bold text
        g.flush();
        g.pose().popPose();

        // ── Bottom strip (covers canvas elements panned to bottom) ────────────
        g.pose().pushPose();
        g.pose().translate(0, 0, 4000.0f);
        int by0 = this.height - 28;
        
        // Draw bottom bar Andesite plate background
        g.fill(SIDEBAR_W, by0, this.width, this.height, 0xFF2A2C2D);
        
        // Horizontal Brass divider pipe/rail running along the top of the strip
        g.fill(SIDEBAR_W, by0, this.width, by0 + 1, 0xFF141312); // outline shadow
        g.fill(SIDEBAR_W, by0 + 1, this.width, by0 + 2, 0xFFE9C583); // brass highlight
        g.fill(SIDEBAR_W, by0 + 2, this.width, by0 + 3, 0xFFC8963E); // brass core
        g.fill(SIDEBAR_W, by0 + 3, this.width, by0 + 4, 0xFF8C5F1C); // brass shadow

        // Bottom-left hint
        if (selectedNode != null) {
            g.drawString(this.font, "§c[X] Delete node", cx0 + 6, this.height - 17, 0xFFFF6666);
        } else if (selectedLink != null) {
            g.drawString(this.font, "§c[X] Delete wire", cx0 + 6, this.height - 17, 0xFFFF6666);
        }

        // Draw custom buttons!
        drawCreateButton(g, "Export", this.width - 250, this.height - 23, 54, 18, mx, my, false);
        drawCreateButton(g, "Import", this.width - 190, this.height - 23, 54, 18, mx, my, false);
        drawCreateButton(g, "Save",   this.width - 130, this.height - 23, 54, 18, mx, my, true); // Brass style
        drawCreateButton(g, "Cancel", this.width - 70,  this.height - 23, 54, 18, mx, my, false);

        g.flush();
        g.pose().popPose();

        // ── Hotbar (for Link nodes) ────────────────────────────────────────────
        if (selectedNode instanceof LinkInputNode || selectedNode instanceof LinkOutputNode) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 4300.0f);
            renderHotbar(g);
            g.flush();
            g.pose().popPose();
        }

        // Widgets (buttons + inline edit box) rendered on top of everything
        g.pose().pushPose();
        g.pose().translate(0, 0, 6000.0f);
        super.render(g, mx, my, pt);
        g.flush();
        g.pose().popPose();

        // Render File Dialog if open
        if (showFileDialog) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 5800.0f);
            int w = 250;
            int h = 200;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            
            // 1. Dialog Casing (Brass plaque)
            drawBeveledPlate(g, x, y, w, h, THEME_BRASS, false);
            
            // Corner rivets on the dialog plate
            drawRivet(g, x + 5, y + 5);
            drawRivet(g, x + w - 7, y + 5);
            drawRivet(g, x + 5, y + h - 7);
            drawRivet(g, x + w - 7, y + h - 7);

            // Title with shadow
            String title = fileDialogExport ? "Export Scheme" : "Import Scheme";
            int twTitle = this.font.width(title);
            g.drawString(this.font, title, x + (w - twTitle) / 2, y + 6, 0xFFFFD700);
            
            // Inset client area for inputs and lists
            int insetX = x + 8;
            int insetY = y + 18;
            int insetW = w - 16;
            int insetH = h - 52;
            drawInsetPanel(g, insetX, insetY, insetW, insetH, THEME_ANDESITE);
            
            if (fileDialogExport) {
                g.drawString(this.font, "Enter name:", insetX + 6, insetY + 6, 0xFFACAFB0);
                
                // Text input slot background
                g.fill(insetX + 6, insetY + 16, insetX + insetW - 6, insetY + 30, 0xFF141312);
                g.fill(insetX + 7, insetY + 17, insetX + insetW - 7, insetY + 29, 0xFF0D0C0B);
            }

            // Schemes List recess
            int lx = insetX + 6;
            int ly = insetY + (fileDialogExport ? 34 : 6);
            int lw = insetW - 12;
            int lh = fileDialogExport ? 90 : 118;
            int maxVisible = fileDialogExport ? 6 : 8;
            
            g.fill(lx, ly, lx + lw, ly + lh, 0xFF141312);
            g.fill(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1, 0xFF080706);
            
            if (availableSchemes.isEmpty()) {
                int tw = this.font.width("No schemes found");
                g.drawString(this.font, "No schemes found", lx + (lw - tw) / 2, ly + (lh - 9) / 2, 0xFF5F5A57);
            } else {
                int startIdx = (int) schemeScrollOffset;
                for (int i = 0; i < maxVisible; i++) {
                    int idx = startIdx + i;
                    if (idx >= availableSchemes.size()) break;
                    
                    String name = availableSchemes.get(idx);
                    int itemY = ly + 2 + i * 14;
                    boolean isSelected = idx == selectedSchemeIndex;
                    boolean isHover = mx >= lx + 2 && mx < lx + lw - 12 && my >= itemY && my < itemY + 14;
                    
                    if (isSelected) {
                        g.fill(lx + 2, itemY, lx + lw - 12, itemY + 14, 0xFF35201B); // deep copper
                        g.fill(lx + 2, itemY, lx + lw - 12, itemY + 1, 0xFFB76D55); // highlight
                        g.fill(lx + 2, itemY + 13, lx + lw - 12, itemY + 14, 0xFF5E2E1F); // shadow
                    } else if (isHover) {
                        g.fill(lx + 2, itemY, lx + lw - 12, itemY + 14, 0xFF1A1A1A); // Hover grey
                    }
                    
                    g.drawString(this.font, trunc(name, 24), lx + 6, itemY + 3, isSelected ? 0xFFFFEAA0 : 0xFFCCCCCC);
                }
                
                // Draw Scrollbar as a mechanical brass track/slider
                if (availableSchemes.size() > maxVisible) {
                    int sbX = lx + lw - 10;
                    int sbY = ly + 2;
                    int sbH = lh - 4;
                    
                    // Track path
                    g.fill(sbX, sbY, sbX + 8, sbY + sbH, 0xFF141312);
                    g.fill(sbX + 3, sbY, sbX + 5, sbY + sbH, 0xFF3A3D3E); // guide rail
                    
                    double viewRatio = (double) maxVisible / availableSchemes.size();
                    int thumbH = (int) (sbH * viewRatio);
                    if (thumbH < 10) thumbH = 10;
                    int thumbY = sbY + (int) ((sbH - thumbH) * (schemeScrollOffset / (availableSchemes.size() - maxVisible)));
                    
                    // Mechanical Brass slider knob
                    g.fill(sbX + 1, thumbY, sbX + 7, thumbY + thumbH, 0xFF141312);
                    g.fill(sbX + 2, thumbY + 1, sbX + 6, thumbY + thumbH - 1, 0xFFC8963E); // brass knob
                    g.fill(sbX + 2, thumbY + 1, sbX + 6, thumbY + 2, 0xFFE9C583); // knob highlight
                    g.fill(sbX + 2, thumbY + thumbH - 2, sbX + 6, thumbY + thumbH - 1, 0xFF8C5F1C); // knob shadow
                }
            }

            // Draw dialog status / instructions at the bottom of the inset
            String desc = fileDialogError.isEmpty() 
                ? (fileDialogExport ? "Enter name & click Export" : "Select a scheme to import")
                : fileDialogError;
            int descColor = fileDialogError.isEmpty() ? 0xFF8C5F1C : 0xFFFF5555;
            g.drawString(this.font, trunc(desc, 36), x + 12, y + h - 30, descColor);

            // Draw Buttons below the inset
            String actLabel = fileDialogExport ? "Export" : "Import";
            drawDialogButton(g, actLabel, x + 10, y + h - 23, 70, 18, mx, my);
            drawDialogButton(g, "Cancel", x + 85, y + h - 23, 65, 18, mx, my);
            drawDialogButton(g, "Folder 📂", x + 155, y + h - 23, 85, 18, mx, my);

            g.flush();
            g.pose().popPose();
        }

        // Render Error Banner if active
        if (System.currentTimeMillis() < errorMessageExpiry) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 7000.0f);
            String displayMsg = "⚠ " + errorMessage;
            int ew = this.font.width(displayMsg) + 30;
            int eh = 22;
            int ex = SIDEBAR_W + (this.width - SIDEBAR_W - ew) / 2;
            int ey = 10;
            
            // Draw hazard warning plaque
            drawBeveledPlate(g, ex, ey, ew, eh, THEME_REDSTONE, false);
            
            // Draw hazard stripes on the left & right borders inside plaque
            g.fill(ex + 4, ey + 4, ex + 8, ey + eh - 4, 0xFFFFA500); // yellow/orange warning stripes
            g.fill(ex + 4, ey + 4, ex + 6, ey + eh - 4, 0xFF141312);
            g.fill(ex + ew - 8, ey + 4, ex + ew - 4, ey + eh - 4, 0xFFFFA500);
            g.fill(ex + ew - 6, ey + 4, ex + ew - 4, ey + eh - 4, 0xFF141312);
            
            // Glowing redstone warning lamp next to the text
            int lampX = ex + 14;
            int lampY = ey + 7;
            g.fill(lampX, lampY, lampX + 8, lampY + 8, 0xFF141312);
            int lampCol = (System.currentTimeMillis() % 500 < 250) ? 0xFFFF3333 : 0xFF881111;
            g.fill(lampX + 1, lampY + 1, lampX + 7, lampY + 7, lampCol);
            g.fill(lampX + 1, lampY + 1, lampX + 3, lampY + 3, 0xFFFFFFFF); // reflection
            
            // Warning text
            g.drawString(this.font, errorMessage, ex + 28, ey + 7, 0xFFFFFFFF);
            
            g.flush();
            g.pose().popPose();
        }
    }

    // ─── Grid ─────────────────────────────────────────────────────────────────
    private void drawTechnicalSchematics(GuiGraphics g) {
        // Technical labels in semi-transparent brass styling
        g.drawString(this.font, "CREATE CO. CONSOLE SCHEMATIC", 30, 30, 0x22C8963E);
        g.drawString(this.font, "MODULE: RADIOLOGISTICS COMPUTER BOARD", 30, 42, 0x15C8963E);
        g.drawString(this.font, "DESIGN SECTION: IV // RL-COMP-V1", 30, 54, 0x15C8963E);
        
        // Technical alignment corner markings
        g.fill(20, 20, 25, 21, 0x22C8963E);
        g.fill(20, 20, 21, 25, 0x22C8963E);
        
        g.fill(5100, 20, 5105, 21, 0x22C8963E);
        g.fill(5104, 20, 5105, 25, 0x22C8963E);
        
        g.fill(20, 3180, 25, 3181, 0x22C8963E);
        g.fill(20, 3176, 21, 3181, 0x22C8963E);
        
        g.fill(5100, 3180, 5105, 3181, 0x22C8963E);
        g.fill(5104, 3176, 5105, 3181, 0x22C8963E);
    }

    // ─── Grid ─────────────────────────────────────────────────────────────────
    private void drawCreateGridAndRivetPatterns(GuiGraphics g) {
        int gs = 20;
        int cx0 = SIDEBAR_W, cy0 = 0, cx1 = this.width, cy1 = this.height - 28;
        double minCX = (cx0 - panX - SIDEBAR_W) / zoom;
        double maxCX = (cx1 - panX - SIDEBAR_W) / zoom;
        double minCY = (cy0 - panY)              / zoom;
        double maxCY = (cy1 - panY)              / zoom;

        int xStart = Math.max(0, (int) Math.floor(minCX / gs) * gs);
        int xEnd = Math.min(5120, (int) Math.ceil(maxCX / gs) * gs);
        int yStart = Math.max(0, (int) Math.floor(minCY / gs) * gs);
        int yEnd = Math.min(3200, (int) Math.ceil(maxCY / gs) * gs);

        // 1. Draw grid lines inside visible area
        for (int cx = xStart; cx <= xEnd; cx += gs) {
            int col = (cx % 100 == 0) ? 0x228C5F1C : 0x12141312; // Brass vs dark iron
            g.fill(cx, yStart, cx + 1, yEnd, col);
        }
        for (int cy = yStart; cy <= yEnd; cy += gs) {
            int col = (cy % 100 == 0) ? 0x228C5F1C : 0x12141312;
            g.fill(xStart, cy, xEnd, cy + 1, col);
        }

        // 2. Draw rivets at 100px intersections inside visible area
        int rxStart = Math.max(100, ((xStart + 99) / 100) * 100);
        int rxEnd = Math.min(5000, (xEnd / 100) * 100);
        int ryStart = Math.max(100, ((yStart + 99) / 100) * 100);
        int ryEnd = Math.min(3100, (yEnd / 100) * 100);

        for (int rx = rxStart; rx <= rxEnd; rx += 100) {
            for (int ry = ryStart; ry <= ryEnd; ry += 100) {
                g.fill(rx - 1, ry - 1, rx + 1, ry + 1, 0xFF141312); // shadow
                g.fill(rx - 1, ry - 1, rx, ry, 0xFF85807D); // highlight
            }
        }

        // Technical schematics overlay
        drawTechnicalSchematics(g);
    }

    // ─── Sidebar ──────────────────────────────────────────────────────────────
    // ─── Sidebar ──────────────────────────────────────────────────────────────
    private int getSidebarContentHeight() {
        int h = 24; // start Y
        h += 22 + CONSTANT_CARDS.length * 30;
        h += 22 + OPERATOR_CARDS.length * 30;
        
        int ioCount = IO_CARDS.length;
        if (connectedModules.contains("redstone_link")) ioCount += 2;
        if (connectedModules.contains("gyroscope")) ioCount += 2;
        if (connectedModules.contains("antenna")) ioCount += 1;
        if (connectedModules.contains("network_controller")) ioCount += 1;
        if (connectedModules.contains("jammer")) ioCount += 1;
        if (connectedModules.contains("audio")) ioCount += 2;
        h += 22 + ioCount * 30;
        
        h += 22 + VIEWER_CARDS.length * 30;
        
        if (connectedModules.contains("memory")) {
            h += 22 + 20 + graph.getVariables().size() * 26;
        }
        return h;
    }

    private void updateSidebarScrollFromMouse(double my, int sbY, int sbH, int totalHeight) {
        double relativeY = my - sbY;
        double viewRatio = (double) sbH / totalHeight;
        int thumbH = (int) (sbH * viewRatio);
        if (thumbH < 15) thumbH = 15;
        double scrollPercent = (relativeY - thumbH / 2.0) / (sbH - thumbH);
        scrollPercent = Math.max(0.0, Math.min(1.0, scrollPercent));
        leftScrollY = -scrollPercent * (totalHeight - sbH);
    }

    private void drawSectionHeader(GuiGraphics g, String title, int y) {
        if (y + 22 < 0 || y > this.height) return;
        int x = 4;
        int w = SIDEBAR_W - 14;
        int h = 14;
        int py = y + 4;
        
        // Outline
        g.fill(x, py, x + w, py + h, 0xFF141312);
        
        // Plate background
        g.fill(x + 1, py + 1, x + w - 1, py + h - 1, 0xFF7A5828); // dark brass
        // Highlights/shadows
        g.fill(x + 1, py + 1, x + w - 1, py + 2, 0xFFA67C3E); // light highlight
        g.fill(x + 1, py + 1, x + 2, py + h - 1, 0xFFA67C3E);
        g.fill(x + w - 2, py + 1, x + w - 1, py + h - 1, 0xFF4A3212); // shadow
        g.fill(x + 1, py + h - 2, x + w - 1, py + h - 1, 0xFF4A3212);
        
        // Tiny rivets on left & right
        g.fill(x + 3, py + 5, x + 5, py + 7, 0xFF141312);
        g.fill(x + 3, py + 5, x + 4, py + 6, 0xFFACAFB0);
        g.fill(x + w - 5, py + 5, x + w - 3, py + 7, 0xFF141312);
        g.fill(x + w - 5, py + 5, x + w - 4, py + 6, 0xFFACAFB0);
        
        // Centered bold warm-yellow text
        int tw = this.font.width(title);
        g.drawString(this.font, title, x + (w - tw) / 2, py + 3, 0xFFFFD700);
    }

    private void renderSidebarCards(GuiGraphics g, double mx, double my) {
        int totalHeight = getSidebarContentHeight();
        int sbH = this.height - 22;
        double maxScroll = Math.min(0, sbH - totalHeight - 10);
        leftScrollY = Math.max(maxScroll, Math.min(0, leftScrollY));

        int y = 24 + (int) leftScrollY;

        // --- CONSTANTS ---
        drawSectionHeader(g, "CONSTANTS", y); y += 22;
        for (String[] card : CONSTANT_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }

        // --- OPERATORS ---
        drawSectionHeader(g, "OPERATORS", y); y += 22;
        for (String[] card : OPERATOR_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }

        // --- I/O NODES ---
        drawSectionHeader(g, "I/O NODES", y); y += 22;
        for (String[] card : IO_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }
        if (connectedModules.contains("redstone_link")) {
            drawNodeCard(g, "Link In",     "link_input",     "↓", y, mx, my); y += 30;
            drawNodeCard(g, "Link Out",    "link_output",    "↑", y, mx, my); y += 30;
        }
        if (connectedModules.contains("gyroscope")) {
            drawNodeCard(g, "Gyroscope",   "gyroscope",      "⊕", y, mx, my); y += 30;
            drawNodeCard(g, "Gyro Position", "gyroscope_position", "⛖", y, mx, my); y += 30;
        }
        if (connectedModules.contains("antenna")) {
            drawNodeCard(g, "Send Signal", "antenna_output", "→", y, mx, my); y += 30;
        }
        if (connectedModules.contains("network_controller")) {
            drawNodeCard(g, "Active Target", "active_target", "⌖", y, mx, my); y += 30;
        }
        if (connectedModules.contains("jammer")) {
            drawNodeCard(g, "Jammer",      "jammer",         "⚡", y, mx, my); y += 30;
        }
        if (connectedModules.contains("audio")) {
            drawNodeCard(g, "Text Speak",  "text_speak",     "🗣", y, mx, my); y += 30;
            drawNodeCard(g, "Sound Play",  "sound_play",     "🎵", y, mx, my); y += 30;
        }

        // --- VIEWERS ---
        drawSectionHeader(g, "VIEWERS", y); y += 22;
        for (String[] card : VIEWER_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }

        // --- VARIABLES ---
        if (connectedModules.contains("memory")) {
            drawSectionHeader(g, "VARS", y); y += 22;

            // +T  +#  +B buttons
            drawSmallBtn(g, 4,  y, "+T", 0xFF3B7A57, mx, my);
            drawSmallBtn(g, 42, y, "+#", 0xFF225588, mx, my);
            drawSmallBtn(g, 80, y, "+B", 0xFF886622, mx, my);
            y += 20;

            for (String vn : new ArrayList<>(graph.getVariables().keySet())) {
                if (y + 24 < 0 || y > this.height) {
                    if (inlineActive && vn.equals(inlineVarName)) {
                        inlineBox.setY(-600);
                    }
                    y += 26;
                    continue;
                }
                String type = graph.getVariableTypes().getOrDefault(vn, "text");
                boolean sel = vn.equals(selectedVar);
                int w = SIDEBAR_W - 14;
                boolean hoverVar = mx >= 4 && mx < 4 + w && my >= y && my < y + 22;
                
                MaterialTheme varTheme = THEME_ROSE;
                int borderCol = sel ? 0xFFFFD700 : (hoverVar ? 0xFFC97C8E : 0xFF141312);
                g.fill(4, y, 4 + w, y + 22, borderCol);
                
                int varBg = sel ? varTheme.headerBg : (hoverVar ? varTheme.bodyBg : 0xFF2A1C1F);
                g.fill(5, y + 1, 4 + w - 1, y + 21, varBg);
                
                // Type icon/badge
                int tc = varTypeColor(type);
                String tl = switch (type) { case "number" -> "#"; case "bool" -> "B"; default -> "T"; };
                g.fill(7, y + 3, 17, y + 19, 0xFF141312);
                g.fill(8, y + 4, 16, y + 18, tc);
                g.drawString(this.font, tl, 10, y + 6, 0xFFFFFFFF);
                
                if (inlineActive && vn.equals(inlineVarName)) {
                    inlineBox.setY(y + 5);
                    if ("var_rename".equals(inlineField)) {
                        String val = String.valueOf(graph.getVariables().getOrDefault(vn, ""));
                        g.drawString(this.font, trunc("=" + val, 6), 52, y + 7, 0xFF888888);
                    } else if ("var_value".equals(inlineField)) {
                        g.drawString(this.font, vn + "=", 22, y + 7, 0xFFCCCCCC);
                    } else {
                        String valStr = String.valueOf(graph.getVariables().getOrDefault(vn, ""));
                        String sn = trunc(vn + "=" + valStr, 8);
                        g.drawString(this.font, sn, 22, y + 7, 0xFFCCCCCC);
                    }
                } else {
                    String valStr = String.valueOf(graph.getVariables().getOrDefault(vn, ""));
                    String sn = trunc(vn + "=" + valStr, 8);
                    g.drawString(this.font, sn, 22, y + 7, 0xFFCCCCCC);
                }
                
                // Spawn Get (G) / Set (S) / Delete (X) buttons
                int gxBtn = 4 + w - 46;
                boolean hoverG = mx >= gxBtn      && mx < gxBtn + 13 && my >= y + 4 && my < y + 18;
                boolean hoverS = mx >= gxBtn + 15 && mx < gxBtn + 28 && my >= y + 4 && my < y + 18;
                boolean hoverX = mx >= gxBtn + 30 && mx < gxBtn + 43 && my >= y + 4 && my < y + 18;
                
                // G button (Brass style)
                int gBg = hoverG ? 0xFFD8A64E : 0xFFC8963E;
                g.fill(gxBtn, y + 4, gxBtn + 13, y + 18, 0xFF141312);
                g.fill(gxBtn + 1, y + 5, gxBtn + 12, y + 17, gBg);
                g.drawString(this.font, "G", gxBtn + 4, y + 6, hoverG ? 0xFFFFFFFF : 0xFFFFEAA0);
                
                // S button (Brass style)
                int sBg = hoverS ? 0xFFD8A64E : 0xFFC8963E;
                g.fill(gxBtn + 15, y + 4, gxBtn + 28, y + 18, 0xFF141312);
                g.fill(gxBtn + 16, y + 5, gxBtn + 27, y + 17, sBg);
                g.drawString(this.font, "S", gxBtn + 19, y + 6, hoverS ? 0xFFFFFFFF : 0xFFFFEAA0);

                // X button (Redstone Red style)
                int xBg = hoverX ? 0xFFC94A4C : 0xFF9E2A2B;
                g.fill(gxBtn + 30, y + 4, gxBtn + 43, y + 18, 0xFF141312);
                g.fill(gxBtn + 31, y + 5, gxBtn + 42, y + 17, xBg);
                g.drawString(this.font, "x", gxBtn + 34, y + 5, hoverX ? 0xFFFFFFFF : 0xFFFFD2D2);
                
                y += 26;
            }
        }

        // Draw mechanical scrollbar if needed
        if (totalHeight > sbH) {
            int sbX = SIDEBAR_W - 8;
            int sbY = 22;
            
            // guide rail track
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, 0xFF141312);
            g.fill(sbX + 1, sbY, sbX + 3, sbY + sbH, 0xFF3E3A36);
            
            double viewRatio = (double) sbH / totalHeight;
            int thumbH = (int) (sbH * viewRatio);
            if (thumbH < 15) thumbH = 15;
            int thumbY = sbY + (int) ((sbH - thumbH) * (-leftScrollY / (totalHeight - sbH)));
            
            boolean hoverThumb = mx >= sbX - 2 && mx <= sbX + 6 && my >= thumbY && my <= thumbY + thumbH;
            int thumbBg = (hoverThumb || draggingSidebarScrollbar) ? 0xFFE9C583 : 0xFFC8963E;
            
            g.fill(sbX - 1, thumbY, sbX + 5, thumbY + thumbH, 0xFF141312);
            g.fill(sbX, thumbY + 1, sbX + 4, thumbY + thumbH - 1, thumbBg);
            g.fill(sbX, thumbY + 1, sbX + 4, thumbY + 2, 0xFFFFFFFF); // highlight dot
        }
    }

    private void drawNodeCard(GuiGraphics g, String label, String type, String sym, int y, double mx, double my) {
        if (y + 28 < 0 || y > this.height) return;
        int x = 4;
        int w = SIDEBAR_W - 14;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 28;
        
        MaterialTheme theme = getThemeForType(type);
        
        // Card outline
        int outlineCol = hover ? 0xFFFFD700 : 0xFF141312; // Gold when hovered
        g.fill(x, y, x + w, y + 28, outlineCol);
        
        // Card body highlight/shadow
        int frameHighlight = hover ? 0xFFFFA500 : theme.highlight;
        int frameShadow = hover ? 0xFFCC6600 : theme.shadow;
        int bg = hover ? theme.headerBg : theme.bodyBg;
        
        g.fill(x + 1, y + 1, x + w - 1, y + 27, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, frameHighlight);
        g.fill(x + 1, y + 2, x + 2, y + 27, frameHighlight);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 27, frameShadow);
        g.fill(x + 1, y + 26, x + w - 1, y + 27, frameShadow);
        
        // Accent stripe on the left edge
        g.fill(x + 3, y + 3, x + 6, y + 25, theme.primary);
        
        int textShift = hover ? 1 : 0;
        int textCol = hover ? 0xFFFFFFFF : 0xFFCCCCCC;
        
        int symW = this.font.width(sym);
        int symX = x + 8 + (24 - symW) / 2 + textShift;
        g.drawString(this.font, "§e" + sym, symX, y + 9, 0xFFFFD700);
        g.drawString(this.font, label, x + 36 + textShift, y + 10, textCol);
        
        // Add tiny corner rivets to the card for that extra industrial detail
        g.fill(x + 2, y + 2, x + 3, y + 3, 0xFF141312);
        g.fill(x + w - 3, y + 2, x + w - 2, y + 3, 0xFF141312);
        g.fill(x + 2, y + 25, x + 3, y + 26, 0xFF141312);
        g.fill(x + w - 3, y + 25, x + w - 2, y + 26, 0xFF141312);
    }

    private void drawSmallBtn(GuiGraphics g, int x, int y, String label, int accent, double mx, double my) {
        boolean hover = mx >= x && mx < x + 34 && my >= y && my < y + 14;
        int border = hover ? 0xFFFFD700 : 0xFF141312;
        int bg = hover ? 0xFF4E4B48 : 0xFF2A2826;
        
        g.fill(x, y, x + 34, y + 14, border);
        g.fill(x + 1, y + 1, x + 33, y + 13, bg);
        // Bevel highlights
        g.fill(x + 1, y + 1, x + 33, y + 2, hover ? 0xFFFFA500 : 0xFF5A5856);
        g.fill(x + 1, y + 1, x + 2, y + 13, hover ? 0xFFFFA500 : 0xFF5A5856);
        
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (34 - tw) / 2, y + 3, accent);
    }

    // ─── Node rendering (canvas-space) ────────────────────────────────────────
    private void renderNode(GuiGraphics g, AlgoNode node, float zLevel) {
        int nw = NODE_W;
        int nh = nodeHeight(node);
        MaterialTheme theme = getThemeForType(node.getType());
        boolean isSelected = selectedNodeIds.contains(node.getId()) || (node == selectedNode);

        g.pose().pushPose();
        g.pose().translate(node.getX(), node.getY(), zLevel);

        // 1. Draw the beveled plate chassis
        drawBeveledPlate(g, 0, 0, nw, nh, theme, isSelected);

        // 2. Draw the header plate
        int headerBg = isSelected ? 0xFF533F25 : theme.headerBg; // golden/brass header when selected
        g.fill(3, 3, nw - 3, HDR_H, headerBg);
        
        // Highlight line at the bottom of the header
        g.fill(3, HDR_H - 1, nw - 3, HDR_H, 0x40000000); // black translucent shadow line
        g.fill(3, HDR_H, nw - 3, HDR_H + 1, theme.highlight); // metallic highlight line below header

        // Header corner rivets
        drawRivet(g, 5, 4);
        drawRivet(g, nw - 7, 4);
        drawRivet(g, 5, HDR_H - 4);
        drawRivet(g, nw - 7, HDR_H - 4);

        // Title centered in header with drop shadow
        String title = nodeTitle(node.getType());
        int tw = this.font.width(title);
        int titleX = (nw - tw) / 2;
        int titleY = 4;
        g.drawString(this.font, title, titleX, titleY, isSelected ? 0xFFFFEAA0 : 0xFFFFFFFF);

        // 3. Draw inset panel for the rest of the node (ports + properties)
        int insetY = HDR_H + 3;
        int insetH = nh - insetY - 4;
        drawInsetPanel(g, 4, insetY, nw - 8, insetH, theme);

        if (node instanceof com.radiologistics.create.node.nodes.CommentNode cn) {
            String fullText = (cn.getId().equals(inlineNodeId) && inlineActive && "comment".equals(inlineField)) 
                ? inlineBox.getValue() + "_" : cn.getComment();
            List<String> lines = splitComment(fullText, nw - 16); // adjusted width to fit inside inset
            int py = insetY + 4;
            for (String line : lines) {
                g.drawString(this.font, line, 8, py, 0xFFCCCCCC);
                py += 10;
            }
        } else {
            List<String> ins  = node.getInputPorts();
            List<String> outs = node.getOutputPorts();
            int maxP  = Math.max(ins.size(), outs.size());

            // Input ports (female sockets / copper)
            for (int i = 0; i < ins.size(); i++) {
                String pn = ins.get(i);
                int py = insetY + i * ROW_H + 2;
                int px = 3;
                boolean conn = isInputConnected(node.getId(), pn);
                int pcol = getPortColor(node, pn, false);
                drawPort(g, px, py + 6, pcol, !conn, false);
                g.drawString(this.font, pn, px + 9, py + 2, 0xFFACAFB0);
            }

            // Output ports (male plugs / brass)
            for (int i = 0; i < outs.size(); i++) {
                String pn = outs.get(i);
                int py = insetY + i * ROW_H + 2;
                int px = nw - 3;
                boolean conn = isOutputConnected(node.getId(), pn);
                int pcol = getPortColor(node, pn, true);
                drawPort(g, px, py + 6, pcol, !conn, true);
                int pw = this.font.width(pn);
                g.drawString(this.font, pn, px - 9 - pw, py + 2, 0xFFACAFB0);
            }

            // Inline properties
            int propSY = insetY + maxP * ROW_H + 2;
            renderNodeProps(g, node, 4, nw - 8, propSY); // coordinates adjusted to be relative to node local space
        }

        g.flush();
        g.pose().popPose();
    }

    private void renderNodeProps(GuiGraphics g, AlgoNode node, int sx, int sw, int py) {
        boolean editing = node.getId().equals(inlineNodeId) && inlineActive;

        if (node instanceof TextNode tn) {
            String val = editing && "text".equals(inlineField) ? inlineBox.getValue() + "_" : tn.getText();
            if (val.length() > 13) val = val.substring(0, 11) + "…";
            drawPropRow(g, sx, sw, py, "\"" + val + "\"", 0xFF88AA88, editing);

        } else if (node instanceof NumberNode nn) {
            String val = editing && "number".equals(inlineField) ? inlineBox.getValue() + "_" : nn.getValueString();
            drawPropRow(g, sx, sw, py, val, 0xFF88AACC, editing);

        } else if (node instanceof BoolNode bn) {
            boolean val = bn.getValue();
            int ph = PROP_H;
            int bx = sx + 4;
            int bw = sw - 8;
            
            // Draw beveled button covering the whole area
            int btnBg = val ? 0xFFC8963E : 0xFF5A5D5E; // Brass for TRUE, Andesite for FALSE
            int btnHighlight = val ? 0xFFE9C583 : 0xFF808284;
            int btnShadow = val ? 0xFF8C5F1C : 0xFF383A3B;
            
            // Outline
            g.fill(bx, py + 2, bx + bw, py + ph - 2, 0xFF141312);
            
            // Bevel faces
            g.fill(bx + 1, py + 3, bx + bw - 1, py + ph - 3, btnBg);
            // Highlight top & left
            g.fill(bx + 1, py + 3, bx + bw - 1, py + 4, btnHighlight);
            g.fill(bx + 1, py + 3, bx + 2, py + ph - 3, btnHighlight);
            // Shadow bottom & right
            g.fill(bx + bw - 2, py + 3, bx + bw - 1, py + ph - 3, btnShadow);
            g.fill(bx + 1, py + ph - 4, bx + bw - 1, py + ph - 3, btnShadow);
            
            String label = val ? "TRUE" : "FALSE";
            int textCol = val ? 0xFF88FF88 : 0xFFFF8888;
            int tw = this.font.width(label);
            g.drawString(this.font, label, bx + (bw - tw) / 2, py + 5, textCol);

        } else if (node instanceof RedstoneOutputNode ron) {
            String side = switch (ron.getSide().toLowerCase()) {
                case "north" -> "▲ NORTH"; case "south" -> "▼ SOUTH";
                case "east"  -> "► EAST";  case "west"  -> "◄ WEST";
                case "up"    -> "↑ UP";    case "down"  -> "↓ DOWN";
                default -> ron.getSide().toUpperCase();
            };
            drawPropRow(g, sx, sw, py, side, 0xFFCC8888, false);

        } else if (node instanceof AntennaInputNode ain) {
            String val = editing && "channel".equals(inlineField) ? inlineBox.getValue() + "_" : ain.getChannel();
            drawPropRow(g, sx, sw, py, "ch: " + val, 0xFF88CCCC, editing);

        } else if (node instanceof SignalNode sn) {
            String l = sn.getLegacyChannel();
            g.drawString(this.font, (l != null && !l.isBlank()) ? "ch: " + l : "ch: (wired)",
                    sx + 8, py + 4, 0xFF666666);

        } else if (node instanceof VariableNode vn) {
            g.drawString(this.font, "get: " + trunc(vn.getVariableName(), 12), sx + 8, py + 4, 0xFF888888);
        } else if (node instanceof SetVariableNode svn) {
            g.drawString(this.font, "set: " + trunc(svn.getVariableName(), 12), sx + 8, py + 4, 0xFF888888);
        } else if (node instanceof LinkInputNode lin) {
            boolean active1 = activeFreqSlot == 0 && node == selectedNode;
            boolean active2 = activeFreqSlot == 1 && node == selectedNode;
            
            int cx = sx + sw / 2;
            g.fill(cx - 28, py, cx + 28, py + 18, 0xFF141312);
            g.fill(cx - 27, py + 1, cx + 27, py + 17, THEME_REDSTONE.insetBg);
            
            // Slot 1 (Frequency 1) - Blue border
            int border1 = active1 ? 0xFFFFD700 : 0xFF2F5597;
            g.fill(cx - 22, py + 1, cx - 4, py + 17, border1);
            g.fill(cx - 21, py + 2, cx - 5, py + 16, 0xFF0D0C0B);
            
            ItemStack stk1 = getItemStackFromId(lin.getFreq1());
            if (!stk1.isEmpty()) {
                g.renderFakeItem(stk1, cx - 21, py + 1);
            } else {
                int tw = this.font.width("—");
                g.drawString(this.font, "—", cx - 21 + (16 - tw) / 2, py + 5, 0xFF888888);
            }
            
            // Slot 2 (Frequency 2) - Red border
            int border2 = active2 ? 0xFFFFD700 : 0xFFC00000;
            g.fill(cx + 4, py + 1, cx + 22, py + 17, border2);
            g.fill(cx + 5, py + 2, cx + 21, py + 16, 0xFF0D0C0B);
            
            ItemStack stk2 = getItemStackFromId(lin.getFreq2());
            if (!stk2.isEmpty()) {
                g.renderFakeItem(stk2, cx + 5, py + 1);
            } else {
                int tw = this.font.width("—");
                g.drawString(this.font, "—", cx + 5 + (16 - tw) / 2, py + 5, 0xFF888888);
            }
            
        } else if (node instanceof LinkOutputNode lon) {
            boolean active1 = activeFreqSlot == 0 && node == selectedNode;
            boolean active2 = activeFreqSlot == 1 && node == selectedNode;
            
            int cx = sx + sw / 2;
            g.fill(cx - 28, py, cx + 28, py + 18, 0xFF141312);
            g.fill(cx - 27, py + 1, cx + 27, py + 17, THEME_REDSTONE.insetBg);
            
            // Slot 1 (Frequency 1) - Blue border
            int border1 = active1 ? 0xFFFFD700 : 0xFF2F5597;
            g.fill(cx - 22, py + 1, cx - 4, py + 17, border1);
            g.fill(cx - 21, py + 2, cx - 5, py + 16, 0xFF0D0C0B);
            
            ItemStack stk1 = getItemStackFromId(lon.getFreq1());
            if (!stk1.isEmpty()) {
                g.renderFakeItem(stk1, cx - 21, py + 1);
            } else {
                int tw = this.font.width("—");
                g.drawString(this.font, "—", cx - 21 + (16 - tw) / 2, py + 5, 0xFF888888);
            }
            
            // Slot 2 (Frequency 2) - Red border
            int border2 = active2 ? 0xFFFFD700 : 0xFFC00000;
            g.fill(cx + 4, py + 1, cx + 22, py + 17, border2);
            g.fill(cx + 5, py + 2, cx + 21, py + 16, 0xFF0D0C0B);
            
            ItemStack stk2 = getItemStackFromId(lon.getFreq2());
            if (!stk2.isEmpty()) {
                g.renderFakeItem(stk2, cx + 5, py + 1);
            } else {
                int tw = this.font.width("—");
                g.drawString(this.font, "—", cx + 5 + (16 - tw) / 2, py + 5, 0xFF888888);
            }
        } else if (node instanceof GyroscopeNode) {
            g.drawString(this.font, "pitch / yaw", sx + 8, py + 4, 0xFF888888);
        } else if (node instanceof GyroscopePositionNode) {
            g.drawString(this.font, "x / y / z pos", sx + 8, py + 4, 0xFF888888);
        } else if (node instanceof AntennaOutputNode) {
            g.drawString(this.font, "ch + msg  →  wired", sx + 8, py + 4, 0xFF666666);
        } else if (node instanceof BoolViewerNode bvn) {
            int ph = PROP_H;
            g.fill(sx + 4, py + 2, sx + sw - 4, py + ph - 2, 0xFF141312);
            
            if (!bvn.hasValue()) {
                g.fill(sx + 5, py + 3, sx + sw - 5, py + ph - 3, 0xFF222222);
                int lw = this.font.width("OFFLINE");
                g.drawString(this.font, "OFFLINE", sx + (sw - lw) / 2, py + 5, 0xFF5F5A57);
            } else {
                boolean val = bvn.getLastValue();
                int bg = val ? 0xFF1E3A1E : 0xFF3A1E1E;
                int fg = val ? 0xFF88FF88 : 0xFFFF8888;
                g.fill(sx + 5, py + 3, sx + sw - 5, py + ph - 3, bg);
                
                String lbl = val ? "● ACTIVE" : "○ INACTIVE";
                int lw = this.font.width(lbl);
                g.drawString(this.font, lbl, sx + (sw - lw) / 2, py + 5, fg);
            }
        } else if (node instanceof NumberViewerNode nvn) {
            int ph = PROP_H;
            g.fill(sx + 4, py + 2, sx + sw - 4, py + ph - 2, 0xFF141312);
            g.fill(sx + 5, py + 3, sx + sw - 5, py + ph - 3, 0xFF110F0E);
            
            if (!nvn.hasValue()) {
                int lw = this.font.width("—");
                g.drawString(this.font, "—", sx + (sw - lw) / 2, py + 5, 0xFF5F5A57);
            } else {
                double val = nvn.getLastValue();
                String lbl;
                if (val == (long) val) {
                    lbl = String.format(Locale.ROOT, "%d", (long) val);
                } else {
                    lbl = String.format(Locale.ROOT, "%.4f", val);
                    if (lbl.indexOf('.') > 0) {
                        lbl = lbl.replaceAll("0*$", "").replaceAll("\\.$", "");
                    }
                }
                if (lbl.length() > 14) lbl = lbl.substring(0, 12) + "…";
                
                int lw = this.font.width(lbl);
                g.drawString(this.font, lbl, sx + (sw - lw) / 2, py + 5, 0xFFFF8400); // Amber nixie tube text
            }
        } else if (node instanceof TextViewerNode tvn) {
            int ph = PROP_H;
            g.fill(sx + 4, py + 2, sx + sw - 4, py + ph - 2, 0xFF141312);
            g.fill(sx + 5, py + 3, sx + sw - 5, py + ph - 3, 0xFF0D0C0B);
            
            if (!tvn.hasValue()) {
                int lw = this.font.width("—");
                g.drawString(this.font, "—", sx + (sw - lw) / 2, py + 5, 0xFF5F5A57);
            } else {
                String lbl = tvn.getLastValue();
                if (lbl.length() > 14) lbl = lbl.substring(0, 12) + "…";
                int lw = this.font.width(lbl);
                g.drawString(this.font, lbl, sx + (sw - lw) / 2, py + 5, 0xFF88AA88);
            }
        }
    }

    /**
     * Draws a clickable/editable property row.
     */
    private void drawPropRow(GuiGraphics g, int sx, int sw, int py,
                              String label, int textColor, boolean isActive) {
        int ph = PROP_H;
        int borderCol = isActive ? 0xFFFFA500 : 0xFF141312;
        g.fill(sx + 4, py + 2, sx + sw - 4, py + ph - 2, borderCol);
        
        int bg = isActive ? 0xFF2A2826 : 0xFF0D0C0B;
        g.fill(sx + 5, py + 3, sx + sw - 5, py + ph - 3, bg);
        
        if (!isActive) {
            g.drawString(this.font, "⚙", sx + sw - 14, py + 4, 0xFF6E655E);
        }
        g.drawString(this.font, label, sx + 8, py + 5, textColor);
    }

    // ─── Hotbar ───────────────────────────────────────────────────────────────
    private void renderHotbar(GuiGraphics g) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int hx = SIDEBAR_W + (this.width - SIDEBAR_W - 180) / 2;
        int hy = this.height - 54;
        g.fill(hx - 4, hy - 18, hx + 184, hy + 22, 0xDD1F1F1F);
        g.fill(hx - 4, hy - 18, hx + 184, hy - 17, 0xFF3C3C3C);
        g.drawString(this.font,
                "Click slot → freq " + (activeFreqSlot == 0 ? "1" : "2"),
                hx, hy - 14, 0xFF888888);
        for (int i = 0; i < 9; i++) {
            int sx = hx + i * 20;
            boolean selSlot = (i == activeFreqSlot);
            g.fill(sx, hy, sx + 18, hy + 18, selSlot ? 0xFF2A3A2A : 0xFF1E1E1E);
            g.fill(sx, hy, sx + 18, hy + 1,  selSlot ? 0xFF4CAF50 : 0xFF333333);
            ItemStack stk = player.getInventory().getItem(i);
            if (!stk.isEmpty()) {
                g.renderFakeItem(stk, sx + 1, hy + 1);
                g.renderItemDecorations(this.font, stk, sx + 1, hy + 1);
            } else {
                g.drawString(this.font, "−", sx + 6, hy + 5, 0xFF555555);
            }
        }
    }

    // ─── Inline editing ───────────────────────────────────────────────────────
    private void openInlineEdit(AlgoNode node, String field, String value) {
        if (!"export".equals(field) && !"import".equals(field)) {
            pushUndoState();
        }
        inlineNodeId = node != null ? node.getId() : null;
        inlineField  = field;
        inlineActive = true;
        positionInlineBox(node);
        inlineBox.setValue(value);
        inlineBox.visible = true;
        inlineBox.setHighlightPos(0);
        // Route keyboard input to the EditBox through Screen's focus system
        this.setFocused(inlineBox);
        inlineBox.setFocused(true);
    }

    private void positionInlineBox(AlgoNode node) {
        if (showFileDialog && fileDialogExport) {
            int x = (this.width - 250) / 2;
            int y = (this.height - 200) / 2;
            inlineBox.setX(x + 16);
            inlineBox.setY(y + 36);
            inlineBox.setWidth(218);
        } else {
            inlineBox.setX(-600);
            inlineBox.setY(-600);
            inlineBox.setWidth(10);
        }
    }

    private void closeInlineEdit() {
        if (inlineActive && "var_rename".equals(inlineField) && inlineVarName != null) {
            String newName = inlineBox.getValue().trim();
            if (!newName.isEmpty() && !newName.equals(inlineVarName)) {
                if (!graph.getVariables().containsKey(newName)) {
                    pushUndoState();
                    Object val = graph.getVariables().remove(inlineVarName);
                    graph.getVariables().put(newName, val != null ? val : "");
                    String type = graph.getVariableTypes().remove(inlineVarName);
                    graph.getVariableTypes().put(newName, type != null ? type : "text");
                    for (AlgoNode n : graph.getNodes().values()) {
                        if (n instanceof VariableNode vn && vn.getVariableName().equals(inlineVarName)) {
                            vn.setVariableName(newName);
                        }
                        if (n instanceof SetVariableNode sv && sv.getVariableName().equals(inlineVarName)) {
                            sv.setVariableName(newName);
                        }
                    }
                    if (inlineVarName.equals(selectedVar)) {
                        selectedVar = newName;
                    }
                }
            }
        }
        inlineActive = false;
        inlineNodeId = null;
        inlineField  = null;
        inlineVarName = null;
        inlineBox.visible = false;
        inlineBox.setFocused(false);
        inlineBox.setX(-600);
        inlineBox.setY(-600);
        inlineBox.setWidth(10);
        this.setFocused(null);
    }

    private void onInlineChange(String text) {
        if (!inlineActive) return;
        if (showFileDialog) return;
        if ("var_rename".equals(inlineField)) return;
        if (inlineVarName != null) {
            graph.getVariables().put(inlineVarName, text);
            return;
        }
        if (inlineNodeId == null) return;
        AlgoNode node = graph.getNodes().get(inlineNodeId);
        if (node == null) return;
        switch (inlineField) {
            case "text"    -> { if (node instanceof TextNode tn)          tn.setText(text); }
            case "number"  -> { if (node instanceof NumberNode nn) {
                try { nn.setValue(Double.parseDouble(text.replace(",", "."))); }
                catch (NumberFormatException ignored) {}
            }}
            case "channel" -> { if (node instanceof AntennaInputNode ain) ain.setChannel(text); }
            case "comment" -> { if (node instanceof com.radiologistics.create.node.nodes.CommentNode cn) cn.setComment(text); }
        }
    }

    // ─── Mouse ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (showFileDialog) {
            int w = 250;
            int h = 200;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            boolean inDialog = mx >= x && mx <= x + w && my >= y && my <= y + h;
            if (!inDialog) {
                closeFileDialog();
                return true;
            }
            
            // 1. Clicks on the list
            int lx = x + 10;
            int ly = y + (fileDialogExport ? 55 : 22);
            int lw = 230;
            int lh = fileDialogExport ? 95 : 128;
            
            if (mx >= lx + 2 && mx < lx + lw - 12 && my >= ly + 2 && my < ly + lh - 2) {
                int clickedRow = (int) ((my - ly - 2) / 14);
                int startIdx = (int) schemeScrollOffset;
                int idx = startIdx + clickedRow;
                if (idx >= 0 && idx < availableSchemes.size()) {
                    selectedSchemeIndex = idx;
                    String name = availableSchemes.get(idx);
                    inlineBox.setValue(name);
                    
                    // Double click check
                    long now = System.currentTimeMillis();
                    if (idx == lastClickedIndex && now - lastClickTime < 300) {
                        handleFileDialogSubmit();
                    } else {
                        lastClickTime = now;
                        lastClickedIndex = idx;
                    }
                    return true;
                }
            }
            
            // 1b. Scrollbar click
            int maxVisible = fileDialogExport ? 6 : 9;
            if (availableSchemes.size() > maxVisible) {
                int sbX = lx + lw - 10;
                int sbY = ly + 2;
                int sbH = lh - 4;
                if (mx >= sbX && mx <= sbX + 8 && my >= sbY && my <= sbY + sbH) {
                    draggingSchemeScrollbar = true;
                    updateSchemeScrollFromMouse(my, sbY, sbH, maxVisible);
                    return true;
                }
            }
            
            // 2. Click on the text box (for Export)
            if (fileDialogExport) {
                int bx = inlineBox.getX(), by = inlineBox.getY();
                boolean inBox = mx >= bx && mx <= bx + inlineBox.getWidth()
                             && my >= by && my <= by + 14;
                if (inBox) {
                    inlineBox.setFocused(true);
                    this.setFocused(inlineBox);
                    return true;
                }
            }
            
            // 3. Clicks on the buttons
            // Button 1: Action (Import/Export)
            int b1x = x + 10, b1y = y + 172, b1w = 70, b1h = 18;
            if (mx >= b1x && mx < b1x + b1w && my >= b1y && my < b1y + b1h) {
                handleFileDialogSubmit();
                return true;
            }
            
            // Button 2: Cancel
            int b2x = x + 90, b2y = y + 172, b2w = 60, b2h = 18;
            if (mx >= b2x && mx < b2x + b2w && my >= b2y && my < b2y + b2h) {
                closeFileDialog();
                return true;
            }
            
            // Button 3: Open Folder
            int b3x = x + 160, b3y = y + 172, b3w = 80, b3h = 18;
            if (mx >= b3x && mx < b3x + b3w && my >= b3y && my < b3y + b3h) {
                java.io.File dir = new java.io.File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "radiologistics_schemes");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                net.minecraft.Util.getPlatform().openFile(dir);
                return true;
            }
            
            return true;
        }

        // If inline box is active and click lands outside it → close it
        if (inlineActive) {
            int bx = inlineBox.getX(), by = inlineBox.getY();
            boolean inBox = mx >= bx && mx <= bx + inlineBox.getWidth()
                         && my >= by && my <= by + 14;
            if (!inBox) closeInlineEdit();
        }

        // Clicks on bottom bar buttons
        int by0 = this.height - 28;
        if (my >= by0 && mx >= SIDEBAR_W) {
            if (mx >= this.width - 250 && mx < this.width - 250 + 54 && my >= this.height - 23 && my < this.height - 23 + 18) {
                startExport();
                playClickSound();
                return true;
            }
            if (mx >= this.width - 190 && mx < this.width - 190 + 54 && my >= this.height - 23 && my < this.height - 23 + 18) {
                startImport();
                playClickSound();
                return true;
            }
            if (mx >= this.width - 130 && mx < this.width - 130 + 54 && my >= this.height - 23 && my < this.height - 23 + 18) {
                saveAndClose();
                playClickSound();
                return true;
            }
            if (mx >= this.width - 70 && mx < this.width - 70 + 54 && my >= this.height - 23 && my < this.height - 23 + 18) {
                this.onClose();
                playClickSound();
                return true;
            }
        }

        // Left sidebar
        if (mx < SIDEBAR_W) {
            int totalHeight = getSidebarContentHeight();
            int sbH = this.height - 22;
            if (totalHeight > sbH && mx >= SIDEBAR_W - 10 && mx <= SIDEBAR_W - 3 && my >= 22) {
                draggingSidebarScrollbar = true;
                updateSidebarScrollFromMouse(my, 22, sbH, totalHeight);
                return true;
            }
            handleSidebarClick(mx, my);
            return true;
        }

        // Hotbar
        if ((selectedNode instanceof LinkInputNode || selectedNode instanceof LinkOutputNode)
                && handleHotbarClick(mx, my)) {
            return true;
        }

        // Canvas — convert to canvas-space
        double cmx = toCanvasX(mx);
        double cmy = toCanvasY(my);

        // 1. Port hit-test
        for (AlgoNode node : graph.getNodes().values()) {
            for (String pn : node.getInputPorts()) {
                double[] pp = portCanvasPos(node, pn, false);
                if (dist2(cmx, cmy, pp[0], pp[1]) < 36) {
                    wireSrcId = node.getId(); wireSrcPort = pn;
                    wireSrcOut = false; wireDragCX = cmx; wireDragCY = cmy;
                    return true;
                }
            }
            for (String pn : node.getOutputPorts()) {
                double[] pp = portCanvasPos(node, pn, true);
                if (dist2(cmx, cmy, pp[0], pp[1]) < 36) {
                    wireSrcId = node.getId(); wireSrcPort = pn;
                    wireSrcOut = true; wireDragCX = cmx; wireDragCY = cmy;
                    return true;
                }
            }
        }

        // 2. Node body hit-test
        for (int i = nodeOrder.size() - 1; i >= 0; i--) {
            String id = nodeOrder.get(i);
            AlgoNode node = graph.getNodes().get(id);
            if (node == null) continue;
            double nx = node.getX(), ny = node.getY();
            double totalH = nodeHeight(node);
            if (cmx >= nx && cmx <= nx + NODE_W && cmy >= ny && cmy <= ny + totalH) {
                // Move this node to the end of the order list so it is drawn last (on top)
                nodeOrder.remove(id);
                nodeOrder.add(id);

                // Property row hit or Comment body hit?
                if (node instanceof com.radiologistics.create.node.nodes.CommentNode cn) {
                    if (cmy >= ny + HDR_H) {
                        openInlineEdit(cn, "comment", cn.getComment());
                    }
                } else {
                    int maxP = Math.max(node.getInputPorts().size(), node.getOutputPorts().size());
                    double propCY = ny + HDR_H + maxP * ROW_H + 3;
                    if (cmy >= propCY && cmy <= propCY + propRowCount(node) * PROP_H) {
                        handleNodePropClick(node, cmx - nx);
                    }
                }
                selectedNode = node; selectedLink = null; selectedVar = null;
                if (!selectedNodeIds.contains(node.getId())) {
                    selectedNodeIds.clear();
                    selectedNodeIds.add(node.getId());
                }
                dragging = node; dragOffX = cmx - nx; dragOffY = cmy - ny;
                dragStartState = graph.toNBT(); // Save state before drag starts
                return true;
            }
        }

        // 3. Wire hit-test
        NodeLink hit = hitTestWire(cmx, cmy);
        if (hit != null) {
            selectedLink = hit; selectedNode = null; selectedVar = null;
            selectedNodeIds.clear();
            closeInlineEdit();
            return true;
        }

        // 4. Canvas pan / deselect
        selectedNode = null; selectedLink = null; selectedVar = null;
        selectedNodeIds.clear();
        closeInlineEdit();
        if (button == 0) {
            isPanning = true; panStartX = mx - panX; panStartY = my - panY;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void handleNodePropClick(AlgoNode node, double relX) {
        if (node instanceof BoolNode bn) {
            pushUndoState();
            bn.toggle();
        } else if (node instanceof RedstoneOutputNode ron) {
            pushUndoState();
            List<String> sides = List.of("north", "south", "east", "west", "up", "down");
            int idx = sides.indexOf(ron.getSide().toLowerCase());
            ron.setSide(sides.get((idx + 1) % sides.size()));
        } else if (node instanceof TextNode tn) {
            openInlineEdit(node, "text", tn.getText());
        } else if (node instanceof NumberNode nn) {
            openInlineEdit(node, "number", nn.getValueString());
        } else if (node instanceof AntennaInputNode ain) {
            openInlineEdit(node, "channel", ain.getChannel());
        } else if (node instanceof LinkInputNode || node instanceof LinkOutputNode) {
            if (relX < NODE_W / 2.0) {
                activeFreqSlot = 0;
            } else {
                activeFreqSlot = 1;
            }
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void handleSidebarClick(double mx, double my) {
        int y = 24 + (int) leftScrollY;

        // --- CONSTANTS ---
        y += 22; // Skip header
        for (String[] card : CONSTANT_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }

        // --- OPERATORS ---
        y += 22; // Skip header
        for (String[] card : OPERATOR_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }

        // --- I/O NODES ---
        y += 22; // Skip header
        for (String[] card : IO_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }
        if (connectedModules.contains("redstone_link")) {
            if (my >= y && my < y + 28) { addNewNode("link_input");  return; } y += 30;
            if (my >= y && my < y + 28) { addNewNode("link_output"); return; } y += 30;
        }
        if (connectedModules.contains("gyroscope")) {
            if (my >= y && my < y + 28) { addNewNode("gyroscope");   return; } y += 30;
            if (my >= y && my < y + 28) { addNewNode("gyroscope_position"); return; } y += 30;
        }
        if (connectedModules.contains("antenna")) {
            if (my >= y && my < y + 28) { addNewNode("antenna_output"); return; } y += 30;
        }
        if (connectedModules.contains("network_controller")) {
            if (my >= y && my < y + 28) { addNewNode("active_target"); return; } y += 30;
        }
        if (connectedModules.contains("jammer")) {
            if (my >= y && my < y + 28) { addNewNode("jammer"); return; } y += 30;
        }
        if (connectedModules.contains("audio")) {
            if (my >= y && my < y + 28) { addNewNode("text_speak"); return; } y += 30;
            if (my >= y && my < y + 28) { addNewNode("sound_play"); return; } y += 30;
        }

        // --- VIEWERS ---
        y += 22; // Skip header
        for (String[] card : VIEWER_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }

        // --- VARIABLES ---
        if (connectedModules.contains("memory")) {
            y += 22; // Skip header
            // +T / +# / +B
            if (my >= y && my < y + 14) {
                if (mx >= 4  && mx < 38)  { addVariable("text");   return; }
                if (mx >= 42 && mx < 76)  { addVariable("number"); return; }
                if (mx >= 80 && mx < 114) { addVariable("bool");   return; }
            }
            y += 20;
            for (String vn : new ArrayList<>(graph.getVariables().keySet())) {
                if (my >= y && my < y + 22) {
                    int w = SIDEBAR_W - 14;
                    int gxBtn = 4 + w - 46;
                    if (mx >= gxBtn      && mx < gxBtn + 13) { spawnGetVar(vn); return; }
                    if (mx >= gxBtn + 15 && mx < gxBtn + 28) { spawnSetVar(vn); return; }
                    if (mx >= gxBtn + 30 && mx < gxBtn + 43) {
                        deleteVar(vn);
                        Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return;
                    }
                    if (mx >= 5          && mx < 19)         { cycleVarType(vn); return; }
                    if (mx >= 19         && mx < 52)         { startVarRename(vn, y); return; }
                    if (mx >= 52         && mx < gxBtn)      { handleVarClick(vn, y); return; }
                    selectedVar = vn; selectedNode = null;
                    return;
                }
                y += 26;
            }
        }
    }

    private void handleVarClick(String varName, int cardY) {
        String type = graph.getVariableTypes().getOrDefault(varName, "text");
        if (type.equals("bool")) {
            pushUndoState();
            String val = String.valueOf(graph.getVariables().getOrDefault(varName, "false"));
            boolean nextVal = !Boolean.parseBoolean(val);
            graph.getVariables().put(varName, String.valueOf(nextVal));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } else {
            pushUndoState();
            inlineVarName = varName;
            inlineNodeId = null;
            inlineField = "var_value";
            inlineActive = true;
            String val = String.valueOf(graph.getVariables().getOrDefault(varName, ""));
            inlineBox.setValue(val);
            inlineBox.visible = true;
            inlineBox.setHighlightPos(0);
            this.setFocused(inlineBox);
            inlineBox.setFocused(true);
            inlineBox.setX(52);
            inlineBox.setY(cardY + 5);
            inlineBox.setWidth(20);
        }
    }

    private void startVarRename(String varName, int cardY) {
        pushUndoState();
        inlineVarName = varName;
        inlineNodeId = null;
        inlineField = "var_rename";
        inlineActive = true;
        inlineBox.setValue(varName);
        inlineBox.visible = true;
        inlineBox.setHighlightPos(0);
        this.setFocused(inlineBox);
        inlineBox.setFocused(true);
        inlineBox.setX(22);
        inlineBox.setY(cardY + 5);
        inlineBox.setWidth(50);
    }

    private boolean handleHotbarClick(double mx, double my) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        int hx = SIDEBAR_W + (this.width - SIDEBAR_W - 180) / 2;
        int hy = this.height - 54;
        if (my >= hy && my <= hy + 18) {
            for (int i = 0; i < 9; i++) {
                int sx = hx + i * 20;
                if (mx >= sx && mx <= sx + 18) {
                    ItemStack stk = player.getInventory().getItem(i);
                    String id = stk.isEmpty() ? "minecraft:air"
                                              : BuiltInRegistries.ITEM.getKey(stk.getItem()).toString();
                    pushUndoState();
                    if (selectedNode instanceof LinkInputNode lin) {
                        if (activeFreqSlot == 0) lin.setFreq1(id); else lin.setFreq2(id);
                    } else if (selectedNode instanceof LinkOutputNode lon) {
                        if (activeFreqSlot == 0) lon.setFreq1(id); else lon.setFreq2(id);
                    }
                    activeFreqSlot = (activeFreqSlot + 1) % 2;
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSidebarScrollbar) {
            int totalHeight = getSidebarContentHeight();
            int sbH = this.height - 22;
            updateSidebarScrollFromMouse(my, 22, sbH, totalHeight);
            return true;
        }
        if (showFileDialog && draggingSchemeScrollbar) {
            int w = 250;
            int h = 200;
            int x = (this.width - w) / 2;
            int y = (this.height - h) / 2;
            int lx = x + 10;
            int ly = y + (fileDialogExport ? 55 : 22);
            int lw = 230;
            int lh = fileDialogExport ? 95 : 128;
            int maxVisible = fileDialogExport ? 6 : 9;
            int sbY = ly + 2;
            int sbH = lh - 4;
            updateSchemeScrollFromMouse(my, sbY, sbH, maxVisible);
            return true;
        }
        if (isPanning) {
            panX = mx - panStartX;
            panY = my - panStartY;
            clampPan();
            if (inlineActive && inlineNodeId != null) {
                AlgoNode n = graph.getNodes().get(inlineNodeId);
                if (n != null) positionInlineBox(n);
            }
            return true;
        }
        if (dragging != null) {
            double cmx = toCanvasX(mx), cmy = toCanvasY(my);
            double targetX = cmx - dragOffX;
            double targetY = cmy - dragOffY;
            int nh = nodeHeight(dragging);
            double clampedX = Math.max(0.0, Math.min(5120.0 - NODE_W, targetX));
            double clampedY = Math.max(0.0, Math.min(3200.0 - nh, targetY));
            
            double shiftX = clampedX - dragging.getX();
            double shiftY = clampedY - dragging.getY();
            
            if (selectedNodeIds.contains(dragging.getId())) {
                for (String id : selectedNodeIds) {
                    AlgoNode n = graph.getNodes().get(id);
                    if (n != null) {
                        double nx = n.getX() + shiftX;
                        double ny = n.getY() + shiftY;
                        int nHeight = nodeHeight(n);
                        nx = Math.max(0.0, Math.min(5120.0 - NODE_W, nx));
                        ny = Math.max(0.0, Math.min(3200.0 - nHeight, ny));
                        n.setPos(nx, ny);
                        if (inlineActive && inlineNodeId != null && inlineNodeId.equals(id)) {
                            positionInlineBox(n);
                        }
                    }
                }
            } else {
                dragging.setPos(clampedX, clampedY);
                if (inlineActive && inlineNodeId != null && inlineNodeId.equals(dragging.getId())) {
                    positionInlineBox(dragging);
                }
            }
            return true;
        }
        if (wireSrcId != null) {
            wireDragCX = toCanvasX(mx);
            wireDragCY = toCanvasY(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSidebarScrollbar) {
            draggingSidebarScrollbar = false;
            return true;
        }
        if (showFileDialog && draggingSchemeScrollbar) {
            draggingSchemeScrollbar = false;
            return true;
        }
        if (isPanning) { isPanning = false; return true; }
        if (dragging  != null) {
            CompoundTag current = graph.toNBT();
            if (dragStartState != null && !dragStartState.equals(current)) {
                pushUndoStateDirect(dragStartState);
            }
            dragStartState = null;
            dragging = null;
            return true;
        }
        if (wireSrcId != null) {
            double cmx = toCanvasX(mx), cmy = toCanvasY(my);
            tryConnectWire(cmx, cmy);
            wireSrcId = null; wireSrcPort = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void tryConnectWire(double cmx, double cmy) {
        for (AlgoNode node : graph.getNodes().values()) {
            if (node.getId().equals(wireSrcId)) continue;
            if (wireSrcOut) {
                for (String pn : node.getInputPorts()) {
                    double[] pp = portCanvasPos(node, pn, false);
                    if (dist2(cmx, cmy, pp[0], pp[1]) < 36) {
                        pushUndoState();
                        graph.addLink(wireSrcId, wireSrcPort, node.getId(), pn); return;
                    }
                }
            } else {
                for (String pn : node.getOutputPorts()) {
                    double[] pp = portCanvasPos(node, pn, true);
                    if (dist2(cmx, cmy, pp[0], pp[1]) < 36) {
                        pushUndoState();
                        graph.addLink(node.getId(), pn, wireSrcId, wireSrcPort); return;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (showFileDialog) {
            int maxVisible = fileDialogExport ? 6 : 9;
            schemeScrollOffset = Math.max(0, Math.min(availableSchemes.size() - maxVisible, schemeScrollOffset - sy));
            return true;
        }
        if (mx < SIDEBAR_W) {
            int totalHeight = getSidebarContentHeight();
            int sbH = this.height - 22;
            double maxScroll = Math.min(0, sbH - totalHeight - 10);
            leftScrollY = Math.max(maxScroll, Math.min(0, leftScrollY + sy * 15));
            return true;
        }
        double minZoom = Math.min((double)(this.width - SIDEBAR_W) / 5120.0, (double)(this.height - 28) / 3200.0);
        double pz = zoom;
        zoom = Math.max(minZoom, Math.min(2.5, zoom + sy * 0.1));
        // Zoom centred on cursor
        double cmx = (mx - SIDEBAR_W - panX) / pz;
        double cmy = (my - panY) / pz;
        panX = mx - SIDEBAR_W - cmx * zoom;
        panY = my           - cmy * zoom;
        clampPan();
        if (inlineActive && inlineNodeId != null) {
            AlgoNode n = graph.getNodes().get(inlineNodeId);
            if (n != null) positionInlineBox(n);
        }
        return true;
    }

    // ─── Keys ─────────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showFileDialog) {
            if (keyCode == 257 || keyCode == 335) {
                handleFileDialogSubmit();
                return true;
            }
            if (keyCode == 256) {
                closeFileDialog();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Route all keys to inline box when it's active
        if (inlineActive && inlineBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { closeInlineEdit(); return true; } // Enter
            if (keyCode == 256) { closeInlineEdit(); return true; }                    // Esc
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Ctrl + Z — undo
        if (keyCode == 90 && hasControlDown()) {
            performUndo();
            return true;
        }

        // A — select all nodes
        if (keyCode == 65) {
            selectedNodeIds.clear();
            selectedNodeIds.addAll(graph.getNodes().keySet());
            if (!selectedNodeIds.isEmpty()) {
                selectedNode = graph.getNodes().get(nodeOrder.get(0));
            } else {
                selectedNode = null;
            }
            selectedLink = null;
            selectedVar = null;
            return true;
        }

        // X — delete selection
        if (keyCode == 88) {
            deleteSelection(); return true;
        }
        // Delete / Backspace when not in text field
        if (keyCode == 261) {
            deleteSelection(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void deleteSelection() {
        if (selectedNodeIds.isEmpty() && selectedNode == null && selectedLink == null && selectedVar == null) {
            return;
        }
        pushUndoState();
        if (!selectedNodeIds.isEmpty()) {
            for (String id : new ArrayList<>(selectedNodeIds)) {
                graph.removeNode(id);
                nodeOrder.remove(id);
            }
            selectedNodeIds.clear();
            selectedNode = null;
            closeInlineEdit();
        } else if (selectedNode != null) {
            String id = selectedNode.getId();
            graph.removeNode(id);
            nodeOrder.remove(id);
            selectedNode = null;
            closeInlineEdit();
        } else if (selectedLink != null) {
            graph.removeLink(selectedLink.fromNode(), selectedLink.fromPort(),
                    selectedLink.toNode(), selectedLink.toPort());
            selectedLink = null;
        } else if (selectedVar != null) {
            deleteVar(selectedVar);
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────
    private void addNewNode(String type) {
        if (type.equals("jammer")) {
            long jammerCount = graph.getNodes().values().stream()
                .filter(n -> n.getType().equals("jammer"))
                .count();
            if (jammerCount >= 1) {
                showErrorMessage("Можна встановити лише один Jammer!");
                return;
            }
        }
        pushUndoState();
        String id = type + "_" + System.currentTimeMillis();
        double cx = ((this.width - SIDEBAR_W) / 2.0 - panX) / zoom - NODE_W / 2.0;
        double cy = (this.height / 2.0           - panY) / zoom - 30;
        
        // Clamp to background bounds
        cx = Math.max(10.0, Math.min(5120.0 - NODE_W - 10, cx));
        cy = Math.max(10.0, Math.min(3200.0 - 80.0, cy));
        
        AlgoNode node = AlgoNode.createNode(type, id, cx, cy);
        if (node != null) {
            graph.addNode(node);
            nodeOrder.add(id);
            selectedNode = node;
            selectedLink = null;
        }
    }

    private void addVariable(String type) {
        pushUndoState();
        String base = "var"; int i = 1;
        while (graph.getVariables().containsKey(base + i)) i++;
        String name = base + i;
        graph.getVariables().put(name, switch (type) { case "bool" -> "false"; case "number" -> "0"; default -> ""; });
        graph.getVariableTypes().put(name, type);
        selectedVar = name; selectedNode = null;
    }

    private void spawnGetVar(String name) {
        pushUndoState();
        String id = "variable_" + System.currentTimeMillis();
        double cx = ((this.width - SIDEBAR_W) / 2.0 - panX) / zoom - NODE_W / 2.0;
        double cy = (this.height / 2.0           - panY) / zoom - 30;
        cx = Math.max(10.0, Math.min(5120.0 - NODE_W - 10, cx));
        cy = Math.max(10.0, Math.min(3200.0 - 80.0, cy));
        VariableNode node = new VariableNode(id, cx, cy);
        node.setVariableName(name); 
        graph.addNode(node); 
        nodeOrder.add(id);
        selectedNode = node;
    }

    private void spawnSetVar(String name) {
        pushUndoState();
        String id = "set_variable_" + System.currentTimeMillis();
        double cx = ((this.width - SIDEBAR_W) / 2.0 - panX) / zoom - NODE_W / 2.0;
        double cy = (this.height / 2.0           - panY) / zoom - 30;
        cx = Math.max(10.0, Math.min(5120.0 - NODE_W - 10, cx));
        cy = Math.max(10.0, Math.min(3200.0 - 80.0, cy));
        SetVariableNode node = new SetVariableNode(id, cx, cy);
        node.setVariableName(name); 
        graph.addNode(node); 
        nodeOrder.add(id);
        selectedNode = node;
    }

    private void cycleVarType(String name) {
        pushUndoState();
        String cur  = graph.getVariableTypes().getOrDefault(name, "text");
        String next = switch (cur) { case "text" -> "number"; case "number" -> "bool"; default -> "text"; };
        graph.getVariableTypes().put(name, next);
        graph.getVariables().put(name, switch (next) { case "bool" -> "false"; case "number" -> "0"; default -> ""; });
    }

    private void deleteVar(String name) {
        pushUndoState();
        graph.getVariables().remove(name);
        graph.getVariableTypes().remove(name);
        for (AlgoNode n : graph.getNodes().values()) {
            if (n instanceof VariableNode vn    && vn.getVariableName().equals(name))  vn.setVariableName("");
            if (n instanceof SetVariableNode sv && sv.getVariableName().equals(name))  sv.setVariableName("");
        }
        if (name.equals(selectedVar)) selectedVar = null;
    }

    private void saveAndClose() {
        for (AlgoNode node : graph.getNodes().values()) {
            if (node instanceof AntennaInputNode ain && (ain.getChannel() == null || ain.getChannel().isBlank()))
                ain.setChannel("0");
        }
        PacketDistributor.sendToServer(new SaveComputerGraphPacket(pos, graph.toNBT()));
        this.onClose();
    }

    private void pushUndoStateDirect(CompoundTag tag) {
        if (!undoHistory.isEmpty()) {
            CompoundTag top = undoHistory.get(undoHistory.size() - 1);
            if (top.equals(tag)) {
                return;
            }
        }
        if (undoHistory.size() >= MAX_UNDO_STATES) {
            undoHistory.remove(0);
        }
        undoHistory.add(tag);
    }

    private void pushUndoState() {
        pushUndoStateDirect(graph.toNBT());
    }

    private void performUndo() {
        if (!undoHistory.isEmpty()) {
            CompoundTag lastState = undoHistory.remove(undoHistory.size() - 1);
            graph.loadNBT(lastState);
            nodeOrder.clear();
            nodeOrder.addAll(graph.getNodes().keySet());
            selectedNode = null;
            selectedLink = null;
            selectedVar = null;
            selectedNodeIds.clear();
            closeInlineEdit();
        }
    }

    // ─── Coordinate helpers ───────────────────────────────────────────────────
    /** Canvas X → screen X */
    private int toSX(double cx) { return (int)(cx * zoom + panX + SIDEBAR_W); }
    /** Canvas Y → screen Y */
    private int toSY(double cy) { return (int)(cy * zoom + panY); }
    /** Scale a canvas-space dimension to screen pixels */
    private int sD(double d)    { return Math.max(1, (int)(d * zoom)); }
    /** Screen X → canvas X */
    private double toCanvasX(double sx) { return (sx - panX - SIDEBAR_W) / zoom; }
    /** Screen Y → canvas Y */
    private double toCanvasY(double sy) { return (sy - panY) / zoom; }

    private int getPortColor(AlgoNode node, String port, boolean isOutput) {
        String type = node.getType();
        
        // Outputs
        if (isOutput) {
            if (node instanceof BoolNode) return 0xFFCC8822; // Boolean (orange)
            if (node instanceof NumberNode) return 0xFF5090D0; // Number (blue)
            if (node instanceof TextNode) return 0xFF4CAF50; // Text (green)
            
            if (node instanceof EqualNode || node instanceof NotEqualNode 
                    || node instanceof GreaterThanNode || node instanceof LessThanNode
                    || node instanceof OrNode) {
                return 0xFFCC8822; // Outputs boolean (orange)
            }
            if (node instanceof AddNode || node instanceof SubtractNode 
                    || node instanceof MultiplyNode || node instanceof DivideNode
                    || node instanceof FloorNode || node instanceof SqrtNode || node instanceof SquareNode
                    || node instanceof Atan2Node || node instanceof InvertNode || node instanceof AbsNode
                    || node instanceof SinNode || node instanceof CosNode || node instanceof TanNode || node instanceof CtgNode) {
                return 0xFF5090D0; // Outputs number (blue)
            }
            if (node instanceof ActiveTargetNode) {
                if (port.equals("name")) return 0xFF4CAF50; // Text (green)
                return 0xFF5090D0; // Number (blue)
            }
            if (node instanceof GyroscopeNode) {
                return 0xFF5090D0; // Outputs numbers (blue)
            }
            if (node instanceof GyroscopePositionNode) {
                return 0xFF5090D0; // Outputs numbers (blue)
            }
            if (node instanceof LinkInputNode) {
                return 0xFF5090D0; // Outputs number (blue)
            }
            if (node instanceof SignalNode) {
                return 0xFFCCCCCC; // Text (grey)
            }
            if (node instanceof VariableNode vn) {
                String varType = graph.getVariableTypes().getOrDefault(vn.getVariableName(), "text");
                return switch (varType) {
                    case "bool" -> 0xFFCC8822;
                    case "number" -> 0xFF5090D0;
                    default -> 0xFF4CAF50;
                };
            }
            if (node instanceof DisplayLinkNode || node instanceof TextSplitNode || node instanceof TextJoinNode) {
                return 0xFF4CAF50; // Outputs text (green)
            }
            return 0xFFCCCCCC; // Default/Any (grey)
        }
        
        // Inputs
        if (node instanceof BoolViewerNode) {
            return 0xFFCC8822; // Expects boolean (orange)
        }
        if (node instanceof NumberViewerNode) {
            return 0xFF5090D0; // Expects number (blue)
        }
        if (node instanceof TextViewerNode) {
            return 0xFF4CAF50; // Expects text (green)
        }
        if (node instanceof IfNode) {
            if (port.equals("condition")) return 0xFFCC8822; // Expects boolean (orange)
            return 0xFFCCCCCC; // Expects any (grey)
        }
        if (node instanceof OrNode) {
            return 0xFFCC8822; // Expects boolean (orange)
        }
        if (node instanceof GreaterThanNode || node instanceof LessThanNode 
                || node instanceof AddNode || node instanceof SubtractNode 
                || node instanceof MultiplyNode || node instanceof DivideNode
                || node instanceof FloorNode || node instanceof SqrtNode || node instanceof SquareNode
                || node instanceof Atan2Node || node instanceof InvertNode || node instanceof AbsNode
                || node instanceof SinNode || node instanceof CosNode || node instanceof TanNode || node instanceof CtgNode) {
            return 0xFF5090D0; // Expects number (blue)
        }
        if (node instanceof RedstoneOutputNode || node instanceof LinkOutputNode) {
            return 0xFF5090D0; // Expects number (blue)
        }
        if (node instanceof SetVariableNode svn) {
            if (port.equals("event")) return 0xFFCC8822; // Expects boolean (orange)
            String varType = graph.getVariableTypes().getOrDefault(svn.getVariableName(), "text");
            return switch (varType) {
                case "bool" -> 0xFFCC8822;
                case "number" -> 0xFF5090D0;
                default -> 0xFF4CAF50;
            };
        }
        if (type.equals("signal") || type.equals("antenna_output")) {
            if (port.equals("channel")) return 0xFF5090D0; // Expects number (blue)
            if (port.equals("event")) return 0xFFCC8822; // Expects boolean (orange)
            return 0xFFCCCCCC; // Expects any/text (grey)
        }
        if (type.equals("jammer")) {
            if (port.equals("event")) return 0xFFCC8822; // Expects boolean (orange)
            return 0xFF5090D0; // Expects number (blue)
        }
        if (node instanceof TextSplitNode) {
            if (port.equals("text")) return 0xFF4CAF50; // Expects text (green)
            if (port.equals("part_number")) return 0xFF5090D0; // Expects number (blue)
        }
        if (node instanceof TextJoinNode) {
            return 0xFF4CAF50; // Expects text (green)
        }
        
        return 0xFFCCCCCC;
    }

    // ─── Drawing helpers ──────────────────────────────────────────────────────
    private void drawRivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0xFF141312); // Rivet body shadow
        g.fill(x, y, x + 1, y + 1, 0xFF888481); // Rivet head highlight
    }

    private void drawBeveledPlate(GuiGraphics g, int x, int y, int w, int h, MaterialTheme theme, boolean selected) {
        // Outermost border: dark outline shadow
        int outlineCol = selected ? 0xFFFFA500 : 0xFF141312; // Orange glow if selected, dark outline otherwise
        g.fill(x, y, x + w, y + h, outlineCol);
        
        // 3D Bevel highlight on the frame inside edges
        int frameHighlight = selected ? 0xFFFFD700 : theme.highlight;
        int frameShadow = selected ? 0xFFCC6600 : theme.shadow;
        int primary = selected ? 0xFFFFA500 : theme.primary;
        
        // Top and Left light highlight
        g.fill(x + 1, y + 1, x + w - 1, y + 2, frameHighlight);
        g.fill(x + 1, y + 2, x + 2, y + h - 1, frameHighlight);
        
        // Right and Bottom shadow
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, frameShadow);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, frameShadow);
        
        // Frame thickness (middle border)
        g.fill(x + 2, y + 2, x + w - 2, y + 3, primary);
        g.fill(x + 2, y + 3, x + 3, y + h - 3, primary);
        g.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, primary);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, primary);
        
        // Body background fill
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, theme.bodyBg);
    }

    private void drawInsetPanel(GuiGraphics g, int x, int y, int w, int h, MaterialTheme theme) {
        // Outline shadow top/left
        g.fill(x, y, x + w, y + h, theme.shadow);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF141312); // darker outline inside
        
        // Inset highlight (bottom and right edge)
        g.fill(x + 1, y + h - 1, x + w, y + h, theme.highlight);
        g.fill(x + w - 1, y + 1, x + w, y + h, theme.highlight);
        
        // Inside fill
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, theme.insetBg);
    }

    private double[] portCanvasPos(AlgoNode node, String portName, boolean isOutput) {
        List<String> ports = isOutput ? node.getOutputPorts() : node.getInputPorts();
        int idx = Math.max(0, ports.indexOf(portName));
        double px = isOutput ? node.getX() + NODE_W - 3 : node.getX() + 3;
        double py = node.getY() + HDR_H + idx * ROW_H + 11;
        return new double[]{px, py};
    }

    private boolean isInputConnected(String nodeId, String port) {
        return graph.getLinks().stream().anyMatch(l -> l.toNode().equals(nodeId) && l.toPort().equals(port));
    }

    private boolean isOutputConnected(String nodeId, String port) {
        return graph.getLinks().stream().anyMatch(l -> l.fromNode().equals(nodeId) && l.fromPort().equals(port));
    }

    private double dist2(double x1, double y1, double x2, double y2) {
        return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2);
    }

    private NodeLink hitTestWire(double cmx, double cmy) {
        for (NodeLink link : graph.getLinks()) {
            AlgoNode from = graph.getNodes().get(link.fromNode());
            AlgoNode to   = graph.getNodes().get(link.toNode());
            if (from == null || to == null) continue;
            double[] op = portCanvasPos(from, link.fromPort(), true);
            double[] ip = portCanvasPos(to,   link.toPort(),   false);
            if (nearBezierCanvas(cmx, cmy, op[0], op[1], ip[0], ip[1], 4.5)) return link;
        }
        return null;
    }

    private boolean nearBezierCanvas(double mx, double my,
                                      double x1, double y1, double x2, double y2,
                                      double thr) {
        double dx = Math.abs(x2 - x1), co = Math.max(30, dx / 2);
        double cx1 = x1 + co, cy1 = y1, cx2 = x2 - co, cy2 = y2;
        for (int i = 0; i <= 32; i++) {
            double t = i / 32.0, mt = 1 - t;
            double px = mt*mt*mt*x1 + 3*mt*mt*t*cx1 + 3*mt*t*t*cx2 + t*t*t*x2;
            double py = mt*mt*mt*y1 + 3*mt*mt*t*cy1 + 3*mt*t*t*cy2 + t*t*t*y2;
            if ((mx - px) * (mx - px) + (my - py) * (my - py) < thr * thr) return true;
        }
        return false;
    }

    private void drawPort(GuiGraphics g, int cx, int cy, int color, boolean hollow, boolean isOutput) {
        int ringColor = isOutput ? 0xFFC8963E : 0xFFB76D55; // Brass output, Copper input
        
        // 7x7 octagonal/rounded bushing ring
        g.fill(cx - 3, cy - 2, cx + 4, cy + 3, 0xFF141312); // outer shadow vertical
        g.fill(cx - 2, cy - 3, cx + 3, cy + 4, 0xFF141312); // outer shadow horizontal
        
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, ringColor); // metallic collar
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF110F0E); // dark hole inside
        
        if (!hollow) {
            // Plugged wire: center color with 3D highlight
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
            g.fill(cx - 1, cy - 1, cx, cy, 0xFFFFFFFF); // tiny white reflection
        }
    }

    private void drawCreatePipe(GuiGraphics g, double cx1, double cy1,
                                double cx2, double cy2, boolean selected) {
        double startX = cx1 + 3;
        double endX = cx2 - 3;

        double dx = Math.abs(endX - startX);
        double dy = Math.abs(cy2 - cy1);
        double dist = Math.sqrt(dx * dx + dy * dy);
        double estLength = dist + (startX > endX ? Math.max(60, dx) : 0);
        double stepSize = Math.max(1.0, 1.2 / zoom);
        int steps = (int) Math.max(16, estLength / stepSize);

        double co = Math.max(30, dx / 2);
        double ccx1 = startX + co, ccy1 = cy1;
        double ccx2 = endX - co, ccy2 = cy2;
        
        // Create-style color palette
        int outlineCol = selected ? 0xFF4A3212 : 0xFF35201B; // dark brass / dark copper
        int coreCol = selected ? 0xFFC8963E : 0xFFB76D55;    // brass / copper
        int highlightCol = selected ? 0xFFE9C583 : 0xFFDCA28E; // bright brass / bright copper

        // Cache calculated Bezier points to prevent redrawing math overhead
        double[] nxs = new double[steps + 1];
        double[] nys = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double mt = 1 - t;
            nxs[i] = mt*mt*mt*startX + 3*mt*mt*t*ccx1 + 3*mt*t*t*ccx2 + t*t*t*endX;
            nys[i] = mt*mt*mt*cy1 + 3*mt*mt*t*ccy1 + 3*mt*t*t*ccy2 + t*t*t*cy2;
        }

        // 1. Draw outline (4x4 squares)
        for (int i = 0; i <= steps; i++) {
            g.fill((int)nxs[i] - 2, (int)nys[i] - 2, (int)nxs[i] + 2, (int)nys[i] + 2, outlineCol);
        }

        // 2. Draw core (2x2 squares)
        for (int i = 0; i <= steps; i++) {
            g.fill((int)nxs[i] - 1, (int)nys[i] - 1, (int)nxs[i] + 1, (int)nys[i] + 1, coreCol);
        }

        // 3. Draw highlight (1x1 squares)
        for (int i = 0; i <= steps; i++) {
            g.fill((int)nxs[i] - 1, (int)nys[i] - 1, (int)nxs[i], (int)nys[i], highlightCol);
        }
    }

    /**
     * Draws a cubic bezier in canvas space.
     * Control points are in canvas-space.
     */
    private void drawBezierCanvas(GuiGraphics g, double cx1, double cy1,
                                   double cx2, double cy2, int color) {
        drawCreatePipe(g, cx1, cy1, cx2, cy2, true);
    }

    /** Redstone-style dashed wire: alternates bright-red / dark-red segments. */
    private void drawRsWireCanvas(GuiGraphics g, double cx1, double cy1,
                                   double cx2, double cy2) {
        drawCreatePipe(g, cx1, cy1, cx2, cy2, false);
    }

    // ─── Lookup helpers ───────────────────────────────────────────────────────
    private int propRowCount(AlgoNode node) {
        if (node instanceof com.radiologistics.create.node.nodes.CommentNode) return 0;
        // Pure math/logic operator nodes have no property row
        if (node instanceof EqualNode || node instanceof NotEqualNode
                || node instanceof GreaterThanNode || node instanceof LessThanNode
                || node instanceof AddNode || node instanceof SubtractNode
                || node instanceof MultiplyNode || node instanceof DivideNode
                || node instanceof IfNode || node instanceof OrNode
                || node instanceof FloorNode || node instanceof SqrtNode || node instanceof SquareNode
                || node instanceof Atan2Node || node instanceof InvertNode || node instanceof AbsNode
                || node instanceof SinNode || node instanceof CosNode || node instanceof TanNode || node instanceof CtgNode
                || node instanceof ActiveTargetNode
                || node instanceof com.radiologistics.create.node.nodes.JammerNode
                || node instanceof DisplayLinkNode
                || node instanceof TextSplitNode
                || node instanceof TextJoinNode
                || node instanceof TextSpeakNode
                || node instanceof SoundPlayNode) return 0;
        return 1;
    }

    private int nodeHeaderColor(String type) {
        return switch (type) {
            case "signal"                 -> 0xFF4F6E80; // Zinc/Blue
            case "bool"                   -> 0xFFB76D55; // Copper/Red-Brown
            case "number"                 -> 0xFF7F8485; // Andesite/Gray
            case "text"                   -> 0xFFC8963E; // Brass/Yellow
            case "comment"                -> 0xFF5F5A57; // Dark Grey/Iron
            case "equal","not_equal"      -> 0xFFB76D55; // Copper
            case "greater_than","less_than" -> 0xFFB76D55; // Copper
            case "or"                     -> 0xFFB76D55; // Copper
            case "add","subtract","multiply","divide" -> 0xFF7F8485; // Andesite
            case "floor","sqrt","square"  -> 0xFF7F8485; // Andesite
            case "atan2"                  -> 0xFF7F8485; // Andesite
            case "invert","abs"           -> 0xFF7F8485; // Andesite
            case "sin","cos","tan","ctg"  -> 0xFF7F8485; // Andesite
            case "if"                     -> 0xFFB76D55; // Copper
            case "redstone_output"        -> 0xFF9E2A2B; // Redstone
            case "jammer"                 -> 0xFF9E2A2B; // Jammer
            case "link_input","link_output" -> 0xFFD2691E; // Industrial Orange
            case "gyroscope","gyroscope_position" -> 0xFF5C3C6B; // Obsidian/Purple
            case "antenna_input","antenna_output" -> 0xFF4F6E80; // Wireless/Zinc
            case "variable","set_variable"-> 0xFFC97C8E; // Rose Quartz
            case "active_target"          -> 0xFF5C3C6B; // Target/Obsidian
            case "bool_viewer"            -> 0xFFB76D55; // Copper
            case "number_viewer"          -> 0xFF7F8485; // Andesite
            case "text_viewer"            -> 0xFFC8963E; // Brass
            case "display_link"           -> 0xFFC8963E; // Brass
            case "text_split","text_join","text_speak","sound_play" -> 0xFFC8963E; // Brass
            default                       -> 0xFF5F5A57;
        };
    }

    private String nodeTitle(String type) {
        return switch (type) {
            case "signal"          -> "Signal";
            case "bool"            -> "Bool";
            case "number"          -> "Number";
            case "text"            -> "Text";
            case "comment"         -> "Comment";
            case "equal"           -> "Equal";
            case "not_equal"       -> "≠ Not Equal";
            case "greater_than"    -> "> Greater";
            case "less_than"       -> "< Less";
            case "or"              -> "Or";
            case "add"             -> "+ Add";
            case "subtract"        -> "− Subtract";
            case "multiply"        -> "× Multiply";
            case "divide"          -> "÷ Divide";
            case "floor"           -> "Floor";
            case "sqrt"            -> "√ Sqrt";
            case "square"          -> "x² Square";
            case "atan2"           -> "Atan2";
            case "invert"          -> "Invert";
            case "abs"             -> "Abs";
            case "sin"             -> "Sin";
            case "cos"             -> "Cos";
            case "tan"             -> "Tan";
            case "ctg"             -> "Ctg";
            case "if"              -> "If";
            case "redstone_output" -> "RS Output";
            case "link_input"      -> "Link Input";
            case "link_output"     -> "Link Output";
            case "gyroscope"       -> "Gyroscope";
            case "gyroscope_position" -> "Gyro Position";
            case "antenna_input"   -> "Antenna In";
            case "antenna_output"  -> {
                if (this.activeComputer != null) {
                    int antHeight = this.activeComputer.getComputerAntennaHeight();
                    int range = antHeight == 0 ? 100 : 100 + antHeight * 150;
                    if (range > 3000) range = 3000;
                    yield "Send Signal (" + range + "m)";
                }
                yield "Send Signal";
            }
            case "jammer"          -> "Jammer";
            case "variable"        -> "Get Var";
            case "set_variable"    -> "Set Var";
            case "active_target"   -> "Active Target";
            case "bool_viewer"     -> "Bool View";
            case "number_viewer"   -> "Num View";
            case "text_viewer"     -> "Text View";
            case "display_link"    -> "Display Link";
            case "text_split"      -> "Text Split";
            case "text_join"       -> "Text Join";
            case "text_speak"      -> "Text Speak";
            case "sound_play"      -> "Sound Play";
            default                -> type;
        };
    }

    private int varTypeColor(String type) {
        return switch (type) { case "number" -> 0xFF1A3A5A; case "bool" -> 0xFF3A2A1A; default -> 0xFF1A2A1A; };
    }

    private String shortItem(String id) {
        if (id == null || id.equals("minecraft:air")) return "—";
        int c = id.indexOf(':'); return c >= 0 ? id.substring(c + 1) : id;
    }

    private ItemStack getItemStackFromId(String id) {
        if (id == null || id.equals("minecraft:air") || id.isBlank()) return ItemStack.EMPTY;
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(id);
            if (BuiltInRegistries.ITEM.containsKey(rl)) {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(rl);
                return new ItemStack(item);
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }


    private String trunc(String s, int max) {
        if (s == null) return ""; return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private void startExport() {
        showFileDialog = true;
        fileDialogExport = true;
        fileDialogError = "";
        refreshAvailableSchemes();
        openInlineEdit(null, "export", "scheme1");
        positionInlineBox(null);
    }

    private void startImport() {
        showFileDialog = true;
        fileDialogExport = false;
        fileDialogError = "";
        refreshAvailableSchemes();
        if (!availableSchemes.isEmpty()) {
            selectedSchemeIndex = 0;
            openInlineEdit(null, "import", availableSchemes.get(0));
        } else {
            openInlineEdit(null, "import", "");
        }
        positionInlineBox(null);
    }

    private void refreshAvailableSchemes() {
        availableSchemes.clear();
        selectedSchemeIndex = -1;
        schemeScrollOffset = 0;
        try {
            java.io.File dir = new java.io.File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "radiologistics_schemes");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (java.io.File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".json")) {
                        availableSchemes.add(name.substring(0, name.length() - 5));
                    }
                }
            }
            availableSchemes.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (Exception ignored) {}
    }

    private void closeFileDialog() {
        showFileDialog = false;
        fileDialogError = "";
        draggingSchemeScrollbar = false;
        closeInlineEdit();
    }

    private void drawDialogButton(GuiGraphics g, String label, int bx, int by, int bw, int bh, double mx, double my) {
        drawCreateButton(g, label, bx, by, bw, bh, mx, my, label.equals("Export") || label.equals("Import"));
    }

    private void drawCreateButton(GuiGraphics g, String label, int x, int y, int w, int h, double mx, double my, boolean brassStyle) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        
        // Outline (shadow)
        g.fill(x, y, x + w, y + h, 0xFF141312);
        
        // Base plate colors
        int bg = brassStyle ? (hover ? 0xFFD8A64E : 0xFFC8963E) : (hover ? 0xFF6A6D6E : 0xFF5A5D5E);
        int highlight = brassStyle ? 0xFFE9C583 : 0xFF808284;
        int shadow = brassStyle ? 0xFF8C5F1C : 0xFF383A3B;
        
        // Draw beveled faces
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        // Highlight top & left
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, highlight);
        // Shadow bottom & right
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shadow);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shadow);
        
        // Inset face
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF141312);
        int faceBg = brassStyle ? (hover ? 0xFFB8860B : 0xFF996515) : (hover ? 0xFF4E5152 : 0xFF3A3D3E);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, faceBg);
        
        // Text with engraved drop shadow
        int tw = this.font.width(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 9) / 2;
        
        g.drawString(this.font, label, tx + 1, ty + 1, 0x80000000); // drop shadow
        int textColor = hover ? 0xFFFFFFFF : (brassStyle ? 0xFFFFEAA0 : 0xFFE0E0E0);
        g.drawString(this.font, label, tx, ty, textColor);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void handleFileDialogSubmit() {
        String filename = inlineBox.getValue().trim();
        if (filename.isEmpty()) {
            fileDialogError = "Name cannot be empty";
            return;
        }
        filename = filename.replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (filename.isEmpty()) {
            fileDialogError = "Invalid name";
            return;
        }

        java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "radiologistics_schemes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        java.io.File file = new java.io.File(dir, filename + ".json");

        if (fileDialogExport) {
            try {
                CompoundTag nbt = graph.toNBT();
                String snbt = nbt.toString();
                java.nio.file.Files.writeString(file.toPath(), snbt, java.nio.charset.StandardCharsets.UTF_8);
                closeFileDialog();
            } catch (Exception e) {
                fileDialogError = "Failed to export: " + e.getMessage();
            }
        } else {
            try {
                if (!file.exists()) {
                    fileDialogError = "File not found";
                    return;
                }
                String snbt = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                CompoundTag nbt = net.minecraft.nbt.TagParser.parseTag(snbt);
                pushUndoState();
                Set<String> oldIds = new HashSet<>(graph.getNodes().keySet());
                graph.mergeNBT(nbt);
                
                List<String> newImportedIds = new ArrayList<>();
                for (String id : graph.getNodes().keySet()) {
                    if (!oldIds.contains(id)) {
                        newImportedIds.add(id);
                    }
                }
                
                double maxX = 0;
                double maxY = 0;
                for (String id : newImportedIds) {
                    AlgoNode node = graph.getNodes().get(id);
                    if (node != null) {
                        if (node.getX() > maxX) maxX = node.getX();
                        if (node.getY() > maxY) maxY = node.getY();
                    }
                }
                if (maxX < 2000 && maxY < 1500) {
                    for (String id : newImportedIds) {
                        AlgoNode node = graph.getNodes().get(id);
                        if (node != null) {
                            node.setPos(node.getX() + 2560, node.getY() + 1600);
                        }
                    }
                }
                
                nodeOrder.addAll(newImportedIds);
                selectedNode = null;
                selectedLink = null;
                selectedVar = null;
                selectedNodeIds.clear();
                closeFileDialog();
            } catch (Exception e) {
                fileDialogError = "Failed to import: " + e.getMessage();
            }
        }
    }

    private void updateSchemeScrollFromMouse(double my, int sbY, int sbH, int maxVisible) {
        double relativeY = my - sbY;
        double viewRatio = (double) maxVisible / availableSchemes.size();
        int thumbH = (int) (sbH * viewRatio);
        double scrollPercent = (relativeY - thumbH / 2.0) / (sbH - thumbH);
        scrollPercent = Math.max(0.0, Math.min(1.0, scrollPercent));
        schemeScrollOffset = Math.max(0.0, scrollPercent * (availableSchemes.size() - maxVisible));
    }

    private List<String> splitComment(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ", -1);
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (word.contains("\n") || word.contains("|")) {
                String[] parts = word.split("\n|\\|", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder();
                    }
                    appendWord(currentLine, parts[i], maxWidth, lines);
                }
            } else {
                appendWord(currentLine, word, maxWidth, lines);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private void appendWord(StringBuilder currentLine, String word, int maxWidth, List<String> lines) {
        String testString = currentLine.length() == 0 ? word : currentLine + " " + word;
        if (this.font.width(testString) <= maxWidth) {
            if (currentLine.length() > 0) currentLine.append(" ");
            currentLine.append(word);
        } else {
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            if (this.font.width(word) > maxWidth) {
                StringBuilder temp = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    if (this.font.width(temp.toString() + c) <= maxWidth) {
                        temp.append(c);
                    } else {
                        lines.add(temp.toString());
                        temp = new StringBuilder();
                        temp.append(c);
                    }
                }
                if (temp.length() > 0) {
                    currentLine.append(temp);
                }
            } else {
                currentLine.append(word);
            }
        }
    }

    private int nodeHeight(AlgoNode node) {
        int nhH = HDR_H;
        if (node instanceof com.radiologistics.create.node.nodes.CommentNode cn) {
            String fullText = (cn.getId().equals(inlineNodeId) && inlineActive && "comment".equals(inlineField)) 
                ? inlineBox.getValue() + "_" : cn.getComment();
            List<String> lines = splitComment(fullText, NODE_W - 16);
            return nhH + lines.size() * 10 + 13;
        } else {
            List<String> ins  = node.getInputPorts();
            List<String> outs = node.getOutputPorts();
            int maxP  = Math.max(ins.size(), outs.size());
            int prCnt = propRowCount(node);
            return nhH + 4 + maxP * ROW_H + (prCnt > 0 ? (prCnt * PROP_H + 2) : 0) + 6;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
