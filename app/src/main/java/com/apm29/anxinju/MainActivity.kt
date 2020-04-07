package com.apm29.anxinju

import android.content.Intent
import android.os.Bundle

class MainActivity : BaseActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun initSuccess() {
        startActivity(Intent(this, FaceAttrPreviewActivity::class.java))
    }
}
