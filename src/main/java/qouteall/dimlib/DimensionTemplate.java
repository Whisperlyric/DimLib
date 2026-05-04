package qouteall.dimlib;

import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DimensionTemplate(
    RegistryKey<DimensionType> dimensionTypeId,
    DimensionFactory dimensionFactory
) {
    
    public static interface DimensionFactory {
        DimensionOptions createLevelStem(
            MinecraftServer server,
            RegistryEntry<DimensionType> dimensionTypeHolder
        );
    }
    
    static final Map<String, DimensionTemplate> DIMENSION_TEMPLATES = new LinkedHashMap<>();
    
    public static void registerDimensionTemplate(
        String name, DimensionTemplate dimensionTemplate
    ) {
        DIMENSION_TEMPLATES.put(name, dimensionTemplate);
    }
    
    public DimensionOptions createLevelStem(MinecraftServer server) {
        DynamicRegistryManager registryManager = server.getRegistryManager();
        Registry<DimensionType> dimensionTypes = registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE);
        
        RegistryEntry.Reference<DimensionType> holder = dimensionTypes.getOrThrow(dimensionTypeId);
        
        return dimensionFactory.createLevelStem(
            server, holder
        );
    }
    
    public static void init() {
        registerDimensionTemplate("void", VOID_TEMPLATE);
        registerDimensionTemplate("flat", FLAT_TEMPLATE);
        registerDimensionTemplate("stone", STONE_TEMPLATE);
    }
    
    public static final DimensionTemplate FLAT_TEMPLATE = new DimensionTemplate(
        DimensionTypes.OVERWORLD,
        (server, dimTypeHolder) -> {
            DynamicRegistryManager registryManager = server.getRegistryManager();
            Registry<Biome> biomeRegistry = registryManager.getOrThrow(RegistryKeys.BIOME);
            
            RegistryKey<Biome> plainsKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "plains"));
            RegistryEntry.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(plainsKey);
            
            FlatChunkGeneratorConfig config = new FlatChunkGeneratorConfig(
                Optional.empty(),
                plainsHolder,
                List.of()
            );
            config.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.BEDROCK));
            config.getLayers().add(new FlatChunkGeneratorLayer(2, Blocks.DIRT));
            config.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK));
            
            return new DimensionOptions(dimTypeHolder, new FlatChunkGenerator(config));
        }
    );
    
    public static final DimensionTemplate STONE_TEMPLATE = new DimensionTemplate(
        DimensionTypes.OVERWORLD,
        (server, dimTypeHolder) -> {
            DynamicRegistryManager registryManager = server.getRegistryManager();
            Registry<Biome> biomeRegistry = registryManager.getOrThrow(RegistryKeys.BIOME);
            
            RegistryKey<Biome> plainsKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "plains"));
            RegistryEntry.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(plainsKey);
            
            FlatChunkGeneratorConfig config = new FlatChunkGeneratorConfig(
                Optional.empty(),
                plainsHolder,
                List.of()
            );
            config.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.BEDROCK));
            config.getLayers().add(new FlatChunkGeneratorLayer(60, Blocks.STONE));
            config.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK));
            
            return new DimensionOptions(dimTypeHolder, new FlatChunkGenerator(config));
        }
    );
    
    public static final DimensionTemplate VOID_TEMPLATE = new DimensionTemplate(
        DimensionTypes.OVERWORLD,
        (server, dimTypeHolder) -> {
            DynamicRegistryManager registryManager = server.getRegistryManager();
            
            Registry<Biome> biomeRegistry = registryManager.getOrThrow(RegistryKeys.BIOME);
            
            RegistryKey<Biome> plainsKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", "plains"));
            RegistryEntry.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(plainsKey);
            
            FlatChunkGeneratorConfig flatChunkGeneratorConfig =
                new FlatChunkGeneratorConfig(
                    Optional.empty(),
                    plainsHolder,
                    List.of()
                );
            flatChunkGeneratorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.AIR));
            
            FlatChunkGenerator chunkGenerator = new FlatChunkGenerator(flatChunkGeneratorConfig);
            
            return new DimensionOptions(dimTypeHolder, chunkGenerator);
        }
    );
}
