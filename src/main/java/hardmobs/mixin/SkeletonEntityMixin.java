package hardmobs.mixin;

import hardmobs.SkeletonExplosiveArrowGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkeletonEntity.class)
public abstract class SkeletonEntityMixin extends AbstractSkeletonEntity {

    // Пустой конструктор для компиляции (т.к. мы наследуемся)
    protected SkeletonEntityMixin(EntityType<? extends AbstractSkeletonEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<? extends SkeletonEntity> type, World world, CallbackInfo ci) {
        // Здесь мы уже имеем прямой доступ к goalSelector, так как мы "внутри" класса
        this.goalSelector.add(2, new SkeletonExplosiveArrowGoal((SkeletonEntity)(Object)this));
    }
}
