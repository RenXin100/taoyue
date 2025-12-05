package com.renxin.registry;

import com.renxin.block.entity.MusicBurnerBlockEntity;
import com.renxin.block.entity.MusicPlayerBlockEntity;
import com.renxin.cpmod.CpModConstants;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class CpBlockEntities {

    public static final BlockEntityType<MusicBurnerBlockEntity> MUSIC_BURNER_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(CpModConstants.MOD_ID, "music_burner"),
                    BlockEntityType.Builder.create(MusicBurnerBlockEntity::new, CpBlocks.MUSIC_BURNER).build(null));

    public static final BlockEntityType<MusicPlayerBlockEntity> MUSIC_PLAYER_ENTITY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(CpModConstants.MOD_ID, "music_player"),
                    BlockEntityType.Builder.create(MusicPlayerBlockEntity::new, CpBlocks.MUSIC_PLAYER).build(null));

    public static void init() {
        CpModConstants.LOGGER.debug("CpBlockEntities initialized");
    }
}
