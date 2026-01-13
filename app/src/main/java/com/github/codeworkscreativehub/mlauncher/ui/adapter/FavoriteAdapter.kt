package com.github.codeworkscreativehub.mlauncher.ui.adapter

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppDiffCallback

// Adapter to display Home Apps
class FavoriteAdapter(
    private val apps: MutableList<AppListItem>, // List of AppListItem objects
    private val onItemMoved: (fromPosition: Int, toPosition: Int) -> Unit,
    private val prefs: Prefs
) : RecyclerView.Adapter<FavoriteAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appTextView: TextView = itemView.findViewById(R.id.homeAppLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.home_app_button, parent, false)
        return AppViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appItem = apps[position]

        // Set the label text from the app item
        holder.appTextView.text = appItem.activityLabel

        // Set the text size and color dynamically using prefs
        holder.appTextView.setTextColor(prefs.appColor)  // Get color from prefs
        holder.appTextView.textSize = prefs.appSize.toFloat()  // Get text size from prefs

        // Set the gravity to align text to the left and ensure it's centered vertically
        holder.appTextView.gravity = Gravity.START or Gravity.CENTER_VERTICAL

        // Set drawable to the right side of the text
        val prefixDrawable: Drawable? =
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_order_apps)
        holder.appTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, prefixDrawable, null)
    }


    override fun getItemCount(): Int = apps.size

    // Notify when an item is moved
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val temp = apps[fromPosition]
        apps[fromPosition] = apps[toPosition]
        apps[toPosition] = temp
        notifyItemMoved(fromPosition, toPosition)
        onItemMoved(fromPosition, toPosition)  // Notify the view model of the change
    }

    // Update the list when the data changes
    fun updateList(newList: List<AppListItem>) {
        val diffCallback = AppDiffCallback(apps, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        apps.clear()
        apps.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }
}
