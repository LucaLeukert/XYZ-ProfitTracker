package net.leukert.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

public class MathUtils {

    /**
     * @return the interpolated world‐position of {@link Entity} at partialTicks,
     * lifted by yOffset.
     *
     * @param entity the entity to interpolate
     */
    public static Vec3 interpVec3(Entity entity, float partialTicks, double yOffset) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + yOffset;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        return new Vec3(x, y, z);
    }
}
