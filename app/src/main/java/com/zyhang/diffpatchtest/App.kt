package com.zyhang.diffpatchtest

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.blankj.utilcode.util.EncryptUtils
import com.mcxiaoke.packer.helper.PackerNg
import java.io.File
import java.util.*

/**
 * Created by zyhang on 2020/6/22.10:17
 */
class App : Application() {

    companion object {
        lateinit var context: Context
        val apkOld = MutableLiveData<File>()
        val apkNew = MutableLiveData<File>()
        val patch = MutableLiveData<File>()
        val apkFinal = MutableLiveData<File>()

        val diffAvailable = MediatorLiveData<Boolean>().apply {
            addSource(apkOld) {
                value = it != null && apkNew.value != null
            }
            addSource(apkNew) {
                value = apkOld.value != null && it != null
            }
            value = false
        }
        val patchAvailable = MediatorLiveData<Boolean>().apply {
            addSource(apkOld) {
                value = it != null && patch.value != null
            }
            addSource(patch) {
                value = apkOld.value != null && it != null
            }
            value = false
        }
        val installAvailable = MediatorLiveData<Boolean>().apply {
            addSource(apkFinal) {
                value = it != null
            }
            value = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        context = this
    }
}

fun makeFile(type: String, name: String): File {
    return File(BuildConfig.ASSETS_DIR + "/$type", name)
}

fun timing(block: () -> Unit): Long {
    val start = System.currentTimeMillis()
    block.invoke()
    return System.currentTimeMillis() - start
}

fun String.trimApk() = replace(".apk", "")
fun String.trimPatch() = replace(".patch", "")

fun File.md5(): String {
    return EncryptUtils.encryptMD5File2String(this).toLowerCase(Locale.ROOT)
}

fun File.readNGChannel(): String? {
    return try {
        PackerNg.Helper.readMarket(this)
    } catch (e: Exception) {
        null
    }
}

fun File.removeNGChannel(): File {
    val removed = File(App.context.cacheDir, this.name.trimApk() + ".d.apk")
    PackerNgExtend.deleteMarket(this, removed)
    return removed
}

fun File.writeNGChannel(channel: String): File {
    PackerNg.Helper.writeMarket(this, channel)
    return this
}

fun Context.sourceDir(packageName: String): String? {
    return try {
        packageManager.getApplicationInfo(packageName, 0).sourceDir
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
