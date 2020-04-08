package com.apm29.anxinju.db.dao

import androidx.room.*
import com.apm29.anxinju.model.ArcUser

/**
 *  author : ciih
 *  date : 2020/4/8 8:57 AM
 *  description :
 */
@Dao
abstract class  UserDao {

    @Query("SELECT * from t_arc_user where id = :id")
    abstract fun getUser(id:Int): ArcUser?

    @Query("SELECT * FROM t_arc_user")
    abstract fun getUsers(): List<ArcUser>?

    @Query("DELETE FROM t_arc_user WHERE user_id = :userId")
    abstract fun userDelete(userId:String?)

    @Update(entity = ArcUser::class,onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateUser(arcUser: ArcUser): Int

    @Insert(entity = ArcUser::class,onConflict = OnConflictStrategy.REPLACE)
    abstract fun addUser(arcUser: ArcUser)
}