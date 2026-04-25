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
    private BlockPos targetBlock;
    private int breakProgress = -1;
    private int lastProgress = -1;
    private int buildCooldown = 0;

    public ZombieBreakBlockGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        // Захватываем все контроли, так как мы теперь единственный ИИ
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        // Работает всегда, если есть цель (игрок) и наступил 3-й день
        if (zombie.getWorld().isClient || zombie.getTarget() == null) return false;
        return DifficultyManager.getDay((ServerWorld)zombie.getWorld()) >= 3;
    }

    @Override
    public boolean shouldContinue() {
        return zombie.getTarget() != null && !zombie.getWorld().isClient;
    }

    @Override
    public void tick() {
        if (zombie.getTarget() == null) return;

        ServerWorld world = (ServerWorld) zombie.getWorld();
        double dist = zombie.distanceTo(zombie.getTarget());
        double dy = zombie.getTarget().getY() - zombie.getY();

        // 1. ПРИОРИТЕТ: АТАКА
        // Увеличили дистанцию до 2.1, чтобы компенсировать хитбоксы
        if (dist < 2.1 && Math.abs(dy) < 1.5) {
            zombie.getNavigation().stop();
            zombie.getLookControl().lookAt(zombie.getTarget(), 30.0F, 30.0F);

            if (zombie.age % 10 == 0) { // Проверка каждые 10 тиков (0.5 сек)
                zombie.swingHand(net.minecraft.util.Hand.MAIN_HAND); // Визуальный взмах
                zombie.tryAttack(zombie.getTarget()); // Нанесение урона
            }
            return;
        }

        // 2. ПРИОРИТЕТ: ЛОМАНИЕ
        if (zombie.horizontalCollision && targetBlock == null) {
            targetBlock = findTargetBlock(world);
        }

        if (targetBlock != null) {
            handleBreaking(world);
            return;
        }

        // 3. ПРИОРИТЕТ: СТРОЙКА
        if (buildCooldown <= 0) {
            if (shouldBuild(world)) {
                tryBuild(world);
                return;
            }
        } else {
            buildCooldown--;
        }

        // 4. ПРИОРИТЕТ: ДВИЖЕНИЕ
        zombie.getNavigation().startMovingTo(zombie.getTarget(), 1.0);
        zombie.getLookControl().lookAt(zombie.getTarget(), 30.0F, 30.0F);
    }


    private void handleBreaking(ServerWorld world) {
        zombie.getNavigation().stop();
        zombie.getLookControl().lookAt(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);

        int ticksNeeded = 40; // Скорость ломания (можно привязать к DifficultyManager)
        breakProgress++;

        int visualProgress = (int) ((float) breakProgress / (float) ticksNeeded * 10.0F);
        if (visualProgress != lastProgress) {
            world.setBlockBreakingInfo(zombie.getId(), targetBlock, visualProgress);
            lastProgress = visualProgress;
        }

        if (breakProgress >= ticksNeeded) {
            world.breakBlock(targetBlock, true);
            stop();
        }
    }

    private boolean shouldBuild(ServerWorld world) {
        double dy = zombie.getTarget().getY() - zombie.getY();
        double horizontalDistSq = zombie.squaredDistanceTo(zombie.getTarget().getX(), zombie.getY(), zombie.getTarget().getZ());

        // СТОЛБ: Игрок выше, и зомби застрял или стоит под ним
        if (dy > 1.2) {
            // Проверяем, не стоит ли зомби почти неподвижно
            boolean isStuck = zombie.getVelocity().horizontalLengthSquared() < 0.002;
            if (zombie.horizontalCollision || isStuck || horizontalDistSq < 2.5) {
                return zombie.isOnGround();
            }
        }

        // МОСТ: Если впереди пропасть (воздух на уровне ног и ниже)
        Direction dir = getDirectionToTarget();
        BlockPos front = zombie.getBlockPos().offset(dir);
        if (world.isAir(front) && world.isAir(front.down()) && dy > -1.5) {
            return true;
        }

        return false;
    }


    private void tryBuild(ServerWorld world) {
        zombie.getNavigation().stop();
        BlockPos pos = zombie.getBlockPos();
        double dy = zombie.getTarget().getY() - zombie.getY();

        if (dy > 1.2) {
            // Логика постройки СТОЛБА
            if (world.isAir(pos.up(2))) { // Проверка, что сверху нет потолка
                zombie.jump();
                world.setBlockState(pos, Blocks.DIRT.getDefaultState());
                // Поднимаем зомби чуть выше блока, чтобы избежать застревания
                zombie.refreshPositionAfterTeleport(zombie.getX(), zombie.getY() + 0.2, zombie.getZ());
                buildCooldown = 12;
            }
        } else {
            // Логика постройки МОСТА
            Direction dir = getDirectionToTarget();
            BlockPos bridgePos = pos.offset(dir).down();
            if (world.isAir(bridgePos)) {
                world.setBlockState(bridgePos, Blocks.DIRT.getDefaultState());
                buildCooldown = 6;
            }
        }
    }


    private BlockPos findTargetBlock(ServerWorld world) {
        Direction dir = zombie.getHorizontalFacing();
        BlockPos[] checks = {zombie.getBlockPos().offset(dir).up(), zombie.getBlockPos().offset(dir)};
        for (BlockPos p : checks) {
            if (!world.isAir(p) && world.getBlockState(p).getHardness(world, p) >= 0) return p;
        }
        return null;
    }

    private Direction getDirectionToTarget() {
        double dx = zombie.getTarget().getX() - zombie.getX();
        double dz = zombie.getTarget().getZ() - zombie.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    @Override
    public void stop() {
        if (targetBlock != null) {
            ((ServerWorld)zombie.getWorld()).setBlockBreakingInfo(zombie.getId(), targetBlock, -1);
        }
        targetBlock = null;
        breakProgress = -1;
        lastProgress = -1;
    }
}
