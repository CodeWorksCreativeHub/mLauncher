package com.github.codeworkscreativehub.mlauncher.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.codeworkscreativehub.mlauncher.data.SavedWidgetEntity

@Dao
interface WidgetDao {

    @Query("SELECT * FROM widgets")
    suspend fun getAll(): List<SavedWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(widget: SavedWidgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(widgets: List<SavedWidgetEntity>): List<Long>

    @Delete
    suspend fun delete(widget: SavedWidgetEntity): Int

    @Query("DELETE FROM widgets")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM widgets WHERE appWidgetId = :id")
    suspend fun deleteById(id: Int)
}

