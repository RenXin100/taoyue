package com.renxin.cpmod;

import com.renxin.audio.PortableMusicManager;
import com.renxin.command.CpCommands;
import com.renxin.config.CpConfig;
import com.renxin.registry.CpBlockEntities;
import com.renxin.registry.CpBlocks;
import com.renxin.registry.CpItems;
import com.renxin.registry.CpScreenHandlers;
import com.renxin.registry.CpSounds;
import com.renxin.cpmod.network.CpModNetworking;
import net.fabricmc.api.ModInitializer;

public class CpMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CpModConstants.LOGGER.info("Initializing {}", CpModConstants.MOD_NAME);
        CpConfig.load();
        CpBlocks.init();
        CpItems.init();
        CpBlockEntities.init();
        CpScreenHandlers.init();
        CpSounds.init();
        CpModNetworking.registerServerReceivers();
        // 注册指令
        CpCommands.register();
        // 注册心跳管理器
        PortableMusicManager.init();
        CpBlocks.init();
    }
}