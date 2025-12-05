package com.renxin.item;

import com.renxin.audio.PortableMusicManager;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.inventory.PortablePlayerInventory;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.screen.PortablePlayerScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class PortableAudioPlayerItem extends Item {

    public PortableAudioPlayerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            user.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override
                public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {}

                @Override
                public Text getDisplayName() {
                    return stack.getName();
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
                    PortablePlayerInventory inventory = new PortablePlayerInventory(stack);
                    return new PortablePlayerScreenHandler(syncId, playerInventory, inventory, stack);
                }
            });
        }
        return TypedActionResult.success(stack);
    }

    /**
     * 核心逻辑：检测掉落、拿出唱片
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;

        NbtCompound nbt = stack.getOrCreateNbt();
        boolean isPlaying = nbt.getBoolean("IsPlaying");
        long pausedProgress = nbt.getLong("PausedProgress");
        int currentSlot = nbt.getInt("CurrentSlot");

        // === 1. 唱片在位检测 ===
        // 我们只检查当前选中的槽位，如果那个槽位的唱片被拿走了，必须立刻重置
        PortablePlayerInventory inv = new PortablePlayerInventory(stack);
        ItemStack disc = inv.getStack(currentSlot);

        boolean hasValidDisc = !disc.isEmpty() && (disc.getItem() instanceof CustomMusicDiscItem || disc.getItem() instanceof MusicDiscItem);

        if (!hasValidDisc) {
            // 只要没唱片，如果还在播放状态 OR 进度条不为0，就必须重置
            if (isPlaying || pausedProgress > 0) {
                // 停止播放状态
                nbt.putBoolean("IsPlaying", false);
                // 进度彻底归零
                nbt.putLong("PausedProgress", 0);
                nbt.putLong("PlayStartTime", 0);
                nbt.putString("CurrentTrackName", ""); // 清空歌名

                // 如果之前是在播放中被拿走的，发送停止包通知周围
                if (isPlaying) {
                    CpModNetworking.sendPlayControlEntity((ServerWorld) world, player.getId(), null, false, 0, 0);
                }
            }
        }

        // === 2. 心跳维持 (防丢弃检测) ===
        // 如果正在播放，就更新心跳。如果玩家把物品扔了，inventoryTick 停止运行，
        // 心跳超时，PortableMusicManager 会自动发送停止包。
        if (nbt.getBoolean("IsPlaying")) {
            PortableMusicManager.updateHeartbeat(player.getUuid());
        }
    }
}