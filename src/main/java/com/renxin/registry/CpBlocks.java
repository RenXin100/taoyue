package com.renxin.registry;

import com.renxin.block.entity.MusicBurnerBlock;
import com.renxin.block.entity.MusicPlayerBlock;
import com.renxin.cpmod.CpModConstants;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class CpBlocks {

    // === 修改点：使用 Blocks.PLANKS (木板) 作为属性基底 ===
    // 这样硬度适中，且不需要特殊工具就能掉落
    public static final Block MUSIC_BURNER = register("music_burner",
            new MusicBurnerBlock(
                    FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)
                            .strength(2.0f) // 显式设置硬度
                            .nonOpaque()    // 保持透明渲染
            ));

    public static final Block MUSIC_PLAYER = register("music_player",
            new MusicPlayerBlock(
                    FabricBlockSettings.copyOf(Blocks.OAK_PLANKS)
                            .strength(2.0f)
                            .nonOpaque()
            ));

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(CpModConstants.MOD_ID, name);
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void init() {
        CpModConstants.LOGGER.debug("CpBlocks initialized");
    }
}