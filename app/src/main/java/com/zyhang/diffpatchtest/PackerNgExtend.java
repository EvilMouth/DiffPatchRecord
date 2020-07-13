package com.zyhang.diffpatchtest;

import com.mcxiaoke.packer.helper.PackerNg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by zyhang on 2020/7/2.11:41
 */
class PackerNgExtend {

    public static void deleteMarket(File src, File dest) throws IOException {
        if (!src.exists()) {
            throw new RuntimeException("src file not exists");
        }
        long contentLength = getContentLength(src);
        if (contentLength < 0) {
            throw new PackerNg.MarketNotFoundException("Zip comment content not found");
        }
        FileInputStream in = new FileInputStream(src.getAbsolutePath());
        if (!dest.exists())
            //noinspection ResultOfMethodCallIgnored
            dest.createNewFile();
        FileOutputStream out = new FileOutputStream(dest);
        int c;
        long copyed = contentLength;
        byte[] buffer = new byte[1024];
        while ((c = in.read(buffer)) != -1) {
            if (copyed != c && c == buffer.length) {
                copyed = copyed - c;
                out.write(buffer, 0, c);
            } else {
                //还原源文件,需要把最后两个字节置为0  表示apk没有注释
                buffer[(int) (copyed - 1)] = 0;
                buffer[(int) (copyed - 2)] = 0;
                out.write(buffer, 0, (int) copyed);
            }
        }
        in.close();
        out.close();
    }

    static final String UTF_8 = "UTF-8";
    // ZIP文件注释长度字段和MAGIC的字节数
    static final int SHORT_LENGTH = 2;
    // 文件最后用于定位的MAGIC字节
    static final byte[] MAGIC = new byte[]{0x21, 0x5a, 0x58, 0x4b, 0x21}; //!ZXK!

    private static long getContentLength(File file) throws IOException {
        // 减去渠道信息长度
        String comment = PackerNg.Helper.readMarket(file);
        byte[] data = comment.getBytes(UTF_8);
        int length = data.length + SHORT_LENGTH + MAGIC.length;
        System.out.println(length);
        return file.length() - length;
    }
}
