package com.renxin.cpmod;

import com.renxin.client.audio.MusicClientManager;
import com.renxin.client.screen.MusicBurnerScreen;
import com.renxin.client.screen.PortablePlayerScreen;
import com.renxin.cpmod.network.CpModClientNetworking;
import com.renxin.registry.CpScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
public class CpModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 把刻录机的 ScreenHandler 绑定到客户端 GUI
        HandledScreens.register(CpScreenHandlers.MUSIC_BURNER_SCREEN_HANDLER, MusicBurnerScreen::new);

        // 注册所有 S2C 客户端监听（包含后面播放广播音乐用到的包）
        CpModClientNetworking.registerClientReceivers();

        // 初始化客户端音乐管理器（解码、缓存、距离衰减等都可以从这里起）
        MusicClientManager.init();
        // 注册新的便携播放机界面
        HandledScreens.register(CpScreenHandlers.PORTABLE_PLAYER_SCREEN_HANDLER, PortablePlayerScreen::new);

        CpModClientNetworking.registerClientReceivers();
        MusicClientManager.init();
    }
}
