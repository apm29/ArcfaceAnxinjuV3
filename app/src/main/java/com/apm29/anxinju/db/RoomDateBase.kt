package com.apm29.anxinju.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apm29.anxinju.db.dao.UserDao
import com.apm29.anxinju.model.ArcUser

/**
 *  author : ciih
 *  date : 2020/4/8 8:55 AM
 *  description :
 */
@Database(entities = [ArcUser::class],  version = 4)
abstract class RoomDateBase: RoomDatabase() {

    companion object {
        private const val DATA_BASE_NAME = "user.db"
        fun getInstance(context: Context): RoomDateBase {
            return Room.databaseBuilder(
                context, RoomDateBase::class.java,
                DATA_BASE_NAME
            )
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    abstract  fun getUserDao():UserDao

}