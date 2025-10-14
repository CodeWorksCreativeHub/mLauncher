/**
 * Prepare the data for the app drawer, which is the list of all the installed applications.
 */

package com.github.droidworksstudio.mlauncher.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.biometric.BiometricPrompt
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.droidworksstudio.common.AppLogger
import com.github.droidworksstudio.common.getLocalizedString
import com.github.droidworksstudio.common.isSystemApp
import com.github.droidworksstudio.common.showKeyboard
import com.github.droidworksstudio.fuzzywuzzy.FuzzyFinder
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.data.AppListItem
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Constants.AppDrawerFlag
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.databinding.AdapterAppDrawerBinding
import com.github.droidworksstudio.mlauncher.helper.IconCacheTarget
import com.github.droidworksstudio.mlauncher.helper.IconPackHelper.getSafeAppIcon
import com.github.droidworksstudio.mlauncher.helper.dp2px
import com.github.droidworksstudio.mlauncher.helper.getSystemIcons
import com.github.droidworksstudio.mlauncher.helper.utils.BiometricHelper
import com.github.droidworksstudio.mlauncher.helper.utils.visibleHideLayouts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

class AppDrawerAdapter(
    private val context: Context,
    private val fragment: Fragment,
    internal var flag: AppDrawerFlag,
    private val gravity: Int,
    private val appClickListener: (AppListItem) -> Unit,
    private val appDeleteListener: (AppListItem) -> Unit,
    private val appRenameListener: (String, String) -> Unit,
    private val appTagListener: (String, String, UserHandle) -> Unit,
    private val appHideListener: (AppDrawerFlag, AppListItem) -> Unit,
    private val appInfoListener: (AppListItem) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

    private lateinit var prefs: Prefs
    private var appFilter = createAppFilter()
    var appsList: MutableList<AppListItem> = mutableListOf()
    var appFilteredList: MutableList<AppListItem> = mutableListOf()
    private lateinit var binding: AdapterAppDrawerBinding
    private lateinit var biometricHelper: BiometricHelper

    // Add icon cache
    private val iconCache = ConcurrentHashMap<String, Drawable?>()
    private val iconLoadingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isBangSearch = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        binding = AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefs = Prefs(parent.context)
        biometricHelper = BiometricHelper(fragment.requireActivity())
        val fontColor = prefs.appColor
        binding.appTitle.setTextColor(fontColor)

        binding.appTitle.textSize = prefs.appSize.toFloat()
        val padding: Int = prefs.textPaddingSize
        binding.appTitle.setPadding(0, padding, 0, padding)
        return ViewHolder(binding)
    }

    fun getItemAt(position: Int): AppListItem? {
        return if (position in appsList.indices) appsList[position] else null
    }

    @SuppressLint("RecyclerView")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (appFilteredList.isEmpty() || position !in appFilteredList.indices) {
            AppLogger.d("AppListDebug", "⚠️ onBindViewHolder called but appFilteredList is empty or position out of bounds")
            return
        }

        val appModel = appFilteredList[holder.absoluteAdapterPosition]
        AppLogger.d("AppListDebug", "🔧 Binding position=$position, label=${appModel.label}, package=${appModel.activityPackage}")

        // Pass icon cache and loading scope to bind
        holder.bind(flag, gravity, appModel, appClickListener, appInfoListener, appDeleteListener, iconCache, iconLoadingScope, prefs)

        holder.appHide.setOnClickListener {
            AppLogger.d("AppListDebug", "❌ Hide clicked for ${appModel.label} (${appModel.activityPackage})")

            appFilteredList.removeAt(holder.absoluteAdapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.absoluteAdapterPosition)

            AppLogger.d("AppListDebug", "📤 notifyItemRemoved at ${holder.absoluteAdapterPosition}")
            appHideListener(flag, appModel)
        }

        holder.appLock.setOnClickListener {
            val appName = appModel.activityPackage
            val currentLockedApps = prefs.lockedApps

            if (currentLockedApps.contains(appName)) {
                biometricHelper.startBiometricAuth(appModel, object : BiometricHelper.CallbackApp {
                    override fun onAuthenticationSucceeded(appListItem: AppListItem) {
                        AppLogger.d("AppListDebug", "🔓 Auth succeeded for $appName - unlocking")
                        holder.appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock_off, 0, 0)
                        holder.appLock.text = getLocalizedString(R.string.lock)
                        currentLockedApps.remove(appName)
                        prefs.lockedApps = currentLockedApps
                        AppLogger.d("AppListDebug", "🔐 Updated lockedApps: $currentLockedApps")
                    }

                    override fun onAuthenticationFailed() {
                        AppLogger.e("Authentication", getLocalizedString(R.string.text_authentication_failed))
                    }

                    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                        val msg = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> getLocalizedString(R.string.text_authentication_cancel)
                            else -> getLocalizedString(R.string.text_authentication_error).format(errorMessage, errorCode)
                        }
                        AppLogger.e("Authentication", msg)
                    }
                })
            } else {
                AppLogger.d("AppListDebug", "🔒 Locking $appName")
                holder.appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                holder.appLock.text = getLocalizedString(R.string.unlock)
                currentLockedApps.add(appName)
            }

            // Update the lockedApps value (save the updated set back to prefs)
            prefs.lockedApps = currentLockedApps
            AppLogger.d("lockedApps", prefs.lockedApps.toString())
        }

        holder.appSaveRename.setOnClickListener {
            val name = holder.appRenameEdit.text.toString().trim()
            AppLogger.d("AppListDebug", "✏️ Renaming ${appModel.activityPackage} to $name")
            appModel.customLabel = name
            notifyItemChanged(holder.absoluteAdapterPosition)
            AppLogger.d("AppListDebug", "🔁 notifyItemChanged at ${holder.absoluteAdapterPosition}")
            appRenameListener(appModel.activityPackage, appModel.customLabel)
        }

        holder.appSaveTag.setOnClickListener {
            val name = holder.appTagEdit.text.toString().trim()
            AppLogger.d("AppListDebug", "✏️ Tagging ${appModel.activityPackage} to $name")
            appModel.customTag = name
            notifyItemChanged(holder.absoluteAdapterPosition)
            AppLogger.d("AppListDebug", "🔁 notifyItemChanged at ${holder.absoluteAdapterPosition}")
            appTagListener(appModel.activityPackage, appModel.customTag, appModel.user)
        }

        autoLaunch(position)
    }

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = listOf("#").any { prefix -> charSearch?.startsWith(prefix) == true }
                prefs = Prefs(context)

                val searchChars = charSearch.toString().trim().lowercase()
                val filteredApps: MutableList<AppListItem>

                val isTagSearch = searchChars.startsWith("#")
                val query = if (isTagSearch) searchChars.substringAfter("#") else searchChars
                val normalizeField: (AppListItem) -> String = { app -> if (isTagSearch) normalize(app.tag) else normalize(app.label) }

                // Scoring logic
                val scoredApps: Map<AppListItem, Int> = if (prefs.enableFilterStrength) {
                    appsList.associateWith { app ->
                        if (isTagSearch) {
                            FuzzyFinder.scoreString(normalize(app.tag), query, Constants.MAX_FILTER_STRENGTH)
                        } else {
                            FuzzyFinder.scoreApp(app, query, Constants.MAX_FILTER_STRENGTH)
                        }
                    }
                } else {
                    emptyMap()
                }

                filteredApps = if (searchChars.isEmpty()) {
                    appsList.toMutableList()
                } else {
                    val filtered = if (prefs.enableFilterStrength) {
                        // Filter using scores
                        scoredApps.filter { (app, score) ->
                            (prefs.searchFromStart && normalizeField(app).startsWith(query) ||
                                    !prefs.searchFromStart && normalizeField(app).contains(query))
                                    && score > prefs.filterStrength
                        }.map { it.key }
                    } else {
                        // Filter without scores
                        appsList.filter { app ->
                            if (prefs.searchFromStart) {
                                normalizeField(app).startsWith(query)
                            } else {
                                FuzzyFinder.isMatch(normalizeField(app), query)
                            }
                        }
                    }

                    filtered.toMutableList()
                }

                AppLogger.d("searchQuery", query)

                val filterResults = FilterResults()
                filterResults.values = filteredApps
                return filterResults
            }

            fun normalize(input: String): String {
                // Normalize to NFC to keep composed characters (é stays é, not e + ´)
                val temp = Normalizer.normalize(input, Normalizer.Form.NFC)
                return temp
                    .lowercase()                  // lowercase Latin letters; other scripts unaffected
                    .filter { it.isLetterOrDigit() } // keep letters/digits from any language, including accented letters
            }


            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values is MutableList<*>) {
                    appFilteredList = results.values as MutableList<AppListItem>
                    notifyDataSetChanged()
                } else {
                    return
                }
            }
        }
    }

    private fun autoLaunch(position: Int) {
        val lastMatch = itemCount == 1
        val openApp = flag == AppDrawerFlag.LaunchApp
        val autoOpenApp = prefs.autoOpenApp
        if (lastMatch && openApp && autoOpenApp) {
            try { // Automatically open the app when there's only one search result
                if (isBangSearch.not()) appClickListener(appFilteredList[position])
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setAppList(appsList: MutableList<AppListItem>) {
        this.appsList = appsList
        this.appFilteredList = appsList
        notifyDataSetChanged()
    }

    fun launchFirstInList() {
        if (appFilteredList.isNotEmpty())
            appClickListener(appFilteredList[0])
    }

    fun getFirstInList(): String? {
        if (appFilteredList.isNotEmpty())
            return appFilteredList[0].label
        return null
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Optionally clear icon to avoid wrong icons on recycled views
        holder.clearIcon()
    }

    class ViewHolder(
        itemView: AdapterAppDrawerBinding
    ) : RecyclerView.ViewHolder(itemView.root) {
        val appHide: TextView = itemView.appHide
        val appLock: TextView = itemView.appLock
        val appRenameEdit: EditText = itemView.appRenameEdit
        val appSaveRename: TextView = itemView.appSaveRename
        val appTagEdit: EditText = itemView.appTagEdit
        val appSaveTag: TextView = itemView.appSaveTag

        private val appHideLayout: LinearLayout = itemView.appHideLayout
        private val appRenameLayout: LinearLayout = itemView.appRenameLayout
        private val appTagLayout: LinearLayout = itemView.appTagLayout
        private val appRename: TextView = itemView.appRename
        private val appTag: TextView = itemView.appTag
        private val appPin: TextView = itemView.appPin
        private val appTitle: TextView = itemView.appTitle
        private val appTitleFrame: FrameLayout = itemView.appTitleFrame
        private val appClose: TextView = itemView.appClose
        private val appInfo: TextView = itemView.appInfo
        private val appDelete: TextView = itemView.appDelete

        @SuppressLint("RtlHardcoded", "NewApi")
        fun bind(
            flag: AppDrawerFlag,
            appLabelGravity: Int,
            appListItem: AppListItem,
            appClickListener: (AppListItem) -> Unit,
            appInfoListener: (AppListItem) -> Unit,
            appDeleteListener: (AppListItem) -> Unit,
            iconCache: ConcurrentHashMap<String, Drawable?>,
            iconLoadingScope: CoroutineScope,
            prefs: Prefs
        ) = with(itemView) {
            val contextMenuFlags = prefs.getMenuFlags("CONTEXT_MENU_FLAGS", "0011111")
            appHideLayout.isVisible = false
            appRenameLayout.isVisible = false
            appTagLayout.isVisible = false

            appHide.apply {
                isVisible = contextMenuFlags[2]
                // set show/hide icon
                if (flag == AppDrawerFlag.HiddenApps) {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.visibility, 0, 0)
                    text = getLocalizedString(R.string.show)
                } else {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.visibility_off, 0, 0)
                    text = getLocalizedString(R.string.hide)
                }
            }

            val appName = appListItem.activityPackage
            // Access the current locked apps set
            val currentLockedApps = prefs.lockedApps

            appLock.apply {
                isVisible = contextMenuFlags[1]
                if (currentLockedApps.contains(appName)) {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                    text = getLocalizedString(R.string.unlock)
                } else {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock_off, 0, 0)
                    text = getLocalizedString(R.string.lock)
                }
            }

            val currentPinnedApps = prefs.pinnedApps

            appPin.apply {
                isVisible = contextMenuFlags[0]
                if (currentPinnedApps.contains(appName)) {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.pin, 0, 0)
                    text = getLocalizedString(R.string.unpin)
                } else {
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.pin_off, 0, 0)
                    text = getLocalizedString(R.string.pin)
                }
            }

            appRename.apply {
                isVisible = contextMenuFlags[3]
                setOnClickListener {
                    if (appListItem.activityPackage.isNotEmpty()) {
                        appRenameEdit.hint = appListItem.activityLabel
                        appRenameLayout.isVisible = true
                        appHideLayout.isVisible = false
                        appRenameEdit.showKeyboard()
                        appRenameEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                    }
                }
            }

            appTag.apply {
                isVisible = contextMenuFlags[4]
                setOnClickListener {
                    if (appListItem.activityPackage.isNotEmpty()) {
                        appTagEdit.hint = appListItem.activityLabel
                        appTagLayout.isVisible = true
                        appHideLayout.isVisible = false
                        appTagEdit.showKeyboard()
                        appTagEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                    }
                }
            }

            appRenameEdit.apply {
                addTextChangedListener(object : TextWatcher {

                    override fun afterTextChanged(s: Editable) {}

                    override fun beforeTextChanged(
                        s: CharSequence, start: Int,
                        count: Int, after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence, start: Int,
                        before: Int, count: Int
                    ) {
                        if (appRenameEdit.text.isEmpty()) {
                            appSaveRename.text = getLocalizedString(R.string.reset)
                        } else if (appRenameEdit.text.toString() == appListItem.customLabel) {
                            appSaveRename.text = getLocalizedString(R.string.cancel)
                        } else {
                            appSaveRename.text = getLocalizedString(R.string.rename)
                        }
                    }
                })
                // set current name as default text in EditText
                text = Editable.Factory.getInstance().newEditable(appListItem.label)
            }

            appTagEdit.apply {
                addTextChangedListener(object : TextWatcher {

                    override fun afterTextChanged(s: Editable) {}

                    override fun beforeTextChanged(
                        s: CharSequence, start: Int,
                        count: Int, after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence, start: Int,
                        before: Int, count: Int
                    ) {
                        if (appTagEdit.text.toString() == appListItem.customTag) {
                            appSaveTag.text = getLocalizedString(R.string.cancel)
                        } else {
                            appSaveTag.text = getLocalizedString(R.string.tag)
                        }
                    }
                })
                // set current name as default text in EditText
                text = Editable.Factory.getInstance().newEditable(appListItem.customTag)
            }

            appTitle.text = appListItem.label

            // set text gravity
            val params = appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = appLabelGravity
            appTitle.layoutParams = params

            // add icon next to app name to indicate that this app is installed on another profile
            val packageName = appListItem.activityPackage

            // Use a placeholder icon
            val placeholderIcon = AppCompatResources.getDrawable(context, R.drawable.ic_default_app)

            // Add work profile or private space icon if needed
            if (packageName.isNotBlank() && prefs.iconPackAppList != Constants.IconPacks.Disabled) {
                // Try to get from cache first
                val cachedIcon = iconCache[packageName]
                if (cachedIcon != null) {
                    // Use cached icon
                    setAppTitleIcon(appTitle, cachedIcon, prefs)
                } else {
                    // Set placeholder immediately
                    setAppTitleIcon(appTitle, placeholderIcon, prefs)
                    // Load icon asynchronously
                    iconLoadingScope.launch {
                        val icon = withContext(Dispatchers.IO) {
                            // Get icon as before
                            val iconPackPackage = prefs.customIconPackAppList
                            val iconPackage = IconCacheTarget.APP_LIST
                            val nonNullDrawable: Drawable = getSafeAppIcon(
                                context = context,
                                packageName = packageName,
                                useIconPack = (iconPackPackage.isNotEmpty() && prefs.iconPackAppList == Constants.IconPacks.Custom),
                                iconPackTarget = iconPackage
                            )
                            getSystemIcons(
                                context,
                                prefs,
                                iconPackage,
                                nonNullDrawable
                            ) ?: nonNullDrawable
                        }
                        iconCache[packageName] = icon
                        // Only update if still bound to this app
                        if (appTitle.text == appListItem.label) {
                            setAppTitleIcon(appTitle, icon, prefs)
                        }
                    }
                }
            }


            val padding = dp2px(resources, 24)
            appTitle.updatePadding(left = padding, right = padding)

            val sidebarContainer = (context as Activity).findViewById<View>(R.id.sidebar_container)

            appTitleFrame.apply {
                setOnClickListener {
                    appClickListener(appListItem)
                }
                setOnLongClickListener {
                    val openApp =
                        flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.HiddenApps
                    if (openApp) {
                        try {
                            appDelete.alpha =
                                if (context.isSystemApp(appListItem.activityPackage)) 0.3f else 1.0f
                            appHideLayout.isVisible = true
                            sidebarContainer.isVisible = false
                            visibleHideLayouts.add(absoluteAdapterPosition)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
            }

            appInfo.apply {
                isVisible = contextMenuFlags[5]
                setOnClickListener {
                    appInfoListener(appListItem)
                }
            }

            appDelete.apply {
                isVisible = contextMenuFlags[6]
                setOnClickListener {
                    appDeleteListener(appListItem)
                }
            }

            appClose.apply {
                setOnClickListener {
                    appHideLayout.isVisible = false
                    visibleHideLayouts.remove(absoluteAdapterPosition)
                    if (visibleHideLayouts.isEmpty()) {
                        sidebarContainer.isVisible = prefs.showAZSidebar
                    }
                }
            }

            appPin.apply {
                setOnClickListener {
                    val appName = appListItem.activityPackage
                    val updatedPinnedApps = prefs.pinnedApps.toMutableSet()

                    val isPinned = updatedPinnedApps.contains(appName)
                    AppLogger.d("AppListDebug", if (isPinned) "📌 Unpinning $appName" else "📌 Pinning $appName")

                    if (isPinned) {
                        updatedPinnedApps.remove(appName)
                        appPin.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.pin_off, 0, 0)
                        appPin.text = getLocalizedString(R.string.pin)
                    } else {
                        updatedPinnedApps.add(appName)
                        appPin.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.pin, 0, 0)
                        appPin.text = getLocalizedString(R.string.unpin)
                    }

                    prefs.pinnedApps = updatedPinnedApps.toSet()
                    AppLogger.d("AppListDebug", "✅ Updated pinnedApps: ${prefs.pinnedApps}")
                }
            }
        }

        // Helper to set icon on appTitle with correct size and alignment
        private fun setAppTitleIcon(appTitle: TextView, icon: Drawable?, prefs: Prefs) {
            if (icon == null) {
                appTitle.setCompoundDrawables(null, null, null, null)
                return
            }
            val iconSize = (prefs.appSize * 1.4).toInt()
            val iconPadding = (iconSize / 1.2).toInt()
            icon.setBounds(
                0,
                0,
                ((iconSize * 1.6).toInt()),
                ((iconSize * 1.6).toInt())
            )
            when (prefs.drawerAlignment) {
                Constants.Gravity.Left -> {
                    appTitle.setCompoundDrawables(icon, null, null, null)
                    appTitle.compoundDrawablePadding = iconPadding
                }

                Constants.Gravity.Right -> {
                    appTitle.setCompoundDrawables(null, null, icon, null)
                    appTitle.compoundDrawablePadding = iconPadding
                }

                else -> appTitle.setCompoundDrawables(null, null, null, null)
            }
        }

        // Clear icon when view is recycled
        fun clearIcon() {
            appTitle.setCompoundDrawables(null, null, null, null)
        }
    }
}
