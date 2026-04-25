package hardmobs;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;


public class SkeletonExplosiveArrowGoal extends Goal {
    private final SkeletonEntity skeleton;
    private int cooldown = 0;

    public SkeletonExplosiveArrowGoal(SkeletonEntity skeleton) {
        this.skeleton = skeleton;
    }

    @Override
    public boolean canStart() {
        return skeleton.getTarget() != null && !skeleton.getWorld().isClient;
    }

    @Override
    public void tick() {
        if (skeleton.getTarget() == null) return;

        ServerWorld world = (ServerWorld) skeleton.getWorld();
        long day = DifficultyManager.getDay(world);

        // Работает только с 3-го дня
        if (day < 3) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        double dist = skeleton.distanceTo(skeleton.getTarget());

        // Если скелет готов выстрелить (имитируем тайминг стрельбы)
        if (dist < 15.0 && skeleton.age % 40 == 0) {

            // Расчет шанса: 2% + (день - 3)%
            double chance = 0.02 + (day - 3) * 0.01;
            if (chance > 0.5) chance = 0.5; // Ограничим до 50%, чтобы не было безумия

            if (world.random.nextDouble() < chance) {
                shootExplosive(world);
                cooldown = 100; // Кулдаун между взрывными стрелами (5 секунд)
            }
        }
    }

    private void shootExplosive(ServerWorld world) {
        if (skeleton.getTarget() == null) return;

        // Вместо создания сложной сущности стрелы, мы создаем взрыв в точке игрока
        // Но с небольшой задержкой или траекторией (для простоты - в позиции цели)
        BlockPos targetPos = skeleton.getTarget().getBlockPos();

        // Визуальный эффект выстрела
        skeleton.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        // Создаем взрыв силой с крипера (3.0F)
        world.createExplosion(skeleton, targetPos.getX(), targetPos.getY(), targetPos.getZ(), 3.0F, World.ExplosionSourceType.MOB);
    }
}
