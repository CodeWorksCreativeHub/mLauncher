package com.github.droidworksstudio.mlauncher.ui.adapter

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
import com.github.droidworksstudio.common.AppLogger
import com.github.droidworksstudio.fuzzywuzzy.FuzzyFinder
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.ContactListItem
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.databinding.AdapterAppDrawerBinding
import java.text.Normalizer

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
                prefs = Prefs(context)

                val searchChars = charSearch.toString().trim().lowercase()
                val filteredContacts: MutableList<ContactListItem>

                // Normalization function for contacts
                val normalizeField: (ContactListItem) -> String = { contact -> normalize(contact.displayName) }

                // Scoring logic
                val scoredContacts: Map<ContactListItem, Int> = if (prefs.enableFilterStrength) {
                    contactsList.associateWith { contact ->
                        FuzzyFinder.scoreContact(contact, searchChars, Constants.MAX_FILTER_STRENGTH)
                    }
                } else {
                    emptyMap()
                }

                filteredContacts = if (searchChars.isEmpty()) {
                    contactsList.toMutableList()
                } else {
                    if (prefs.enableFilterStrength) {
                        // Filter using scores
                        scoredContacts.filter { (contact, score) ->
                            (prefs.searchFromStart && normalizeField(contact).startsWith(searchChars)
                                    || !prefs.searchFromStart && normalizeField(contact).contains(searchChars))
                                    && score > prefs.filterStrength
                        }.map { it.key }.toMutableList()
                    } else {
                        // Filter without scores
                        contactsList.filter { contact ->
                            if (prefs.searchFromStart) {
                                normalizeField(contact).startsWith(searchChars)
                            } else {
                                FuzzyFinder.isMatch(normalizeField(contact), searchChars)
                            }
                        }.toMutableList()
                    }
                }

                AppLogger.d("searchQuery", searchChars)

                val filterResults = FilterResults()
                filterResults.values = filteredContacts
                return filterResults
            }

            fun normalize(input: String): String {
                val temp = Normalizer.normalize(input, Normalizer.Form.NFC)
                return temp
                    .lowercase()
                    .filter { it.isLetterOrDigit() }
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
        ) = with(itemView) {

            appTitle.text = contactItem.displayName

            // set text gravity
            val params = appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = contactLabelGravity
            appTitle.layoutParams = params

            appTitleFrame.setOnClickListener {
                contactClickListener(contactItem)
            }

            val padding = 24
            appTitle.updatePadding(left = padding, right = padding)
        }
    }
}
