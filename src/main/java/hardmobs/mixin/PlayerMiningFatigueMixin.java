package hardmobs.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerMiningFatigueMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void applyDeepFatigue(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // Проверяем только на сервере и только раз в секунду (каждые 20 тиков) для оптимизации
        if (!player.getWorld().isClient && player.age % 20 == 0) {

            if (player.getY() < 60) {
                // Выдаем Утомление (MINING_FATIGUE)
                // 3 уровень (в коде это 2, так как отсчет с 0)
                // Длительность 2 секунды (40 тиков), чтобы эффект не мерцал
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.MINING_FATIGUE, 40, 2, false, false, true
                ));
            }
        }
    }
}
