package com.renxin.client.audio;

import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.registry.CpBlocks; // 导入方块注册
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MusicClientManager {
    private static final Map<BlockPos, DynamicTrackSoundInstance> PLAYING_BLOCKS = new HashMap<>();
    private static final Map<Integer, EntityBoundDynamicTrackSoundInstance> PLAYING_ENTITIES = new ConcurrentHashMap<>();

    record PendingPlay(UUID trackId, int offsetMs, int range, long receivedTime) {}

    private static final Map<BlockPos, PendingPlay> PENDING_BLOCKS = new ConcurrentHashMap<>();
    private static final Map<Integer, PendingPlay> PENDING_ENTITIES = new ConcurrentHashMap<>();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MusicClientManager::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        // === 1. 检查实体播放 (随身听) ===
        Iterator<Map.Entry<Integer, EntityBoundDynamicTrackSoundInstance>> itEntity = PLAYING_ENTITIES.entrySet().iterator();
        while (itEntity.hasNext()) {
            Map.Entry<Integer, EntityBoundDynamicTrackSoundInstance> entry = itEntity.next();
            int entityId = entry.getKey();
            EntityBoundDynamicTrackSoundInstance sound = entry.getValue();

            Entity entity = client.world.getEntityById(entityId);
            // 实体消失或死亡 -> 停止
            if (entity == null || !entity.isAlive()) {
                client.getSoundManager().stop(sound);
                itEntity.remove();
                continue;
            }
            // 自然播放结束
            if (!client.getSoundManager().isPlaying(sound)) {
                itEntity.remove();
                if (entity == client.player) {
                    ClientPlayNetworking.send(CpModNetworking.SONG_FINISHED, PacketByteBufs.empty());
                }
            }
        }

        // === 2. 检查方块播放 (播放机) - 新增防呆检测 ===
        Iterator<Map.Entry<BlockPos, DynamicTrackSoundInstance>> itBlock = PLAYING_BLOCKS.entrySet().iterator();
        while (itBlock.hasNext()) {
            Map.Entry<BlockPos, DynamicTrackSoundInstance> entry = itBlock.next();
            BlockPos pos = entry.getKey();
            DynamicTrackSoundInstance sound = entry.getValue();

            // 核心修复：检查方块是否还是播放机
            // 如果方块变成了空气，或者变成了别的方块，立刻停止音乐
            if (!client.world.getBlockState(pos).isOf(CpBlocks.MUSIC_PLAYER)) {
                client.getSoundManager().stop(sound);
                itBlock.remove();
                continue;
            }

            // 自然播放结束检测
            if (!client.getSoundManager().isPlaying(sound)) {
                itBlock.remove();
            }
        }
    }

    // === 下面的播放/暂停逻辑保持不变 ===
    public static void playEntityTrack(int entityId, UUID trackId, int offsetMs, int range) {
        if (PLAYING_ENTITIES.containsKey(entityId)) return;
        if (PENDING_ENTITIES.containsKey(entityId)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.player != null) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity != null) {
                double maxDist = range + 16.0;
                if (entity.squaredDistanceTo(client.player) > maxDist * maxDist) return;

                if (ClientTrackCache.has(trackId)) {
                    startEntityPlaying(entity, trackId, offsetMs, range);
                } else {
                    PENDING_ENTITIES.put(entityId, new PendingPlay(trackId, offsetMs, range, System.currentTimeMillis()));
                    requestDownload(trackId);
                }
            }
        }
    }

    private static void startEntityPlaying(Entity entity, UUID trackId, int offsetMs, int range) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        EntityBoundDynamicTrackSoundInstance instance = new EntityBoundDynamicTrackSoundInstance(entity, trackId, offsetMs, range);
        PLAYING_ENTITIES.put(entity.getId(), instance);
        client.getSoundManager().play(instance);
    }

    public static void stopEntityTrack(int entityId) {
        PENDING_ENTITIES.remove(entityId);
        var instance = PLAYING_ENTITIES.remove(entityId);
        if (instance != null) {
            MinecraftClient.getInstance().getSoundManager().stop(instance);
        }
    }

    public static void playBlockTrack(BlockPos pos, UUID trackId, int offsetMs, int range) {
        stopBlockTrack(pos);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        double maxDist = range + 16.0;
        if (client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > maxDist * maxDist) return;

        if (ClientTrackCache.has(trackId)) {
            startBlockPlaying(pos, trackId, offsetMs, range);
        } else {
            PENDING_BLOCKS.put(pos, new PendingPlay(trackId, offsetMs, range, System.currentTimeMillis()));
            requestDownload(trackId);
        }
    }

    private static void startBlockPlaying(BlockPos pos, UUID trackId, int offsetMs, int range) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        DynamicTrackSoundInstance instance = new DynamicTrackSoundInstance(null, pos, trackId, offsetMs, range);
        PLAYING_BLOCKS.put(pos, instance);
        client.getSoundManager().play(instance);
    }

    public static void stopBlockTrack(BlockPos pos) {
        PENDING_BLOCKS.remove(pos);
        var instance = PLAYING_BLOCKS.remove(pos);
        if (instance != null) {
            MinecraftClient.getInstance().getSoundManager().stop(instance);
        }
    }

    public static void onTrackDataLoaded(UUID trackId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<BlockPos, PendingPlay>> blockIt = PENDING_BLOCKS.entrySet().iterator();
        while (blockIt.hasNext()) {
            Map.Entry<BlockPos, PendingPlay> entry = blockIt.next();
            PendingPlay pending = entry.getValue();
            if (pending.trackId().equals(trackId)) {
                long delay = now - pending.receivedTime();
                int compensatedOffset = pending.offsetMs() + (int) delay;
                startBlockPlaying(entry.getKey(), trackId, compensatedOffset, pending.range());
                blockIt.remove();
            }
        }

        Iterator<Map.Entry<Integer, PendingPlay>> entityIt = PENDING_ENTITIES.entrySet().iterator();
        while (entityIt.hasNext()) {
            Map.Entry<Integer, PendingPlay> entry = entityIt.next();
            PendingPlay pending = entry.getValue();
            if (pending.trackId().equals(trackId)) {
                Entity entity = client.world.getEntityById(entry.getKey());
                if (entity != null) {
                    long delay = now - pending.receivedTime();
                    int compensatedOffset = pending.offsetMs() + (int) delay;
                    startEntityPlaying(entity, trackId, compensatedOffset, pending.range());
                }
                entityIt.remove();
            }
        }
    }

    private static void requestDownload(UUID trackId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(trackId);
        ClientPlayNetworking.send(CpModNetworking.REQUEST_TRACK_DATA, buf);
    }

    public static void stopAll() {
        PENDING_BLOCKS.clear();
        PENDING_ENTITIES.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        for (var i : PLAYING_BLOCKS.values()) client.getSoundManager().stop(i);
        PLAYING_BLOCKS.clear();
        for (var i : PLAYING_ENTITIES.values()) client.getSoundManager().stop(i);
        PLAYING_ENTITIES.clear();
    }
}