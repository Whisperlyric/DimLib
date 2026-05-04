package qouteall.dimlib.mixin.client;

import eu.midnightdust.lib.config.MidnightConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.DimLibEntry;
import qouteall.dimlib.config.DimLibConfig;

import java.util.Objects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Mixin(ConfirmScreen.class)
public abstract class MixinBackupConfirmScreen extends Screen {
    
    @Shadow
    @Final
    private Component message;
    @Unique
    private boolean dimlib_isExperimentalWarning = false;
    
    protected MixinBackupConfirmScreen(Component title) {
        super(title);
        throw new RuntimeException();
    }
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInitEnd(
        it.unimi.dsi.fastutil.booleans.BooleanConsumer callback,
        Component title,
        Component message,
        CallbackInfo ci
    ) {
        dimlib_isExperimentalWarning = Objects.equals(
            title,
            Component.translatable("selectWorld.backupQuestion.experimental")
        );
    }
    
    @Inject(method = "init", at = @At("RETURN"))
    private void onInitEnd(CallbackInfo ci) {
        if (dimlib_isExperimentalWarning) {
            addRenderableWidget(Button
                .builder(
                    Component.translatable(
                        "dimlib.i_know_what_i_am_doing_and_disable_warning"
                    ),
                    button -> {
                        DimLibConfig.suppressExperimentalWarning = true;
                        MidnightConfig.write(DimLibEntry.MODID);
                        
                        this.onClose();
                    }
                )
                .bounds(
                    this.width / 2 - 200, this.height / 2 + 50,
                    400, 20
                )
                .build()
            );
            
        }
    }
}
