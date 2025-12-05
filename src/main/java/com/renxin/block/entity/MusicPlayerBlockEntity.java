package com.renxin.block.entity;

import com.renxin.audio.ServerTrackRegistry;
import com.renxin.config.CpConfig;
import com.renxin.cpmod.network.CpModNetworking;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.registry.CpBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.UUID;

public class MusicPlayerBlockEntity extends BlockEntity implements Inventory {

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private boolean powered = false;
    private boolean playing = false;
    public boolean clientPlaying = false;
    private long startTime = 0;
    private long pausedProgress = 0;
    public long lastInteractTime = 0;

    public MusicPlayerBlockEntity(BlockPos pos, BlockState state) {
        super(CpBlockEntities.MUSIC_PLAYER_ENTITY, pos, state);
    }

    // Inventory 接口实现
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) { stopPlaybackAndReset(); markDirty(); }
        return result;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) { stopPlaybackAndReset(); markDirty(); }
        return result;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        stopPlaybackAndReset();
        markDirty();
    }
    @Override public void clear() { items.clear(); stopPlaybackAndReset(); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, items);
        powered = nbt.getBoolean("Powered");
        playing = nbt.getBoolean("Playing");
        pausedProgress = nbt.getLong("Progress");
        if (playing) startTime = System.currentTimeMillis();
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        nbt.putBoolean("Powered", powered);
        nbt.putBoolean("Playing", playing);
        nbt.putLong("Progress", getCurrentTotalProgress());
    }

    private long getCurrentTotalProgress() {
        if (playing) {
            return pausedProgress + (System.currentTimeMillis() - startTime);
        }
        return pausedProgress;
    }

    public void onRedstoneUpdate(ServerWorld world, boolean newPowered) {
        if (newPowered == this.powered) return;
        this.powered = newPowered;
        if (newPowered) pausePlayback(world);
        else if (!getStack(0).isEmpty()) startPlayback(world);
    }

    public void startPlayback(ServerWorld world) {
        ItemStack stack = getStack(0);
        if (stack.isEmpty() || playing) return;

        UUID trackId = CustomMusicDiscItem.getTrackId(stack);
        if (trackId == null) return;
        Path trackPath = ServerTrackRegistry.getTrackPath(trackId);
        if (trackPath == null || !trackPath.toFile().exists()) return;

        playing = true;
        startTime = System.currentTimeMillis();
        markDirty();

        int range = CpConfig.get().broadcastRange;
        CpModNetworking.sendPlayControl(world, pos, trackId, true, (int)pausedProgress, range);
    }

    public void pausePlayback(ServerWorld world) {
        if (!playing) return;
        pausedProgress += (System.currentTimeMillis() - startTime);
        playing = false;
        startTime = 0;
        markDirty();
        CpModNetworking.sendPlayControl(world, pos, null, false, 0, 0);
    }

    private void stopPlaybackAndReset() {
        playing = false;
        startTime = 0;
        pausedProgress = 0;
        if (world instanceof ServerWorld sw) {
            CpModNetworking.sendPlayControl(sw, pos, null, false, 0, 0);
        }
    }

    public void forceStop(ServerWorld world) {
        stopPlaybackAndReset();
    }

    // 客户端 Tick
    public static void clientTick(World world, BlockPos pos, BlockState state, MusicPlayerBlockEntity be) {
        if (be.clientPlaying) {
            if (world.getTime() % 20 == 0 || world.random.nextInt(50) == 0) {
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 1.2;
                double z = pos.getZ() + 0.5;
                double noteColor = world.random.nextInt(24) / 24.0;
                world.addParticle(ParticleTypes.NOTE, x, y, z, noteColor, 0.0, 0.0);
            }
        }
    }
}