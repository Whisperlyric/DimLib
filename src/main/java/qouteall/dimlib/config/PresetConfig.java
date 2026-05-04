package qouteall.dimlib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.api.DimensionAPI;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PresetConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "dimlib_presets.json";
    
    public static void loadAndRegister(MinecraftServer server) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        
        if (!Files.exists(configPath)) {
            createDefaultConfig(configPath);
        }
        
        try (BufferedReader reader = Files.newBufferedReader(configPath)) {
            JsonObject config = GSON.fromJson(reader, JsonObject.class);
            
            if (config.has("presets") && config.get("presets").isJsonObject()) {
                JsonObject presets = config.getAsJsonObject("presets");
                
                for (String presetName : presets.keySet()) {
                    try {
                        JsonObject presetData = presets.getAsJsonObject(presetName);
                        DimensionTemplate template = parsePreset(server, presetName, presetData);
                        if (template != null) {
                            DimensionAPI.registerDimensionTemplate(presetName, template);
                            LOGGER.info("Registered custom preset '{}' from config", presetName);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse preset '{}': {}", presetName, e.getMessage());
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to load preset config: {}", e.getMessage());
        }
    }
    
    private static void createDefaultConfig(Path path) {
        try {
            Files.createDirectories(path.getParent());
            
            JsonObject defaultConfig = new JsonObject();
            defaultConfig.addProperty("_comment", "DimLib Custom Presets Configuration");
            defaultConfig.addProperty("_docs", "");
            
            JsonObject presets = new JsonObject();
            
            JsonObject exampleFlat = new JsonObject();
            exampleFlat.addProperty("type", "flat");
            exampleFlat.addProperty("biome", "minecraft:plains");
            JsonArray layers = new JsonArray();
            layers.add(createLayer("minecraft:bedrock", 1));
            layers.add(createLayer("minecraft:dirt", 3));
            layers.add(createLayer("minecraft:grass_block", 1));
            exampleFlat.add("layers", layers);
            presets.add("example_flat", exampleFlat);
            
            JsonObject exampleVoid = new JsonObject();
            exampleVoid.addProperty("type", "flat");
            exampleVoid.addProperty("biome", "minecraft:the_void");
            JsonArray voidLayers = new JsonArray();
            voidLayers.add(createLayer("minecraft:air", 1));
            exampleVoid.add("layers", voidLayers);
            presets.add("example_void", exampleVoid);
            
            defaultConfig.add("presets", presets);
            
            Files.writeString(path, GSON.toJson(defaultConfig));
            LOGGER.info("Created default preset config at {}", path);
        } catch (IOException e) {
            LOGGER.error("Failed to create default preset config: {}", e.getMessage());
        }
    }
    
    private static JsonObject createLayer(String block, int height) {
        JsonObject layer = new JsonObject();
        layer.addProperty("block", block);
        layer.addProperty("height", height);
        return layer;
    }
    
    private static DimensionTemplate parsePreset(MinecraftServer server, String name, JsonObject data) {
        String type = data.has("type") ? data.get("type").getAsString() : "flat";
        
        if (!type.equals("flat")) {
            LOGGER.warn("Preset '{}' has unsupported type '{}', only 'flat' is supported", name, type);
            return null;
        }
        
        String biomeId = data.has("biome") ? data.get("biome").getAsString() : "minecraft:plains";
        Identifier biomeIdentifier = Identifier.tryParse(biomeId);
        if (biomeIdentifier == null) {
            LOGGER.error("Invalid biome id '{}' in preset '{}'", biomeId, name);
            return null;
        }
        
        if (!data.has("layers") || !data.get("layers").isJsonArray()) {
            LOGGER.error("Preset '{}' missing 'layers' array", name);
            return null;
        }
        
        JsonArray layersArray = data.getAsJsonArray("layers");
        List<PresetLayer> layers = new ArrayList<>();
        
        for (int i = 0; i < layersArray.size(); i++) {
            JsonObject layerObj = layersArray.get(i).getAsJsonObject();
            String blockId = layerObj.has("block") ? layerObj.get("block").getAsString() : "minecraft:air";
            int height = layerObj.has("height") ? layerObj.get("height").getAsInt() : 1;
            
            Identifier blockIdentifier = Identifier.tryParse(blockId);
            if (blockIdentifier == null) {
                LOGGER.error("Invalid block id '{}' in preset '{}'", blockId, name);
                return null;
            }
            
            layers.add(new PresetLayer(blockIdentifier, height));
        }
        
        return new DimensionTemplate(
            net.minecraft.world.dimension.DimensionTypes.OVERWORLD,
            (srv, dimTypeHolder) -> {
                var registryManager = srv.getRegistryManager();
                var biomeRegistry = registryManager.getOrThrow(RegistryKeys.BIOME);
                
                RegistryKey<net.minecraft.world.biome.Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeIdentifier);
                var biomeHolder = biomeRegistry.getOrThrow(biomeKey);
                
                var config = new net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig(
                    Optional.empty(),
                    biomeHolder,
                    List.of()
                );
                
                for (PresetLayer layer : layers) {
                    Block block = Registries.BLOCK.get(layer.block);
                    config.getLayers().add(new net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer(
                        layer.height, block
                    ));
                }
                
                return new net.minecraft.world.dimension.DimensionOptions(
                    dimTypeHolder,
                    new net.minecraft.world.gen.chunk.FlatChunkGenerator(config)
                );
            }
        );
    }
    
    private record PresetLayer(Identifier block, int height) {}
}
