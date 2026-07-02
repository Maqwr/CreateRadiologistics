package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Radiologistics.MODID, value = Dist.CLIENT)
public class ClientAudioPlayer {

    private static final Map<BlockPos, CustomSoundInstance> activeSessions = new ConcurrentHashMap<>();

    private static final Map<BlockPos, Thread> activeStreamThreads = new ConcurrentHashMap<>();
    private static final Map<BlockPos, javax.sound.sampled.Clip> activeClips = new ConcurrentHashMap<>();
    private static final Map<BlockPos, javax.sound.sampled.SourceDataLine> activeLines = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Double> streamBaseVolumes = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Double> streamPitches = new ConcurrentHashMap<>();

    private static final Map<BlockPos, Thread> activeTtsThreads = new ConcurrentHashMap<>();

    private static void setClipVolume(javax.sound.sampled.Clip clip, double volume) {
        if (clip == null) return;
        try {
            javax.sound.sampled.FloatControl gainControl =
                    (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
            float dB = (float)(20.0 * Math.log10(Math.max(0.0001, volume)));
            float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(clamped);
        } catch (Throwable ignored) {}
    }

    private static void setLineVolume(javax.sound.sampled.SourceDataLine line, double volume) {
        if (line == null) return;
        try {
            javax.sound.sampled.FloatControl gainControl =
                    (javax.sound.sampled.FloatControl) line.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
            float dB = (float)(20.0 * Math.log10(Math.max(0.0001, volume)));
            float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(clamped);
        } catch (Throwable ignored) {}
    }

    private static void setClipPitch(javax.sound.sampled.Clip clip, double pitch) {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.SAMPLE_RATE)) {
                javax.sound.sampled.FloatControl rateControl =
                        (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.SAMPLE_RATE);
                float targetRate = (float) (clip.getFormat().getSampleRate() * pitch);
                rateControl.setValue(Math.max(rateControl.getMinimum(), Math.min(rateControl.getMaximum(), targetRate)));
            }
        } catch (Throwable ignored) {}
    }

    private static void setClipPan(javax.sound.sampled.Clip clip, float pan) {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.BALANCE)) {
                javax.sound.sampled.FloatControl balance = (javax.sound.sampled.FloatControl)
                        clip.getControl(javax.sound.sampled.FloatControl.Type.BALANCE);
                balance.setValue(pan);
            } else if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.PAN)) {
                javax.sound.sampled.FloatControl panControl = (javax.sound.sampled.FloatControl)
                        clip.getControl(javax.sound.sampled.FloatControl.Type.PAN);
                panControl.setValue(pan);
            }
        } catch (Throwable ignored) {}
    }

    private static void setLinePan(javax.sound.sampled.SourceDataLine line, float pan) {
        if (line == null) return;
        try {
            if (line.isControlSupported(javax.sound.sampled.FloatControl.Type.BALANCE)) {
                javax.sound.sampled.FloatControl balance = (javax.sound.sampled.FloatControl)
                        line.getControl(javax.sound.sampled.FloatControl.Type.BALANCE);
                balance.setValue(pan);
            } else if (line.isControlSupported(javax.sound.sampled.FloatControl.Type.PAN)) {
                javax.sound.sampled.FloatControl panControl = (javax.sound.sampled.FloatControl)
                        line.getControl(javax.sound.sampled.FloatControl.Type.PAN);
                panControl.setValue(pan);
            }
        } catch (Throwable ignored) {}
    }

    private static float calculatePan(BlockPos sourcePos, Vec3 playerPos, Vec3 playerLook) {
        Vec3 toSource = new Vec3(sourcePos.getX() + 0.5 - playerPos.x, 0, sourcePos.getZ() + 0.5 - playerPos.z);
        double distSq = toSource.lengthSqr();
        if (distSq < 0.01) return 0.0f;
        toSource = toSource.normalize();

        double sourceAngle = Math.atan2(toSource.z, toSource.x);
        double playerAngle = Math.atan2(playerLook.z, playerLook.x);
        double relativeAngle = sourceAngle - playerAngle;

        while (relativeAngle < -Math.PI) relativeAngle += 2 * Math.PI;
        while (relativeAngle > Math.PI) relativeAngle -= 2 * Math.PI;

        return (float) Math.sin(relativeAngle);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            stopAll();
        }
    }

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        Vec3 playerPos = mc.player.position();
        Vec3 playerLook = mc.player.getLookAngle();

        for (Map.Entry<BlockPos, javax.sound.sampled.Clip> entry : activeClips.entrySet()) {
            BlockPos pos = entry.getKey();
            javax.sound.sampled.Clip clip = entry.getValue();
            double baseVolume = streamBaseVolumes.getOrDefault(pos, 1.0);
            double distanceSq = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double volume = baseVolume * calculateAttenuatedVolume(distanceSq);
            setClipVolume(clip, volume);

            float pan = calculatePan(pos, playerPos, playerLook);
            setClipPan(clip, pan);
        }

        for (Map.Entry<BlockPos, javax.sound.sampled.SourceDataLine> entry : activeLines.entrySet()) {
            BlockPos pos = entry.getKey();
            javax.sound.sampled.SourceDataLine line = entry.getValue();
            double baseVolume = streamBaseVolumes.getOrDefault(pos, 1.0);
            double distanceSq = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double volume = baseVolume * calculateAttenuatedVolume(distanceSq);
            setLineVolume(line, volume);

            float pan = calculatePan(pos, playerPos, playerLook);
            setLinePan(line, pan);
        }
    }

    private static double calculateAttenuatedVolume(double distanceSq) {
        double maxRange = 24.0;
        double maxRangeSq = maxRange * maxRange;
        if (distanceSq >= maxRangeSq) {
            return 0.0;
        }
        double distance = Math.sqrt(distanceSq);
        return 1.0 - (distance / maxRange);
    }

    private static void stopNarrator() {
        try {
            com.mojang.text2speech.Narrator nativeNarrator = com.mojang.text2speech.Narrator.getNarrator();
            if (nativeNarrator != null && nativeNarrator.active()) {
                nativeNarrator.clear();
                nativeNarrator.say(" ", true);
            }
        } catch (Throwable ignored) {}
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getNarrator() != null) {
                mc.getNarrator().clear();
            }
        } catch (Throwable ignored) {}
    }

    public static void stopAll() {
        for (BlockPos pos : activeSessions.keySet()) {
            stopInternal(pos);
        }
        for (BlockPos pos : activeStreamThreads.keySet()) {
            stopInternal(pos);
        }
        for (BlockPos pos : activeTtsThreads.keySet()) {
            stopInternal(pos);
        }
        activeSessions.clear();
        activeStreamThreads.clear();
        activeClips.clear();
        activeLines.clear();
        activeTtsThreads.clear();
        streamBaseVolumes.clear();
        streamPitches.clear();
        stopNarrator();
    }

    public static void stop(BlockPos pos) {
        stopInternal(pos);
    }

    private static void stopInternal(BlockPos pos) {

        CustomSoundInstance sound = activeSessions.remove(pos);
        if (sound != null) {
            sound.stopSound();
            try { Minecraft.getInstance().getSoundManager().stop(sound); } catch (Throwable ignored) {}
        }

        Thread streamThread = activeStreamThreads.remove(pos);
        if (streamThread != null) {
            streamThread.interrupt();
        }

        javax.sound.sampled.Clip clip = activeClips.remove(pos);
        if (clip != null) {
            try { clip.stop(); clip.close(); } catch (Throwable ignored) {}
        }

        javax.sound.sampled.SourceDataLine line = activeLines.remove(pos);
        if (line != null) {
            try { line.stop(); line.close(); } catch (Throwable ignored) {}
        }
        streamBaseVolumes.remove(pos);
        streamPitches.remove(pos);

        Thread ttsThread = activeTtsThreads.remove(pos);
        if (ttsThread != null) {
            ttsThread.interrupt();
        }

        stopNarrator();
    }

    public static void playTTS(BlockPos pos, String text, double volume, double pitch) {

        stop(pos);

        String textToSpeak = text;
        int idx = text.indexOf('|');
        if (idx != -1) {
            textToSpeak = text.substring(idx + 1);
        }

        if (textToSpeak.isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
            return;
        }

        final String finalText = textToSpeak;
        final BlockPos finalPos = pos;

        try {
            com.mojang.text2speech.Narrator narrator = com.mojang.text2speech.Narrator.getNarrator();
            if (narrator != null && narrator.active()) {
                narrator.clear();
                narrator.say(finalText, true);
            }
        } catch (Throwable t) {
            Radiologistics.LOGGER.error("Failed to speak TTS on main thread", t);
        }

        Thread task = new Thread(() -> {
            try {
                int durationMs = Math.max(1000, finalText.length() * 80);
                Thread.sleep(durationMs);

                final Thread currentThread = Thread.currentThread();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.level != null && !currentThread.isInterrupted()) {

                        if (activeTtsThreads.remove(finalPos, currentThread)) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(finalPos));
                        }
                    }
                });
            } catch (InterruptedException ignored) {
                activeTtsThreads.remove(finalPos, Thread.currentThread());
            }
        });
        task.setName("radiologistics-tts-" + pos.toShortString());
        task.setDaemon(true);
        activeTtsThreads.put(pos, task);
        task.start();
    }

    public static void playURL(BlockPos pos, String urlStr, double volume, double pitch, double seekSeconds) {
        String finalUrlStr = com.radiologistics.create.Radiologistics.rewriteUrl(urlStr);

        if (finalUrlStr.startsWith("http://") || finalUrlStr.startsWith("https://") || finalUrlStr.startsWith("file://")) {
            Thread activeThread = activeStreamThreads.get(pos);
            if (activeThread != null) {
                streamBaseVolumes.put(pos, volume);
                streamPitches.put(pos, pitch);

                javax.sound.sampled.Clip clip = activeClips.get(pos);
                if (clip != null) {
                    setClipVolume(clip, volume);
                    setClipPitch(clip, pitch);
                }

                javax.sound.sampled.SourceDataLine line = activeLines.get(pos);
                if (line != null) {
                    setLineVolume(line, volume);
                }
                return;
            }
        } else {
            CustomSoundInstance existing = activeSessions.get(pos);
            if (existing != null && finalUrlStr.equals(existing.getUrlStr())) {
                existing.setVolume(volume);
                existing.setPitch(pitch);
                return;
            }
        }

        stop(pos);

        if (finalUrlStr.startsWith("http://") || finalUrlStr.startsWith("https://") || finalUrlStr.startsWith("file://")) {
            playHttpUrl(pos, finalUrlStr, volume, pitch, seekSeconds);
            return;
        }

        SoundEvent soundEvent = null;
        ResourceLocation rl = ResourceLocation.tryParse(finalUrlStr);
        if (rl != null && BuiltInRegistries.SOUND_EVENT.containsKey(rl)) {
            soundEvent = BuiltInRegistries.SOUND_EVENT.get(rl);
        }
        if (soundEvent == null) {

            Radiologistics.LOGGER.warn("[ClientAudioPlayer] Unknown sound '{}' — using fallback bell", urlStr);
            soundEvent = SoundEvents.NOTE_BLOCK_BELL.value();
        }

        CustomSoundInstance sound = new CustomSoundInstance(soundEvent, SoundSource.RECORDS, pos, urlStr, volume, pitch);
        activeSessions.put(pos, sound);
        Minecraft.getInstance().getSoundManager().play(sound);

        Thread timer = new Thread(() -> {
            try {
                int durationMs = estimateDurationMs(urlStr, pitch);
                Thread.sleep(durationMs);
                if (activeSessions.remove(pos, sound)) {
                    sound.stopSound();
                    try { Minecraft.getInstance().getSoundManager().stop(sound); } catch (Throwable ignored) {}
                    if (!Thread.currentThread().isInterrupted()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
                    }
                }
            } catch (InterruptedException ignored) {

            }
        });
        timer.setName("radiologistics-url-timer-" + pos.toShortString());
        timer.setDaemon(true);
        timer.start();
    }

    private static void playHttpUrl(BlockPos pos, String urlStr, double volume, double pitch, double seekSeconds) {
        streamBaseVolumes.put(pos, volume);
        streamPitches.put(pos, pitch);

        String lower = urlStr.toLowerCase(java.util.Locale.ROOT);
        String unsupportedDomain = null;
        if (lower.contains("spotify.com")) unsupportedDomain = "Spotify";
        else if (lower.contains("music.apple.com")) unsupportedDomain = "Apple Music";
        else if (lower.contains("tidal.com")) unsupportedDomain = "Tidal";
        else if (lower.contains("deezer.com")) unsupportedDomain = "Deezer";
        else if (lower.contains("bandcamp.com") && !lower.endsWith(".mp3") && !lower.endsWith(".ogg")) unsupportedDomain = "Bandcamp";
        if (unsupportedDomain != null) {
            final String platform = unsupportedDomain;
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "[Audio Module] " + platform + " links are not supported. Use a direct audio file URL (.mp3 / .wav)."
                        ).withStyle(net.minecraft.ChatFormatting.RED), false);
                }
            });
            return;
        }

        Thread streamThread = new Thread(() -> {
            javax.sound.sampled.AudioInputStream audioIn = null;
            javax.sound.sampled.Clip clip = null;
            try {
                String targetUrlStr = urlStr;
                String lowerUrl = urlStr.toLowerCase(java.util.Locale.ROOT);
                if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")
                        || lowerUrl.contains("soundcloud.com") || lowerUrl.contains("music.youtube.com")) {
                    String resolved = resolveCobaltUrl(urlStr);
                    if (resolved == null) {
                        throw new java.io.IOException("Could not resolve media stream URL via Cobalt API.");
                    }
                    targetUrlStr = resolved;
                }

                java.net.URL url = new java.net.URL(targetUrlStr);

                java.net.URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Minecraft-AudioModule/1.0)");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();

                String contentType = conn.getContentType();
                if (contentType != null && (contentType.startsWith("text/html") || contentType.startsWith("text/plain"))) {
                    final String ct = contentType;
                    Minecraft.getInstance().execute(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                    "[Audio Module] URL returned '" + ct + "' — not a playable audio file."
                                ).withStyle(net.minecraft.ChatFormatting.RED), false);
                        }
                    });
                    return;
                }

                boolean playedAsWav = false;
                try {
                    audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(conn.getInputStream()));

                    javax.sound.sampled.AudioFormat baseFormat = audioIn.getFormat();
                    javax.sound.sampled.AudioFormat decodeFormat = new javax.sound.sampled.AudioFormat(
                            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                            baseFormat.getSampleRate(),
                            16,
                            baseFormat.getChannels(),
                            baseFormat.getChannels() * 2,
                            baseFormat.getSampleRate(),
                            false
                    );

                    javax.sound.sampled.AudioInputStream decoded =
                            javax.sound.sampled.AudioSystem.getAudioInputStream(decodeFormat, audioIn);

                    clip = javax.sound.sampled.AudioSystem.getClip();
                    clip.open(decoded);

                    if (seekSeconds > 0) {
                        long seekFrames = (long)(seekSeconds * decodeFormat.getSampleRate());
                        if (seekFrames < clip.getFrameLength()) {
                            clip.setFramePosition((int) seekFrames);
                        }
                    }

                    setClipVolume(clip, volume);
                    setClipPitch(clip, pitch);

                    activeClips.put(pos, clip);
                    clip.start();
                    playedAsWav = true;

                    while (!Thread.currentThread().isInterrupted() && clip.isRunning()) {
                        Thread.sleep(100);
                    }

                    if (!Thread.currentThread().isInterrupted()) {
                        boolean removed = activeStreamThreads.remove(pos, Thread.currentThread());
                        activeClips.remove(pos, clip);
                        clip.stop();
                        clip.close();
                        if (removed) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
                        }
                    }
                } catch (Exception e) {

                    playMp3Stream(url, pos, volume, pitch, seekSeconds);
                }
            } catch (InterruptedException ignored) {

                if (clip != null) { try { clip.stop(); clip.close(); } catch (Throwable ignored2) {} }
                activeStreamThreads.remove(pos, Thread.currentThread());
                if (clip != null) {
                    activeClips.remove(pos, clip);
                }
            } catch (Throwable t) {
                Radiologistics.LOGGER.warn("[AudioModule] Failed to stream URL '{}': {}", urlStr, t.getMessage());
                activeStreamThreads.remove(pos, Thread.currentThread());
                if (clip != null) {
                    activeClips.remove(pos, clip);
                }
                Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                "[Audio Module] Could not play audio: " + t.getMessage()
                            ).withStyle(net.minecraft.ChatFormatting.RED), false);
                    }
                });
            } finally {
                if (audioIn != null) { try { audioIn.close(); } catch (Throwable ignored) {} }
                activeStreamThreads.remove(pos, Thread.currentThread());
                streamBaseVolumes.remove(pos);
                streamPitches.remove(pos);
            }
        });
        streamThread.setName("radiologistics-stream-" + pos.toShortString());
        streamThread.setDaemon(true);
        activeStreamThreads.put(pos, streamThread);
        streamThread.start();
    }

    private static void playMp3Stream(java.net.URL url, BlockPos pos, double volume, double pitch, double seekSeconds) throws Exception {
        java.net.URLConnection conn = url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Minecraft-AudioModule/1.0)");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.connect();

        try (java.io.InputStream rawIn = conn.getInputStream();
             java.io.BufferedInputStream bufIn = new java.io.BufferedInputStream(rawIn)) {

            Bitstream bitstream = new Bitstream(bufIn);
            Decoder decoder = new Decoder();
            javax.sound.sampled.SourceDataLine line = null;

            try {
                Header header = bitstream.readFrame();

                if (seekSeconds > 0) {
                    double frameDuration = 0.026;
                    int framesToSkip = (int)(seekSeconds / frameDuration);
                    for (int i = 0; i < framesToSkip && header != null; i++) {
                        bitstream.closeFrame();
                        header = bitstream.readFrame();
                    }
                }

                while (header != null && !Thread.currentThread().isInterrupted()) {
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] pcm = output.getBuffer();
                    int len = output.getBufferLength();

                    double currentPitch = streamPitches.getOrDefault(pos, 1.0);
                    float targetSampleRate = (float) (output.getSampleFrequency() * currentPitch);

                    if (line == null || Math.abs(line.getFormat().getSampleRate() - targetSampleRate) > 0.01) {
                        if (line != null) {
                            activeLines.remove(pos, line);
                            line.stop();
                            line.close();
                        }
                        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                                targetSampleRate, 16, output.getChannelCount(), true, false);
                        line = javax.sound.sampled.AudioSystem.getSourceDataLine(format);
                        line.open(format);
                        line.start();
                        activeLines.put(pos, line);
                    }

                    setLineVolume(line, volume);

                    byte[] bytes = new byte[len * 2];
                    for (int i = 0; i < len; i++) {
                        bytes[i * 2] = (byte) (pcm[i] & 0xff);
                        bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
                    }
                    line.write(bytes, 0, bytes.length);

                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }

                if (!Thread.currentThread().isInterrupted()) {
                    boolean removed = activeStreamThreads.remove(pos, Thread.currentThread());
                    if (line != null) {
                        activeLines.remove(pos, line);
                        line.drain();
                        line.stop();
                        line.close();
                    }
                    if (removed) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
                    }
                }
            } finally {
                try { bitstream.close(); } catch (Throwable ignored) {}
                if (line != null) {
                    activeLines.remove(pos, line);
                    try { line.stop(); line.close(); } catch (Throwable ignored) {}
                }
                activeStreamThreads.remove(pos, Thread.currentThread());
            }
        }
    }

    private static int estimateDurationMs(String urlStr, double pitch) {
        String lower = urlStr.toLowerCase();
        int durationMs;
        if (lower.contains("music_disc") || lower.contains("record")) {
            durationMs = 180_000;
        } else if (lower.contains("music") || lower.contains("ambient")) {
            durationMs = 60_000;
        } else if (lower.contains("explode") || lower.contains("blast") || lower.contains("thunder")) {
            durationMs = 2_000;
        } else if (lower.contains("click") || lower.contains("button") || lower.contains("ui") || lower.contains("hit")) {
            durationMs = 500;
        } else {
            durationMs = 3_000;
        }
        if (pitch > 0.1) {
            durationMs = (int)(durationMs / pitch);
        }
        return durationMs;
    }

    private static class CustomSoundInstance extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final BlockPos pos;
        private final String urlStr;
        private volatile double customVolume;
        private volatile double customPitch;
        private boolean done = false;

        public CustomSoundInstance(SoundEvent sound, SoundSource source, BlockPos pos, String urlStr, double volume, double pitch) {
            super(sound, source, net.minecraft.util.RandomSource.create());
            this.pos = pos;
            this.urlStr = urlStr;
            this.customVolume = volume;
            this.customPitch = pitch;

            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;

            this.volume = (float) volume;
            this.pitch = (float) pitch;
            this.looping = false;
            this.delay = 0;
            this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR;
            this.relative = false;
        }

        public String getUrlStr() { return urlStr; }

        public void setVolume(double volume) {
            this.customVolume = volume;
            this.volume = (float) volume;
        }

        public void setPitch(double pitch) {
            this.customPitch = pitch;
            this.pitch = (float) pitch;
        }

        public void stopSound() { this.done = true; }

        @Override
        public boolean isStopped() { return this.done; }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                this.done = true;
                return;
            }

            boolean foundAudioModule = mc.level.getBlockState(pos).getBlock()
                    instanceof com.radiologistics.create.block.AudioModuleBlock;

            if (!foundAudioModule) {
                try {
                    Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                    Object companion = companionClass.getField("INSTANCE").get(null);
                    java.lang.reflect.Method getContaining = companionClass.getMethod(
                            "getContaining", net.minecraft.world.level.Level.class, net.minecraft.core.Vec3i.class);
                    Object subLevelAccess = getContaining.invoke(companion, mc.level, pos);
                    if (subLevelAccess != null) foundAudioModule = true;
                } catch (Exception ignored) {}
            }

            if (!foundAudioModule) {
                this.done = true;
                return;
            }

            net.minecraft.util.RandomSource rnd = mc.level.getRandom();
            if (rnd.nextInt(5) == 0) {
                double px = pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4;
                double py = pos.getY() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4;
                double pz = pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4;
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, px, py, pz, rnd.nextDouble(), 0.0, 0.0);
            }

            this.volume = (float) this.customVolume;
            this.pitch = (float) this.customPitch;
        }
    }

    private static String resolveCobaltUrl(String originalUrl) {
        String[] instances = {
            "https://nuko-c.meowing.de/",
            "https://api.cobalt.blackcat.sweeux.org/",
            "https://cobalt.alpha.wolfy.love/",
            "https://cobalt.omega.wolfy.love/",
            "https://api.qwkuns.me/",
            "https://grapefruit.clxxped.lol/"
        };

        for (String instanceUrl : instances) {
            try {
                java.net.URL url = new java.net.URL(instanceUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);

                com.google.gson.JsonObject requestJson = new com.google.gson.JsonObject();
                requestJson.addProperty("url", originalUrl);
                requestJson.addProperty("downloadMode", "audio");
                requestJson.addProperty("audioFormat", "mp3");

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream(), "utf-8")) {
                        com.google.gson.JsonObject responseJson = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if (responseJson.has("url")) {
                            return responseJson.get("url").getAsString();
                        }
                    }
                } else {
                    Radiologistics.LOGGER.debug("[AudioModule] Cobalt instance '{}' returned HTTP response code {}", instanceUrl, code);
                }
            } catch (Throwable t) {
                Radiologistics.LOGGER.debug("[AudioModule] Failed to resolve URL via Cobalt instance '{}': {}", instanceUrl, t.getMessage());
            }
        }
        return null;
    }
}
