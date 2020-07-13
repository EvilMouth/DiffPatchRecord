package com.zyhang.diffpatchtest

import androidx.lifecycle.MutableLiveData
import com.github.sisong.ApkPatch
import com.google.archivepatcher.applier.FileByFileDeltaApplier
import com.google.archivepatcher.generator.FileByFileDeltaGenerator
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow
import com.google.archivepatcher.shared.PatchConstants
import com.zyhang.bsdiff.BSDiff
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Created by zyhang on 2020/6/22.17:55
 */
interface Differ {
    val name: String

    /**
     * @param args
     * 0: apkOld
     * 1: apkNew
     * 2: patch
     */
    fun diff(args: List<File>): Any

    /**
     * @param args
     * 0: apkOld
     * 1: patch
     * 2: apkFinal
     */
    fun patch(args: List<File>): Any

    companion object {
        val collect = listOf(
            Differ1(),
            Differ2(),
            Differ3()
        )

        val differ = MutableLiveData<Differ>()

        init {
            differ.value = collect.last()
            differ.observeForever {
                App.apkOld.value = null
                App.apkNew.value = null
                App.patch.value = null
                App.apkFinal.value = null
            }
        }
    }
}

class Differ1 : Differ {
    override val name: String
        get() = "bsdiff"

    override fun diff(args: List<File>): Any {
        return BSDiff.diff(args[0].absolutePath, args[1].absolutePath, args[2].absolutePath)
    }

    override fun patch(args: List<File>): Any {
        return BSDiff.patch(args[0].absolutePath, args[1].absolutePath, args[2].absolutePath)
    }
}

class Differ2 : Differ {

    init {
        println("archive-patcher compatible: " + DefaultDeflateCompatibilityWindow().isCompatible)
    }

    override val name: String
        get() = "archive-patcher"

    override fun diff(args: List<File>): Any {
        val compressor = Deflater(9, true)
        return try {
            val patchOut = FileOutputStream(args[2])
            val compressedPatchOut = DeflaterOutputStream(patchOut, compressor, 32768)
            FileByFileDeltaGenerator(
                emptyList(),
                setOf(PatchConstants.DeltaFormat.BSDIFF)
            ).generateDelta(
                args[0],
                args[1],
                compressedPatchOut
            )
            compressedPatchOut.finish()
            compressedPatchOut.flush()
            true
        } finally {
            compressor.end()
        }
    }

    override fun patch(args: List<File>): Any {
        val uncompressor = Inflater(true)
        return try {
            val compressedPatchIn = FileInputStream(args[1])
            val patchIn = InflaterInputStream(compressedPatchIn, uncompressor, 32768)
            FileByFileDeltaApplier().applyDelta(
                args[0],
                patchIn,
                FileOutputStream(args[2])
            )
            true
        } finally {
            uncompressor.end()
        }
    }
}

class Differ3 : Differ {
    override val name: String
        get() = "apkdiff"

    override fun diff(args: List<File>): Any {
        TODO("Not yet implemented")
    }

    override fun patch(args: List<File>): Any {
        return ApkPatch.patch(
            args[0].absolutePath,
            args[1].absolutePath,
            args[2].absolutePath,
            8388608,
            makeFile(name, "temp").absolutePath,
            1
        )
    }
}