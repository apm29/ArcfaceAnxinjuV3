/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.apm29.anxinju.model

import android.util.Base64
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户实体类
 */
@Entity(tableName = "t_arc_user")
class ArcUser(
    @ColumnInfo
    @PrimaryKey
    var id: Int = 0,
    @ColumnInfo(name = "user_id")
    var userId: String? = "",
    @ColumnInfo(name = "user_name")
    var userName: String? = "",
    @ColumnInfo(name = "group_id")
    var groupId: String? = "",
    @ColumnInfo(name = "ctime")
    var ctime: Long? = 0,
    @ColumnInfo(name = "update_time")
    var updateTime: Long? = 0,
    @ColumnInfo(name = "user_info")
    var userInfo: String? = "",
    @ColumnInfo(name = "face_token")
    var faceToken: String? = null,
    @ColumnInfo(name = "image_name")
    var imageName: String? = "",
    @ColumnInfo(name = "feature")
    var feature: ByteArray? = null,
    var isChecked: Boolean = false
)