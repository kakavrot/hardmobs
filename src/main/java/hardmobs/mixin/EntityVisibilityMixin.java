package hardmobs.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class EntityVisibilityMixin {

    @Inject(method = "canSee", at = @At("HEAD"), cancellable = true)
    private void onCanSee(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Если "смотрящий" — это монстр, он видит любую сущность сквозь стены
        if ((Object) this instanceof Monster) {
            cir.setReturnValue(true);
        }
    }
}
