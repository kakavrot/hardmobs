package hardmobs;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.EnumSet;

public class ZombieBreakBlockGoal extends Goal {
    private final ZombieEntity zombie;
    private int rageTimer = 0;
    private int buildCooldown = 0;

    public ZombieBreakBlockGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        if (zombie.getWorld().isClient || zombie.getTarget() == null) return false;
        // Работает только с 3-го дня
        return DifficultyManager.getDay((ServerWorld)zombie.getWorld()) >= 3;
    }

    @Override
    public boolean shouldContinue() {
        return zombie.getTarget() != null && !zombie.getWorld().isClient;
    }

    private double lastY = -999; // Добавь это поле в начало класса

    @Override
    public void tick() {
        if (zombie.getTarget() == null) return;
        ServerWorld world = (ServerWorld) zombie.getWorld();

        double dist = zombie.distanceTo(zombie.getTarget());
        double dy = zombie.getTarget().getY() - zombie.getY();
        long day = DifficultyManager.getDay(world);

        // 1. ТАЙМЕР АННИГИЛЯЦИИ
        // Если зомби поднялся выше (строит столб) — замедляем или сбрасываем таймер
        if (zombie.getY() > lastY + 0.1) {
            rageTimer = 0;
            lastY = zombie.getY();
        }

        // Если зомби в процессе стройки (buildCooldown > 0), таймер НЕ растет
        if (buildCooldown <= 0) {
            rageTimer++;
        }

        int secondsLimit = (int) (20 - (day - 3));
        if (secondsLimit < 5) secondsLimit = 5;
        int ticksLimit = secondsLimit * 20;

        if (rageTimer >= ticksLimit) {
            annihilateSurroundings(world);
            rageTimer = 0;
            return;
        }

        // 2. АТАКА
        zombie.getLookControl().lookAt(zombie.getTarget(), 30.0F, 30.0F);
        if (dist < 2.3 && Math.abs(dy) < 1.5) {
            zombie.getNavigation().stop();
            if (zombie.age % 10 == 0) {
                zombie.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                if (zombie.tryAttack(zombie.getTarget())) {
                    rageTimer = 0;
                }
            }
            return;
        }

        // 3. СТРОЙКА
        if (buildCooldown <= 0) {
            if (shouldBuild(world)) {
                tryBuild(world);
                return; // Важно: выходим, чтобы не включилось обычное движение в этот тик
            }
        } else {
            buildCooldown--;
        }

        // 4. ДВИЖЕНИЕ
        if (dy > 1.2) {
            zombie.getNavigation().startMovingTo(zombie.getTarget().getX(), zombie.getY(), zombie.getTarget().getZ(), 1.0);
        } else {
            zombie.getNavigation().startMovingTo(zombie.getTarget(), 1.0);
        }
    }


    private void annihilateSurroundings(ServerWorld world) {
        BlockPos center = zombie.getBlockPos();
        int radius = 2; // Радиус разрушения

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= radius + 1; y++) { // Захватываем блок под ногами и над головой
                for (int z = -radius; z <= radius; z++) {
                    BlockPos target = center.add(x, y, z);
                    // Ломаем всё, кроме бедрока
                    if (world.getBlockState(target).getHardness(world, target) >= 0) {
                        world.breakBlock(target, true);
                    }
                }
            }
        }
        zombie.velocityModified = true;
    }

    private boolean shouldBuild(ServerWorld world) {
        if (zombie.getTarget() == null) return false;

        double dy = zombie.getTarget().getY() - zombie.getY();
        double dist = zombie.distanceTo(zombie.getTarget());

        // 1. ЛОГИКА СТОЛБА
        if (dy > 1.2) {
            // Условие стало проще: если мы на земле и игрок выше нас в радиусе 4 блоков
            // (4 блока — чтобы он начинал строиться заранее, а не только впритык)
            return zombie.isOnGround() && dist < 4.0;
        }

        // 2. ЛОГИКА МОСТА
        Direction dir = getDirectionToTarget();
        BlockPos front = zombie.getBlockPos().offset(dir);

        // Проверяем, что впереди пропасть
        if (world.isAir(front) && world.isAir(front.down())) {
            // Строим мост, если игрок не слишком далеко и не сильно ниже нас
            return dist < 10.0 && dy > -1.5;
        }

        return false;
    }


    private void tryBuild(ServerWorld world) {
        zombie.getNavigation().stop();
        BlockPos pos = zombie.getBlockPos();
        double dy = zombie.getTarget().getY() - zombie.getY();

        if (dy > 1.2) {
            // СТОЛБ
            zombie.jump();
            world.setBlockState(pos, Blocks.DIRT.getDefaultState());
            zombie.refreshPositionAfterTeleport(pos.getX() + 0.5, zombie.getY() + 0.25, pos.getZ() + 0.5);
            buildCooldown = 7;
        } else {
            // МОСТ
            Direction dir = getDirectionToTarget();
            BlockPos bridgePos = pos.offset(dir).down();
            if (world.isAir(bridgePos)) {
                world.setBlockState(bridgePos, Blocks.DIRT.getDefaultState());
                buildCooldown = 4;
            }
        }
    }

    private Direction getDirectionToTarget() {
        double dx = zombie.getTarget().getX() - zombie.getX();
        double dz = zombie.getTarget().getZ() - zombie.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }
}
