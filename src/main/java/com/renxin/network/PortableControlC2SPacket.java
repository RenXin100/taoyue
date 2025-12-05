package com.renxin.network;

import com.renxin.audio.ServerTrackRegistry;
import com.renxin.config.CpConfig;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.inventory.PortablePlayerInventory;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.registry.CpItems;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.nio.file.Path;
import java.util.UUID;

public class PortableControlC2SPacket {

    public static final byte CMD_PREV = 0;
    public static final byte CMD_PLAY_PAUSE = 1;
    public static final byte CMD_NEXT = 2;
    public static final byte CMD_MODE = 3;

    public static final int MODE_SEQ = 0;
    public static final int MODE_LOOP_ONE = 1;
    public static final int MODE_SHUFFLE = 2;
    public static final int MODE_LOOP_LIST = 3;

    public static void receive(MinecraftServer server,
                               ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler,
                               PacketByteBuf buf,
                               PacketSender responseSender) {
        byte command = buf.readByte();

        server.execute(() -> {
            ItemStack stack = findPortablePlayer(player);
            if (stack.isEmpty()) return;

            NbtCompound nbt = stack.getOrCreateNbt();
            PortablePlayerInventory inv = new PortablePlayerInventory(stack);

            switch (command) {
                case CMD_PLAY_PAUSE -> togglePlayPause(player, stack, nbt, inv);
                case CMD_PREV -> playRelative(player, stack, nbt, inv, -1);
                case CMD_NEXT -> playRelative(player, stack, nbt, inv, 1);
                case CMD_MODE -> {
                    int mode = nbt.getInt("PlayMode");
                    mode = (mode + 1) % 4;
                    nbt.putInt("PlayMode", mode);
                }
            }
        });
    }

    private static ItemStack findPortablePlayer(ServerPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(CpItems.PORTABLE_MUSIC_PLAYER)) return main;
        ItemStack off = player.getOffHandStack();
        if (off.isOf(CpItems.PORTABLE_MUSIC_PLAYER)) return off;
        return ItemStack.EMPTY;
    }

    private static void togglePlayPause(ServerPlayerEntity player, ItemStack stack, NbtCompound nbt, PortablePlayerInventory inv) {
        boolean isPlaying = nbt.getBoolean("IsPlaying");

        if (isPlaying) {
            stopPlaying(player, nbt);
        } else {
            int currentSlot = nbt.getInt("CurrentSlot");
            if (!isValidDisc(inv.getStack(currentSlot))) {
                int firstValid = -1;
                for (int i = 0; i < inv.size(); i++) {
                    if (isValidDisc(inv.getStack(i))) {
                        firstValid = i;
                        break;
                    }
                }
                if (firstValid != -1) {
                    currentSlot = firstValid;
                    nbt.putInt("CurrentSlot", currentSlot);
                    // 自动跳槽位视为切歌，重置进度
                    nbt.putLong("PausedProgress", 0);
                } else {
                    return;
                }
            }
            playSlot(player, stack, nbt, inv, currentSlot);
        }
    }

    public static void stopPlaying(ServerPlayerEntity player, NbtCompound nbt) {
        // === 修改点 1：保存进度 ===
        if (nbt.getBoolean("IsPlaying")) {
            long start = nbt.getLong("PlayStartTime");
            long alreadyPlayed = nbt.getLong("PausedProgress");
            long elapsed = System.currentTimeMillis() - start;
            // 累加进度
            nbt.putLong("PausedProgress", alreadyPlayed + elapsed);
        }

        nbt.putBoolean("IsPlaying", false);
        CpModNetworking.sendPlayControlEntity((ServerWorld) player.getWorld(), player.getId(), null, false, 0, 0);
    }

    private static void playRelative(ServerPlayerEntity player, ItemStack stack, NbtCompound nbt, PortablePlayerInventory inv, int direction) {
        int currentSlot = nbt.getInt("CurrentSlot");
        int startSlot = currentSlot;
        int size = inv.size();

        for (int i = 0; i < size; i++) {
            currentSlot = (currentSlot + direction + size) % size;

            ItemStack disc = inv.getStack(currentSlot);
            if (isValidDisc(disc)) {
                // 切歌了，重置进度
                nbt.putLong("PausedProgress", 0);
                playSlot(player, stack, nbt, inv, currentSlot);
                return;
            }

            if (currentSlot == startSlot) {
                if (isValidDisc(inv.getStack(currentSlot))) {
                    nbt.putLong("PausedProgress", 0); // 即使是同一首，如果是切了一圈回来的，通常也期望重播
                    playSlot(player, stack, nbt, inv, currentSlot);
                } else {
                    stopPlaying(player, nbt);
                }
                return;
            }
        }
    }

    public static void playSlot(ServerPlayerEntity player, ItemStack stack, NbtCompound nbt, PortablePlayerInventory inv, int slot) {
        ItemStack disc = inv.getStack(slot);
        if (!isValidDisc(disc)) {
            stopPlaying(player, nbt);
            return;
        }

        // 检查是否在手动切换到另一首歌
        if (slot != nbt.getInt("CurrentSlot")) {
            nbt.putLong("PausedProgress", 0);
        }

        UUID trackId = null;
        if (disc.getItem() instanceof CustomMusicDiscItem) {
            trackId = CustomMusicDiscItem.getTrackId(disc);
        }

        if (trackId != null) {
            Path trackPath = ServerTrackRegistry.getTrackPath(trackId);
            if (trackPath != null) {
                int range = CpConfig.get().broadcastRange;
                CpModNetworking.sendPlayControlEntity((ServerWorld) player.getWorld(), player.getId(), null, false, 0, 0);
                // === 修改点 2：读取保存的进度 ===
                long offset = nbt.getLong("PausedProgress");

                // 发送播放指令 (带 Offset)
                CpModNetworking.sendPlayControlEntity((ServerWorld) player.getWorld(), player.getId(), trackId, true, (int) offset, range);

                nbt.putBoolean("IsPlaying", true);
                nbt.putInt("CurrentSlot", slot);
                nbt.putString("CurrentTrackName", disc.getName().getString());

                // 更新开始时间
                nbt.putLong("PlayStartTime", System.currentTimeMillis());

                // 估算时长保持不变
                long fileSize = 0;
                try { fileSize = java.nio.file.Files.size(trackPath); } catch (Exception ignored) {}
                long estimatedDurationMs = (fileSize / (1024 * 16)) * 1000;
                if (estimatedDurationMs < 1000) estimatedDurationMs = 180000;
                nbt.putLong("TotalDuration", estimatedDurationMs);
            }
        }
    }

    private static boolean isValidDisc(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof CustomMusicDiscItem || stack.getItem() instanceof MusicDiscItem);
    }
}