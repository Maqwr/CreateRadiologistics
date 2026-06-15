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
            case "or","and"               -> THEME_COPPER;
            case "add","subtract","multiply","divide" -> THEME_ANDESITE;
            case "floor","sqrt","square"  -> THEME_ANDESITE;
            case "atan2"                  -> THEME_ANDESITE;
            case "invert","abs"           -> THEME_ANDESITE;
            case "sin","cos","tan","ctg","pos_to_rot","rot_to_pos","random","degree_vector"  -> THEME_ANDESITE;
            case "if"                     -> THEME_COPPER;
            case "redstone_output"        -> THEME_REDSTONE;
            case "jammer"                 -> THEME_REDSTONE;
            case "link_input","link_output" -> THEME_BRASS;
            case "gyroscope","gyroscope_position" -> THEME_OBSIDIAN;
            case "antenna_output" -> THEME_ZINC;
            case "variable","set_variable"-> THEME_ROSE;
            case "active_target"          -> THEME_OBSIDIAN;
            case "bool_viewer"            -> THEME_COPPER;
            case "number_viewer"          -> THEME_ANDESITE;
            case "text_viewer"            -> THEME_BRASS;
            case "display_link"           -> THEME_BRASS;
            case "text_split","text_join" -> THEME_BRASS;
            case "text_speak","sound_play","microphone","audio_play" -> THEME_BRASS;
            case "helmet_pos","helmet_rotation","helmet_screen" -> THEME_BRASS;
            case "camera","screen","display_board" -> THEME_BRASS;
            case "cannon_rot"             -> THEME_OBSIDIAN;
            case "gizmos_2d","gizmos_3d","gizmos_combine","gizmos_view" -> THEME_COPPER;
            default                       -> THEME_COMMENT;
        };
    }


    // ─── Sidebar card catalogue ───────────────────────────────────────────────
    private static final String[][] CONSTANT_CARDS = {
        {"Bool",       "bool",            "T/F"},
        {"Comment",    "comment",         "//"},
        {"Number",     "number",          "#"},
        {"Text",       "text",            "\"\""},
    };

    private static final String[][] LOGIC_CARDS = {
        {"Equal",      "equal",           "="},
        {"Not Equal",  "not_equal",       "≠"},
        {">",          "greater_than",    ">"},
        {"<",          "less_than",       "<"},
        {"And",        "and",             "&&"},
        {"Or",         "or",              "||"},
        {"If",         "if",              "?"},
    };

    private static final String[][] MATH_CARDS = {
        {"Add",        "add",             "+"},
        {"Subtract",   "subtract",        "−"},
        {"Multiply",   "multiply",        "×"},
        {"Divide",     "divide",          "÷"},
        {"Abs",        "abs",             "|x|"},
        {"Invert",     "invert",          "−x"},
        {"Floor",      "floor",           "⌊⌋"},
        {"Square",     "square",          "x²"},
        {"Sqrt",       "sqrt",            "√"},
        {"Sin",        "sin",             "sin"},
        {"Cos",        "cos",             "cos"},
        {"Tan",        "tan",             "tan"},
        {"Ctg",        "ctg",             "ctg"},
        {"Atan2",      "atan2",           "atan2"},
        {"Random",     "random",          "🎲"},
        {"Deg Vector", "degree_vector",   "∠"},
        {"Pos to rot", "pos_to_rot",      "⇄"},
        {"Rot to pos", "rot_to_pos",      "⇆"},
    };

    private static final String[][] TEXT_CARDS = {
        {"Text Join",  "text_join",       "⧉"},
        {"Text Split", "text_split",      "✂"},
    };

    private static final String[][] VIEWER_CARDS = {
        {"Bool View",  "bool_viewer",     "👁B"},
        {"Num View",   "number_viewer",   "👁#"},
        {"Text View",  "text_viewer",     "👁T"},
    };

    private List<String[]> getSignalsCards() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"Signal", "signal", "📡"});
        if (connectedModules.contains("antenna")) {
            list.add(new String[]{"Send Signal", "antenna_output", "→"});
        }
        if (connectedModules.contains("jammer")) {
            list.add(new String[]{"Jammer", "jammer", "⚡"});
        }
        if (connectedModules.contains("network_controller")) {
            list.add(new String[]{"Active Target", "active_target", "⌖"});
        }
        return list;
    }

    private List<String[]> getLinkControllerCards() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"RS Output", "redstone_output", "RS"});
        if (connectedModules.contains("redstone_link")) {
            list.add(new String[]{"Link In", "link_input", "↓"});
            list.add(new String[]{"Link Out", "link_output", "↑"});
        }
        return list;
    }

    private List<String[]> getGyroscopeCards() {
        List<String[]> list = new ArrayList<>();
        if (connectedModules.contains("gyroscope")) {
            list.add(new String[]{"Gyroscope", "gyroscope", "⊕"});
            list.add(new String[]{"Gyro Position", "gyroscope_position", "⛖"});
        }
        return list;
    }

    private List<String[]> getHelmetCards() {
        List<String[]> list = new ArrayList<>();
        if (connectedModules.contains("helmet")) {
            list.add(new String[]{"Helmet Pos", "helmet_pos", "⛗"});
            list.add(new String[]{"Helmet Rot", "helmet_rotation", "⚙"});
            list.add(new String[]{"Helmet Screen", "helmet_screen", "☠"});
        }
        return list;
    }

    private List<String[]> getAudioCards() {
        List<String[]> list = new ArrayList<>();
        if (connectedModules.contains("audio")) {
            list.add(new String[]{"Audio Play", "audio_play", "🔊"});
            list.add(new String[]{"Microphone", "microphone", "🎙"});
            list.add(new String[]{"Link Stream", "sound_play", "🔗"});
            list.add(new String[]{"Text Stream", "text_speak", "📝"});
        }
        return list;
    }

    private List<String[]> getMediaCards() {
        List<String[]> list = new ArrayList<>();
        if (connectedModules.contains("display_link")) {
            list.add(new String[]{"Display Link", "display_link", "DL"});
        }
        if (connectedModules.contains("screen")) {
            list.add(new String[]{"Screen Node", "screen", "☐"});
        }
        if (connectedModules.contains("cassette_reader")) {
            list.add(new String[]{"Cassette Reader", "camera", "📼"});
        }
        List<String> sortedDisplayBoards = connectedModules.stream()
                .filter(m -> m.startsWith("display_board_"))
                .sorted()
                .toList();
        for (String m : sortedDisplayBoards) {
            String coords = m.substring("display_board_".length()).replace('_', ',');
            String label = "Display Board " + coords;
            if (label.length() > 18) label = label.substring(0, 16) + "..";
            list.add(new String[]{label, m, "☐"});
        }
        return list;
    }

    private List<String[]> getCannonCards() {
        List<String[]> list = new ArrayList<>();
        if (connectedModules.contains("cannon_mount")) {
            list.add(new String[]{"Cannon Rot", "cannon_rot", "⚙"});
        }
        return list;
    }

    private List<String[]> getGizmosCards() {
        List<String[]> list = new ArrayList<>();
        if (connectedModules.contains("helmet") || connectedModules.contains("screen")) {
            list.add(new String[]{"Shape", "shape", "■"});
            list.add(new String[]{"Color RGB", "color_rgb", "🎨"});
            list.add(new String[]{"Gizmos 2D", "gizmos_2d", "2D"});
            list.add(new String[]{"Gizmos 3D", "gizmos_3d", "3D"});
            list.add(new String[]{"Gizmos Comb", "gizmos_combine", "⧉"});
            list.add(new String[]{"Gizmos View", "gizmos_view", "👁"});
        }
        return list;
    }


    // ─── Data ────────────────────────────────────────────────────────────────
    private final BlockPos   pos;
    private final NodeGraph  graph;
    private final Set<String> connectedModules = new HashSet<>();
    private int jammerCount = 0;
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

    private int getJammerCount() {
        if (this.activeComputer != null) {
            int count = 0;
            for (String key : this.activeComputer.getLinkedModules().keySet()) {
                if (key.startsWith("jammer_")) {
                    count++;
                }
            }
            return count;
        }
        int count = 0;
        for (String key : this.connectedModules) {
            if (key.startsWith("jammer_")) {
                count++;
            }
        }
        return count > 0 ? count : this.jammerCount;
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

    // ─── Ponder State Fields ──────────────────────────────────────────────────
    private boolean  wKeyDown                 = false;
    private boolean  wKeyReleasedSinceLastClose = true;
    private long     ponderOpenTime           = 0;
    private AlgoNode ponderHoveredNode        = null;
    private String   ponderHoveredType        = null;
    private long     ponderHoldStart          = 0;
    private int      ponderHoveredCardY       = -1;
    
    private boolean  ponderOpen               = false;
    private String   ponderOpenType           = null;
    private double   ponderTimelineElapsedTime = 0.0;
    private long     ponderLastUpdateNano     = 0;
    private boolean  ponderPaused             = false;
    private boolean  isScrubberDragging       = false;
    
    private boolean  renderingPonderScene     = false;
    private List<PonderWire> currentPonderWires = new ArrayList<>();
    private final List<PonderChapter> currentPonderChapters = new ArrayList<>();
    private double   lastMouseX               = 0;
    private double   lastMouseY               = 0;
    private String   lastHoveredSidebarType   = null;
    private int      lastHoveredSidebarY      = -1;
    private AlgoNode ponderOpenNode           = null;
    
    private static final double TOTAL_TIMELINE_DURATION_MS = 24000.0;

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
        if (graphNBT.contains("jammerCount")) {
            this.jammerCount = graphNBT.getInt("jammerCount");
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
        lastMouseX = mx;
        lastMouseY = my;

        // Read physical W key state using GLFW to ensure reliability, also check bound key
        long windowHandle = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        boolean wPhysicallyDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle, 87); // 87 is GLFW_KEY_W
        try {
            int boundKey = net.minecraft.client.Minecraft.getInstance().options.keyUp.getKey().getValue();
            if (boundKey > 0 && boundKey < 500) {
                wPhysicallyDown |= com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle, boundKey);
            }
        } catch (Exception ignored) {}

        if (!wPhysicallyDown) {
            wKeyReleasedSinceLastClose = true;
        }

        wKeyDown = wPhysicallyDown && !inlineActive && !showFileDialog && wKeyReleasedSinceLastClose;

        // Tick timeline
        if (ponderOpen) {
            long now = System.nanoTime();
            if (ponderLastUpdateNano == 0) {
                ponderLastUpdateNano = now;
            }
            if (!ponderPaused && !isScrubberDragging) {
                double deltaMs = (now - ponderLastUpdateNano) / 1_000_000.0;
                ponderTimelineElapsedTime += deltaMs;
                if (ponderTimelineElapsedTime >= TOTAL_TIMELINE_DURATION_MS) {
                    ponderTimelineElapsedTime = 0.0;
                }
            }
            ponderLastUpdateNano = now;
        } else {
            ponderLastUpdateNano = 0;
        }

        // Handle W-key hover validation / detection / invalidation
        if (wKeyDown && !ponderOpen) {
            // If we don't have a hovered node or type yet, try to detect one!
            if (ponderHoveredNode == null && ponderHoveredType == null) {
                detectPonderHover(mx, my);
            }
            
            boolean valid = false;
            if (ponderHoveredNode != null) {
                double cmx = toCanvasX(mx);
                double cmy = toCanvasY(my);
                int nh = nodeHeight(ponderHoveredNode);
                if (cmx >= ponderHoveredNode.getX() && cmx <= ponderHoveredNode.getX() + NODE_W
                 && cmy >= ponderHoveredNode.getY() && cmy <= ponderHoveredNode.getY() + nh) {
                    valid = true;
                }
            } else if (ponderHoveredType != null) {
                if (mx < SIDEBAR_W && lastHoveredSidebarType != null && lastHoveredSidebarType.equals(ponderHoveredType)) {
                    valid = true;
                }
            }
            if (!valid) {
                ponderHoveredNode = null;
                ponderHoveredType = null;
                ponderHoldStart = 0;
                ponderHoveredCardY = -1;
            }
        } else if (!wKeyDown) {
            ponderHoveredNode = null;
            ponderHoveredType = null;
            ponderHoldStart = 0;
            ponderHoveredCardY = -1;
        }

        // Automatically open ponder when hold reaches 1 second (1000ms)
        if (ponderHoldStart > 0 && !ponderOpen) {
            double elapsed = System.currentTimeMillis() - ponderHoldStart;
            if (elapsed >= 1000.0) {
                ponderOpen = true;
                ponderOpenTime = System.currentTimeMillis();
                ponderOpenType = (ponderHoveredNode != null) ? ponderHoveredNode.getType() : ponderHoveredType;
                ponderTimelineElapsedTime = 0.0;
                ponderLastUpdateNano = System.nanoTime();
                ponderPaused = false;
                ponderOpenNode = ponderHoveredNode;
                initPonderChapters(ponderOpenType, ponderHoveredNode);
                ponderHoveredNode = null;
                ponderHoveredType = null;
                ponderHoldStart = 0;
                ponderHoveredCardY = -1;
                
                wKeyDown = false;
            }
        }

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

        // Wires, Nodes and Wire-drag preview
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
            g.drawString(this.font, "§c[X] Delete node", cx0 + 6, this.height - 22, 0xFFFF6666);
            g.drawString(this.font, "§6[W] to ponder", cx0 + 6, this.height - 11, 0xFFFFD700);
        } else if (selectedLink != null) {
            g.drawString(this.font, "§c[X] Delete wire", cx0 + 6, this.height - 17, 0xFFFF6666);
        } else {
            g.drawString(this.font, "§6[W] to ponder", cx0 + 6, this.height - 17, 0xFFFFD700);
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
                    g.fill(sbX + 2, thumbY + thumbH - 2, sbX + 7, thumbY + thumbH - 1, 0xFF8C5F1C); // knob shadow
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

        // Render Ponder Overlay
        if (ponderOpen) {
            g.flush(); // Flush background elements
            g.pose().pushPose();
            g.pose().translate(0, 0, 8000.0f);
            renderPonderOverlay(g, mx, my);
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
        h += 22 + LOGIC_CARDS.length * 30;
        h += 22 + MATH_CARDS.length * 30;
        h += 22 + TEXT_CARDS.length * 30;
        
        List<String[]> signalsCards = getSignalsCards();
        if (!signalsCards.isEmpty()) h += 22 + signalsCards.size() * 30;
        
        List<String[]> linkControllerCards = getLinkControllerCards();
        if (!linkControllerCards.isEmpty()) h += 22 + linkControllerCards.size() * 30;
        
        List<String[]> gyroscopeCards = getGyroscopeCards();
        if (!gyroscopeCards.isEmpty()) h += 22 + gyroscopeCards.size() * 30;
        
        List<String[]> helmetCards = getHelmetCards();
        if (!helmetCards.isEmpty()) h += 22 + helmetCards.size() * 30;
        
        List<String[]> audioCards = getAudioCards();
        if (!audioCards.isEmpty()) h += 22 + audioCards.size() * 30;
        
        List<String[]> mediaCards = getMediaCards();
        if (!mediaCards.isEmpty()) h += 22 + mediaCards.size() * 30;
        
        List<String[]> gizmosCards = getGizmosCards();
        if (!gizmosCards.isEmpty()) h += 22 + gizmosCards.size() * 30;

        List<String[]> cannonCards = getCannonCards();
        if (!cannonCards.isEmpty()) h += 22 + cannonCards.size() * 30;
        
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
        lastHoveredSidebarType = null;
        lastHoveredSidebarY = -1;
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

        // --- LOGIC ---
        drawSectionHeader(g, "LOGIC", y); y += 22;
        for (String[] card : LOGIC_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }

        // --- MATHEMATICS ---
        drawSectionHeader(g, "MATHEMATICS", y); y += 22;
        for (String[] card : MATH_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }

        // --- TEXT OPS ---
        drawSectionHeader(g, "TEXT OPS", y); y += 22;
        for (String[] card : TEXT_CARDS) {
            drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
            y += 30;
        }

        // --- SIGNALS ---
        List<String[]> signalsCards = getSignalsCards();
        if (!signalsCards.isEmpty()) {
            drawSectionHeader(g, "SIGNALS", y); y += 22;
            for (String[] card : signalsCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- LINK CONTROLLER ---
        List<String[]> linkControllerCards = getLinkControllerCards();
        if (!linkControllerCards.isEmpty()) {
            drawSectionHeader(g, "LINK CONTROLLER", y); y += 22;
            for (String[] card : linkControllerCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- GYROSCOPE ---
        List<String[]> gyroscopeCards = getGyroscopeCards();
        if (!gyroscopeCards.isEmpty()) {
            drawSectionHeader(g, "GYROSCOPE", y); y += 22;
            for (String[] card : gyroscopeCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- HELMET ---
        List<String[]> helmetCards = getHelmetCards();
        if (!helmetCards.isEmpty()) {
            drawSectionHeader(g, "HELMET", y); y += 22;
            for (String[] card : helmetCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- AUDIO ---
        List<String[]> audioCards = getAudioCards();
        if (!audioCards.isEmpty()) {
            drawSectionHeader(g, "AUDIO", y); y += 22;
            for (String[] card : audioCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- MEDIA ---
        List<String[]> mediaCards = getMediaCards();
        if (!mediaCards.isEmpty()) {
            drawSectionHeader(g, "MEDIA", y); y += 22;
            for (String[] card : mediaCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- GIZMOS ---
        List<String[]> gizmosCards = getGizmosCards();
        if (!gizmosCards.isEmpty()) {
            drawSectionHeader(g, "GIZMOS", y); y += 22;
            for (String[] card : gizmosCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
        }

        // --- CANNON ---
        List<String[]> cannonCards = getCannonCards();
        if (!cannonCards.isEmpty()) {
            drawSectionHeader(g, "CANNON", y); y += 22;
            for (String[] card : cannonCards) {
                drawNodeCard(g, card[0], card[1], card[2], y, mx, my);
                y += 30;
            }
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

    private String elide(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixW = this.font.width(suffix);
        if (suffixW >= maxWidth) {
            return "";
        }
        int len = text.length();
        for (int i = len - 1; i >= 0; i--) {
            String sub = text.substring(0, i) + suffix;
            if (this.font.width(sub) <= maxWidth) {
                return sub;
            }
        }
        return suffix;
    }

    private void drawNodeCard(GuiGraphics g, String label, String type, String sym, int y, double mx, double my) {
        if (y + 28 < 0 || y > this.height) return;
        int x = 4;
        int w = SIDEBAR_W - 14;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 28;
        
        if (hover && !ponderOpen) {
            lastHoveredSidebarType = type;
            lastHoveredSidebarY = y;
        }
        
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
        
        int maxLabelW = w - 40;
        String elidedLabel = elide(label, maxLabelW);
        g.drawString(this.font, elidedLabel, x + 36 + textShift, y + 10, textCol);
        
        // If this card is currently being hovered & W is held, render the progress bar inside it
        if (ponderOpenType == null && type.equals(ponderHoveredType) && ponderHoldStart > 0) {
            double elapsed = System.currentTimeMillis() - ponderHoldStart;
            float progress = (float)(elapsed / 1000.0);
            if (progress > 0 && progress < 1.0f) {
                int barW = w - 8;
                int barH = 3;
                int bx = x + 4;
                int by = y + 23;
                g.fill(bx, by, bx + barW, by + barH, 0xFF141312);
                int fillW = (int) (barW * progress);
                if (fillW > 0) {
                    g.fill(bx, by, bx + fillW, by + barH, 0xFFC8963E);
                    g.fill(bx, by, bx + fillW, by + 1, 0xFFE9C583);
                }
            }
        }
        
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

        // If this node is currently being hovered & W is held, render the progress bar above it
        if (ponderOpenType == null && node == ponderHoveredNode && ponderHoldStart > 0) {
            double elapsed = System.currentTimeMillis() - ponderHoldStart;
            float progress = (float)(elapsed / 1000.0);
            if (progress > 0 && progress < 1.0f) {
                int barW = nw;
                int barH = 4;
                int bx = 0;
                int by = -8;
                g.fill(bx, by, bx + barW, by + barH, 0xFF141312);
                int fillW = (int) (barW * progress);
                if (fillW > 0) {
                    g.fill(bx, by, bx + fillW, by + barH, 0xFFC8963E);
                    g.fill(bx, by, bx + fillW, by + 1, 0xFFE9C583);
                }
            }
        }

        // 1. Draw the beveled plate chassis
        drawBeveledPlate(g, 0, 0, nw, nh, theme, isSelected);

        boolean isMock = false;
        int insetY;
        if (isMock) {
            insetY = 3;
        } else {
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
            int maxTitleW = nw - 24;
            String elidedTitle = elide(title, maxTitleW);
            int tw = this.font.width(elidedTitle);
            int titleX = (nw - tw) / 2;
            int titleY = 4;
            g.drawString(this.font, elidedTitle, titleX, titleY, isSelected ? 0xFFFFEAA0 : 0xFFFFFFFF);
            
            insetY = HDR_H + 3;
        }

        // 3. Draw inset panel for the rest of the node (ports + properties)
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
                int maxPortW = nw / 2 - 14;
                String elidedPn = elide(pn, maxPortW);
                g.drawString(this.font, elidedPn, px + 9, py + 2, 0xFFACAFB0);
            }

            // Output ports (male plugs / brass)
            for (int i = 0; i < outs.size(); i++) {
                String pn = outs.get(i);
                int py = insetY + i * ROW_H + 2;
                int px = nw - 3;
                boolean conn = isOutputConnected(node.getId(), pn);
                int pcol = getPortColor(node, pn, true);
                drawPort(g, px, py + 6, pcol, !conn, true);
                int maxPortW = nw / 2 - 14;
                String elidedPn = elide(pn, maxPortW);
                int pw = this.font.width(elidedPn);
                g.drawString(this.font, elidedPn, px - 9 - pw, py + 2, 0xFFACAFB0);
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
        } else if (node instanceof com.radiologistics.create.node.nodes.ShapeNode sn) {
            String val = sn.getShape().toUpperCase();
            int ph = PROP_H;
            int bx = sx + 4;
            int bw = sw - 8;
            int btnBg = 0xFF5A5D5E;
            int btnHighlight = 0xFF808284;
            int btnShadow = 0xFF383A3B;
            g.fill(bx, py + 2, bx + bw, py + ph - 2, 0xFF141312);
            g.fill(bx + 1, py + 3, bx + bw - 1, py + ph - 3, btnBg);
            g.fill(bx + 1, py + 3, bx + bw - 1, py + 4, btnHighlight);
            g.fill(bx + 1, py + 3, bx + 2, py + ph - 3, btnHighlight);
            g.fill(bx + bw - 2, py + 3, bx + bw - 1, py + ph - 3, btnShadow);
            g.fill(bx + 1, py + ph - 4, bx + bw - 1, py + ph - 3, btnShadow);
            int textCol = 0xFFACAFB0;
            int tw = this.font.width(val);
            g.drawString(this.font, val, bx + (bw - tw) / 2, py + 5, textCol);
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
        } else if (node instanceof com.radiologistics.create.node.nodes.GizmosViewNode gvn) {
            // Mini canvas preview — 80px tall, full node width minus margins
            int canvasX = sx + 4;
            int canvasY = py;
            int canvasW = sw - 8;
            int canvasH = 80;
            // Background
            g.fill(canvasX,     canvasY,     canvasX + canvasW, canvasY + canvasH, 0xFF141312);
            g.fill(canvasX + 1, canvasY + 1, canvasX + canvasW - 1, canvasY + canvasH - 1, 0xFF0A0908);
            if (gvn.getLastCount() == 0) {
                // Empty state
                String lbl = "no signal";
                int lw = this.font.width(lbl);
                g.drawString(this.font, lbl, canvasX + (canvasW - lw) / 2, canvasY + canvasH / 2 - 4, 0xFF444444);
            } else {
                // Draw each gizmo rect scaled to canvas
                for (long[] rect : gvn.getPreviewRects()) {
                    int rx = canvasX + 1 + (int)(rect[0] * (canvasW - 2) / 1000);
                    int ry = canvasY + 1 + (int)(rect[1] * (canvasH - 2) / 1000);
                    int rw = Math.max(1, (int)(rect[2] * (canvasW - 2) / 1000));
                    int rh = Math.max(1, (int)(rect[3] * (canvasH - 2) / 1000));
                    int col = (int) rect[4];
                    g.fill(rx, ry, rx + rw, ry + rh, col);
                }
                // Count label overlay
                String cnt = gvn.getLastCount() + "x";
                g.drawString(this.font, cnt, canvasX + canvasW - this.font.width(cnt) - 3, canvasY + 2, 0xAAB76D55);
            }
        } else if (node instanceof com.radiologistics.create.node.nodes.JammerNode jn) {
            int ph = PROP_H;
            g.fill(sx + 4, py + 2, sx + sw - 4, py + ph - 2, 0xFF141312);
            g.fill(sx + 5, py + 3, sx + sw - 5, py + ph - 3, 0xFF0D0C0B);

            int jamCount = getJammerCount();
            int dist = 150 * jamCount;
            int chans = 5 * jamCount;

            String lang = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
            boolean isUa = lang.toLowerCase().contains("uk_") || lang.toLowerCase().contains("ukr");

            String text = isUa 
                ? "Радіус: " + dist + "м | Канали: " + chans
                : "Radius: " + dist + "m | Chans: " + chans;

            int tw = this.font.width(text);
            if (tw > sw - 12) {
                text = dist + "m | " + chans + " ch";
                tw = this.font.width(text);
            }
            g.drawString(this.font, text, sx + (sw - tw) / 2, py + 5, 0xFFFF5555);
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
            case "comment" -> { if (node instanceof com.radiologistics.create.node.nodes.CommentNode cn) cn.setComment(text); }
        }
    }

    // ─── Mouse ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ponderOpen) {
            handlePonderClick((int)mx, (int)my);
            return true;
        }

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
        } else if (node instanceof com.radiologistics.create.node.nodes.ShapeNode sn) {
            pushUndoState();
            sn.cycle();
        } else if (node instanceof RedstoneOutputNode ron) {
            pushUndoState();
            List<String> sides = List.of("north", "south", "east", "west", "up", "down");
            int idx = sides.indexOf(ron.getSide().toLowerCase());
            ron.setSide(sides.get((idx + 1) % sides.size()));
        } else if (node instanceof TextNode tn) {
            openInlineEdit(node, "text", tn.getText());
        } else if (node instanceof NumberNode nn) {
            openInlineEdit(node, "number", nn.getValueString());
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

        // --- LOGIC ---
        y += 22;
        for (String[] card : LOGIC_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }

        // --- MATHEMATICS ---
        y += 22;
        for (String[] card : MATH_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }

        // --- TEXT OPS ---
        y += 22;
        for (String[] card : TEXT_CARDS) {
            if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
            y += 30;
        }

        // --- SIGNALS ---
        List<String[]> signalsCards = getSignalsCards();
        if (!signalsCards.isEmpty()) {
            y += 22;
            for (String[] card : signalsCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- LINK CONTROLLER ---
        List<String[]> linkControllerCards = getLinkControllerCards();
        if (!linkControllerCards.isEmpty()) {
            y += 22;
            for (String[] card : linkControllerCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- GYROSCOPE ---
        List<String[]> gyroscopeCards = getGyroscopeCards();
        if (!gyroscopeCards.isEmpty()) {
            y += 22;
            for (String[] card : gyroscopeCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- HELMET ---
        List<String[]> helmetCards = getHelmetCards();
        if (!helmetCards.isEmpty()) {
            y += 22;
            for (String[] card : helmetCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- AUDIO ---
        List<String[]> audioCards = getAudioCards();
        if (!audioCards.isEmpty()) {
            y += 22;
            for (String[] card : audioCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- MEDIA ---
        List<String[]> mediaCards = getMediaCards();
        if (!mediaCards.isEmpty()) {
            y += 22;
            for (String[] card : mediaCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- GIZMOS ---
        List<String[]> gizmosCards = getGizmosCards();
        if (!gizmosCards.isEmpty()) {
            y += 22;
            for (String[] card : gizmosCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- CANNON ---
        List<String[]> cannonCards = getCannonCards();
        if (!cannonCards.isEmpty()) {
            y += 22;
            for (String[] card : cannonCards) {
                if (my >= y && my < y + 28) { addNewNode(card[1]); return; }
                y += 30;
            }
        }

        // --- VIEWERS ---
        y += 22;
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
        if (ponderOpen || ponderHoldStart > 0) {
            if (ponderOpen) {
                handlePonderDrag((int)mx, (int)my);
            }
            return true;
        }

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
        if (ponderOpen) {
            isScrubberDragging = false;
            return true;
        }

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
                        AlgoNode srcNode = graph.getNodes().get(wireSrcId);
                        if (srcNode != null) {
                            String srcType = getPortType(srcNode, wireSrcPort, true);
                            String destType = getPortType(node, pn, false);
                            if (!portsAreCompatible(srcType, destType)) {
                                showErrorMessage(net.minecraft.network.chat.Component.translatable("gui.radiologistics.incompatible_ports").getString());
                                return;
                            }
                        }
                        pushUndoState();
                        graph.addLink(wireSrcId, wireSrcPort, node.getId(), pn); return;
                    }
                }
            } else {
                for (String pn : node.getOutputPorts()) {
                    double[] pp = portCanvasPos(node, pn, true);
                    if (dist2(cmx, cmy, pp[0], pp[1]) < 36) {
                        AlgoNode destNode = graph.getNodes().get(wireSrcId);
                        if (destNode != null) {
                            String srcType = getPortType(node, pn, true);
                            String destType = getPortType(destNode, wireSrcPort, false);
                            if (!portsAreCompatible(srcType, destType)) {
                                showErrorMessage(net.minecraft.network.chat.Component.translatable("gui.radiologistics.incompatible_ports").getString());
                                return;
                            }
                        }
                        pushUndoState();
                        graph.addLink(node.getId(), pn, wireSrcId, wireSrcPort); return;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (ponderOpen) {
            return true;
        }

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
        if (ponderOpen) {
            if (keyCode == 256) { // Escape
                closePonder();
            }
            return true;
        }

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

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return super.keyReleased(keyCode, scanCode, modifiers);
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
        
        AlgoNode node;
        if (type.startsWith("display_board_")) {
            String[] parts = type.split("_");
            int bx = Integer.parseInt(parts[2]);
            int by = Integer.parseInt(parts[3]);
            int bz = Integer.parseInt(parts[4]);
            com.radiologistics.create.node.nodes.DisplayBoardNode dbn = new com.radiologistics.create.node.nodes.DisplayBoardNode(id, cx, cy);
            dbn.setBoardPos(new BlockPos(bx, by, bz));
            node = dbn;
        } else {
            node = AlgoNode.createNode(type, id, cx, cy);
        }
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

    /**
     * Returns true when a wire from a port of srcType can feed into a port of destType.
     * Allows implicit conversions: bool↔number, bool→text, number→text.
     */
    private boolean portsAreCompatible(String srcType, String destType) {
        if (srcType.equals("any") || destType.equals("any")) return true;
        if (srcType.equals(destType)) return true;
        // Bool ↔ Number (bool is treated as 0/1 in number context and vice versa)
        if ((srcType.equals("bool") && destType.equals("number"))
                || (srcType.equals("number") && destType.equals("bool"))) return true;
        // Bool → Text  ("true"/"false" representation)
        if (srcType.equals("bool") && destType.equals("text")) return true;
        // Number → Text  (numeric value as string)
        if (srcType.equals("number") && destType.equals("text")) return true;
        // Text → Number / Bool  (parsed at runtime by the receiving node)
        if (srcType.equals("text") && (destType.equals("number") || destType.equals("bool"))) return true;
        return false;
    }

    private String getPortType(AlgoNode node, String port, boolean isOutput) {
        String type = node.getType();
        if (isOutput) {
            if (type.equals("bool") || type.equals("equal") || type.equals("not_equal") 
                    || type.equals("greater_than") || type.equals("less_than")
                    || type.equals("or") || type.equals("and")
                    || type.equals("audio_play")) {
                return "bool";
            }
            if (type.equals("number") || type.equals("add") || type.equals("subtract") 
                    || type.equals("multiply") || type.equals("divide")
                    || type.equals("floor") || type.equals("sqrt") || type.equals("square")
                    || type.equals("atan2") || type.equals("invert") || type.equals("abs")
                    || type.equals("sin") || type.equals("cos") || type.equals("tan") || type.equals("ctg")
                    || type.equals("random") || type.equals("degree_vector")
                    || type.equals("gyroscope") || type.equals("gyroscope_position") || type.equals("link_input")
                    || type.equals("helmet_pos") || type.equals("helmet_rotation")) {
                return "number";
            }
            if (type.equals("text") || type.equals("active_target") || type.equals("display_link") 
                    || type.equals("text_split") || type.equals("text_join")
                    || type.equals("microphone") || type.equals("signal")
                    || type.equals("sound_play") || type.equals("text_speak")) {
                if (type.equals("active_target") && !port.equals("name")) return "number";
                return "text";
            }
            if (type.equals("color_rgb")) {
                return "color";
            }
            if (type.equals("shape")) {
                return "shape";
            }
            if (type.equals("gizmos_2d") || type.equals("gizmos_3d") 
                    || type.equals("gizmos_combine") || type.equals("gizmos_view")) {
                return "gizmos";
            }
            if (type.equals("variable") && node instanceof VariableNode vn) {
                return graph.getVariableTypes().getOrDefault(vn.getVariableName(), "text");
            }
            return "any";
        } else {
            // Inputs
            if (type.equals("audio_play")) {
                if (port.equals("event")) return "bool";
                if (port.equals("stream")) return "text";
                return "any";
            }
            if (type.equals("antenna_output")) {
                if (port.equals("event")) return "bool";
                if (port.equals("message")) return "text";
                if (port.equals("channel")) return "number";
                return "any";
            }
            if (type.equals("signal")) {
                if (port.equals("channel")) return "number";
                return "any";
            }
            if (type.equals("shape")) return "number";
            if (type.equals("sound_play")) {
                // link, volume, pitch
                if (port.equals("link")) return "text";
                return "number";
            }
            if (type.equals("text_speak")) {
                // text, volume, pitch
                if (port.equals("text")) return "text";
                return "number";
            }
            if (type.equals("bool_viewer")) return "bool";
            if (type.equals("number_viewer")) return "number";
            if (type.equals("text_viewer")) return "text";
            if (type.equals("if")) {
                if (port.equals("condition")) return "bool";
                return "any";
            }
            if (type.equals("or") || type.equals("and")) return "bool";
            if (type.equals("greater_than") || type.equals("less_than") 
                    || type.equals("add") || type.equals("subtract") 
                    || type.equals("multiply") || type.equals("divide")
                    || type.equals("floor") || type.equals("sqrt") || type.equals("square")
                    || type.equals("atan2") || type.equals("invert") || type.equals("abs")
                    || type.equals("cos") || type.equals("sin") || type.equals("tan") || type.equals("ctg")
                    || type.equals("random") || type.equals("degree_vector")
                    || type.equals("color_rgb")) {
                return "number";
            }
            // link_output and redstone_output accept bool OR number on their power port
            if (type.equals("redstone_output") || type.equals("link_output")) {
                return "any";
            }
            if (type.equals("set_variable") && node instanceof SetVariableNode svn) {
                if (port.equals("event")) return "bool";
                return graph.getVariableTypes().getOrDefault(svn.getVariableName(), "text");
            }
            if (type.equals("display_link") || type.equals("display_board")) return "text";
            if (type.equals("text_split")) {
                if (port.equals("index")) return "number";
                return "text";
            }
            if (type.equals("text_join")) return "text";
            if (type.equals("gizmos_2d") || type.equals("gizmos_3d")) {
                if (port.equals("shape")) return "shape";
                if (port.equals("color")) return "color";
                if (port.equals("text")) return "text";
                return "number";
            }
            if (type.equals("gizmos_combine") || type.equals("gizmos_view")
                    || type.equals("helmet_screen") || type.equals("screen")) {
                if (port.startsWith("g") || port.equals("gizmos")) return "gizmos";
                return "any";
            }
            return "any";
        }
    }

    private int getPortColor(AlgoNode node, String port, boolean isOutput) {
        String type = getPortType(node, port, isOutput);
        return switch (type) {
            case "bool" -> 0xFFCC8822; // Orange
            case "number" -> 0xFF5090D0; // Blue
            case "text" -> 0xFF4CAF50; // Green
            case "color" -> 0xFFE91E63; // Pink
            case "shape" -> 0xFF9C27B0; // Purple
            case "gizmos" -> 0xFF00FFFF; // Cyan / Bright Blue
            default -> 0xFFCCCCCC; // Grey
        };
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
        int hdr = HDR_H;
        double py = node.getY() + hdr + idx * ROW_H + 11;
        return new double[]{px, py};
    }

    private boolean isInputConnected(String nodeId, String port) {
        if (renderingPonderScene) {
            return currentPonderWires.stream().anyMatch(w -> w.toNode.equals(nodeId) && w.toPort.equals(port));
        }
        return graph.getLinks().stream().anyMatch(l -> l.toNode().equals(nodeId) && l.toPort().equals(port));
    }

    private boolean isOutputConnected(String nodeId, String port) {
        if (renderingPonderScene) {
            return currentPonderWires.stream().anyMatch(w -> w.fromNode.equals(nodeId) && w.fromPort.equals(port));
        }
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
        drawCreatePipeGrow(g, cx1, cy1, cx2, cy2, selected, 1.0);
    }

    private void drawCreatePipeGrow(GuiGraphics g, double cx1, double cy1,
                                    double cx2, double cy2, boolean selected, double growPct) {
        double startX = cx1 + 3;
        double endX = cx2 - 3;

        double dx = Math.abs(endX - startX);
        double dy = Math.abs(cy2 - cy1);
        double dist = Math.sqrt(dx * dx + dy * dy);
        double estLength = dist + (startX > endX ? Math.max(60, dx) : 0);

        int nodeCount = this.graph != null ? this.graph.getNodes().size() : 0;
        double complexityFactor = 1.0 + Math.max(0.0, (nodeCount - 10) / 10.0);
        double stepSize = Math.max(1.0, 1.2 / zoom) * complexityFactor;
        int minSteps = Math.max(4, (int)(16 / complexityFactor));
        int steps = (int) Math.max(minSteps, estLength / stepSize);

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

        int activeSteps = (int)((steps + 1) * growPct);
        if (activeSteps > steps + 1) activeSteps = steps + 1;

        // 1. Draw outline
        for (int i = 0; i < activeSteps; i++) {
            g.fill((int)nxs[i] - 2, (int)nys[i] - 2, (int)nxs[i] + 2, (int)nys[i] + 2, outlineCol);
        }

        // 2. Draw core
        for (int i = 0; i < activeSteps; i++) {
            g.fill((int)nxs[i] - 1, (int)nys[i] - 1, (int)nxs[i] + 1, (int)nys[i] + 1, coreCol);
        }

        // 3. Draw highlight
        for (int i = 0; i < activeSteps; i++) {
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
        if (node instanceof com.radiologistics.create.node.nodes.CommentNode
                || node instanceof HelmetScreenNode) return 0;
        // Pure math/logic operator nodes have no property row
        if (node instanceof EqualNode || node instanceof NotEqualNode
                || node instanceof GreaterThanNode || node instanceof LessThanNode
                || node instanceof AddNode || node instanceof SubtractNode
                || node instanceof MultiplyNode || node instanceof DivideNode
                || node instanceof IfNode || node instanceof OrNode || node instanceof AndNode
                || node instanceof FloorNode || node instanceof SqrtNode || node instanceof SquareNode
                || node instanceof Atan2Node || node instanceof InvertNode || node instanceof AbsNode
                || node instanceof SinNode || node instanceof CosNode || node instanceof TanNode || node instanceof CtgNode
                || node instanceof ActiveTargetNode
                || node instanceof com.radiologistics.create.node.nodes.RandomNode
                || node instanceof com.radiologistics.create.node.nodes.DegreeVectorNode
                || node instanceof DisplayLinkNode
                || node instanceof TextSplitNode
                || node instanceof TextJoinNode
                || node instanceof TextSpeakNode
                || node instanceof SoundPlayNode
                || node instanceof com.radiologistics.create.node.nodes.MicrophoneNode
                || node instanceof com.radiologistics.create.node.nodes.AudioPlayNode
                || node instanceof HelmetPosNode
                || node instanceof HelmetRotationNode
                || node instanceof Gizmos2DNode
                || node instanceof Gizmos3DNode
                || node instanceof GizmosCombineNode
                || node instanceof CameraNode
                || node instanceof ScreenNode
                || node instanceof DisplayBoardNode
                || node instanceof com.radiologistics.create.node.nodes.CannonRotNode
                || node instanceof com.radiologistics.create.node.nodes.GizmosViewNode) return 0;
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
            case "or","and"               -> 0xFFB76D55; // Copper
            case "add","subtract","multiply","divide" -> 0xFF7F8485; // Andesite
            case "floor","sqrt","square"  -> 0xFF7F8485; // Andesite
            case "atan2"                  -> 0xFF7F8485; // Andesite
            case "invert","abs"           -> 0xFF7F8485; // Andesite
            case "sin","cos","tan","ctg","random","degree_vector"  -> 0xFF7F8485; // Andesite
            case "if"                     -> 0xFFB76D55; // Copper
            case "redstone_output"        -> 0xFF9E2A2B; // Redstone
            case "jammer"                 -> 0xFF9E2A2B; // Jammer
            case "link_input","link_output" -> 0xFFD2691E; // Industrial Orange
            case "gyroscope","gyroscope_position" -> 0xFF5C3C6B; // Obsidian/Purple
            case "antenna_output" -> 0xFF4F6E80; // Wireless/Zinc
            case "variable","set_variable"-> 0xFFC97C8E; // Rose Quartz
            case "active_target"          -> 0xFF5C3C6B; // Target/Obsidian
            case "bool_viewer"            -> 0xFFB76D55; // Copper
            case "number_viewer"          -> 0xFF7F8485; // Andesite
            case "text_viewer"            -> 0xFFC8963E; // Brass
            case "display_link"           -> 0xFFC8963E; // Brass
            case "text_split","text_join","text_speak","sound_play","microphone","audio_play" -> 0xFFC8963E; // Brass
            case "helmet_pos","helmet_rotation","helmet_screen" -> 0xFFC8963E;
            case "camera","screen","display_board" -> 0xFFC8963E;
            case "cannon_rot"             -> 0xFF5C3C6B;
            case "gizmos_2d","gizmos_3d","gizmos_combine","gizmos_view" -> 0xFFB76D55;
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
            case "and"             -> "And";
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
            case "random"          -> "Random";
            case "degree_vector"   -> "∠ Deg Vector";
            case "if"              -> "If";
            case "redstone_output" -> "RS Output";
            case "link_input"      -> "Link Input";
            case "link_output"     -> "Link Output";
            case "gyroscope"       -> "Gyroscope";
            case "gyroscope_position" -> "Gyro Position";
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
            case "text_speak"      -> "Text Stream";
            case "sound_play"      -> "Link Stream";
            case "microphone"      -> "Microphone";
            case "audio_play"      -> "Audio Play";
            case "helmet_pos"      -> "Helmet Pos";
            case "helmet_rotation" -> "Helmet Rot";
            case "gizmos_2d"       -> "Gizmos 2D";
            case "gizmos_3d"       -> "Gizmos 3D";
            case "gizmos_combine"  -> "Gizmos Combine";
            case "gizmos_view"     -> "Gizmos View";
            case "helmet_screen"   -> "Helmet Screen";
            case "camera"          -> "Cassette Reader";
            case "cannon_rot"      -> "Cannon Rot";
            case "screen"          -> "Screen Node";
            case "display_board"   -> "Display Board";
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
        } else if (node instanceof com.radiologistics.create.node.nodes.GizmosViewNode) {
            // Extra tall to hold the 80px mini canvas preview
            int maxP = Math.max(node.getInputPorts().size(), node.getOutputPorts().size());
            return nhH + 4 + maxP * ROW_H + 84;
        } else {
            List<String> ins  = node.getInputPorts();
            List<String> outs = node.getOutputPorts();
            int maxP  = Math.max(ins.size(), outs.size());
            int prCnt = propRowCount(node);
            return nhH + 4 + maxP * ROW_H + (prCnt > 0 ? (prCnt * PROP_H + 2) : 0) + 6;
        }
    }

    // ─── Ponder Data Structures ───────────────────────────────────────────────
    public static class PonderNode {
        public String type;
        public String id;
        public String valueLabel;
        public int x, y;
        public boolean active = false;
        public float alpha = 1.0f;
        public List<String> inputs = new ArrayList<>();
        public List<String> outputs = new ArrayList<>();

        public PonderNode(String type, String id, String valueLabel, int x, int y) {
            this.type = type;
            this.id = id;
            this.valueLabel = valueLabel;
            this.x = x;
            this.y = y;
            AlgoNode dummy = AlgoNode.createNode(type, id, 0, 0);
            if (dummy != null) {
                this.inputs.addAll(dummy.getInputPorts());
                this.outputs.addAll(dummy.getOutputPorts());
            }
        }
    }

    public static class PonderWire {
        public String fromNode, fromPort;
        public String toNode, toPort;
        public boolean pulse = false;
        public int color;

        public PonderWire(String fromNode, String fromPort, String toNode, String toPort, int color, boolean pulse) {
            this.fromNode = fromNode;
            this.fromPort = fromPort;
            this.toNode = toNode;
            this.toPort = toPort;
            this.color = color;
            this.pulse = pulse;
        }
    }

    public static class PonderBubble {
        public String text;
        public int targetX = -1, targetY = -1;
        public String targetNodeId = null;
        public String targetPort = null;
        public boolean isOutputPort = false;
        public double startPct, endPct;

        public PonderBubble(String text, int targetX, int targetY, double startPct, double endPct) {
            this.text = text;
            this.targetX = targetX;
            this.targetY = targetY;
            this.startPct = startPct;
            this.endPct = endPct;
        }

        public PonderBubble(String text, String targetNodeId, double startPct, double endPct) {
            this.text = text;
            this.targetNodeId = targetNodeId;
            this.startPct = startPct;
            this.endPct = endPct;
        }

        public PonderBubble(String text, String targetNodeId, String targetPort, boolean isOutputPort, double startPct, double endPct) {
            this.text = text;
            this.targetNodeId = targetNodeId;
            this.targetPort = targetPort;
            this.isOutputPort = isOutputPort;
            this.startPct = startPct;
            this.endPct = endPct;
        }
    }

    public static class PonderChapter {
        public String title;
        public String description;
        public List<PonderNode> nodes = new ArrayList<>();
        public List<PonderWire> wires = new ArrayList<>();
        public List<PonderBubble> bubbles = new ArrayList<>();

        public PonderChapter(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    // ─── Concise Descriptions Registry ─────────────────────────────────────────
    private String getConciseDescription(String type, boolean isUa) {
        return switch (type) {
            case "signal" -> isUa 
                ? "Зчитує силу та значення бездротового радіосигналу на вибраному каналі. Використовується для побудови систем віддаленого контролю та радіоприймачів."
                : "Reads wireless signal strength and value on the selected channel. Used to build remote monitoring systems and radio receivers.";
            case "bool" -> isUa 
                ? "Логічна константа (TRUE/FALSE). Використовується для ручного встановлення перемикачів, активації умов чи блокувальних ліній."
                : "A constant boolean (TRUE/FALSE). Used to manually set toggle switches, conditional triggers, or override logic lines.";
            case "number" -> isUa 
                ? "Числова константа (підтримує десяткові дроби). Використовується для статичних налаштувань лімітів швидкості, затримок чи координат."
                : "A constant numeric value (supports decimals). Used to hardcode limits, speed variables, coordinate offsets, or timers.";
            case "text" -> isUa 
                ? "Текстова константа. Використовується для вказання назв радіоканалів, глобальних змінних, підписів на табло або повідомлень TTS."
                : "A constant text string. Used to specify radio channel names, global variables, display labels, or TTS speech lines.";
            case "add" -> isUa 
                ? "Додає два числа (A + B). Використовується для зміщення координат, об'єднання показників сенсорів або розрахунку дальності цілі."
                : "Adds two numbers (A + B). Used to offset coordinates, combine sensor readings, or calculate target distances.";
            case "subtract" -> isUa 
                ? "Віднімає B від A (A - B). Використовується для вимірювання відхилень кутів, відносного положення або розрахунку відстані між об'єктами."
                : "Subtracts B from A (A - B). Used to calculate relative positions, coordinate differences, or distance between objects.";
            case "multiply" -> isUa 
                ? "Множить два вхідні числа (A * B). Корисно для масштабування чутливості керування, переведення одиниць виміру або підсилення сигналу."
                : "Multiplies two numbers (A * B). Useful for scaling control sensitivity, converting units, or multiplying signal strengths.";
            case "divide" -> isUa 
                ? "Ділить число A на B (A / B). Використовується для нормалізації показників, розрахунку частот або пропорційного розподілу значень."
                : "Divides A by B (A / B). Used to normalize values, compute ratios, or scale coordinates proportionally.";
            case "equal" -> isUa 
                ? "Порівнює два значення на рівність, повертаючи TRUE якщо вони збігаються. Використовується для підтвердження досягнення цільової точки."
                : "Compares two values for equality, returning TRUE if they match. Used to check if a specific state or target coordinate is reached.";
            case "not_equal" -> isUa 
                ? "Повертає TRUE, якщо вхідні значення відрізняються. Корисно для тригерів виявлення змін та автоматичного скидання систем."
                : "Returns TRUE if inputs are different. Useful for change-detection triggers or automated system reset logic.";
            case "greater_than" -> isUa 
                ? "Повертає TRUE, якщо A більше за B. Використовується для порогів безпеки (наприклад, перевищення висоти чи небезпечна швидкість)."
                : "Returns TRUE if A is greater than B. Used for threshold triggers like altitude limits, overload safety, or range checks.";
            case "less_than" -> isUa 
                ? "Повертає TRUE, якщо A менше за B. Використовується для сигналізаторів низького заряду, перевірки близькості ворогів або мінімумів."
                : "Returns TRUE if A is less than B. Used for low-fuel/energy warning triggers, close proximity checks, or minimum thresholds.";
            case "or" -> isUa 
                ? "Логічне АБО двох Bool-значень. Повертає TRUE, якщо хоча б одна з умов виконується. Зручно для об'єднання кількох аварійних датчиків."
                : "Logical OR of two Bool values. Returns TRUE if at least one input is TRUE. Useful for merging multiple emergency alarm lines.";
            case "and" -> isUa 
                ? "Логічне І двох Bool-значень. Повертає TRUE, якщо ВСІ умови виконуються. Використовується як запобіжник у двофакторних системах запуску."
                : "Logical AND of two Bool values. Returns TRUE only if all inputs are TRUE. Used for multi-condition safety interlocks.";
            case "if" -> isUa 
                ? "Пропускає значення, якщо логічна умова є TRUE, інакше вихід залишається порожнім. Дозволяє створювати умовні розгалуження в програмах."
                : "Passes the input value if the condition is TRUE, otherwise blocks it. Allows creating conditional logic routing in programs.";
            case "floor" -> isUa 
                ? "Округлює число вниз до найближчого цілого. Використовується для прив'язки координат до сітки блоків перед відправкою команд."
                : "Rounds the number down to the nearest integer. Used to align coordinates to block grids before triggering actions.";
            case "sqrt" -> isUa 
                ? "Обчислює квадратний корінь числа. Необхідно для розрахунку гіпотенуз, євклідових відстаней між точками за формулою Піфагора."
                : "Calculates the square root of a number. Crucial for Euclidean distance formula and geometric math.";
            case "square" -> isUa 
                ? "Зводит число у квадрат (A^2). Використовується у розрахунках дальності та квадратичних прискорень."
                : "Squares the input value (A^2). Commonly used in distance mathematics and quadratic scaling.";
            case "abs" -> isUa 
                ? "Повертає абсолютне (додатне) значення числа (|A|). Дозволяє вимірювати лінійне відхилення незалежно від напрямку руху."
                : "Returns the absolute (positive) value of a number. Allows measuring delta offset sizes regardless of positive/negative direction.";
            case "invert" -> isUa 
                ? "Змінює знак числа на протилежний (множить на -1). Використовується для реверсування осей руху, зміни ліворуч/праворуч."
                : "Inverts the sign of the input number. Used to reverse control axis directions or mirror coordinates.";
            case "sin" -> isUa 
                ? "Обчислює синус кута (у градусах). Дозволяє генерувати плавні хвильові рухи, проектувати вектори або створювати автоколивні системи."
                : "Calculates the sine of an angle in degrees. Used to program wave movements, project vectors, or create oscillating automations.";
            case "cos" -> isUa 
                ? "Обчислює косинус кута (у градусах). Необхідно для розрахунку горизонтальних проекцій та кругових траєкторій руху."
                : "Calculates the cosine of an angle in degrees. Essential for computing directional coordinates and circular movements.";
            case "tan" -> isUa 
                ? "Обчислює тангенс кута. Використовується в геометрії та тригонометричному масштабуванні."
                : "Calculates the tangent of an angle in degrees. Used in advanced geometry and trigonometric positioning.";
            case "ctg" -> isUa 
                ? "Обчислює котангенс кута. Корисно для розширених просторових розрахунків."
                : "Calculates the cotangent of an angle in degrees. Used in specialized geometry calculations.";
            case "atan2" -> isUa 
                ? "Обчислює кут напрямку у градусах за координатами Y та X. Необхідно для наведення турелей та радарів на цілі."
                : "Calculates the direction angle in degrees from Y and X coords. Essential for pointing turrets and radars at specific targets.";
            case "random" -> isUa 
                ? "Генерує випадкове число у вказаному діапазоні. Застосовується для хаотичного патрулювання, шумів або випадкових інтервалів."
                : "Generates a random number within limits. Used for random turret sweep patterns, delays, or decorative graphic noise.";
            case "degree_vector" -> isUa 
                ? "Обчислює найкоротшу кутову різницю (-180..180 градусів) між двома напрямками. Запобігає обертанню турелі на 360 градусів."
                : "Calculates the shortest angle delta (-180 to 180 degrees) between angles. Crucial for smooth, short turret rotation sweeps.";
            case "variable" -> isUa 
                ? "Зчитує значення глобальної змінної за її назвою. Дозволяє передавати дані між різними комп'ютерними програмами."
                : "Reads the value of a global variable by name. Enables sharing states and telemetry between different computers.";
            case "set_variable" -> isUa 
                ? "Записує значення у глобальну змінну. Використовується для запам'ятовування станів, накопичення лічильників та збереження цілей."
                : "Writes a value to a global variable. Used to store states, increment counters, or remember target coordinates.";
            case "bool_viewer" -> isUa 
                ? "Відображає логічний стан порту у вигляді кольорового світлодіода. Допомагає візуально відлагоджувати логіку роботи."
                : "Displays boolean port status as a colored indicator light. Helpful for debugging logical flows in real-time.";
            case "number_viewer" -> isUa 
                ? "Відображає числове значення порту на панелі ноди. Використовується для моніторингу сенсорів та обчислень в реальному часі."
                : "Displays the numeric port value directly on the node. Used to monitor coordinates, speeds, or calculations.";
            case "text_viewer" -> isUa 
                ? "Відображає текстовий рядок порту. Зручно для швидкої перевірки каналів, імен змінних або вихідних повідомлень."
                : "Displays text port value. Convenient for verifying channel names, active variables, or output message content.";
            case "text_split" -> isUa 
                ? "Розділяє вхідний текст за обраним символом та видає шматок під вказаним індексом. Декодує складні пакети даних."
                : "Splits text by separator.";
            case "text_join" -> isUa 
                ? "Об'єднує два тексти в один. Дозволяє динамічно збирати інформаційні рядки, наприклад: 'Висота: ' + число."
                : "Joins two text strings.";
            case "text_speak" -> isUa 
                ? "Створює TTS (Text-to-Speech) голосовий потік з тексту. Дозволяє комп'ютеру озвучувати тривоги та інструкції."
                : "Generates a TTS (Text-to-Speech) voice stream from text. Allows the computer to speak alarms or instructions in-world.";
            case "sound_play" -> isUa 
                ? "Завантажує зовнішній аудіопотік з MP3 URL-посилання. Дозволяє транслювати сирени, радіо або звукові ефекти."
                : "Loads an external audio stream from an MP3 URL. Allows broadcasting sirens, music streams, or voice warnings.";
            case "microphone" -> isUa 
                ? "Зчитує голосове мовлення з мікрофонів гравців навколо комп'ютера. Дозволяє створювати системи зв'язку та рації."
                : "Captures in-game voice chat from players near the microphone block. Used to build intercoms or radio systems.";
            case "audio_play" -> isUa 
                ? "Керує підключеним модулем Audio Play для трансляції аудіопотоку у світ. Підтримує голос гравців, TTS та інтернет-радіо."
                : "Controls the linked Audio Play block, playing the audio stream. Supports player voice, TTS streams, and MP3 links.";
            case "link_input" -> isUa 
                ? "Отримує бездротовий сигнал червоного каменю від мережі Create Redstone Link. Створює віддалені вимикачі."
                : "Receives wireless signals from the Create Redstone Link network. Enables wireless remote controller logic.";
            case "link_output" -> isUa 
                ? "Надсилає бездротовий сигнал у мережу Create Redstone Link. Дозволяє комп'ютеру дистанційно активувати механізми."
                : "Sends wireless signals to the Create Redstone Link network. Allows the computer to trigger remote machines.";
            case "antenna_output" -> isUa 
                ? "Надсилає структуровані пакети даних через підключену антену на вказаний радіоканал. Основа бездротових радарів."
                : "Transmits structured data packets on a radio channel via the linked Antenna module. Critical for wireless coordinates transmission.";
            case "redstone_output" -> isUa 
                ? "Подає аналоговий сигнал червоного каменю (0-15) на фізичні порти комп'ютера. Використовується для плавного керування двигунами."
                : "Outputs analog redstone power (0-15) to the computer's ports. Used to control speed levels or signal indicator lamps.";
            case "gyroscope" -> isUa 
                ? "Вимірює поточні кути нахилу (Pitch, Yaw, Roll) летючого корабля чи машини. Необхідно для систем автопілоту та автостабілізації."
                : "Measures current rotation angles (Pitch, Yaw, Roll) of a moving ship or carriage. Needed for stabilization flight systems.";
            case "gyroscope_position" -> isUa 
                ? "Вимірює поточні координати XYZ рухомої платформи. Потрібно для навігації та автоматичного повернення транспортів додому."
                : "Reads the current XYZ coordinates of a moving platform. Used for vehicle navigation and automated autopilot pathfinding.";
            case "active_target" -> isUa 
                ? "Зчитує координати цілі, захопленої радаром Create Radars. Використовується для автоматичного наведення зброї."
                : "Reads target coordinate tracking from a Create Radar. Critical for automated defense systems and target tracking.";
            case "jammer" -> isUa 
                ? "Керує підключеним модулем Jammer для придушення радіочастот навколо. Захищає бази від ворожого сканування."
                : "Controls the linked Jammer block. Jams and disables nearby wireless signals to protect against remote tracking.";
            case "screen" -> isUa 
                ? "Передає графічний інтерфейс та об'єкти на підключений прозорий екран Transparent Screen."
                : "Sends graphic layouts and camera video feeds to the linked Transparent Screen block.";
            case "gizmos_2d" -> isUa 
                ? "Створює групу плоских 2D фігур (кола, текст, лінії) у піксельних координатах. Малює приціли, інтерфейси та карти."
                : "Creates flat 2D overlays (shapes, text, lines) using screen coordinates. Used to design target reticles or maps.";
            case "gizmos_3d" -> isUa 
                ? "Створює просторові 3D маркери та рамки навколо точок у світі. Позначає цілі, межі чанків та небезпечні зони."
                : "Creates 3D bounding boxes and line markers anchored in the world. Displays target markers or waypoint lines.";
            case "gizmos_combine" -> isUa 
                ? "Об'єднує декілька графічних груп (наприклад, 2D приціл та 3D маркери) в один пакет для виведення на екран."
                : "Combines multiple graphics groups (e.g. 2D overlay and 3D coordinate boxes) into a single packet to display.";
            case "gizmos_view" -> isUa 
                ? "Створює віджет трансляції зображення з камери Vista. Дозволяє виводити камери спостереження на монітори бази."
                : "Creates a Camera Feed element using Vista camera streams to display live surveillance feeds on base monitors.";
            case "helmet_pos" -> isUa 
                ? "Визначає координати XYZ гравця, який носить шолом. Використовується для фокусування систем захисту на гравцеві."
                : "Reads current coordinates of the player wearing the linked Pilot Helmet. Used to follow or protect the player.";
            case "helmet_rotation" -> isUa 
                ? "Зчитує напрямок погляду гравця в шоломі (Yaw, Pitch). Дозволяє повертати камери та турелі слідом за головою пілота."
                : "Measures view angles of the player wearing the helmet. Perfect for head-tracking cameras and follow-mouse turrets.";
            case "helmet_screen" -> isUa 
                ? "Надсилає графічний інтерфейс прямо на HUD-дисплей шолома пілота. Незамінно для окулярів нічного бачення та радарів шолома."
                : "Transmits HUD layout graphics and video feeds directly to the player's Pilot Helmet screen overlay.";
            case "color_rgb" -> isUa 
                ? "Створює код кольору із значень Red, Green, Blue (0-255). Використовується для динамічного перефарбування прицілів."
                : "Generates a color code from Red, Green, and Blue values (0-255). Useful for custom GUI color themes and alert indicators.";
            case "shape" -> isUa 
                ? "Створює опис графічного примітиву (прямокутник, коло, лінія, текст) для малювання у 2D гізмосах."
                : "Defines a visual primitive (rect, circle, line, text) with settings. Passed to 2D gizmos node to render.";
            case "camera" -> isUa 
                ? "Керує камерою Vista (Zoom, Pitch, Yaw). Використовується для систем віддаленого відеоспостереження та стеження за цілями."
                : "Controls Vista Camera properties (Zoom, Pitch, Yaw). Used for remote surveillance, zoom sweeps, and follow targets.";
            case "cannon_rot" -> isUa 
                ? "Визначає кути наведення та команду пострілу для Cannon Mount з Create Big Cannons. Автоматизує артилерію."
                : "Calculates targeting angles and firing commands for Create Big Cannons Mount blocks. Used to build auto-turrets.";
            case "display_board" -> isUa 
                ? "Виводить текст на інформаційне табло Create Display Board. Використовується для вокзальних табло та дисплеїв стану реакторів."
                : "Sends text to a Create Display Board. Perfect for train station arrival boards or reactor status displays.";
            case "display_link" -> isUa 
                ? "Дозволяє комп'ютеру бути джерелом для Create Display Link, передаючи довільні дані на табло."
                : "Acts as a source for Create Display Links, sending custom values to reader boards.";
            case "pos_to_rot" -> isUa 
                ? "Перетворює різницю координат XYZ у кути Pitch та Yaw. Потрібно для обчислення наведення турелей на координати цілі."
                : "Converts coordinate difference vector into Pitch and Yaw angles. Essential for pointing turrets at coordinates.";
            case "rot_to_pos" -> isUa 
                ? "Конвертує кути Pitch та Yaw у тривимірний напрямний вектор. Дозволяє розраховувати промені та траєкторії польоту."
                : "Converts Pitch and Yaw angles into a 3D direction vector. Useful for raycasting and forward trajectory paths.";
            case "comment" -> isUa 
                ? "Блок текстової замітки на робочій області. Допомагає структурувати та коментувати складні логічні схеми нод."
                : "A text note block on the workspace canvas. Used to comment and organize large, complex node networks.";
            default -> {
                String title = net.minecraft.network.chat.Component.translatable("radiologistics.node_ponder." + type + ".title").getString();
                if (title.startsWith("radiologistics.")) title = nodeTitle(type);
                yield isUa ? "Блок " + title + ". Обробляє дані у вашій логічній схемі." : title + " block. Processes data in your logic network.";
            }
        };
    }

    private String getProcessingText(String type, boolean isUa) {
        return switch (type) {
            case "number", "bool", "text" -> isUa ? "Константа фіксує та утримує задане значення на виході." : "The constant block registers and outputs a fixed value.";
            case "comment" -> isUa ? "Замітка відображає підказку або документацію в редакторі." : "The comment block displays a design guide or documentation.";
            case "bool_viewer", "number_viewer", "text_viewer" -> isUa ? "Візуалізатор відображає отримані дані в режимі реального часу." : "The viewer displays the received telemetry data in real-time.";
            case "variable" -> isUa ? "Зчитує поточний стан з комірки глобальної пам'яті комп'ютера." : "Reads the current state from the computer's global memory cell.";
            case "set_variable" -> isUa ? "Записує вхідні дані в глобальну пам'ять під вказаним ключем." : "Overwrites the computer's global memory cell with the new value.";
            case "sound_play" -> isUa ? "Завантажує та транслює потокове аудіо через вказане посилання." : "Decodes and streams direct audio from the specified web link.";
            case "text_speak" -> isUa ? "Перетворює отриманий текст на потік голосового мовлення (TTS)." : "Synthesizes the received text into a voice stream (TTS).";
            case "microphone" -> isUa ? "Записує голос гравців навколо та надсилає його в мережу." : "Captures vocal input from nearby players to route into the stream.";
            case "audio_play" -> isUa ? "Відтворює отриманий голосовий або звуковий потік у навколишній світ." : "Streams the received audio feed into the surrounding game world.";
            case "redstone_output" -> isUa ? "Комп'ютер генерує фізичний аналоговий редстоун-сигнал на своїх портах." : "The computer generates a physical analog redstone output on its ports.";
            case "antenna_output" -> isUa ? "Комп'ютер транслює радіопакет через антену в ефір." : "The computer broadcasts the data packet wirelessly via the antenna.";
            case "link_output" -> isUa ? "Передає логічний сигнал у бездротову мережу редстоун лінків Create." : "Transmits the state into Create's wireless redstone link network.";
            case "link_input" -> isUa ? "Зчитує поточний стан з бездротової мережі редстоун лінків Create." : "Receives the state from Create's wireless redstone link network.";
            case "jammer" -> isUa ? "Глушник генерує сильні радіоперешкоди, відключаючи приймачі та лінки." : "The jammer block outputs high-energy radio noise, disabling receivers.";
            case "display_link" -> isUa ? "Транслює інформацію для відображення на табло або nixie-трубках." : "Broadcasts the output telemetry to nixie tubes or reader boards.";
            case "gyroscope" -> isUa ? "Гіроскоп фіксує кути обертання та крену рухомої конструкції." : "The gyroscope measures rotation angles of the moving carriage.";
            case "gyroscope_position" -> isUa ? "Сенсор відстежує точні координати конструкції на карті." : "The coordinate sensor tracks the platform's position in the world.";
            case "active_target" -> isUa ? "Радар видає координати супротивника або цілі в радіусі сканування." : "The targeting computer outputs the tracked enemy target's position.";
            case "signal" -> isUa ? "Приймач зчитує силу та корисне навантаження радіосигналу." : "The receiver measures radio channel signal strength and data content.";
            case "camera" -> isUa ? "Змінює кути нахилу камери та масштабує об'єктив." : "Updates view finder rotation angles and adjusts camera zoom levels.";
            case "cannon_rot" -> isUa ? "Направляє гармату на ціль та активує спусковий механізм." : "Positions the cannon mount at the target and fires a shot.";
            case "display_board" -> isUa ? "Виводить сформований текст на інформаційне табло." : "Displays the text layout on the mechanical information board.";
            case "screen", "helmet_screen" -> isUa ? "Формує фінальне зображення та відправляє його на дисплей." : "Compiles the graphics layout and sends it to the visual display.";
            case "add", "subtract", "multiply", "divide",
                 "floor", "sqrt", "square", "atan2", "invert", "abs",
                 "sin", "cos", "tan", "ctg", "random", "degree_vector",
                 "equal", "not_equal", "greater_than", "less_than",
                 "or", "and", "if", "pos_to_rot", "rot_to_pos", "color_rgb", "shape",
                 "gizmos_2d", "gizmos_3d", "gizmos_combine", "gizmos_view",
                 "helmet_pos", "helmet_rotation" -> isUa ? "Вузол обробляє вхідні сигнали та виконує логіко-математичний такт." : "The node processes inputs and executes a logic/math operation.";
            default -> isUa ? "Вузол виконує логічний крок обробки даних." : "The node executes a data processing step.";
        };
    }

    // ─── Ponder Storyboard Initialization ──────────────────────────────────────
    private void initPonderChapters(String type, AlgoNode openNode) {
        currentPonderChapters.clear();
        
        String lang = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        boolean isUa = lang.toLowerCase().contains("uk_") || lang.toLowerCase().contains("ukr");
        
        AlgoNode dummy = AlgoNode.createNode(type, "main", 0, 0);
        if (dummy == null) return;
        
        if (openNode != null) {
            if (dummy instanceof com.radiologistics.create.node.nodes.TextNode tn && openNode instanceof com.radiologistics.create.node.nodes.TextNode otn) {
                tn.setText(otn.getText());
            } else if (dummy instanceof com.radiologistics.create.node.nodes.NumberNode nn && openNode instanceof com.radiologistics.create.node.nodes.NumberNode onn) {
                nn.setValue(onn.getValue());
            } else if (dummy instanceof com.radiologistics.create.node.nodes.BoolNode bn && openNode instanceof com.radiologistics.create.node.nodes.BoolNode obn) {
                bn.setValue(obn.getValue());
            } else if (dummy instanceof com.radiologistics.create.node.nodes.VariableNode vn && openNode instanceof com.radiologistics.create.node.nodes.VariableNode ovn) {
                vn.setVariableName(ovn.getVariableName());
            } else if (dummy instanceof com.radiologistics.create.node.nodes.SetVariableNode svn && openNode instanceof com.radiologistics.create.node.nodes.SetVariableNode osvn) {
                svn.setVariableName(osvn.getVariableName());
            }
        } else {
            if (dummy instanceof com.radiologistics.create.node.nodes.TextNode tn) tn.setText("Hello");
            else if (dummy instanceof com.radiologistics.create.node.nodes.NumberNode nn) nn.setValue(5.0);
            else if (dummy instanceof com.radiologistics.create.node.nodes.BoolNode bn) bn.setValue(false);
            else if (dummy instanceof com.radiologistics.create.node.nodes.VariableNode vn) vn.setVariableName("speed");
            else if (dummy instanceof com.radiologistics.create.node.nodes.SetVariableNode svn) svn.setVariableName("speed");
        }
        
        int nh = nodeHeight(dummy);
        int mainX = 185;
        int mainY = 95 - nh/2;
        
        List<String> ins = dummy.getInputPorts();
        List<String> outs = dummy.getOutputPorts();
        
        String title = net.minecraft.network.chat.Component.translatable("radiologistics.node_ponder." + type + ".title").getString();
        if (title.startsWith("radiologistics.")) title = nodeTitle(type);
        
        String compat = net.minecraft.network.chat.Component.translatable("radiologistics.node_ponder." + type + ".compat").getString();
        if (compat.startsWith("radiologistics.")) compat = isUa ? "Будь-який сумісний блок" : "Any compatible node";

        // Define the 4 sections of our timeline
        PonderChapter ch1 = new PonderChapter(isUa ? "Огляд" : "Overview", "");
        PonderChapter ch2 = new PonderChapter(isUa ? "Підключення" : "Connections", "");
        PonderChapter ch3 = new PonderChapter(isUa ? "Робота блоку" : "In Action", "");
        PonderChapter ch4 = new PonderChapter(isUa ? "Сумісність" : "Applications", "");
        
        // Add nodes to master chapter data structure (ch1)
        PonderNode mNode = new PonderNode(type, "main", getValLabel(dummy), mainX, mainY);
        ch1.nodes.add(mNode);
        
        // Build inputs (left: x = 12)
        int inSpacing = ins.size() > 3 ? 42 : 44;
        for (int i = 0; i < ins.size(); i++) {
            String portName = ins.get(i);
            int py = 95 - (ins.size() * inSpacing)/2 + i * inSpacing + 10;
            String inType = getInputNodeType(dummy, portName);
            PonderNode inNode = new PonderNode(inType, "in_" + i, getValLabelForType(inType, true), 12, py);
            ch1.nodes.add(inNode);
            ch1.wires.add(new PonderWire("in_" + i, "value", "main", portName, getPortColor(dummy, portName, false), true));
        }
        
        // Build outputs (right: x = 358)
        int outSpacing = outs.size() > 3 ? 42 : 44;
        for (int j = 0; j < outs.size(); j++) {
            String portName = outs.get(j);
            int py = 95 - (outs.size() * outSpacing)/2 + j * outSpacing + 10;
            String outType = getOutputNodeType(dummy, portName);
            PonderNode outNode = new PonderNode(outType, "out_" + j, getValLabelForType(outType, false), 358, py);
            ch1.nodes.add(outNode);
            ch1.wires.add(new PonderWire("main", portName, "out_" + j, "value", getPortColor(dummy, portName, true), true));
        }
        
        String conciseDesc = getConciseDescription(type, isUa);
        
        // Section 1 bubbles (0.0 to 0.25)
        ch1.bubbles.add(new PonderBubble(conciseDesc, "main", 0.0, 0.25));
        
        // Section 2 bubbles (0.25 to 0.50)
        if (!ins.isEmpty()) {
            ch1.bubbles.add(new PonderBubble(
                isUa ? "Вхідні порти (мідне кільце)." : "Input ports (copper collar).",
                "main", ins.get(0), false, 0.25, 0.50));
        }
        if (!outs.isEmpty()) {
            ch1.bubbles.add(new PonderBubble(
                isUa ? "Вихідні порти (латунне кільце)." : "Output ports (brass collar).",
                "main", outs.get(0), true, 0.38, 0.50));
        }
        if (ins.isEmpty() && outs.isEmpty()) {
            ch1.bubbles.add(new PonderBubble(
                isUa ? "Блок не має вхідних чи вихідних портів." : "The block has no inputs or outputs.",
                "main", 0.25, 0.50));
        }
        
        // Section 3 bubbles (0.50 to 0.75)
        if (!ins.isEmpty()) {
            ch1.bubbles.add(new PonderBubble(
                isUa ? "Вхідні значення змінюються." : "Input values update.",
                "in_0", 0.50, 0.75));
        }
        ch1.bubbles.add(new PonderBubble(
            getProcessingText(type, isUa),
            "main", 0.58, 0.75));
        if (!outs.isEmpty()) {
            ch1.bubbles.add(new PonderBubble(
                isUa ? "Результат передається далі." : "The result is transmitted out.",
                "out_0", 0.66, 0.75));
        }
        
        // Section 4 bubbles (0.75 to 1.00)
        ch1.bubbles.add(new PonderBubble(
            (isUa ? "Сумісність: " : "Compatible with: ") + compat,
            "main", 0.75, 1.0));
            
        currentPonderChapters.add(ch1);
        currentPonderChapters.add(ch2);
        currentPonderChapters.add(ch3);
        currentPonderChapters.add(ch4);
    }

    private String getValLabel(AlgoNode node) {
        if (node instanceof com.radiologistics.create.node.nodes.TextNode tn) return "\"" + tn.getText() + "\"";
        if (node instanceof com.radiologistics.create.node.nodes.NumberNode nn) return nn.getValueString();
        if (node instanceof com.radiologistics.create.node.nodes.BoolNode bn) return bn.getValue() ? "TRUE" : "FALSE";
        if (node instanceof com.radiologistics.create.node.nodes.VariableNode vn) return vn.getVariableName();
        if (node instanceof com.radiologistics.create.node.nodes.SetVariableNode svn) return svn.getVariableName();
        return "";
    }

    private String getValLabelForType(String type, boolean isSource) {
        if (type.equals("number")) return isSource ? "5.0" : "0.0";
        if (type.equals("bool")) return isSource ? "TRUE" : "FALSE";
        if (type.equals("text")) return "\"A\"";
        return "";
    }

    private String getInputNodeType(AlgoNode main, String portName) {
        int color = getPortColor(main, portName, false);
        if (color == 0xFFCC8822) return "bool";
        if (color == 0xFF4CAF50) return "text";
        if (color == 0xFFE91E63) return "color_rgb";
        if (color == 0xFF9C27B0) return "shape";
        if (color == 0xFF00FFFF) return "gizmos_2d";
        return "number";
    }

    private String getOutputNodeType(AlgoNode main, String portName) {
        int color = getPortColor(main, portName, true);
        if (color == 0xFFCC8822) return "bool_viewer";
        if (color == 0xFF4CAF50) return "text_viewer";
        if (color == 0xFF00FFFF) return "screen";
        return "number_viewer";
    }

    // ─── Ponder Animation Logic ────────────────────────────────────────────────
    // ─── Ponder Animation Logic ────────────────────────────────────────────────
    private void animateChapterNodes(PonderChapter masterChapter, float t) {
        PonderNode main = null;
        for (PonderNode pn : masterChapter.nodes) {
            if (pn.id.equals("main")) {
                main = pn;
                break;
            }
        }
        if (main == null) return;
        
        main.active = false;
        for (PonderNode pn : masterChapter.nodes) {
            if (pn.id.startsWith("out_")) pn.active = false;
        }

        // Recreate the main AlgoNode to get its input ports and evaluate values
        AlgoNode mainAlgoNode = AlgoNode.createNode(main.type, "main", 0, 0);
        if (mainAlgoNode != null && ponderOpenNode != null) {
            if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.TextNode tn && ponderOpenNode instanceof com.radiologistics.create.node.nodes.TextNode otn) {
                tn.setText(otn.getText());
            } else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.NumberNode nn && ponderOpenNode instanceof com.radiologistics.create.node.nodes.NumberNode onn) {
                nn.setValue(onn.getValue());
            } else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.BoolNode bn && ponderOpenNode instanceof com.radiologistics.create.node.nodes.BoolNode obn) {
                bn.setValue(obn.getValue());
            } else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.VariableNode vn && ponderOpenNode instanceof com.radiologistics.create.node.nodes.VariableNode ovn) {
                vn.setVariableName(ovn.getVariableName());
            } else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.SetVariableNode svn && ponderOpenNode instanceof com.radiologistics.create.node.nodes.SetVariableNode osvn) {
                svn.setVariableName(osvn.getVariableName());
            }
        } else if (mainAlgoNode != null) {
            if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.TextNode tn) tn.setText("Hello");
            else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.NumberNode nn) nn.setValue(5.0);
            else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.BoolNode bn) bn.setValue(false);
            else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.VariableNode vn) vn.setVariableName("speed");
            else if (mainAlgoNode instanceof com.radiologistics.create.node.nodes.SetVariableNode svn) svn.setVariableName("speed");
        }

        if (t < 0.25f) {
            for (PonderNode pn : masterChapter.nodes) {
                if (pn.id.startsWith("in_")) {
                    pn.valueLabel = getValLabelForType(pn.type, true);
                }
                if (pn.id.startsWith("out_")) {
                    pn.valueLabel = getValLabelForType(pn.type, false);
                }
            }
        } else if (t >= 0.25f && t < 0.50f) {
            for (PonderNode pn : masterChapter.nodes) {
                if (pn.id.startsWith("in_")) {
                    pn.valueLabel = getValLabelForType(pn.type, true);
                }
                if (pn.id.startsWith("out_")) {
                    pn.valueLabel = getValLabelForType(pn.type, false);
                }
            }
        } else if (t >= 0.50f && t < 0.75f) {
            float t_local = (t - 0.50f) / 0.25f;
            if (t_local < 0.35f) {
                for (PonderNode pn : masterChapter.nodes) {
                    if (pn.id.startsWith("in_")) {
                        pn.valueLabel = getValLabelForType(pn.type, true);
                    }
                    if (pn.id.startsWith("out_")) {
                        pn.valueLabel = getValLabelForType(pn.type, false);
                    }
                }
            } else if (t_local >= 0.35f && t_local < 0.7f) {
                main.active = true;
                for (PonderNode pn : masterChapter.nodes) {
                    if (pn.id.startsWith("in_")) {
                        if (pn.id.equals("in_0")) {
                            if (pn.type.equals("number")) pn.valueLabel = "8.0";
                            else if (pn.type.equals("bool")) pn.valueLabel = "FALSE";
                            else if (pn.type.equals("text")) pn.valueLabel = "\"B\"";
                        } else {
                            pn.valueLabel = getValLabelForType(pn.type, true);
                        }
                    }
                    if (pn.id.startsWith("out_")) {
                        pn.valueLabel = getValLabelForType(pn.type, false);
                    }
                }
            } else {
                for (PonderNode pn : masterChapter.nodes) {
                    if (pn.id.startsWith("in_")) {
                        if (pn.id.equals("in_0")) {
                            if (pn.type.equals("number")) pn.valueLabel = "8.0";
                            else if (pn.type.equals("bool")) pn.valueLabel = "FALSE";
                            else if (pn.type.equals("text")) pn.valueLabel = "\"B\"";
                        } else {
                            pn.valueLabel = getValLabelForType(pn.type, true);
                        }
                    }
                }
                Map<String, Object> inputValues = new HashMap<>();
                if (mainAlgoNode != null) {
                    List<String> ins = mainAlgoNode.getInputPorts();
                    for (int i = 0; i < ins.size(); i++) {
                        String portName = ins.get(i);
                        PonderNode inNode = null;
                        for (PonderNode pn : masterChapter.nodes) {
                            if (pn.id.equals("in_" + i)) {
                                inNode = pn;
                                break;
                            }
                        }
                        if (inNode != null) {
                            Object val = parseValueLabel(inNode.valueLabel);
                            if (val != null) inputValues.put(portName, val);
                        }
                    }
                }
                for (PonderNode pn : masterChapter.nodes) {
                    if (pn.id.startsWith("out_")) {
                        pn.active = true;
                        if (mainAlgoNode != null) {
                            try {
                                int outIdx = Integer.parseInt(pn.id.substring(4));
                                List<String> outs = mainAlgoNode.getOutputPorts();
                                if (outIdx >= 0 && outIdx < outs.size()) {
                                    String outPort = outs.get(outIdx);
                                    Object res = mainAlgoNode.evaluate(outPort, inputValues, null);
                                    pn.valueLabel = valueToString(res);
                                }
                            } catch (Exception e) {
                                if (pn.type.equals("number_viewer")) pn.valueLabel = "8.0";
                                else if (pn.type.equals("bool_viewer")) pn.valueLabel = "TRUE";
                                else if (pn.type.equals("text_viewer")) pn.valueLabel = "\"B\"";
                            }
                        } else {
                            if (pn.type.equals("number_viewer")) pn.valueLabel = "8.0";
                            else if (pn.type.equals("bool_viewer")) pn.valueLabel = "TRUE";
                            else if (pn.type.equals("text_viewer")) pn.valueLabel = "\"B\"";
                        }
                    }
                }
            }
        } else {
            // Applications phase
            main.active = false;
            for (PonderNode pn : masterChapter.nodes) {
                if (pn.id.startsWith("in_")) {
                    if (pn.id.equals("in_0")) {
                        if (pn.type.equals("number")) pn.valueLabel = "8.0";
                        else if (pn.type.equals("bool")) pn.valueLabel = "FALSE";
                        else if (pn.type.equals("text")) pn.valueLabel = "\"B\"";
                    } else {
                        pn.valueLabel = getValLabelForType(pn.type, true);
                    }
                }
            }
            Map<String, Object> inputValues = new HashMap<>();
            if (mainAlgoNode != null) {
                List<String> ins = mainAlgoNode.getInputPorts();
                for (int i = 0; i < ins.size(); i++) {
                    String portName = ins.get(i);
                    PonderNode inNode = null;
                    for (PonderNode pn : masterChapter.nodes) {
                        if (pn.id.equals("in_" + i)) {
                            inNode = pn;
                            break;
                        }
                    }
                    if (inNode != null) {
                        Object val = parseValueLabel(inNode.valueLabel);
                        if (val != null) inputValues.put(portName, val);
                    }
                }
            }
            for (PonderNode pn : masterChapter.nodes) {
                if (pn.id.startsWith("out_")) {
                    pn.active = false;
                    if (mainAlgoNode != null) {
                        try {
                            int outIdx = Integer.parseInt(pn.id.substring(4));
                            List<String> outs = mainAlgoNode.getOutputPorts();
                            if (outIdx >= 0 && outIdx < outs.size()) {
                                String outPort = outs.get(outIdx);
                                Object res = mainAlgoNode.evaluate(outPort, inputValues, null);
                                pn.valueLabel = valueToString(res);
                            }
                        } catch (Exception e) {
                            if (pn.type.equals("number_viewer")) pn.valueLabel = "8.0";
                            else if (pn.type.equals("bool_viewer")) pn.valueLabel = "TRUE";
                            else if (pn.type.equals("text_viewer")) pn.valueLabel = "\"B\"";
                        }
                    } else {
                        if (pn.type.equals("number_viewer")) pn.valueLabel = "8.0";
                        else if (pn.type.equals("bool_viewer")) pn.valueLabel = "TRUE";
                        else if (pn.type.equals("text_viewer")) pn.valueLabel = "\"B\"";
                    }
                }
            }
        }
    }

    private Object parseValueLabel(String label) {
        if (label == null || label.isEmpty()) return null;
        if (label.startsWith("\"") && label.endsWith("\"") && label.length() >= 2) {
            return label.substring(1, label.length() - 1);
        }
        if ("TRUE".equalsIgnoreCase(label)) return Boolean.TRUE;
        if ("FALSE".equalsIgnoreCase(label)) return Boolean.FALSE;
        try {
            return Double.parseDouble(label);
        } catch (NumberFormatException e) {
            return label;
        }
    }

    private String valueToString(Object val) {
        if (val == null) return "";
        if (val instanceof Boolean b) return b ? "TRUE" : "FALSE";
        if (val instanceof Number num) {
            double d = num.doubleValue();
            if (d == (long) d) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (val instanceof String s) {
            return "\"" + s + "\"";
        }
        return String.valueOf(val);
    }

    // ─── Ponder Node Renderer ──────────────────────────────────────────────────
    private void renderPonderNode(GuiGraphics g, PonderNode pn, float parentAlpha, float zLevel) {
        if (pn.alpha <= 0.0f) return;
        AlgoNode dummy = AlgoNode.createNode(pn.type, pn.id, pn.x, pn.y);
        if (dummy == null) return;
        
        if (dummy instanceof com.radiologistics.create.node.nodes.TextNode tn) {
            String label = pn.valueLabel;
            if (label.startsWith("\"") && label.endsWith("\"") && label.length() >= 2) {
                label = label.substring(1, label.length() - 1);
            }
            tn.setText(label);
        } else if (dummy instanceof com.radiologistics.create.node.nodes.NumberNode nn) {
            try {
                nn.setValue(Double.parseDouble(pn.valueLabel));
            } catch (Exception e) {}
        } else if (dummy instanceof com.radiologistics.create.node.nodes.BoolNode bn) {
            bn.setValue("TRUE".equalsIgnoreCase(pn.valueLabel));
        } else if (dummy instanceof com.radiologistics.create.node.nodes.VariableNode vn) {
            vn.setVariableName(pn.valueLabel);
        } else if (dummy instanceof com.radiologistics.create.node.nodes.SetVariableNode svn) {
            svn.setVariableName(pn.valueLabel);
        } else if (dummy instanceof com.radiologistics.create.node.nodes.BoolViewerNode bvn) {
            String label = pn.valueLabel;
            if (label != null && !label.isEmpty()) {
                bvn.evaluate("value", Map.of("value", "TRUE".equalsIgnoreCase(label)), null);
            }
        } else if (dummy instanceof com.radiologistics.create.node.nodes.NumberViewerNode nvn) {
            String label = pn.valueLabel;
            if (label != null && !label.isEmpty()) {
                nvn.evaluate("value", Map.of("value", label), null);
            }
        } else if (dummy instanceof com.radiologistics.create.node.nodes.TextViewerNode tvn) {
            String label = pn.valueLabel;
            if (label != null && !label.isEmpty()) {
                if (label.startsWith("\"") && label.endsWith("\"") && label.length() >= 2) {
                    label = label.substring(1, label.length() - 1);
                }
                tvn.evaluate("value", Map.of("value", label), null);
            }
        }
        
        renderingPonderScene = true;
        
        boolean oldSelected = selectedNodeIds.contains(dummy.getId());
        if (pn.active) {
            selectedNodeIds.add(dummy.getId());
        }
        
        // Render node at FULL opacity so fills completely block any text drawn by other nodes.
        // This fixes text bleed-through: semi-transparent fills cannot cover other nodes' text.
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        renderNode(g, dummy, zLevel);
        
        // Apply fade-in effect by overlaying a semi-transparent dark rect over the node.
        // This gives the same visual appearance as fading the node itself, but the
        // underlying fills were drawn at full opacity so no text bleed-through occurs.
        float effectiveAlpha = pn.alpha * parentAlpha;
        if (effectiveAlpha < 0.99f) {
            g.flush();
            int fadeAlpha = (int)((1.0f - effectiveAlpha) * 208); // max 0xD0 to match backdrop
            if (fadeAlpha > 0) {
                int nh2 = nodeHeight(dummy);
                // The fade rect is drawn in the SAME coordinate space as renderNode used
                // (pose translated to pn.x, pn.y — we re-translate here)
                g.pose().pushPose();
                g.pose().translate(pn.x, pn.y, zLevel + 0.5f);
                g.fill(0, 0, NODE_W, nh2, (fadeAlpha << 24) | 0x101114);
                g.flush();
                g.pose().popPose();
            }
        }
        
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        if (pn.active && !oldSelected) {
            selectedNodeIds.remove(dummy.getId());
        }
        
        renderingPonderScene = false;
    }

    // ─── Ponder Wires & Signal Flow Renderer ──────────────────────────────────
    private void renderPonderWires(GuiGraphics g, PonderChapter masterChapter, float t, float t_local, double growPct, float alpha, float zLevel) {
        currentPonderWires = masterChapter.wires;
        renderingPonderScene = true;
        
        g.pose().pushPose();
        g.pose().translate(0, 0, zLevel);
        
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        
        for (PonderWire w : masterChapter.wires) {
            PonderNode from = null, to = null;
            for (PonderNode pn : masterChapter.nodes) {
                if (pn.id.equals(w.fromNode)) from = pn;
                if (pn.id.equals(w.toNode)) to = pn;
            }
            if (from == null || to == null) continue;
            
            AlgoNode dummyFrom = AlgoNode.createNode(from.type, from.id, from.x, from.y);
            AlgoNode dummyTo = AlgoNode.createNode(to.type, to.id, to.x, to.y);
            if (dummyFrom == null || dummyTo == null) continue;
            
            double[] start = portCanvasPos(dummyFrom, w.fromPort, true);
            double[] end = portCanvasPos(dummyTo, w.toPort, false);
            
            boolean isRs = w.color == 0xFFCCCCCC || dummyFrom instanceof com.radiologistics.create.node.nodes.SignalNode || dummyTo instanceof com.radiologistics.create.node.nodes.SignalNode;
            if (isRs) {
                drawCreatePipeGrow(g, start[0], start[1], end[0], end[1], false, growPct);
            } else {
                drawCreatePipeGrow(g, start[0], start[1], end[0], end[1], w.pulse, growPct);
            }
            
            if (w.pulse && (t >= 0.50f && t < 0.75f) && growPct >= 1.0) {
                drawWireParticle(g, start[0], start[1], end[0], end[1], t_local, w.color);
            }
        }
        
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.pose().popPose();
        renderingPonderScene = false;
    }

    private void renderPonderOverlay(GuiGraphics g, int mx, int my) {
        if (currentPonderChapters.isEmpty()) return;
        PonderChapter masterChapter = currentPonderChapters.get(0);
        
        // Background dimmed backdrop fade-in over 300ms
        double elapsedFromOpen = System.currentTimeMillis() - ponderOpenTime;
        double bgAlphaPct = Math.min(1.0, elapsedFromOpen / 300.0);
        int bgAlpha = (int)(0xD0 * bgAlphaPct);
        g.fill(0, 0, this.width, this.height, (bgAlpha << 24) | 0x101114);
        
        float overlayAlpha = (float)bgAlphaPct;
        
        int pw = 500;
        int vh = 190;
        
        // Calculate scaling if screen width is narrow
        float scale = 1.0f;
        int minMargin = 20;
        if (this.width < pw + minMargin * 2) {
            scale = (float)(this.width - minMargin * 2) / pw;
        }
        int scaledPw = (int)(pw * scale);
        int scaledVh = (int)(vh * scale);
        int px = (this.width - scaledPw) / 2;
        int py = (this.height - (scaledVh + (int)(35 * scale))) / 2;
        if (py < 16) py = 16;
        
        int localMx = (int)((mx - px) / scale);
        int localMy = (int)((my - py) / scale);
        
        String headerTitle = net.minecraft.network.chat.Component.translatable("radiologistics.node_ponder." + ponderOpenType + ".title").getString();
        if (headerTitle.startsWith("radiologistics.")) headerTitle = nodeTitle(ponderOpenType);
        
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, overlayAlpha);
        
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(scale, scale, 1.0f);
        
        g.drawString(this.font, "📖 Ponder: " + headerTitle, 2, -12, 0xFFFFD700);
        
        boolean hoverClose = localMx >= pw - 15 && localMx < pw && localMy >= -15 && localMy < 0;
        g.drawString(this.font, "✕", pw - 12, -12, hoverClose ? 0xFFFF5555 : 0xFFACAFB0);
        
        drawInsetPanel(g, 0, 0, pw, vh, THEME_ANDESITE);
        
        // Inner grid background
        g.fill(1, 1, pw - 1, vh - 1, 0xFF111214);
        for (int gx = 15; gx < pw - 1; gx += 15) {
            g.fill(gx, 1, gx + 1, vh - 1, 0xFF1C1D20);
        }
        for (int gy = 15; gy < vh - 1; gy += 15) {
            g.fill(1, gy, pw - 1, gy + 1, 0xFF1C1D20);
        }
        
        float t = (float)(ponderTimelineElapsedTime / TOTAL_TIMELINE_DURATION_MS);
        t = Math.max(0.0f, Math.min(1.0f, t));
        double t_ms = ponderTimelineElapsedTime;
        
        // Determine layout dimensions
        AlgoNode mainDummy = AlgoNode.createNode(ponderOpenType, "main", 0, 0);
        int nh = mainDummy != null ? nodeHeight(mainDummy) : 40;
        int mainX = 185;
        int mainY = 95 - nh/2;
        
        // Animating positions: sliding in from side/bottom
        int currentMainY = mainY;
        float mainAlpha = 1.0f;
        if (t_ms < 1000.0) {
            double progress = t_ms / 1000.0;
            double ease = 1.0 - Math.pow(1.0 - progress, 3);
            currentMainY = (int)(mainY + 40 * (1.0 - ease));
            mainAlpha = (float)progress;
        }
        
        int currentInX = 12;
        int currentOutX = 358;
        float sideAlpha = 1.0f;
        if (t_ms >= 6000.0 && t_ms < 7000.0) {
            double progress = (t_ms - 6000.0) / 1000.0;
            double ease = 1.0 - Math.pow(1.0 - progress, 3);
            currentInX = (int)(-140 + (12 - (-140)) * ease);
            currentOutX = (int)(510 + (358 - 510) * ease);
            sideAlpha = (float)progress;
        } else if (t_ms < 6000.0) {
            currentInX = -140;
            currentOutX = 510;
            sideAlpha = 0.0f;
        } else {
            currentInX = 12;
            currentOutX = 358;
            sideAlpha = 1.0f;
        }
        
        // Apply animated positions and alpha to chapter node objects
        for (PonderNode pn : masterChapter.nodes) {
            if (pn.id.equals("main")) {
                pn.y = currentMainY;
                pn.alpha = mainAlpha;
            } else if (pn.id.startsWith("in_")) {
                pn.x = currentInX;
                pn.alpha = sideAlpha;
            } else if (pn.id.startsWith("out_")) {
                pn.x = currentOutX;
                pn.alpha = sideAlpha;
            }
        }
        
        double growPct = 1.0;
        if (t_ms < 6000.0) {
            growPct = 0.0;
        } else if (t_ms >= 6000.0 && t_ms < 12000.0) {
            if (t_ms < 7000.0) {
                growPct = 0.0;
            } else if (t_ms < 8500.0) {
                double linearPct = (t_ms - 7000.0) / 1500.0;
                growPct = 1.0 - Math.pow(1.0 - linearPct, 3);
            }
        }
        
        float t_local = 0.0f;
        if (t >= 0.50f && t < 0.75f) {
            t_local = (t - 0.50f) / 0.25f;
        } else if (t >= 0.75f) {
            t_local = 1.0f;
        }
        
        animateChapterNodes(masterChapter, t);
        
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        g.enableScissor(px, py, px + (int)(pw * scale), py + (int)(vh * scale));
        g.flush();
        
        if (t_ms >= 6000.0) {
            renderPonderWires(g, masterChapter, t, t_local, growPct, overlayAlpha, 5.0f);
        }
        
        for (PonderNode pn : masterChapter.nodes) {
            if (pn.id.equals("main")) continue;
            if (t_ms < 6000.0) continue;
            renderPonderNode(g, pn, overlayAlpha, 10.0f);
        }
        for (PonderNode pn : masterChapter.nodes) {
            if (!pn.id.equals("main")) continue;
            renderPonderNode(g, pn, overlayAlpha, 20.0f);
        }
        
        // Resolve Word Bubbles overlap layout dynamically
        class BubbleLayout {
            final PonderBubble bubble;
            int bx, by, bw, bh;
            BubbleLayout(PonderBubble bubble, int bx, int by, int bw, int bh) {
                this.bubble = bubble;
                this.bx = bx;
                this.by = by;
                this.bw = bw;
                this.bh = bh;
            }
        }
        
        List<BubbleLayout> activeLayouts = new ArrayList<>();
        for (PonderBubble b : masterChapter.bubbles) {
            if (t >= b.startPct && t <= b.endPct) {
                int tx = b.targetX;
                int ty = b.targetY;
                if (b.targetNodeId != null) {
                    PonderNode targetNode = null;
                    for (PonderNode pn : masterChapter.nodes) {
                        if (pn.id.equals(b.targetNodeId)) {
                            targetNode = pn;
                            break;
                        }
                    }
                    if (targetNode != null) {
                        AlgoNode dummy = AlgoNode.createNode(targetNode.type, targetNode.id, targetNode.x, targetNode.y);
                        int nhNode = dummy != null ? nodeHeight(dummy) : 40;
                        if (b.targetPort != null) {
                            double[] pp = portCanvasPos(dummy, b.targetPort, b.isOutputPort);
                            tx = (int)pp[0];
                            ty = (int)pp[1];
                        } else {
                            tx = targetNode.x + NODE_W / 2;
                            ty = targetNode.y + nhNode / 2;
                        }
                    }
                }
                if (tx == -1 || ty == -1) continue;
                
                int maxW = 125;
                List<String> lines = splitComment(b.text, maxW - 12);
                int bwVal = maxW;
                int bhVal = lines.size() * 10 + 10;
                
                int bxVal;
                if (tx > 250) {
                    bxVal = tx - bwVal - 50;
                } else {
                    bxVal = tx + 50;
                }
                int byVal = ty - bhVal / 2;
                
                bxVal = Math.max(10, Math.min(500 - bwVal - 10, bxVal));
                byVal = Math.max(10, Math.min(190 - bhVal - 10, byVal));
                
                activeLayouts.add(new BubbleLayout(b, bxVal, byVal, bwVal, bhVal));
            }
        }
        
        activeLayouts.sort(Comparator.comparingInt(l -> l.by));
        for (int i = 0; i < activeLayouts.size(); i++) {
            BubbleLayout current = activeLayouts.get(i);
            boolean hasOverlap;
            int attempts = 0;
            do {
                hasOverlap = false;
                for (int j = 0; j < i; j++) {
                    BubbleLayout other = activeLayouts.get(j);
                    boolean xOverlap = current.bx < other.bx + other.bw && current.bx + current.bw > other.bx;
                    boolean yOverlap = current.by < other.by + other.bh && current.by + current.bh > other.by;
                    if (xOverlap && yOverlap) {
                        current.by = other.by + other.bh + 4;
                        hasOverlap = true;
                    }
                }
                attempts++;
            } while (hasOverlap && attempts < 10);
            
            if (current.by + current.bh > 180) {
                current.by = 180 - current.bh;
                for (int j = i - 1; j >= 0; j--) {
                    BubbleLayout other = activeLayouts.get(j);
                    boolean xOverlap = current.bx < other.bx + other.bw && current.bx + current.bw > other.bx;
                    boolean yOverlap = current.by < other.by + other.bh && current.by + current.bh > other.by;
                    if (xOverlap && yOverlap) {
                        current.by = other.by - current.bh - 4;
                    }
                }
                if (current.by < 10) current.by = 10;
            }
        }
        
        for (BubbleLayout layout : activeLayouts) {
            renderPonderBubble(g, masterChapter, layout.bubble, layout.bx, layout.by, t, overlayAlpha);
        }
        
        g.flush();
        g.disableScissor();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        
        // Draw timeline controls relative to local space
        int cy = vh + 6;
        
        // Row 1: Full width progress bar
        int sx = 8;
        int sw = 484;
        g.fill(sx, cy + 4, sx + sw, cy + 6, 0xFF141312);
        
        // Chapter divider dots on timeline
        g.fill(sx + sw/4 - 1, cy + 3, sx + sw/4 + 1, cy + 7, 0xFF5F5A57);
        g.fill(sx + sw/2 - 1, cy + 3, sx + sw/2 + 1, cy + 7, 0xFF5F5A57);
        g.fill(sx + 3*sw/4 - 1, cy + 3, sx + 3*sw/4 + 1, cy + 7, 0xFF5F5A57);
        
        int progressW = (int) (sw * t);
        g.fill(sx, cy + 4, sx + progressW, cy + 6, 0xFFC8963E);
        
        int kx = sx + progressW;
        g.fill(kx - 2, cy + 2, kx + 2, cy + 8, 0xFFFFD700);
        
        // Row 2: Play/Pause, Rewind, and Scene switcher below timeline
        int cy2 = cy + 13;
        
        boolean hoverPlay = localMx >= 8 && localMx < 20 && localMy >= cy2 && localMy < cy2 + 12;
        String playIcon = ponderPaused ? "▶" : "‖";
        g.drawString(this.font, playIcon, 8, cy2 + 1, hoverPlay ? 0xFFFFFFFF : 0xFFACAFB0);
        
        boolean hoverRewind = localMx >= 24 && localMx < 36 && localMy >= cy2 && localMy < cy2 + 12;
        g.drawString(this.font, "⏪", 24, cy2 + 1, hoverRewind ? 0xFFFFFFFF : 0xFFACAFB0);
        
        int activeSec = Math.max(0, Math.min(3, (int)(t * 4)));
        PonderChapter currentSec = currentPonderChapters.get(activeSec);
        String chLabel = (activeSec + 1) + "/4: " + currentSec.title;
        int lblW = this.font.width(chLabel);
        
        // Centered switcher
        int switcherW = lblW + 34;
        int switcherX = (pw - switcherW) / 2;
        
        boolean hoverPrev = localMx >= switcherX && localMx < switcherX + 12 && localMy >= cy2 && localMy < cy2 + 12;
        g.drawString(this.font, "◀", switcherX + 2, cy2 + 1, hoverPrev ? 0xFFFFFFFF : 0xFFACAFB0);
        
        g.drawString(this.font, chLabel, switcherX + 16, cy2 + 1, 0xFFE9C583);
        
        boolean hoverNext = localMx >= switcherX + 16 + lblW + 6 && localMx < switcherX + 16 + lblW + 18 && localMy >= cy2 && localMy < cy2 + 12;
        g.drawString(this.font, "▶", switcherX + 16 + lblW + 6, cy2 + 1, hoverNext ? 0xFFFFFFFF : 0xFFACAFB0);
        
        g.pose().popPose();
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawWireParticle(GuiGraphics g, double x1, double y1, double x2, double y2, float t_local, int color) {
        boolean isInput = x2 > 100 && x2 < 260;
        float progress = 0.0f;
        boolean draw = false;
        
        if (isInput) {
            if (t_local <= 0.5f) {
                progress = t_local / 0.5f;
                draw = true;
            }
        } else {
            if (t_local >= 0.5f) {
                progress = (t_local - 0.5f) / 0.5f;
                draw = true;
            }
        }
        
        if (draw) {
            double dx = Math.abs(x2 - x1), co = Math.max(30, dx / 2);
            double cx1 = x1 + co, cy1 = y1, cx2 = x2 - co, cy2 = y2;
            
            double mt = 1.0 - progress;
            double px = mt*mt*mt*x1 + 3*mt*mt*progress*cx1 + 3*mt*progress*progress*cx2 + progress*progress*progress*x2;
            double py = mt*mt*mt*y1 + 3*mt*mt*progress*cy1 + 3*mt*progress*progress*cy2 + progress*progress*progress*y2;
            
            g.fill((int)px - 3, (int)py - 3, (int)px + 3, (int)py + 3, color);
            g.fill((int)px - 1, (int)py - 1, (int)px + 1, (int)py + 1, 0xFFFFFFFF);
        }
    }

    private void renderPonderBubble(GuiGraphics g, PonderChapter masterChapter, PonderBubble bubble, int bx, int by, float t, float parentAlpha) {
        int tx = bubble.targetX;
        int ty = bubble.targetY;
        
        if (bubble.targetNodeId != null) {
            PonderNode targetNode = null;
            for (PonderNode pn : masterChapter.nodes) {
                if (pn.id.equals(bubble.targetNodeId)) {
                    targetNode = pn;
                    break;
                }
            }
            if (targetNode != null) {
                AlgoNode dummy = AlgoNode.createNode(targetNode.type, targetNode.id, targetNode.x, targetNode.y);
                int nh = dummy != null ? nodeHeight(dummy) : 40;
                
                if (bubble.targetPort != null) {
                    double[] pp = portCanvasPos(dummy, bubble.targetPort, bubble.isOutputPort);
                    tx = (int)pp[0];
                    ty = (int)pp[1];
                } else {
                    tx = targetNode.x + NODE_W / 2;
                    ty = targetNode.y + nh / 2;
                }
            }
        }
        
        if (tx == -1 || ty == -1) return;
        
        int maxW = 125;
        List<String> lines = splitComment(bubble.text, maxW - 12);
        int bw = maxW;
        int bh = lines.size() * 10 + 10;
        
        double bubbleStartPct = bubble.startPct;
        double bubbleStartMs = bubbleStartPct * 24000.0;
        double elapsedBubble = (t * 24000.0) - bubbleStartMs;
        
        double bubbleEndMs = bubble.endPct * 24000.0;
        double remainingMs = bubbleEndMs - (t * 24000.0);
        
        double fadeInProgress = Math.min(1.0, elapsedBubble / 500.0);
        double fadeOutProgress = Math.min(1.0, remainingMs / 300.0);
        float alpha = (float) Math.max(0.0, Math.min(fadeInProgress, fadeOutProgress));
        
        if (alpha <= 0.0f) return;
        
        double slideProgress = Math.min(1.0, elapsedBubble / 500.0);
        double slideEase = 1.0 - Math.pow(1.0 - slideProgress, 3);
        
        int finalBx = bx;
        if (slideEase < 1.0) {
            if (tx > 250) {
                finalBx = (int)(bx - 20 * (1.0 - slideEase));
            } else {
                finalBx = (int)(bx + 20 * (1.0 - slideEase));
            }
        }
        
        int lx = (tx > 205) ? finalBx + bw : finalBx;
        
        g.pose().pushPose();
        g.pose().translate(0, 0, 30.0f);
        
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * parentAlpha);
        
        // Crisp horizontal single-pixel connection line
        g.fill(Math.min(lx, tx), ty, Math.max(lx, tx), ty + 1, 0x80C8963E);
        
        // Vertical step line to the bubble center Y if shifted
        int bubbleCenterY = by + bh / 2;
        if (ty != bubbleCenterY) {
            g.fill(lx, Math.min(ty, bubbleCenterY), lx + 1, Math.max(ty, bubbleCenterY), 0x80C8963E);
        }
        
        // Gold locator circle
        g.fill(tx - 2, ty - 2, tx + 3, ty + 3, 0xFF141312);
        g.fill(tx - 1, ty - 1, tx + 2, ty + 2, 0xFFFFD700);
        g.fill(tx, ty, tx + 1, ty + 1, 0xFF110F0E);
        
        // Bubble box styling
        g.fill(finalBx, by, finalBx + bw, by + bh, 0xFF141312);
        g.fill(finalBx + 1, by + 1, finalBx + bw - 1, by + bh - 1, 0xFF2C2D2F);
        
        g.fill(finalBx + 1, by + 1, finalBx + bw - 1, by + 2, 0xFFFFD700);
        g.fill(finalBx + 1, by + 1, finalBx + 2, by + bh - 1, 0xFFFFD700);
        g.fill(finalBx + bw - 2, by + 2, finalBx + bw - 1, by + bh - 2, 0xFF8C5F1C);
        g.fill(finalBx + 1, by + bh - 2, finalBx + bw - 1, by + bh - 1, 0xFF8C5F1C);
        
        int textY = by + 5;
        for (String line : lines) {
            g.drawString(this.font, line, finalBx + 6, textY, 0xFFEEEEEE);
            textY += 10;
        }
        
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.pose().popPose();
    }

    // ─── Ponder Input Click and Drag Managers ──────────────────────────────────
    private void handlePonderClick(int mx, int my) {
        if (currentPonderChapters.isEmpty()) return;
        
        int pw = 500;
        int vh = 190;
        
        float scale = 1.0f;
        int minMargin = 20;
        if (this.width < pw + minMargin * 2) {
            scale = (float)(this.width - minMargin * 2) / pw;
        }
        int scaledPw = (int)(pw * scale);
        int scaledVh = (int)(vh * scale);
        int px = (this.width - scaledPw) / 2;
        int py = (this.height - (scaledVh + (int)(35 * scale))) / 2;
        if (py < 16) py = 16;
        
        int localMx = (int)((mx - px) / scale);
        int localMy = (int)((my - py) / scale);
        
        boolean inClose = localMx >= pw - 15 && localMx < pw && localMy >= -15 && localMy < 0;
        boolean inWindow = localMx >= 0 && localMx < pw && localMy >= 0 && localMy < vh + 35;
        
        if (inClose || !inWindow) {
            closePonder();
            return;
        }
        
        int cy = vh + 6;
        int cy2 = cy + 13;
        
        // Play button click
        if (localMx >= 8 && localMx < 20 && localMy >= cy2 && localMy < cy2 + 12) {
            ponderPaused = !ponderPaused;
            if (!ponderPaused) {
                ponderLastUpdateNano = System.nanoTime();
            }
            return;
        }
        
        // Rewind button click
        if (localMx >= 24 && localMx < 36 && localMy >= cy2 && localMy < cy2 + 12) {
            ponderTimelineElapsedTime = 0.0;
            return;
        }
        
        // Scrubber click/drag start
        int sx = 8;
        int sw = 484;
        if (localMx >= sx && localMx < sx + sw && localMy >= cy + 1 && localMy < cy + 11) {
            isScrubberDragging = true;
            handlePonderDrag(mx, my);
            return;
        }
        
        float t = (float)(ponderTimelineElapsedTime / TOTAL_TIMELINE_DURATION_MS);
        int activeSec = Math.max(0, Math.min(3, (int)(t * 4)));
        PonderChapter currentSec = currentPonderChapters.get(activeSec);
        String chLabel = (activeSec + 1) + "/4: " + currentSec.title;
        int lblW = this.font.width(chLabel);
        int switcherW = lblW + 34;
        int switcherX = (pw - switcherW) / 2;
        
        // Prev button click
        if (localMx >= switcherX && localMx < switcherX + 12 && localMy >= cy2 && localMy < cy2 + 12) {
            double secStart = activeSec * 6000.0;
            if (ponderTimelineElapsedTime - secStart < 500.0) {
                activeSec = (activeSec - 1 + 4) % 4;
            }
            ponderTimelineElapsedTime = activeSec * 6000.0;
            ponderPaused = false;
            ponderLastUpdateNano = System.nanoTime();
            return;
        }
        
        // Next button click
        if (localMx >= switcherX + 16 + lblW + 6 && localMx < switcherX + 16 + lblW + 18 && localMy >= cy2 && localMy < cy2 + 12) {
            activeSec = (activeSec + 1) % 4;
            ponderTimelineElapsedTime = activeSec * 6000.0;
            ponderPaused = false;
            ponderLastUpdateNano = System.nanoTime();
            return;
        }
    }

    private void handlePonderDrag(int mx, int my) {
        if (!isScrubberDragging || currentPonderChapters.isEmpty()) return;
        
        int pw = 500;
        int vh = 190;
        
        float scale = 1.0f;
        int minMargin = 20;
        if (this.width < pw + minMargin * 2) {
            scale = (float)(this.width - minMargin * 2) / pw;
        }
        int scaledPw = (int)(pw * scale);
        int scaledVh = (int)(vh * scale);
        int px = (this.width - scaledPw) / 2;
        int py = (this.height - (scaledVh + (int)(35 * scale))) / 2;
        if (py < 16) py = 16;
        
        int localMx = (int)((mx - px) / scale);
        
        int sx = 8;
        int sw = 484;
        
        double progress = (double)(localMx - sx) / sw;
        progress = Math.max(0.0, Math.min(1.0, progress));
        
        ponderTimelineElapsedTime = progress * TOTAL_TIMELINE_DURATION_MS;
        ponderPaused = true;
    }

    private void detectPonderHover(double mx, double my) {
        if (mx >= SIDEBAR_W) {
            double cmx = toCanvasX(mx);
            double cmy = toCanvasY(my);
            AlgoNode target = null;
            for (int i = nodeOrder.size() - 1; i >= 0; i--) {
                String nid = nodeOrder.get(i);
                AlgoNode n = graph.getNodes().get(nid);
                if (n == null) continue;
                int nh = nodeHeight(n);
                if (cmx >= n.getX() && cmx <= n.getX() + NODE_W
                 && cmy >= n.getY() && cmy <= n.getY() + nh) {
                    target = n;
                    break;
                }
            }
            if (target != null) {
                ponderHoveredNode = target;
                ponderHoldStart = System.currentTimeMillis();
                isPanning = false;
                dragging = null;
            }
        } else if (lastHoveredSidebarType != null) {
            ponderHoveredType = lastHoveredSidebarType;
            ponderHoldStart = System.currentTimeMillis();
            ponderHoveredCardY = lastHoveredSidebarY;
        }
    }

    private void closePonder() {
        ponderOpen = false;
        ponderOpenNode = null;
        ponderOpenType = null;
        wKeyDown = false;
        wKeyReleasedSinceLastClose = false;
        ponderHoveredNode = null;
        ponderHoveredType = null;
        ponderHoldStart = 0;
        ponderHoveredCardY = -1;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
