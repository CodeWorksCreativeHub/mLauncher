package com.github.codeworkscreativehub.mlauncher.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.github.codeworkscreativehub.mlauncher.data.SavedWidgetEntity

@Database(entities = [SavedWidgetEntity::class], version = 1)
abstract class WidgetDatabase : RoomDatabase() {
    abstract fun widgetDao(): WidgetDao

    companion object {
        @Volatile
        private var INSTANCE: WidgetDatabase? = null

        fun getDatabase(context: Context): WidgetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WidgetDatabase::class.java,
                    "widget_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
