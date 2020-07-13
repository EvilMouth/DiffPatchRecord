package com.zyhang.diffpatchtest

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import com.blankj.utilcode.util.EncryptUtils
import com.leon.lfilepickerlibrary.LFilePicker
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val loading by lazy(LazyThreadSafetyMode.NONE) {
        AlertDialog.Builder(this)
            .setView(ProgressBar(this))
            .setCancelable(false)
            .create()
    }
    private var backupChannel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.decorView.keepScreenOn = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                100
            )
        }

        println("fuck: " + applicationInfo.sourceDir)

        choose_differ.setOnClickListener {
            AlertDialog.Builder(this)
                .setSingleChoiceItems(
                    Differ.collect.map { it.name }.toTypedArray(),
                    Differ.collect.indexOf(Differ.differ.value!!)
                ) { dialog, which ->
                    run {
                        dialog.dismiss()
                        Differ.differ.value = Differ.collect[which]
                    }
                }
                .show()
        }
        Differ.differ.observe(this, androidx.lifecycle.Observer {
            choose_differ_tips.text = it.name
            // reset
            start_diff_tips.text = null
            start_patch_tips.text = null
        })

        choose_old_apk.setOnClickListener {
            LFilePicker()
                .withActivity(this)
                .withRequestCode(100)
                .withStartPath(BuildConfig.ASSETS_DIR + "/apk")
                .withMaxNum(1)
                .withFileFilter(arrayOf(".apk"))
                .start()
        }
        App.apkOld.observe(this, androidx.lifecycle.Observer {
            choose_old_apk_tips.text = it?.name
        })

        choose_new_apk.setOnClickListener {
            LFilePicker()
                .withActivity(this)
                .withRequestCode(101)
                .withStartPath(BuildConfig.ASSETS_DIR + "/apk")
                .withMaxNum(1)
                .withFileFilter(arrayOf(".apk"))
                .start()
        }
        App.apkNew.observe(this, androidx.lifecycle.Observer {
            choose_new_apk_tips.text = it?.name
        })

        choose_patch.setOnClickListener {
            LFilePicker()
                .withActivity(this)
                .withRequestCode(102)
                .withStartPath(BuildConfig.ASSETS_DIR + "/${Differ.differ.value!!.name}")
                .withMaxNum(1)
                .withFileFilter(arrayOf(".patch"))
                .start()
        }
        App.patch.observe(this, androidx.lifecycle.Observer {
            choose_patch_tips.text = it?.name
        })

        choose_final_apk.setOnClickListener {
            LFilePicker()
                .withActivity(this)
                .withRequestCode(103)
                .withStartPath(BuildConfig.ASSETS_DIR + "/${Differ.differ.value!!.name}")
                .withMaxNum(1)
                .withFileFilter(arrayOf(".apk"))
                .start()
        }
        App.apkFinal.observe(this, androidx.lifecycle.Observer {
            choose_final_apk_tips.text = it?.name
        })

        start_diff.setOnClickListener {
            loading.show()
            App.patch.value = null
            thread {
                val old = App.apkOld.value!!
                val new = App.apkNew.value!!
                val patch =
                    makeFile(
                        Differ.differ.value!!.name,
                        "(${old.name.trimApk()}-${new.name.trimApk()}).patch"
                    )
                val cost = timing {
                    Differ.differ.value!!.diff(listOf(old, new, patch))
                }
                runOnUiThread {
                    loading.dismiss()
                    App.patch.value = patch
                    start_diff_tips.text = "cost $cost ms"
                }
            }
        }
        App.diffAvailable.observe(this, androidx.lifecycle.Observer {
            start_diff.isEnabled = true == it
        })

        start_patch.setOnClickListener {
            loading.show()
            App.apkFinal.value = null
            thread {
                var old = App.apkOld.value!!

                val channel = old.readNGChannel()
                if (null != channel) {
                    // 移除ng-plugin渠道信息，因为会影响patch
                    old = old.removeNGChannel()
                    println("移除渠道后的md5: ${old.md5()}")
                }

                val patch = App.patch.value!!
                var final = makeFile(
                    Differ.differ.value!!.name,
                    "(${old.name.trimApk()}-${patch.name}).apk"
                )
                val cost = timing {
                    Differ.differ.value!!.patch(listOf(old, patch, final))
                }

                if (backupChannel && null != channel) {
                    // 复原渠道信息
                    println("插入渠道前的md5：${final.md5()}")
                    final = final.writeNGChannel(channel)
                }

                runOnUiThread {
                    loading.dismiss()
                    App.apkFinal.value = final
                    start_patch_tips.text = "cost $cost ms"
                }
            }
        }
        App.patchAvailable.observe(this, androidx.lifecycle.Observer {
            start_patch.isEnabled = true == it
        })

        start_install.setOnClickListener {
            startActivity(Intent().apply {
                action = Intent.ACTION_VIEW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            BuildConfig.APPLICATION_ID + ".provider",
                            App.apkFinal.value!!
                        ), "application/vnd.android.package-archive"
                    )
                } else {
                    setDataAndType(
                        Uri.fromFile(App.apkFinal.value!!),
                        "application/vnd.android.package-archive"
                    )
                }
            })
        }
        App.installAvailable.observe(this, androidx.lifecycle.Observer {
            start_install.isEnabled = true == it
        })

        // md5 calculate
        val md5Observer = Observer<Any> {
            md5_tips.text = StringBuilder().apply {
                App.apkOld.value?.let {
                    append(
                        "旧包：md5: ${it.md5()}" +
                                "\n channel: ${it.readNGChannel()}"
                    )
                    append("\n")
                }
                App.apkNew.value?.let {
                    append(
                        "新包：md5: ${it.md5()}" +
                                "\n channel: ${it.readNGChannel()}"
                    )
                    append("\n")
                }
                App.patch.value?.let {
                    append(
                        "差分包：md5: ${it.md5()}"
                    )
                    append("\n")
                }
                App.apkFinal.value?.let {
                    append(
                        "最终包：md5: ${it.md5()}" +
                                "\n channel: ${it.readNGChannel()}"
                    )
                }
            }
        }
        App.apkOld.observe(this, md5Observer)
        App.apkNew.observe(this, md5Observer)
        App.patch.observe(this, md5Observer)
        App.apkFinal.observe(this, md5Observer)

        check_backup_channel.setOnCheckedChangeListener { _, isChecked ->
            backupChannel = isChecked
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                100 -> {
                    val path = data.getStringArrayListExtra("paths")!![0]
                    App.apkOld.value = File(path)
                }
                101 -> {
                    val path = data.getStringArrayListExtra("paths")!![0]
                    App.apkNew.value = File(path)
                }
                102 -> {
                    val path = data.getStringArrayListExtra("paths")!![0]
                    App.patch.value = File(path)
                }
                103 -> {
                    val path = data.getStringArrayListExtra("paths")!![0]
                    App.apkFinal.value = File(path)
                }
            }
        }
    }
}
