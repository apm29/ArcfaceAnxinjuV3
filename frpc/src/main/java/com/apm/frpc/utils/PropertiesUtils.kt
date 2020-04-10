package com.baidu.idl.sample.utils

import android.content.Context
import android.util.Log
import com.apm.frpc.utils.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*


class PropertiesUtils {

    private var mContext: Context? = null
    private var mPath: String? = null
    private var mFile: String? = null
    private val mProp: Properties by lazy {
        Properties()
    }

    fun setPath(path: String): PropertiesUtils {
        mPath = path
        return this
    }

    fun setFile(file: String): PropertiesUtils {
        mFile = file
        return this
    }

    fun init(): PropertiesUtils {
        Log.d(TAG, "path=$mPath/$mFile")
        try {
            val dir = File(mPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, mFile)
            if (!file.exists()) {
                file.createNewFile()
            }
            val inputStream = FileInputStream(file)
            mProp.load(inputStream)
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return this
    }

    fun commit() {
        try {
            val file = File("$mPath/$mFile")
            val os = FileOutputStream(file)
            mProp.store(os, "")
            os.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mProp.clear()
    }

    fun clear() {
        mProp.clear()
    }

    fun open() {
        mProp.clear()
        try {
            val file = File("$mPath/$mFile")
            val inputStream = FileInputStream(file)
            mProp.load(inputStream)
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun writeString(name: String, value: String) {
        mProp.setProperty(name, value)
    }

    fun readString(name: String, defaultValue: String): String {
        return mProp.getProperty(name, defaultValue)
    }

    fun writeInt(name: String, value: Int) {
        mProp.setProperty(name, "" + value)
    }

    fun readInt(name: String, defaultValue: Int): Int {
        return Integer.parseInt(mProp.getProperty(name, "" + defaultValue))
    }

    fun writeBoolean(name: String, value: Boolean) {
        mProp.setProperty(name, "" + value)
    }

    fun readBoolean(name: String, defaultValue: Boolean): Boolean {
        return java.lang.Boolean.parseBoolean(mProp.getProperty(name, "" + defaultValue))
    }

    fun writeDouble(name: String, value: Double) {
        mProp.setProperty(name, "" + value)
    }

    fun readDouble(name: String, defaultValue: Double): Double {
        return java.lang.Double.parseDouble(mProp.getProperty(name, "" + defaultValue))
    }

    companion object {
        private const val TAG = "PropertiesUtils"
        private val M_PROP_UTILS: PropertiesUtils by lazy {
            PropertiesUtils()
        }

        fun getInstance(context: Context): PropertiesUtils {
            M_PROP_UTILS.mContext = context
            M_PROP_UTILS.mPath = FileUtils.getBatchFaceDirectory().absolutePath
            M_PROP_UTILS.mFile = "properties.ini"
            return M_PROP_UTILS
        }
    }
}