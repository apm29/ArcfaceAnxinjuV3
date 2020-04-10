package com.apm.frpc

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log

import com.apm.test.R

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.concurrent.thread

class FrpcService : Service() {

    private var TAG = "FrpcService"


    private var frpcStarted = false

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException("on bind not implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "service onStartCommand")
        //startFrp();
        frpc()

        // 在API11之后构建Notification的方式
        val builder = Notification.Builder(this.applicationContext) //获取一个Notification构造器
        val nfIntent = Intent(this, MainActivity::class.java)

        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0))
                .setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                .setContentTitle("FRPC服务") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("FRPC服务") // 设置上下文内容
                .setWhen(System.currentTimeMillis()) // 设置该通知发生的时间

        val notification = builder.build() // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND //设置为默认的声音
        startForeground(111, notification)
        return super.onStartCommand(intent, flags, startId)
    }
    private fun frpc() {
        if (frpcStarted) {
            Log.d(TAG, "FRPC SERVICE ALREADY START")
            return
        }

        frpcStarted = true
        Log.d(TAG, "FRPC START")
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val bufferedErrorReader = BufferedReader(InputStreamReader(process.errorStream))
            thread {
                try {
                    var line: String? = bufferedErrorReader.readLine()
                    while (line != null) {
                        Log.e(TAG, line)
                        line = bufferedErrorReader.readLine()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            thread {
                try {
                    var line: String? = bufferedReader.readLine()
                    while (line != null) {
                        Log.d(TAG, line)
                        line = bufferedReader.readLine()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            val dataOutputStream = DataOutputStream(outputStream)
            thread {
                while (true){
                    dataOutputStream.writeBytes("/data/local/tmp/frp_0.27.0_linux_arm/frpc -c /data/local/tmp/frp_0.27.0_linux_arm/frpc.ini\n")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }



    override fun onDestroy() {
        super.onDestroy()
        frpcStarted = false
        stopForeground(true)
    }
}
