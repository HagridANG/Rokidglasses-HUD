package com.mouzhi.headup.companion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val REQUEST_PERMISSIONS = 1001
        private const val PREFS_NAME = "HeadUp_companion"
        private const val KEY_AUTO_PUSH = "auto_push_enabled"
    }

    private lateinit var statusText: TextView
    private lateinit var pushButton: Button
    private lateinit var autoPushSwitch: Switch
    private lateinit var cityInput: EditText
    private lateinit var pushCityButton: Button
    private lateinit var refreshButton: Button

    private var currentLocation: Location? = null
    private var currentWeather: WeatherData? = null

    data class WeatherData(val city: String, val temp: Double, val desc: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        pushButton = findViewById(R.id.pushButton)
        autoPushSwitch = findViewById(R.id.autoPushSwitch)
        cityInput = findViewById(R.id.cityInput)
        pushCityButton = findViewById(R.id.pushCityButton)
        refreshButton = findViewById(R.id.refreshButton)

        checkPermissions()
        startLocationUpdates()
        loadAutoPushState()

        pushButton.setOnClickListener { pushCurrentLocation() }

        refreshButton.setOnClickListener {
            statusText.text = "姝ｅ湪鍒锋柊..."
            currentLocation?.let { loc ->
                fetchWeather(loc.latitude, loc.longitude)
            } ?: run {
                statusText.text = "绛夊緟 GPS 瀹氫綅..."
            }
        }

        pushCityButton.setOnClickListener {
            val cityName = cityInput.text.toString().trim()
            if (cityName.isEmpty()) {
                Toast.makeText(this, "璇疯緭鍏ュ煄甯傚悕", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pushCustomCity(cityName)
        }

        autoPushSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveAutoPushState(isChecked)
            if (isChecked) {
                startAutoPushWorker()
                Toast.makeText(this, "宸插紑鍚瘡30鍒嗛挓鑷姩鎺ㄩ€?, Toast.LENGTH_SHORT).show()
            } else {
                stopAutoPushWorker()
                Toast.makeText(this, "宸插叧闂嚜鍔ㄦ帹閫?, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun startLocationUpdates() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location ->
            currentLocation = location
            statusText.text = "浣嶇疆: ${location.latitude.toFloat()}, ${location.longitude.toFloat()}\n姝ｅ湪鑾峰彇澶╂皵..."
            fetchWeather(location.latitude, location.longitude)
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5000L, 10f, listener, Looper.getMainLooper()
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 5000L, 10f, listener, Looper.getMainLooper()
            )
            statusText.text = "绛夊緟 GPS 瀹氫綅..."
        } catch (e: SecurityException) {
            statusText.text = "瀹氫綅鏉冮檺涓嶈冻"
        }
    }

    private fun fetchWeather(lat: Double, lon: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                    val desc = getWeatherDesc(code)
                    val cityName = getCityName(lat, lon)
                    currentWeather = WeatherData(cityName, temp, desc)

                    withContext(Dispatchers.Main) {
                        statusText.text = "浣嶇疆: ${lat.toFloat()}, ${lon.toFloat()}\n" +
                                "澶╂皵: $cityName ${temp.toInt()}掳C $desc\n鐐瑰嚮鎺ㄩ€佹寜閽彂閫佸埌鐪奸暅"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "鑾峰彇澶╂皵澶辫触: ${e.message}"
                }
            }
        }
    }

    private fun getCityName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let {
                it.locality ?: it.subAdminArea ?: it.adminArea
            } ?: "褰撳墠浣嶇疆"
        } catch (e: Exception) {
            "褰撳墠浣嶇疆"
        }
    }

    private fun getWeatherDesc(code: Int): String {
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

    /** 鎺ㄩ€佸綋鍓?GPS 浣嶇疆鐨勫ぉ姘?*/
    private fun pushCurrentLocation() {
        val loc = currentLocation
        val weather = currentWeather
        if (loc == null || weather == null) {
            Toast.makeText(this, "绛夊緟瀹氫綅涓?..", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val result = sendToGlasses(loc.latitude, loc.longitude, weather)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 鎺ㄩ€佺敤鎴疯緭鍏ョ殑鍩庡競澶╂皵 */
    private fun pushCustomCity(cityName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = pushCustomCityInternal(cityName)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pushCustomCityInternal(cityName: String): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(cityName, 1)
            val address = addresses?.firstOrNull()
                ?: return "鏃犳硶鎵惧埌璇ュ煄甯?

            val lat = address.latitude
            val lon = address.longitude
            val resolvedCity = address.locality ?: address.subAdminArea ?: address.adminArea ?: cityName

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
                val desc = getWeatherDesc(code)

                val weather = WeatherData(resolvedCity, temp, desc)
                sendToGlasses(lat, lon, weather)
            } else {
                "鑾峰彇澶╂皵澶辫触: HTTP ${conn.responseCode}"
            }
        } catch (e: Exception) {
            "鎺ㄩ€佸け璐? ${e.message}"
        }
    }

    private fun sendToGlasses(lat: Double, lon: Double, weather: WeatherData): String {
        var socket: BluetoothSocket? = null
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return "[1]鎵嬫満涓嶆敮鎸佽摑鐗?
            if (!adapter.isEnabled) return "[2]璇峰厛寮€鍚摑鐗?

            val glasses = adapter.bondedDevices.find {
                it.name?.contains("Glasses", ignoreCase = true) == true
            } ?: adapter.bondedDevices.firstOrNull()
                ?: return "[3]鏈壘鍒板凡閰嶅鐨勭溂闀滆澶囷紝璇峰厛閰嶅"

            android.util.Log.i("HeadUpCompanion", "鎵惧埌鐪奸暅: ${glasses.name} (${glasses.address})")

            socket = glasses.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
            try {
                android.util.Log.i("HeadUpCompanion", "灏濊瘯RFCOMM杩炴帴...")
                socket.connect()
                android.util.Log.i("HeadUpCompanion", "RFCOMM杩炴帴鎴愬姛")
            } catch (e: Exception) {
                android.util.Log.w("HeadUpCompanion", "RFCOMM杩炴帴澶辫触: ${e.message}, 灏濊瘯鍙嶅皠fallback")
                try { socket.close() } catch (_: Exception) {}
                val method = glasses.javaClass.getMethod("createRfcommSocket", Int::class.java)
                socket = method.invoke(glasses, 1) as BluetoothSocket
                socket.connect()
                android.util.Log.i("HeadUpCompanion", "鍙嶅皠fallback杩炴帴鎴愬姛")
            }

            val json = JSONObject().apply {
                put("lat", lat)
                put("lon", lon)
                put("city", weather.city)
                put("temp", weather.temp)
                put("weather", weather.desc)
            }
            val jsonStr = "$json\n"
            android.util.Log.i("HeadUpCompanion", "鍙戦€丣SON: $jsonStr")

            socket.outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
            android.util.Log.i("HeadUpCompanion", "鏁版嵁宸瞗lush锛岀瓑寰?00ms纭繚钃濈墮閾捐矾鍙戦€佸畬鎴?..")
            Thread.sleep(500) // 閬垮厤鍙戦€佸悗绔嬪嵆close瀵艰嚧鏁版嵁涓㈠け
            socket.close()
            android.util.Log.i("HeadUpCompanion", "鍙戦€佸畬鎴愶紝socket宸插叧闂?)
            "宸叉帹閫? ${weather.city} ${weather.temp.toInt()}掳C ${weather.desc}"
        } catch (e: SecurityException) {
            android.util.Log.e("HeadUpCompanion", "钃濈墮鏉冮檺涓嶈冻", e)
            "[4]钃濈墮鏉冮檺涓嶈冻"
        } catch (e: Exception) {
            android.util.Log.e("HeadUpCompanion", "鎺ㄩ€佸紓甯?, e)
            "[5]鎺ㄩ€佸け璐? ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    // ====== 鑷姩鎺ㄩ€?Worker 绠＄悊 ======

    private fun loadAutoPushState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_AUTO_PUSH, false)
        autoPushSwitch.isChecked = enabled
        if (enabled) {
            startAutoPushWorker()
        }
    }

    private fun saveAutoPushState(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_PUSH, enabled)
            .apply()
    }

    private fun startAutoPushWorker() {
        val request = PeriodicWorkRequestBuilder<WeatherPushWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeatherPushWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun stopAutoPushWorker() {
        WorkManager.getInstance(this).cancelUniqueWork(WeatherPushWorker.WORK_NAME)
    }
}
