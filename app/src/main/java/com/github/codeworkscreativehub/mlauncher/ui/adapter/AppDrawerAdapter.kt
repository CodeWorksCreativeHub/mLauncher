/**
 * Prepare the data for the app drawer, which is the list of all the installed applications.
 */

package com.github.codeworkscreativehub.mlauncher.ui.adapter

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
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.isSystemApp
import com.github.codeworkscreativehub.common.showKeyboard
import com.github.codeworkscreativehub.fuzzywuzzy.FuzzyFinder
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Constants.AppDrawerFlag
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.AdapterAppDrawerBinding
import com.github.codeworkscreativehub.mlauncher.helper.IconCacheTarget
import com.github.codeworkscreativehub.mlauncher.helper.IconPackHelper.getSafeAppIcon
import com.github.codeworkscreativehub.mlauncher.helper.dp2px
import com.github.codeworkscreativehub.mlauncher.helper.getSystemIcons
import com.github.codeworkscreativehub.mlauncher.helper.utils.BiometricHelper
import com.github.codeworkscreativehub.mlauncher.helper.utils.visibleHideLayouts
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
    private val iconCache = ConcurrentHashMap<String, Drawable>()
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

                if (query.isNotEmpty()) AppLogger.d("searchQuery", query)

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
            iconCache: ConcurrentHashMap<String, Drawable>,
            iconLoadingScope: CoroutineScope,
            prefs: Prefs
        ) = with(itemView) {

            val contextMenuFlags = prefs.getMenuFlags("CONTEXT_MENU_FLAGS", "0011111")

            // ----------------------------
            // 1️⃣ Hide optional layouts
            appHideLayout.isVisible = false
            appRenameLayout.isVisible = false
            appTagLayout.isVisible = false

            val packageName = appListItem.activityPackage

            // ----------------------------
            // 2️⃣ Precompute lock/pin/hide state
            val isLocked = prefs.lockedApps.contains(packageName)
            val isPinned = prefs.pinnedApps.contains(packageName)
            val isHidden = (flag == AppDrawerFlag.HiddenApps)

            // ----------------------------
            // 3️⃣ Setup lock/pin/hide buttons
            appLock.apply {
                isVisible = contextMenuFlags[1]
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (isLocked) R.drawable.padlock else R.drawable.padlock_off,
                    0,
                    0
                )
                text = if (isLocked) getLocalizedString(R.string.unlock) else getLocalizedString(R.string.lock)
            }

            appPin.apply {
                isVisible = contextMenuFlags[0]
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (isPinned) R.drawable.pin_off else R.drawable.pin,
                    0,
                    0
                )
                text = if (isPinned) getLocalizedString(R.string.unpin) else getLocalizedString(R.string.pin)
            }

            appHide.apply {
                isVisible = contextMenuFlags[2]
                setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    if (isHidden) R.drawable.visibility else R.drawable.visibility_off,
                    0,
                    0
                )
                text = if (isHidden) getLocalizedString(R.string.show) else getLocalizedString(R.string.hide)
            }

            // ----------------------------
            // 4️⃣ Setup rename/tag layouts
            appRename.apply {
                isVisible = contextMenuFlags[3]
                setOnClickListener {
                    appRenameEdit.hint = appListItem.activityLabel
                    appRenameLayout.isVisible = true
                    appHideLayout.isVisible = false
                    appRenameEdit.showKeyboard()
                    appRenameEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }

            appTag.apply {
                isVisible = contextMenuFlags[4]
                setOnClickListener {
                    appTagEdit.hint = appListItem.activityLabel
                    appTagLayout.isVisible = true
                    appHideLayout.isVisible = false
                    appTagEdit.showKeyboard()
                    appTagEdit.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }

            appRenameEdit.apply {
                text = Editable.Factory.getInstance().newEditable(appListItem.label)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable) {}
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                        appSaveRename.text = when {
                            text.isEmpty() -> getLocalizedString(R.string.reset)
                            text.toString() == appListItem.customLabel -> getLocalizedString(R.string.cancel)
                            else -> getLocalizedString(R.string.rename)
                        }
                    }
                })
            }

            appTagEdit.apply {
                text = Editable.Factory.getInstance().newEditable(appListItem.customTag)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable) {}
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                        appSaveTag.text = if (text.toString() == appListItem.customTag) getLocalizedString(R.string.cancel)
                        else getLocalizedString(R.string.tag)
                    }
                })
            }

            appClose.setOnClickListener {
                appHideLayout.isVisible = false
                visibleHideLayouts.remove(absoluteAdapterPosition)
                val sidebarContainer = (context as? Activity)?.findViewById<View>(R.id.sidebar_container)
                if (visibleHideLayouts.isEmpty()) sidebarContainer?.isVisible = prefs.showAZSidebar
            }

            // ----------------------------
            // 5️⃣ App title
            appTitle.text = appListItem.label
            val params = appTitle.layoutParams as FrameLayout.LayoutParams
            params.gravity = appLabelGravity
            appTitle.layoutParams = params
            val padding = dp2px(resources, 24)
            appTitle.updatePadding(left = padding, right = padding)

            // ----------------------------
            // 6️⃣ Icon loading off main thread
            val placeholderIcon = AppCompatResources.getDrawable(context, R.drawable.ic_default_app)
            val cachedIcon = iconCache[packageName]
            setAppTitleIcon(appTitle, cachedIcon ?: placeholderIcon, prefs)

            if (cachedIcon == null && packageName.isNotBlank() && prefs.iconPackAppList != Constants.IconPacks.Disabled) {
                iconLoadingScope.launch {
                    val icon = withContext(Dispatchers.IO) {
                        val nonNullDrawable: Drawable = getSafeAppIcon(
                            context = context,
                            packageName = packageName,
                            useIconPack = prefs.customIconPackAppList.isNotEmpty() &&
                                    prefs.iconPackAppList == Constants.IconPacks.Custom,
                            iconPackTarget = IconCacheTarget.APP_LIST
                        )
                        getSystemIcons(context, prefs, IconCacheTarget.APP_LIST, nonNullDrawable) ?: nonNullDrawable
                    }
                    iconCache[packageName] = icon
                    if (appTitle.text == appListItem.label) setAppTitleIcon(appTitle, icon, prefs)
                }
            }

            // ----------------------------
            // 7️⃣ Click listeners
            val sidebarContainer = (context as? Activity)?.findViewById<View>(R.id.sidebar_container)
            appTitleFrame.apply {
                setOnClickListener { appClickListener(appListItem) }
                setOnLongClickListener {
                    val openApp = flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.HiddenApps
                    if (openApp) {
                        try {
                            appDelete.alpha = if (context.isSystemApp(packageName)) 0.3f else 1f
                            appHideLayout.isVisible = true
                            sidebarContainer?.isVisible = false
                            visibleHideLayouts.add(absoluteAdapterPosition)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
            }

            appInfo.setOnClickListener { appInfoListener(appListItem) }
            appDelete.setOnClickListener { appDeleteListener(appListItem) }

            // ----------------------------
            // 8️⃣ Lock/Pin toggle actions
            appLock.setOnClickListener {
                val updated = prefs.lockedApps.toMutableSet()
                if (isLocked) updated.remove(packageName) else updated.add(packageName)
                prefs.lockedApps = updated
            }
            appPin.setOnClickListener {
                val updated = prefs.pinnedApps.toMutableSet()
                if (isPinned) updated.remove(packageName) else updated.add(packageName)
                prefs.pinnedApps = updated
            }
        }


        // Helper to set icon on appTitle with correct size and alignment
        private fun setAppTitleIcon(appTitle: TextView, icon: Drawable?, prefs: Prefs) {
            if (icon == null || prefs.iconPackAppList == Constants.IconPacks.Disabled) {
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
