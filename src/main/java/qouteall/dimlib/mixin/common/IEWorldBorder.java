package qouteall.dimlib.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;

@Mixin(WorldBorder.class)
public interface IEWorldBorder {
    @Accessor("listeners")
    List<BorderChangeListener> ip_getListeners();
}
