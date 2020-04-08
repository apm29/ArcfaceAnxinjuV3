package com.apm29.anxinju

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.renderscript.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import com.apm.data.api.Api
import com.apm.data.api.ApiKt
import com.apm.data.db.FaceDBManager
import com.apm.data.db.entity.PassageLog
import com.apm.data.model.BaseResponse
import com.apm.data.model.RetrofitManager
import com.apm.data.persistence.PropertiesUtils
import com.apm.rs485reader.service.DataSyncService
import com.apm.rs485reader.service.IPictureCaptureInterface
import com.apm29.anxinju.faceserver.CompareResult
import com.apm29.anxinju.faceserver.FaceServer
import com.apm29.anxinju.model.DrawInfo
import com.apm29.anxinju.model.FacePreviewInfo
import com.apm29.anxinju.service.FaceDataSyncService
import com.apm29.anxinju.util.ConfigUtil
import com.apm29.anxinju.util.DrawHelper
import com.apm29.anxinju.util.QRCodeUtils
import com.apm29.anxinju.util.camera.CameraHelper
import com.apm29.anxinju.util.camera.CameraListener
import com.apm29.anxinju.util.face.FaceHelper
import com.apm29.anxinju.util.face.FaceListener
import com.apm29.anxinju.util.face.RecognizeColor
import com.apm29.anxinju.util.face.RequestFeatureStatus
import com.apm29.anxinju.widget.FaceRectView
import com.arcsoft.face.*
import com.arcsoft.face.enums.DetectFaceOrientPriority
import com.arcsoft.face.enums.DetectMode
import com.arcsoft.imageutil.ArcSoftImageFormat
import com.arcsoft.imageutil.ArcSoftImageUtil
import com.arcsoft.imageutil.ArcSoftImageUtilError
import com.arcsoft.imageutil.ArcSoftRotateDegree
import com.common.pos.api.util.PosUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_face_attr_preview.*
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FaceAttrPreviewActivity : BaseActivity(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
    private var detectQrCode = false
    lateinit var cameraHelper: CameraHelper
    lateinit var drawHelper: DrawHelper
    lateinit var previewSize: Camera.Size
    private val rgbCameraId: Int? = Camera.CameraInfo.CAMERA_FACING_BACK
    lateinit var ftEngine: FaceEngine
    lateinit var frEngine: FaceEngine
    lateinit var faceHelper: FaceHelper

    @Volatile
    private var rgbData: ByteArray? = null

    private val deviceId by lazy {
        PropertiesUtils.getInstance().init()
        PropertiesUtils.getInstance().readString("deviceId", "19BC95DC053A2A4D130FC17C9B4E6EED43")
        //"19BC95DC053A2A4D130FC17C9B4E6EED43"
    }

    fun showTip(s: String) {
        handler.post {
            mTipText.text = s
            mTipText.visibility = View.VISIBLE
        }
        handler.postDelayed({
            mTipText.visibility = View.GONE
        }, 3000)
    }

    /**
     * 用于记录人脸识别相关状态
     */
    private val requestFeatureStatusMap =
        ConcurrentHashMap<Int, Int>()


    private var afCode = -1
    private val processMask =
        FaceEngine.ASF_AGE or FaceEngine.ASF_FACE3DANGLE or FaceEngine.ASF_GENDER or FaceEngine.ASF_LIVENESS

    /**
     * 相机预览显示的控件，可为SurfaceView或TextureView
     */
    private var previewView: View? = null
    private var faceRectView: FaceRectView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_attr_preview)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attributes = window.attributes
        attributes.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        window.attributes = attributes

        // Activity启动后就锁定为启动时的方向
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        previewView = findViewById(R.id.texture_preview)
        faceRectView = findViewById(R.id.face_rect_view)
        hideUi()

        qrCode.setOnClickListener {
            detectQrCode = true
            showTip("请将二维码对准扫描区域")
            qrHandler.removeCallbacksAndMessages(null)
            qrHandler.postDelayed({
                detectQrCode = false
            }, 30000)
        }
    }

    private fun hideUi() {
        val decorView = window.decorView

        val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.decorView.systemUiVisibility = flags
        decorView
            .setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    decorView.systemUiVisibility = flags
                }
            }
    }


    private fun initEngine() {
        if (!::ftEngine.isInitialized) {
            ftEngine = FaceEngine()
        }
        if (!::frEngine.isInitialized) {
            frEngine = FaceEngine()
        }
        frEngine.init(
            this, DetectMode.ASF_DETECT_MODE_IMAGE,
            DetectFaceOrientPriority.ASF_OP_270_ONLY,
            16,
            20,
            FaceEngine.ASF_FACE_RECOGNITION
        )
        afCode = ftEngine.init(
            this,
            DetectMode.ASF_DETECT_MODE_VIDEO,
            DetectFaceOrientPriority.ASF_OP_90_ONLY,
            16,
            20,
            FaceEngine.ASF_FACE_DETECT or FaceEngine.ASF_AGE or FaceEngine.ASF_FACE3DANGLE or FaceEngine.ASF_GENDER or FaceEngine.ASF_LIVENESS
        )
        Log.i(Companion.TAG, "initEngine:  init: $afCode")
        if (afCode != ErrorInfo.MOK) {
            showToast("初始化引擎失败:$afCode")
        }

        FaceServer.getInstance().init(this)
        //人脸同步
        val service = Intent(this, FaceDataSyncService::class.java)
        startService(service)

        //射频id同步
        val syncService = Intent(this, DataSyncService::class.java)
        syncService.putExtra(DataSyncService.DEVICE_ID, deviceId)
        //19BC95DC053A2A4D130FC17C9B4E6EED43
        startService(syncService)

    }

    private var mBinder: IPictureCaptureInterface? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBinder = IPictureCaptureInterface.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {

        }
    }

    override fun onStart() {
        super.onStart()
        val syncService = Intent(this, DataSyncService::class.java)
        syncService.putExtra(DataSyncService.DEVICE_ID, deviceId)
        bindService(syncService, serviceConnection, Service.BIND_AUTO_CREATE)
    }


    override fun onPause() {
        super.onPause()
        startActivity(Intent(this, FaceAttrPreviewActivity::class.java))
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }


    var `in`: Allocation? = null
    var out: Allocation? = null
    var yuvType: Type.Builder? = null
    var rgbaType: Type.Builder? = null
    private val rs: RenderScript by lazy {
        RenderScript.create(this)
    }
    private val yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB by lazy {
        ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    }

    private fun saveBitmapForRFID(data: ByteArray) {
        if (yuvType == null) {
            yuvType = Type.Builder(rs, Element.U8(rs)).setX(data.size)
            `in` = Allocation.createTyped(rs, yuvType!!.create(), Allocation.USAGE_SCRIPT)

            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(PREFER_HEIGHT)
                .setY(PREFER_WIDTH)
            out = Allocation.createTyped(rs, rgbaType!!.create(), Allocation.USAGE_SCRIPT)
        }
        `in`!!.copyFrom(data)

        yuvToRgbIntrinsic.setInput(`in`)
        yuvToRgbIntrinsic.forEach(out)

        val bmpout =
            Bitmap.createBitmap(
                PREFER_HEIGHT,
                PREFER_WIDTH, Bitmap.Config.ARGB_8888
            )
        out!!.copyTo(bmpout)
        if (mBinder != null) {
            try {
                mBinder?.setPicture(bmpout)
            } catch (e: Throwable) {

            }

        }
    }

    private fun unInitEngine() {
        if (afCode == 0) {
            afCode = ftEngine.unInit()
            Log.i(Companion.TAG, "unInitEngine: $afCode")
        }
    }

    override fun onDestroy() {
        if (::cameraHelper.isInitialized) {
            cameraHelper.release()
        }
        unInitEngine()
        super.onDestroy()
    }

    private suspend fun isAllPass(): Boolean = suspendCoroutine {
        val instance = PropertiesUtils.getInstance()
        instance.init()
        instance.open()
        it.resume(instance.readBoolean("allPass", false))
    }

    private var lastOpenTime = 0L
    private var disposable: Disposable? = null

    @SuppressLint("CheckResult")
    private fun letGo() = launch {
        val now = System.currentTimeMillis()
        val instance = PropertiesUtils.getInstance()
        instance.init()
        instance.open()
        val relayCount = instance.readInt("relayCount", 2)
        val relayDelay = instance.readInt("relayDelay", 2000)

        if ((now - lastOpenTime) > (relayCount * relayDelay + 1000L)) {
            if (disposable?.isDisposed == false) {
                disposable?.dispose()
            }
            lastOpenTime = now
            disposable = Observable.interval(0, relayDelay.toLong(), TimeUnit.MILLISECONDS)
                .take(relayCount.toLong())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    println("YJW:open gate")
                    val relayPowerSuccess = PosUtil.setRelayPower(1)
                    println("继电器:$relayPowerSuccess")
                }, {
                    it.printStackTrace()
                }, {
                    PosUtil.setRelayPower(0)
                })
        }

    }

    //上次记录时间
    private var lastRecordTimeMillis: Long = 0
    private fun afterSeconds(second: Int): Boolean {
        return System.currentTimeMillis() - lastRecordTimeMillis > 1000 * second
    }


    private val threadContext: CoroutineContext =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private fun initCamera() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val cameraListener: CameraListener = object : CameraListener {
            override fun onCameraOpened(
                camera: Camera,
                cameraId: Int,
                displayOrientation: Int,
                isMirror: Boolean
            ) {
                Log.i(
                    Companion.TAG,
                    "onCameraOpened: $cameraId  $displayOrientation $isMirror"
                )
                previewSize = camera.parameters.previewSize
                if (!::drawHelper.isInitialized)
                    drawHelper = DrawHelper(
                        previewSize.width,
                        previewSize.height,
                        previewView!!.width,
                        previewView!!.height,
                        displayOrientation
                        ,
                        cameraId,
                        isMirror,
                        false,
                        true
                    )

                val faceListener: FaceListener = object : FaceListener {
                    override fun onFail(e: java.lang.Exception) {
                        Log.e(
                            TAG,
                            "onFail: " + e.message
                        )
                    }

                    //请求FR的回调
                    override fun onFaceFeatureInfoGet(
                        faceFeature: FaceFeature?,
                        requestId: Int,
                        errorCode: Int,
                        nv21: ByteArray?
                    ) {
                        //FR成功
                        if (faceFeature != null) {
                            Log.i(
                                TAG,
                                "onPreview: fr end = " + System.currentTimeMillis() + " trackId = " + requestId
                            )
                            //获取特征值
                            val compareResult: CompareResult? = FaceServer.getInstance()
                                .compareInDatabase(faceFeature, this@FaceAttrPreviewActivity)
                            if (compareResult != null && compareResult.similar > 0.8F) {
                                requestFeatureStatusMap[requestId] = RequestFeatureStatus.SUCCEED
                                faceHelper.setName(requestId, "已注册${compareResult.userName ?: ""}")
                                letGo()
                                launch(threadContext) {
                                    sendEnterLog(
                                        compareResult.id,
                                        compareResult.userName,
                                        compareResult.userId,
                                        nv21,
                                        previewSize.width,
                                        previewSize.height
                                    )
                                }
                                hideKeyboard(0)
                            } else {
                                faceHelper.setName(requestId, "未注册")
                                requestFeatureStatusMap[requestId] = RequestFeatureStatus.FAILED
                                launch(threadContext) {
                                    if (isAllPass()) {
                                        letGo()
                                    } else {
                                        if (!detectQrCode)
                                            showKeyCodeIME()
                                    }
                                    val tempFile = File(
                                        getTempDirectory(),
                                        "image_captured.jpg"
                                    )
                                    val image = saveImageFile(
                                        tempFile, nv21, previewSize.width,
                                        previewSize.height
                                    )
                                    if (image != null) {
                                        recordTempVisitorFace(image)
                                    }
                                }

                            }
                        } else {
                            Log.e(TAG, "fr fail")
                        }
                    }

                    override fun onFaceLivenessInfoGet(
                        livenessInfo: LivenessInfo?,
                        requestId: Int,
                        errorCode: Int
                    ) {
                    }
                }

                faceHelper = FaceHelper.Builder()
                    .ftEngine(ftEngine)
                    .frEngine(frEngine)
                    .frQueueSize(10)
                    .flQueueSize(10)
                    .previewSize(previewSize)
                    .faceListener(faceListener)
                    .trackedFaceCount(ConfigUtil.getTrackedFaceCount(applicationContext))
                    .build()
            }

            override fun onPreview(
                nv21: ByteArray,
                camera: Camera
            ) {
                if (faceRectView != null) {
                    faceRectView!!.clearFaceInfo()
                }

                try {
                    saveBitmapForRFID(nv21)
                } finally {

                }
                nv21DataOfCurrentFrame = nv21.clone()
                if (detectQrCode) {
                    launch(threadContext) {
                        val qrcode = QRCodeUtils.decodeWithImage(
                            nv21,
                            640,
                            480
                        )
                        if (qrcode != null) {
                            detectQrCode = false
                            sendKeyPassInfo(qrcode)
                        }
                    }
                    hideKeyboard(0)

                }
                val faceInfoList: List<FaceInfo> =
                    ArrayList()
                //                long start = System.currentTimeMillis();
                var code = ftEngine.detectFaces(
                    nv21,
                    previewSize.width,
                    previewSize.height,
                    FaceEngine.CP_PAF_NV21,
                    faceInfoList
                )
                if (code == ErrorInfo.MOK && faceInfoList.isNotEmpty()) {
                    code = ftEngine.process(
                        nv21,
                        previewSize.width,
                        previewSize.height,
                        FaceEngine.CP_PAF_NV21,
                        faceInfoList,
                        processMask
                    )
                    if (code != ErrorInfo.MOK) {
                        return
                    }
                } else {
                    val drawInfoList: MutableList<DrawInfo> =
                        ArrayList()
                    if (detectQrCode) {
                        drawInfoList.add(
                            DrawInfo(
                                rect =  Rect(
                                    100,280,540,680
                                ),
                                age = 0,
                                liveness = 0,
                                sex = 0,
                                color = RecognizeColor.COLOR_FAILED,
                                name = "请对准扫描区域"
                            )
                        )
                    }
                    drawHelper.draw(faceRectView, drawInfoList)
                    return
                }
                rgbData = nv21
                processPreviewData()
                val ageInfoList: List<AgeInfo> = ArrayList()
                val genderInfoList: List<GenderInfo> =
                    ArrayList()
                val face3DAngleList: List<Face3DAngle> =
                    ArrayList()
                val faceLivenessInfoList: List<LivenessInfo> =
                    ArrayList()
                val ageCode = ftEngine.getAge(ageInfoList)
                val genderCode = ftEngine.getGender(genderInfoList)
                val face3DAngleCode = ftEngine.getFace3DAngle(face3DAngleList)
                val livenessCode = ftEngine.getLiveness(faceLivenessInfoList)

                // 有其中一个的错误码不为ErrorInfo.MOK，return
                if (ageCode or genderCode or face3DAngleCode or livenessCode != ErrorInfo.MOK) {
                    return
                }
                if (faceRectView != null) {
                    val drawInfoList: MutableList<DrawInfo> =
                        ArrayList()
                    for (i in faceInfoList.indices) {

                        drawInfoList.add(
                            DrawInfo(
                                drawHelper.adjustRect(
                                    faceInfoList[i].rect
                                ),
                                genderInfoList[i].gender,
                                ageInfoList[i].age,
                                faceLivenessInfoList[i].liveness,
                                if (faceHelper.getName(faceInfoList[i].faceId) == null) RecognizeColor.COLOR_UNKNOWN else RecognizeColor.COLOR_SUCCESS,
                                faceHelper.getName(faceInfoList[i].faceId)
                            )
                        )
                        keyboardHandler.removeCallbacksAndMessages(null)
                    }
                    if (detectQrCode) {
                        drawInfoList.add(
                            DrawInfo(
                                rect = Rect(
                                    100,280,540,680
                                ),
                                age = 0,
                                liveness = 0,
                                sex = 0,
                                color = RecognizeColor.COLOR_FAILED,
                                name = "请对准扫描区域"
                            )
                        )
                    }
                    drawHelper.draw(faceRectView, drawInfoList)
                }

            }

            override fun onCameraClosed() {
                Log.i(Companion.TAG, "onCameraClosed: ")
            }

            override fun onCameraError(e: Exception) {
                Log.i(
                    Companion.TAG,
                    "onCameraError: " + e.message
                )
            }

            override fun onCameraConfigurationChanged(
                cameraID: Int,
                displayOrientation: Int
            ) {
                drawHelper.cameraDisplayOrientation = displayOrientation
                Log.i(
                    Companion.TAG,
                    "onCameraConfigurationChanged: $cameraID  $displayOrientation"
                )
            }
        }
        cameraHelper = CameraHelper.Builder()
            .previewViewSize(
                Point(
                    previewView!!.measuredWidth,
                    previewView!!.measuredHeight
                )
            )
            .rotation(windowManager.defaultDisplay.rotation)
            .specificCameraId(rgbCameraId ?: Camera.CameraInfo.CAMERA_FACING_FRONT)
            .isMirror(false)
            .previewOn(previewView)
            .cameraListener(cameraListener)
            .build()
        cameraHelper.init()
        cameraHelper.start()
    }

    override fun initSuccess() {
        initEngine()
        initCamera()
    }

    override fun onRestart() {
        super.onRestart()
        if (::cameraHelper.isInitialized) {
            cameraHelper.init()
            cameraHelper.start()
        }
    }

    companion object {
        // 图片越大，性能消耗越大，也可以选择640*480， 1280*720
        private const val PREFER_WIDTH = 640
        private const val PREFER_HEIGHT = 480
        private const val TAG = "FaceAttrPreviewActivity"
    }

    var nv21DataOfCurrentFrame: ByteArray? = null

    /**
     * 处理预览数据
     */
    @Synchronized
    private fun processPreviewData() {
        rgbData?.apply {
            val cloneNv21Rgb: ByteArray = this.clone()
            if (faceRectView != null) {
                faceRectView!!.clearFaceInfo()
            }
            val facePreviewInfoList: List<FacePreviewInfo>? =
                faceHelper.onPreviewFrame(cloneNv21Rgb)
            if (!facePreviewInfoList.isNullOrEmpty() && ::previewSize.isInitialized) {
                for (facePreviewInfo in facePreviewInfoList) {
                    // 注意：这里虽然使用的是IR画面活体检测，RGB画面特征提取，但是考虑到成像接近，所以只用了RGB画面的图像质量检测
                    val status: Int? =
                        requestFeatureStatusMap[facePreviewInfo.trackId]
                    /**
                     * 对于每个人脸，若状态为空或者为失败，则请求特征提取（可根据需要添加其他判断以限制特征提取次数），
                     * 特征提取回传的人脸特征结果在[FaceListener.onFaceFeatureInfoGet]中回传
                     */
                    if (status == null || status == RequestFeatureStatus.TO_RETRY) {
                        requestFeatureStatusMap[facePreviewInfo.trackId] =
                            RequestFeatureStatus.SEARCHING
                        faceHelper.requestFaceFeature(
                            cloneNv21Rgb, facePreviewInfo.faceInfo,
                            previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21,
                            facePreviewInfo.trackId
                        )
                    }
                }
            }
            rgbData = null
        }
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

    private fun saveImageFile(file: File?, nv21: ByteArray?, width: Int, height: Int): File? {
        if (nv21 == null) {
            return null
        }
        val bitmap = getCropImage(nv21, width, height, FaceEngine.ASF_OC_90) ?: return null
        var fileOutputStream: FileOutputStream? = null
        val directory =
            getTempDirectory()
        val image: File
        image = file ?: File(directory, "img_captured.jpg")
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

    private suspend fun sendEnterLog(
        id: Int,
        userName: String?,
        userId: String?,
        nv21: ByteArray?,
        width: Int,
        height: Int
    ) {
        println("YJW:${Thread.currentThread()}")
        val tempFile = File(
            getTempDirectory(),
            "image_captured_${System.currentTimeMillis()}.jpg"
        )
        val image = saveImageFile(tempFile, nv21, width, height) ?: return
        val apiKt = RetrofitManager.getInstance().retrofit.create(ApiKt::class.java)
        try {
            val uploadResp = apiKt.uploadFile(
                MultipartBody.Builder()
                    .addFormDataPart(
                        "file",
                        image.name,
                        RequestBody.create(MediaType.parse("multipart/form-data"), image)
                    )
                    .build()
            )
            if (uploadResp.success()) {
                val addLogResp = apiKt.passByFaceId(
                    id.toString(),
                    deviceId,
                    uploadResp.data.filePath
                )
                if (addLogResp.success()) {
                    Log.e(TAG, "上传日志成功")
                } else {
                    Log.e(TAG, "上传日志失败:${addLogResp.text}")
                }

            } else {
                Log.e(TAG, "上传图片失败")
            }

            val logFile = if (uploadResp.success()) {
                null
            } else {
                val logFile = File(
                    Environment.getExternalStorageDirectory(),
                    "${System.currentTimeMillis()}_log.jpg"
                )
                tempFile.copyTo(logFile, overwrite = true)
            }

            FaceDBManager.getInstance(this).getLogDao().addLog(
                PassageLog(
                    uploadTime = Date(),
                    isUploaded = uploadResp.success(),
                    personId = userId ?: "UNKNOWN",
                    personName = userName ?: "UNKNOWN",
                    imageUrl = if (uploadResp.success()) uploadResp.data.toString() else logFile?.absolutePath
                        ?: "NO_FILE_RECORD"
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "上传日志失败")
            e.printStackTrace()
        } finally {
            if (tempFile.exists())
                tempFile.delete()
        }


    }

    private suspend fun recordTempVisitorFace(image: File) {
        println("YJW:record face")
        println("RESPONSE:${Thread.currentThread()}")
        val api = RetrofitManager.getInstance().retrofit.create(ApiKt::class.java)
        val upload = try {
            val response = api.addTempVisitorRecord(
                MultipartBody.Builder()
                    .addFormDataPart(
                        "pic",
                        image.name,
                        RequestBody.create(MediaType.parse("multipart/form-data"), image)
                    )
                    .addFormDataPart("gateId", deviceId)
                    .build()
            )
            response

        } catch (e: Exception) {
            e.printStackTrace()
            BaseResponse.fail(e)
        }
        try {
            val logFile = if (upload.success()) {
                showToast("上传日志成功")
                null
            } else {
                val logFile = File(
                    Environment.getExternalStorageDirectory(),
                    "${System.currentTimeMillis()}_log.jpg"
                )
                image.copyTo(logFile, overwrite = true)
            }
            FaceDBManager.getInstance(this).getLogDao().addLog(
                PassageLog(
                    uploadTime = Date(),
                    isUploaded = upload.success(),
                    personId = "UNKNOWN",
                    personName = "VISITOR",
                    imageUrl = if (upload.success()) upload.data.toString() else logFile?.absolutePath
                        ?: "NO_FILE_RECORD"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    private val keyboardHandler = Handler()
    private val qrHandler = Handler()


    private fun showKeyCodeIME() {
        configKeyBoard()
        val tvKeyCode: EditText = findViewById(R.id.tv_key_code)
        if (tvKeyCode.visibility == View.GONE) {
            tvKeyCode.text = null
        }
        handler.post {
            layout_keyboard.visibility = View.VISIBLE
        }
        hideKeyboard()
    }

    private fun hideKeyboard(delay: Long = 30000) {
        keyboardHandler.postDelayed({
            layout_keyboard.visibility = View.GONE
        }, delay)

    }

    private fun configKeyBoard() {
        val btn0: Button = findViewById(R.id.btn_code_0)
        val btn1: Button = findViewById(R.id.btn_code_1)
        val btn2: Button = findViewById(R.id.btn_code_2)
        val btn3: Button = findViewById(R.id.btn_code_3)
        val btn4: Button = findViewById(R.id.btn_code_4)
        val btn5: Button = findViewById(R.id.btn_code_5)
        val btn6: Button = findViewById(R.id.btn_code_6)
        val btn7: Button = findViewById(R.id.btn_code_7)
        val btn8: Button = findViewById(R.id.btn_code_8)
        val btn9: Button = findViewById(R.id.btn_code_9)
        val btnDelete: Button = findViewById(R.id.btn_code_delete)
        val btnConfirm: Button = findViewById(R.id.btn_code_confirm)
        val tvKeyCode: EditText = findViewById(R.id.tv_key_code)
        val numberListener = View.OnClickListener { v ->
            if (v is Button) {
                val origin = tvKeyCode.text
                if (origin.length < 10) {
                    tvKeyCode.append(v.text)
                }
            }
            keyboardHandler.removeCallbacksAndMessages(null)
            hideKeyboard()
        }
        btn0.setOnClickListener(numberListener)
        btn1.setOnClickListener(numberListener)
        btn2.setOnClickListener(numberListener)
        btn3.setOnClickListener(numberListener)
        btn4.setOnClickListener(numberListener)
        btn5.setOnClickListener(numberListener)
        btn6.setOnClickListener(numberListener)
        btn7.setOnClickListener(numberListener)
        btn8.setOnClickListener(numberListener)
        btn9.setOnClickListener(numberListener)
        btnDelete.setOnClickListener {
            val origin = tvKeyCode.text
            if (origin.isNotEmpty()) {
                tvKeyCode.setText(origin.subSequence(0, origin.length - 1))
            }
        }

        btnConfirm.setOnClickListener {
            val passCode = tvKeyCode.text.toString().trim { it <= ' ' }
            RetrofitManager.getInstance().retrofit.create(Api::class.java)
                .passByKeyCode(deviceId, passCode)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({ baseResponse ->
                    showToast(baseResponse.text)
                    if (baseResponse.success()) {
                        letGo()
                        layout_keyboard.visibility = View.GONE
                        launch(threadContext) {
                            sendKeyPassInfo(passCode)
                        }
                    }
                    tvKeyCode.text = null
                    println(baseResponse)
                }, { throwable ->
                    throwable.printStackTrace()
                    showToast("ERROR:" + throwable.localizedMessage)
                })
        }
    }

    //上传通行码通行的信息
    @SuppressLint("CheckResult")
    private suspend fun sendKeyPassInfo(passCode: String) {
        val tempFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "image_captured.jpg"
        )
        val image =
            saveImageFile(tempFile, nv21DataOfCurrentFrame, previewSize.width, previewSize.height)
        if (image == null) {
            println("图片为空")
            return
        }
        val api = RetrofitManager.getInstance().retrofit.create(ApiKt::class.java)

        val uploadResponse = api.uploadImageSync(
            MultipartBody.Builder()
                .addFormDataPart(
                    "pic",
                    image.name,
                    RequestBody.create(
                        MediaType.parse("multipart/form-data"),
                        image
                    )
                )
                .build()
        )

        try {
            if (uploadResponse.success()) {
                val passResponse = api.addKeyPassRecord(
                    deviceId,
                    passCode,
                    uploadResponse.data.orginPicPath
                )


                if (passResponse.success()) {
                    showTip(passResponse.text)
                } else {
                    showTip(passResponse.text)
                }
            } else {
                showTip(uploadResponse.text)
            }
        } catch (e: Exception) {
            showTip(e.message ?: "通行码通行发生错误")
        } finally {
            detectQrCode = false
        }
        val logFile = if (uploadResponse.success()) {
            showToast("上传日志成功")
            null
        } else {
            val logFile = File(
                Environment.getExternalStorageDirectory(),
                "${System.currentTimeMillis()}_log.jpg"
            )
            image.copyTo(logFile, overwrite = true)
        }

        FaceDBManager.getInstance(this).getLogDao().addLog(
            PassageLog(
                uploadTime = Date(),
                isUploaded = uploadResponse.success(),
                personId = "UNKNOWN",
                personName = "VISITOR",
                imageUrl = if (uploadResponse.success()) uploadResponse.data.toString() else logFile?.absolutePath
                    ?: "NO_FILE_RECORD"
            )
        )

    }


}