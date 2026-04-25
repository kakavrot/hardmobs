package hardmobs;

import net.minecraft.server.world.ServerWorld;

public class DifficultyManager {
    // Статическая переменная для хранения последнего вычисленного дня.
    // По умолчанию 1, чтобы миксины не получили 0 при старте.
    private static long lastMeasuredDay = 1;

    /**
     * Вычисляет текущий день и обновляет статическую переменную.
     * @param world Серверный мир
     * @return Текущий день (начиная с 1)
     */
    public static long getDay(ServerWorld world) {
        // getTimeOfDay() возвращает общее время мира в тиках.
        // В сутках 24000 тиков. Добавляем 1, чтобы первый день был 1, а не 0.
        lastMeasuredDay = (world.getTimeOfDay() / 24000L) + 1;
        return lastMeasuredDay;
    }

    /**
     * Позволяет получить номер дня там, где нет прямого доступа к объекту World.
     * Используется в SpawnGroupMixin.
     */
    public static long getLastMeasuredDay() {
        return lastMeasuredDay;
    }
}
