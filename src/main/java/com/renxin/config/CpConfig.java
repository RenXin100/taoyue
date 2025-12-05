package com.renxin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CpConfig {

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("cp-mod.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 单例实例
    private static CpConfig INSTANCE;

    // === 配置项 ===
    public int broadcastRange = 64; // 默认 64 格

    // === 静态方法 ===

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, CpConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load cp-mod config, using defaults.");
                e.printStackTrace();
            }
        }

        if (INSTANCE == null) {
            INSTANCE = new CpConfig();
        }
    }

    public static void save() {
        if (INSTANCE == null) return;
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            System.err.println("Failed to save cp-mod config.");
            e.printStackTrace();
        }
    }

    public static CpConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }
}