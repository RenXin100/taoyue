package com.renxin.screen;

import com.renxin.block.entity.MusicBurnerBlockEntity;
import com.renxin.registry.CpItems;
import com.renxin.registry.CpScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class MusicBurnerScreenHandler extends ScreenHandler {

    public static final int INPUT_SLOT_X = 60;
    public static final int INPUT_SLOT_Y = 28;
    public static final int OUTPUT_SLOT_X = 98;
    public static final int OUTPUT_SLOT_Y = 28;

    private final Inventory inventory;
    private final BlockPos pos;

    // 服务器端构造
    public MusicBurnerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos pos) {
        super(CpScreenHandlers.MUSIC_BURNER_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.pos = pos;

        checkSize(inventory, 2);
        inventory.onOpen(playerInventory.player);

        // 输入槽：左侧
        this.addSlot(new Slot(inventory, MusicBurnerBlockEntity.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(CpItems.BLANK_DISC);
            }
        });

        // 输出槽：右侧（不允许放入）
        this.addSlot(new Slot(inventory, MusicBurnerBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // 玩家背包，位置照抄熔炉
        addPlayerInventory(playerInventory, 8, 84);
    }

    // 客户端构造（从 buf 读 BlockPos）
    public MusicBurnerScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(2), buf.readBlockPos());
    }

    private void addPlayerInventory(PlayerInventory playerInventory, int x, int y) {
        // 主背包 3x9
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        x + col * 18, y + row * 18));
            }
        }
        // 热键栏
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col,
                    x + col * 18, y + 58));
        }
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public boolean hasBlankDisc() {
        ItemStack stack = inventory.getStack(MusicBurnerBlockEntity.SLOT_INPUT);
        return !stack.isEmpty() && stack.isOf(CpItems.BLANK_DISC);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            int containerSlots = 2;

            if (index < containerSlots) {
                // 从容器转移到玩家背包
                if (!this.insertItem(original, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包转移到输入槽（只接受空唱片）
                if (original.isOf(CpItems.BLANK_DISC)) {
                    if (!this.insertItem(original, MusicBurnerBlockEntity.SLOT_INPUT,
                            MusicBurnerBlockEntity.SLOT_INPUT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (original.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return newStack;
    }
}
