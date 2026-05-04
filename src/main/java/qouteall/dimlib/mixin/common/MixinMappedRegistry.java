package qouteall.dimlib.mixin.common;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.dimlib.ducks.IMappedRegistry;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

@Mixin(MappedRegistry.class)
public abstract class MixinMappedRegistry<T> implements IMappedRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("DimLib");
    
    @Shadow
    @Final
    private Map<Identifier, Holder.Reference<T>> byLocation;
    
    @Shadow
    public abstract @Nullable T byId(int id);
    
    @Shadow
    @Final
    private ObjectList<Holder.Reference<T>> byId;
    
    @Shadow
    @Final
    private Reference2IntMap<T> toId;
    
    @Shadow
    @Final
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;
    
    @Shadow
    @Final
    ResourceKey<? extends Registry<T>> key;
    
    @Shadow
    @Final
    private Map<T, Holder.Reference<T>> byValue;
    
    @Shadow
    private boolean frozen;
    
    @Shadow
    @Final
    private Map<ResourceKey<T>, RegistrationInfo> registrationInfos;
    
    @Override
    public boolean dimlib_getIsFrozen() {
        return frozen;
    }
    
    @Override
    public void dimlib_setIsFrozen(boolean cond) {
        frozen = cond;
    }
    
    @Override
    public boolean dimlib_forceRemove(Identifier id) {
        LOGGER.debug("[DimLib] Trying to remove {} from {}", id, this.key);
        
        Holder.Reference<T> holder = byLocation.remove(id);
        
        if (holder == null) {
            LOGGER.debug("[DimLib] {} not found in {} when trying to remove", id, this.key);
            return false;
        }
        
        ResourceKey<T> eleKey = holder.key();
        T value = holder.value();
        
        int removedId = toId.getInt(value);
        
        if (removedId != -1) {
            toId.removeInt(value);
            
            int lastId = byId.size() - 1;
            if (removedId < lastId) {
                Holder.Reference<T> lastEntry = byId.get(lastId);
                if (lastEntry != null) {
                    T lastValue = lastEntry.value();
                    toId.put(lastValue, removedId);
                    byId.set(removedId, lastEntry);
                }
            }
            byId.remove(lastId);
        }
        
        byKey.remove(eleKey);
        byValue.remove(value);
        registrationInfos.remove(eleKey);
        
        return true;
    }
    
}
