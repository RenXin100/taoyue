package com.renxin.screen;

import com.renxin.item.CustomMusicDiscItem;
import com.renxin.registry.CpScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class PortablePlayerScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final ItemStack playerStack;

    public PortablePlayerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, ItemStack stack) {
        super(CpScreenHandlers.PORTABLE_PLAYER_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.playerStack = stack;

        this.propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                NbtCompound nbt = playerStack.getOrCreateNbt();
                return switch (index) {
                    case 0 -> nbt.getBoolean("IsPlaying") ? 1 : 0;
                    case 1 -> nbt.getInt("PlayMode");
                    case 2 -> {
                        // === 修改点：进度计算 ===
                        long paused = nbt.getLong("PausedProgress");
                        if (!nbt.getBoolean("IsPlaying")) {
                            // 暂停状态：只显示保存的进度
                            yield (int) (paused / 1000);
                        } else {
                            // 播放状态：当前时间 - 开始时间 + 保存的进度
                            long start = nbt.getLong("PlayStartTime");
                            long now = System.currentTimeMillis();
                            yield (int) ((now - start + paused) / 1000);
                        }
                    }
                    case 3 -> {
                        long totalMs = nbt.getLong("TotalDuration");
                        yield totalMs == 0 ? 1 : (int) (totalMs / 1000);
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int size() { return 4; }
        };

        this.addProperties(propertyDelegate);

        // ... 后面的布局代码保持不变 ...
        inventory.onOpen(playerInventory.player);
        int gridX = 62; int gridY = 17;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(inventory, col + row * 3, gridX + col * 18, gridY + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return stack.getItem() instanceof MusicDiscItem || stack.getItem() instanceof CustomMusicDiscItem;
                    }
                });
            }
        }
        int playerInvY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
            }
        }
        int hotBarY = 142;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, hotBarY));
        }
    }

    public PortablePlayerScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        super(CpScreenHandlers.PORTABLE_PLAYER_SCREEN_HANDLER, syncId);
        this.inventory = new SimpleInventory(9);
        this.playerStack = ItemStack.EMPTY;
        this.propertyDelegate = new net.minecraft.screen.ArrayPropertyDelegate(4);
        this.addProperties(propertyDelegate);

        // 客户端槽位布局必须完全一致
        int gridX = 62; int gridY = 17;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) this.addSlot(new Slot(inventory, col + row * 3, gridX + col * 18, gridY + row * 18));
        }
        int playerInvY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
        }
        int hotBarY = 142;
        for (int col = 0; col < 9; ++col) this.addSlot(new Slot(playerInventory, col, 8 + col * 18, hotBarY));
    }

    @Override public boolean canUse(PlayerEntity player) { return inventory.canPlayerUse(player); }
    public boolean isPlaying() { return propertyDelegate.get(0) == 1; }
    public int getPlayMode() { return propertyDelegate.get(1); }
    public float getProgress() {
        int current = propertyDelegate.get(2);
        int total = propertyDelegate.get(3);
        if (total == 0) return 0;
        return Math.min(1.0f, (float) current / total);
    }

    // ... quickMove 保持不变 ...
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            if (index < 9) {
                if (!this.insertItem(original, 9, 45, true)) return ItemStack.EMPTY;
            } else {
                if (original.getItem() instanceof MusicDiscItem || original.getItem() instanceof CustomMusicDiscItem) {
                    if (!this.insertItem(original, 0, 9, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}