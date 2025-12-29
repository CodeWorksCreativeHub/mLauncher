package com.github.codeworkscreativehub.mlauncher.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.receivers.WeatherReceiver
import kotlinx.coroutines.launch

class WeatherHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val updateWeatherUi: (temperatureText: String) -> Unit
) {

    private val prefs = Prefs(context)

    fun getWeather() {
        val useGps = prefs.gpsLocation // or however you store it

        if (useGps) {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val fineLocationGranted = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseLocationGranted = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fineLocationGranted && !coarseLocationGranted) {
                AppLogger.w("WeatherReceiver", "Location permission not granted. Aborting.")
                return
            }

            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> {
                    AppLogger.e("WeatherReceiver", "No location provider enabled.")
                    return
                }
            }

            // ✅ Try last known location first
            val lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown != null) {
                handleLocation(lastKnown)
                return
            }

            // 🔁 Fallback: wait for real-time update
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    handleLocation(location)
                }

                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
            }

            try {
                locationManager.requestLocationUpdates(
                    provider,
                    30 * 60 * 1000L, // every 30 minutes
                    100f,            // or 100 meters
                    locationListener,
                    Looper.getMainLooper()
                )
            } catch (se: SecurityException) {
                se.printStackTrace()
            }
        } else {
            // 📍 Use saved custom location from prefs
            val savedLocation = prefs.loadLocation() // Pair<Double, Double>?
            if (savedLocation != null) {
                val (lat, lon) = savedLocation
                val fakeLocation = Location("prefs_location").apply {
                    latitude = lat
                    longitude = lon
                }
                handleLocation(fakeLocation)
            } else {
                AppLogger.e("WeatherReceiver", "No custom location saved in prefs.")
            }

        }
    }

    private fun handleLocation(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        AppLogger.d("WeatherReceiver", "Location: $lat, $lon")

        val receiver = WeatherReceiver(context)

        lifecycleOwner.lifecycleScope.launch {
            val weatherReceiver = receiver.loadWeatherForSavedLocation(lat, lon)
            val weatherType =
                receiver.getWeatherEmoji(weatherReceiver?.currentWeather?.weatherCode ?: -1)

            if (weatherReceiver != null) {
                val text = String.format(
                    "%s %s%s",
                    weatherType,
                    weatherReceiver.currentWeather.temperature,
                    weatherReceiver.currentUnits.temperatureUnit
                )
                updateWeatherUi(text)
            }
        }
    }
}
