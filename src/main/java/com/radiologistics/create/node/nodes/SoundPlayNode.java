package com.radiologistics.create.node.nodes;

import com.radiologistics.create.node.AlgoNode;
import com.radiologistics.create.node.EvaluationContext;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/**
 * "Link Stream" node — builds an audio stream descriptor from a URL, volume and pitch,
 * then outputs it on the "stream" port for an Audio Play node to consume.
 * Does NOT send any packets or play audio itself.
 */
public class SoundPlayNode extends AlgoNode {

    public SoundPlayNode(String id, double x, double y) {
        super(id, x, y);
    }

    @Override
    public String getType() {
        return "sound_play";
    }

    @Override
    public List<String> getInputPorts() {
        return List.of("link", "volume", "pitch");
    }

    @Override
    public List<String> getOutputPorts() {
        return List.of("stream");
    }

    @Override
    public CompoundTag saveProperties() {
        return new CompoundTag();
    }

    @Override
    public void loadProperties(CompoundTag tag) {
    }

    @Override
    public Object evaluate(String outputPort, Map<String, Object> inputValues, EvaluationContext context) {
        if (!"stream".equalsIgnoreCase(outputPort)) return null;

        Object linkVal = inputValues.get("link");
        String link = linkVal != null ? String.valueOf(linkVal).trim() : "";

        Object volumeVal = inputValues.get("volume");
        double volume = 1.0;
        if (volumeVal instanceof Number num) {
            volume = num.doubleValue();
        } else if (volumeVal != null) {
            try { volume = Double.parseDouble(String.valueOf(volumeVal).trim()); } catch (NumberFormatException ignored) {}
        }
        volume = Math.max(0.0, Math.min(10.0, volume));

        Object pitchVal = inputValues.get("pitch");
        double pitch = 1.0;
        if (pitchVal instanceof Number num) {
            pitch = num.doubleValue();
        } else if (pitchVal != null) {
            try { pitch = Double.parseDouble(String.valueOf(pitchVal).trim()); } catch (NumberFormatException ignored) {}
        }

        if (link.isEmpty()) return "";

        return String.format(java.util.Locale.ROOT,
            "{\"audio_type\": \"sound\", \"url\": \"%s\", \"volume\": %.2f, \"pitch\": %.2f}",
            link.replace("\"", "\\\""), volume, pitch);
    }
}
