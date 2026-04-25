package hardmobs;

import net.minecraft.server.world.ServerWorld;

public class DifficultyManager {
    public static long getDay(ServerWorld world) {
        // Используем Math.floor, чтобы корректно считать прошедшие дни
        return (long) Math.floor(world.getTimeOfDay() / 24000.0) + 1;
    }

    public static float getMultiplier(ServerWorld world) {
        long day = getDay(world);
        // Важно: 0.05f — это 5%. На 1-й день будет (1 * 0.05) = 0.05.
        // Итоговый множитель: 1.05
        return 1.0f + (day * 0.05f);
    }
}
