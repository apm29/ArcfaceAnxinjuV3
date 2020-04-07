package com.apm29.anxinju

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apm29.anxinju.common.Constants
import com.arcsoft.face.ActiveFileInfo
import com.arcsoft.face.ErrorInfo
import com.arcsoft.face.FaceEngine
import com.bumptech.glide.Glide.init
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.ArrayList

/**
 *  author : ciih
 *  date : 2020/4/7 2:34 PM
 *  description :
 */
abstract class BaseActivity : AppCompatActivity() {


    val TAG: String = "MainActivity"

    // Demo 所需的动态库文件
    private val LIBRARIES = arrayOf( // 人脸相关
        "libarcsoft_face_engine.so",
        "libarcsoft_face.so",  // 图像库相关
        "libarcsoft_image_util.so"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissions = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 101)
        } else {
            init()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var isAllGranted = true
        for (grantResult in grantResults) {
            isAllGranted = isAllGranted and (grantResult == PackageManager.PERMISSION_GRANTED)
        }
        if (isAllGranted) {
            init()
        }
    }


    private fun init(){
        if (checkSoFile(LIBRARIES)) {
            Observable.create(ObservableOnSubscribe<Int?> { emitter ->
                val runtimeABI = FaceEngine.getRuntimeABI()
                Log.i(TAG, "subscribe: getRuntimeABI() $runtimeABI")
                val start = System.currentTimeMillis()
                val activeCode = FaceEngine.activeOnline(this@BaseActivity, Constants.APP_ID, Constants.SDK_KEY)
                Log.i(TAG, "subscribe cost: " + (System.currentTimeMillis() - start))
                emitter.onNext(activeCode)
            })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<Int?> {
                    override fun onSubscribe(d: Disposable) {}
                    override fun onNext(activeCode: Int) {
                        when (activeCode) {
                            ErrorInfo.MOK -> {
                                showToast("激活成功")
                                initSuccess()
                            }
                            ErrorInfo.MERR_ASF_ALREADY_ACTIVATED -> {
                                showToast("已激活")
                                initSuccess()
                            }
                            else -> {
                                showToast("激活失败:$activeCode")
                            }
                        }
                        val activeFileInfo = ActiveFileInfo()
                        val res = FaceEngine.getActiveFileInfo(this@BaseActivity, activeFileInfo)
                        if (res == ErrorInfo.MOK) {
                            Log.i(TAG, activeFileInfo.toString())
                        }
                    }

                    override fun onError(e: Throwable) {
                        showToast(e.message)
                    }

                    override fun onComplete() {}
                })
        }
    }

    open fun initSuccess(){

    }

    /**
     * 检查能否找到动态链接库，如果找不到，请修改工程配置
     *
     * @param libraries 需要的动态链接库
     * @return 动态库是否存在
     */
    private fun checkSoFile(libraries: Array<String>): Boolean {
        val dir = File(applicationInfo.nativeLibraryDir)
        val files = dir.listFiles()
        if (files == null || files.isEmpty()) {
            return false
        }
        val libraryNameList: MutableList<String> = ArrayList()
        for (file in files) {
            libraryNameList.add(file.name)
        }
        var exists = true
        for (library in libraries) {
            exists = exists and libraryNameList.contains(library)
        }
        return exists
    }

    protected fun showToast(s: String?) {
        Toast.makeText(applicationContext, s, Toast.LENGTH_SHORT).show()
    }

}