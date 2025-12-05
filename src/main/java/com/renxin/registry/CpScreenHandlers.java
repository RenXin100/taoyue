package com.renxin.registry;

import com.renxin.cpmod.CpModConstants;
import com.renxin.screen.MusicBurnerScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class CpScreenHandlers {

    public static final ScreenHandlerType<MusicBurnerScreenHandler> MUSIC_BURNER_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER,
                    new Identifier(CpModConstants.MOD_ID, "music_burner"),
                    new ExtendedScreenHandlerType<>(MusicBurnerScreenHandler::new));

    public static void init() {
        CpModConstants.LOGGER.debug("CpScreenHandlers initialized");
    }
    public static final ScreenHandlerType<com.renxin.screen.PortablePlayerScreenHandler> PORTABLE_PLAYER_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER,
                    new Identifier(CpModConstants.MOD_ID, "portable_player"),
                    new ExtendedScreenHandlerType<>(com.renxin.screen.PortablePlayerScreenHandler::new));
}
