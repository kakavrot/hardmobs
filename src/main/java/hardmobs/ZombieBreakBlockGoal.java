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

        // Считаем тики, если зомби стоит на месте (застрял)
        if (zombie.getVelocity().horizontalLengthSquared() < 0.001) {
            rageTimer++;
        } else {
            // Если он хоть немного движется, таймер копится в 5 раз медленнее
            rageTimer += 0; // Или можно поставить 1, если хочешь чтобы он все равно бахал со временем
        }

        // Расчет времени взрыва
        int secondsLimit = (int) (20 - (day - 3));
        if (secondsLimit < 5) secondsLimit = 5;
        int ticksLimit = secondsLimit * 20;

        if (rageTimer >= ticksLimit) {
            annihilateSurroundings(world);
            rageTimer = 0;
        }

        // Если зомби удалось ударить игрока, ванильный ИИ это сделает сам,
        // а мы просто сбросим таймер по факту близости
        if (zombie.distanceTo(zombie.getTarget()) < 2.0) {
            rageTimer = 0;
        }
    }

    private void annihilateSurroundings(ServerWorld world) {
        BlockPos center = zombie.getBlockPos();
        int radius = 2;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= radius + 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos target = center.add(x, y, z);
                    if (world.getBlockState(target).getHardness(world, target) >= 0) {
                        world.breakBlock(target, true);
                    }
                }
            }
        }
    }
}
