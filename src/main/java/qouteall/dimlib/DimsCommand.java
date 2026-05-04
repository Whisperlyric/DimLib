package qouteall.dimlib;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.dimension.DimensionOptions;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;

import java.util.List;
import java.util.stream.Collectors;

public class DimsCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static RegistryKey<net.minecraft.world.World> pendingRemovalDimension = null;
    private static long pendingRemovalTime = 0;
    private static final long CONFIRMATION_TIMEOUT_MS = 30000;
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> builder = CommandManager
            .literal("dims")
            .requires(source -> {
                Permission required = new Permission.Level(PermissionLevel.fromLevel(2));
                return source.getPermissions().hasPermission(required);
            });
        
        builder.then(CommandManager
            .literal("clone_dimension")
            .then(CommandManager.argument("templateDimension", DimensionArgumentType.dimension())
                .then(CommandManager.argument("newDimensionID", IdentifierArgumentType.identifier())
                    .executes(context -> {
                        ServerWorld templateDimension =
                            DimensionArgumentType.getDimensionArgument(context, "templateDimension");
                        Identifier newDimId = IdentifierArgumentType.getIdentifier(context, "newDimensionID");
                        
                        if (newDimId.getNamespace().equals("minecraft")) {
                            context.getSource().sendError(
                                Text.literal("namespace cannot be minecraft")
                            );
                            return 0;
                        }
                        
                        if (DimensionAPI.dimensionExistsInRegistry(
                            context.getSource().getServer(), newDimId
                        )) {
                            context.getSource().sendError(
                                Text.literal("Dimension " + newDimId + " already exists")
                            );
                            return 0;
                        }
                        
                        cloneDimension(
                            templateDimension, newDimId
                        );
                        
                        context.getSource().sendFeedback(() -> Text.literal(
                            "Dynamically added dimension %s".formatted(newDimId.toString())
                        ), true);
                        
                        return 0;
                    })
                )
            )
        );
        
        RequiredArgumentBuilder<ServerCommandSource, Identifier> addDimensionCommandNode =
            CommandManager.argument("newDimensionId", IdentifierArgumentType.identifier());
        
        for (var e : DimensionTemplate.DIMENSION_TEMPLATES.entrySet()) {
            String dimTemplateId = e.getKey();
            DimensionTemplate dimensionTemplate = e.getValue();
            addDimensionCommandNode.then(CommandManager.literal(dimTemplateId)
                .executes(context -> {
                    return runAddDimension(context, dimensionTemplate);
                })
            );
        }
        
        builder.then(CommandManager.literal("add_dimension")
            .then(addDimensionCommandNode)
        );
        
        builder.then(CommandManager
            .literal("remove_dimension")
            .then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
                .executes(context -> {
                    ServerWorld dimension =
                        DimensionArgumentType.getDimensionArgument(context, "dimension");
                    
                    int playerCount = dimension.getPlayers().size();
                    if (playerCount > 0) {
                        context.getSource().sendError(Text.literal(
                            "Cannot remove dimension %s because there are %d player(s) in it. Please teleport them out first."
                                .formatted(dimension.getRegistryKey().getValue(), playerCount)
                        ));
                        return 0;
                    }
                    
                    pendingRemovalDimension = dimension.getRegistryKey();
                    pendingRemovalTime = System.currentTimeMillis();
                    
                    context.getSource().sendFeedback(() -> Text.literal(
                        "Are you sure you want to remove dimension %s? ".formatted(dimension.getRegistryKey().getValue()) +
                        "The dimension will be unloaded and its data will not be saved. " +
                        "Run /dims confirm within 30 seconds to confirm."
                    ), false);
                    
                    return 0;
                })
            )
        );
        
        builder.then(CommandManager.literal("confirm")
            .executes(context -> {
                if (pendingRemovalDimension == null) {
                    context.getSource().sendError(Text.literal(
                        "No pending dimension removal. Run /dims remove_dimension <dimension> first."
                    ));
                    return 0;
                }
                
                if (System.currentTimeMillis() - pendingRemovalTime > CONFIRMATION_TIMEOUT_MS) {
                    pendingRemovalDimension = null;
                    context.getSource().sendError(Text.literal(
                        "Confirmation timed out. Please run /dims remove_dimension <dimension> again."
                    ));
                    return 0;
                }
                
                MinecraftServer server = context.getSource().getServer();
                ServerWorld dimension = server.getWorld(pendingRemovalDimension);
                
                if (dimension == null) {
                    context.getSource().sendError(Text.literal(
                        "Dimension %s no longer exists.".formatted(pendingRemovalDimension.getValue())
                    ));
                    pendingRemovalDimension = null;
                    return 0;
                }
                
                int playerCount = dimension.getPlayers().size();
                if (playerCount > 0) {
                    context.getSource().sendError(Text.literal(
                        "Cannot remove dimension %s because there are %d player(s) in it. Please teleport them out first."
                            .formatted(dimension.getRegistryKey().getValue(), playerCount)
                    ));
                    pendingRemovalDimension = null;
                    return 0;
                }
                
                RegistryKey<net.minecraft.world.World> removedKey = pendingRemovalDimension;
                pendingRemovalDimension = null;
                
                DimensionAPI.removeDimensionDynamically(dimension);
                
                context.getSource().sendFeedback(() -> Text.literal(
                    ("Dynamically removed dimension %s . Its world file is not yet deleted. " +
                        "Note: if the datapack config for that dimension exists, the dimension will be re-added after server restart.")
                        .formatted(removedKey.getValue())
                ), true);
                
                return 0;
            })
        );
        
        builder.then(CommandManager.literal("list")
            .executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                
                MutableText text = Text.literal(
                    server.getWorldRegistryKeys()
                        .stream()
                        .map(k -> k.getValue().toString())
                        .sorted()
                        .collect(Collectors.joining("\n"))
                );
                
                context.getSource().sendFeedback(() -> text, false);
                
                return 0;
            }));
        
        builder.then(CommandManager.literal("view_dim_config")
            .then(CommandManager.argument("dim", DimensionArgumentType.dimension())
                .executes(context -> {
                    ServerWorld world =
                        DimensionArgumentType.getDimensionArgument(context, "dim");
                    
                    SimpleRegistry<DimensionOptions> dimensionRegistry =
                        DimensionImpl.getDimensionRegistry(world.getServer());
                    
                    DimensionOptions dimensionOptions = dimensionRegistry.get(world.getRegistryKey().getValue());
                    
                    if (dimensionOptions == null) {
                        context.getSource().sendError(
                            Text.literal("Dimension config not found")
                        );
                        return 0;
                    }
                    
                    DataResult<JsonElement> encoded = DimensionOptions.CODEC.encodeStart(
                        RegistryOps.of(JsonOps.INSTANCE, world.getRegistryManager()),
                        dimensionOptions
                    );
                    
                    if (encoded.result().isPresent()) {
                        String jsonStr = DimLibUtil.GSON.toJson(encoded.result().get());
                        
                        context.getSource().sendFeedback(
                            () -> Text.literal(jsonStr),
                            true
                        );
                    }
                    else {
                        context.getSource().sendError(Text.literal(
                            encoded.error().toString()
                        ));
                    }
                    
                    return 0;
                })
            )
        );
        
        dispatcher.register(builder);
    }
    
    private static int runAddDimension(
        CommandContext<ServerCommandSource> context, DimensionTemplate template
    ) {
        Identifier newDimId = IdentifierArgumentType.getIdentifier(
            context, "newDimensionId"
        );
        
        if (newDimId.getNamespace().equals("minecraft")) {
            context.getSource().sendError(
                Text.literal("namespace cannot be minecraft")
            );
            return 0;
        }
        
        MinecraftServer server = context.getSource().getServer();
        
        if (DimensionAPI.dimensionExistsInRegistry(server, newDimId)) {
            context.getSource().sendError(
                Text.literal("Dimension " + newDimId + " already exists")
            );
            return 0;
        }
        
        DimensionAPI.addDimensionDynamically(
            server,
            newDimId,
            template.createLevelStem(server)
        );
        
        return 0;
    }
    
    private static void cloneDimension(
        ServerWorld templateDimension, Identifier newDimId
    ) {
        ChunkGenerator generator = templateDimension.getChunkManager().getChunkGenerator();
        
        MinecraftServer server = templateDimension.getServer();
        
        RegistryOps<JsonElement> registryOps = RegistryOps.of(
            JsonOps.INSTANCE,
            server.getRegistryManager()
        );
        
        DataResult<JsonElement> encoded = ChunkGenerator.CODEC.encodeStart(registryOps, generator);
        
        ChunkGenerator clonedGenerator = ChunkGenerator.CODEC.parse(registryOps, encoded.getOrThrow())
            .getOrThrow(error -> new RuntimeException("Failed to clone chunk generator: " + error));
        
        DimensionAPI.addDimension(
            server,
            newDimId,
            new DimensionOptions(
                templateDimension.getDimensionEntry(),
                clonedGenerator
            )
        );
    }
    
}
