package com.renxin.client.datagen;

import com.renxin.registry.CpBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;

public class CpModLootTableProvider extends FabricBlockLootTableProvider {
    public CpModLootTableProvider(FabricDataOutput dataOutput) {
        super(dataOutput);
    }

    @Override
    public void generate() {
        // 注册两个方块的掉落：破坏方块 -> 掉落方块本身
        addDrop(CpBlocks.MUSIC_BURNER);
        addDrop(CpBlocks.MUSIC_PLAYER);
    }
}