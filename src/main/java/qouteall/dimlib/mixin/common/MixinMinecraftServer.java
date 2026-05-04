package qouteall.dimlib.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.config.PresetConfig;
import qouteall.dimlib.ducks.IMinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer
    extends ReentrantThreadExecutor<ServerTask> implements IMinecraftServer {
    
    public MixinMinecraftServer(String name) {
        super(name);
        throw new RuntimeException();
    }
    
    @Mutable
    @Shadow
    @Final
    private Map<RegistryKey<World>, ServerWorld> worlds;
    
    @Shadow
    public abstract boolean isStopped();
    
    @Shadow
    public abstract boolean isDedicated();
    
    @Shadow
    @Final
    protected LevelStorage.Session session;
    
    @Shadow
    protected abstract void waitForTasks();
    
    @Unique
    private boolean ip_canDirectlyRegisterDimension = false;
    
    @Unique
    private boolean ip_finishedCreatingWorlds = false;
    
    @Unique
    private List<Runnable> dimlib_taskList;
    
    @Inject(method = "createWorlds", at = @At("HEAD"))
    private void onBeforeCreateWorlds(CallbackInfo ci) {
        Validate.isTrue(
            !ip_canDirectlyRegisterDimension, "invalid server initialization status"
        );
        ip_canDirectlyRegisterDimension = true;
        
        PresetConfig.loadAndRegister((MinecraftServer) (Object) this);
        
        DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.invoker().run(
            (MinecraftServer) (Object) this
        );
        
        ip_canDirectlyRegisterDimension = false;
    }
    
    @Inject(
        method = "createWorlds",
        at = @At("RETURN")
    )
    private void onFinishedLoadingAllWorlds(
        CallbackInfo ci
    ) {
        ip_finishedCreatingWorlds = true;
    }
    
    @Override
    public void dimlib_addDimensionToWorldMap(RegistryKey<World> dim, ServerWorld world) {
        // use read-copy-update to avoid concurrency issues
        LinkedHashMap<RegistryKey<World>, ServerWorld> newMap =
            Maps.<RegistryKey<World>, ServerWorld>newLinkedHashMap();
        
        Map<RegistryKey<World>, ServerWorld> oldMap = this.worlds;
        
        newMap.putAll(oldMap);
        newMap.put(dim, world);
        
        this.worlds = newMap;
    }
    
    @Override
    public void dimlib_removeDimensionFromWorldMap(RegistryKey<World> dimension) {
        // use read-copy-update to avoid concurrency issues
        LinkedHashMap<RegistryKey<World>, ServerWorld> newMap =
            Maps.<RegistryKey<World>, ServerWorld>newLinkedHashMap();
        
        Map<RegistryKey<World>, ServerWorld> oldMap = this.worlds;
        
        for (Map.Entry<RegistryKey<World>, ServerWorld> entry : oldMap.entrySet()) {
            if (entry.getKey() != dimension) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        
        this.worlds = newMap;
    }
    
    @Override
    public boolean dimlib_getCanDirectlyRegisterDimensions() {
        return ip_canDirectlyRegisterDimension;
    }
    
    @Override
    public boolean dimlib_getIsFinishedCreatingWorlds() {
        return ip_finishedCreatingWorlds;
    }
    
    @Override
    public LevelStorage.Session dimlib_getStorageSource() {
        return session;
    }
    
    @Override
    public Executor dimlib_getExecutor() {
        return this;
    }
    
    @Override
    public void dimlib_waitUntilNextTick() {
        Validate.isTrue(!isExecutionInProgress());
        
        waitForTasks();
    }
    
    @Override
    public void dimlib_addTask(Runnable task) {
        if (dimlib_taskList == null) {
            dimlib_taskList = new ArrayList<>();
        }
        
        dimlib_taskList.add(task);
    }
    
    @Override
    public void dimlib_processTasks() {
        if (dimlib_taskList != null) {
            for (Runnable task : dimlib_taskList) {
                task.run();
            }
            dimlib_taskList = null;
        }
    }
}
