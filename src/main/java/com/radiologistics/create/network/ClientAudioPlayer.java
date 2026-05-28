package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Radiologistics.MODID, value = Dist.CLIENT)
public class ClientAudioPlayer {
    private static final Map<BlockPos, SoundInstance> activeSounds = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
    }

    public static void stopAll() {
        for (BlockPos pos : activeSounds.keySet()) {
            stop(pos);
        }
        activeSounds.clear();
    }

    public static void stop(BlockPos pos) {
        SoundInstance sound = activeSounds.remove(pos);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    public static void playTTS(BlockPos pos, String text, double volume, double pitch) {
        stop(pos);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getNarrator().isActive()) {
            mc.getNarrator().sayNow(text);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
    }

    public static void playURL(BlockPos pos, String soundId, double volume, double pitch) {
        stop(pos);

        if (soundId == null || soundId.trim().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ResourceLocation soundLoc = ResourceLocation.tryParse(soundId.trim());
        if (soundLoc != null) {
            SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                soundLoc,
                SoundSource.RECORDS,
                (float) (volume * 0.1),
                (float) pitch,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                false
            );

            mc.getSoundManager().play(soundInstance);
            activeSounds.put(pos, soundInstance);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (Map.Entry<BlockPos, SoundInstance> entry : activeSounds.entrySet()) {
            BlockPos pos = entry.getKey();
            SoundInstance sound = entry.getValue();
            if (!mc.getSoundManager().isActive(sound)) {
                activeSounds.remove(pos);
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
            }
        }
    }
}
