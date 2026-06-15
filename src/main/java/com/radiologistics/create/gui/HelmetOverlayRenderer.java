package com.radiologistics.create.gui;

import com.radiologistics.create.item.PilotHelmetItem;
import com.radiologistics.create.network.ClientOnlyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;

@EventBusSubscriber(modid = "radiologistics", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class HelmetOverlayRenderer {
    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean isHelmet = head.getItem() instanceof PilotHelmetItem;
        if (!isHelmet) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int borderSize = 60;
        for (int i = 0; i < borderSize; i += 2) {
            float alpha = 1.0f - ((float) i / borderSize);
            alpha = alpha * alpha;
            int alphaInt = (int) (alpha * 0x60);
            int col = (alphaInt << 24) | 0x0055FF;
            // Top: fade downwards from top (y=0) to y=borderSize
            g.fill(0, i, screenW, i + 2, col);
            // Bottom: fade upwards from bottom (y=screenH) to y=screenH-borderSize
            g.fill(0, screenH - i - 2, screenW, screenH - i, col);
            // Left: fade rightwards from left (x=0) to x=borderSize
            g.fill(i, 0, i + 2, screenH, col);
            // Right: fade leftwards from right (x=screenW) to x=screenW-borderSize
            g.fill(screenW - i - 2, 0, screenW - i, screenH, col);
        }

        // Read linked computer pos from helmet NBT
        BlockPos linkedPos = null;
        net.minecraft.world.item.component.CustomData customData = head.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            if (tag.contains("LinkedComputer")) {
                linkedPos = BlockPos.of(tag.getLong("LinkedComputer"));
            }
        }
        if (linkedPos == null) return;

        BlockPos activePos = ClientOnlyHandler.getActiveHelmetComputerPos();
        if (activePos == null || !linkedPos.equals(activePos)) {
            return;
        }

        String json = ClientOnlyHandler.getActiveHelmetGizmosJson();
        if (json == null || json.equals("[]") || json.isBlank()) return;

        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return;
            JsonArray arr = root.getAsJsonArray();

            Font font = mc.font;

            java.util.List<JsonObject> sorted = sortGizmos(arr);
            for (JsonObject obj : sorted) {
                String type = obj.has("type") ? obj.get("type").getAsString() : "2d";

                if (type.equals("2d")) {
                    render2D(g, font, obj, -1000000.0, 1000000.0, -1000000.0, 1000000.0, net.minecraft.client.renderer.RenderType.gui());
                } else if (type.equals("3d")) {
                    render3D(g, font, obj, screenW, screenH);
                } else if (type.equals("camera_feed")) {
                    String cacheKey = "radiologistics_helmet_feed";
                    CameraFeedRenderer.render(obj, g.pose(), g.bufferSource(), cacheKey, 0xF000F0, -1000000.0f, 1000000.0f, -1000000.0f, 1000000.0f);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void fillClamped(GuiGraphics g, int x1, int y1, int x2, int y2, int color, double minX, double maxX, double minY, double maxY, net.minecraft.client.renderer.RenderType renderType) {
        int rx1 = Math.max((int) minX, x1);
        int ry1 = Math.max((int) minY, y1);
        int rx2 = Math.min((int) maxX, x2);
        int ry2 = Math.min((int) maxY, y2);
        if (rx1 < rx2 && ry1 < ry2) {
            g.fill(renderType, rx1, ry1, rx2, ry2, color);
        }
    }

    public static void render2D(GuiGraphics g, Font font, JsonObject obj, double minX, double maxX, double minY, double maxY) {
        render2D(g, font, obj, minX, maxX, minY, maxY, net.minecraft.client.renderer.RenderType.gui());
    }

    public static void render2D(GuiGraphics g, Font font, JsonObject obj, double minX, double maxX, double minY, double maxY, net.minecraft.client.renderer.RenderType renderType) {
        String shape = obj.has("shape") ? obj.get("shape").getAsString() : "rect";
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double w = obj.has("w") ? obj.get("w").getAsDouble() : 0;
        double h = obj.has("h") ? obj.get("h").getAsDouble() : 0;
        String colorStr = obj.has("color") ? obj.get("color").getAsString() : "";
        String text = obj.has("text") ? obj.get("text").getAsString() : "";
        int outline = obj.has("outline") ? obj.get("outline").getAsInt() : 100;

        int col = parseColor(colorStr, 0xFF00FFFF); // default cyan

        if (shape.equals("rect")) {
            int ix = (int) (x - w / 2.0);
            int iy = (int) (y - h / 2.0);
            int iw = (int) w;
            int ih = (int) h;
            if (iw > 0 && ih > 0) {
                if (outline < 100) {
                    int thickness = Math.min(Math.min(iw / 2, ih / 2), Math.max(1, outline));
                    fillClamped(g, ix, iy, ix + iw, iy + thickness, col, minX, maxX, minY, maxY, renderType); // Top
                    fillClamped(g, ix, iy + ih - thickness, ix + iw, iy + ih, col, minX, maxX, minY, maxY, renderType); // Bottom
                    fillClamped(g, ix, iy + thickness, ix + thickness, iy + ih - thickness, col, minX, maxX, minY, maxY, renderType); // Left
                    fillClamped(g, ix + iw - thickness, iy + thickness, ix + iw, iy + ih - thickness, col, minX, maxX, minY, maxY, renderType); // Right
                } else {
                    fillClamped(g, ix, iy, ix + iw, iy + ih, col, minX, maxX, minY, maxY, renderType);
                }
            }
        } else if (shape.equals("circle")) {
            drawCircle(g, (int)x, (int)y, (int)(w / 2.0), col, outline, minX, maxX, minY, maxY, renderType);
        } else if (shape.equals("line")) {
            // using width/height as x2/y2 if x2/y2 aren't specified
            double x2 = obj.has("x2") ? obj.get("x2").getAsDouble() : (x + w);
            double y2 = obj.has("y2") ? obj.get("y2").getAsDouble() : (y + h);
            drawLine(g, (int)x, (int)y, (int)x2, (int)y2, col, minX, maxX, minY, maxY, renderType);
        } else if (shape.equals("text")) {
            float scale = 1.0f;
            if (obj.has("scale")) {
                scale = obj.get("scale").getAsFloat();
            } else if (obj.has("size")) {
                scale = obj.get("size").getAsFloat();
            } else {
                if (outline > 0 && outline < 10) {
                    scale = (float) outline;
                } else if (outline >= 10) {
                    scale = outline / 100.0f;
                }
            }
            if (scale <= 0) scale = 1.0f;
            int tw = font.width(text);
            int th = font.lineHeight;
            double scaledW = tw * scale;
            double scaledH = th * scale;
            if (x + scaledW / 2.0 < minX || x - scaledW / 2.0 > maxX || y + scaledH / 2.0 < minY || y - scaledH / 2.0 > maxY) {
                return;
            }
            g.pose().pushPose();
            g.pose().translate(x, y, 0.05f);
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(font, text, -tw / 2, -th / 2, col, false);
            g.pose().popPose();
        } else if (shape.equals("grid")) {
            int gw = obj.has("gw") ? obj.get("gw").getAsInt() : 16;
            int gh = obj.has("gh") ? obj.get("gh").getAsInt() : 16;
            String colorsStr = obj.has("colors") ? obj.get("colors").getAsString() : "";
            double pixelW = w / gw;
            double pixelH = h / gh;

            for (int u = 0; u < gw; u++) {
                for (int v = 0; v < gh; v++) {
                    int idx = (u * gh + v) * 8;
                    if (idx + 8 <= colorsStr.length()) {
                        String colorHex = colorsStr.substring(idx, idx + 8);
                        try {
                            long parsedVal = Long.parseLong(colorHex, 16);
                            int pixelCol = (int) parsedVal;
                            if ((pixelCol & 0xFF000000) != 0) { // only fill if not fully transparent
                                int px1 = (int)(x + u * pixelW);
                                int py1 = (int)(y + (gh - 1 - v) * pixelH);
                                int px2 = (int)(x + u * pixelW + pixelW + 0.5);
                                int py2 = (int)(y + (gh - 1 - v) * pixelH + pixelH + 0.5);
                                fillClamped(g, px1, py1, px2, py2, pixelCol, minX, maxX, minY, maxY, renderType);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    public static void render3D(GuiGraphics g, Font font, JsonObject obj, double screenW, double screenH) {
        double worldX = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double worldY = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double worldZ = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        JsonArray subGizmos = obj.has("gizmos") && obj.get("gizmos").isJsonArray()
                ? obj.get("gizmos").getAsJsonArray() : new JsonArray();

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        double dx = worldX - camPos.x;
        double dy = worldY - camPos.y;
        double dz = worldZ - camPos.z;

        org.joml.Vector3f target = new org.joml.Vector3f((float)dx, (float)dy, (float)dz);
        org.joml.Quaternionf conjugate = new org.joml.Quaternionf(camera.rotation()).conjugate();
        target.rotate(conjugate);

        // target.z is in camera coordinate space (negative Z is forward in Minecraft camera space)
        if (target.z < 0) {
            double fovRad = Math.toRadians(mc.options.fov().get());
            double scale = (screenH / 2.0) / Math.tan(fovRad / 2.0);

            double dist = -target.z;
            double screenX = (screenW / 2.0) + (target.x / dist) * scale;
            double screenY = (screenH / 2.0) - (target.y / dist) * scale;

            double billboardSize = scale / dist;
            billboardSize = Math.min(screenH * 0.5, billboardSize); // clamp max size

            // No clipping limits on the helmet overlay screen
            double scaleFactor = billboardSize / 100.0;

            g.pose().pushPose();
            g.pose().translate(screenX, screenY, 0);
            g.pose().scale((float) scaleFactor, (float) scaleFactor, 1.0f);

            for (JsonElement subItem : subGizmos) {
                if (!subItem.isJsonObject()) continue;
                JsonObject subObj = subItem.getAsJsonObject();
                String subType = subObj.has("type") ? subObj.get("type").getAsString() : "2d";
                if (subType.equals("2d")) {
                    render2D(g, font, subObj, -1000000.0, 1000000.0, -1000000.0, 1000000.0, net.minecraft.client.renderer.RenderType.gui());
                }
            }

            g.pose().popPose();
        }

    }

    public static int parseColor(String colorStr, int defaultCol) {
        if (colorStr == null || colorStr.isEmpty()) return defaultCol;
        try {
            if (colorStr.startsWith("#")) {
                long val = Long.parseLong(colorStr.substring(1), 16);
                if (colorStr.length() <= 7) {
                    return (int) (val | 0xFF000000); // add alpha if missing
                }
                return (int) val;
            }
            int parsed = Integer.parseInt(colorStr);
            if ((parsed & 0xFF000000) == 0) {
                parsed |= 0xFF000000;
            }
            return parsed;
        } catch (Exception e) {
            return defaultCol;
        }
    }

    public static void drawCircle(GuiGraphics g, int cx, int cy, int radius, int color, int outline, double minX, double maxX, double minY, double maxY, net.minecraft.client.renderer.RenderType renderType) {
        if (radius <= 0) return;
        if (outline >= 100) {
            for (int y = -radius; y <= radius; y++) {
                int ry = cy + y;
                if (ry < minY || ry >= maxY) continue;
                int dx = (int) Math.sqrt(radius * radius - y * y);
                int rx1 = Math.max((int) minX, cx - dx);
                int rx2 = Math.min((int) maxX, cx + dx + 1);
                if (rx1 < rx2) {
                    g.fill(renderType, rx1, ry, rx2, ry + 1, color);
                }
            }
        } else {
            int thickness = Math.min(radius, Math.max(1, outline));
            int innerRadius = radius - thickness;
            for (int y = -radius; y <= radius; y++) {
                int ry = cy + y;
                if (ry < minY || ry >= maxY) continue;
                int dxOuter = (int) Math.sqrt(radius * radius - y * y);
                int dxInner = 0;
                if (innerRadius > 0 && Math.abs(y) <= innerRadius) {
                    dxInner = (int) Math.sqrt(innerRadius * innerRadius - y * y);
                }
                if (dxInner > 0) {
                    int leftX1 = Math.max((int) minX, cx - dxOuter);
                    int leftX2 = Math.min((int) maxX, cx - dxInner);
                    if (leftX1 < leftX2) {
                        g.fill(renderType, leftX1, ry, leftX2, ry + 1, color);
                    }
                    int rightX1 = Math.max((int) minX, cx + dxInner + 1);
                    int rightX2 = Math.min((int) maxX, cx + dxOuter + 1);
                    if (rightX1 < rightX2) {
                        g.fill(renderType, rightX1, ry, rightX2, ry + 1, color);
                    }
                } else {
                    int rx1 = Math.max((int) minX, cx - dxOuter);
                    int rx2 = Math.min((int) maxX, cx + dxOuter + 1);
                    if (rx1 < rx2) {
                        g.fill(renderType, rx1, ry, rx2, ry + 1, color);
                    }
                }
            }
        }
    }

    public static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color, double minX, double maxX, double minY, double maxY, net.minecraft.client.renderer.RenderType renderType) {
        // Bresenham's line algorithm drawn using pixel fills
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x1 >= minX && x1 < maxX && y1 >= minY && y1 < maxY) {
                g.fill(renderType, x1, y1, x1 + 1, y1 + 1, color);
            }
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private static java.util.List<JsonObject> sortGizmos(JsonArray arr) {
        java.util.List<JsonObject> list = new java.util.ArrayList<>();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                list.add(el.getAsJsonObject());
            }
        }
        list.sort((a, b) -> {
            int layerA = a.has("layer") && a.get("layer").isJsonPrimitive() ? a.get("layer").getAsInt() : 0;
            int layerB = b.has("layer") && b.get("layer").isJsonPrimitive() ? b.get("layer").getAsInt() : 0;
            return Integer.compare(layerA, layerB);
        });
        return list;
    }
}

