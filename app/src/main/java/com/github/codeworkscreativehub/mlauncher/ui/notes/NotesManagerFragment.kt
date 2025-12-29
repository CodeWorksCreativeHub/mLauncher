package com.github.codeworkscreativehub.mlauncher.ui.notes

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.codeworkscreativehub.common.CrashHandler
import com.github.codeworkscreativehub.common.getCurrentTimestamp
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.getLocalizedStringArray
import com.github.codeworkscreativehub.common.isGestureNavigationEnabled
import com.github.codeworkscreativehub.common.share.ShareUtils
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Message
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.FragmentNotesManagerBinding
import com.github.codeworkscreativehub.mlauncher.helper.sortMessages
import com.github.codeworkscreativehub.mlauncher.helper.utils.messages
import com.github.codeworkscreativehub.mlauncher.ui.BaseFragment
import com.github.codeworkscreativehub.mlauncher.ui.adapter.NotesManagerAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NotesManagerFragment : BaseFragment() {
    private lateinit var prefs: Prefs
    private lateinit var shareUtils: ShareUtils
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentNotesManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotesManagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotesManagerBinding.inflate(inflater, container, false)
        prefs = Prefs(requireContext())
        shareUtils = ShareUtils(requireContext(), requireActivity())
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val expanded = adapter.getExpandedPosition()
                    if (expanded != null) {
                        adapter.collapseExpandedItem()
                    } else {
                        // Back press not handled here, pass it up
                        remove() // Detach this callback
                        requireActivity().onBackPressedDispatcher.onBackPressed() // Forward the back press
                    }
                }
            }
        )

        messages.clear() // 👈 CLEAR FIRST to avoid duplication
        messages.addAll(prefs.loadMessages())
        updateEmptyHintVisibility()
        adapter = NotesManagerAdapter(requireContext(), requireActivity(), messages) {
            prefs.saveMessages(messages)
        }

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        viewModel.ismlauncherDefault()

        val categoryAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, getLocalizedStringArray(R.array.categories))
        val priorityAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, getLocalizedStringArray(R.array.priorities))


        binding.apply {
            taskMessengerLayout.setBackgroundColor(prefs.notesBackgroundColor)
            messageLayout.setBackgroundColor(prefs.notesBackgroundColor)
            listEmptyHint.setTextColor(prefs.bubbleMessageTextColor)

            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter

            inputMessage.apply {
                hint = getLocalizedString(R.string.message_hint)
                setTextColor(prefs.inputMessageColor)
                setHintTextColor(prefs.inputMessageColor)
                backgroundTintList = ColorStateList.valueOf(prefs.inputMessageHintColor)
            }

            categoryLabel.apply {
                hint = getLocalizedString(R.string.category)
                defaultHintTextColor = ColorStateList.valueOf(prefs.inputMessageHintColor)
            }
            categoryDropdown.apply {
                setTextColor(prefs.inputMessageColor)
                setHintTextColor(prefs.inputMessageHintColor)
                backgroundTintList = ColorStateList.valueOf(prefs.inputMessageHintColor)
            }


            priorityLabel.apply {
                hint = getLocalizedString(R.string.priority)
                defaultHintTextColor = ColorStateList.valueOf(prefs.inputMessageHintColor)
            }
            priorityDropdown.apply {
                setTextColor(prefs.inputMessageColor)
                setHintTextColor(prefs.inputMessageHintColor)
                backgroundTintList = ColorStateList.valueOf(prefs.inputMessageHintColor)
            }

            binding.categoryDropdown.setAdapter(categoryAdapter)
            binding.priorityDropdown.setAdapter(priorityAdapter)

            // Post to ensure the views are laid out and have their measurements available
            inputMessage.post {
                // Get the width of inputMessage
                val newWidth = inputMessage.width / 2

                // Set the width for categoryDropdown and priorityDropdown
                categoryDropdown.layoutParams = categoryDropdown.layoutParams.apply {
                    width = newWidth
                }
                priorityDropdown.layoutParams = priorityDropdown.layoutParams.apply {
                    width = newWidth
                }
            }

            val (categorySettings, prioritySettings) = prefs.loadSettings()
            listOf(
                categorySettings to categoryDropdown,
                prioritySettings to priorityDropdown
            ).forEach { (value, dropdown) ->
                if (value != "None") dropdown.setText(value, false)
            }

            binding.priorityDropdown.apply {
                keyListener = null // makes it non-editable
                isCursorVisible = false
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener {
                    val options = getLocalizedStringArray(R.array.priorities)
                    val popup = MaterialAlertDialogBuilder(requireContext())
                        .setItems(options) { _, which ->
                            binding.priorityDropdown.setText(options[which], false)
                        }
                        .create()

                    popup.show()
                }
            }

            // 🔥 Keyboard height detection and bottomSpacer expansion
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

                val spacerParams = bottomSpacer.layoutParams

                if (imeVisible) {
                    spacerParams.height = imeHeight
                    bottomSpacer.layoutParams = spacerParams
                    bottomSpacer.isVisible = true
                } else {
                    if (!isGestureNavigationEnabled(requireContext())) {
                        spacerParams.height = resources.getDimensionPixelSize(R.dimen.default_bottom_spacer)
                        bottomSpacer.layoutParams = spacerParams
                        bottomSpacer.isVisible = true
                    } else {
                        bottomSpacer.isVisible = false
                    }
                }
                insets
            }

            sendButton.setOnClickListener {
                val messageText = inputMessage.text?.toString() ?: ""
                if (messageText.isNotBlank()) {
                    val timestamp = requireContext().getCurrentTimestamp(prefs)
                    val category = categoryDropdown.text.ifBlank { "None" }
                    val priority = priorityDropdown.text.ifBlank { "None" }

                    val newMessage = Message(messageText, timestamp, category.toString(), priority.toString())

                    messages.add(newMessage)

                    // ✅ Sort the list after adding
                    val sortedMessages = sortMessages(messages)

                    // ✅ Update internal list and notify adapter
                    messages.clear()
                    messages.addAll(sortedMessages)
                    adapter.notifyDataSetChanged() // full refresh due to possible reorder

                    prefs.saveMessages(messages)
                    prefs.saveSettings(category.toString(), priority.toString())

                    inputMessage.text?.clear()
                    updateEmptyHintVisibility()

                    Handler(Looper.getMainLooper()).postDelayed({
                        hideButtons()
                    }, 500)
                }
                CrashHandler.logUserAction("Notes sendButton Clicked")
            }
        }
    }

    private fun updateEmptyHintVisibility() {
        binding.listEmptyHint.isVisible = messages.isEmpty()
    }

    private fun hideButtons() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()

        for (i in first..last) {
            val holder = binding.recyclerView.findViewHolderForAdapterPosition(i)
            if (holder is NotesManagerAdapter.MessageViewHolder) {
                holder.actionButtonsLayout.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        shareUtils.shareDialog?.dismiss()
        super.onDestroyView()
        _binding = null
    }
}