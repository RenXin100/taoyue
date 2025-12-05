package com.renxin.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

/**
 * 一个依附于 ItemStack NBT 的临时库存。
 * 每次操作都会实时写回 NBT。
 */
public class PortablePlayerInventory implements Inventory {

    private final ItemStack stack;
    private final DefaultedList<ItemStack> items;
    public static final int SIZE = 9; // 我们给它设 9 个槽位，足够放一个专辑了

    public PortablePlayerInventory(ItemStack stack) {
        this.stack = stack;
        this.items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
        readNbt();
    }

    private void readNbt() {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (nbt.contains("Items")) {
            Inventories.readNbt(nbt, items);
        }
    }

    private void writeNbt() {
        NbtCompound nbt = stack.getOrCreateNbt();
        Inventories.writeNbt(nbt, items);
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public void markDirty() {
        // 关键：每次库存变动，都强制写回 ItemStack NBT
        writeNbt();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        // 简单校验：只要玩家手里还拿着这个物品，就能用
        // 如果玩家把物品扔了或者换手了，这里应该返回 false 防止刷物品
        // 这里做一个简单的手持校验
        return player.getMainHandStack() == stack || player.getOffHandStack() == stack;
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }
}