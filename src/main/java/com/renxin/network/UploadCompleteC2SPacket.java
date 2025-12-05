package com.renxin.network;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * C2S: 客户端分片全部发完，通知服务器可以收尾了。
 */
public final class UploadCompleteC2SPacket {

    private UploadCompleteC2SPacket() {}

    public static void receive(MinecraftServer server,
                               ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler,
                               PacketByteBuf buf,
                               PacketSender responseSender) {

        BlockPos pos = buf.readBlockPos();
        UUID uploadId = buf.readUuid();

        server.execute(() -> {
            NetworkHandler.handleUploadComplete(player, pos, uploadId);
        });
    }
}
