package com.github.codeworkscreativehub.mlauncher.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.github.codeworkscreativehub.fuzzywuzzy.FuzzyFinder
import com.github.codeworkscreativehub.fuzzywuzzy.FuzzyFinder.filterItems
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.ContactListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.AdapterAppDrawerBinding

class ContactDrawerAdapter(
    private val context: Context,
    private val gravity: Int,
    private val contactClickListener: (ContactListItem) -> Unit
) : RecyclerView.Adapter<ContactDrawerAdapter.ViewHolder>(), Filterable {

    private lateinit var prefs: Prefs
    private var contactFilter = createContactFilter()
    var contactsList: MutableList<ContactListItem> = mutableListOf()
    var contactFilteredList: MutableList<ContactListItem> = mutableListOf()
    private lateinit var binding: AdapterAppDrawerBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        binding = AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefs = Prefs(parent.context)
        val fontColor = prefs.appColor
        binding.appTitle.setTextColor(fontColor)

        binding.appTitle.textSize = prefs.appSize.toFloat()
        val padding: Int = prefs.textPaddingSize
        binding.appTitle.setPadding(0, padding, 0, padding)
        return ViewHolder(binding)
    }

    fun getItemAt(position: Int): ContactListItem? {
        return if (position in contactsList.indices) contactsList[position] else null
    }

    @SuppressLint("RecyclerView")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (contactFilteredList.isEmpty() || position !in contactFilteredList.indices) return

        val contactModel = contactFilteredList[holder.absoluteAdapterPosition]

        holder.bind(gravity, contactModel, contactClickListener)
    }

    override fun getItemCount(): Int = contactFilteredList.size

    override fun getFilter(): Filter = this.contactFilter

    private fun createContactFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                val searchChars = charSearch.toString().trim().lowercase()

                val filtered = filterItems(
                    itemsList = contactsList,
                    query = searchChars,
                    prefs = Prefs(context),
                    scoreProvider = { contact, q ->
                        FuzzyFinder.scoreContact(contact, q, Constants.MAX_FILTER_STRENGTH)
                    },
                    labelProvider = { contact -> contact.displayName },
                    loggerTag = "contactScore"
                )

                return FilterResults().apply { values = filtered }
            }

            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values is MutableList<*>) {
                    contactFilteredList = results.values as MutableList<ContactListItem>
                    notifyDataSetChanged()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setContactList(contactsList: MutableList<ContactListItem>) {
        this.contactsList = contactsList
        this.contactFilteredList = contactsList
        notifyDataSetChanged()
    }

    fun launchFirstInList() {
        if (contactFilteredList.isNotEmpty()) {
            contactClickListener(contactFilteredList[0])
        }
    }

    fun getFirstInList(): String? {
        return if (contactFilteredList.isNotEmpty()) {
            contactFilteredList[0].displayName   // or .displayName depending on your model
        } else {
            null
        }
    }


    class ViewHolder(
        itemView: AdapterAppDrawerBinding,
    ) : RecyclerView.ViewHolder(itemView.root) {
        private val appTitle: TextView = itemView.appTitle
        private val appTitleFrame: FrameLayout = itemView.appTitleFrame

        fun bind(
            contactLabelGravity: Int,
            contactItem: ContactListItem,
            contactClickListener: (ContactListItem) -> Unit,
        ) {
            appTitle.text = contactItem.displayName

            // set text gravity
            val params = appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = contactLabelGravity
            appTitle.layoutParams = params

            appTitleFrame.setOnClickListener {
                contactClickListener(contactItem)
            }

            val padding = 24
            return appTitle.updatePadding(left = padding, right = padding)
        }
    }
}
