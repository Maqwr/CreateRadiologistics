package com.radiologistics.create.node.nodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * GizmosViewNode — a sink node that accepts a gizmos JSON string and
 * exposes the parsed data so the node editor can draw a live preview canvas.
 */
public class GizmosViewNode extends AlgoNode {
    private String lastGizmosJson = "[]";
    private JsonArray lastParsed = new JsonArray();

    public GizmosViewNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override public String getType() { return "gizmos_view"; }
    @Override public List<String> getInputPorts()  { return List.of("gizmos"); }
    @Override public List<String> getOutputPorts() { return Collections.emptyList(); }
    @Override public CompoundTag saveProperties()  { return new CompoundTag(); }
    @Override public void loadProperties(CompoundTag tag) {}

    public String getLastGizmosJson() { return lastGizmosJson; }
    public JsonArray getLastParsed()  { return lastParsed; }

    /** How many gizmo elements are currently buffered */
    public int getLastCount() { return lastParsed.size(); }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        Object val = inputValues.get("gizmos");
        lastGizmosJson = (val != null && !String.valueOf(val).isBlank()) ? String.valueOf(val) : "[]";
        try {
            JsonElement el = JsonParser.parseString(lastGizmosJson);
            lastParsed = el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            lastParsed = new JsonArray();
        }
        return null; // sink — no output
    }

    /**
     * Returns a list of simple 2D draw commands for the node-editor mini-preview.
     * Each entry: [shape, x, y, w, h, colorARGB]  (normalised 0..1 coordinates)
     * Only type="2d" elements are rendered in the preview.
     */
    public List<long[]> getPreviewRects() {
        List<long[]> out = new ArrayList<>();
        for (JsonElement el : lastParsed) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : "2d";
            if (!"2d".equals(type) && !"camera_feed".equals(type)) continue;

            String shape = o.has("shape") ? o.get("shape").getAsString() : "rect";
            if ("camera_feed".equals(shape)) {
                double x = o.has("x") ? o.get("x").getAsDouble() : 0;
                double y = o.has("y") ? o.get("y").getAsDouble() : 0;
                double w = o.has("w") ? o.get("w").getAsDouble() : 100;
                double h = o.has("h") ? o.get("h").getAsDouble() : 100;

                // 1. Sky (Sky Blue)
                out.add(new long[]{
                    (long)((x / 100.0) * 1000),
                    (long)((y / 100.0) * 1000),
                    (long)((w / 100.0) * 1000),
                    (long)(((h / 2.0) / 100.0) * 1000),
                    0xFF87CEEB
                });

                // 2. Ground (Grass Green)
                out.add(new long[]{
                    (long)((x / 100.0) * 1000),
                    (long)(((y + h / 2.0) / 100.0) * 1000),
                    (long)((w / 100.0) * 1000),
                    (long)(((h / 2.0) / 100.0) * 1000),
                    0xFF557A46
                });

                // 3. Mock block/object in center (Oak Planks color)
                out.add(new long[]{
                    (long)(((x + w * 0.4) / 100.0) * 1000),
                    (long)(((y + h * 0.4) / 100.0) * 1000),
                    (long)(((w * 0.2) / 100.0) * 1000),
                    (long)(((h * 0.4) / 100.0) * 1000),
                    0xFFB8621D
                });

                // 4. Crosshair - Horizontal (white-translucent)
                out.add(new long[]{
                    (long)(((x + w * 0.45) / 100.0) * 1000),
                    (long)(((y + h * 0.49) / 100.0) * 1000),
                    (long)(((w * 0.1) / 100.0) * 1000),
                    (long)(((h * 0.02) / 100.0) * 1000),
                    0xAAFFFFFF
                });

                // 5. Crosshair - Vertical (white-translucent)
                out.add(new long[]{
                    (long)(((x + w * 0.49) / 100.0) * 1000),
                    (long)(((y + h * 0.45) / 100.0) * 1000),
                    (long)(((w * 0.02) / 100.0) * 1000),
                    (long)(((h * 0.1) / 100.0) * 1000),
                    0xAAFFFFFF
                });
            } else if ("grid".equals(shape)) {
                double x = o.has("x") ? o.get("x").getAsDouble() : 0;
                double y = o.has("y") ? o.get("y").getAsDouble() : 0;
                double w = o.has("w") ? o.get("w").getAsDouble() : 100;
                double h = o.has("h") ? o.get("h").getAsDouble() : 100;
                int gw = o.has("gw") ? o.get("gw").getAsInt() : 16;
                int gh = o.has("gh") ? o.get("gh").getAsInt() : 16;
                String colorsStr = o.has("colors") ? o.get("colors").getAsString() : "";

                double pixelW = w / gw;
                double pixelH = h / gh;

                // Downsample preview for editor performance if grid is large
                int step = 1;
                int maxDim = Math.max(gw, gh);
                if (maxDim > 80) {
                    step = 8;
                } else if (maxDim > 40) {
                    step = 4;
                } else if (maxDim > 20) {
                    step = 2;
                }

                for (int u = 0; u < gw; u += step) {
                    for (int v = 0; v < gh; v += step) {
                        int idx = (u * gh + v) * 8;
                        if (idx + 8 <= colorsStr.length()) {
                            String colorHex = colorsStr.substring(idx, idx + 8);
                            int col = parsePreviewColor("#" + colorHex, 0xFF00FFFF);
                            if ((col & 0xFF000000) != 0) {
                                double px = (x + u * pixelW) / 100.0;
                                double py = (y + (gh - 1 - v) * pixelH) / 100.0;
                                double pw = (pixelW * step) / 100.0;
                                double ph = (pixelH * step) / 100.0;
                                out.add(new long[]{
                                    (long)(px * 1000),
                                    (long)(py * 1000),
                                    (long)(pw * 1000),
                                    (long)(ph * 1000),
                                    col
                                });
                                if (out.size() >= 512) break;
                            }
                        }
                    }
                    if (out.size() >= 512) break;
                }
            } else {
                double nx = o.has("x") ? o.get("x").getAsDouble() / 100.0 : 0;
                double ny = o.has("y") ? o.get("y").getAsDouble() / 100.0 : 0;
                double nw = o.has("w") ? o.get("w").getAsDouble() / 100.0 : 0.1;
                double nh = o.has("h") ? o.get("h").getAsDouble() / 100.0 : 0.1;
                String colorStr = o.has("color") ? o.get("color").getAsString() : "";
                int col = parsePreviewColor(colorStr, 0xFF00FFFF);
                out.add(new long[]{(long)(nx*1000), (long)(ny*1000), (long)(nw*1000), (long)(nh*1000), col});
            }
            if (out.size() >= 512) break; // cap for performance
        }
        return out;
    }

    private static int parsePreviewColor(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try {
            if (s.startsWith("#")) {
                long v = Long.parseLong(s.substring(1), 16);
                return (s.length() == 7) ? (int)(0xFF000000L | v) : (int)v;
            }
            return (int)Long.parseLong(s);
        } catch (Exception e) { return def; }
    }
}
