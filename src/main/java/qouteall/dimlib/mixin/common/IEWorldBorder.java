package qouteall.dimlib.mixin.common;

import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(WorldBorder.class)
public interface IEWorldBorder {
    @Accessor("listeners")
    List<WorldBorderListener> ip_getListeners();
}
