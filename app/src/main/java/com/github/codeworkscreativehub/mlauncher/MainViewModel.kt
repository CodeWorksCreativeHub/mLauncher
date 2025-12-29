package com.github.codeworkscreativehub.mlauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.ContactsContract
import androidx.biometric.BiometricPrompt
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.common.CrashHandler
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.hideKeyboard
import com.github.codeworkscreativehub.common.showShortToast
import com.github.codeworkscreativehub.mlauncher.data.AppCategory
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Constants
import com.github.codeworkscreativehub.mlauncher.data.Constants.AppDrawerFlag
import com.github.codeworkscreativehub.mlauncher.data.ContactCategory
import com.github.codeworkscreativehub.mlauncher.data.ContactListItem
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.analytics.AppUsageMonitor
import com.github.codeworkscreativehub.mlauncher.helper.ismlauncherDefault
import com.github.codeworkscreativehub.mlauncher.helper.logActivitiesFromPackage
import com.github.codeworkscreativehub.mlauncher.helper.utils.BiometricHelper
import com.github.codeworkscreativehub.mlauncher.helper.utils.PrivateSpaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _appScrollMap = MutableLiveData<Map<String, Int>>()
    val appScrollMap: LiveData<Map<String, Int>> = _appScrollMap

    private val _contactScrollMap = MutableLiveData<Map<String, Int>>()
    val contactScrollMap: LiveData<Map<String, Int>> = _contactScrollMap

    private lateinit var biometricHelper: BiometricHelper

    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    // Cache files
    private val appsCacheFile = File(appContext.cacheDir, "apps_cache.json")
    private val contactsCacheFile = File(appContext.cacheDir, "contacts_cache.json")

    // in-memory caches for instant load
    private var appsMemoryCache: MutableList<AppListItem>? = null
    private var contactsMemoryCache: MutableList<ContactListItem>? = null

    // Ensure we don't trigger concurrent refreshes
    private val appsRefreshing = AtomicBoolean(false)
    private val contactsRefreshing = AtomicBoolean(false)

    // setup variables with initial values
    val firstOpen = MutableLiveData<Boolean>()

    val appList = MutableLiveData<List<AppListItem>?>()
    val contactList = MutableLiveData<List<ContactListItem>?>()
    val hiddenApps = MutableLiveData<List<AppListItem>?>()
    val homeAppsOrder = MutableLiveData<List<AppListItem>>()  // Store actual app items
    val launcherDefault = MutableLiveData<Boolean>()

    val showDate = MutableLiveData(prefs.showDate)
    val showClock = MutableLiveData(prefs.showClock)
    val showAlarm = MutableLiveData(prefs.showAlarm)
    val showDailyWord = MutableLiveData(prefs.showDailyWord)
    val clockAlignment = MutableLiveData(prefs.clockAlignment)
    val dateAlignment = MutableLiveData(prefs.dateAlignment)
    val alarmAlignment = MutableLiveData(prefs.alarmAlignment)
    val dailyWordAlignment = MutableLiveData(prefs.dailyWordAlignment)
    val homeAppsAlignment = MutableLiveData(Pair(prefs.homeAlignment, prefs.homeAlignmentBottom))
    val homeAppsNum = MutableLiveData(prefs.homeAppsNum)
    val homePagesNum = MutableLiveData(prefs.homePagesNum)
    val opacityNum = MutableLiveData(prefs.opacityNum)
    val filterStrength = MutableLiveData(prefs.filterStrength)
    val recentCounter = MutableLiveData(prefs.recentCounter)
    val customIconPackHome = MutableLiveData(prefs.customIconPackHome)
    val iconPackHome = MutableLiveData(prefs.iconPackHome)
    val customIconPackAppList = MutableLiveData(prefs.customIconPackAppList)
    val iconPackAppList = MutableLiveData(prefs.iconPackAppList)

    private val prefsNormal = prefs.prefsNormal
    private val pinnedAppsKey = prefs.pinnedAppsKey

    private val pinnedAppsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == pinnedAppsKey) {
            AppLogger.d("MainViewModel", "Pinned apps changed")
            // refresh in background, but keep cache immediate
            getAppList()
        }
    }

    // ContentObserver for contacts - invalidate cache on change
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            AppLogger.d("MainViewModel", "Contacts changed - invalidating cache")
            contactsMemoryCache = null
            // trigger background refresh
            getContactList()
        }
    }

    init {
        prefsNormal.registerOnSharedPreferenceChangeListener(pinnedAppsListener)

        // Register content observer for contacts to refresh cache only when changes occur
        try {
            appContext.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver
            )
        } catch (t: Throwable) {
            AppLogger.e("MainViewModel", "Failed to register contacts observer: ${t.message}", t)
        }

        // Fast immediate load from cache, then background refresh
        getAppList()
        getContactList()
    }

    fun selectedApp(fragment: Fragment, app: AppListItem, flag: AppDrawerFlag, n: Int = 0) {
        when (flag) {
            AppDrawerFlag.SetHomeApp -> prefs.setHomeAppModel(n, app)
            AppDrawerFlag.SetShortSwipeUp -> prefs.appShortSwipeUp = app
            AppDrawerFlag.SetShortSwipeDown -> prefs.appShortSwipeDown = app
            AppDrawerFlag.SetShortSwipeLeft -> prefs.appShortSwipeLeft = app
            AppDrawerFlag.SetShortSwipeRight -> prefs.appShortSwipeRight = app
            AppDrawerFlag.SetLongSwipeUp -> prefs.appLongSwipeUp = app
            AppDrawerFlag.SetLongSwipeDown -> prefs.appLongSwipeDown = app
            AppDrawerFlag.SetLongSwipeLeft -> prefs.appLongSwipeLeft = app
            AppDrawerFlag.SetLongSwipeRight -> prefs.appLongSwipeRight = app
            AppDrawerFlag.SetClickClock -> prefs.appClickClock = app
            AppDrawerFlag.SetAppUsage -> prefs.appClickUsage = app
            AppDrawerFlag.SetFloating -> prefs.appFloating = app
            AppDrawerFlag.SetClickDate -> prefs.appClickDate = app
            AppDrawerFlag.SetDoubleTap -> prefs.appDoubleTap = app
            AppDrawerFlag.LaunchApp, AppDrawerFlag.HiddenApps, AppDrawerFlag.PrivateApps -> launchApp(
                app,
                fragment
            )

            AppDrawerFlag.None -> {}
        }
    }

    /**
     * Call this when a contact is selected in the drawer
     */
    fun selectedContact(fragment: Fragment, contact: ContactListItem, n: Int = 0) {
        callContact(contact, fragment)

        // You can also perform additional logic here if needed
        // For example, updating a detail view, logging, or triggering actions
        AppLogger.d("MainViewModel", "Contact selected: ${contact.displayName}, index=$n")
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun setShowDate(visibility: Boolean) {
        showDate.value = visibility
    }

    fun setShowClock(visibility: Boolean) {
        showClock.value = visibility
    }

    fun setShowAlarm(visibility: Boolean) {
        showAlarm.value = visibility
    }

    fun setShowDailyWord(visibility: Boolean) {
        showDailyWord.value = visibility
    }

    fun setDefaultLauncher(visibility: Boolean) {
        val reverseValue = !visibility
        launcherDefault.value = reverseValue
    }

    fun launchApp(appListItem: AppListItem, fragment: Fragment) {
        biometricHelper = BiometricHelper(fragment.requireActivity())

        val packageName = appListItem.activityPackage
        val currentLockedApps = prefs.lockedApps

        logActivitiesFromPackage(appContext, packageName)

        if (currentLockedApps.contains(packageName)) {

            biometricHelper.startBiometricAuth(appListItem, object : BiometricHelper.CallbackApp {
                override fun onAuthenticationSucceeded(appListItem: AppListItem) {
                    if (fragment.isAdded) {
                        fragment.hideKeyboard()
                    }
                    launchUnlockedApp(appListItem)
                }

                override fun onAuthenticationFailed() {
                    AppLogger.e(
                        "Authentication",
                        getLocalizedString(R.string.text_authentication_failed)
                    )
                }

                override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> AppLogger.e(
                            "Authentication",
                            getLocalizedString(R.string.text_authentication_cancel)
                        )

                        else -> AppLogger.e(
                            "Authentication",
                            getLocalizedString(R.string.text_authentication_error).format(
                                errorMessage, errorCode
                            )
                        )
                    }
                }
            })
        } else {
            launchUnlockedApp(appListItem)
        }
    }

    fun callContact(contactItem: ContactListItem, fragment: Fragment) {
        val phoneNumber = contactItem.phoneNumber // Ensure ContactListItem has a phoneNumber property
        if (phoneNumber.isBlank()) {
            AppLogger.e("CallContact", "No phone number available for ${contactItem.displayName}")
            return
        }

        // Hide keyboard if fragment is attached
        if (fragment.isAdded) {
            fragment.hideKeyboard()
        }

        // Launch the dialer
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:$phoneNumber".toUri()
        }
        fragment.requireContext().startActivity(intent)
    }

    private fun launchUnlockedApp(appListItem: AppListItem) {
        val packageName = appListItem.activityPackage
        val userHandle = appListItem.user
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        if (activityInfo.isNotEmpty()) {
            val component = ComponentName(packageName, activityInfo.first().name)
            launchAppWithPermissionCheck(component, packageName, userHandle, launcher)
        } else {
            appContext.showShortToast("App not found")
        }
    }

    private fun launchAppWithPermissionCheck(
        component: ComponentName,
        packageName: String,
        userHandle: UserHandle,
        launcher: LauncherApps
    ) {
        val appUsageTracker = AppUsageMonitor.createInstance(appContext)

        fun tryLaunch(user: UserHandle): Boolean {
            return try {
                appUsageTracker.updateLastUsedTimestamp(packageName)
                launcher.startMainActivity(component, user, null, null)
                CrashHandler.logUserAction("${component.packageName} App Launched")
                true
            } catch (_: Exception) {
                false
            }
        }

        if (!tryLaunch(userHandle)) {
            if (!tryLaunch(Process.myUserHandle())) {
                appContext.showShortToast("Unable to launch app")
            }
        }
    }

    /**
     * Public entry: loads apps from cache instantly and refreshes in background.
     */
    fun getAppList(includeHiddenApps: Boolean = true, includeRecentApps: Boolean = true) {
        // Fast path: show memory cache
        appsMemoryCache?.let {
            appList.postValue(it)
        } ?: run {
            // try file cache
            loadAppsFromFileCache()?.let { cached ->
                appsMemoryCache = cached.toMutableList()
                appList.postValue(cached)
            }
        }

        // Background refresh (only one at a time)
        if (appsRefreshing.compareAndSet(false, true)) {
            viewModelScope.launch {
                try {
                    val fresh = getAppsList(appContext, includeRegularApps = true, includeHiddenApps, includeRecentApps)
                    appsMemoryCache = fresh
                    saveAppsToFileCache(fresh)
                    // publish on main
                    withContext(Dispatchers.Main) {
                        appList.value = fresh
                    }
                } finally {
                    appsRefreshing.set(false)
                }
            }
        }
    }

    /**
     * Public entry: loads contacts from cache instantly and refreshes in background.
     */
    fun getContactList(includeHiddenContacts: Boolean = true) {
        // Fast path: show memory cache
        contactsMemoryCache?.let {
            contactList.postValue(it)
        } ?: run {
            // try file cache
            loadContactsFromFileCache()?.let { cached ->
                contactsMemoryCache = cached.toMutableList()
                contactList.postValue(cached)
            }
        }

        // Background refresh (only one at a time)
        if (contactsRefreshing.compareAndSet(false, true)) {
            viewModelScope.launch {
                try {
                    val fresh = getContactsList(appContext, includeHiddenContacts)
                    contactsMemoryCache = fresh
                    saveContactsToFileCache(fresh)
                    withContext(Dispatchers.Main) {
                        contactList.value = fresh
                    }
                } finally {
                    contactsRefreshing.set(false)
                }
            }
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value =
                getAppsList(appContext, includeRegularApps = false, includeHiddenApps = true)
        }
    }

    fun ismlauncherDefault() {
        val isDefault = ismlauncherDefault(appContext)
        launcherDefault.value = !isDefault
    }

    fun resetDefaultLauncherApp(context: Context) {
        (context as MainActivity).setDefaultHomeScreen(context)
    }

    fun updateDrawerAlignment(gravity: Constants.Gravity) {
        prefs.drawerAlignment = gravity
    }

    fun updateDateAlignment(gravity: Constants.Gravity) {
        dateAlignment.value = gravity
    }

    fun updateClockAlignment(gravity: Constants.Gravity) {
        clockAlignment.value = gravity
    }

    fun updateAlarmAlignment(gravity: Constants.Gravity) {
        alarmAlignment.value = gravity
    }

    fun updateDailyWordAlignment(gravity: Constants.Gravity) {
        dailyWordAlignment.value = gravity
    }

    fun updateHomeAppsAlignment(gravity: Constants.Gravity, onBottom: Boolean) {
        homeAppsAlignment.value = Pair(gravity, onBottom)
    }

    fun updateAppOrder(fromPosition: Int, toPosition: Int) {
        val currentOrder = homeAppsOrder.value?.toMutableList() ?: return

        // Move the actual app object in the list
        val app = currentOrder.removeAt(fromPosition)
        currentOrder.add(toPosition, app)

        homeAppsOrder.postValue(currentOrder)
        saveAppOrder(currentOrder)  // Save new order in preferences
    }

    private fun saveAppOrder(order: List<AppListItem>) {
        order.forEachIndexed { index, app ->
            prefs.setHomeAppModel(index, app)  // Save app in its new order
        }
    }

    fun loadAppOrder() {
        val savedOrder =
            (0 until prefs.homeAppsNum).mapNotNull { prefs.getHomeAppModel(it) } // Ensure it doesn’t return null
        homeAppsOrder.postValue(savedOrder) // ✅ Now posts a valid list
    }

    // Clean up listener to prevent memory leaks
    override fun onCleared() {
        super.onCleared()
        prefsNormal.unregisterOnSharedPreferenceChangeListener(pinnedAppsListener)
        try {
            appContext.contentResolver.unregisterContentObserver(contactsObserver)
        } catch (t: Throwable) {
            AppLogger.e("MainViewModel", "Failed to unregister contacts observer: ${t.message}", t)
        }
    }

    suspend fun <T, R> buildList(
        items: List<T>,
        seenKey: MutableSet<String> = mutableSetOf(),
        scrollMapLiveData: MutableLiveData<Map<String, Int>>? = null,
        includeHidden: Boolean = false,
        getKey: (T) -> String,
        isHidden: (T) -> Boolean = { false },
        isPinned: (T) -> Boolean = { false },
        buildItem: (T) -> R,
        getLabel: (R) -> String,
        normalize: (String) -> String = { it.lowercase() },
    ): MutableList<R> = withContext(Dispatchers.IO) {

        val list = mutableListOf<R>()
        val pinnedStatusMap = mutableMapOf<R, Boolean>()
        val scrollMap = mutableMapOf<String, Int>()

        // Build the visible list AND pinned map together (this avoids mismatched sizes)
        items.forEach { raw ->
            val key = getKey(raw)

            // Skip duplicates
            if (!seenKey.add(key)) return@forEach

            // Skip hidden items unless included
            if (isHidden(raw) && !includeHidden) return@forEach

            // Build the final item
            val built = buildItem(raw)
            list.add(built)

            // Map pin state to the built item
            pinnedStatusMap[built] = isPinned(raw)
        }

        // Stable, safe sorting — no null comparisons
        list.sortWith(
            compareByDescending<R> { pinnedStatusMap[it] == true }
                .thenBy { normalize(getLabel(it)) }
        )

        // Build scroll index
        list.forEachIndexed { index, item ->
            val label = getLabel(item)
            val pinned = pinnedStatusMap[item] == true
            val key = if (pinned) "★" else label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            scrollMap.putIfAbsent(key, index)
        }

        scrollMapLiveData?.postValue(scrollMap)
        list
    }

    /**
     * Build app list on IO dispatcher. This function is suspend and safe to call
     * from a background thread, but it ensures all heavy operations happen on Dispatchers.IO.
     *
     * Features:
     * - Fetches recent apps and user profile apps in parallel.
     * - Filters hidden/pinned apps before creating AppListItem objects.
     * - Updates profile counters efficiently.
     * - Optimized for memory and CPU usage on devices with many apps.
     */
    suspend fun getAppsList(
        context: Context,
        includeRegularApps: Boolean = true,
        includeHiddenApps: Boolean = false,
        includeRecentApps: Boolean = true
    ): MutableList<AppListItem> = withContext(Dispatchers.IO) {

        val prefs = Prefs(context)
        val hiddenAppsSet = prefs.hiddenApps
        val pinnedPackages = prefs.pinnedApps.toSet()
        val seenAppKeys = mutableSetOf<String>()
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val profiles = userManager.userProfiles.toList()
        val privateManager = PrivateSpaceManager(context)

        fun appKey(pkg: String, cls: String, profileHash: Int) = "$pkg|$cls|$profileHash"
        fun isHidden(pkg: String, key: String) =
            listOf(pkg, key, "$pkg|${key.hashCode()}").any { it in hiddenAppsSet }

        // Lightweight intermediate storage
        data class RawApp(
            val pkg: String,
            val cls: String,
            val label: String,
            val user: UserHandle,
            val profileType: String,
            val category: AppCategory
        )

        val rawApps = mutableListOf<RawApp>()

        // 🔹 Recent apps
        if (prefs.recentAppsDisplayed && includeRecentApps) {
            runCatching {
                AppUsageMonitor.createInstance(context)
                    .getLastTenAppsUsed(context)
                    .forEach { (pkg, name, activity) ->
                        val key = appKey(pkg, activity, 0)
                        if (seenAppKeys.add(key)) {
                            rawApps.add(
                                RawApp(
                                    pkg = pkg,
                                    cls = activity,
                                    label = name,
                                    user = Process.myUserHandle(),
                                    profileType = "SYSTEM",
                                    category = AppCategory.RECENT
                                )
                            )
                        }
                    }
            }.onFailure { t ->
                AppLogger.e("AppListDebug", "Failed to add recent apps: ${t.message}", t)
            }
        }

        // 🔹 Profile apps in parallel
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val deferreds = profiles.map { profile ->
            async {
                if (privateManager.isPrivateSpaceProfile(profile) && privateManager.isPrivateSpaceLocked()) {
                    AppLogger.d("AppListDebug", "🔒 Skipping locked private profile: $profile")
                    emptyList<RawApp>()
                } else {
                    val profileType = when {
                        privateManager.isPrivateSpaceProfile(profile) -> "PRIVATE"
                        profile != Process.myUserHandle() -> "WORK"
                        else -> "SYSTEM"
                    }

                    runCatching { launcherApps.getActivityList(null, profile) }
                        .getOrElse {
                            AppLogger.e("AppListDebug", "Failed to get activities for $profile: ${it.message}", it)
                            emptyList()
                        }
                        .mapNotNull { info ->
                            val pkg = info.applicationInfo.packageName
                            val cls = info.componentName.className

                            // Skip your own app
                            if (pkg == BuildConfig.APPLICATION_ID) return@mapNotNull null

                            val key = appKey(pkg, cls, profile.hashCode())
                            if (!seenAppKeys.add(key)) return@mapNotNull null

                            // Skip hidden / regular apps based on toggles
                            if ((isHidden(pkg, key) && !includeHiddenApps) || (!isHidden(pkg, key) && !includeRegularApps))
                                return@mapNotNull null

                            val category = if (pkg in pinnedPackages) AppCategory.PINNED else AppCategory.REGULAR
                            RawApp(pkg, cls, info.label.toString(), profile, profileType, category)
                        }
                }
            }
        }

        deferreds.forEach { rawApps.addAll(it.await()) }

        // 🔹 Update profile counters
        listOf("SYSTEM", "PRIVATE", "WORK", "USER").forEach { type ->
            prefs.setProfileCounter(type, rawApps.count { it.profileType == type })
        }

        // 🔹 Convert RawApp → AppListItem
        val allApps = rawApps.map { raw ->
            AppListItem(
                activityLabel = raw.label,
                activityPackage = raw.pkg,
                activityClass = raw.cls,
                user = raw.user,
                profileType = raw.profileType,
                customLabel = prefs.getAppAlias(raw.pkg),
                customTag = prefs.getAppTag(raw.pkg, raw.user),
                category = raw.category
            )
        }
            // 🔹 Sort pinned apps first, then regular/recent alphabetically
            .sortedWith(
                compareByDescending<AppListItem> { it.category == AppCategory.PINNED }
                    .thenBy { normalizeForSort(it.label) }
            )
            .toMutableList()

        // 🔹 Build scroll map and finalize
        buildList(
            items = allApps,
            seenKey = mutableSetOf(),
            scrollMapLiveData = _appScrollMap,
            includeHidden = includeHiddenApps,
            getKey = { "${it.activityPackage}|${it.activityClass}|${it.user.hashCode()}" },
            isHidden = { it.activityPackage in hiddenAppsSet },
            isPinned = { it.activityPackage in pinnedPackages },
            buildItem = { it },
            getLabel = { it.label },
            normalize = ::normalizeForSort
        )
    }


    /**
     * Build contact list on IO dispatcher. Uses batched phone/email queries for speed.
     */
    suspend fun getContactsList(
        context: Context,
        includeHiddenContacts: Boolean = false
    ): MutableList<ContactListItem> = withContext(Dispatchers.IO) {

        val prefs = Prefs(context)
        val hiddenContacts = prefs.hiddenContacts
        val pinnedContacts = prefs.pinnedContacts.toSet()
        val seenContacts = mutableSetOf<String>()

        AppLogger.d("ContactListDebug", "🔄 getContactsList called: includeHiddenContacts=$includeHiddenContacts")

        val contentResolver = context.contentResolver

        // 🔹 Query basic contact info
        val basicContacts = runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.LOOKUP_KEY
                ),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                generateSequence {
                    if (cursor.moveToNext()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                        val lookup = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                        Triple(id, name, lookup)
                    } else null
                }.toList()
            } ?: emptyList()
        }.getOrElse {
            AppLogger.e("ContactListDebug", "❌ Failed to query contacts: ${it.message}", it)
            emptyList()
        }

        val contactIds = basicContacts.map { it.first }

        // 🔹 Fetch phone numbers
        val phonesMap = mutableMapOf<String, String>()
        if (contactIds.isNotEmpty()) {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})",
                contactIds.toTypedArray(),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    phonesMap.putIfAbsent(id, number)
                }
            }
        }

        // 🔹 Fetch emails
        val emailsMap = mutableMapOf<String, String>()
        if (contactIds.isNotEmpty()) {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})",
                contactIds.toTypedArray(),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID))
                    val email = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)) ?: ""
                    emailsMap.putIfAbsent(id, email)
                }
            }
        }

        // 🔹 Create lightweight intermediate data (raw contacts)
        data class RawContact(
            val id: String,
            val displayName: String,
            val lookupKey: String,
            val phone: String,
            val email: String
        )

        val rawContacts = basicContacts.map { (id, name, lookup) ->
            RawContact(
                id = id,
                displayName = name,
                lookupKey = lookup,
                phone = phonesMap[id] ?: "",
                email = emailsMap[id] ?: ""
            )
        }

        AppLogger.d("ContactListDebug", "📦 Raw contacts ready: ${rawContacts.size}")

        // 🔹 Delegate to buildList()
        buildList(
            items = rawContacts,
            seenKey = seenContacts,
            scrollMapLiveData = _contactScrollMap,
            includeHidden = includeHiddenContacts,
            getKey = { "${it.id}|${it.lookupKey}" },
            isHidden = { it.lookupKey in hiddenContacts },
            isPinned = { it.lookupKey in pinnedContacts },
            buildItem = {
                ContactListItem(
                    displayName = it.displayName,
                    phoneNumber = it.phone,
                    email = it.email,
                    category = if (it.lookupKey in pinnedContacts)
                        ContactCategory.FAVORITE
                    else
                        ContactCategory.REGULAR
                )
            },
            getLabel = { it.displayName },
            normalize = ::normalizeForSort
        )
    }

    // -------------------------
    // Helper: cheap normalization for sorting
    // Removes diacritics/unsupported characters cheaply and collapses whitespace.
    private fun normalizeForSort(s: String): String {
        // Keep letters, digits and spaces. Collapse multiple spaces. Lowercase using default locale.
        val sb = StringBuilder(s.length)
        var lastWasSpace = false
        for (ch in s) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch.lowercaseChar())
                lastWasSpace = false
            } else if (ch.isWhitespace()) {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } // else skip punctuation
        }
        return sb.toString().trim()
    }

    // -------------------------
    // Simple file cache helpers using org.json (no external deps)
    private fun saveAppsToFileCache(list: List<AppListItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("label", item.activityLabel)
                obj.put("package", item.activityPackage)
                obj.put("class", item.activityClass)
                obj.put("userHash", item.user.hashCode())
                obj.put("profileType", item.profileType)
                obj.put("customLabel", item.customLabel)
                obj.put("customTag", item.customTag)
                obj.put("category", item.category.ordinal)
                array.put(obj)
            }
            val top = JSONObject()
            top.put("timestamp", System.currentTimeMillis())
            top.put("items", array)
            FileOutputStream(appsCacheFile).use { fos ->
                fos.write(top.toString().toByteArray(Charset.forName("UTF-8")))
            }
        } catch (t: Throwable) {
            AppLogger.e("MainViewModel", "Failed to save apps cache: ${t.message}", t)
        }
    }

    private fun loadAppsFromFileCache(): List<AppListItem>? {
        try {
            if (!appsCacheFile.exists()) return null
            val bytes = FileInputStream(appsCacheFile).use { it.readBytes() }
            val text = String(bytes, Charset.forName("UTF-8"))
            val top = JSONObject(text)
            val array = top.getJSONArray("items")
            val list = mutableListOf<AppListItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                // user handle restoration cannot be exact; use current user handle as best effort
                val userHandle = Process.myUserHandle()
                val item = AppListItem(
                    activityLabel = obj.optString("label", ""),
                    activityPackage = obj.optString("package", ""),
                    activityClass = obj.optString("class", ""),
                    user = userHandle,
                    profileType = obj.optString("profileType", "SYSTEM"),
                    customLabel = obj.optString("customLabel", ""),
                    customTag = obj.optString("customTag", ""),
                    category = AppCategory.entries.getOrNull(obj.optInt("category", 1)) ?: AppCategory.REGULAR
                )
                list.add(item)
            }
            return list
        } catch (t: Throwable) {
            AppLogger.e("MainViewModel", "Failed to load apps cache: ${t.message}", t)
            return null
        }
    }

    private fun saveContactsToFileCache(list: List<ContactListItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("displayName", item.displayName)
                obj.put("phoneNumber", item.phoneNumber)
                obj.put("email", item.email)
                obj.put("category", item.category.ordinal)
                array.put(obj)
            }
            val top = JSONObject()
            top.put("timestamp", System.currentTimeMillis())
            top.put("items", array)
            FileOutputStream(contactsCacheFile).use { fos ->
                fos.write(top.toString().toByteArray(Charset.forName("UTF-8")))
            }
        } catch (t: Throwable) {
            AppLogger.e("MainViewModel", "Failed to save contacts cache: ${t.message}", t)
        }
    }

    private fun loadContactsFromFileCache(): List<ContactListItem>? {
        try {
            if (!contactsCacheFile.exists()) return null
            val bytes = FileInputStream(contactsCacheFile).use { it.readBytes() }
            val text = String(bytes, Charset.forName("UTF-8"))
            val top = JSONObject(text)
            val array = top.getJSONArray("items")
            val list = mutableListOf<ContactListItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = ContactListItem(
                    displayName = obj.optString("displayName", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    email = obj.optString("email", ""),
                    category = ContactCategory.entries.getOrNull(obj.optInt("category", 1)) ?: ContactCategory.REGULAR
                )
                list.add(item)
            }
            return list
        } catch (t: Throwable) {
            AppLogger.e("MainViewModel", "Failed to load contacts cache: ${t.message}", t)
            return null
        }
    }
}
