package com.renxin.network;

import com.renxin.audio.ServerTrackRegistry;
import com.renxin.cpmod.network.CpModNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.UUID;

public class RequestTrackDataC2SPacket {

    public static void receive(MinecraftServer server,
                               ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler,
                               PacketByteBuf buf,
                               PacketSender responseSender) {

        UUID trackId = buf.readUuid();

        server.execute(() -> {
            // 收到请求，查找文件
            Path path = ServerTrackRegistry.getTrackPath(trackId);
            if (path != null) {
                // 精准投喂：只发给这一个玩家
                CpModNetworking.sendTrackDataToPlayer(player, trackId, path);
            }
        });
    }
}