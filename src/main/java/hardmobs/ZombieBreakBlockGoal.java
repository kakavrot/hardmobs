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

        // Если блока для ломания нет, проверяем, нужно ли строить
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

        // Условие для столба
        if (targetY > zombie.getY() + 1.2 && zombie.isOnGround()) return true;

        // Условие для моста
        Direction dir = zombie.getHorizontalFacing();
        if (world.isAir(zombiePos.offset(dir).down()) && targetY >= zombie.getY() - 1.0) return true;

        return false;
    }

    private boolean isBreakable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        // Не ломаем бедрок и воздух
        return !state.isAir() && state.getHardness(world, pos) >= 0;
    }

    @Override
    public void tick() {
        if (zombie.getTarget() == null) return;
        ServerWorld world = (ServerWorld) zombie.getWorld();
        // В самом начале tick, после проверок на null
        zombie.getLookControl().lookAt(zombie.getTarget(), 30.0F, 30.0F);


        // Дистанция до цели
        double distSq = zombie.squaredDistanceTo(zombie.getTarget());

        // Если игрок рядом — приоритет на атаку (прекращаем работу цели)
        if (distSq < 4.0 && Math.abs(zombie.getY() - zombie.getTarget().getY()) < 1.5) {
            stop();
            return;
        }

        // 1. ЛОГИКА ЛОМАНИЯ
        if (targetBlock != null) {
            zombie.getNavigation().stop(); // Останавливаемся, чтобы копать
            zombie.getLookControl().lookAt(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);

            long day = DifficultyManager.getDay(world);
            int ticksNeeded = (int) (60 / (1.0 + (day - 3) * 0.1));
            if (ticksNeeded < 5) ticksNeeded = 5;

            breakProgress++;

            // Визуализация трещин (от 0 до 9)
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
        // 2. ЛОГИКА СТРОЙКИ
        else if (buildCooldown <= 0) {
            tryBuild(world);
        }

        if (buildCooldown > 0) buildCooldown--;
    }

    private void tryBuild(ServerWorld world) {
        BlockPos zombiePos = zombie.getBlockPos();
        double targetY = zombie.getTarget().getY();

        // Вектор направления к игроку
        double dx = zombie.getTarget().getX() - zombie.getX();
        double dz = zombie.getTarget().getZ() - zombie.getZ();

        // Определяем основное направление (N, S, E, W) на основе координат
        Direction dir;
        if (Math.abs(dx) > Math.abs(dz)) {
            dir = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            dir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // СТОЛБ (вверх)
        if (targetY > zombie.getY() + 1.2 && zombie.isOnGround()) {
            if (world.isAir(zombiePos.up(2))) {
                zombie.jump();
                world.setBlockState(zombiePos, Blocks.DIRT.getDefaultState());
                buildCooldown = 15;
            }
        }
        // МОСТ (в сторону игрока)
        else {
            BlockPos bridgePos = zombiePos.offset(dir).down();
            if (world.isAir(bridgePos)) {
                // Ставим блок под ноги в направлении цели
                world.setBlockState(bridgePos, Blocks.DIRT.getDefaultState());
                buildCooldown = 5;
            }
        }
    }


    @Override
    public boolean shouldContinue() {
        // Если цель пропала - выключаемся
        if (zombie.getTarget() == null) return false;

        // Если мы в процессе ломания - продолжаем, пока блок не исчезнет
        if (targetBlock != null) return isBreakable((ServerWorld)zombie.getWorld(), targetBlock);

        // В остальных случаях (стройка) проверяем условия заново
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
}
