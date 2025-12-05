package com.renxin.network;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * C2S: 通知服务器“我要开始上传某个文件”。
 * 只负责接收，不包含客户端发送逻辑。
 */
public final class UploadStartC2SPacket {

    private UploadStartC2SPacket() {}

    public static void receive(MinecraftServer server,
                               ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler,
                               PacketByteBuf buf,
                               PacketSender responseSender) {

        BlockPos pos = buf.readBlockPos();
        UUID uploadId = buf.readUuid();
        String fileName = buf.readString();
        int totalSize = buf.readInt();

        server.execute(() -> {
            NetworkHandler.handleUploadStart(player, pos, uploadId, fileName, totalSize);
        });
    }
}
