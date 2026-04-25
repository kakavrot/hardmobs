package hardmobs.mixin;

import hardmobs.ZombieBreakBlockGoal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    @Inject(method = "initGoals", at = @At("HEAD"), cancellable = true)
    private void replaceAllGoals(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;

        // Используем твой Аксессор, чтобы добраться до скрытых полей
        MobEntityAccessor accessor = (MobEntityAccessor) zombie;

        // 1. Очищаем все ванильные цели
        accessor.getGoalSelector().getGoals().clear();
        accessor.getTargetSelector().getGoals().clear();

        // 2. Добавляем твой кастомный ИИ
        accessor.getGoalSelector().add(0, new ZombieBreakBlockGoal(zombie));

        // 3. Добавляем поиск цели (игрока)
        accessor.getTargetSelector().add(1, new ActiveTargetGoal<>(zombie, PlayerEntity.class, true));

        // 4. Отменяем ванильный метод
        ci.cancel();
    }
}
