package com.renxin.client.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 照搬 NetMusic 的实现：
 * mp3 库会捕获所有 IOException, 连接超时的异常也被一同捕获,
 * 导致 read 方法陷入死循环。这里重写 read 方法，强制抛出 RuntimeException 打断死循环。
 */
public class MusicBufferedInputStream extends BufferedInputStream {
    public MusicBufferedInputStream(InputStream in) {
        super(in);
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        try {
            return super.read(b, off, len);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}