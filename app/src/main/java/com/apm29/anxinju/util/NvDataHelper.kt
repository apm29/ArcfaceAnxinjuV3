package com.apm29.anxinju.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.Camera
import android.os.Environment
import android.util.Log
import com.arcsoft.face.FaceEngine
import com.arcsoft.imageutil.ArcSoftImageFormat
import com.arcsoft.imageutil.ArcSoftImageUtil
import com.arcsoft.imageutil.ArcSoftImageUtilError
import com.arcsoft.imageutil.ArcSoftRotateDegree
import kotlinx.coroutines.*
import okhttp3.internal.wait
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 *  author : ciih
 *  date : 2020/4/13 9:10 AM
 *  description :
 */
object NvDataHelper : CoroutineScope{

    override val coroutineContext:CoroutineContext
        get() = Dispatchers.Default
    private var lastJob :Deferred<File?>? = null

    private var nv21:ByteArray = ByteArray(size = 0)
    private var previewSize: Camera.Size? = null

    suspend fun getFrameImageFile(width: Int = previewSize?.width?:640,height: Int = previewSize?.height?:480 ,file: File? = null): File? {
            if(nv21.isEmpty()){
                println("图片ByteArray为空")
                return null
            }
            if (lastJob!=null && lastJob?.isCompleted == false){
                return lastJob?.await()
            }
            lastJob = coroutineScope {
                async {
                    saveImageFile(file,nv21, width, height)
                }
            }
            return lastJob?.await()
    }

    fun saveNv21Data(nv21: ByteArray) {
        if(lastJob!=null && lastJob?.isCompleted == false){
            Log.e("NvDataHelper","Skip Frame : ${System.currentTimeMillis()}")
            return
        }
        this.nv21 = nv21
    }

    private fun getTempDirectory(): File {
        val sdRootFile = Environment.getExternalStorageDirectory()
        val batchImageDir = File(sdRootFile, "Arc-Face-Temp")
        if (sdRootFile.exists()) {
            if (!batchImageDir.exists()) {
                batchImageDir.mkdirs()
            }
        }
        return batchImageDir
    }

    private fun saveImageFile(file: File?, nv21: ByteArray?, width: Int, height: Int): File? {
        if (nv21 == null) {
            return null
        }
        Log.e("NvDataHelper",Thread.currentThread().toString())
        val bitmap = getCropImage(nv21, width, height, FaceEngine.ASF_OC_90) ?: return null
        var fileOutputStream: FileOutputStream? = null
        val directory =
            getTempDirectory()
        val image: File
        image = file ?: File(directory, "img_captured_${System.currentTimeMillis()}.jpg")
        try {

            fileOutputStream = FileOutputStream(image)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            fileOutputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }
        return image
    }


    /**
     * 截取合适的头像并旋转
     *
     * @param originImageData 原始的BGR24数据
     * @param width           BGR24图像宽度
     * @param height          BGR24图像高度
     * @param orient          人脸角度
     * @param cropRect        裁剪的位置
     * @param imageFormat     图像格式
     * @return 头像的图像数据
     */
    private fun getCropImage(
        originImageData: ByteArray,
        width: Int,
        height: Int,
        orient: Int,
        cropRect: Rect = Rect(0, 0, width, height)
    ): Bitmap? {
        val headImageData =
            ArcSoftImageUtil.createImageData(
                cropRect.width(),
                cropRect.height(),
                ArcSoftImageFormat.NV21
            )
        val cropCode = ArcSoftImageUtil.cropImage(
            originImageData,
            headImageData,
            width,
            height,
            cropRect,
            ArcSoftImageFormat.NV21
        )
        if (cropCode != ArcSoftImageUtilError.CODE_SUCCESS) {
            throw RuntimeException("crop image failed, code is $cropCode")
        }

        //判断人脸旋转角度，若不为0度则旋转注册图
        var rotateHeadImageData: ByteArray? = null
        val rotateCode: Int
        val cropImageWidth: Int
        val cropImageHeight: Int
        // 90度或270度的情况，需要宽高互换
        if (orient == FaceEngine.ASF_OC_90 || orient == FaceEngine.ASF_OC_270) {
            cropImageWidth = cropRect.height()
            cropImageHeight = cropRect.width()
        } else {
            cropImageWidth = cropRect.width()
            cropImageHeight = cropRect.height()
        }
        var rotateDegree: ArcSoftRotateDegree? = null
        when (orient) {
            FaceEngine.ASF_OC_90 -> rotateDegree = ArcSoftRotateDegree.DEGREE_270
            FaceEngine.ASF_OC_180 -> rotateDegree = ArcSoftRotateDegree.DEGREE_180
            FaceEngine.ASF_OC_270 -> rotateDegree = ArcSoftRotateDegree.DEGREE_90
            FaceEngine.ASF_OC_0 -> rotateHeadImageData = headImageData
            else -> rotateHeadImageData = headImageData
        }
        // 非0度的情况，旋转图像
        if (rotateDegree != null) {
            rotateHeadImageData = ByteArray(headImageData.size)
            rotateCode = ArcSoftImageUtil.rotateImage(
                headImageData,
                rotateHeadImageData,
                cropRect.width(),
                cropRect.height(),
                rotateDegree,
                ArcSoftImageFormat.NV21
            )
            if (rotateCode != ArcSoftImageUtilError.CODE_SUCCESS) {
                throw RuntimeException("rotate image failed, code is $rotateCode")
            }
        }
        // 将创建一个Bitmap，并将图像数据存放到Bitmap中
        val headBmp = Bitmap.createBitmap(
            cropImageWidth,
            cropImageHeight,
            Bitmap.Config.RGB_565
        )
        if (ArcSoftImageUtil.imageDataToBitmap(
                rotateHeadImageData,
                headBmp,
                ArcSoftImageFormat.NV21
            ) != ArcSoftImageUtilError.CODE_SUCCESS
        ) {
            throw RuntimeException("failed to transform image data to bitmap")
        }
        return headBmp
    }

    fun setImageSize(previewSize: Camera.Size?) {
        this.previewSize = previewSize
    }

}