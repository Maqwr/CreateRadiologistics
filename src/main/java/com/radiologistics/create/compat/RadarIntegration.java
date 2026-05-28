package com.radiologistics.create.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;

public class RadarIntegration {

    public record TargetInfo(double x, double y, double z, String name) {}

    public static TargetInfo getTargetInfo(BlockEntity be) {
        if (be instanceof NetworkFiltererBlockEntity filterer) {
            RadarTrack track = getActiveOrAutoTarget(filterer);
            if (track != null && track.position() != null) {
                return new TargetInfo(
                    track.position().x,
                    track.position().y,
                    track.position().z,
                    track.id() != null ? track.id() : ""
                );
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static RadarTrack getActiveOrAutoTarget(NetworkFiltererBlockEntity filterer) {
        if (filterer == null) return null;
        
        RadarTrack track = filterer.activeTrackCache;
        if (track != null) return track;
        
        try {
            java.lang.reflect.Field targetingField = NetworkFiltererBlockEntity.class.getDeclaredField("targeting");
            targetingField.setAccessible(true);
            com.happysg.radar.block.behavior.networks.config.TargetingConfig targeting = (com.happysg.radar.block.behavior.networks.config.TargetingConfig) targetingField.get(filterer);
            
            if (targeting != null && targeting.autoTarget()) {
                java.lang.reflect.Field cachedTracksField = NetworkFiltererBlockEntity.class.getDeclaredField("cachedTracks");
                cachedTracksField.setAccessible(true);
                java.util.List<RadarTrack> cachedTracks = (java.util.List<RadarTrack>) cachedTracksField.get(filterer);
                
                if (cachedTracks != null && !cachedTracks.isEmpty()) {
                    java.util.List<net.minecraft.world.phys.AABB> safeZones = null;
                    try {
                        java.lang.reflect.Field safeZonesField = NetworkFiltererBlockEntity.class.getDeclaredField("safeZones");
                        safeZonesField.setAccessible(true);
                        safeZones = (java.util.List<net.minecraft.world.phys.AABB>) safeZonesField.get(filterer);
                    } catch (Exception ignored) {}

                    java.util.Set<java.lang.String> ignoreList = null;
                    try {
                        java.lang.reflect.Method readIdentMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("readIdentificationFromSlot");
                        readIdentMethod.setAccessible(true);
                        com.happysg.radar.block.behavior.networks.config.IdentificationConfig ident = (com.happysg.radar.block.behavior.networks.config.IdentificationConfig) readIdentMethod.invoke(filterer);
                        
                        if (ident != null) {
                            java.lang.reflect.Method buildIgnoreMethod = NetworkFiltererBlockEntity.class.getDeclaredMethod("buildIgnoreList", com.happysg.radar.block.behavior.networks.config.IdentificationConfig.class);
                            buildIgnoreMethod.setAccessible(true);
                            ignoreList = (java.util.Set<java.lang.String>) buildIgnoreMethod.invoke(filterer, ident);
                        }
                    } catch (Exception ignored) {}

                    Vec3 filtererCenter = Vec3.atCenterOf(filterer.getBlockPos());
                    RadarTrack closestTrack = null;
                    double closestDistSq = Double.MAX_VALUE;
                    
                    for (RadarTrack t : cachedTracks) {
                        if (t != null && t.position() != null) {
                            if (!targeting.test(t.trackCategory())) {
                                continue;
                            }
                            if (safeZones != null && !safeZones.isEmpty()) {
                                if (com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper.isInSafeZone(t.position(), safeZones)) {
                                    continue;
                                }
                            }
                            if (ignoreList != null && !ignoreList.isEmpty() && filterer.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                                if (com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper.isIgnoredByIdentification(t, serverLevel, ignoreList)) {
                                    continue;
                                }
                            }
                            
                            double distSq = t.position().distanceToSqr(filtererCenter);
                            if (distSq < closestDistSq) {
                                closestDistSq = distSq;
                                closestTrack = t;
                            }
                        }
                    }
                    return closestTrack;
                }
            }
        } catch (Exception ignored) {}
        
        return null;
    }
}
