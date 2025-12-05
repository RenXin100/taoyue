package com.renxin.audio;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public final class ServerTrackRegistry {

    private static final Logger LOGGER = LogManager.getLogger("cp-mod-tracks");

    private ServerTrackRegistry() {}

    /** 根目录：.minecraft/cp-mod/tracks */
    private static Path getTracksDir() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("cp-mod")
                .resolve("tracks");
    }

    /**
     * 注册并保存上传的音轨文件。
     *
     * 现在的落盘路径结构：
     *   cp-mod/tracks/<playerName>/<trackId>_<originalName(without ext)>.mp3
     *
     * @param player   上传者
     * @param fileName 上传时的原始文件名（带扩展名）
     * @param data     音频数据（你现在使用的是 mp3）
     */
    public static UUID registerUploadedTrack(ServerPlayerEntity player,
                                             String fileName,
                                             byte[] data) throws IOException {
        UUID trackId = UUID.randomUUID();

        // 根目录
        Path rootDir = getTracksDir();
        Files.createDirectories(rootDir);

        // 玩家子目录：使用玩家名简单做一个清洗，避免奇怪字符
        String rawPlayerName = player.getGameProfile().getName();
        String playerDirName = sanitizeFilePart(rawPlayerName);
        if (playerDirName.isEmpty()) {
            playerDirName = "unknown_player";
        }
        Path playerDir = rootDir.resolve(playerDirName);
        Files.createDirectories(playerDir);

        // 原始文件名（去掉扩展名），也做一下安全清洗
        String baseName = stripExtension(fileName);
        baseName = sanitizeFilePart(baseName);
        if (baseName.isEmpty()) {
            baseName = "track";
        }

        // 最终文件名：<uuid>_<原始名>.mp3
        String finalFileName = trackId.toString() + "_" + baseName + ".mp3";
        Path filePath = playerDir.resolve(finalFileName);

        Files.write(filePath, data);

        LOGGER.info("Saved uploaded track {} from {} (file='{}', size={} bytes) -> {}",
                trackId,
                rawPlayerName,
                fileName,
                data.length,
                filePath.toAbsolutePath());

        return trackId;
    }

    /**
     * 根据 trackId 找到对应文件路径。
     *
     * 新的命名规则下我们不知道玩家名 / 原始名，
     * 所以这里会在 cp-mod/tracks/ 下递归查找第一个
     * 文件名以 "<trackId>_" 开头并且以 ".mp3" 结尾的文件。
     *
     * 这样：
     *   - 接口保持不变：还是通过 UUID 查路径
     *   - 文件系统里又能按玩家+原始名管理
     */
    public static Path getTrackPath(UUID trackId) {
        Path rootDir = getTracksDir();
        if (!Files.exists(rootDir)) {
            return null;
        }

        String prefix = trackId.toString() + "_";

        try (Stream<Path> stream = Files.walk(rootDir, 2)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith(prefix.toLowerCase(Locale.ROOT))
                                && name.endsWith(".mp3");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            LOGGER.error("Failed to lookup track file for {}", trackId, e);
            return null;
        }
    }

    /** 去掉扩展名，只保留最后一个点之前的部分 */
    private static String stripExtension(String fileName) {
        if (fileName == null) return "";
        String name = fileName;
        // 防止带路径的情况，只保留最后一段
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    /**
     * 清洗文件/目录名中的非法字符：
     *  - 去掉路径分隔符
     *  - 把不可见或特殊字符替换成 '_'
     */
    private static String sanitizeFilePart(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // 不允许出现路径分隔符
            if (c == '/' || c == '\\' || c == ':' || c == '*'
                    || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                sb.append('_');
            } else if (Character.isISOControl(c)) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        // 可以视情况截断太长的名字避免极端情况
        String result = sb.toString().trim();
        int maxLen = 80;
        if (result.length() > maxLen) {
            result = result.substring(0, maxLen);
        }
        return result;
    }
}
