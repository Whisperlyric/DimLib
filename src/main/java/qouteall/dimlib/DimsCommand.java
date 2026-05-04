package qouteall.dimlib;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.MappedRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.dimension.LevelStem;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;

import java.util.List;
import java.util.stream.Collectors;

public class DimsCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ResourceKey<net.minecraft.world.level.Level> pendingRemovalDimension = null;
    private static long pendingRemovalTime = 0;
    private static final long CONFIRMATION_TIMEOUT_MS = 30000;
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands
            .literal("dims")
            .requires(source -> {
                Permission required = new Permission.HasCommandLevel(PermissionLevel.byId(2));
                return source.permissions().hasPermission(required);
            });
        
        builder.then(Commands
            .literal("clone_dimension")
            .then(Commands.argument("templateDimension", DimensionArgument.dimension())
                .then(Commands.argument("newDimensionID", IdentifierArgument.id())
                    .executes(context -> {
                        ServerLevel templateDimension =
                            DimensionArgument.getDimension(context, "templateDimension");
                        Identifier newDimId = IdentifierArgument.getId(context, "newDimensionID");
                        
                        if (newDimId.getNamespace().equals("minecraft")) {
                            context.getSource().sendFailure(
                                Component.literal("namespace cannot be minecraft")
                            );
                            return 0;
                        }
                        
                        if (DimensionAPI.dimensionExistsInRegistry(
                            context.getSource().getServer(), newDimId
                        )) {
                            context.getSource().sendFailure(
                                Component.literal("Dimension " + newDimId + " already exists")
                            );
                            return 0;
                        }
                        
                        cloneDimension(
                            templateDimension, newDimId
                        );
                        
                        context.getSource().sendSuccess(() -> Component.literal(
                            "Dynamically added dimension %s".formatted(newDimId.toString())
                        ), true);
                        
                        return 0;
                    })
                )
            )
        );
        
        RequiredArgumentBuilder<CommandSourceStack, Identifier> addDimensionCommandNode =
            Commands.argument("newDimensionId", IdentifierArgument.id());
        
        for (var e : DimensionTemplate.DIMENSION_TEMPLATES.entrySet()) {
            String dimTemplateId = e.getKey();
            DimensionTemplate dimensionTemplate = e.getValue();
            addDimensionCommandNode.then(Commands.literal(dimTemplateId)
                .executes(context -> {
                    return runAddDimension(context, dimensionTemplate);
                })
            );
        }
        
        builder.then(Commands.literal("add_dimension")
            .then(addDimensionCommandNode)
        );
        
        builder.then(Commands
            .literal("remove_dimension")
            .then(Commands.argument("dimension", DimensionArgument.dimension())
                .executes(context -> {
                    ServerLevel dimension =
                        DimensionArgument.getDimension(context, "dimension");
                    
                    int playerCount = dimension.players().size();
                    if (playerCount > 0) {
                        context.getSource().sendFailure(Component.literal(
                            "Cannot remove dimension %s because there are %d player(s) in it. Please teleport them out first."
                                .formatted(dimension.dimension().identifier(), playerCount)
                        ));
                        return 0;
                    }
                    
                    pendingRemovalDimension = dimension.dimension();
                    pendingRemovalTime = System.currentTimeMillis();
                    
                    context.getSource().sendSuccess(() -> Component.literal(
                        "Are you sure you want to remove dimension %s? ".formatted(dimension.dimension().identifier()) +
                        "The dimension will be unloaded and its data will not be saved. " +
                        "Run /dims confirm within 30 seconds to confirm."
                    ), false);
                    
                    return 0;
                })
            )
        );
        
        builder.then(Commands.literal("confirm")
            .executes(context -> {
                if (pendingRemovalDimension == null) {
                    context.getSource().sendFailure(Component.literal(
                        "No pending dimension removal. Run /dims remove_dimension <dimension> first."
                    ));
                    return 0;
                }
                
                if (System.currentTimeMillis() - pendingRemovalTime > CONFIRMATION_TIMEOUT_MS) {
                    pendingRemovalDimension = null;
                    context.getSource().sendFailure(Component.literal(
                        "Confirmation timed out. Please run /dims remove_dimension <dimension> again."
                    ));
                    return 0;
                }
                
                MinecraftServer server = context.getSource().getServer();
                ServerLevel dimension = server.getLevel(pendingRemovalDimension);
                
                if (dimension == null) {
                    context.getSource().sendFailure(Component.literal(
                        "Dimension %s no longer exists.".formatted(pendingRemovalDimension.identifier())
                    ));
                    pendingRemovalDimension = null;
                    return 0;
                }
                
                int playerCount = dimension.players().size();
                if (playerCount > 0) {
                    context.getSource().sendFailure(Component.literal(
                        "Cannot remove dimension %s because there are %d player(s) in it. Please teleport them out first."
                            .formatted(dimension.dimension().identifier(), playerCount)
                    ));
                    pendingRemovalDimension = null;
                    return 0;
                }
                
                ResourceKey<net.minecraft.world.level.Level> removedKey = pendingRemovalDimension;
                pendingRemovalDimension = null;
                
                DimensionAPI.removeDimensionDynamically(dimension);
                
                context.getSource().sendSuccess(() -> Component.literal(
                    ("Dynamically removed dimension %s . Its world file is not yet deleted. " +
                        "Note: if the datapack config for that dimension exists, the dimension will be re-added after server restart.")
                        .formatted(removedKey.identifier())
                ), true);
                
                return 0;
            })
        );
        
        builder.then(Commands.literal("list")
            .executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                
                MutableComponent text = Component.literal(
                    server.levelKeys()
                        .stream()
                        .map(k -> k.identifier().toString())
                        .sorted()
                        .collect(Collectors.joining("\n"))
                );
                
                context.getSource().sendSuccess(() -> text, false);
                
                return 0;
            }));
        
        builder.then(Commands.literal("view_dim_config")
            .then(Commands.argument("dim", DimensionArgument.dimension())
                .executes(context -> {
                    ServerLevel world =
                        DimensionArgument.getDimension(context, "dim");
                    
                    MappedRegistry<LevelStem> dimensionRegistry =
                        DimensionImpl.getDimensionRegistry(world.getServer());
                    
                    LevelStem dimensionOptions = dimensionRegistry.getValue(world.dimension().identifier());
                    
                    if (dimensionOptions == null) {
                        context.getSource().sendFailure(
                            Component.literal("Dimension config not found")
                        );
                        return 0;
                    }
                    
                    DataResult<JsonElement> encoded = LevelStem.CODEC.encodeStart(
                        RegistryOps.create(JsonOps.INSTANCE, world.registryAccess()),
                        dimensionOptions
                    );
                    
                    if (encoded.result().isPresent()) {
                        String jsonStr = DimLibUtil.GSON.toJson(encoded.result().get());
                        
                        context.getSource().sendSuccess(
                            () -> Component.literal(jsonStr),
                            true
                        );
                    }
                    else {
                        context.getSource().sendFailure(Component.literal(
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
        CommandContext<CommandSourceStack> context, DimensionTemplate template
    ) {
        Identifier newDimId = IdentifierArgument.getId(
            context, "newDimensionId"
        );
        
        if (newDimId.getNamespace().equals("minecraft")) {
            context.getSource().sendFailure(
                Component.literal("namespace cannot be minecraft")
            );
            return 0;
        }
        
        MinecraftServer server = context.getSource().getServer();
        
        if (DimensionAPI.dimensionExistsInRegistry(server, newDimId)) {
            context.getSource().sendFailure(
                Component.literal("Dimension " + newDimId + " already exists")
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
        ServerLevel templateDimension, Identifier newDimId
    ) {
        MinecraftServer server = templateDimension.getServer();
        
        LevelStem originalLevelStem = new LevelStem(
            templateDimension.dimensionTypeRegistration(),
            templateDimension.getChunkSource().getGenerator()
        );
        
        RegistryOps<JsonElement> registryOps = RegistryOps.create(
            JsonOps.INSTANCE,
            server.registryAccess()
        );
        
        DataResult<JsonElement> encoded = LevelStem.CODEC.encodeStart(registryOps, originalLevelStem);
        
        if (encoded.isError()) {
            LOGGER.error("Failed to encode level stem: {}", encoded.error().get().message());
            throw new RuntimeException("Failed to encode level stem: " + encoded.error().get().message());
        }
        
        LevelStem clonedLevelStem = LevelStem.CODEC.parse(registryOps, encoded.getOrThrow())
            .getOrThrow(error -> new RuntimeException("Failed to clone level stem: " + error));
        
        DimensionAPI.addDimensionDynamically(
            server,
            newDimId,
            clonedLevelStem
        );
    }
    
}
