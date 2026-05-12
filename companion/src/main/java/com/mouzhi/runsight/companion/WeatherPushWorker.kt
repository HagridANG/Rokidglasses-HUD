package com.mouzhi.headup.companion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * WorkManager Worker锛氬悗鍙拌嚜鍔ㄨ幏鍙栦綅缃€佸埛鏂板ぉ姘斿苟鎺ㄩ€佸埌鐪奸暅
 */
class WeatherPushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "auto_weather_push"
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        const val TAG = "WeatherPushWorker"

        fun getWeatherDesc(code: Int): String {
            return when (code) {
                0 -> "鏅?
                1, 2, 3 -> "澶氫簯"
                45, 48 -> "闆?
                51, 53, 55 -> "灏忛洦"
                61, 63, 65 -> "闆?
                71, 73, 75 -> "闆?
                80, 81, 82 -> "闃甸洦"
                95, 96, 99 -> "闆烽洦"
                else -> "鏈煡"
            }
        }
    }

    private data class WeatherData(val city: String, val temp: Double, val desc: String)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "鍚庡彴鑷姩鎺ㄩ€佸紑濮?)

            // 1. 鑾峰彇褰撳墠浣嶇疆
            val location = getLastLocation() ?: run {
                Log.w(TAG, "鏃犳硶鑾峰彇浣嶇疆锛岃烦杩囨湰娆℃帹閫?)
                return@withContext Result.retry()
            }

            // 2. 鍙嶆煡鍩庡競鍚?            val cityName = getCityName(location.latitude, location.longitude)
            Log.i(TAG, "浣嶇疆: ${location.latitude}, ${location.longitude}, 鍩庡競: $cityName")

            // 3. 鑾峰彇澶╂皵
            val weather = fetchWeather(location.latitude, location.longitude) ?: run {
                Log.w(TAG, "鑾峰彇澶╂皵澶辫触锛岃烦杩囨湰娆℃帹閫?)
                return@withContext Result.retry()
            }
            val weatherData = WeatherData(cityName, weather.temp, weather.desc)
            Log.i(TAG, "澶╂皵: ${weatherData.city} ${weatherData.temp.toInt()}掳C ${weatherData.desc}")

            // 4. 鎺ㄩ€佸埌鐪奸暅
            val result = sendToGlasses(location.latitude, location.longitude, weatherData)
            Log.i(TAG, "鎺ㄩ€佺粨鏋? $result")

            if (result.startsWith("宸叉帹閫?)) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "鍚庡彴鎺ㄩ€佸紓甯?, e)
            Result.retry()
        }
    }

    private fun getLastLocation(): Location? {
        return try {
            val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var bestLocation: Location? = null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
            bestLocation
        } catch (e: SecurityException) {
            null
        }
    }

    private fun getCityName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let {
                it.locality ?: it.subAdminArea ?: it.adminArea
            } ?: "褰撳墠浣嶇疆"
        } catch (e: Exception) {
            "褰撳墠浣嶇疆"
        }
    }

    private data class WeatherResult(val temp: Double, val desc: String)

    private fun fetchWeather(lat: Double, lon: Double): WeatherResult? {
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?" +
                        "latitude=$lat&longitude=$lon&current_weather=true&timezone=auto"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val current = json.getJSONObject("current_weather")
                val temp = current.getDouble("temperature")
                val code = current.getInt("weathercode")
                WeatherResult(temp, getWeatherDesc(code))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendToGlasses(lat: Double, lon: Double, weather: WeatherData): String {
        var socket: BluetoothSocket? = null
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return "鎵嬫満涓嶆敮鎸佽摑鐗?
            if (!adapter.isEnabled) return "钃濈墮鏈紑鍚?

            val glasses = adapter.bondedDevices.find {
                it.name?.contains("Glasses", ignoreCase = true) == true
            } ?: adapter.bondedDevices.firstOrNull()
                ?: return "鏈壘鍒板凡閰嶅鐨勭溂闀?

            socket = glasses.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
            try {
                socket.connect()
            } catch (_: Exception) {
                socket.close()
                val method = glasses.javaClass.getMethod("createRfcommSocket", Int::class.java)
                socket = method.invoke(glasses, 1) as BluetoothSocket
                socket.connect()
            }

            val json = JSONObject().apply {
                put("lat", lat)
                put("lon", lon)
                put("city", weather.city)
                put("temp", weather.temp)
                put("weather", weather.desc)
            }

            socket.outputStream.write("$json\n".toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
            Thread.sleep(500) // 閬垮厤鍙戦€佸悗绔嬪嵆close瀵艰嚧鏁版嵁涓㈠け
            socket.close()
            "宸叉帹閫? ${weather.city} ${weather.temp.toInt()}掳C ${weather.desc}"
        } catch (e: SecurityException) {
            "钃濈墮鏉冮檺涓嶈冻"
        } catch (e: Exception) {
            "鎺ㄩ€佸け璐? ${e.message}"
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
