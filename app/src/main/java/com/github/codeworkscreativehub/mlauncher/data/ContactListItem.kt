package com.github.codeworkscreativehub.mlauncher.data

import java.text.Collator

val contactCollator: Collator = Collator.getInstance()

/**
 * Represents a contact item for lists such as:
 * 1. Contact drawer
 *
 * @property displayName
 * The name of the contact (as stored in the Contacts provider).
 *
 * @property phoneNumber
 * The primary phone number (can be empty if none).
 *
 * @property email
 * The primary email address (optional).
 *
 * @property category
 * Used to separate contacts into groups (e.g., FAVORITE, RECENT, REGULAR).
 */
data class ContactListItem(
    val displayName: String,
    val phoneNumber: String,
    val email: String,
    var category: ContactCategory = ContactCategory.REGULAR
) : Comparable<ContactListItem> {

    /** Speed up sort and search */
    private val collationKey = contactCollator.getCollationKey(displayName)

    override fun compareTo(other: ContactListItem): Int =
        collationKey.compareTo(other.collationKey)
}

/**
 * Categories to classify contacts in the list
 */
enum class ContactCategory {
    FAVORITE, REGULAR
}
