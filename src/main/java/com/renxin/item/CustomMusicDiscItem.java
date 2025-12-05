package com.renxin.item;

import com.renxin.registry.CpSounds;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.UUID;

public class CustomMusicDiscItem extends MusicDiscItem {

    private static final String NBT_TRACK_ID = "cp_mod_track_id";
    private static final String NBT_TRACK_NAME = "cp_mod_track_name";

    public CustomMusicDiscItem(Settings settings) {
        // 正式化：换回我们自己的 SoundEvent
        // 这样放入原版唱片机时，会播放 Harp（竖琴）声，而不是猫叫，也不会和原版唱片混淆
        super(15, CpSounds.DYNAMIC_DISC, settings, 180);
    }

    public static void setTrack(ItemStack stack, UUID trackId, String fileName) {
        if (stack.isEmpty() || trackId == null) return;
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putUuid(NBT_TRACK_ID, trackId);
        if (fileName != null) {
            nbt.putString(NBT_TRACK_NAME, fileName);
        }
    }

    public static UUID getTrackId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid(NBT_TRACK_ID)) return null;
        return nbt.getUuid(NBT_TRACK_ID);
    }

    public static String getTrackName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_TRACK_NAME)) return null;
        return nbt.getString(NBT_TRACK_NAME);
    }

    @Override
    public Text getName(ItemStack stack) {
        String trackName = getTrackName(stack);
        if (trackName != null && !trackName.isEmpty()) {
            return Text.translatable("item.cp-mod.custom_music_disc.named", trackName);
        }
        return Text.translatable("item.cp-mod.custom_music_disc");
    }
}