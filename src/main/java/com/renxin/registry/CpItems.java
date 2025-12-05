package com.renxin.registry;

import com.renxin.cpmod.CpModConstants;
import com.renxin.item.BlankDiscItem;
import com.renxin.item.CustomMusicDiscItem;
import com.renxin.item.PortableAudioPlayerItem; // 导入新类
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class CpItems {

    public static final Item BLANK_DISC = register("blank_disc",
            new BlankDiscItem(new FabricItemSettings().maxCount(16)));
    public static final Item CUSTOM_MUSIC_DISC = register("custom_music_disc",
            new CustomMusicDiscItem(new FabricItemSettings().maxCount(1)));

    // 原有的方块物品保持不变
    public static final Item MUSIC_BURNER = register("music_burner",
            new BlockItem(CpBlocks.MUSIC_BURNER, new FabricItemSettings()));
    public static final Item MUSIC_PLAYER = register("music_player",
            new BlockItem(CpBlocks.MUSIC_PLAYER, new FabricItemSettings()));

    // === 新增：便携式播放机 ===
    // 设置 maxCount(1) 确保它不可堆叠
    public static final Item PORTABLE_MUSIC_PLAYER = register("portable_music_player",
            new PortableAudioPlayerItem(new FabricItemSettings().maxCount(1)));

    private static Item register(String name, Item item) {
        Identifier id = new Identifier(CpModConstants.MOD_ID, name);
        return Registry.register(Registries.ITEM, id, item);
    }

    public static void init() {
        CpModConstants.LOGGER.debug("CpItems initialized");

        // 添加到红石物品栏 (和你的机器放在一起)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(MUSIC_BURNER);
            entries.add(MUSIC_PLAYER);
        });

        // 添加到工具物品栏 (因为它是个手持工具)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(BLANK_DISC);
            entries.add(CUSTOM_MUSIC_DISC);
            entries.add(PORTABLE_MUSIC_PLAYER); // 添加在这里
        });
    }
}