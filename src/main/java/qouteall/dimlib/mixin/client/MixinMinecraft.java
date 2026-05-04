package qouteall.dimlib.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.ClientDimensionInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraft {
    @Inject(method = "setWorld", at = @At("HEAD"))
    private void onClientReset(ClientWorld level, CallbackInfo ci) {
        if (level == null) {
            ClientDimensionInfo.cleanup();
        }
    }
}
