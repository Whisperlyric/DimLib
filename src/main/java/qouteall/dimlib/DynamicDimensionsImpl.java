package qouteall.dimlib;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
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
        DimensionOptions dimensionOptions
    ) {
        RegistryKey<World> dimensionResourceKey = RegistryKey.of(
            RegistryKeys.WORLD, dimensionId
        );
        
        Validate.isTrue(server.isOnThread(), "this should be called in server main thread");
        Validate.isTrue(server.isRunning(), "Server is not running");
        
        if (server.getWorld(dimensionResourceKey) != null) {
            throw new RuntimeException("Dimension " + dimensionId + " already exists.");
        }
        
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        WorldBorder worldBorder = overworld.getWorldBorder();
        Validate.notNull(worldBorder, "Overworld world border is null");
        
        SaveProperties worldData = server.getSaveProperties();
        ServerWorldProperties serverLevelData = worldData.getMainWorldProperties();
        
        long seed = worldData.getGeneratorOptions().getSeed();
        long obfuscatedSeed = BiomeAccess.hashSeed(seed);
        
        ServerWorldProperties derivedLevelData = worldData.getMainWorldProperties();
        
        ServerWorld newWorld = new ServerWorld(
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
        
        Registry<DimensionOptions> levelStemRegistry = server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION);
        ((IMappedRegistry) levelStemRegistry).dimlib_setIsFrozen(false);
        ((SimpleRegistry<DimensionOptions>) levelStemRegistry).add(
            RegistryKey.of(RegistryKeys.DIMENSION, dimensionId),
            dimensionOptions,
            RegistryEntryInfo.DEFAULT
        );
        ((IMappedRegistry) levelStemRegistry).dimlib_setIsFrozen(true);
        
        LOGGER.info("Added Dimension {}", dimensionId);
        
        var dimSyncPacket = ServerPlayNetworking.createS2CPacket(
            DimLibNetworking.DimSyncPacket.createPacket(server)
        );
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(dimSyncPacket);
        }
        
        DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.getWorldRegistryKeys());
    }
    
    public static void removeDimensionDynamically(ServerWorld world) {
        MinecraftServer server = world.getServer();
        
        Validate.isTrue(server.isOnThread());
        
        RegistryKey<World> dimension = world.getRegistryKey();
        
        if (dimension == World.OVERWORLD || dimension == World.NETHER || dimension == World.END) {
            throw new RuntimeException("Cannot remove vanilla dimension");
        }
        
        Validate.isTrue(server.isRunning(), "Server is not running");
        
        LOGGER.info("Started Removing Dimension {}", dimension.getValue());
        
        ((IMinecraftServer) server).dimlib_addTask(() -> {
            DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world);
            
            evacuatePlayersFromDimension(world);
            
            long startTime = System.nanoTime();
            long lastLogTime = System.nanoTime();
            
            isRemovingDimension = true;
            
            ((IMinecraftServer) server).dimlib_removeDimensionFromWorldMap(dimension);
            
            try {
                while (world.getChunkManager().getPendingTasks() > 0) {
                    world.getChunkManager().tick(() -> true, false);
                    
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
                world.iterateEntities().iterator().hasNext()
            );
            
            server.saveAll(false, true, false);
            
            try {
                world.close();
            }
            catch (IOException e) {
                LOGGER.error("Error when closing world", e);
            }
            
            resetWorldBorderListener(server);
            
            Registry<DimensionOptions> levelStemRegistry = server.getRegistryManager()
                .getOrThrow(RegistryKeys.DIMENSION);
            ((IMappedRegistry) levelStemRegistry).dimlib_forceRemove(dimension.getValue());
            
            LOGGER.info("Removed Dimension {}", dimension.getValue());
            
            var dimSyncPacket = ServerPlayNetworking.createS2CPacket(
                DimLibNetworking.DimSyncPacket.createPacket(server)
            );
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.networkHandler.sendPacket(dimSyncPacket);
            }
            
            DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.getWorldRegistryKeys());
        });
    }
    
    private static void resetWorldBorderListener(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        
        WorldBorder worldBorder = overworld.getWorldBorder();
        List<WorldBorderListener> borderChangeListeners =
            ((IEWorldBorder) worldBorder).ip_getListeners();
        borderChangeListeners.clear();
        for (ServerWorld serverWorld : server.getWorlds()) {
            if (serverWorld != overworld) {
                worldBorder.addListener(createDelegateBorderChangeListener(serverWorld.getWorldBorder()));
            }
        }
    }
    
    private static void evacuatePlayersFromDimension(ServerWorld world) {
        MinecraftServer server = world.getServer();
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        
        List<ServerPlayerEntity> players = world.getPlayers(p -> true);
        
        BlockPos spawnPos = overworld.getLevelProperties().getSpawnPoint().getPos();
        
        for (ServerPlayerEntity player : players) {
            player.teleport(
                overworld,
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                Set.of(),
                0, 0,
                false
            );
            player.sendMessage(
                Text.literal(
                    "Teleported to spawn pos because dimension %s had been removed"
                        .formatted(world.getRegistryKey().getValue())
                ),
                false
            );
        }
    }
    
    private static WorldBorderListener createDelegateBorderChangeListener(WorldBorder border) {
        return new WorldBorderListener() {
            @Override
            public void onSizeChange(WorldBorder worldBorder, double size) {
                border.setSize(size);
            }
            
            @Override
            public void onInterpolateSize(WorldBorder worldBorder, double fromSize, double toSize, long time, long timeStart) {
                border.interpolateSize(fromSize, toSize, time, timeStart);
            }
            
            @Override
            public void onCenterChanged(WorldBorder worldBorder, double x, double z) {
                border.setCenter(x, z);
            }
            
            @Override
            public void onWarningTimeChanged(WorldBorder worldBorder, int warningTime) {
                border.setWarningTime(warningTime);
            }
            
            @Override
            public void onWarningBlocksChanged(WorldBorder worldBorder, int warningBlocks) {
                border.setWarningBlocks(warningBlocks);
            }
            
            @Override
            public void onDamagePerBlockChanged(WorldBorder worldBorder, double damagePerBlock) {
                border.setDamagePerBlock(damagePerBlock);
            }
            
            @Override
            public void onSafeZoneChanged(WorldBorder worldBorder, double safeZoneRadius) {
                border.setSafeZone(safeZoneRadius);
            }
        };
    }
    
}
