package com.renxin.cpmod.network;

import com.renxin.network.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.UUID;

public final class CpModNetworking {
    public static final String MOD_ID = "cp-mod";

    public static final Identifier PLAY_CONTROL = new Identifier(MOD_ID, "play_control");
    public static final Identifier UPLOAD_START = new Identifier(MOD_ID, "upload_start");
    public static final Identifier UPLOAD_CHUNK = new Identifier(MOD_ID, "upload_chunk");
    public static final Identifier UPLOAD_COMPLETE = new Identifier(MOD_ID, "upload_complete");
    public static final Identifier TRACK_DATA_START = new Identifier(MOD_ID, "track_data_start");
    public static final Identifier TRACK_DATA_CHUNK = new Identifier(MOD_ID, "track_data_chunk");
    public static final Identifier TRACK_DATA_COMPLETE = new Identifier(MOD_ID, "track_data_complete");
    public static final Identifier PORTABLE_CONTROL = new Identifier(MOD_ID, "portable_control");
    public static final Identifier SONG_FINISHED = new Identifier(MOD_ID, "song_finished");
    public static final Identifier REQUEST_TRACK_DATA = new Identifier(MOD_ID, "request_track_data");

    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_START, UploadStartC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_CHUNK, UploadChunkC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_COMPLETE, UploadCompleteC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(PORTABLE_CONTROL, PortableControlC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(SONG_FINISHED, SongFinishedC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_TRACK_DATA, RequestTrackDataC2SPacket::receive);
    }

    public static void registerServerReceivers() { registerC2SPackets(); }

    // === S2C 发包逻辑 ===

    public static void sendPlayControl(ServerWorld world, BlockPos pos, UUID trackId, boolean playing, int offsetMs, int range) {
        sendPlayControlInternal(world, false, -1, pos, trackId, playing, offsetMs, range);
    }

    // 兼容旧方法 (默认64)
    public static void sendPlayControl(ServerWorld world, BlockPos pos, UUID trackId, boolean playing, int offsetMs) {
        sendPlayControlInternal(world, false, -1, pos, trackId, playing, offsetMs, 64);
    }

    public static void sendPlayControlEntity(ServerWorld world, int entityId, UUID trackId, boolean playing, int offsetMs, int range) {
        sendPlayControlInternal(world, true, entityId, BlockPos.ORIGIN, trackId, playing, offsetMs, range);
    }

    private static void sendPlayControlInternal(ServerWorld world, boolean isEntity, int entityId, BlockPos pos, UUID trackId, boolean playing, int offsetMs, int range) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(isEntity);
        if (isEntity) {
            buf.writeInt(entityId);
        } else {
            buf.writeBlockPos(pos);
        }
        buf.writeBoolean(playing);
        if (playing) {
            if (trackId == null) trackId = new UUID(0, 0);
            buf.writeUuid(trackId);
            buf.writeInt(offsetMs);
            buf.writeInt(range);
        }

        // === 关键优化：服务端距离筛选 ===

        // 1. 确定发声源位置
        Vec3d sourcePos;
        if (isEntity) {
            Entity entity = world.getEntityById(entityId);
            if (entity == null) return; // 实体没了就不发了
            sourcePos = entity.getPos();
        } else {
            sourcePos = Vec3d.ofCenter(pos);
        }

        // 2. 计算最大广播距离
        // 我们给它加 32 格的余量 (Buffer)，防止玩家在边缘反复横跳导致丢包
        // 比如 range=64，我们发给 96 格内的人，让他们提前准备
        double maxDist = range + 32.0;
        double maxDistSq = maxDist * maxDist;

        // 3. 遍历玩家，精准投送
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(sourcePos) <= maxDistSq) {
                ServerPlayNetworking.send(player, PLAY_CONTROL, buf);
            }
        }
    }

    public static void sendTrackDataToPlayer(ServerPlayerEntity player, UUID trackId, Path trackPath) {
        try {
            byte[] allBytes = java.nio.file.Files.readAllBytes(trackPath);
            int totalSize = allBytes.length;
            // 分片大小 16KB
            int chunkSize = 16 * 1024;

            PacketByteBuf startBuf = PacketByteBufs.create();
            startBuf.writeUuid(trackId);
            startBuf.writeInt(totalSize);
            ServerPlayNetworking.send(player, TRACK_DATA_START, startBuf);

            int offset = 0;
            while (offset < totalSize) {
                int len = Math.min(chunkSize, totalSize - offset);
                PacketByteBuf chunkBuf = PacketByteBufs.create();
                chunkBuf.writeUuid(trackId);
                chunkBuf.writeInt(len);
                chunkBuf.writeBytes(allBytes, offset, len);
                ServerPlayNetworking.send(player, TRACK_DATA_CHUNK, chunkBuf);
                offset += len;
            }
            PacketByteBuf completeBuf = PacketByteBufs.create();
            completeBuf.writeUuid(trackId);
            ServerPlayNetworking.send(player, TRACK_DATA_COMPLETE, completeBuf);
        } catch (java.io.IOException e) { e.printStackTrace(); }
    }
}