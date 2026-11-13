package com.github.codeworkscreativehub.mlauncher.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widgets")
data class SavedWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val col: Int,
    val row: Int,
    val width: Int,
    val height: Int,
    val cellsW: Int,
    val cellsH: Int,
    // Package of the widget's provider. Empty for rows saved before this field existed.
    @ColumnInfo(defaultValue = "") val providerPackage: String = ""
)
