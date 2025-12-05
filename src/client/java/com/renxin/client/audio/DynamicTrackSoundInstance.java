package com.renxin.client.audio;

import com.renxin.registry.CpSounds;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DynamicTrackSoundInstance extends AbstractSoundInstance implements FabricSoundInstance {

    private final UUID trackId;
    private final int startOffsetMs;

    public DynamicTrackSoundInstance(SoundEvent ignored, BlockPos pos, UUID trackId, int startOffsetMs, int range) {
        super(CpSounds.DYNAMIC_DISC, SoundCategory.RECORDS, SoundInstance.createRandom());
        this.trackId = trackId;
        this.startOffsetMs = startOffsetMs;

        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;

        // === 关键修改 ===
        // 使用标准基数 16.0 进行计算
        this.volume = Math.max(0.1f, range / 16.0f);

        this.pitch = 1.0F;
        this.repeat = false;
        this.attenuationType = AttenuationType.LINEAR;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ClientTrackCache.createStream(trackId, startOffsetMs);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }, Util.getMainWorkerExecutor());
    }
}