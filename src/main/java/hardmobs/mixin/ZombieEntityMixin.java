package hardmobs.mixin;

import hardmobs.ZombieBreakBlockGoal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    // 1. ИНИЦИАЛИЗАЦИЯ ИИ (Зомби снова ломают блоки и ходят)
    @Inject(method = "initGoals", at = @At("TAIL"))
    private void addCustomGoals(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        // Используем аксессор, чтобы добавить твой кастомный ИИ
        ((MobEntityAccessor) zombie).getGoalSelector().add(1, new ZombieBreakBlockGoal(zombie));
    }

    // 2. ГРУППОВОЙ СПАВН (Когда появляется один - призывает еще двоих)
    @Inject(method = "initialize", at = @At("TAIL"))
    private void spawnHorde(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
        if (world.isClient()) return;
        ServerWorld serverWorld = world.toServerWorld();

        // Считаем текущее количество мобов
        int mobCount = 0;
        for (Entity entity : serverWorld.iterateEntities()) {
            if (entity instanceof HostileEntity) mobCount++;
        }

        // Если лимит не достигнут, создаем группу
        if (mobCount < 150 && spawnReason != SpawnReason.REINFORCEMENT) {
            for (int i = 0; i < 2; i++) {
                ZombieEntity follower = new ZombieEntity(serverWorld);
                follower.refreshPositionAndAngles(((ZombieEntity)(Object)this).getX() + 1, ((ZombieEntity)(Object)this).getY(), ((ZombieEntity)(Object)this).getZ() + 1, 0, 0);
                serverWorld.spawnEntity(follower);
            }
        }
    }

    // 3. АКТИВНЫЙ ДОСПАВН (Чтобы всегда поддерживать лимит 150)
    @Inject(method = "tick", at = @At("HEAD"))
    private void keepPopulationHigh(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;

        // Проверяем раз в 5 секунд, чтобы не лагало
        if (!zombie.getWorld().isClient && zombie.age % 100 == 0) {
            ServerWorld world = (ServerWorld) zombie.getWorld();

            int mobCount = 0;
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof HostileEntity) mobCount++;
            }

            // Если кто-то умер и мобов стало меньше 150 - спавним нового зомби рядом
            if (mobCount < 150) {
                ZombieEntity newZombie = new ZombieEntity(world);
                double x = zombie.getX() + (world.random.nextDouble() - 0.5) * 32;
                double z = zombie.getZ() + (world.random.nextDouble() - 0.5) * 32;

                newZombie.refreshPositionAndAngles(x, zombie.getY() + 1, z, 0, 0);
                world.spawnEntity(newZombie);
            }
        }
    }
}
