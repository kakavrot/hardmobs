package hardmobs;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.EnumSet;

public class ZombieBreakBlockGoal extends Goal {
    private final ZombieEntity zombie;
    private int rageTimer = 0;

    public ZombieBreakBlockGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        // Нам не нужно захватывать MOVE или LOOK, пусть ими управляет ванильный ИИ
        this.setControls(EnumSet.noneOf(Control.class));
    }

    @Override
    public boolean canStart() {
        if (zombie.getWorld().isClient || zombie.getTarget() == null) return false;
        return DifficultyManager.getDay((ServerWorld)zombie.getWorld()) >= 3;
    }

    @Override
    public void tick() {
        if (zombie.getTarget() == null) return;
        ServerWorld world = (ServerWorld) zombie.getWorld();
        long day = DifficultyManager.getDay(world);

        // 1. ТАЙМЕР АННИГИЛЯЦИИ (Копится всегда)
        rageTimer++;

        // Расчет лимита (как договаривались: на 3 день - 20 сек, дальше меньше)
        int secondsLimit = (int) (20 - (day - 3));
        if (secondsLimit < 5) secondsLimit = 5;
        int ticksLimit = secondsLimit * 20;

        // Если время вышло - БАБАХ
        if (rageTimer >= ticksLimit) {
            annihilateSurroundings(world);
            rageTimer = 0; // Сброс для следующего цикла
            zombie.getNavigation().stop(); // Останавливаем зомби, чтобы он "осознал" перемены
            return;
        }

        // 2. АТАКА (Сбрасывает таймер только если удар БЫЛ)
        double dist = zombie.distanceTo(zombie.getTarget());
        double dy = zombie.getTarget().getY() - zombie.getY();

        if (dist < 2.3 && Math.abs(dy) < 1.5) {
            if (zombie.age % 10 == 0) {
                zombie.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                // Если атака успешна (игрок получил урон) — только тогда сброс
                if (zombie.tryAttack(zombie.getTarget())) {
                    rageTimer = 0;
                }
            }
        }
    }


    private void annihilateSurroundings(ServerWorld world) {
        BlockPos center = zombie.getBlockPos();
        int radius = 3; // Увеличили радиус для пущей эффективности

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 3; y++) { // Теперь ломает выше головы (до 3 блоков)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos target = center.add(x, y, z);
                    if (world.getBlockState(target).getHardness(world, target) >= 0) {
                        world.breakBlock(target, true);
                    }
                }
            }
        }
        // Легкий толчок вверх, чтобы не застрял в падающих вещах
        zombie.addVelocity(0, 0.1, 0);
    }

}
