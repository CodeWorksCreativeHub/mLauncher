package com.github.codeworkscreativehub.mlauncher.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.codeworkscreativehub.common.CrashHandler
import com.github.codeworkscreativehub.common.getCurrentTimestamp
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.getLocalizedStringArray
import com.github.codeworkscreativehub.common.share.ShareUtils
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Message
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NotesManagerAdapter(
    private val context: Context,
    private val activity: Activity,
    private var messages: MutableList<Message>,
    private val onMessageUpdated: () -> Unit
) : RecyclerView.Adapter<NotesManagerAdapter.MessageViewHolder>() {

    private lateinit var prefs: Prefs
    private lateinit var shareUtils: ShareUtils
    private var expandedPosition: Int? = null
    private val doubleExpandedPositions = mutableSetOf<Int>()
    private val collapsedByUser = mutableSetOf<Int>()

    private var lastClickTime = 0L
    private var clickRunnable: Runnable? = null
    private val clickHandler = Handler(Looper.getMainLooper())


    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemRootLayout: LinearLayout = view.findViewById(R.id.itemRootLayout)
        val messageItemLayout: LinearLayout = view.findViewById(R.id.messageItemLayout)
        val messageText: TextView = view.findViewById(R.id.messageText)
        val messageTimestamp: TextView = view.findViewById(R.id.messageTimestamp)
        val messageCategory: TextView = view.findViewById(R.id.messageCategory)
        val actionButtonsLayout: LinearLayout = view.findViewById(R.id.actionButtonsLayout)
        val shareButton: AppCompatImageButton = view.findViewById(R.id.shareButton)
        val editButton: AppCompatImageButton = view.findViewById(R.id.editButton)
        val removeButton: AppCompatImageButton = view.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        prefs = Prefs(parent.context)
        shareUtils = ShareUtils(context, activity)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // Set priority color
        val priorityColor = when (message.priority) {
            "High" -> ContextCompat.getColor(context, R.color.high)
            "Medium" -> ContextCompat.getColor(context, R.color.medium)
            "Low" -> ContextCompat.getColor(context, R.color.low)
            else -> ContextCompat.getColor(context, R.color.low)
        }

        // Background with border
        val background = GradientDrawable().apply {
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f,
                holder.messageItemLayout.resources.displayMetrics
            )
            setColor(prefs.bubbleBackgroundColor)
            setStroke(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 2f,
                    holder.messageItemLayout.resources.displayMetrics
                ).toInt(),
                priorityColor
            )
        }

        holder.messageItemLayout.background = background
        holder.messageText.setTextColor(prefs.bubbleMessageTextColor)
        holder.messageTimestamp.setTextColor(prefs.bubbleTimeDateColor)
        holder.messageCategory.setTextColor(prefs.bubbleCategoryColor)

        holder.messageText.text = message.text
        holder.messageTimestamp.text = message.timestamp
        holder.messageCategory.text = getLocalizedString(R.string.message_category, message.category)

        holder.shareButton.apply {
            backgroundTintList = ColorStateList.valueOf(prefs.bubbleBackgroundColor)
            setColorFilter(
                ContextCompat.getColor(context, android.R.color.holo_orange_light),
                PorterDuff.Mode.SRC_ATOP
            )
            setOnClickListener { onShareClick(position) }
        }

        holder.editButton.apply {
            backgroundTintList = ColorStateList.valueOf(prefs.bubbleBackgroundColor)
            setColorFilter(
                ContextCompat.getColor(context, android.R.color.holo_green_light),
                PorterDuff.Mode.SRC_ATOP
            )
            setOnClickListener { onEditClick(position) }
        }

        holder.removeButton.apply {
            backgroundTintList = ColorStateList.valueOf(prefs.bubbleBackgroundColor)
            setColorFilter(
                ContextCompat.getColor(context, android.R.color.holo_red_light),
                PorterDuff.Mode.SRC_ATOP
            )
            setOnClickListener { onDeleteClick(position) }
        }

        // Expansion states
        val isExpanded = position == expandedPosition
        val isDoubleExpanded = doubleExpandedPositions.contains(position)
        val isAutoExpanded = prefs.autoExpandNotes
        val isUserCollapsed = collapsedByUser.contains(position)

        // Handle root visibility
        holder.itemRootLayout.isVisible = !(expandedPosition != null && !isExpanded)

        // Final message text expansion logic
        holder.messageText.maxLines = when {
            isUserCollapsed && expandedPosition != position -> 0
            isAutoExpanded || isExpanded || isDoubleExpanded -> Int.MAX_VALUE
            else -> 0
        }

        // Show/hide action buttons only on single-click
        holder.actionButtonsLayout.isVisible = (prefs.clickToEditDelete && isExpanded)

        // Click listener with double-click detection
        holder.itemView.setOnClickListener {
            val clickTime = System.currentTimeMillis()
            val isDoubleClick = clickTime - lastClickTime < Constants.DOUBLE_CLICK_TIME_DELTA
            lastClickTime = clickTime

            if (isDoubleClick) {
                clickRunnable?.let { clickHandler.removeCallbacks(it) }

                if (prefs.autoExpandNotes) {
                    // Manual toggle overrides auto-expand
                    if (collapsedByUser.contains(position)) {
                        collapsedByUser.remove(position) // Re-expand
                    } else {
                        collapsedByUser.add(position) // Collapse
                    }
                } else {
                    // Toggle custom double-expanded state
                    if (doubleExpandedPositions.contains(position)) {
                        doubleExpandedPositions.remove(position)
                    } else {
                        doubleExpandedPositions.add(position)
                    }
                }

                notifyItemChanged(position)
            } else {
                clickRunnable = Runnable {
                    val wasExpanded = expandedPosition == position
                    expandedPosition = if (wasExpanded) null else position

                    notifyDataSetChanged()
                }
                clickHandler.postDelayed(clickRunnable!!, Constants.DOUBLE_CLICK_TIME_DELTA)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    @SuppressLint("NotifyDataSetChanged")
    private fun onDeleteClick(position: Int) {
        CrashHandler.Companion.logUserAction("Notes onDeleteClick")
        MaterialAlertDialogBuilder(context)
            .setTitle(getLocalizedString(R.string.confirm_delete_title))
            .setMessage(getLocalizedString(R.string.confirm_delete_message))
            .setPositiveButton(getLocalizedString(R.string.delete)) { _, _ ->
                messages.removeAt(position)
                if (expandedPosition == position) {
                    expandedPosition = null
                }
                doubleExpandedPositions.remove(position)
                notifyDataSetChanged()
                onMessageUpdated()
                CrashHandler.Companion.logUserAction("Note Deleted")
            }
            .setNegativeButton(getLocalizedString(R.string.cancel), null)
            .show()
    }

    private fun onShareClick(position: Int) {
        // Dismiss existing dialog if any
        shareUtils.shareDialog?.dismiss()

        CrashHandler.Companion.logUserAction("Notes onShareClick")
        shareUtils.showMaterialShareDialog(context, getLocalizedString(R.string.share_note), messages[position].text)
    }

    private fun onEditClick(position: Int) {
        CrashHandler.Companion.logUserAction("Notes onEditClick")
        showEditDialog(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showEditDialog(position: Int) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(getLocalizedString(R.string.edit_note))

        // Container layout for the dialog content
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 0)
        }

        // --- Message Label and EditText ---
        val messageLabel = TextView(context).apply {
            text = getLocalizedString(R.string.notes_settings_title)
        }
        val inputText = EditText(context).apply {
            setText(messages[position].text)
        }

        // --- Category Label and AutoCompleteTextView ---
        val categoryLabel = TextView(context).apply {
            text = getLocalizedString(R.string.category)
        }
        val categories = getLocalizedStringArray(R.array.categories)
        val categoryInput = AutoCompleteTextView(context).apply {
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, categories))
            setText(messages[position].category, false)
        }

        // --- Priority Label and Spinner ---
        val priorityLabel = TextView(context).apply {
            text = getLocalizedString(R.string.priority)
        }
        val prioritySpinner = Spinner(context)
        val priorities = getLocalizedStringArray(R.array.priorities)
        prioritySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, priorities)
        prioritySpinner.setSelection(priorities.indexOf(messages[position].priority))

        // --- Add all views to container ---
        container.apply {
            addView(messageLabel)
            addView(inputText)
            addView(categoryLabel)
            addView(categoryInput)
            addView(priorityLabel)
            addView(prioritySpinner)
        }

        // Set up dialog
        builder.setView(container)

        val timestamp = context.getCurrentTimestamp(prefs)

        builder.setPositiveButton(getLocalizedString(R.string.save)) { _, _ ->
            messages[position] = Message(
                inputText.text.toString(),
                timestamp,
                categoryInput.text.toString(),
                prioritySpinner.selectedItem.toString()
            )
            notifyDataSetChanged()
            onMessageUpdated()
            CrashHandler.Companion.logUserAction("Note Updated")
        }

        builder.setNegativeButton(getLocalizedString(R.string.cancel), null)
        builder.show()
    }

    fun getExpandedPosition(): Int? = expandedPosition

    @SuppressLint("NotifyDataSetChanged")
    fun collapseExpandedItem() {
        expandedPosition = null
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: MessageViewHolder) {
        super.onViewRecycled(holder)
        // Optional: auto-dismiss when item is recycled
        shareUtils.shareDialog?.dismiss()
    }
}