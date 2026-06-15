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

    /**
     * Gets the rotation of the moving vehicle (Pitch X, Pitch Z, Yaw) in degrees.
     * Returns [Pitch X, Pitch Z, Yaw]. Defaults to [0, 0, 0] if not on a physics contraption.
     */
    public float[] getContraptionRotation() {
        if (level == null) return new float[]{0, 0, 0};
        if (com.radiologistics.create.Radiologistics.isServerStopping) {
            return new float[]{0, 0, 0};
        }

        try {
            Class<?> subLevelClass = null;
            Method getLevelMethod = null;
            try {
                subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                getLevelMethod = subLevelClass.getMethod("getLevel");
            } catch (ClassNotFoundException | LinkageError ignored) {}

            double accumW = 1.0, accumX = 0.0, accumY = 0.0, accumZ = 0.0;
            boolean hasSublevel = false;

            if (subLevelClass != null) {
                // Nested contraption logic
                Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                Object companion = companionClass.getField("INSTANCE").get(null);
                Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);

                Level currLevel = level;
                Object currSub = null;

                if (subLevelClass.isInstance(currLevel)) {
                    currSub = currLevel;
                } else {
                    currSub = getContainingMethod.invoke(companion, currLevel, worldPosition);
                }

                while (currSub != null && subLevelClass.isInstance(currSub)) {
                    // Find logicalPose() or getLogicalPose()
                    Method poseMethod = null;
                    for (Method m : currSub.getClass().getMethods()) {
                        if (m.getName().equals("getLogicalPose") || m.getName().equals("logicalPose")) {
                            poseMethod = m;
                            break;
                        }
                    }
                    
                    if (poseMethod != null) {
                        Object poseObj = poseMethod.invoke(currSub);
                        if (poseObj != null) {
                            // Find rotation() or getRotation() or orientation()
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
                                Object quat = rotMethod.invoke(poseObj);
                                if (quat != null) {
                                    double x = getDoubleValue(quat, "x");
                                    double y = getDoubleValue(quat, "y");
                                    double z = getDoubleValue(quat, "z");
                                    double w = getDoubleValue(quat, "w");
                                    
                                    // Multiply: Q_new = Q_current_sublevel * Q_accumulated
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
                            }
                        }
                    }
                    
                    // Traverse to parent level
                    currLevel = (Level) getLevelMethod.invoke(currSub);
                    if (currLevel != null && subLevelClass.isInstance(currLevel)) {
                        currSub = currLevel;
                    } else {
                        currSub = null;
                    }
                }
            } else {
                // Fallback / Single-level logic (e.g. for testing where SubLevel class cannot be fully loaded)
                Class<?> subLevelAccessClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
                Object subLevelAccess = null;

                if (subLevelAccessClass.isInstance(level)) {
                    subLevelAccess = level;
                } else {
                    Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                    Object companion = companionClass.getField("INSTANCE").get(null);
                    Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);
                    subLevelAccess = getContainingMethod.invoke(companion, level, worldPosition);
                }
                
                if (subLevelAccess != null) {
                    Method poseMethod = null;
                    for (Method m : subLevelAccess.getClass().getMethods()) {
                        if (m.getName().equals("getLogicalPose") || m.getName().equals("logicalPose")) {
                            poseMethod = m;
                            break;
                        }
                    }
                    if (poseMethod != null) {
                        Object poseObj = poseMethod.invoke(subLevelAccess);
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
                                Object quat = rotMethod.invoke(poseObj);
                                if (quat != null) {
                                    accumX = getDoubleValue(quat, "x");
                                    accumY = getDoubleValue(quat, "y");
                                    accumZ = getDoubleValue(quat, "z");
                                    accumW = getDoubleValue(quat, "w");
                                    hasSublevel = true;
                                }
                            }
                        }
                    }
                }
            }

            if (hasSublevel) {
                // Determine local block orientation relative to the ship
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

                // Multiply Q_accumulated * R_local
                double bw = accumW * rw - accumY * ry;
                double bx = accumX * rw - accumZ * ry;
                double by = accumY * rw + accumW * ry;
                double bz = accumZ * rw + accumX * ry;

                // Standard Quaternion to Euler angles conversion on modified quaternion
                double yaw = Math.atan2(2 * (bw * bz + bx * by), 1 - 2 * (by * by + bz * bz));
                double pitchX = Math.asin(Math.max(-1.0, Math.min(1.0, 2 * (bw * bx - by * bz))));
                double pitchZ = Math.atan2(2 * (bw * by + bz * bx), 1 - 2 * (bx * bx + by * by));
                
                return new float[]{
                    (float) Math.toDegrees(yaw),     // Pitch X
                    (float) Math.toDegrees(pitchX),  // Pitch Z
                    (float) Math.toDegrees(pitchZ)   // Yaw
                };
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Sable is not loaded
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new float[]{0, 0, 0};
    }

    private double getDoubleValue(Object obj, String name) {
        try {
            try {
                Field f = obj.getClass().getField(name);
                return f.getDouble(obj);
            } catch (NoSuchFieldException e) {
                Method m = obj.getClass().getMethod(name);
                return ((Number) m.invoke(obj)).doubleValue();
            }
        } catch (Exception e) {
            return 0.0;
        }
    }
}
