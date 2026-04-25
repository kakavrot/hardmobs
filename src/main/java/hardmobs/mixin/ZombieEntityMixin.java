package hardmobs.mixin;

import hardmobs.ZombieBreakBlockGoal;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    /**
     * Используем TAIL, чтобы добавить нашу логику ПОСЛЕ того,
     * как Майнкрафт настроит стандартное поведение зомби.
     */
    @Inject(method = "initGoals", at = @At("TAIL"))
    private void addRageGoal(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;

        // Добавляем твой класс с таймером взрыва.
        // Приоритет 1 позволит ему работать параллельно с атакой.
        ((MobEntityAccessor) zombie).getGoalSelector().add(1, new ZombieBreakBlockGoal(zombie));
    }
}
