package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;

public class DimLibNetworking {
    public static final Logger LOGGER = LoggerFactory.getLogger(DimLibNetworking.class);
    
    public static record DimSyncPacket(
        NbtCompound dimIdToTypeIdTag
    ) implements CustomPayload {
        public static final CustomPayload.Id<DimSyncPacket> TYPE =
            new Id<>(Identifier.of("dimlib", "dim_sync"));
        
        public static final PacketCodec<RegistryByteBuf, DimSyncPacket> CODEC =
            PacketCodec.of(DimSyncPacket::write, DimSyncPacket::new);
        
        public DimSyncPacket(RegistryByteBuf buf) {
            this(buf.readNbt());
        }
        
        public void write(RegistryByteBuf buf) {
            buf.writeNbt(dimIdToTypeIdTag);
        }
        
        public static DimSyncPacket createPacket(MinecraftServer server) {
            DynamicRegistryManager registryManager = server.getRegistryManager();
            Registry<DimensionType> dimensionTypes = registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE);
            
            NbtCompound dimIdToDimTypeId = new NbtCompound();
            for (ServerWorld world : server.getWorlds()) {
                RegistryKey<World> dimId = world.getRegistryKey();
                
                RegistryEntry<DimensionType> dimTypeEntry = world.getDimensionEntry();
                Identifier dimTypeId = dimensionTypes.getId(dimTypeEntry.value());
                
                if (dimTypeId == null) {
                    LOGGER.error("Cannot find dimension type for {}", dimId.getValue());
                    LOGGER.error(
                        "Registered dimension types {}", dimensionTypes.getIds()
                    );
                    dimTypeId = DimensionTypes.OVERWORLD.getValue();
                }
                
                dimIdToDimTypeId.putString(
                    dimId.getValue().toString(),
                    dimTypeId.toString()
                );
            }
            
            return new DimSyncPacket(dimIdToDimTypeId);
        }
        
        public ImmutableMap<RegistryKey<World>, RegistryKey<DimensionType>> toMap() {
            NbtCompound tag = dimIdToTypeIdTag();
            
            ImmutableMap.Builder<RegistryKey<World>, RegistryKey<DimensionType>> builder =
                new ImmutableMap.Builder<>();
            
            for (String key : tag.getKeys()) {
                RegistryKey<World> dimId = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(key)
                );
                String dimTypeId = tag.getString(key).orElse("");
                RegistryKey<DimensionType> dimType = RegistryKey.of(
                    RegistryKeys.DIMENSION_TYPE,
                    Identifier.of(dimTypeId)
                );
                builder.put(dimId, dimType);
            }
            
            return builder.build();
        }
        
        @Environment(EnvType.CLIENT)
        public void handle(ClientPlayPacketListener listener) {
            Validate.isTrue(
                MinecraftClient.getInstance().isOnThread(),
                "Not running in client thread"
            );
            
            LOGGER.info(
                "Client received dimension info\n{}",
                String.join("\n", dimIdToTypeIdTag.getKeys())
            );
            
            var dimIdToDimType = this.toMap();
            ClientDimensionInfo.accept(dimIdToDimType);
            // Note: levels field was removed from ClientPlayNetworkHandler in 1.21.11
            // ((IClientPacketListener) listener).ip_setLevels(dimIdToDimType.keySet());
            
            DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.invoker().run(
                ClientDimensionInfo.getDimensionIds()
            );
        }
        
        @Override
        public @NotNull Id<? extends CustomPayload> getId() {
            return TYPE;
        }
    }
    
    public static void init() {
        PayloadTypeRegistry.playS2C().register(
            DimSyncPacket.TYPE, DimSyncPacket.CODEC
        );
    }
    
    @SuppressWarnings("resource")
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            DimSyncPacket.TYPE,
            (p, c) -> {
                // it's now handled in client thread, not networking thread
                MinecraftClient client = c.client();
                ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
                if (networkHandler != null) {
                    p.handle(networkHandler);
                }
            }
        );
    }
}
