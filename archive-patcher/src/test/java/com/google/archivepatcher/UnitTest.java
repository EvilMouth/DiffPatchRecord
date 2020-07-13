package com.google.archivepatcher;

import com.google.archivepatcher.generator.FileByFileDeltaGenerator;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.PatchConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Created by zyhang on 2020/6/28.11:10
 */
public class UnitTest {

    private static final String apkDir = "app/assets/apk/";
    private static final String patchDir = "app/assets/archive-patcher/";

    private static final String[] apks = new String[]{"3.9.zlib", "3.9.1.zlib", "4.0.zlib", "4.1.zlib", "4.2.zlib", "4.3.zlib", "4.3.1.zlib"};

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < apks.length; i++) {
            for (int j = i + 1; j < apks.length; j++) {
                String apkOld = apks[i];
                String apkNew = apks[j];
                String patch = String.format("(%s-%s)", apkOld, apkNew);
                diff(new String[]{
                        apkDir + apkOld + ".apk",
                        apkDir + apkNew + ".apk",
                        patchDir + patch + ".patch"
                });
                System.out.println(patchDir + patch + ".patch" + " is generate");
            }
        }
    }

    public static void diff(String[] args) throws Exception {
//        if (!new DefaultDeflateCompatibilityWindow().isCompatible()) {
//            System.err.println("zlib not compatible on this system");
//            System.exit(-1);
//        }
        File oldFile = new File(args[0]); // must be a zip archive
        File newFile = new File(args[1]); // must be a zip archive
        Deflater compressor = new Deflater(/* level= */ 9, /* nowrap= */ true); // to compress the patch
        try (FileOutputStream patchOut = new FileOutputStream(args[2]);
             DeflaterOutputStream compressedPatchOut =
                     new DeflaterOutputStream(patchOut, compressor, /* size= */ 32768)) {
            new FileByFileDeltaGenerator(
                    /* preDiffPlanEntryModifiers= */ Collections.emptyList(),
                    Collections.singleton(PatchConstants.DeltaFormat.BSDIFF))
                    .generateDelta(oldFile, newFile, compressedPatchOut);
            compressedPatchOut.finish();
            compressedPatchOut.flush();
        } finally {
            compressor.end();
        }
    }
}
