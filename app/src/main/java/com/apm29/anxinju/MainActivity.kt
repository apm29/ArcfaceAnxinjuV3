package com.apm29.anxinju

import android.content.Intent
import android.os.Bundle
import com.apm.data.persistence.PropertiesUtils
import com.apm29.anxinju.service.KeepAliveService
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : BaseActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val keepAliveService = Intent(this, KeepAliveService::class.java)
        startService(keepAliveService)
        PropertiesUtils.getInstance().init()
        val deviceId = PropertiesUtils.getInstance().readString("deviceId", "ArcFace")
        tv.text = deviceId
    }

    override fun initSuccess() {
        startActivity(Intent(this, FaceAttrPreviewActivity::class.java))
    }
}
