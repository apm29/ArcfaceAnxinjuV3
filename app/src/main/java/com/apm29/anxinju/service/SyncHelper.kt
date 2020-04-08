package com.apm29.anxinju.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import com.apm.data.api.ApiKt
import com.apm.data.db.FaceDBManager
import com.apm.data.model.FaceModel
import com.apm.data.model.RetrofitManager
import com.apm.data.persistence.PropertiesUtils
import com.apm29.anxinju.db.RoomDateBase
import com.apm29.anxinju.faceserver.FaceServer
import com.apm29.anxinju.model.ArcUser
import com.apm29.anxinju.service.FaceDataSyncService.Companion.ACTION_NOTIFY_REGISTER
import com.apm29.anxinju.service.FaceDataSyncService.Companion.KEY_NOTIFY_REGISTER_MODEL
import com.apm29.anxinju.service.FaceDataSyncService.Companion.KEY_NOTIFY_REGISTER_SUCCESS
import com.arcsoft.face.FaceFeature
import com.arcsoft.face.util.ImageUtils
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext

/**
 *  author : ciih
 *  date : 2019-10-09 10:22
 *  description :
 */
object SyncHelper : CoroutineScope {


    private val coroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private var loopAsSync = true

    @Volatile
    private var inLoop = false
    private const val TAG = "SyncHelper"
    private const val GROUP_NAME = "0"
    private val failedIds = HashSet<Int>()
    private val failedIdWithCount = HashMap<Int, Int>()

    override val coroutineContext: CoroutineContext
        get() = coroutineDispatcher

    fun startSync(context: Context, deviceId: String, onFinished: () -> Unit) =
        launch {
            loopAsSync = true
            Log.d(TAG, "开始同步")
            val retrofitManager = RetrofitManager.getInstance()
            val apiKt = retrofitManager.retrofit.create(ApiKt::class.java)
            val prop = PropertiesUtils.getInstance()
            val syncDao = FaceDBManager.getInstance(context).getSyncDao()
            if (inLoop) {
                Log.e(TAG, "已在同步")
                return@launch
            }
            while (loopAsSync) {
                Log.d(TAG, "等待：${simpleDateFormat.format(Date())}")
                Log.d(TAG, "获取人脸库大小。。。")
                val userDao = RoomDateBase.getInstance(context).getUserDao()
                val userList = userDao.getUsers()
                Log.d(TAG, "人脸库大小：${userList?.size}")
                try {
                    inLoop = true
                    delay(30000)
                    prop.init()
                    prop.open()
                    val lastSyncTime = syncDao.getFaceLastSyncTime()
                    val doorCurrentTime = System.currentTimeMillis()
                    Log.d(
                        TAG,
                        "同步开始：${simpleDateFormat.format(Date())}  last sync time:${simpleDateFormat.format(
                            Date(lastSyncTime)
                        )}"
                    )
                    val syncResp =
                        apiKt.getNotSync(
                            lastSyncTime = simpleDateFormat.format(Date(lastSyncTime)),
                            doorCurrentTime = simpleDateFormat.format(Date(doorCurrentTime)),
                            deviceId = deviceId
                        )

                    if (syncResp.success()) {
                        Log.d(TAG, "数据获取：${syncResp.data.size}")
                        syncResp.data.forEach { faceModel: FaceModel ->
                            var success = false
                            try {
                                Log.d(TAG, "人脸信息:$faceModel")
                                if (faceModel.delete()) {
                                    //删除用户
                                    val user2Delete = userDao.getUser(
                                        faceModel.id
                                    )
                                    if (user2Delete != null) {
                                        userDao
                                            .userDelete(user2Delete.userId)
                                        Log.d(TAG, "删除user:${user2Delete.userId}")
                                        success = true
                                    } else {
                                        Log.d(TAG, "无需删除user:${faceModel.id}")
                                        success = true
                                    }
                                } else {
                                    Log.d(TAG, "新增用户:${faceModel.id} ${faceModel.personPic}")
                                    //新增用户
                                    val picUrl = faceModel.absolutePicUrl(
                                        prop.readString(
                                            "fileBaseUrl",
                                            "http://axj.ciih.net/"
                                        )
                                    )
                                    val batchImageDir = getImportDirectory()
                                    val imageName = "origin_${faceModel.id}_id.jpg"
                                    val imageFile = File(batchImageDir, imageName)
                                    val responseBody = withContext(coroutineDispatcher) {
                                        apiKt.downloadFile(picUrl)
                                    }
                                    writeResponseBodyToDisk(responseBody, imageFile)

                                    success =
                                        registerByFile(context, imageFile, faceModel, imageName)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "注册异常")
                                e.printStackTrace()
                            } finally {
                                if (!success) {
                                    failedIdWithCount[faceModel.id] =
                                        (failedIdWithCount.getOrElse(faceModel.id) { 0 } + 1)
                                    if (failedIdWithCount[faceModel.id] ?: 0 <= 3) {
                                        failedIds.add(faceModel.id)
                                    }
                                }
                                sendRegisterSuccess(context, faceModel, success)
                            }

                        }
                        syncDao.resetFaceLastSyncTime(System.currentTimeMillis() - 30_000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "同步异常")
                    e.printStackTrace()
                } finally {
                    try {
                        if (failedIds.isNotEmpty()) {
                            val copy = HashSet(failedIds)
                            val unregisterIds = copy.joinToString(",")
                            apiKt.addUnRegisterIds(
                                unregisterIds,
                                deviceId
                            )
                            Log.d(TAG, "上报未完成id：$unregisterIds")
                        }
                    } finally {
                        failedIds.clear()
                    }
                }
            }
            inLoop = false
            Log.d(TAG, "同步循环退出")
            onFinished()
        }

    private fun getImportDirectory(): File {
        val sdRootFile = Environment.getExternalStorageDirectory()
        val batchImageDir = File(sdRootFile, "Arc-Face-Import")
        if (sdRootFile.exists()) {
            if (!batchImageDir.exists()) {
                batchImageDir.mkdirs()
            }
        }
        return batchImageDir
    }

    private fun getSuccessDirectory(): File {
        val sdRootFile = Environment.getExternalStorageDirectory()
        val batchImageDir = File(sdRootFile, "Arc-Face-Success")
        if (sdRootFile.exists()) {
            if (!batchImageDir.exists()) {
                batchImageDir.mkdirs()
            }
        }
        return batchImageDir
    }

    /**
     * 保存图片
     */
    private fun saveBitmap(file: File, bitmap: Bitmap): Boolean {
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            return true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            try {
                out?.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    @Throws(Exception::class)
    fun registerByFile(
        context: Context,
        imageFile: File,
        faceModel: FaceModel,
        imageName: String
    ): Boolean {
        var success = false
        var faceBitmap = BitmapFactory.decodeFile(imageFile.path)
        if (faceBitmap != null) {
            val faceFeature = FaceFeature()
            // 走人脸SDK接口，通过人脸检测、特征提取拿到人脸特征值
            faceBitmap = ImageUtils.alignBitmapForBgr24(faceBitmap)
            val bgr24 = ImageUtils.bitmapToBgr24(faceBitmap)
            val ret = FaceServer.getInstance().extractFromBgr24(
                context,
                bgr24,
                faceBitmap.width,
                faceBitmap.height,
                faceFeature
            )
            if (!ret) {
                Log.e(
                    TAG,
                    faceModel.id.toString() + "未检测到人脸，可能原因：人脸太小或角度不正确"
                )
            } else {
                val userDao = RoomDateBase.getInstance(context).getUserDao()
                val user = userDao
                    .getUser(faceModel.id)
                val importDBSuccess: Boolean
                if (user != null) {
                    importDBSuccess =
                        userDao.updateUser(
                            ArcUser(
                                id = faceModel.id,
                                userId = faceModel.personId,
                                userName = faceModel.uid,
                                userInfo = faceModel.personPic,
                                feature = faceFeature.featureData
                            )
                        ) != 0
                } else {
                    // 将用户信息和用户组信息保存到数据库
                    userDao.addUser(
                        ArcUser(
                            id = faceModel.id,
                            userId = faceModel.personId,
                            userName = faceModel.uid,
                            userInfo = faceModel.personPic,
                            feature = faceFeature.featureData
                        )
                    )
                    importDBSuccess = true
                }

                // 保存数据库成功
                if (importDBSuccess) {
                    success = true
                    // 保存图片到新目录中
                    val facePicDir =
                        getSuccessDirectory()
                    val savePicPath =
                        File(facePicDir, imageName)
                    if (saveBitmap(
                            savePicPath,
                            faceBitmap
                        )
                    ) {
                        Log.i(TAG, "图片保存成功")
                    } else {
                        Log.i(TAG, "图片保存失败")
                    }

                } else {
                    Log.e(TAG, "$imageName：保存到数据库失败")
                }
            }

        } else {
            Log.e(
                TAG,
                "解析图片为空：${faceModel.id}  ${faceModel.personPic}"
            )
        }
        return success
    }

    private fun sendRegisterSuccess(context: Context, faceModel: FaceModel, success: Boolean) {
        val intent = Intent(ACTION_NOTIFY_REGISTER)
        intent.putExtra(KEY_NOTIFY_REGISTER_MODEL, faceModel)
        intent.putExtra(KEY_NOTIFY_REGISTER_SUCCESS, success)
        context.sendBroadcast(intent)
    }

    private fun writeResponseBodyToDisk(body: ResponseBody, file: File) {
        try {
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                val fileReader = ByteArray(4096)
                inputStream = body.byteStream()
                outputStream = FileOutputStream(file)
                while (true) {
                    val read = inputStream.read(fileReader)
                    if (read == -1) {
                        break
                    }
                    outputStream.write(fileReader, 0, read)
                }
                outputStream.flush()
            } catch (e: IOException) {
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        } catch (e: IOException) {
            Log.e(TAG, "response 写入磁盘失败")
            e.printStackTrace()
        }

    }

    fun stopSync() {
        loopAsSync = false
    }
}