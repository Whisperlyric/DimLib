package qouteall.dimlib;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMappedRegistry;
import qouteall.dimlib.ducks.IMinecraftServer;
import qouteall.dimlib.mixin.common.IEWorldBorder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class DynamicDimensionsImpl {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static boolean isRemovingDimension = false;
    
    public static void init() {
    
    }
    
    public static void addDimensionDynamically(
        MinecraftServer server,
        Identifier dimensionId,
        LevelStem dimensionOptions
    ) {
        ResourceKey<Level> dimensionResourceKey = ResourceKey.create(
            Registries.DIMENSION, dimensionId
        );
        
        Validate.isTrue(server.isSameThread(), "this should be called in server main thread");
        Validate.isTrue(server.isRunning(), "Server is not running");
        
        if (server.getLevel(dimensionResourceKey) != null) {
            throw new RuntimeException("Dimension " + dimensionId + " already exists.");
        }
        
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        WorldBorder worldBorder = overworld.getWorldBorder();
        Validate.notNull(worldBorder, "Overworld world border is null");
        
        WorldData worldData = server.getWorldData();
        ServerLevelData serverLevelData = worldData.overworldData();
        
        long seed = worldData.worldGenOptions().seed();
        long obfuscatedSeed = BiomeManager.obfuscateSeed(seed);
        
        ServerLevelData derivedLevelData = worldData.overworldData();
        
        ServerLevel newWorld = new ServerLevel(
            server,
            ((IMinecraftServer) server).dimlib_getExecutor(),
            ((IMinecraftServer) server).dimlib_getStorageSource(),
            derivedLevelData,
            dimensionResourceKey,
            dimensionOptions,
            false,
            obfuscatedSeed,
            ImmutableList.of(),
            false,
            overworld.getRandomSequences()
        );
        
        worldBorder.addListener(createDelegateBorderChangeListener(newWorld.getWorldBorder()));
        
        ((IMinecraftServer) server).dimlib_addDimensionToWorldMap(dimensionResourceKey, newWorld);
        
        Registry<LevelStem> levelStemRegistry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        ((IMappedRegistry) levelStemRegistry).dimlib_setIsFrozen(false);
        ((MappedRegistry<LevelStem>) levelStemRegistry).register(
            ResourceKey.create(Registries.LEVEL_STEM, dimensionId),
            dimensionOptions,
            RegistrationInfo.BUILT_IN
        );
        ((IMappedRegistry) levelStemRegistry).dimlib_setIsFrozen(true);
        
        LOGGER.info("Added Dimension {}", dimensionId);
        
        var dimSyncPacket = ServerPlayNetworking.createS2CPacket(
            DimLibNetworking.DimSyncPacket.createPacket(server)
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(dimSyncPacket);
        }
        
        DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.levelKeys());
    }
    
    public static void removeDimensionDynamically(ServerLevel world) {
        MinecraftServer server = world.getServer();
        
        Validate.isTrue(server.isSameThread());
        
        ResourceKey<Level> dimension = world.dimension();
        
        if (dimension == Level.OVERWORLD || dimension == Level.NETHER || dimension == Level.END) {
            throw new RuntimeException("Cannot remove vanilla dimension");
        }
        
        Validate.isTrue(server.isRunning(), "Server is not running");
        
        LOGGER.info("Started Removing Dimension {}", dimension.identifier());
        
        ((IMinecraftServer) server).dimlib_addTask(() -> {
            DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world);
            
            evacuatePlayersFromDimension(world);
            
            long startTime = System.nanoTime();
            long lastLogTime = System.nanoTime();
            
            isRemovingDimension = true;
            
            ((IMinecraftServer) server).dimlib_removeDimensionFromWorldMap(dimension);
            
            try {
                while (world.getChunkSource().getPendingTasksCount() > 0) {
                    world.getChunkSource().tick(() -> true, false);
                    
                    if (System.nanoTime() - lastLogTime > DimLibUtil.secondToNano(1)) {
                        lastLogTime = System.nanoTime();
                        LOGGER.info("waiting for chunk tasks to finish");
                    }
                    
                    if (System.nanoTime() - startTime > DimLibUtil.secondToNano(15)) {
                        LOGGER.error("Waited too long for chunk tasks");
                        break;
                    }
                }
            }
            catch (Throwable e) {
                LOGGER.error("Error when waiting for chunk tasks", e);
            }
            
            isRemovingDimension = false;
            
            LOGGER.info(
                "Finished chunk tasks in {} seconds",
                DimLibUtil.nanoToSecond(System.nanoTime() - startTime)
            );
            
            LOGGER.info(
                "Has entities: {}",
                world.getAllEntities().iterator().hasNext()
            );
            
            server.saveEverything(false, true, false);
            
            try {
                world.close();
            }
            catch (IOException e) {
                LOGGER.error("Error when closing world", e);
            }
            
            resetWorldBorderListener(server);
            
            Registry<LevelStem> levelStemRegistry = server.registryAccess()
                .lookupOrThrow(Registries.LEVEL_STEM);
            ((IMappedRegistry) levelStemRegistry).dimlib_forceRemove(dimension.identifier());
            
            LOGGER.info("Removed Dimension {}", dimension.identifier());
            
            var dimSyncPacket = ServerPlayNetworking.createS2CPacket(
                DimLibNetworking.DimSyncPacket.createPacket(server)
            );
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(dimSyncPacket);
            }
            
            DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.levelKeys());
        });
    }
    
    private static void resetWorldBorderListener(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        
        WorldBorder worldBorder = overworld.getWorldBorder();
        List<BorderChangeListener> borderChangeListeners =
            ((IEWorldBorder) worldBorder).ip_getListeners();
        borderChangeListeners.clear();
        for (ServerLevel serverWorld : server.getAllLevels()) {
            if (serverWorld != overworld) {
                worldBorder.addListener(createDelegateBorderChangeListener(serverWorld.getWorldBorder()));
            }
        }
    }
    
    private static void evacuatePlayersFromDimension(ServerLevel world) {
        MinecraftServer server = world.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        
        List<ServerPlayer> players = world.getPlayers(p -> true);
        
        BlockPos spawnPos = overworld.getLevelData().getRespawnData().pos();
        
        for (ServerPlayer player : players) {
            player.teleportTo(
                overworld,
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                Set.of(),
                0, 0,
                false
            );
            player.displayClientMessage(
                Component.literal(
                    "Teleported to spawn pos because dimension %s had been removed"
                        .formatted(world.dimension().identifier())
                ),
                false
            );
        }
    }
    
    private static BorderChangeListener createDelegateBorderChangeListener(WorldBorder border) {
        return new BorderChangeListener() {
            @Override
            public void onSetSize(WorldBorder worldBorder, double size) {
                border.setSize(size);
            }
            
            @Override
            public void onLerpSize(WorldBorder worldBorder, double fromSize, double toSize, long time, long timeStart) {
                border.lerpSizeBetween(fromSize, toSize, time, timeStart);
            }
            
            @Override
            public void onSetCenter(WorldBorder worldBorder, double x, double z) {
                border.setCenter(x, z);
            }
            
            @Override
            public void onSetWarningTime(WorldBorder worldBorder, int warningTime) {
                border.setWarningTime(warningTime);
            }
            
            @Override
            public void onSetWarningBlocks(WorldBorder worldBorder, int warningBlocks) {
                border.setWarningBlocks(warningBlocks);
            }
            
            @Override
            public void onSetDamagePerBlock(WorldBorder worldBorder, double damagePerBlock) {
                border.setDamagePerBlock(damagePerBlock);
            }
            
            @Override
            public void onSetSafeZone(WorldBorder worldBorder, double safeZoneRadius) {
                border.setSafeZone(safeZoneRadius);
            }
        };
    }
    
}
