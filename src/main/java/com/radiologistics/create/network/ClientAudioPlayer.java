package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Radiologistics.MODID, value = Dist.CLIENT)
public class ClientAudioPlayer {

    /** Active Minecraft SoundInstance sessions (for minecraft: sound events). */
    private static final Map<BlockPos, CustomSoundInstance> activeSessions = new ConcurrentHashMap<>();

    /** Active HTTP-stream audio threads (for real URLs). */
    private static final Map<BlockPos, Thread> activeStreamThreads = new ConcurrentHashMap<>();
    private static final Map<BlockPos, javax.sound.sampled.Clip> activeClips = new ConcurrentHashMap<>();

    /** Active TTS threads so we can interrupt them on stop(). */
    private static final Map<BlockPos, Thread> activeTtsThreads = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
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
        activeTtsThreads.clear();
    }

    public static void stop(BlockPos pos) {
        stopInternal(pos);
    }

    private static void stopInternal(BlockPos pos) {
        // 1. Stop SoundInstance (minecraft: events)
        CustomSoundInstance sound = activeSessions.remove(pos);
        if (sound != null) {
            sound.stopSound();
            try { Minecraft.getInstance().getSoundManager().stop(sound); } catch (Throwable ignored) {}
        }

        // 2. Interrupt streaming audio thread
        Thread streamThread = activeStreamThreads.remove(pos);
        if (streamThread != null) {
            streamThread.interrupt();
        }
        // Close clip if any
        javax.sound.sampled.Clip clip = activeClips.remove(pos);
        if (clip != null) {
            try { clip.stop(); clip.close(); } catch (Throwable ignored) {}
        }

        // 3. Interrupt TTS thread + clear narrator queue
        Thread ttsThread = activeTtsThreads.remove(pos);
        if (ttsThread != null) {
            ttsThread.interrupt();
        }
        // Always stop narrator immediately on any stop call
        try {
            com.mojang.text2speech.Narrator nativeNarrator = com.mojang.text2speech.Narrator.getNarrator();
            if (nativeNarrator != null && nativeNarrator.active()) {
                nativeNarrator.clear();
                nativeNarrator.say("", true);
            }
        } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------
    //  TTS
    // ---------------------------------------------------------------

    public static void playTTS(BlockPos pos, String text, double volume, double pitch) {
        // Stop any previously running audio at this position
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

        Thread task = new Thread(() -> {
            try {
                com.mojang.text2speech.Narrator narrator = com.mojang.text2speech.Narrator.getNarrator();
                if (narrator != null && narrator.active()) {
                    narrator.clear();
                    narrator.say(finalText, true);
                }

                int durationMs = Math.max(1000, finalText.length() * 80);
                Thread.sleep(durationMs);

                // Only send finish packet if we weren't interrupted
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && !Thread.currentThread().isInterrupted()) {
                    // Check we're still the active session for this pos
                    if (activeTtsThreads.get(finalPos) == Thread.currentThread()) {
                        activeTtsThreads.remove(finalPos);
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(finalPos));
                    }
                }
            } catch (InterruptedException ignored) {
                // We were stopped — clear narrator immediately
                try {
                    com.mojang.text2speech.Narrator narrator = com.mojang.text2speech.Narrator.getNarrator();
                    if (narrator != null && narrator.active()) {
                        narrator.clear();
                        narrator.say("", true);
                    }
                } catch (Throwable t) { /* ignore */ }
                activeTtsThreads.remove(finalPos);
            }
        });
        task.setName("radiologistics-tts-" + pos.toShortString());
        task.setDaemon(true);
        activeTtsThreads.put(pos, task);
        task.start();
    }

    // ---------------------------------------------------------------
    //  URL / Sound Event playback
    // ---------------------------------------------------------------

    public static void playURL(BlockPos pos, String urlStr, double volume, double pitch, double seekSeconds) {
        // If already playing the same URL (HTTP/file or Minecraft Sound Event)
        if (urlStr.startsWith("http://") || urlStr.startsWith("https://") || urlStr.startsWith("file://")) {
            Thread activeThread = activeStreamThreads.get(pos);
            if (activeThread != null) {
                // Adjust volume of existing clip
                javax.sound.sampled.Clip clip = activeClips.get(pos);
                if (clip != null) {
                    try {
                        javax.sound.sampled.FloatControl gainControl =
                                (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                        float dB = (float)(20.0 * Math.log10(Math.max(0.0001, volume)));
                        float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                        gainControl.setValue(clamped);
                    } catch (Throwable ignored) {}
                }
                return;
            }
        } else {
            CustomSoundInstance existing = activeSessions.get(pos);
            if (existing != null && urlStr.equals(existing.getUrlStr())) {
                existing.setVolume(volume);
                existing.setPitch(pitch);
                return;
            }
        }

        // Stop any current audio at this position
        stop(pos);

        // ---- Attempt 1: Real HTTP/file URL streaming via Java AudioSystem ----
        if (urlStr.startsWith("http://") || urlStr.startsWith("https://") || urlStr.startsWith("file://")) {
            playHttpUrl(pos, urlStr, volume, pitch, seekSeconds);
            return;
        }

        // ---- Attempt 2: Minecraft ResourceLocation SoundEvent ----
        SoundEvent soundEvent = null;
        ResourceLocation rl = ResourceLocation.tryParse(urlStr);
        if (rl != null && BuiltInRegistries.SOUND_EVENT.containsKey(rl)) {
            soundEvent = BuiltInRegistries.SOUND_EVENT.get(rl);
        }
        if (soundEvent == null) {
            // Unknown identifier — play a simple bell as fallback
            Radiologistics.LOGGER.warn("[ClientAudioPlayer] Unknown sound '{}' — using fallback bell", urlStr);
            soundEvent = SoundEvents.NOTE_BLOCK_BELL.value();
        }

        CustomSoundInstance sound = new CustomSoundInstance(soundEvent, SoundSource.RECORDS, pos, urlStr, volume, pitch);
        activeSessions.put(pos, sound);
        Minecraft.getInstance().getSoundManager().play(sound);

        // Timer to send finish packet when the sound finishes (estimate)
        Thread timer = new Thread(() -> {
            try {
                int durationMs = estimateDurationMs(urlStr, pitch);
                Thread.sleep(durationMs);
                if (activeSessions.get(pos) == sound) {
                    activeSessions.remove(pos);
                    sound.stopSound();
                    try { Minecraft.getInstance().getSoundManager().stop(sound); } catch (Throwable ignored) {}
                    if (!Thread.currentThread().isInterrupted()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
                    }
                }
            } catch (InterruptedException ignored) {
                // stopped externally
            }
        });
        timer.setName("radiologistics-url-timer-" + pos.toShortString());
        timer.setDaemon(true);
        timer.start();
    }

    private static void playHttpUrl(BlockPos pos, String urlStr, double volume, double pitch, double seekSeconds) {
        // ---- Pre-flight: detect streaming platforms that require external tools ----
        // The mod only supports direct audio file URLs (e.g. ending in .mp3, .wav, .ogg, .flac)
        // streamed via Java's AudioSystem. Sites like YouTube and SoundCloud serve HTML pages
        // that cannot be decoded by AudioSystem. No external tools (yt-dlp, ffmpeg, PowerShell)
        // are used — this keeps the mod CurseForge-compliant.
        String lower = urlStr.toLowerCase(java.util.Locale.ROOT);
        String unsupportedDomain = null;
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) unsupportedDomain = "YouTube";
        else if (lower.contains("soundcloud.com")) unsupportedDomain = "SoundCloud";
        else if (lower.contains("spotify.com")) unsupportedDomain = "Spotify";
        else if (lower.contains("music.apple.com")) unsupportedDomain = "Apple Music";
        else if (lower.contains("music.youtube.com")) unsupportedDomain = "YouTube Music";
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
                            "[Audio Module] " + platform + " links are not supported. Use a direct audio file URL (.mp3 / .ogg / .wav)."
                        ).withStyle(net.minecraft.ChatFormatting.RED), false);
                }
            });
            return;
        }

        Thread streamThread = new Thread(() -> {
            javax.sound.sampled.AudioInputStream audioIn = null;
            javax.sound.sampled.Clip clip = null;
            try {
                java.net.URL url = new java.net.URL(urlStr);
                // Open connection with a User-Agent so servers don't block Java requests
                java.net.URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Minecraft-AudioModule/1.0)");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();

                // Check Content-Type — bail out early on HTML pages
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

                audioIn = javax.sound.sampled.AudioSystem.getAudioInputStream(conn.getInputStream());

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

                // Seek
                if (seekSeconds > 0) {
                    long seekFrames = (long)(seekSeconds * decodeFormat.getSampleRate());
                    if (seekFrames < clip.getFrameLength()) {
                        clip.setFramePosition((int) seekFrames);
                    }
                }

                // Volume
                try {
                    javax.sound.sampled.FloatControl gainControl =
                            (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                    float dB = (float)(20.0 * Math.log10(Math.max(0.0001, volume)));
                    float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                    gainControl.setValue(clamped);
                } catch (Throwable ignored) {}

                activeClips.put(pos, clip);
                clip.start();

                // Wait until clip finishes or thread is interrupted
                while (!Thread.currentThread().isInterrupted() && clip.isRunning()) {
                    Thread.sleep(100);
                }

                if (!Thread.currentThread().isInterrupted()) {
                    activeStreamThreads.remove(pos);
                    activeClips.remove(pos);
                    clip.stop();
                    clip.close();
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
                }
            } catch (InterruptedException ignored) {
                // stopInternal() interrupted us
                if (clip != null) { try { clip.stop(); clip.close(); } catch (Throwable ignored2) {} }
                activeStreamThreads.remove(pos);
                activeClips.remove(pos);
            } catch (javax.sound.sampled.UnsupportedAudioFileException uafe) {
                Radiologistics.LOGGER.warn("[AudioModule] Unsupported audio format for URL '{}': {}", urlStr, uafe.getMessage());
                activeStreamThreads.remove(pos);
                activeClips.remove(pos);
                Minecraft.getInstance().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                "[Audio Module] Unsupported audio format. Supported: .mp3 / .ogg / .wav / .flac"
                            ).withStyle(net.minecraft.ChatFormatting.RED), false);
                    }
                });
            } catch (Throwable t) {
                Radiologistics.LOGGER.warn("[AudioModule] Failed to stream URL '{}': {}", urlStr, t.getMessage());
                activeStreamThreads.remove(pos);
                activeClips.remove(pos);
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
            }
        });
        streamThread.setName("radiologistics-stream-" + pos.toShortString());
        streamThread.setDaemon(true);
        activeStreamThreads.put(pos, streamThread);
        streamThread.start();
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

    // ---------------------------------------------------------------
    //  CustomSoundInstance — Minecraft SoundEvent wrapper
    // ---------------------------------------------------------------

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

            // Check audio module block is still present
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

            // Note particles
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
}
