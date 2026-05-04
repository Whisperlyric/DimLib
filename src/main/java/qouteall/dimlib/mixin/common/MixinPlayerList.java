package qouteall.dimlib.mixin.common;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.DimLibNetworking;

@Mixin(PlayerManager.class)
public class MixinPlayerList {
    @Inject(
        method = "onPlayerConnect",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/packet/s2c/play/DifficultyS2CPacket;<init>(Lnet/minecraft/world/Difficulty;Z)V"
        )
    )
    private void onConnectionEstablished(
        ClientConnection connection,
        ServerPlayerEntity player,
        ConnectedClientData clientData,
        CallbackInfo ci
    ) {
        MinecraftServer server = ((PlayerManager)(Object)this).getServer();
        player.networkHandler.sendPacket(
            ServerPlayNetworking.createS2CPacket(
                DimLibNetworking.DimSyncPacket.createPacket(server)
            )
        );
    }
}
