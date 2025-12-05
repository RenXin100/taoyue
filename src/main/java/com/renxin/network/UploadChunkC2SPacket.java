package com.renxin.network;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * C2S: 上传数据分片。
 * 仍然只负责接收，客户端发送在 MusicBurnerScreen 里写。
 */
public final class UploadChunkC2SPacket {

    private UploadChunkC2SPacket() {}

    /** 单片建议大小：16KB（必须 < 32767 字节） */
    public static final int MAX_CHUNK_SIZE = 16 * 1024;

    public static void receive(MinecraftServer server,
                               ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler,
                               PacketByteBuf buf,
                               PacketSender responseSender) {

        UUID uploadId = buf.readUuid();
        int index = buf.readVarInt();
        int len = buf.readVarInt();
        byte[] chunk = new byte[len];
        buf.readBytes(chunk);

        server.execute(() -> {
            NetworkHandler.handleUploadChunk(player, uploadId, index, chunk);
        });
    }
}
