package com.github.droidworksstudio.mlauncher.helper.receivers

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class WeatherReceiver {

    // Create Moshi instance once
    private val moshi = Moshi.Builder().build()

    // Adapter for your WeatherResponse class
    private val weatherAdapter = moshi.adapter(WeatherResponse::class.java)

    // Simple in-memory cache
    private var cachedWeather: WeatherResponse? = null

    suspend fun getCurrentWeather(latitude: Double, longitude: Double): WeatherResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr =
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=weather_code,temperature_2m"
                val response = URL(urlStr).readText()
                val weather = weatherAdapter.fromJson(response)
                cachedWeather = weather // update cache
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cachedWeather
        }
    }

    fun getWeatherEmoji(weatherCode: Int): String {
        return when (weatherCode) {
            0 -> "☀️" // Clear sky
            1 -> "🌤️" // Mainly clear
            2 -> "⛅" // Partly cloudy
            3 -> "☁️" // Overcast
            45, 48 -> "🌫️" // Fog

            51, 53, 55, 56, 57 -> "🌦️" // Drizzle / light rain
            61, 63, 65, 66, 67 -> "🌧️" // Rain
            80, 81, 82 -> "🌧️" // Rain showers

            71, 73, 75, 77, 85, 86 -> "❄️" // Snow
            95, 96, 99 -> "⛈️" // Thunderstorm

            else -> "❔" // Unknown
        }
    }
}

// 🔧 Full response wrapper
@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "current")
    val currentWeather: CurrentWeather,

    @Json(name = "current_units")
    val currentUnits: CurrentUnits
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "temperature_2m")
    val temperature: Double,

    @Json(name = "weather_code")
    val weatherCode: Int,
)

@JsonClass(generateAdapter = true)
data class CurrentUnits(
    @Json(name = "temperature_2m")
    val temperatureUnit: String,

    @Json(name = "weather_code")
    val weatherCodeUnit: String,
)
