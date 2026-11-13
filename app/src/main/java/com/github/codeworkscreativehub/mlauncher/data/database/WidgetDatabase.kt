package com.github.codeworkscreativehub.mlauncher.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.codeworkscreativehub.mlauncher.data.SavedWidgetEntity

@Database(entities = [SavedWidgetEntity::class], version = 2)
abstract class WidgetDatabase : RoomDatabase() {
    abstract fun widgetDao(): WidgetDao

    companion object {
        @Volatile
        private var INSTANCE: WidgetDatabase? = null

        // Keeps existing saved widgets; the new column defaults to empty
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE widgets ADD COLUMN providerPackage TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): WidgetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WidgetDatabase::class.java,
                    "widget_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
