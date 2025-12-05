package com.renxin.network;

import com.renxin.audio.ServerTrackRegistry;
import com.renxin.item.BlankDiscItem;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.registry.CpItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkHandler {

    private static final Logger LOGGER = LogManager.getLogger("cp-mod-network");

    // === 修改点：10MB ===
    private static final int HARD_MAX_SIZE = 10 * 1024 * 1024;

    private NetworkHandler() {}

    private static class UploadSession {
        final UUID uploadId;
        final UUID playerUuid;
        final BlockPos burnerPos;
        final String fileName;
        final int totalSize;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        UploadSession(ServerPlayerEntity player,
                      BlockPos burnerPos,
                      UUID uploadId,
                      String fileName,
                      int totalSize) {
            this.uploadId = uploadId;
            this.playerUuid = player.getUuid();
            this.burnerPos = burnerPos.toImmutable();
            this.fileName = fileName;
            this.totalSize = totalSize;
        }
    }

    private static final Map<UUID, UploadSession> UPLOAD_SESSIONS = new ConcurrentHashMap<>();

    public static void handleUploadStart(ServerPlayerEntity player,
                                         BlockPos burnerPos,
                                         UUID uploadId,
                                         String fileName,
                                         int totalSize) {
        if (totalSize <= 0 || totalSize > HARD_MAX_SIZE) {
            LOGGER.warn("Reject upload {} from {}: invalid totalSize={}",
                    uploadId, player.getName().getString(), totalSize);
            return;
        }

        UploadSession session = new UploadSession(player, burnerPos, uploadId, fileName, totalSize);
        UPLOAD_SESSIONS.put(uploadId, session);

        LOGGER.info("Start upload {} from {} at {} (file='{}', size={} bytes)",
                uploadId, player.getName().getString(), burnerPos, fileName, totalSize);
    }

    public static void handleUploadChunk(ServerPlayerEntity player,
                                         UUID uploadId,
                                         int index,
                                         byte[] chunk) {
        UploadSession session = UPLOAD_SESSIONS.get(uploadId);
        if (session == null) return;

        if (!player.getUuid().equals(session.playerUuid)) return;

        try {
            session.buffer.write(chunk);
        } catch (IOException e) {
            UPLOAD_SESSIONS.remove(uploadId);
            return;
        }

        int current = session.buffer.size();
        if (current > session.totalSize || current > HARD_MAX_SIZE) {
            LOGGER.warn("Upload exceeded size limit, aborting");
            UPLOAD_SESSIONS.remove(uploadId);
        }
    }

    public static void handleUploadComplete(ServerPlayerEntity player,
                                            BlockPos burnerPos,
                                            UUID uploadId) {
        UploadSession session = UPLOAD_SESSIONS.remove(uploadId);
        if (session == null) return;

        if (!player.getUuid().equals(session.playerUuid)) return;

        byte[] data = session.buffer.toByteArray();
        int size = data.length;

        if (size <= 0 || size != session.totalSize) return;

        UUID trackId;
        try {
            trackId = ServerTrackRegistry.registerUploadedTrack(
                    player, session.fileName, data
            );
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(session.burnerPos);
        if (!(be instanceof Inventory inv)) return;

        int inputSlot = 0;
        int outputSlot = 1;

        ItemStack in = inv.getStack(inputSlot);
        if (in.isEmpty() || !(in.getItem() instanceof BlankDiscItem)) return;

        ItemStack result = new ItemStack(CpItems.CUSTOM_MUSIC_DISC);
        CustomMusicDiscItem.setTrack(result, trackId, session.fileName);

        in.decrement(1);
        inv.setStack(inputSlot, in);

        ItemStack out = inv.getStack(outputSlot);
        if (out.isEmpty()) {
            inv.setStack(outputSlot, result);
        } else {
            if (!player.getInventory().insertStack(result)) {
                player.dropItem(result, false);
            }
        }

        inv.markDirty();
        BlockState state = world.getBlockState(session.burnerPos);
        world.updateListeners(session.burnerPos, state, state, 3);
    }
}