package qouteall.dimlib.mixin.common;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.dimlib.DimensionImpl;
import qouteall.dimlib.config.DimLibConfig;

@Mixin(LevelProperties.class)
public class MixinPrimaryLevelData {
    // disable the warning from the root
    @Inject(method = "getLifecycle", at = @At("HEAD"), cancellable = true)
    private void onGetLifecycle(CallbackInfoReturnable<Lifecycle> cir) {
        if (DimLibConfig.suppressExperimentalWarning
            || DimensionImpl.suppressExperimentalWarning
        ) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
