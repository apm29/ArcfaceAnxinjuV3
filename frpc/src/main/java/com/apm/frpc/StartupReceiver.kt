package com.apm.frpc

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class StartupReceiver : BroadcastReceiver() {
    val ACTION = "android.intent.action.BOOT_COMPLETED"

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION) {

//
//            try {
////                val sshIntent = Intent()
////                sshIntent.action = "berserker.android.apps.sshdroid.command.START"
////                context.sendBroadcast(sshIntent)
//
//                val ssh2
//                //1.如果自启动APP，参数为需要自动启动的应用包名
//                        = context.packageManager.getLaunchIntentForPackage("berserker.android.apps.sshdroid")?.apply {
//                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                }
//                context.startActivity(ssh2)
//
//                val mIntent = Intent(Intent.ACTION_MAIN)
//                mIntent.addCategory(Intent.CATEGORY_LAUNCHER)
//                //要启动的app的包名"src/com/Routon/HIDTest"
//                val packageName = "berserker.android.apps.sshdroid"
//                //要启动的Activity "src/com/Routon/HIDTest/HIDTestActivity"
//                val className = "berserker.android.apps.sshdroid.MainActivity"
//                //Create a new component identifier.创建一个新的组件标识符
//                val cn = ComponentName(packageName, className)
//                //给mIntent设置组件
//                mIntent.component = cn
//                mIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                //打开新Activity
//                context.startActivity(mIntent)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//
//            try {
//                val newIntent
//                //1.如果自启动APP，参数为需要自动启动的应用包名
//                        = context.packageManager.getLaunchIntentForPackage("com.apm.govrs485")?.apply {
//                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                }
//                context.startActivity(newIntent)
//            }  catch (e: Exception) {
//                e.printStackTrace()
//            }
//
//            val newIntent
//            //1.如果自启动APP，参数为需要自动启动的应用包名
//                    = context.packageManager.getLaunchIntentForPackage("com.apm.frpc")?.apply {
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            }
//            context.startActivity(newIntent)
            context.startService(Intent(context,FrpcService::class.java))

        }
    }
}