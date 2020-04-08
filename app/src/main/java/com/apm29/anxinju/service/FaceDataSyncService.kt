package com.apm29.anxinju.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apm.data.persistence.PropertiesUtils
import com.apm.rs485reader.service.DataSyncService
import com.apm29.anxinju.FaceAttrPreviewActivity
import com.apm29.anxinju.common.Constants
import com.apm29.anxinju.faceserver.FaceServer
import com.apm29.anxinju.service.SyncHelper.startSync
import com.apm29.anxinju.service.SyncHelper.stopSync
import com.arcsoft.face.FaceEngine
import kotlinx.coroutines.Job

class FaceDataSyncService : Service() {
    private var deviceId: String? = null
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
        initLicense()
        //deviceId = new FaceAuth().getDeviceId(this);
        PropertiesUtils.getInstance().init()
        deviceId = PropertiesUtils.getInstance().readString("deviceId","19BC95DC053A2A4D130FC17C9B4E6EED43")
        //本地人脸库初始化
        FaceServer.getInstance().init(this)
        startSyncLoop()
    }

    private fun initLicense() {
        FaceEngine.activeOnline(this@FaceDataSyncService, Constants.APP_ID, Constants.SDK_KEY)
    }

    var job: Job? = null

    /**
     * 30s间隔,更新数据
     */
    @SuppressLint("CheckResult")
    private fun startSyncLoop() {
        if (job == null || job!!.isCompleted) {
            job = startSync(this, deviceId!!) {
                stopForeground(true)
            }
        } else {
            Log.e(TAG, "同步进行中，无需更新JOB")
        }
    }

    override fun onStartCommand(
        intent: Intent,
        flags: Int,
        startId: Int
    ): Int {
        Log.d(TAG, "service onStartCommand")
        startSyncLoop()
        // 在API11之后构建Notification的方式
        val builder =
            Notification.Builder(this.applicationContext) //获取一个Notification构造器
        val nfIntent =
            Intent(this, FaceAttrPreviewActivity::class.java)
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0))
            .setContentTitle("数据同步服务") // 设置下拉列表里的标题
            .setContentText("人脸库数据同步服务") // 设置上下文内容
            .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    "人脸库数据同步服务",
                    NotificationManager.IMPORTANCE_HIGH
                )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
            val builderCompat =
                NotificationCompat.Builder(
                    this,
                    CHANNEL_ID
                )
                    .setContentTitle("数据同步服务") // 设置下拉列表里的标题
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentText("人脸库数据同步服务") // 设置上下文内容
                    .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间
            val notification = builderCompat.build() // 获取构建好的Notification
            notification.defaults = Notification.DEFAULT_SOUND //设置为默认的声音
            startForeground(110, notification)
            return super.onStartCommand(intent, flags, startId)
        }
        val notification = builder.build() // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND //设置为默认的声音
        startForeground(110, notification)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("on bind not implemented")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "service onDestroy")
        stopSync()
        stopForeground(true) // 停止前台服务--参数：表示是否移除之前的通知
    }

    companion object {
        private const val TAG = "FaceDataSyncService"
        const val CHANNEL_ID = "FACE_DATA_SYNC-1"
        var ACTION_NOTIFY_REGISTER = "ACTION_NOTIFY_REGISTER"
        var KEY_NOTIFY_REGISTER_MODEL = "KEY_NOTIFY_REGISTER_MODEL"
        var KEY_NOTIFY_REGISTER_SUCCESS = "KEY_NOTIFY_REGISTER_SUCCESS"
    }
}