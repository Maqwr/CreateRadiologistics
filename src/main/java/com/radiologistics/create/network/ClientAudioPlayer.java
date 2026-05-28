package com.radiologistics.create.network;

import com.radiologistics.create.Radiologistics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Radiologistics.MODID, value = Dist.CLIENT)
public class ClientAudioPlayer {
    private static final Map<BlockPos, AudioSession> activeSessions = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
    }

    public static void stopAll() {
        for (BlockPos pos : activeSessions.keySet()) {
            stop(pos);
        }
        activeSessions.clear();
    }

    public static void playTTS(BlockPos pos, String text, double volume, double pitch) {
        stop(pos);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getNarrator().isActive()) {
            mc.getNarrator().sayNow(text);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(pos));
    }

    public static void playURL(BlockPos pos, String urlStr, double volume, double pitch) {
        stop(pos);

        AudioSession session = new AudioSession(pos, volume, pitch);
        activeSessions.put(pos, session);

        Thread task = new Thread(() -> {
            boolean started = false;
            try {
                String targetUrl = null;
                boolean isMp3 = false;

                String lower = urlStr.toLowerCase();
                if (lower.contains("myinstants.com")) {
                    targetUrl = resolveMyInstants(urlStr);
                    isMp3 = true;
                } else if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
                    targetUrl = resolveCobaltWav(urlStr);
                    isMp3 = false;
                } else if (lower.endsWith(".mp3")) {
                    targetUrl = urlStr;
                    isMp3 = true;
                } else {
                    targetUrl = resolveCobaltWav(urlStr);
                    if (targetUrl == null) {
                        targetUrl = urlStr;
                    }
                    isMp3 = targetUrl.toLowerCase().endsWith(".mp3");
                }

                if (session.isStopped()) return;

                if (targetUrl != null) {
                    startPlayback(session, new URI(targetUrl).toURL(), isMp3);
                    started = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (!started) {
                    activeSessions.remove(pos, session);
                }
            }
        });
        session.setTaskThread(task);
        task.start();
    }

    private static String resolveMyInstants(String originalUrl) {
        if (originalUrl.toLowerCase().endsWith(".mp3")) {
            return originalUrl;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(originalUrl))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String html = response.body();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("href=\"([^\"]*media/sounds/[^\"]+\\.mp3)\"");
                java.util.regex.Matcher matcher = pattern.matcher(html);
                if (matcher.find()) {
                    String path = matcher.group(1);
                    if (!path.startsWith("http")) {
                        if (path.startsWith("/")) {
                            path = "https://www.myinstants.com" + path;
                        } else {
                            path = "https://www.myinstants.com/" + path;
                        }
                    }
                    return path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void stop(BlockPos pos) {
        AudioSession session = activeSessions.remove(pos);
        if (session != null) {
            session.stop();
        }
    }

    private static void startPlayback(AudioSession session, URL url, boolean isMp3) {
        try {
            PlaybackThread t = new PlaybackThread(session, url, isMp3);
            session.setPlaybackThread(t);
            t.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final String[] COBALT_INSTANCES = {
        "https://apicobalt.mgytr.top/",
        "https://api.cobalt.liubquanti.click/"
    };

    private static String resolveCobaltWav(String originalUrl) {
        String escapedUrl = originalUrl.replace("\\", "\\\\").replace("\"", "\\\"");
        String jsonRequest = String.format(
            "{\"url\": \"%s\", \"downloadMode\": \"audio\", \"audioFormat\": \"wav\"}",
            escapedUrl
        );

        for (String instanceUrl : COBALT_INSTANCES) {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(instanceUrl))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    int urlIdx = body.indexOf("\"url\":");
                    if (urlIdx != -1) {
                        int start = body.indexOf("\"", urlIdx + 6) + 1;
                        int end = body.indexOf("\"", start);
                        if (start > 0 && end > start) {
                            return body.substring(start, end).replace("\\/", "/");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static class AudioSession {
        private final BlockPos pos;
        private final double volume;
        private final double pitch;
        private volatile boolean stopped = false;
        private Thread taskThread = null;
        private PlaybackThread playbackThread = null;

        public AudioSession(BlockPos pos, double volume, double pitch) {
            this.pos = pos;
            this.volume = volume;
            this.pitch = pitch;
        }

        public double getVolume() { return volume; }
        public double getPitch() { return pitch; }

        public boolean isStopped() {
            return stopped;
        }

        public synchronized void setTaskThread(Thread t) {
            this.taskThread = t;
            if (stopped && t != null) {
                t.interrupt();
            }
        }

        public synchronized void setPlaybackThread(PlaybackThread t) {
            this.playbackThread = t;
            if (stopped && t != null) {
                t.stopPlayback();
            }
        }

        public synchronized void stop() {
            stopped = true;
            if (taskThread != null) {
                taskThread.interrupt();
            }
            if (playbackThread != null) {
                playbackThread.stopPlayback();
            }
        }
    }

    private static class PlaybackThread extends Thread {
        private final AudioSession session;
        private final URL url;
        private final boolean isMp3;
        private final float customVolume;
        private final float customPitch;
        private volatile boolean running = true;
        private volatile InputStream currentStream;
        private SourceDataLine line;

        public PlaybackThread(AudioSession session, URL url, boolean isMp3) {
            this.session = session;
            this.url = url;
            this.isMp3 = isMp3;
            this.customVolume = (float) session.getVolume();
            this.customPitch = (float) session.getPitch();
        }

        public void stopPlayback() {
            running = false;
            if (line != null) {
                try { line.stop(); } catch (Exception ignored) {}
                try { line.close(); } catch (Exception ignored) {}
            }
            if (currentStream != null) {
                try { currentStream.close(); } catch (Exception ignored) {}
            }
        }

        private InputStream openStream() throws Exception {
            if (url.getProtocol().equalsIgnoreCase("file")) {
                return url.openStream();
            }
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .timeout(java.time.Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .GET()
                .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try { response.body().close(); } catch (Exception ignored) {}
                throw new IOException("Failed to download audio: HTTP " + response.statusCode());
            }
            return response.body();
        }

        @Override
        public void run() {
            boolean firstIteration = true;
            while (running) {
                try {
                    currentStream = openStream();
                    if (!running) {
                        try { currentStream.close(); } catch (Exception ignored) {}
                        break;
                    }

                    if (isMp3) {
                        playMp3Loop(firstIteration);
                    } else {
                        playPcmLoop(firstIteration);
                    }
                    firstIteration = false;
                    if (running) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AudioFinishedPacket(session.pos));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (line != null) {
                        try { line.close(); } catch (Exception ignored) {}
                        line = null;
                    }
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                }

                if (!running) break;
                try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
            }
            if (line != null) {
                try { line.close(); } catch (Exception ignored) {}
            }
            activeSessions.remove(session.pos, session);
        }

        private void playMp3Loop(boolean firstIteration) throws Exception {
            try (InputStream s = currentStream;
                 BufferedInputStream bufIn = new BufferedInputStream(s)) {

                javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(bufIn);
                javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();

                javazoom.jl.decoder.Header header = bitstream.readFrame();
                if (header == null) return;

                int sampleRate = header.sample_frequency();
                javazoom.jl.decoder.SampleBuffer output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                int channels = output.getChannelCount();

                AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, 2, 4, sampleRate, false
                );

                float playSampleRate = format.getSampleRate() * customPitch;
                AudioFormat lineFormat = new AudioFormat(
                    format.getEncoding(),
                    playSampleRate,
                    format.getSampleSizeInBits(),
                    format.getChannels(),
                    format.getFrameSize(),
                    playSampleRate,
                    format.isBigEndian()
                );

                if (line == null || !line.isOpen()) {
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, lineFormat);
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(lineFormat);
                    line.start();
                }

                short[] pcm = output.getBuffer();
                int len = output.getBufferLength();
                int initialWriteLen = len * 2 * (channels == 1 ? 2 : 1);
                byte[] byteBuffer = new byte[initialWriteLen];
                if (channels == 1) {
                    for (int i = 0; i < len; i++) {
                        short sample = pcm[i];
                        byteBuffer[i * 4] = (byte) (sample & 0xff);
                        byteBuffer[i * 4 + 1] = (byte) ((sample >> 8) & 0xff);
                        byteBuffer[i * 4 + 2] = (byte) (sample & 0xff);
                        byteBuffer[i * 4 + 3] = (byte) ((sample >> 8) & 0xff);
                    }
                } else {
                    for (int i = 0; i < len; i++) {
                        short sample = pcm[i];
                        byteBuffer[i * 2] = (byte) (sample & 0xff);
                        byteBuffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
                    }
                }
                if (running) line.write(byteBuffer, 0, initialWriteLen);
                bitstream.closeFrame();

                while (running) {
                    header = bitstream.readFrame();
                    if (header == null) break;

                    output = (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                    pcm = output.getBuffer();
                    len = output.getBufferLength();

                    int writeLen = len * 2 * (channels == 1 ? 2 : 1);
                    if (byteBuffer.length < writeLen) byteBuffer = new byte[writeLen];

                    if (channels == 1) {
                        for (int i = 0; i < len; i++) {
                            short sample = pcm[i];
                            byteBuffer[i * 4] = (byte) (sample & 0xff);
                            byteBuffer[i * 4 + 1] = (byte) ((sample >> 8) & 0xff);
                            byteBuffer[i * 4 + 2] = (byte) (sample & 0xff);
                            byteBuffer[i * 4 + 3] = (byte) ((sample >> 8) & 0xff);
                        }
                    } else {
                        for (int i = 0; i < len; i++) {
                            short sample = pcm[i];
                            byteBuffer[i * 2] = (byte) (sample & 0xff);
                            byteBuffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
                        }
                    }

                    updateVolume();
                    if (running) line.write(byteBuffer, 0, writeLen);
                    bitstream.closeFrame();
                }

                if (running) line.drain();
            }
        }

        private void playPcmLoop(boolean firstIteration) throws Exception {
            try (InputStream s = currentStream;
                 BufferedInputStream bufIn = new BufferedInputStream(s);
                 AudioInputStream in = AudioSystem.getAudioInputStream(bufIn)) {

                AudioFormat baseFormat = in.getFormat();
                AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(), 16,
                    2, 4,
                    baseFormat.getSampleRate(), false
                );

                try (AudioInputStream decodedIn = AudioSystem.getAudioInputStream(targetFormat, in)) {
                    if (!running) return;

                    float playSampleRate = targetFormat.getSampleRate() * customPitch;
                    AudioFormat lineFormat = new AudioFormat(
                        targetFormat.getEncoding(),
                        playSampleRate,
                        targetFormat.getSampleSizeInBits(),
                        targetFormat.getChannels(),
                        targetFormat.getFrameSize(),
                        playSampleRate,
                        targetFormat.isBigEndian()
                    );

                    if (line == null || !line.isOpen()) {
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, lineFormat);
                        line = (SourceDataLine) AudioSystem.getLine(info);
                        line.open(lineFormat);
                        line.start();
                    }

                    byte[] buffer = new byte[4096];
                    int n;
                    while (running && (n = decodedIn.read(buffer, 0, buffer.length)) != -1) {
                        updateVolume();
                        if (running) line.write(buffer, 0, n);
                    }

                    if (running) line.drain();
                }
            }
        }

        private void updateVolume() {
            if (line == null) {
                return;
            }
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null) {
                    stopPlayback();
                    return;
                }

                mc.execute(() -> {
                    if (mc.level == null) return;

                    net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(session.pos);
                    boolean foundAudioModule = state.getBlock() instanceof com.radiologistics.create.block.AudioModuleBlock;
                    net.minecraft.world.level.block.state.BlockState finalState = state;

                    if (!foundAudioModule) {
                        try {
                            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                            Object companion = companionClass.getField("INSTANCE").get(null);
                            java.lang.reflect.Method getContaining = companionClass.getMethod(
                                "getContaining",
                                net.minecraft.world.level.Level.class,
                                net.minecraft.core.Vec3i.class
                            );
                            Object subLevelAccess = getContaining.invoke(companion, mc.level, session.pos);
                            if (subLevelAccess != null) {
                                foundAudioModule = true;
                                try {
                                    java.lang.reflect.Method getLevelMethod = subLevelAccess.getClass().getMethod("getLevel");
                                    net.minecraft.world.level.Level subLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(subLevelAccess);
                                    if (subLevel != null) {
                                        net.minecraft.world.level.block.state.BlockState subState = subLevel.getBlockState(session.pos);
                                        if (subState.getBlock() instanceof com.radiologistics.create.block.AudioModuleBlock) {
                                            finalState = subState;
                                        }
                                    }
                                } catch (Exception ignored2) {}
                            }
                        } catch (ClassNotFoundException ignored3) {
                        } catch (Exception ignored4) {}
                    }

                    if (!foundAudioModule) {
                        stopPlayback();
                        return;
                    }

                    net.minecraft.util.RandomSource rnd = mc.level.getRandom();
                    if (rnd.nextInt(5) == 0) {
                        net.minecraft.core.Direction facing = net.minecraft.core.Direction.UP;
                        if (finalState != null && finalState.hasProperty(com.radiologistics.create.block.AudioModuleBlock.FACING)) {
                            facing = finalState.getValue(com.radiologistics.create.block.AudioModuleBlock.FACING);
                        }
                        double px = session.pos.getX() + 0.5 - facing.getStepX() * 0.1 + (rnd.nextDouble() - 0.5) * 0.4;
                        double py = session.pos.getY() + 0.5 - facing.getStepY() * 0.1 + (rnd.nextDouble() - 0.5) * 0.4;
                        double pz = session.pos.getZ() + 0.5 - facing.getStepZ() * 0.1 + (rnd.nextDouble() - 0.5) * 0.4;
                        double noteColor = rnd.nextDouble();
                        mc.level.addParticle(
                            net.minecraft.core.particles.ParticleTypes.NOTE,
                            px, py, pz,
                            noteColor, 0.0, 0.0
                        );
                    }
                });

                Vec3 soundPos = new Vec3(session.pos.getX() + 0.5, session.pos.getY() + 0.5, session.pos.getZ() + 0.5);
                try {
                    Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                    Object companion = companionClass.getField("INSTANCE").get(null);
                    java.lang.reflect.Method getContaining = companionClass.getMethod(
                        "getContaining",
                        net.minecraft.world.level.Level.class,
                        net.minecraft.core.Vec3i.class
                    );
                    Object subLevelAccess = getContaining.invoke(companion, mc.level, session.pos);
                    if (subLevelAccess != null) {
                        java.lang.reflect.Method getLevelMethod = subLevelAccess.getClass().getMethod("getLevel");
                        net.minecraft.world.level.Level subLevel = (net.minecraft.world.level.Level) getLevelMethod.invoke(subLevelAccess);
                        if (subLevel != null) {
                            java.lang.reflect.Method projectMethod = companionClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, net.minecraft.world.phys.Vec3.class);
                            net.minecraft.world.phys.Vec3 projected = (net.minecraft.world.phys.Vec3) projectMethod.invoke(companion, subLevel, soundPos);
                            if (projected != null) {
                                soundPos = projected;
                            }
                        }
                    }
                } catch (Exception ignored) {}

                Vec3 pPos = mc.player.position();
                double dist = pPos.distanceTo(soundPos);
                float volumeFactor = (float) Math.max(0.0, Math.min(1.0, 1.0 - (dist / 24.0))) * (customVolume * 0.1f);

                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float db;
                    if (volumeFactor <= 0.0f) {
                        db = -80.0f;
                    } else {
                        db = (float) (Math.log10(volumeFactor) * 20.0);
                        if (db < -80.0f) db = -80.0f;
                        if (db > 0.0f) db = 0.0f;
                    }
                    gainControl.setValue(db);
                }

                if (line.isControlSupported(FloatControl.Type.PAN)) {
                    float pan = 0.0f;
                    if (dist > 0.1) {
                        double dx = soundPos.x - pPos.x;
                        double dz = soundPos.z - pPos.z;
                        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                        if (horizontalDist > 0.1) {
                            Vec3 look = mc.player.getViewVector(1.0f);
                            double lookX = look.x;
                            double lookZ = look.z;
                            double len = Math.sqrt(lookX * lookX + lookZ * lookZ);
                            if (len > 0.01) {
                                double rx = -lookZ / len;
                                double rz = lookX / len;
                                pan = (float) ((dx * rx + dz * rz) / horizontalDist);
                            }
                        }
                    }
                    FloatControl panControl = (FloatControl) line.getControl(FloatControl.Type.PAN);
                    panControl.setValue(Math.max(-1.0f, Math.min(1.0f, pan)));
                }
            } catch (Exception ignored) {}
        }
    }
}
