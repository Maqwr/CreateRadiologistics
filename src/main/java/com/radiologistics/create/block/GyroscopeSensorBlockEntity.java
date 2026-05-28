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

        try {
            Class<?> subLevelAccessClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
            Object subLevelAccess = null;

            if (subLevelAccessClass.isInstance(level)) {
                subLevelAccess = level;
            } else {
                // 1. Try to get SableCompanion class
                Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                Object companion = companionClass.getField("INSTANCE").get(null);
                
                // 2. Call getContaining(level, pos)
                Method getContainingMethod = companionClass.getMethod("getContaining", Level.class, net.minecraft.core.Vec3i.class);
                subLevelAccess = getContainingMethod.invoke(companion, level, worldPosition);
            }
            
            if (subLevelAccess != null) {
                // 3. Find logicalPose() or getLogicalPose()
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
                        // 4. Find rotation() or getRotation() or orientation()
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

                                // Multiply Q_ship * R_local
                                double bw = w * rw - y * ry;
                                double bx = x * rw - z * ry;
                                double by = y * rw + w * ry;
                                double bz = z * rw + x * ry;

                                // Standard Quaternion to Euler angles conversion on modified quaternion
                                double yaw = Math.atan2(2 * (bw * bz + bx * by), 1 - 2 * (by * by + bz * bz));
                                double pitchX = Math.asin(Math.max(-1.0, Math.min(1.0, 2 * (bw * bx - by * bz))));
                                double pitchZ = Math.atan2(2 * (bw * by + bz * bx), 1 - 2 * (bx * bx + by * by));
                                
                                return new float[]{
                                    (float) Math.toDegrees(yaw),     // Новий Pitch X = Теперішній Yaw
                                    (float) Math.toDegrees(pitchX),  // Новий Pitch Z = Теперішній Pitch X
                                    (float) Math.toDegrees(pitchZ)   // Новий Yaw = Теперішній Pitch Z
                                };
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
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
