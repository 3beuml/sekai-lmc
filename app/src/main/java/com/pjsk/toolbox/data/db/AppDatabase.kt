package com.pjsk.toolbox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MasterRowEntity::class,
        SyncStateEntity::class,
        VersionStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun masterDao(): MasterDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun versionStateDao(): VersionStateDao

    companion object {
        private const val NAME = "sekai-lmc.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                // 这是「可随时重新下载」的缓存式数据，不做迁移，版本变化直接重建，避免无谓的迁移代码
                .fallbackToDestructiveMigration()
                .build()
    }
}
