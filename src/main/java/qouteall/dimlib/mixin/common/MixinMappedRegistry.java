package qouteall.dimlib.mixin.common;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.dimlib.ducks.IMappedRegistry;

import java.util.List;
import java.util.Map;

@Mixin(SimpleRegistry.class)
public abstract class MixinMappedRegistry<T> implements IMappedRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("DimLib");
    
    @Shadow
    @Final
    private Map<Identifier, RegistryEntry.Reference<T>> idToEntry;
    
    @Shadow
    public abstract @Nullable T get(int id);
    
    @Shadow
    @Final
    private ObjectList<RegistryEntry.Reference<T>> rawIdToEntry;
    
    @Shadow
    @Final
    private Reference2IntMap<T> entryToRawId;
    
    @Shadow
    @Final
    private Map<RegistryKey<T>, RegistryEntry.Reference<T>> keyToEntry;
    
    @Shadow
    @Final
    RegistryKey<? extends Registry<T>> key;
    
    @Shadow
    @Final
    private Map<T, RegistryEntry.Reference<T>> valueToEntry;
    
    @Shadow
    private boolean frozen;
    
    @Shadow
    @Final
    private Map<RegistryKey<T>, RegistryEntryInfo> keyToEntryInfo;
    
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
        
        RegistryEntry.Reference<T> holder = idToEntry.remove(id);
        
        if (holder == null) {
            LOGGER.debug("[DimLib] {} not found in {} when trying to remove", id, this.key);
            return false;
        }
        
        RegistryKey<T> eleKey = holder.registryKey();
        T value = holder.value();
        
        int removedId = entryToRawId.getInt(value);
        
        if (removedId != -1) {
            entryToRawId.removeInt(value);
            
            int lastId = rawIdToEntry.size() - 1;
            if (removedId < lastId) {
                RegistryEntry.Reference<T> lastEntry = rawIdToEntry.get(lastId);
                if (lastEntry != null) {
                    T lastValue = lastEntry.value();
                    entryToRawId.put(lastValue, removedId);
                    rawIdToEntry.set(removedId, lastEntry);
                }
            }
            rawIdToEntry.remove(lastId);
        }
        
        keyToEntry.remove(eleKey);
        valueToEntry.remove(value);
        keyToEntryInfo.remove(eleKey);
        
        return true;
    }
    
}
