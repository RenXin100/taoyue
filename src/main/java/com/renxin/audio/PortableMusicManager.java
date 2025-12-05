package com.renxin.audio;

import com.renxin.config.CpConfig;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.item.PortableAudioPlayerItem;
import com.renxin.registry.CpItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PortableMusicManager {

    private static final Map<UUID, Long> HEARTBEATS = new ConcurrentHashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PortableMusicManager::serverTick);
    }

    public static void updateHeartbeat(UUID playerUuid) {
        HEARTBEATS.put(playerUuid, System.currentTimeMillis());
    }

    private static void serverTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = HEARTBEATS.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID playerUuid = entry.getKey();
            long lastSeen = entry.getValue();

            // 1. 超时丢弃检测 (500ms)
            if (now - lastSeen > 500) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    CpModNetworking.sendPlayControlEntity(player.getServerWorld(), player.getId(), null, false, 0, 0);
                }
                it.remove();
                continue;
            }

            // 2. 周期性同步 (每 20 ticks / 1秒 执行一次)
            if (server.getTicks() % 20 == 0) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    syncToNearbyPlayers(player);
                }
            }
        }
    }

    private static void syncToNearbyPlayers(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(CpItems.PORTABLE_MUSIC_PLAYER)) {
            stack = player.getOffHandStack();
            if (!stack.isOf(CpItems.PORTABLE_MUSIC_PLAYER)) return;
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.getBoolean("IsPlaying")) return;

        int currentSlot = nbt.getInt("CurrentSlot");

        // === 修复点：正确读取 Inventory 数据 ===
        // 我们不创建 SimpleInventory，直接用 NBT 还原
        // 或者直接使用我们自己写的 PortablePlayerInventory 包装类
        com.renxin.inventory.PortablePlayerInventory inv = new com.renxin.inventory.PortablePlayerInventory(stack);
        ItemStack disc = inv.getStack(currentSlot);

        UUID trackId = CustomMusicDiscItem.getTrackId(disc);
        if (trackId == null) return;

        long start = nbt.getLong("PlayStartTime");
        long paused = nbt.getLong("PausedProgress");
        int currentOffset = (int) (System.currentTimeMillis() - start + paused);
        int range = CpConfig.get().broadcastRange;

        CpModNetworking.sendPlayControlEntity(player.getServerWorld(), player.getId(), trackId, true, currentOffset, range);
    }
}