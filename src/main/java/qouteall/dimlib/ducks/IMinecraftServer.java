package qouteall.dimlib.ducks;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage;

import java.util.concurrent.Executor;

public interface IMinecraftServer {
    
    LevelStorage.Session dimlib_getStorageSource();
    
    Executor dimlib_getExecutor();
    
    void dimlib_addDimensionToWorldMap(RegistryKey<World> dim, ServerWorld world);
    
    void dimlib_removeDimensionFromWorldMap(RegistryKey<World> dimension);
    
    void dimlib_waitUntilNextTick();
    
    boolean dimlib_getCanDirectlyRegisterDimensions();
    
    boolean dimlib_getIsFinishedCreatingWorlds();
    
    void dimlib_addTask(Runnable task);
    
    void dimlib_processTasks();
}
