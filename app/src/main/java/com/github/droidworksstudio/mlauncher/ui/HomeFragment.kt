package com.github.droidworksstudio.mlauncher.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.droidworksstudio.mlauncher.MainViewModel
import com.github.droidworksstudio.mlauncher.R
import com.github.droidworksstudio.mlauncher.data.AppModel
import com.github.droidworksstudio.mlauncher.data.Constants
import com.github.droidworksstudio.mlauncher.data.Constants.Action
import com.github.droidworksstudio.mlauncher.data.Constants.AppDrawerFlag
import com.github.droidworksstudio.mlauncher.data.Prefs
import com.github.droidworksstudio.mlauncher.databinding.FragmentHomeBinding
import com.github.droidworksstudio.mlauncher.helper.ActionService
import com.github.droidworksstudio.mlauncher.helper.BatteryReceiver
import com.github.droidworksstudio.mlauncher.helper.getHexFontColor
import com.github.droidworksstudio.mlauncher.helper.getHexForOpacity
import com.github.droidworksstudio.mlauncher.helper.hideStatusBar
import com.github.droidworksstudio.mlauncher.helper.initActionService
import com.github.droidworksstudio.mlauncher.helper.ismlauncherDefault
import com.github.droidworksstudio.mlauncher.helper.openAccessibilitySettings
import com.github.droidworksstudio.mlauncher.helper.openAlarmApp
import com.github.droidworksstudio.mlauncher.helper.openCalendar
import com.github.droidworksstudio.mlauncher.helper.openCameraApp
import com.github.droidworksstudio.mlauncher.helper.openDialerApp
import com.github.droidworksstudio.mlauncher.helper.showStatusBar
import com.github.droidworksstudio.mlauncher.helper.showToastLong
import com.github.droidworksstudio.mlauncher.helper.showToastShort
import com.github.droidworksstudio.mlauncher.listener.OnSwipeTouchListener
import com.github.droidworksstudio.mlauncher.listener.ViewSwipeTouchListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var vibrator: Vibrator

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())
        batteryReceiver = BatteryReceiver()

        return view
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hex = getHexForOpacity(requireContext(), prefs)
        binding.mainLayout.setBackgroundColor(hex)

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        vibrator = context?.getSystemService(VIBRATOR_SERVICE) as Vibrator

        initObservers()
        initSwipeTouchListener()
        initClickListeners()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStart() {
        super.onStart()
        if (prefs.showStatusBar) showStatusBar(requireActivity()) else hideStatusBar(requireActivity())
        val typeface = ResourcesCompat.getFont(requireActivity(), R.font.roboto)

        binding.clock.textSize = prefs.textSizeLauncher * prefs.textSizeMultiplier
        binding.date.textSize = prefs.textSizeLauncher.toFloat()
        binding.batteryIcon.textSize = prefs.textSizeLauncher.toFloat() / 1.5f
        binding.batteryText.textSize = prefs.textSizeLauncher.toFloat() / 1.5f
        binding.homeScreenPager.textSize = prefs.textSizeLauncher.toFloat()

        if (prefs.useCustomIconFont) {
            binding.clock.typeface = typeface
            binding.date.typeface = typeface
            binding.batteryText.typeface = typeface
            binding.setDefaultLauncher.typeface = typeface
        }
        binding.homeScreenPager.typeface = typeface
        binding.batteryIcon.typeface = typeface

        val backgroundColor = getHexForOpacity(requireContext(), prefs)
        binding.mainLayout.setBackgroundColor(backgroundColor)
        if (prefs.followAccentColors) {
            val fontColor = getHexFontColor(requireContext())
            binding.clock.setTextColor(fontColor)
            binding.date.setTextColor(fontColor)
            binding.batteryIcon.setTextColor(fontColor)
            binding.batteryText.setTextColor(fontColor)
            binding.setDefaultLauncher.setTextColor(fontColor)
            binding.homeScreenPager.setTextColor(fontColor)
        }
    }

    override fun onResume() {
        super.onResume()

        batteryReceiver = BatteryReceiver()
        /* register battery changes */
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        requireContext().registerReceiver(batteryReceiver, filter)

        val locale = prefs.language.locale()
        val best12 = DateFormat.getBestDateTimePattern(locale, "hhmma")
        val best24 = DateFormat.getBestDateTimePattern(locale, "HHmm")
        binding.clock.format12Hour = best12
        binding.clock.format24Hour = best24

        val best12Date = DateFormat.getBestDateTimePattern(locale, "eeeddMMM")
        val best24Date = DateFormat.getBestDateTimePattern(locale, "eeeddMMM")
        binding.date.format12Hour = best12Date
        binding.date.format24Hour = best24Date

        // only show "set as default"-button if tips are GONE
        if (binding.firstRunTips.visibility == View.GONE) {
            binding.setDefaultLauncher.visibility =
                if (ismlauncherDefault(requireContext())) View.GONE else View.VISIBLE
        }

    }

    override fun onPause() {
        super.onPause()
        /* unregister battery changes */
        requireContext().unregisterReceiver(batteryReceiver)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.clock -> {
                when (val action = prefs.clickClockAction) {
                    Action.OpenApp -> openClickClockApp()
                    else -> handleOtherAction(action)
                }
            }

            R.id.date -> {
                when (val action = prefs.clickDateAction) {
                    Action.OpenApp -> openClickDateApp()
                    else -> handleOtherAction(action)
                }
            }

            R.id.setDefaultLauncher -> {
                viewModel.resetDefaultLauncherApp(requireContext())
            }

            else -> {
                try { // Launch app
                    val appLocation = view.id
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (prefs.homeLocked) return true

        val n = view.id
        showAppList(AppDrawerFlag.SetHomeApp, includeHiddenApps = prefs.hiddenAppsDisplayed, n)
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSwipeTouchListener() {
        binding.touchArea.setOnTouchListener(getHomeScreenGestureListener(requireContext()))
    }

    private fun initClickListeners() {
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        with(viewModel) {

            clockAlignment.observe(viewLifecycleOwner) { gravity ->
                binding.dateTimeLayout.gravity = gravity.value()
            }
            homeAppsAlignment.observe(viewLifecycleOwner) { (gravity, onBottom) ->
                val horizontalAlignment = if (onBottom) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
                binding.homeAppsLayout.gravity = gravity.value() or horizontalAlignment

                binding.homeAppsLayout.children.forEach { view ->
                    (view as TextView).gravity = gravity.value()
                }
            }
            homeAppsCount.observe(viewLifecycleOwner) {
                updateAppCount(it)
            }
            showTime.observe(viewLifecycleOwner) {
                binding.clock.visibility = if (it) View.VISIBLE else View.GONE
            }
            showDate.observe(viewLifecycleOwner) {
                binding.date.visibility = if (it) View.VISIBLE else View.GONE
            }
        }
    }

    private fun homeAppClicked(location: Int) {
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else launchApp(prefs.getHomeAppModel(location))
    }

    private fun launchApp(appModel: AppModel) {
        viewModel.selectedApp(appModel, AppDrawerFlag.LaunchApp)
    }

    private fun showAppList(flag: AppDrawerFlag, includeHiddenApps: Boolean = false, n: Int = 0) {
        viewModel.getAppList(includeHiddenApps)
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                findNavController().navigate(
                    R.id.action_mainFragment_to_appListFragment,
                    bundleOf("flag" to flag.toString(), "n" to n)
                )
            } catch (e: Exception) {
                findNavController().navigate(
                    R.id.appListFragment,
                    bundleOf("flag" to flag.toString())
                )
                e.printStackTrace()
            }
        }
    }

    private fun openSwipeUpApp() {
        if (prefs.appShortSwipeUp.appPackage.isNotEmpty())
            launchApp(prefs.appShortSwipeUp)
        else openDialerApp(requireContext())
    }
    private fun openSwipeDownApp() {
        if (prefs.appShortSwipeDown.appPackage.isNotEmpty())
            launchApp(prefs.appShortSwipeDown)
        else openDialerApp(requireContext())
    }

    private fun openSwipeLeftApp() {
        if (prefs.appShortSwipeLeft.appPackage.isNotEmpty())
            launchApp(prefs.appShortSwipeLeft)
        else openCameraApp(requireContext())
    }

    private fun openSwipeRightApp() {
        if (prefs.appShortSwipeRight.appPackage.isNotEmpty())
            launchApp(prefs.appShortSwipeRight)
        else openDialerApp(requireContext())
    }

    private fun openLongSwipeUpApp() {
        if (prefs.appLongSwipeUp.appPackage.isNotEmpty())
            launchApp(prefs.appLongSwipeUp)
        else openDialerApp(requireContext())
    }
    private fun openLongSwipeDownApp() {
        if (prefs.appLongSwipeDown.appPackage.isNotEmpty())
            launchApp(prefs.appLongSwipeDown)
        else openDialerApp(requireContext())
    }

    private fun openLongSwipeLeftApp() {
        if (prefs.appLongSwipeLeft.appPackage.isNotEmpty())
            launchApp(prefs.appLongSwipeLeft)
        else openCameraApp(requireContext())
    }

    private fun openLongSwipeRightApp() {
        if (prefs.appLongSwipeRight.appPackage.isNotEmpty())
            launchApp(prefs.appLongSwipeRight)
        else openDialerApp(requireContext())
    }

    private fun openClickClockApp() {
        if (prefs.appClickClock.appPackage.isNotEmpty())
            launchApp(prefs.appClickClock)
        else openAlarmApp(requireContext())
    }

    private fun openClickDateApp() {
        if (prefs.appClickDate.appPackage.isNotEmpty())
            launchApp(prefs.appClickDate)
        else openCalendar(requireContext())
    }

    private fun openDoubleTapApp() {
        if (prefs.appDoubleTap.appPackage.isNotEmpty())
            launchApp(prefs.appDoubleTap)
        else openCameraApp(requireContext())
    }

    // This function handles all swipe actions that a independent of the actual swipe direction
    @SuppressLint("NewApi")
    private fun handleOtherAction(action: Action) {
        when (action) {
            Action.ShowNotification -> initActionService(requireContext())?.openNotifications()
            Action.LockScreen -> lockPhone()
            Action.ShowAppList -> showAppList(AppDrawerFlag.LaunchApp, includeHiddenApps = false)
            Action.OpenApp -> {} // this should be handled in the respective onSwipe[Up,Down,Right,Left] functions
            Action.OpenQuickSettings -> initActionService(requireContext())?.openQuickSettings()
            Action.ShowRecents -> initActionService(requireContext())?.showRecents()
            Action.OpenPowerDialog -> initActionService(requireContext())?.openPowerDialog()
            Action.TakeScreenShot -> initActionService(requireContext())?.takeScreenShot()
            Action.LeftPage -> handleSwipeLeft(prefs.homePagesNum)
            Action.RightPage -> handleSwipeRight(prefs.homePagesNum)
            Action.Disabled -> {}
        }
    }

    private fun lockPhone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val actionService = ActionService.instance()
            if (actionService != null) {
                actionService.lockScreen()
            } else {
                openAccessibilitySettings(requireContext())
            }
        } else {
            requireActivity().runOnUiThread {
                try {
                    deviceManager.lockNow()
                } catch (e: SecurityException) {
                    showToastLong(
                        requireContext(),
                        "App does not have the permission to lock the device"
                    )
                } catch (e: Exception) {
                    showToastLong(
                        requireContext(),
                        "mLauncher failed to lock device.\nPlease check your app settings."
                    )
                    prefs.lockModeOn = false
                }
            }
        }
    }

    private fun showLongPressToast() = showToastShort(requireContext(), "Long press to select app")

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getHomeScreenGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            private var startX = 0f
            private var startY = 0f
            private var startTime: Long = 0
            private var longSwipeTriggered = false // Flag to indicate if onLongSwipe was triggered

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = motionEvent.x
                        startY = motionEvent.y
                        startTime = System.currentTimeMillis()
                        longSwipeTriggered = false // Reset the flag
                    }
                    MotionEvent.ACTION_UP -> {
                        val endX = motionEvent.x
                        val endY = motionEvent.y
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        val deltaX = endX - startX
                        val deltaY = endY - startY
                        val distance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        val direction: String = if (abs(deltaX) < abs(deltaY)) {
                            if (deltaY < 0) "up" else "down"
                        } else {
                            if (deltaX < 0) "left" else "right"
                        }

                        // Check if it's a hold swipe gesture
                        val holdDurationThreshold = Constants.HOLD_DURATION_THRESHOLD
                        Constants.updateSwipeDistanceThreshold(context, direction)
                        val swipeDistanceThreshold = Constants.SWIPE_DISTANCE_THRESHOLD

                        if (duration <= holdDurationThreshold && distance >= swipeDistanceThreshold) {
                            onLongSwipe(direction)
                            longSwipeTriggered = true // Set the flag if onLongSwipe was triggered
                        }
                    }
                }
                // Return false to continue to pass the event to onSwipeLeft if onLongSwipe was not triggered
                return !longSwipeTriggered && super.onTouch(view, motionEvent)
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                when (val action = prefs.shortSwipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                when (val action = prefs.shortSwipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                when (val action = prefs.shortSwipeUpAction) {
                    Action.OpenApp -> openSwipeUpApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                when (val action = prefs.shortSwipeDownAction) {
                    Action.OpenApp -> openSwipeDownApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onLongClick() {
                super.onLongClick()
                lifecycleScope.launch(Dispatchers.Main) {
                    biometricPrompt = BiometricPrompt(this@HomeFragment,
                        ContextCompat.getMainExecutor(requireContext()),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                when (errorCode) {
                                    BiometricPrompt.ERROR_USER_CANCELED -> showToastLong(
                                        requireContext(),
                                        getString(R.string.text_authentication_cancel)
                                    )

                                    else -> showToastLong(
                                        requireContext(),
                                        getString(R.string.text_authentication_error).format(
                                            errString,
                                            errorCode
                                        )
                                    )
                                }
                            }

                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                sendToSettingFragment()
                            }

                            override fun onAuthenticationFailed() {
                                showToastLong(
                                    requireContext(),
                                    getString(R.string.text_authentication_failed)
                                )
                            }
                        })

                    promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.text_biometric_login))
                        .setSubtitle(getString(R.string.text_biometric_login_sub))
                        .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                        .setConfirmationRequired(false)
                        .build()


                    if (prefs.settingsLocked) {
                        authenticate()
                    } else {
                        sendToSettingFragment()
                    }

                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                when (val action = prefs.doubleTapAction) {
                    Action.OpenApp -> openDoubleTapApp()
                    else -> handleOtherAction(action)
                }
            }
        }
    }

    private fun authenticate() {
        val code = BiometricManager.from(requireContext())
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> sendToSettingFragment()

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> sendToSettingFragment()

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> sendToSettingFragment()

            else -> showToastLong(requireContext(), getString(R.string.text_authentication_error))
        }
    }

    private fun sendToSettingFragment() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: java.lang.Exception) {
            Log.d("onLongClick", e.toString())
        }
    }

    private fun getHomeAppsGestureListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            private var startX = 0f
            private var startY = 0f
            private var startTime: Long = 0
            private var longSwipeTriggered = false // Flag to indicate if onLongSwipe was triggered

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = motionEvent.x
                        startY = motionEvent.y
                        startTime = System.currentTimeMillis()
                        longSwipeTriggered = false // Reset the flag
                    }
                    MotionEvent.ACTION_UP -> {
                        val endX = motionEvent.x
                        val endY = motionEvent.y
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        val deltaX = endX - startX
                        val deltaY = endY - startY
                        val distance = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                        val direction: String = if (abs(deltaX) < abs(deltaY)) {
                            if (deltaY < 0) "up" else "down"
                        } else {
                            if (deltaX < 0) "left" else "right"
                        }

                        // Check if it's a hold swipe gesture
                        val holdDurationThreshold = Constants.HOLD_DURATION_THRESHOLD
                        Constants.updateSwipeDistanceThreshold(context, direction)
                        val swipeDistanceThreshold = Constants.SWIPE_DISTANCE_THRESHOLD

                        if (duration <= holdDurationThreshold && distance >= swipeDistanceThreshold) {
                            onLongSwipe(direction)
                            longSwipeTriggered = true // Set the flag if onLongSwipe was triggered
                        }
                    }
                }
                // Return false to continue to pass the event to onSwipeLeft if onLongSwipe was not triggered
                return !longSwipeTriggered && super.onTouch(view, motionEvent)
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }

            override fun onSwipeLeft() {
                super.onSwipeLeft()
                when (val action = prefs.shortSwipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                when (val action = prefs.shortSwipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                when (val action = prefs.shortSwipeUpAction) {
                    Action.OpenApp -> openSwipeUpApp()
                    else -> handleOtherAction(action)
                }
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                when (val action = prefs.shortSwipeDownAction) {
                    Action.OpenApp -> openSwipeDownApp()
                    else -> handleOtherAction(action)
                }
            }
        }
    }

    private fun onLongSwipe(direction: String) {
        when (direction) {
            "up" -> when (val action = prefs.longSwipeUpAction) {
                Action.OpenApp -> openLongSwipeUpApp()
                else -> handleOtherAction(action)
            }
            "down" -> when (val action = prefs.longSwipeDownAction) {
                Action.OpenApp -> openLongSwipeDownApp()
                else -> handleOtherAction(action)
            }
            "left" -> when (val action = prefs.longSwipeLeftAction) {
                Action.OpenApp -> openLongSwipeLeftApp()
                else -> handleOtherAction(action)
            }
            "right" -> when (val action = prefs.longSwipeRightAction) {
                Action.OpenApp -> openLongSwipeRightApp()
                else -> handleOtherAction(action)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("InflateParams")
    private fun updateAppCount(newAppsNum: Int) {
        val oldAppsNum = binding.homeAppsLayout.childCount // current number of apps
        val diff = newAppsNum - oldAppsNum

        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                val view = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                view.apply {
                    textSize = prefs.textSizeLauncher.toFloat()
                    id = i
                    text = prefs.getHomeAppModel(i).appLabel
                    setOnTouchListener(getHomeAppsGestureListener(context, this))
                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    gravity = prefs.homeAlignment.value()
                    val padding: Int = prefs.textMarginSize
                    setPadding(0, padding, 0, padding)
                    if (prefs.useCustomIconFont) {
                        val typeface = ResourcesCompat.getFont(requireActivity(), R.font.roboto)
                        typeface?.let { setTypeface(it) }
                    }
                    if (prefs.followAccentColors) {
                        val fontColor = getHexFontColor(requireContext())
                        setTextColor(fontColor)
                    }
                }
                // Add the view to the layout
                binding.homeAppsLayout.addView(view)
            }
        } else if (diff < 0) {
            // Remove extra apps
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }

        // Update the total number of pages and calculate maximum apps per page
        updatePagesAndAppsPerPage(prefs.homeAppsNum, prefs.homePagesNum)
    }

    // updates number of apps visible on home screen
    // does nothing if number has not changed
    private var currentPage = 0
    private var appsPerPage = 0

    private fun updatePagesAndAppsPerPage(totalApps: Int, totalPages: Int) {
        // Calculate the maximum number of apps per page
        appsPerPage = if (totalPages > 0) {
            (totalApps + totalPages - 1) / totalPages // Round up to ensure all apps are displayed
        } else {
            0 // Return 0 if totalPages is 0 to avoid division by zero
        }

        // Update app visibility based on the current page and calculated apps per page
        updateAppsVisibility(totalPages)
    }

    private fun updateAppsVisibility(totalPages: Int) {
        val startIdx = currentPage * appsPerPage
        val endIdx = minOf((currentPage + 1) * appsPerPage, getTotalAppsCount())

        for (i in 0 until getTotalAppsCount()) {
            val view = binding.homeAppsLayout.getChildAt(i)
            view.visibility = if (i in startIdx until endIdx) View.VISIBLE else View.GONE
        }

        val pageSelectorTexts = MutableList(totalPages) { _ -> Constants.NEW_PAGE }
        pageSelectorTexts[currentPage] = Constants.CURRENT_PAGE

        // Set the text for the page selector corresponding to each page
        binding.homeScreenPager.text = pageSelectorTexts.joinToString(" ")
        if (prefs.homePagesNum > 1 && prefs.homePagerOn) binding.homeScreenPager.visibility = View.VISIBLE
    }

    private fun handleSwipeLeft(totalPages:Int) {
        if (currentPage < totalPages - 1) {
            currentPage++
            updateAppsVisibility(totalPages)
        }
    }

    private fun handleSwipeRight(totalPages:Int) {
        if (currentPage > 0) {
            currentPage--
            updateAppsVisibility(totalPages)
        }
    }

    private fun getTotalAppsCount(): Int {
        return binding.homeAppsLayout.childCount
    }
}
