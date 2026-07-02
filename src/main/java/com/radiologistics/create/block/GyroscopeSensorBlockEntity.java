package com.radiologistics.create.block;

import com.radiologistics.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GyroscopeSensorBlockEntity extends BaseModuleBlockEntity {
    public GyroscopeSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GYROSCOPE_SENSOR.get(), pos, state);
    }

    public float[] getContraptionRotation() {
        return getContraptionRotation(this.level);
    }

    private static long lastLogTime = 0;

    public float[] getContraptionRotation(Level fallbackLevel) {
        Level activeLevel = level != null ? level : fallbackLevel;
        if (activeLevel == null) return new float[]{0, 0, 0};
        if (com.radiologistics.create.Radiologistics.isServerStopping) {
            return new float[]{0, 0, 0};
        }

        long now = System.currentTimeMillis();
        boolean shouldLog = (now - lastLogTime > 5000);
        if (shouldLog) {
            lastLogTime = now;
            com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: activeLevel=" + activeLevel.getClass().getName() + " level=" + (level != null ? level.getClass().getName() : "null") + " fallback=" + (fallbackLevel != null ? fallbackLevel.getClass().getName() : "null"));
        }

        try {
            Class<?> subLevelClass = null;
            Method getLevelMethod = null;
            try {
                subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                getLevelMethod = subLevelClass.getMethod("getLevel");
            } catch (ClassNotFoundException | LinkageError e) {
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: SubLevel class not found: " + e.getMessage());
                }
            }

            double accumW = 1.0, accumX = 0.0, accumY = 0.0, accumZ = 0.0;
            boolean hasSublevel = false;

            if (subLevelClass != null) {
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: SubLevel class is present");
                }
                Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                Object companion = companionClass.getField("INSTANCE").get(null);
                Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);

                Level currLevel = activeLevel;
                Object currSub = null;

                boolean isSub = subLevelClass.isInstance(currLevel) || com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(currLevel);
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: isSubLevel(currLevel)=" + isSub);
                }
                if (isSub) {
                    currSub = currLevel;
                } else {
                    currSub = getContainingMethod.invoke(companion, currLevel, worldPosition);
                    if (shouldLog) {
                        com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: getContaining returned=" + (currSub != null ? currSub.getClass().getName() : "null") + " pos=" + worldPosition);
                    }
                }

                while (currSub != null && (subLevelClass.isInstance(currSub) || (currSub instanceof Level && com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel((Level) currSub)))) {
                    Object quat = getQuaternionFromSublevel(currSub);
                    if (shouldLog) {
                        com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: currSub=" + currSub.getClass().getName() + " quat=" + (quat != null ? quat.getClass().getName() : "null"));
                    }
                    if (quat != null) {
                        double x = getDoubleValue(quat, "x");
                        double y = getDoubleValue(quat, "y");
                        double z = getDoubleValue(quat, "z");
                        double w = getDoubleValue(quat, "w");
                        if (shouldLog) {
                            com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: quat values: x=" + x + " y=" + y + " z=" + z + " w=" + w);
                        }

                        double nw = w * accumW - x * accumX - y * accumY - z * accumZ;
                        double nx = w * accumX + x * accumW + y * accumZ - z * accumY;
                        double ny = w * accumY - x * accumZ + y * accumW + z * accumX;
                        double nz = w * accumZ + x * accumY - y * accumX + z * accumW;

                        accumW = nw;
                        accumX = nx;
                        accumY = ny;
                        accumZ = nz;
                        hasSublevel = true;
                    }

                    currLevel = (Level) getLevelMethod.invoke(currSub);
                    if (shouldLog) {
                        com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: parent level=" + (currLevel != null ? currLevel.getClass().getName() : "null"));
                    }
                    if (currLevel != null && (subLevelClass.isInstance(currLevel) || com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(currLevel))) {
                        currSub = currLevel;
                    } else {
                        currSub = null;
                    }
                }
            } else {
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: Fallback branch");
                }
                Class<?> subLevelAccessClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
                Object subLevelAccess = null;

                if (subLevelAccessClass.isInstance(activeLevel) || com.radiologistics.create.block.MainComputerBlockEntity.isSableSubLevel(activeLevel)) {
                    subLevelAccess = activeLevel;
                } else {
                    Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                    Object companion = companionClass.getField("INSTANCE").get(null);
                    Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);
                    subLevelAccess = getContainingMethod.invoke(companion, activeLevel, worldPosition);
                }

                if (subLevelAccess != null) {
                    Object quat = getQuaternionFromSublevel(subLevelAccess);
                    if (shouldLog) {
                        com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: subLevelAccess=" + subLevelAccess.getClass().getName() + " quat=" + (quat != null ? quat.getClass().getName() : "null"));
                    }
                    if (quat != null) {
                        accumX = getDoubleValue(quat, "x");
                        accumY = getDoubleValue(quat, "y");
                        accumZ = getDoubleValue(quat, "z");
                        accumW = getDoubleValue(quat, "w");
                        hasSublevel = true;
                        if (shouldLog) {
                            com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: fallback quat values: x=" + accumX + " y=" + accumY + " z=" + accumZ + " w=" + accumW);
                        }
                    }
                }
            }

            if (hasSublevel) {

                net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
                BlockState state = getBlockState();
                if (state != null && state.hasProperty(GyroscopeSensorBlock.FACING)) {
                    facing = state.getValue(GyroscopeSensorBlock.FACING);
                }

                double rw = 1.0;
                double ry = 0.0;
                if (facing == net.minecraft.core.Direction.SOUTH) {
                    rw = 0.0;
                    ry = 1.0;
                } else if (facing == net.minecraft.core.Direction.EAST) {
                    rw = 0.7071067811865476;
                    ry = -0.7071067811865475;
                } else if (facing == net.minecraft.core.Direction.WEST) {
                    rw = 0.7071067811865476;
                    ry = 0.7071067811865475;
                }

                double bw = accumW * rw - accumY * ry;
                double bx = accumX * rw - accumZ * ry;
                double by = accumY * rw + accumW * ry;
                double bz = accumZ * rw + accumX * ry;

                double yaw = Math.atan2(2 * (bw * bz + bx * by), 1 - 2 * (by * by + bz * bz));
                double pitchX = Math.asin(Math.max(-1.0, Math.min(1.0, 2 * (bw * bx - by * bz))));
                double pitchZ = Math.atan2(2 * (bw * by + bz * bx), 1 - 2 * (bx * bx + by * by));

                float[] result = new float[]{
                    (float) Math.toDegrees(pitchX),
                    (float) Math.toDegrees(pitchZ),
                    (float) Math.toDegrees(yaw)
                };
                if (shouldLog) {
                    com.radiologistics.create.Radiologistics.LOGGER.info("DEBUG GYRO: result: pitchX=" + result[0] + " pitchZ=" + result[1] + " yaw=" + result[2]);
                }
                return result;
            }
        } catch (ClassNotFoundException | LinkageError ignored) {

        } catch (Exception e) {
            if (shouldLog) {
                com.radiologistics.create.Radiologistics.LOGGER.error("DEBUG GYRO: exception: " + e.getMessage(), e);
            }
        }
        return new float[]{0, 0, 0};
    }

    private Object getQuaternionFromSublevel(Object sublevel) {
        if (sublevel == null) return null;

        try {
            Method poseMethod = null;
            for (Method m : sublevel.getClass().getMethods()) {
                if (m.getName().equals("getLogicalPose") || m.getName().equals("logicalPose")) {
                    poseMethod = m;
                    break;
                }
            }
            if (poseMethod != null) {
                poseMethod.setAccessible(true);
                Object poseObj = poseMethod.invoke(sublevel);
                if (poseObj != null) {
                    Method rotMethod = null;
                    for (Method m : poseObj.getClass().getMethods()) {
                        String name = m.getName();
                        if (name.equals("getRotation") || name.equals("rotation") ||
                            name.equals("getOrientation") || name.equals("orientation")) {
                            rotMethod = m;
                            break;
                        }
                    }
                    if (rotMethod == null) {
                        for (Method m : poseObj.getClass().getMethods()) {
                            if (m.getReturnType().getSimpleName().contains("Quaternion")) {
                                rotMethod = m;
                                break;
                            }
                        }
                    }
                    if (rotMethod != null) {
                        rotMethod.setAccessible(true);
                        return rotMethod.invoke(poseObj);
                    } else {
                        com.radiologistics.create.Radiologistics.LOGGER.warn("DEBUG GYRO: rotMethod not found on " + poseObj.getClass().getName());
                    }
                } else {
                    com.radiologistics.create.Radiologistics.LOGGER.warn("DEBUG GYRO: poseObj is null");
                }
            } else {
                com.radiologistics.create.Radiologistics.LOGGER.warn("DEBUG GYRO: poseMethod not found on " + sublevel.getClass().getName());
            }
        } catch (Throwable e) {
            com.radiologistics.create.Radiologistics.LOGGER.error("DEBUG GYRO: getQuaternionFromSublevel error: " + e.getMessage(), e);
        }
        return null;
    }

    private double getDoubleValue(Object obj, String name) {
        if (obj == null) return 0.0;
        if (obj instanceof org.joml.Quaterniondc qd) {
            if (name.equals("x")) return qd.x();
            if (name.equals("y")) return qd.y();
            if (name.equals("z")) return qd.z();
            if (name.equals("w")) return qd.w();
        }
        if (obj instanceof org.joml.Quaternionfc qf) {
            if (name.equals("x")) return qf.x();
            if (name.equals("y")) return qf.y();
            if (name.equals("z")) return qf.z();
            if (name.equals("w")) return qf.w();
        }
        try {
            try {
                Field f = obj.getClass().getField(name);
                f.setAccessible(true);
                return f.getDouble(obj);
            } catch (NoSuchFieldException e) {
                Method m = obj.getClass().getMethod(name);
                m.setAccessible(true);
                return ((Number) m.invoke(obj)).doubleValue();
            }
        } catch (Exception e) {

            for (Class<?> iface : obj.getClass().getInterfaces()) {
                try {
                    Method m = iface.getMethod(name);
                    m.setAccessible(true);
                    return ((Number) m.invoke(obj)).doubleValue();
                } catch (Exception ignored) {}
            }
            return 0.0;
        }
    }
}
