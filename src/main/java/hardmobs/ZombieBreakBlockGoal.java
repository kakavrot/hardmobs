package hardmobs;

import net.minecraft.block.BlockState;
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
        // test
        // Оставляем только LOOK, чтобы MOVE не конфликтовал с навигацией, когда мы просто идем
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (zombie.getWorld().isClient || zombie.getTarget() == null) return false;
        ServerWorld world = (ServerWorld) zombie.getWorld();

        if (DifficultyManager.getDay(world) < 3) return false;

        // Ищем блок для взаимодействия
        targetBlock = findTargetBlock(world);

        // Если блока для ломания нет, проверяем, нужно ли строить?
        if (targetBlock == null) {
            return shouldBuild(world);
        }

        return true;
    }

    private BlockPos findTargetBlock(ServerWorld world) {
        Direction dir = zombie.getHorizontalFacing();

        // 1. Прямо перед собой (ноги и голова)
        BlockPos[] paths = {
                zombie.getBlockPos().offset(dir),
                zombie.getBlockPos().offset(dir).up()
        };
        for (BlockPos p : paths) if (isBreakable(world, p)) return p;

        // 2. Над собой (если игрок выше)
        if (zombie.getTarget().getY() > zombie.getY() + 1.5) {
            BlockPos up = zombie.getBlockPos().up(2);
            if (isBreakable(world, up)) return up;
        }

        // 3. Под собой (если игрок ниже)
        if (zombie.getTarget().getY() < zombie.getY() - 1.0) {
            BlockPos down = zombie.getBlockPos().down();
            if (isBreakable(world, down)) return down;
        }

        return null;
    }

    private boolean shouldBuild(ServerWorld world) {
        if (buildCooldown > 0) return false;

        double targetY = zombie.getTarget().getY();
        BlockPos zombiePos = zombie.getBlockPos();
        Direction dir = getDirectionToTarget();

        // 1. ПРОВЕРКА НА СТОЛБ (Игрок выше)
        if (targetY > zombie.getY() + 1.2) {
            // Строим столб, если уперлись в стену или стоим под игроком
            if (zombie.horizontalCollision || zombie.distanceTo(zombie.getTarget()) < 2.5) {
                return zombie.isOnGround();
            }
        }

        // 2. ПРОВЕРКА НА МОСТ (Игрок далеко на высоте или за пропастью)
        BlockPos frontPos = zombiePos.offset(dir);
        BlockPos bridgePos = frontPos.down();

        // Условие моста: впереди пустота ИЛИ мы на столбе и до игрока есть расстояние
        boolean isGap = world.isAir(frontPos) && world.isAir(bridgePos);
        boolean isHighUp = zombie.getY() > world.getBottomY() + 64 && zombie.distanceTo(zombie.getTarget()) > 1.5;

        if (isGap && (isHighUp || targetY >= zombie.getY() - 1.0)) {
            return true;
        }

        return false;
    }


    private boolean isBreakable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        // Не ломаем бедрок и воздух
        return !state.isAir() && state.getHardness(world, pos) >= 0;
    }

    // 1. Исправленный tick()
    @Override
    public void tick() {
        if (zombie.getTarget() == null) return;
        ServerWorld world = (ServerWorld) zombie.getWorld();
        double distSq = zombie.squaredDistanceTo(zombie.getTarget());

        if (distSq < 4.0 && Math.abs(zombie.getY() - zombie.getTarget().getY()) < 1.5) {
            stop();
            return;
        }

        if (targetBlock != null) {
            zombie.getNavigation().stop();
            zombie.getLookControl().lookAt(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);
            int ticksNeeded = (int) (60 / (1.0 + (DifficultyManager.getDay(world) - 3) * 0.1));
            if (ticksNeeded < 5) ticksNeeded = 5;
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
        } else if (buildCooldown <= 0) {
            tryBuild(world);
        }
        if (buildCooldown > 0) buildCooldown--;
    }

    // 2. Исправленный tryBuild()
    private void tryBuild(ServerWorld world) {
        BlockPos zombiePos = zombie.getBlockPos();
        double targetY = zombie.getTarget().getY();
        Direction dir = getDirectionToTarget();

        // Логика столба
        if (targetY > zombie.getY() + 1.2 && (zombie.horizontalCollision || zombie.distanceTo(zombie.getTarget()) < 2.0)) {
            if (world.isAir(zombiePos.up(2))) {
                zombie.getNavigation().stop();
                zombie.jump();
                world.setBlockState(zombiePos, Blocks.DIRT.getDefaultState());
                zombie.refreshPositionAfterTeleport(zombie.getX(), zombie.getY() + 0.05, zombie.getZ());
                buildCooldown = 12;
                return;
            }
        }

        // Логика моста (теперь более агрессивная)
        BlockPos bridgePos = zombiePos.offset(dir).down();
        if (world.isAir(bridgePos)) {
            // Перед тем как поставить блок моста, чуть-чуть притормаживаем, чтобы не упасть
            zombie.getNavigation().stop();
            world.setBlockState(bridgePos, Blocks.DIRT.getDefaultState());
            buildCooldown = 6; // Быстрая стройка моста
        }
    }

    // 3. Исправленный shouldContinue()
    @Override
    public boolean shouldContinue() {
        if (zombie.getTarget() == null) return false;

        // Если мы ломаем блок - не прерываемся
        if (targetBlock != null) return isBreakable((ServerWorld)zombie.getWorld(), targetBlock);

        // Если мы в процессе стройки (высоко над землей или строим столб) - продолжаем
        if (zombie.getY() > zombie.getTarget().getY() - 2.0 && buildCooldown > 0) return true;

        return shouldBuild((ServerWorld)zombie.getWorld());
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

    @Override
    public boolean shouldRunEveryTick() {
        return true; // Важно для плавности анимации ломания
    }

    private Direction getDirectionToTarget() {
        if (zombie.getTarget() == null) {
            return zombie.getHorizontalFacing();
        }

        // Вычисляем разницу координат между зомби и игроком
        double dx = zombie.getTarget().getX() - zombie.getX();
        double dz = zombie.getTarget().getZ() - zombie.getZ();

        // Выбираем направление по наибольшей разнице (где разрыв больше, туда и строим)
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

}
