package hardmobs.mixin;

import hardmobs.DifficultyManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntity {
    protected MobEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "initialize", at = @At("TAIL"))
    private void onInitialize(ServerWorldAccess world, LocalDifficulty difficulty, net.minecraft.entity.SpawnReason spawnReason, net.minecraft.entity.EntityData entityData, CallbackInfoReturnable cir) {

        // ВОТ ЭТА ПРОВЕРКА: Если заспавнился слайм — мгновенно удаляем его
        if ((Object)this instanceof net.minecraft.entity.mob.SlimeEntity) {
            this.discard(); // Метод discard() полностью удаляет сущность из игры
            return;
        }

    }


    @Inject(method = "initialize", at = @At("TAIL"))
    private void onGeneralInitialize(ServerWorldAccess world, LocalDifficulty difficulty, net.minecraft.entity.SpawnReason spawnReason, net.minecraft.entity.EntityData entityData, CallbackInfoReturnable cir) {
        if (!this.getWorld().isClient && (Object)this instanceof Monster) {
            ServerWorld serverWorld = (ServerWorld) this.getWorld();
            long day = DifficultyManager.getDay(serverWorld);

            // Здоровье + Обзор
            this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20.0 * (1.0 + (day * 0.05)));
            this.setHealth(this.getMaxHealth());
            this.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE).setBaseValue(100.0);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onGeneralTick(CallbackInfo ci) {
        if (!this.getWorld().isClient && this.age % 20 == 0 && (Object)this instanceof Monster) {
            MobEntity mob = (MobEntity)(Object)this;
            if (mob.getTarget() == null) {
                net.minecraft.entity.player.PlayerEntity player = this.getWorld().getClosestPlayer(this.getX(), this.getY(), this.getZ(), 100.0, true);
                if (player != null) mob.setTarget(player);
            }
        }
    }

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void onCheckDespawn(CallbackInfo ci) {
        if (!this.getWorld().isClient && (Object)this instanceof Monster) {
            // Ищем ближайшего игрока
            net.minecraft.entity.player.PlayerEntity player = this.getWorld().getClosestPlayer(this.getX(), this.getY(), this.getZ(), -1.0, false);

            if (player != null) {
                double distanceSq = this.squaredDistanceTo(player);

                // Если моб ближе 96 блоков (96*96 = 9216), мы ЗАПРЕЩАЕМ ему исчезать
                if (distanceSq < 9216) {
                    ci.cancel();
                }
                // Если он дальше 96 блоков, управление возвращается игре,
                // и она его удалит, если лимит мобов превышен.
            }
        }
    }

}
