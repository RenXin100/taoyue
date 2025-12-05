package com.renxin.registry;

import com.renxin.cpmod.CpModConstants;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * 自定义声音事件注册。
 * 目前只有一个 dynamic_disc，供所有自定义唱片的 OGG 播放使用。
 */
public class CpSounds {

    public static final Identifier DYNAMIC_DISC_ID =
            new Identifier(CpModConstants.MOD_ID, "dynamic_disc");

    public static final SoundEvent DYNAMIC_DISC = SoundEvent.of(DYNAMIC_DISC_ID);

    public static void init() {
        Registry.register(Registries.SOUND_EVENT, DYNAMIC_DISC_ID, DYNAMIC_DISC);
        CpModConstants.LOGGER.debug("CpSounds initialized");
    }
}
