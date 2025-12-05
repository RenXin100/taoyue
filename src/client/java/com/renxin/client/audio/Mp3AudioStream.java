package com.renxin.client.audio;

import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.text.Text;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Mp3AudioStream implements AudioStream {
    private final AudioInputStream stream;
    // 这里的 frame 是用来从解码器读原始数据的（可能是立体声）
    private final byte[] frame;
    // 是否需要手动将立体声混合为单声道
    private final boolean convertStereoToMono;

    public Mp3AudioStream(InputStream in, int skipMs) throws IOException {
        // log("§e[MP3] 初始化解码器 (混音版)...");

        InputStream bufferedIn = new MusicBufferedInputStream(in);
        skipID3(bufferedIn);

        try {
            MpegAudioFileReader mp3Reader = new MpegAudioFileReader();
            AudioInputStream originalStream = mp3Reader.getAudioInputStream(bufferedIn);
            AudioFormat originalFormat = originalStream.getFormat();

            int sourceChannels = originalFormat.getChannels();

            // === 策略调整 ===
            // 不强求解码器输出单声道，因为它可能会崩。
            // 我们让它输出和源文件一样的声道数 (通常是 2)。
            // 如果是立体声，我们在 read 阶段手动合成单声道。
            this.convertStereoToMono = (sourceChannels == 2);

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    originalFormat.getSampleRate(),
                    16,
                    sourceChannels, // 保持原声道数，保证解码稳定
                    sourceChannels * 2, // frame size = channels * 2 bytes (16bit)
                    originalFormat.getSampleRate(),
                    false
            );

            MpegFormatConversionProvider converter = new MpegFormatConversionProvider();
            if (!converter.isConversionSupported(targetFormat, originalFormat)) {
                throw new IOException("不支持的 MP3 格式转换: " + originalFormat);
            }

            this.stream = converter.getAudioInputStream(targetFormat, originalStream);
            this.frame = new byte[stream.getFormat().getFrameSize()];

            // 处理跳过 (skipMs)
            if (skipMs > 0) {
                long bytesPerSec = (long)(targetFormat.getSampleRate() * targetFormat.getFrameSize());
                long targetBytesToSkip = (bytesPerSec * skipMs) / 1000;

                // 字节对齐
                int frameSize = targetFormat.getFrameSize();
                if (frameSize > 0) {
                    long remainder = targetBytesToSkip % frameSize;
                    if (remainder != 0) {
                        targetBytesToSkip -= remainder;
                    }
                }

                long totalSkipped = 0;
                byte[] dummy = new byte[4096];
                while (totalSkipped < targetBytesToSkip) {
                    long remaining = targetBytesToSkip - totalSkipped;
                    int toRead = (int) Math.min(dummy.length, remaining);
                    int read = this.stream.read(dummy, 0, toRead);
                    if (read == -1) break;
                    totalSkipped += read;
                }
            }

        } catch (Exception e) {
            throw new IOException("MP3 init failed", e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        // 欺骗游戏引擎：告诉它我们提供的是单声道格式
        // 哪怕解码器吐出来的是立体声，我们也会在 getBuffer 里把它捏成单声道
        AudioFormat base = stream.getFormat();
        if (convertStereoToMono) {
            return new AudioFormat(
                    base.getEncoding(),
                    base.getSampleRate(),
                    16,
                    1, // 单声道
                    2, // 2 bytes per frame
                    base.getFrameRate(),
                    false
            );
        }
        return base;
    }

    @Override
    public ByteBuffer getBuffer(int size) throws IOException {
        ByteBuffer buffer = BufferUtils.createByteBuffer(size);

        // 如果需要转单声道，我们需要从流里读双倍的数据 (因为立体声是单声道的2倍大)
        // 比如游戏要 4096 字节单声道数据，我们得从 MP3 解码器读 8192 字节立体声数据

        int bytesNeededFromStream = convertStereoToMono ? size * 2 : size;
        // 这里的 buffer 只是个中转，不需要 DirectBuffer
        byte[] tempBuffer = new byte[bytesNeededFromStream];

        int totalRead = 0;
        while (totalRead < bytesNeededFromStream) {
            int read = stream.read(tempBuffer, totalRead, bytesNeededFromStream - totalRead);
            if (read == -1) break;
            totalRead += read;
        }

        if (convertStereoToMono) {
            // === 手动混音：立体声(LR) -> 单声道(M) ===
            // 16-bit Little Endian: [L_low, L_high, R_low, R_high]
            // 我们要把它变成: [Mix_low, Mix_high]
            // Mix = (L + R) / 2

            for (int i = 0; i < totalRead; i += 4) {
                // 确保不越界 (虽然后面 read 保证了对齐，但防一手)
                if (i + 3 >= totalRead) break;

                // 读左声道 (16位)
                int lLow = tempBuffer[i] & 0xFF;
                int lHigh = tempBuffer[i+1]; // 保持符号位
                short left = (short) ((lHigh << 8) | lLow);

                // 读右声道 (16位)
                int rLow = tempBuffer[i+2] & 0xFF;
                int rHigh = tempBuffer[i+3];
                short right = (short) ((rHigh << 8) | rLow);

                // 混合
                short mix = (short) ((left + right) / 2);

                // 写入 ByteBuffer (16位 Little Endian)
                buffer.put((byte) (mix & 0xFF));
                buffer.put((byte) ((mix >> 8) & 0xFF));
            }
        } else {
            // 不需要转换，直接塞进去
            buffer.put(tempBuffer, 0, totalRead);
        }

        buffer.flip();
        return buffer;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    private void log(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                if (client.player != null) client.player.sendMessage(Text.literal(msg), false);
            });
        }
    }

    private static void skipID3(InputStream inputStream) throws IOException {
        inputStream.mark(10);
        byte[] header = new byte[10];
        int read = inputStream.read(header, 0, 10);
        if (read < 10) {
            inputStream.reset();
            return;
        }
        if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            int size = (header[6] << 21) | (header[7] << 14) | (header[8] << 7) | header[9];
            int skipped = 0;
            int skip;
            do {
                skip = (int) inputStream.skip(size - skipped);
                if (skip > 0) skipped += skip;
            } while (skipped < size && skip > 0);
        } else {
            inputStream.reset();
        }
    }
}