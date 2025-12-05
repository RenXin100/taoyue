package com.renxin.network;

import com.renxin.inventory.PortablePlayerInventory;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

public class SongFinishedC2SPacket {

    public static void receive(MinecraftServer server,
                               ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler,
                               PacketByteBuf buf,
                               PacketSender responseSender) {

        server.execute(() -> {
            // 1. 获取玩家手持的播放机
            ItemStack stack = player.getMainHandStack();
            // 简单的校验，防止发包时物品已经被换掉了
            // if (!(stack.getItem() instanceof PortableAudioPlayerItem)) return;

            NbtCompound nbt = stack.getOrCreateNbt();
            if (!nbt.getBoolean("IsPlaying")) {
                // 如果服务端认为已经停止了（可能是手动点的停止），就忽略这个包
                return;
            }

            // 2. 根据模式决定下一首
            int mode = nbt.getInt("PlayMode");
            int currentSlot = nbt.getInt("CurrentSlot");
            int nextSlot = currentSlot;
            PortablePlayerInventory inv = new PortablePlayerInventory(stack);
            int size = inv.size();

            switch (mode) {
                case PortableControlC2SPacket.MODE_SEQ: // 顺序播放
                    nextSlot = currentSlot + 1;
                    if (nextSlot >= size) {
                        // 顺序播放到头了，停止播放
                        PortableControlC2SPacket.stopPlaying(player, nbt);
                        return;
                    }
                    break;

                case PortableControlC2SPacket.MODE_LOOP_ONE: // 单曲循环
                    nextSlot = currentSlot; // 保持不变，重新播放
                    break;

                case PortableControlC2SPacket.MODE_SHUFFLE: // 随机播放
                    nextSlot = new Random().nextInt(size);
                    break;

                case PortableControlC2SPacket.MODE_LOOP_LIST: // 列表循环
                default:
                    nextSlot = (currentSlot + 1) % size;
                    break;
            }

            // 3. 寻找下一个有效唱片（跳过空格子）
            // 简单的防死循环计数
            int attempts = 0;
            while (attempts < size) {
                if (!inv.getStack(nextSlot).isEmpty()) {
                    // 找到了，播放它
                    PortableControlC2SPacket.playSlot(player, stack, nbt, inv, nextSlot);
                    return;
                }

                // 这一格是空的，根据模式继续找
                if (mode == PortableControlC2SPacket.MODE_SHUFFLE) {
                    nextSlot = new Random().nextInt(size);
                } else {
                    // 线性查找下一个
                    nextSlot = (nextSlot + 1) % size;
                }
                attempts++;
            }

            // 4. 如果全都是空的（或者找不到有效唱片），停止
            PortableControlC2SPacket.stopPlaying(player, nbt);
        });
    }
}