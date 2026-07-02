package dev.ryanhcode.sable.companion;

import net.minecraft.world.level.Level;
import net.minecraft.core.Vec3i;

public class SableCompanion {
    public static SableCompanion INSTANCE = new SableCompanion();
    
    
    public static SubLevelAccess mockSubLevelAccess = null;

    public Object getContaining(Level level, Vec3i pos) {
        return mockSubLevelAccess;
    }
}
