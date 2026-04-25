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

        zombie.getLookControl().lookAt(zombie.getTarget(), 30.0F, 30.0F);

        // 1. АТАКА (Приоритет только если мы на одном уровне)
        if (dist < 2.3 && Math.abs(dy) < 1.5) {
            zombie.getNavigation().stop();
            if (zombie.age % 10 == 0) {
                zombie.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                zombie.tryAttack(zombie.getTarget());
            }
            return;
        }

        // Детектор столба: если игрок выше, мы В РЕЖИМЕ СТРОЙКИ
        boolean isPillaring = dy > 1.2;

        // 2. ЛОГИКА ЛОМАНИЯ
        if (targetBlock != null || (zombie.horizontalCollision && !isPillaring && dist > 2.3)) {
            if (targetBlock == null) targetBlock = findTargetBlock(world);
            if (targetBlock != null) {
                handleBreaking(world);
                return;
            }
        }

        // 3. ЛОГИКА СТРОЙКИ
        if (buildCooldown <= 0) {
            if (shouldBuild(world)) {
                tryBuild(world);
                return;
            }
        } else {
            buildCooldown--;
        }

        // 4. ДВИЖЕНИЕ
        if (isPillaring) {
            // Вместо полной остановки навигации, просто заставляем его идти "в точку под игроком"
            // Это поможет ему не сходить со столба, но стремиться к центру
            zombie.getNavigation().startMovingTo(zombie.getTarget().getX(), zombie.getY(), zombie.getTarget().getZ(), 1.0);
        } else {
            zombie.getNavigation().startMovingTo(zombie.getTarget(), 1.0);
        }
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
        BlockPos pos = zombie.getBlockPos();
        Direction dir = getDirectionToTarget();

        // Если прямо перед нами блок (на уровне ног или головы), НЕ строим, а ломаем
        BlockPos headPos = pos.offset(dir).up();
        BlockPos footPos = pos.offset(dir);
        if (!world.isAir(headPos) || !world.isAir(footPos)) return false;

        // Логика столба
        if (dy > 1.2) {
            boolean isStuck = zombie.getVelocity().horizontalLengthSquared() < 0.002;
            if (zombie.horizontalCollision || isStuck || zombie.distanceTo(zombie.getTarget()) < 2.5) {
                return zombie.isOnGround();
            }
        }

        // Логика моста
        BlockPos bridgePos = footPos.down();
        return world.isAir(footPos) && world.isAir(bridgePos) && dy > -1.5;
    }



    private void tryBuild(ServerWorld world) {
        BlockPos pos = zombie.getBlockPos();
        double dy = zombie.getTarget().getY() - zombie.getY();

        if (dy > 1.2) {
            // СТОЛБ
            if (world.isAir(pos.up(2))) {
                zombie.getNavigation().stop();
                zombie.jump();
                world.setBlockState(pos, net.minecraft.block.Blocks.DIRT.getDefaultState());

                // Центрируем и чуть подбрасываем (ускоряем процесс)
                zombie.refreshPositionAfterTeleport(pos.getX() + 0.5, zombie.getY() + 0.25, pos.getZ() + 0.5);

                buildCooldown = 7; // Было 15, теперь строит в 2 раза быстрее!
            }
        } else {
            // МОСТ
            Direction dir = getDirectionToTarget();
            BlockPos bridgePos = pos.offset(dir).down();
            if (world.isAir(bridgePos)) {
                world.setBlockState(bridgePos, net.minecraft.block.Blocks.DIRT.getDefaultState());
                buildCooldown = 4; // Мосты строит почти мгновенно
            }
        }
    }




    private BlockPos findTargetBlock(ServerWorld world) {
        // Проверяем классические позиции: перед ногами и головой
        Direction dir = zombie.getHorizontalFacing();
        BlockPos pos = zombie.getBlockPos();
        BlockPos[] primaryChecks = {pos.offset(dir), pos.offset(dir).up(), pos.up(2)};

        for (BlockPos p : primaryChecks) {
            if (isBreakable(world, p)) return p;
        }

        // Если основные не сработали, а зомби уперся — ищем любой блок вокруг,
        // мешающий пройти к вектору цели
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 1; y++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    if (isBreakable(world, checkPos) && zombie.getTarget().squaredDistanceTo(checkPos.getX(), checkPos.getY(), checkPos.getZ()) < zombie.getTarget().squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ())) {
                        return checkPos;
                    }
                }
            }
        }
        return null;
    }


    private boolean isBreakable(ServerWorld world, BlockPos p) {
        return !world.isAir(p) && world.getBlockState(p).getHardness(world, p) >= 0;
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
