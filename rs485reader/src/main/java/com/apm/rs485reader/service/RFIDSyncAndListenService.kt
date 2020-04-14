package com.apm.rs485reader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.apm.data.api.Api
import com.apm.data.api.ApiKt
import com.apm.data.db.FaceDBManager
import com.apm.data.db.GateDataBase
import com.apm.data.db.entity.User
import com.apm.data.model.RetrofitManager
import com.apm.rs485reader.R
import com.common.pos.api.util.PosUtil
import com.spark.zj.comcom.serial.*
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 *  author : ciih
 *  date : 2020/4/10 4:50 PM
 *  description :
 */
class RFIDSyncAndListenService : Service() {


    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    companion object {
        const val NOTIFICATION_ID = 1219
        const val REQUEST_CODE = 1231
        const val DEVICE_ID = "deviceId"
        const val TTY_NAME = "ttyName"
        const val LAST_SYNC_TIME = "lastSyncTime"
        const val CHANNEL_ID = "RFID_DATA_SYNC-1"
        const val TAG: String = "RFID_SYNC ------> "
        const val ACTION_SIGNAL_OPEN: String = "RFIDSyncAndListenService.ACTION.OPEN"
        const val ACTION_KEY_HEX_STRING: String = "RFIDSyncAndListenService.KEY.HEX"
    }

    override fun onBind(intent: Intent?): IBinder? {
        throw UnsupportedOperationException("暂不支持")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }


    private val dataBase: GateDataBase by lazy {
        GateDataBase.getDB(this)
    }

    private lateinit var deviceId: String
    private lateinit var ttyName: String
    private val apiKt: ApiKt by lazy {
        RetrofitManager.getInstance().retrofit.create(ApiKt::class.java)
    }
    private val api: Api by lazy {
        RetrofitManager.getInstance().retrofit.create(Api::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val int = intent?.extras?.getInt("show")

        deviceId = intent?.extras?.getString(DEVICE_ID) ?: "NO_DEVICE_ID"
        ttyName = intent?.extras?.getString(TTY_NAME) ?: "ttyS1"
        Log.d(TAG, "deviceId = $deviceId")
        startListenOnRs485()

        startSelfWithNotification()

        startSyncLoop()

        return super.onStartCommand(intent, flags, startId)
    }

    private var rs485disposable: Disposable? = null
    private var loopDisposable: Disposable? = null
    private var serialSignalEmitter: ObservableEmitter<ByteArray>? = null
    private val serialHelper by lazy {
        //打开485接收
        val status = PosUtil.setRs485Status(0)
        Log.d(TAG, "485 接收状态开启 status = $status")
        val path = SerialPortFinder().allDevicesPath.first {
            it.contains(ttyName, true)
        }
        val serialHelper = SerialHelper(57600, path, 1, 8, 0)
        serialHelper.setDataReceiver {
            val verified = ByteCRC16.verifyCRC16Data(it)
            if (verified && dataBase.getGateDao().exist(ByteCRC16.getData(it))) {
                //有效信号
                serialSignalEmitter?.onNext(ByteCRC16.getData(it))
                Log.d(TAG, "校验成功 ${it?.toHexString()}")
            } else {
                if (!verified)//crc校验失败
                    Log.e(TAG, "校验失败 ${it?.toHexString()}")
                else//库里没有
                    Log.e(TAG, "无数据 ${it?.toHexString()}")
            }
        }
        serialHelper
    }

    private fun dispose(disposable: Disposable?) {
        if (disposable != null && !disposable.isDisposed) {
            disposable.dispose()
        }
    }

    private fun startListenOnRs485() {
        serialHelper.open()
        dispose(rs485disposable)
        rs485disposable = Observable.create<ByteArray> {
            serialSignalEmitter = it
        }
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .doOnError {
                it.printStackTrace()
            }
            .buffer(500, TimeUnit.MILLISECONDS)
            .subscribe {
                if (it != null && it.size > 0) {
                    val remoteId = dataBase.getGateDao()
                        .getUserByHex(it.first().toHexString().lastSixHex())?.remote_id
                    notifyPreviewActivity(remoteId?.toString())
                }
            }
    }

    private fun notifyPreviewActivity(remoteId: String?) = runBlocking {
        val intent = Intent(ACTION_SIGNAL_OPEN)
        intent.putExtra(
            ACTION_KEY_HEX_STRING,
            remoteId.toString()
        )
        sendBroadcast(intent)
        delay(1000)
        sendBroadcast(intent)
    }


    private fun startSelfWithNotification() {
        val intent = Intent(this, RFIDSyncAndListenService::class.java)
        intent.putExtra("show", 2)
        val builder = Notification.Builder(this)
            .setContentIntent(PendingIntent.getService(this, REQUEST_CODE, intent, 0))
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    this.resources,
                    R.drawable.track_image_angle
                )
            ) // 设置下拉列表中的图标(大图标)
            .setContentTitle("RFID数据同步服务") // 设置下拉列表里的标题
            .setSmallIcon(R.drawable.track_image_angle) // 设置状态栏内的小图标
            .setContentText("数据同步中") // 设置上下文内容
            .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "RFID数据同步服务",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
            val builderCompat = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RFID数据同步服务") // 设置下拉列表里的标题
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.track_image_angle) // 设置状态栏内的小图标
                .setContentText("数据同步中") // 设置上下文内容
                .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间
            val notification = builderCompat.build() // 获取构建好的Notification
            notification.defaults = Notification.DEFAULT_SOUND //设置为默认的声音
            startForeground(110, notification)
            return
        }

        val notification = builder.build()

        notification.defaults = Notification.DEFAULT_SOUND

        startForeground(NOTIFICATION_ID, notification)
    }

    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("SyncData", Context.MODE_PRIVATE)
    }

    private fun startSyncLoop() {
        dispose(loopDisposable)

        val syncDao = FaceDBManager.getInstance(this).getSyncDao()
        loopDisposable = Observable.interval(
            30_000, 30_000, TimeUnit.MILLISECONDS, Schedulers.io()
        )
            .flatMap {
                val lastSyncTime =
                    simpleDateFormat.format(syncDao.getRFIDLastSyncTime())
                Log.d(TAG, "last sync time: $lastSyncTime")
                api.getNotSyncRFID(
                    lastSyncTime, simpleDateFormat.format(Date()), deviceId
                )
            }
            .map { response ->
                if (response.success()) {
                    Log.d(
                        TAG,
                        "获取远端数据成功 size: ${response.data.size} \r\n[${response.data.joinToString()}]"
                    )
                    response.data.forEach { model ->
                        try {
                            if (!model.delete) {
                                val user = dataBase.getGateDao().getUserByRemoteId(model.id)
                                if (user != null) {
                                    Log.d(TAG, "user 已存在 remoteId ${model.id}")
                                }
                                val id = dataBase.getGateDao().addUser(
                                    User(
                                        remote_id = model.id
                                            ?: 0, hex = model.hexString
                                    )
                                )
                                Log.d(TAG, "插入成功 remoteId ${model.id} - 数据库ROW ID $id")
                            } else {
                                val deleteUser = dataBase.getGateDao().deleteUser(model.id ?: 0)
                                Log.d(TAG, "删除记录 remoteId ${model.id} - 删除条数 $deleteUser")
                            }
                        } catch (e: Exception) {
                            //返回未完成id
                            api.addUnRegisterRFID(
                                model.id.toString(),
                                deviceId
                            ).subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                                .subscribe({}, {
                                    it.printStackTrace()
                                })

                        }
                    }
                } else {
                    Log.e(TAG, "获取远端数据失败 $response")
                }
                Unit
            }
            .subscribe(
                {
                    Log.d(TAG, "数据库条数: ${dataBase.getGateDao().getAll().size}")
                    syncDao.resetRFIDLastSyncTime(System.currentTimeMillis() - 30_000)
                }
                ,
                {
                    startSyncLoop()
                    it.printStackTrace()
                }
            )
    }

    override fun onDestroy() {
        super.onDestroy()
        dataBase.close()
        dispose(loopDisposable)
        dispose(rs485disposable)
        stopForeground(true)
    }
}