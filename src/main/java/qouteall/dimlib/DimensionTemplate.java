package qouteall.dimlib;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DimensionTemplate(
    ResourceKey<DimensionType> dimensionTypeId,
    DimensionFactory dimensionFactory
) {
    
    public static interface DimensionFactory {
        LevelStem createLevelStem(
            MinecraftServer server,
            Holder<DimensionType> dimensionTypeHolder
        );
    }
    
    static final Map<String, DimensionTemplate> DIMENSION_TEMPLATES = new LinkedHashMap<>();
    
    public static void registerDimensionTemplate(
        String name, DimensionTemplate dimensionTemplate
    ) {
        DIMENSION_TEMPLATES.put(name, dimensionTemplate);
    }
    
    public LevelStem createLevelStem(MinecraftServer server) {
        RegistryAccess registryManager = server.registryAccess();
        Registry<DimensionType> dimensionTypes = registryManager.lookupOrThrow(Registries.DIMENSION_TYPE);
        
        Holder.Reference<DimensionType> holder = dimensionTypes.getOrThrow(dimensionTypeId);
        
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
        BuiltinDimensionTypes.OVERWORLD,
        (server, dimTypeHolder) -> {
            RegistryAccess registryManager = server.registryAccess();
            Registry<Biome> biomeRegistry = registryManager.lookupOrThrow(Registries.BIOME);
            
            ResourceKey<Biome> plainsKey = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "plains"));
            Holder.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(plainsKey);
            
            FlatLevelGeneratorSettings config = new FlatLevelGeneratorSettings(
                Optional.empty(),
                plainsHolder,
                List.of()
            );
            config.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
            config.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
            config.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
            
            return new LevelStem(dimTypeHolder, new FlatLevelSource(config));
        }
    );
    
    public static final DimensionTemplate STONE_TEMPLATE = new DimensionTemplate(
        BuiltinDimensionTypes.OVERWORLD,
        (server, dimTypeHolder) -> {
            RegistryAccess registryManager = server.registryAccess();
            Registry<Biome> biomeRegistry = registryManager.lookupOrThrow(Registries.BIOME);
            
            ResourceKey<Biome> plainsKey = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "plains"));
            Holder.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(plainsKey);
            
            FlatLevelGeneratorSettings config = new FlatLevelGeneratorSettings(
                Optional.empty(),
                plainsHolder,
                List.of()
            );
            config.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
            config.getLayersInfo().add(new FlatLayerInfo(60, Blocks.STONE));
            config.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
            
            return new LevelStem(dimTypeHolder, new FlatLevelSource(config));
        }
    );
    
    public static final DimensionTemplate VOID_TEMPLATE = new DimensionTemplate(
        BuiltinDimensionTypes.OVERWORLD,
        (server, dimTypeHolder) -> {
            RegistryAccess registryManager = server.registryAccess();
            
            Registry<Biome> biomeRegistry = registryManager.lookupOrThrow(Registries.BIOME);
            
            ResourceKey<Biome> plainsKey = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "plains"));
            Holder.Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(plainsKey);
            
            FlatLevelGeneratorSettings flatChunkGeneratorConfig =
                new FlatLevelGeneratorSettings(
                    Optional.empty(),
                    plainsHolder,
                    List.of()
                );
            flatChunkGeneratorConfig.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
            
            FlatLevelSource chunkGenerator = new FlatLevelSource(flatChunkGeneratorConfig);
            
            return new LevelStem(dimTypeHolder, chunkGenerator);
        }
    );
}
