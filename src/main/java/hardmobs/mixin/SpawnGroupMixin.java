package hardmobs.mixin;

import net.minecraft.entity.SpawnGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnGroup.class)
public class SpawnGroupMixin {

    @Inject(method = "getCapacity", at = @At("HEAD"), cancellable = true)
    private void setStrictLimit(CallbackInfoReturnable<Integer> cir) {
        SpawnGroup group = (SpawnGroup) (Object) this;

        if (group == SpawnGroup.MONSTER) {
            // Поскольку у нас в Enum нет доступа к миру,
            // нам нужно получить день через наш DifficultyManager.
            // Мы будем использовать костыль: берем статический доступ,
            // если твой DifficultyManager это позволяет.

            long day = hardmobs.DifficultyManager.getLastMeasuredDay();

            // Твоя строгая формула: на 1-й день 10, на 2-й 15...
            int strictLimit = 10 + (int)((day - 1) * 5);

            cir.setReturnValue(strictLimit);
        }
    }
}
