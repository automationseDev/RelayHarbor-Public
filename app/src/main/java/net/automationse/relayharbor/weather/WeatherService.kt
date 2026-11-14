package net.automationse.relayharbor.weather

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal data class WeatherPlace(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val label: String get() = listOf(name, region, country)
        .filter { it.isNotBlank() }.distinct().joinToString("・")
}

internal data class WeatherHour(
    val time: String,
    val temperature: Double,
    val rainChance: Int,
    val weatherCode: Int
)
internal data class WeatherDay(
    val date: String,
    val high: Double,
    val low: Double,
    val rainChance: Int,
    val weatherCode: Int
)
internal data class WeatherSnapshot(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val observedAt: String,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>
)

internal object WeatherLabels {
    fun condition(code: Int): String = when (code) {
        0 -> "快晴"
        1, 2 -> "晴れ・薄曇り"
        3 -> "曇り"
        45, 48 -> "霧"
        51, 53, 55, 56, 57 -> "霧雨"
        61, 63, 65, 66, 67 -> "雨"
        71, 73, 75, 77, 85, 86 -> "雪"
        80, 81, 82 -> "にわか雨"
        95, 96, 99 -> "雷雨"
        else -> "天気情報"
    }
    fun symbol(code: Int, day: Boolean = true): String = when (code) {
        0 -> if (day) "☀️" else "🌙"
        1, 2 -> if (day) "🌤️" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
    fun validCoordinates(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}

internal class WeatherPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "weather_tab_v1", Context.MODE_PRIVATE
    )

    fun place(): WeatherPlace? = runCatching {
        val name = prefs.getString("name", null)?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val latitude = java.lang.Double.longBitsToDouble(prefs.getLong("lat", 0))
        val longitude = java.lang.Double.longBitsToDouble(prefs.getLong("lon", 0))
        if (!WeatherLabels.validCoordinates(latitude, longitude)) return@runCatching null
        WeatherPlace(name, prefs.getString("region", "").orEmpty(),
            prefs.getString("country", "").orEmpty(), latitude, longitude)
    }.getOrNull()

    fun setPlace(place: WeatherPlace) {
        if (!WeatherLabels.validCoordinates(place.latitude, place.longitude)) return
        prefs.edit()
            .putString("name", place.name.take(100))
            .putString("region", place.region.take(100))
            .putString("country", place.country.take(100))
            .putLong("lat", java.lang.Double.doubleToRawLongBits(place.latitude))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(place.longitude))
            .remove("cached_json").remove("cached_at")
            .apply()
    }

    fun cached(maxAgeMs: Long = 30 * 60 * 1000L): WeatherSnapshot? {
        val timestamp = prefs.getLong("cached_at", 0L)
        if (timestamp <= 0L || System.currentTimeMillis() - timestamp !in 0..maxAgeMs) return null
        return prefs.getString("cached_json", null)?.let {
            runCatching { WeatherService.parseForecast(JSONObject(it)) }.getOrNull()
        }
    }

    fun cache(rawJson: String) {
        prefs.edit().putString("cached_json", rawJson.take(120_000))
            .putLong("cached_at", System.currentTimeMillis()).apply()
    }
}

/** Fixed HTTPS endpoints only. No device location permission, credentials or user-provided URLs. */
internal object WeatherService {
    private const val FORECAST_HOST = "api.open-meteo.com"
    private const val GEOCODE_HOST = "geocoding-api.open-meteo.com"

    fun search(query: String): List<WeatherPlace> {
        val safe = query.trim().take(80)
        if (safe.length < 2) return emptyList()
        val encoded = URLEncoder.encode(safe, "UTF-8")
        val obj = readJson("https://$GEOCODE_HOST/v1/search?name=$encoded&count=6&language=ja")
        val results = obj.optJSONArray("results") ?: return emptyList()
        return (0 until minOf(results.length(), 6)).mapNotNull { index ->
            val v = results.optJSONObject(index) ?: return@mapNotNull null
            val lat = v.optDouble("latitude", Double.NaN)
            val lon = v.optDouble("longitude", Double.NaN)
            if (!WeatherLabels.validCoordinates(lat, lon)) return@mapNotNull null
            val name = v.optString("name").take(100)
            if (name.isBlank()) return@mapNotNull null
            WeatherPlace(name, v.optString("admin1").take(100),
                v.optString("country").take(100), lat, lon)
        }
    }

    fun forecast(place: WeatherPlace): Pair<WeatherSnapshot, String> {
        require(WeatherLabels.validCoordinates(place.latitude, place.longitude))
        val lat = String.format(Locale.US, "%.5f", place.latitude)
        val lon = String.format(Locale.US, "%.5f", place.longitude)
        val url = "https://$FORECAST_HOST/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"
        val obj = readJson(url)
        return parseForecast(obj) to obj.toString()
    }

    fun parseForecast(obj: JSONObject): WeatherSnapshot {
        require(!obj.optBoolean("error", false)) { obj.optString("reason", "予報取得に失敗しました") }
        val now = obj.getJSONObject("current")
        val hourly = obj.getJSONObject("hourly")
        val daily = obj.getJSONObject("daily")
        val ht = hourly.getJSONArray("time")
        val htemp = hourly.getJSONArray("temperature_2m")
        val hrain = hourly.getJSONArray("precipitation_probability")
        val hcode = hourly.getJSONArray("weather_code")
        val currentTime = now.optString("time")
        // The API returns local ISO-8601 timestamps because timezone=auto was set.
        val startHour = currentTime.take(13)
        val first = (0 until ht.length()).firstOrNull {
            ht.optString(it).take(13) >= startHour
        } ?: 0
        val hours = (first until minOf(first + 12, ht.length())).map { index ->
            WeatherHour(ht.getString(index), htemp.getDouble(index),
                hrain.optInt(index, 0), hcode.optInt(index, -1))
        }
        val days = daily.getJSONArray("time")
        val maximum = daily.getJSONArray("temperature_2m_max")
        val minimum = daily.getJSONArray("temperature_2m_min")
        val rain = daily.getJSONArray("precipitation_probability_max")
        val codes = daily.getJSONArray("weather_code")
        return WeatherSnapshot(
            temperature = now.getDouble("temperature_2m"),
            feelsLike = now.getDouble("apparent_temperature"),
            humidity = now.optInt("relative_humidity_2m"),
            weatherCode = now.optInt("weather_code", -1),
            isDay = now.optInt("is_day", 1) == 1,
            observedAt = currentTime,
            hours = hours,
            days = (0 until minOf(days.length(), 7)).map { i ->
                WeatherDay(days.getString(i), maximum.getDouble(i), minimum.getDouble(i),
                    rain.optInt(i, 0), codes.optInt(i, -1))
            }
        )
    }

    private fun readJson(address: String): JSONObject {
        val url = URL(address)
        require(url.protocol == "https" && url.host in setOf(FORECAST_HOST, GEOCODE_HOST))
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            require(connection.responseCode == 200) { "天気サービスとの通信に失敗しました（HTTP ${connection.responseCode}）" }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= 250_000) { "天気データが大きすぎます" }
                    out.write(buffer, 0, count)
                }
            }
            return JSONObject(out.toString(Charsets.UTF_8.name()))
        } finally {
            connection.disconnect()
        }
    }
}
