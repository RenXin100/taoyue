package com.renxin.client.audio;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTrackCache {

    // 内存中的下载缓冲区（下载中的数据暂存这里）
    private static final Map<UUID, ByteArrayOutputStream> DOWNLOADING = new ConcurrentHashMap<>();

    // 缓存目录: .minecraft/cp-cache/
    private static final Path CACHE_DIR = FabricLoader.getInstance().getGameDir().resolve("cp-cache");

    static {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始下载任务
     */
    public static void begin(UUID trackId, int expectedSize) {
        if (has(trackId)) return; // 再次确认，防止重复下载
        DOWNLOADING.put(trackId, new ByteArrayOutputStream(expectedSize > 0 ? expectedSize : 16 * 1024));
    }

    /**
     * 接收分片数据
     */
    public static void append(UUID trackId, byte[] data) {
        ByteArrayOutputStream buffer = DOWNLOADING.get(trackId);
        if (buffer != null) {
            try {
                buffer.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 下载完成，写入硬盘
     */
    public static void finish(UUID trackId) {
        ByteArrayOutputStream buffer = DOWNLOADING.remove(trackId);
        if (buffer != null) {
            Path filePath = getFilePath(trackId);
            try {
                Files.write(filePath, buffer.toByteArray());
                // debug("已缓存到硬盘: " + filePath);
            } catch (IOException e) {
                System.err.println("无法写入音频缓存: " + trackId);
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查本地是否已有缓存（查硬盘）
     */
    public static boolean has(UUID trackId) {
        if (trackId == null) return false;
        return Files.exists(getFilePath(trackId));
    }

    /**
     * 创建音频流（从硬盘读取）
     */
    public static AudioStream createStream(UUID trackId, int skipMs) throws IOException {
        Path path = getFilePath(trackId);
        if (!Files.exists(path)) {
            throw new IOException("Cache file not found: " + trackId);
        }

        // 包装成 BufferedInputStream 以提高读取性能
        InputStream fileIn = new BufferedInputStream(Files.newInputStream(path));

        try {
            return new Mp3AudioStream(fileIn, skipMs);
        } catch (Exception e) {
            MinecraftClient.getInstance().execute(() ->
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("§c[错误] MP3 解码失败"), false)
            );
            throw new IOException("Failed to decode MP3", e);
        }
    }

    private static Path getFilePath(UUID trackId) {
        return CACHE_DIR.resolve(trackId.toString() + ".mp3");
    }

    public static void clear() {
        DOWNLOADING.clear();
        // 通常不需要清理硬盘缓存，留着下次进服还能用
        // 如果需要清理，可以在这里遍历删除文件
    }
}