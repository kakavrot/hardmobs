package hardmobs.mixin;

import hardmobs.ZombieBreakBlockGoal;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    @Inject(method = "initCustomGoals", at = @At("TAIL"))
    private void addBreakBlockGoal(CallbackInfo ci) {
        // Используем аксессор, чтобы безопасно получить доступ к goalSelector
        ((MobEntityAccessor) this).getGoalSelector().add(0, new ZombieBreakBlockGoal((ZombieEntity) (Object) this));
    }
}
