package qouteall.dimlib.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionOptions;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.dimlib.ClientDimensionInfo;
import qouteall.dimlib.DimensionImpl;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.DynamicDimensionsImpl;
import qouteall.dimlib.ducks.IMinecraftServer;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class DimensionAPI {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static interface ServerDimensionsLoadCallback {
        void run(MinecraftServer server);
    }
    
    public static final Event<ServerDimensionsLoadCallback> SERVER_DIMENSIONS_LOAD_EVENT =
        EventFactory.createArrayBacked(
            ServerDimensionsLoadCallback.class,
            (listeners) -> ((server) -> {
                for (ServerDimensionsLoadCallback listener : listeners) {
                    try {
                        listener.run(server);
                    }
                    catch (Exception e) {
                        LOGGER.error("Error during server dimensions load event", e);
                    }
                }
            })
        );
    
    public static void addDimension(
        MinecraftServer server,
        Identifier dimensionId,
        DimensionOptions dimensionOptions
    ) {
        if (((IMinecraftServer) server).dimlib_getIsFinishedCreatingWorlds()) {
            addDimensionDynamically(server, dimensionId, dimensionOptions);
        }
        else {
            if (((IMinecraftServer) server).dimlib_getCanDirectlyRegisterDimensions()) {
                DimensionImpl.directlyRegisterLevelStem(server, dimensionId, dimensionOptions);
            }
            else {
                LOGGER.error(
                    "Cannot add dimension at this time {}", dimensionId, new Throwable()
                );
            }
        }
    }
    
    public static boolean dimensionExistsInRegistry(
        MinecraftServer server, Identifier dimensionId
    ) {
        return DimensionImpl.getDimensionRegistry(server).containsId(dimensionId);
    }
    
    public static void addDimensionIfNotExists(
        MinecraftServer server,
        Identifier dimensionId,
        Supplier<DimensionOptions> dimensionOptions
    ) {
        if (dimensionExistsInRegistry(server, dimensionId)) {
            return;
        }
        
        addDimension(server, dimensionId, dimensionOptions.get());
    }
    
    public static void addDimensionDynamically(
        MinecraftServer server,
        Identifier dimensionId,
        DimensionOptions dimensionOptions
    ) {
        Validate.isTrue(server.isRunning(), "The server is not running");
        DynamicDimensionsImpl.addDimensionDynamically(server, dimensionId, dimensionOptions);
    }
    
    public static void removeDimensionDynamically(ServerWorld world) {
        if (!world.getServer().isRunning()) {
            LOGGER.error(
                "Cannot remove dimension at this time {}", world, new Throwable()
            );
            return;
        }
        
        DynamicDimensionsImpl.removeDimensionDynamically(world);
    }
    
    public static interface ServerDynamicUpdateListener {
        void run(MinecraftServer server, Set<RegistryKey<World>> dimensions);
    }
    
    public static interface ClientDynamicUpdateListener {
        void run(Set<RegistryKey<World>> dimensions);
    }
    
    public static final Event<ServerDynamicUpdateListener> SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT =
        EventFactory.createArrayBacked(
            ServerDynamicUpdateListener.class,
            arr -> (server, dims) -> {
                for (ServerDynamicUpdateListener listener : arr) {
                    try {
                        listener.run(server, dims);
                    }
                    catch (Exception e) {
                        LOGGER.error("Error during dimension update event", e);
                    }
                }
            }
        );
    
    public static final Event<ClientDynamicUpdateListener> CLIENT_DIMENSION_UPDATE_EVENT =
        EventFactory.createArrayBacked(
            ClientDynamicUpdateListener.class,
            arr -> (set) -> {
                for (ClientDynamicUpdateListener listener : arr) {
                    try {
                        listener.run(set);
                    }
                    catch (Exception e) {
                        LOGGER.error("Error during dimension update event", e);
                    }
                }
            }
        );
    
    public static boolean isDimensionAlive(ServerWorld world) {
        return world.getServer().getWorld(world.getRegistryKey()) == world;
    }
    
    public static void suppressExperimentalWarning() {
        DimensionImpl.suppressExperimentalWarning = true;
    }
    
    public static void suppressExperimentalWarningForNamespace(String namespace) {
        DimensionImpl.STABLE_NAMESPACES.add(namespace);
    }
    
    @Environment(EnvType.CLIENT)
    public static Set<RegistryKey<World>> getClientDimensionIds() {
        return ClientDimensionInfo.getDimensionIds();
    }
    
    @Environment(EnvType.CLIENT)
    public static Map<RegistryKey<World>, RegistryKey<DimensionType>> getClientDimensionIdToTypeMap() {
        return ClientDimensionInfo.getDimensionIdToType();
    }
    
    public static void registerDimensionTemplate(
        String name, DimensionTemplate dimensionTemplate
    ) {
        DimensionTemplate.registerDimensionTemplate(name, dimensionTemplate);
    }
    
    public static interface PreRemoveDimensionCallback {
        void accept(ServerWorld world);
    }
    
    public static final Event<PreRemoveDimensionCallback> SERVER_PRE_REMOVE_DIMENSION_EVENT =
        EventFactory.createArrayBacked(
            PreRemoveDimensionCallback.class,
            (listeners) -> ((world) -> {
                for (PreRemoveDimensionCallback listener : listeners) {
                    try {
                        listener.accept(world);
                    }
                    catch (Exception e) {
                        LOGGER.error("Error during before removing dimension event", e);
                    }
                }
            })
        );
}
