package com.renxin.client.audio;

import com.renxin.registry.CpSounds;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EntityBoundDynamicTrackSoundInstance extends MovingSoundInstance implements FabricSoundInstance {

    private final Entity entity;
    private final UUID trackId;
    private final int startOffsetMs;

    public EntityBoundDynamicTrackSoundInstance(Entity entity, UUID trackId, int startOffsetMs, int range) {
        super(CpSounds.DYNAMIC_DISC, SoundCategory.RECORDS, net.minecraft.util.math.random.Random.create());
        this.entity = entity;
        this.trackId = trackId;
        this.startOffsetMs = startOffsetMs;

        // === 关键修改 ===
        // 移除 sounds.json 的硬编码后，Minecraft 默认的基础衰减距离是 16 格。
        // 所以我们用 目标距离 / 16.0 来计算所需的倍率。
        // 例如：想传 64 格 -> 64/16 = 4.0 倍音量
        // 想传 16 格 -> 16/16 = 1.0 倍音量
        this.volume = Math.max(0.1f, range / 16.0f); // 加个0.1保底防止除以0或静音

        this.pitch = 1.0F;
        this.repeat = false;
        this.attenuationType = AttenuationType.LINEAR;

        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (this.entity.isRemoved()) {
            this.setDone();
            return;
        }
        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();
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