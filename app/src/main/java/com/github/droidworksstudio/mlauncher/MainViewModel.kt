package com.github.droidworksstudio.mlauncher

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
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
import com.github.droidworksstudio.common.AppLogger
import com.github.droidworksstudio.common.CrashHandler
import com.github.droidworksstudio.common.getLocalizedString
import com.github.droidworksstudio.common.hideKeyboard
import com.github.droidworksstudio.common.showShortToast
import com.github.droidworksstudio.mlauncher.data.AppCategory
import com.github.droidworksstudio.mlauncher.data.AppListItem
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Constants.AppDrawerFlag
import com.github.droidworksstudio.mlauncher.data.ContactCategory
import com.github.droidworksstudio.mlauncher.data.ContactListItem
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.helper.analytics.AppUsageMonitor
import com.github.droidworksstudio.mlauncher.helper.ismlauncherDefault
import com.github.droidworksstudio.mlauncher.helper.logActivitiesFromPackage
import com.github.droidworksstudio.mlauncher.helper.utils.BiometricHelper
import com.github.droidworksstudio.mlauncher.helper.utils.PrivateSpaceManager
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
    private val appsCacheFile = File(appContext.filesDir, "apps_cache.json")
    private val contactsCacheFile = File(appContext.filesDir, "contacts_cache.json")

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

    /**
     * Build app list on IO dispatcher. This function is still suspend and safe to call
     * from background, but it ensures all heavy operations happen on Dispatchers.IO.
     */
    suspend fun getAppsList(
        context: Context,
        includeRegularApps: Boolean = true,
        includeHiddenApps: Boolean = false,
        includeRecentApps: Boolean = true
    ): MutableList<AppListItem> = withContext(Dispatchers.IO) {

        val fullList: MutableList<AppListItem> = mutableListOf()

        AppLogger.d(
            "AppListDebug",
            "🔄 getAppsList (IO) called with: includeRegular=$includeRegularApps, includeHidden=$includeHiddenApps, includeRecent=$includeRecentApps"
        )

        try {
            val prefs = Prefs(context)
            val hiddenAppsSet = prefs.hiddenApps
            val pinnedPackages = prefs.pinnedApps.toSet()
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val seenAppKeys = mutableSetOf<String>()  // packageName|activityName|userId
            val scrollIndexMap = mutableMapOf<String, Int>()

            // Prepare profile list first
            val profiles = userManager.userProfiles.toList()

            // Process recent apps once if needed
            if (prefs.recentAppsDisplayed && includeRecentApps) {
                try {
                    val tracker = AppUsageMonitor.createInstance(context)
                    val recentApps = tracker.getLastTenAppsUsed(context)
                    AppLogger.d("AppListDebug", "🕓 Adding ${recentApps.size} recent apps")

                    for ((packageName, appName, activityName) in recentApps) {
                        val fakeProfileHash = 0 // recent list treated as system list
                        val appKey = "$packageName|$activityName|$fakeProfileHash"
                        if (seenAppKeys.contains(appKey)) continue

                        val alias = prefs.getAppAlias(packageName).ifEmpty { appName }
                        val tag = prefs.getAppTag(packageName, null)
                        fullList.add(
                            AppListItem(
                                activityLabel = appName,
                                activityPackage = packageName,
                                activityClass = activityName,
                                user = Process.myUserHandle(), // best effort
                                profileType = "SYSTEM",
                                customLabel = alias,
                                customTag = tag,
                                category = AppCategory.RECENT
                            )
                        )
                        seenAppKeys.add(appKey)
                    }
                } catch (t: Throwable) {
                    AppLogger.e("AppListDebug", "Failed to add recent apps: ${t.message}", t)
                }
            }

            // Process profiles in parallel to take advantage of multiple cores
            val deferreds = profiles.map { profile ->
                async {
                    val privateSpaceManager = PrivateSpaceManager(context)
                    val isPrivate = privateSpaceManager.isPrivateSpaceProfile(profile)
                    if (isPrivate && privateSpaceManager.isPrivateSpaceLocked()) {
                        AppLogger.d("AppListDebug", "🔒 Skipping locked private space for profile: $profile")
                        return@async emptyList()
                    }

                    val isWork = profile != Process.myUserHandle() && !isPrivate

                    val profileType = when {
                        isPrivate -> "PRIVATE"
                        isWork -> "WORK"
                        else -> "SYSTEM"
                    }

                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val activities = try {
                        launcherApps.getActivityList(null, profile)
                    } catch (t: Throwable) {
                        AppLogger.e("AppListDebug", "Failed to get activities for $profile: ${t.message}", t)
                        emptyList()
                    }

                    prefs.setProfileCounter(profileType, activities.size)
                    AppLogger.d("AppListDebug", "👤 Processing user profile: $profile|$profileType with ${activities.size} activities")

                    val list = mutableListOf<AppListItem>()

                    for (activityInfo in activities) {
                        val packageName = activityInfo.applicationInfo.packageName
                        val className = activityInfo.componentName.className
                        val label = activityInfo.label.toString()

                        if (packageName == BuildConfig.APPLICATION_ID) continue

                        val appKey = "$packageName|$className|${profile.hashCode()}"
                        if (seenAppKeys.contains(appKey)) {
                            continue
                        }

                        val isHidden = listOf(packageName, appKey, "$packageName|${profile.hashCode()}").any { it in hiddenAppsSet }
                        if ((isHidden && !includeHiddenApps) || (!isHidden && !includeRegularApps)) {
                            continue
                        }

                        val alias = prefs.getAppAlias(packageName)
                        val tag = prefs.getAppTag(packageName, profile)

                        val category = when {
                            pinnedPackages.contains(packageName) -> AppCategory.PINNED
                            else -> AppCategory.REGULAR
                        }

                        list.add(
                            AppListItem(
                                activityLabel = label,
                                activityPackage = packageName,
                                activityClass = className,
                                user = profile,
                                profileType = profileType,
                                customLabel = alias,
                                customTag = tag,
                                category = category
                            )
                        )
                        seenAppKeys.add(appKey)
                    }

                    list
                }
            }

            // Await all profile results and merge
            val results = deferreds.flatMap { it.await() }
            fullList.addAll(results)

            // Sort using a cheap normalization function and category first
            fullList.sortWith(
                compareBy<AppListItem> { it.category.ordinal }
                    .thenBy { normalizeForSort(it.label) }
            )

            // Build scroll index
            for ((index, item) in fullList.withIndex()) {
                if (item.category == AppCategory.PINNED) continue
                val key = item.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                scrollIndexMap.putIfAbsent(key, index)
            }

            // Include pinned under '★'
            fullList.forEachIndexed { index, item ->
                val key = when (item.category) {
                    AppCategory.PINNED -> "★"
                    else -> item.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                }
                if (!scrollIndexMap.containsKey(key)) {
                    scrollIndexMap[key] = index
                }
            }

            AppLogger.d("AppListDebug", "✅ App list built with ${fullList.size} items")
            // publish scroll map on main thread
            withContext(Dispatchers.Main) {
                _appScrollMap.value = scrollIndexMap
            }

        } catch (e: Exception) {
            AppLogger.e("AppListDebug", "❌ Error building app list: ${e.message}", e)
        }

        fullList
    }

    /**
     * Build contact list on IO dispatcher. Uses batched phone/email queries for speed.
     */
    suspend fun getContactsList(
        context: Context,
        includeHiddenContacts: Boolean = false
    ): MutableList<ContactListItem> = withContext(Dispatchers.IO) {

        val fullList: MutableList<ContactListItem> = mutableListOf()
        val prefs = Prefs(context)
        val hiddenContacts = prefs.hiddenContacts // Set of lookupKeys
        val pinnedContacts = prefs.pinnedContacts.toSet() // Set of lookupKeys
        val seenContacts = mutableSetOf<String>() // contactId|lookupKey
        val scrollIndexMap = mutableMapOf<String, Int>()

        AppLogger.d("ContactListDebug", "🔄 getContactsList (IO) called: includeHiddenContacts=$includeHiddenContacts")

        try {
            val contentResolver: ContentResolver = context.contentResolver

            // First query basic contacts (id, name, lookup)
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.LOOKUP_KEY
            )
            val cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            )

            if (cursor == null) {
                AppLogger.e("ContactListDebug", "❌ Cursor is null, no contacts found")
                return@withContext fullList
            }

            val contactIds = mutableListOf<String>()
            val basicContacts = mutableListOf<Triple<String, String, String>>() // id, name, lookupKey

            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val displayName =
                        c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                    val lookupKey = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))

                    val key = "$id|$lookupKey"
                    if (seenContacts.contains(key)) continue
                    seenContacts.add(key)
                    contactIds.add(id)
                    basicContacts.add(Triple(id, displayName, lookupKey))
                }
            }

            AppLogger.d("ContactListDebug", "📇 Contacts found: ${basicContacts.size}")

            // Batch query phones for all contactIds
            val phonesMap = mutableMapOf<String, String>() // contactId -> phone
            if (contactIds.isNotEmpty()) {
                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})",
                    contactIds.toTypedArray(),
                    null
                )
                phoneCursor?.use { pc ->
                    while (pc.moveToNext()) {
                        val cid = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                        val number = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                        // keep first found as primary
                        phonesMap.putIfAbsent(cid, number)
                    }
                }
            }

            // Batch query emails for all contactIds
            val emailsMap = mutableMapOf<String, String>() // contactId -> email
            if (contactIds.isNotEmpty()) {
                val emailCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Email.ADDRESS
                    ),
                    "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} IN (${contactIds.joinToString(",") { "?" }})",
                    contactIds.toTypedArray(),
                    null
                )
                emailCursor?.use { ec ->
                    while (ec.moveToNext()) {
                        val cid = ec.getString(ec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID))
                        val address = ec.getString(ec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)) ?: ""
                        emailsMap.putIfAbsent(cid, address)
                    }
                }
            }

            // Build final contact list from basicContacts with batched phone/email
            for ((id, displayName, lookupKey) in basicContacts) {
                // hidden handling
                val isHidden = lookupKey in hiddenContacts
                if (isHidden && !includeHiddenContacts) {
                    AppLogger.d("ContactListDebug", "🚫 Skipping hidden contact: $displayName ($lookupKey)")
                    continue
                }

                val category = if (pinnedContacts.contains(lookupKey)) {
                    ContactCategory.FAVORITE
                } else ContactCategory.REGULAR

                val email = emailsMap[id] ?: ""
                val phoneNumber = phonesMap[id] ?: ""

                if (email.isNotEmpty()) AppLogger.d("ContactListDebug", "✉️ Found email for $displayName: $email")
                if (phoneNumber.isNotEmpty()) AppLogger.d("ContactListDebug", "📞 Found phone for $displayName: $phoneNumber")

                fullList.add(
                    ContactListItem(
                        displayName = displayName,
                        phoneNumber = phoneNumber,
                        email = email,
                        category = category
                    )
                )
            }

            AppLogger.d("ContactListDebug", "🔢 Total contacts after processing: ${fullList.size}")

            // Sort: FAVORITE first, then alphabetical using cheap normalization
            fullList.sortWith(
                compareBy<ContactListItem> { it.category.ordinal }
                    .thenBy { normalizeForSort(it.displayName) }
            )
            AppLogger.d("ContactListDebug", "🔠 Sorted contact list")

            // Build scroll index for A-Z sidebar
            fullList.forEachIndexed { index, item ->
                val key = when (item.category) {
                    ContactCategory.FAVORITE -> "★"
                    else -> item.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                }
                scrollIndexMap.putIfAbsent(key, index)
            }

            // post scroll map
            withContext(Dispatchers.Main) {
                _contactScrollMap.value = scrollIndexMap
            }

            AppLogger.d("ContactListDebug", "🧭 Scroll index map posted with ${scrollIndexMap.size} entries")

        } catch (e: Exception) {
            AppLogger.e("ContactListDebug", "❌ Error building contact list: ${e.message}", e)
        }

        fullList
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
