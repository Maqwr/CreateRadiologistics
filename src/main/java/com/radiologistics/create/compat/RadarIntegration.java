package com.radiologistics.create.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RadarIntegration {

    private static final java.util.Map<NetworkFiltererBlockEntity, RadarTrack> customTargets =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public record TargetInfo(double x, double y, double z, String name) {}

    public static TargetInfo getTargetInfo(BlockEntity be) {
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            RadarTrack active = filterer.activeTrackCache;
            if (active == null) {
                try {
                    java.lang.reflect.Field currenttrackField = NetworkFiltererBlockEntity.class.getDeclaredField("currenttrack");
                    currenttrackField.setAccessible(true);
                    active = (RadarTrack) currenttrackField.get(filterer);
                } catch (Exception ignored) {}
            }
            if (active != null && active.position() != null) {
                return new TargetInfo(
                    active.position().x,
                    active.position().y,
                    active.position().z,
                    getFriendlyName(be, active)
                );
            }
        }
        return getTargetInfoAtIndex(be, 0);
    }

    @SuppressWarnings("unchecked")
    public static List<RadarTrack> getDetectedTracks(NetworkFiltererBlockEntity filterer) {
        List<RadarTrack> results = new ArrayList<>();
        if (filterer == null) return results;
        try {
            RadarTrack customTrack = customTargets.get(filterer);
            if (customTrack != null) {
                java.lang.reflect.Field cachedTracksField = NetworkFiltererBlockEntity.class.getDeclaredField("cachedTracks");
                cachedTracksField.setAccessible(true);
                List<RadarTrack> cachedTracks = (List<RadarTrack>) cachedTracksField.get(filterer);
                if (cachedTracks == null) {
                    cachedTracks = new ArrayList<>();
                    cachedTracksField.set(filterer, cachedTracks);
                }
                boolean found = false;
                for (RadarTrack t : cachedTracks) {
                    if (t.getId().equals("custom_target")) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    cachedTracks.add(0, customTrack);
                }
                filterer.activeTrackCache = customTrack;
                java.lang.reflect.Field currenttrackField = NetworkFiltererBlockEntity.class.getDeclaredField("currenttrack");
                currenttrackField.setAccessible(true);
                currenttrackField.set(filterer, customTrack);
            }

            java.lang.reflect.Field cachedTracksField = NetworkFiltererBlockEntity.class.getDeclaredField("cachedTracks");
            cachedTracksField.setAccessible(true);
            List<RadarTrack> cachedTracks = (List<RadarTrack>) cachedTracksField.get(filterer);
            if (cachedTracks != null) {
                results.addAll(cachedTracks);
            }

            Vec3 radarCenter;
            try {
                java.lang.reflect.Method getRadarMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("getRadar", net.minecraft.server.level.ServerLevel.class);
                getRadarMethod.setAccessible(true);
                com.happysg.radar.block.radar.behavior.IRadar radar = (com.happysg.radar.block.radar.behavior.IRadar) getRadarMethod.invoke(filterer, (net.minecraft.server.level.ServerLevel) filterer.getLevel());
                if (radar != null && radar.getWorldPos() != null) {
                    radarCenter = Vec3.atCenterOf(radar.getWorldPos());
                } else {
                    radarCenter = Vec3.atCenterOf(filterer.getBlockPos());
                }
            } catch (Exception e) {
                radarCenter = Vec3.atCenterOf(filterer.getBlockPos());
            }

            final Vec3 finalCenter = radarCenter;
            results.sort((t1, t2) -> Double.compare(t1.position().distanceToSqr(finalCenter), t2.position().distanceToSqr(finalCenter)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public static List<TargetInfo> getDetectedTargets(BlockEntity be) {
        List<TargetInfo> results = new ArrayList<>();
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            List<RadarTrack> tracks = getDetectedTracks(filterer);
            for (RadarTrack track : tracks) {
                if (track != null && track.position() != null) {
                    results.add(new TargetInfo(
                        track.position().x,
                        track.position().y,
                        track.position().z,
                        getFriendlyName(be, track)
                    ));
                }
            }
        }
        return results;
    }

    public static TargetInfo getTargetInfoAtIndex(BlockEntity be, int index) {
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            List<RadarTrack> tracks = getDetectedTracks(filterer);
            if (index >= 0 && index < tracks.size()) {
                RadarTrack track = tracks.get(index);
                if (track != null && track.position() != null) {
                    return new TargetInfo(
                        track.position().x,
                        track.position().y,
                        track.position().z,
                        getFriendlyName(be, track)
                    );
                }
            }
        }
        return null;
    }

    public static int getTracksCount(BlockEntity be) {
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            return getDetectedTracks(filterer).size();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    public static List<RadarTrack> getFilteredTargets(NetworkFiltererBlockEntity filterer) {
        List<RadarTrack> results = new ArrayList<>();
        if (filterer == null) return results;

        try {
            java.lang.reflect.Field cachedTracksField = NetworkFiltererBlockEntity.class.getDeclaredField("cachedTracks");
            cachedTracksField.setAccessible(true);
            List<RadarTrack> cachedTracks = (List<RadarTrack>) cachedTracksField.get(filterer);

            if (cachedTracks == null || cachedTracks.isEmpty()) return results;

            Set<String> ignoreList = null;
            try {
                java.lang.reflect.Method readIdentMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("readIdentificationFromSlot");
                readIdentMethod.setAccessible(true);
                com.happysg.radar.block.behavior.networks.config.IdentificationConfig ident = (com.happysg.radar.block.behavior.networks.config.IdentificationConfig) readIdentMethod.invoke(filterer);
                if (ident != null) {
                    java.lang.reflect.Method buildIgnoreMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("buildIgnoreList", com.happysg.radar.block.behavior.networks.config.IdentificationConfig.class);
                    buildIgnoreMethod.setAccessible(true);
                    ignoreList = (Set<String>) buildIgnoreMethod.invoke(filterer, ident);
                }
            } catch (Exception ignored) {}

            com.happysg.radar.block.behavior.networks.config.TargetingConfig targeting = null;
            try {
                java.lang.reflect.Field targetingField = NetworkFiltererBlockEntity.class.getDeclaredField("targeting");
                targetingField.setAccessible(true);
                targeting = (com.happysg.radar.block.behavior.networks.config.TargetingConfig) targetingField.get(filterer);
            } catch (Exception ignored) {}

            com.happysg.radar.block.behavior.networks.config.DetectionConfig detection = null;
            try {
                java.lang.reflect.Field detectionCacheField = NetworkFiltererBlockEntity.class.getDeclaredField("detectionCache");
                detectionCacheField.setAccessible(true);
                detection = (com.happysg.radar.block.behavior.networks.config.DetectionConfig) detectionCacheField.get(filterer);
                if (detection == null) {
                    java.lang.reflect.Method readDetectionMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("readDetectionFromSlot");
                    readDetectionMethod.setAccessible(true);
                    detection = (com.happysg.radar.block.behavior.networks.config.DetectionConfig) readDetectionMethod.invoke(filterer);
                }
            } catch (Exception ignored) {}

            List<net.minecraft.world.phys.AABB> safeZones = null;
            try {
                java.lang.reflect.Field safeZonesField = NetworkFiltererBlockEntity.class.getDeclaredField("safeZones");
                safeZonesField.setAccessible(true);
                safeZones = (List<net.minecraft.world.phys.AABB>) safeZonesField.get(filterer);
            } catch (Exception ignored) {}

            Vec3 radarCenter;
            try {
                java.lang.reflect.Method getRadarMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("getRadar", net.minecraft.server.level.ServerLevel.class);
                getRadarMethod.setAccessible(true);
                com.happysg.radar.block.radar.behavior.IRadar radar = (com.happysg.radar.block.radar.behavior.IRadar) getRadarMethod.invoke(filterer, (net.minecraft.server.level.ServerLevel) filterer.getLevel());
                if (radar != null && radar.getWorldPos() != null) {
                    radarCenter = Vec3.atCenterOf(radar.getWorldPos());
                } else {
                    radarCenter = Vec3.atCenterOf(filterer.getBlockPos());
                }
            } catch (Exception e) {
                radarCenter = Vec3.atCenterOf(filterer.getBlockPos());
            }

            net.minecraft.server.level.ServerLevel serverLevel = filterer.getLevel() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null;

            for (RadarTrack t : cachedTracks) {
                if (t != null && t.position() != null) {
                    if (t.getId().equals("custom_target")) {
                        results.add(t);
                        continue;
                    }

                    if (targeting != null && !targeting.test(t.trackCategory())) {
                        continue;
                    }

                    if (safeZones != null && !safeZones.isEmpty()) {
                        if (com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper.isInSafeZone(t.position(), safeZones)) {
                            continue;
                        }
                    }

                    if (ignoreList != null && !ignoreList.isEmpty() && serverLevel != null) {
                        if (com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper.isIgnoredByIdentification(t, serverLevel, ignoreList)) {
                            continue;
                        }
                    }

                    if (detection != null) {
                        if (!detection.test(t)) {
                            continue;
                        }
                    }
                    results.add(t);
                }
            }

            final Vec3 finalCenter = radarCenter;
            results.sort((t1, t2) -> Double.compare(t1.position().distanceToSqr(finalCenter), t2.position().distanceToSqr(finalCenter)));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    public static RadarTrack getCustomTarget(NetworkFiltererBlockEntity filterer) {
        return customTargets.get(filterer);
    }

    public static String getFriendlyName(BlockEntity be, RadarTrack track) {
        if (track == null) return "";
        if (track.getId().startsWith("custom_target")) {
            return track.entityType();
        }
        String typeStr = track.entityType();
        if (typeStr == null) return "";

        if (typeStr.equals("minecraft:player") || typeStr.equals("player")) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(track.id());
                if (be.getLevel() != null) {
                    net.minecraft.world.entity.player.Player player = be.getLevel().getPlayerByUUID(uuid);
                    if (player != null) {
                        return player.getGameProfile().getName();
                    }
                }
            } catch (Exception ignored) {}
            return "Player";
        }

        String name = typeStr;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }
        name = name.replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static RadarTrack getActiveOrAutoTarget(NetworkFiltererBlockEntity filterer) {
        List<RadarTrack> tracks = getFilteredTargets(filterer);
        if (tracks.isEmpty()) return null;
        return tracks.get(0);
    }

    @SuppressWarnings("unchecked")
    public static void setCustomTarget(BlockEntity be, double x, double y, double z, String name) {
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            try {
                net.minecraft.world.phys.Vec3 pos = new net.minecraft.world.phys.Vec3(x, y, z);
                net.minecraft.world.phys.Vec3 vel = new net.minecraft.world.phys.Vec3(0, 0, 0);

                String customId = "custom_target_" + x + "_" + y + "_" + z;

                RadarTrack customTrack = new RadarTrack(
                    customId,
                    pos,
                    vel,
                    filterer.getLevel().getGameTime(),
                    com.happysg.radar.block.radar.track.TrackCategory.MISC,
                    name,
                    1.0f
                );

                customTargets.put(filterer, customTrack);

                java.lang.reflect.Field selectedWasAutoField = NetworkFiltererBlockEntity.class.getDeclaredField("selectedWasAuto");
                selectedWasAutoField.setAccessible(true);
                selectedWasAutoField.set(filterer, false);

                java.lang.reflect.Field currenttrackField = NetworkFiltererBlockEntity.class.getDeclaredField("currenttrack");
                currenttrackField.setAccessible(true);
                currenttrackField.set(filterer, customTrack);

                filterer.activeTrackCache = customTrack;

                if (filterer.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    com.happysg.radar.block.behavior.networks.NetworkData networkData = com.happysg.radar.block.behavior.networks.NetworkData.get(serverLevel);
                    com.happysg.radar.block.behavior.networks.NetworkData.Group group = networkData.getOrCreateGroup(serverLevel.dimension(), filterer.getBlockPos());
                    group.selectedTargetId = customId;
                    networkData.setDirty();
                }

                java.lang.reflect.Field cachedTracksField = NetworkFiltererBlockEntity.class.getDeclaredField("cachedTracks");
                cachedTracksField.setAccessible(true);
                List<RadarTrack> tracks = (List<RadarTrack>) cachedTracksField.get(filterer);
                if (tracks == null) {
                    tracks = new ArrayList<>();
                    cachedTracksField.set(filterer, tracks);
                }

                tracks.removeIf(t -> t.getId().startsWith("custom_target"));

                tracks.add(0, customTrack);

                java.lang.reflect.Method pushMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("pushToEndpoints", RadarTrack.class);
                pushMethod.setAccessible(true);
                pushMethod.invoke(filterer, customTrack);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void clearCustomTarget(BlockEntity be) {
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            customTargets.remove(filterer);
            try {

                java.lang.reflect.Field currenttrackField = NetworkFiltererBlockEntity.class.getDeclaredField("currenttrack");
                currenttrackField.setAccessible(true);
                RadarTrack current = (RadarTrack) currenttrackField.get(filterer);
                if (current != null && current.getId().startsWith("custom_target")) {
                    currenttrackField.set(filterer, null);
                }
                if (filterer.activeTrackCache != null && filterer.activeTrackCache.getId().startsWith("custom_target")) {
                    filterer.activeTrackCache = null;
                }

                java.lang.reflect.Field cachedTracksField = NetworkFiltererBlockEntity.class.getDeclaredField("cachedTracks");
                cachedTracksField.setAccessible(true);
                List<RadarTrack> tracks = (List<RadarTrack>) cachedTracksField.get(filterer);
                if (tracks != null) {
                    tracks.removeIf(t -> t.getId().startsWith("custom_target"));
                }

                if (filterer.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    com.happysg.radar.block.behavior.networks.NetworkData networkData = com.happysg.radar.block.behavior.networks.NetworkData.get(serverLevel);
                    com.happysg.radar.block.behavior.networks.NetworkData.Group group = networkData.getGroup(serverLevel.dimension(), filterer.getBlockPos());
                    if (group != null && group.selectedTargetId != null && group.selectedTargetId.startsWith("custom_target")) {
                        group.selectedTargetId = null;
                        networkData.setDirty();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
