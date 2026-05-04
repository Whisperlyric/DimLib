package qouteall.dimlib.mixin.client;

import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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

@Mixin(ConfirmScreen.class)
public abstract class MixinBackupConfirmScreen extends Screen {
    
    @Shadow
    @Final
    private Text message;
    @Unique
    private boolean dimlib_isExperimentalWarning = false;
    
    protected MixinBackupConfirmScreen(Text title) {
        super(title);
        throw new RuntimeException();
    }
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInitEnd(
        it.unimi.dsi.fastutil.booleans.BooleanConsumer callback,
        Text title,
        Text message,
        CallbackInfo ci
    ) {
        dimlib_isExperimentalWarning = Objects.equals(
            title,
            Text.translatable("selectWorld.backupQuestion.experimental")
        );
    }
    
    @Inject(method = "init", at = @At("RETURN"))
    private void onInitEnd(CallbackInfo ci) {
        if (dimlib_isExperimentalWarning) {
            addDrawableChild(ButtonWidget
                .builder(
                    Text.translatable(
                        "dimlib.i_know_what_i_am_doing_and_disable_warning"
                    ),
                    button -> {
                        DimLibConfig.suppressExperimentalWarning = true;
                        MidnightConfig.write(DimLibEntry.MODID);
                        
                        this.close();
                    }
                )
                .dimensions(
                    this.width / 2 - 200, this.height / 2 + 50,
                    400, 20
                )
                .build()
            );
            
        }
    }
}
