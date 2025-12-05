package com.renxin.cpmod.network;

import com.renxin.client.audio.ClientTrackCache;
import com.renxin.client.audio.MusicClientManager;
import com.renxin.block.entity.MusicPlayerBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public final class CpModClientNetworking {
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(CpModNetworking.PLAY_CONTROL, (client, handler, buf, sender) -> {
            boolean isEntity = buf.readBoolean();
            int entityId = -1;
            BlockPos pos = BlockPos.ORIGIN;

            if (isEntity) {
                entityId = buf.readInt();
            } else {
                pos = buf.readBlockPos();
            }

            boolean playing = buf.readBoolean();
            UUID trackId = null;
            int offsetMs = 0;
            int range = 64; // 默认值

            if (playing) {
                trackId = buf.readUuid();
                if (buf.readableBytes() >= 4) offsetMs = buf.readInt();
                if (buf.readableBytes() >= 4) range = buf.readInt();
            }

            final boolean isEntityFinal = isEntity;
            final boolean playingFinal = playing;
            final int entityIdFinal = entityId;
            final BlockPos posFinal = pos;
            final UUID trackIdFinal = trackId;
            final int offsetMsFinal = offsetMs;
            final int rangeFinal = range;

            client.execute(() -> {
                if (isEntityFinal) {
                    // 实体播放
                    if (playingFinal) {
                        MusicClientManager.playEntityTrack(entityIdFinal, trackIdFinal, offsetMsFinal, rangeFinal);
                    } else {
                        MusicClientManager.stopEntityTrack(entityIdFinal);
                    }
                } else {
                    // 方块播放
                    if (playingFinal) {
                        // 修复：传入 rangeFinal
                        MusicClientManager.playBlockTrack(posFinal, trackIdFinal, offsetMsFinal, rangeFinal);
                    } else {
                        MusicClientManager.stopBlockTrack(posFinal);
                    }

                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null && mc.world != null) {
                        BlockEntity be = mc.world.getBlockEntity(posFinal);
                        if (be instanceof MusicPlayerBlockEntity playerBe) {
                            playerBe.clientPlaying = playingFinal;
                        }
                    }
                }
            });
        });

        // 轨道数据同步部分保持不变
        ClientPlayNetworking.registerGlobalReceiver(CpModNetworking.TRACK_DATA_START, (client, handler, buf, sender) -> {
            UUID trackId = buf.readUuid();
            int totalSize = buf.readInt();
            client.execute(() -> ClientTrackCache.begin(trackId, totalSize));
        });

        ClientPlayNetworking.registerGlobalReceiver(CpModNetworking.TRACK_DATA_CHUNK, (client, handler, buf, sender) -> {
            UUID trackId = buf.readUuid();
            int len = buf.readInt();
            byte[] data = new byte[len];
            buf.readBytes(data);
            client.execute(() -> ClientTrackCache.append(trackId, data));
        });

        ClientPlayNetworking.registerGlobalReceiver(CpModNetworking.TRACK_DATA_COMPLETE, (client, handler, buf, sender) -> {
            UUID trackId = buf.readUuid();
            client.execute(() -> {
                ClientTrackCache.finish(trackId);
                MusicClientManager.onTrackDataLoaded(trackId);
            });
        });
    }
}