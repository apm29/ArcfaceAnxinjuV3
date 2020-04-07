package com.apm29.anxinju

import android.content.pm.ActivityInfo
import android.graphics.Point
import android.hardware.Camera
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.apm29.anxinju.model.DrawInfo
import com.apm29.anxinju.model.FacePreviewInfo
import com.apm29.anxinju.util.ConfigUtil
import com.apm29.anxinju.util.DrawHelper
import com.apm29.anxinju.util.camera.CameraHelper
import com.apm29.anxinju.util.camera.CameraListener
import com.apm29.anxinju.util.face.*
import com.apm29.anxinju.widget.FaceRectView
import com.arcsoft.face.*
import com.arcsoft.face.enums.DetectFaceOrientPriority
import com.arcsoft.face.enums.DetectMode
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class FaceAttrPreviewActivity : BaseActivity() {
    lateinit var cameraHelper: CameraHelper
    lateinit var drawHelper: DrawHelper
    lateinit var previewSize: Camera.Size
    private val rgbCameraId: Int? = Camera.CameraInfo.CAMERA_FACING_BACK
    lateinit var ftEngine: FaceEngine
    lateinit var frEngine: FaceEngine
    lateinit var faceHelper: FaceHelper

    @Volatile
    private var rgbData: ByteArray? = null


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
                if(!::drawHelper.isInitialized)
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
                        errorCode: Int
                    ) {
                        //FR成功
                        if (faceFeature != null) {
                            Log.i(
                                TAG,
                                "onPreview: fr end = " + System.currentTimeMillis() + " trackId = " + requestId
                            )
                            //todo 获取特征值成功

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
                                RecognizeColor.COLOR_UNKNOWN,
                                null
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

    companion object {
        private const val TAG = "FaceAttrPreviewActivity"
    }


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
                    if (status == null|| status == RequestFeatureStatus.TO_RETRY) {
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
}