package com.zyhang.bsdiff;

/**
 * Created by zyhang on 2020/6/19.17:04
 */
public class BSDiff {

    public static native int diff(String oldApkPath, String newApkPath, String patchOutputPath);

    public static native int patch(String oldApkPath, String patchPath, String newApkOutputPath);

    static {
        System.loadLibrary("native-lib-bsdiff");
    }
}
