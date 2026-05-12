package com.mouzhi.headup.utils

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BluetoothLocationReceiverTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `parse valid JSON with all fields`() {
        val receiver = BluetoothLocationReceiver(context)
        val json = """{"lat":31.2304,"lon":121.4737,"city":"涓婃捣","temp":25.5,"weather":"鏅?}"""
        val data = receiver.parseLocationJson(json)

        assertNotNull(data)
        assertEquals(31.2304, data!!.lat, 0.0001)
        assertEquals(121.4737, data.lon, 0.0001)
        assertEquals("涓婃捣", data.city)
        assertEquals(25.5, data.temp, 0.01)
        assertEquals("鏅?, data.weather)
    }

    @Test
    fun `parse valid JSON without optional fields`() {
        val receiver = BluetoothLocationReceiver(context)
        val json = """{"lat":31.2304,"lon":121.4737}"""
        val data = receiver.parseLocationJson(json)

        assertNotNull(data)
        assertEquals("褰撳墠浣嶇疆", data!!.city)
        assertTrue(data.temp.isNaN())
        assertEquals("", data.weather)
    }

    @Test
    fun `parse JSON with invalid lat returns null`() {
        val receiver = BluetoothLocationReceiver(context)
        val json = """{"lat":"invalid","lon":121.4737}"""
        val data = receiver.parseLocationJson(json)
        assertNull(data)
    }

    @Test
    fun `parse JSON with NaN lat returns null`() {
        val receiver = BluetoothLocationReceiver(context)
        val json = """{"lat":NaN,"lon":121.4737}"""
        val data = receiver.parseLocationJson(json)
        assertNull(data)
    }

    @Test
    fun `parse empty string returns null`() {
        val receiver = BluetoothLocationReceiver(context)
        val data = receiver.parseLocationJson("")
        assertNull(data)
    }

    @Test
    fun `parse malformed JSON returns null`() {
        val receiver = BluetoothLocationReceiver(context)
        val data = receiver.parseLocationJson("{not json")
        assertNull(data)
    }

    @Test
    fun `saveLocationData stores all fields`() {
        val receiver = BluetoothLocationReceiver(context)
        val data = BluetoothLocationReceiver.LocationData(
            lat = 31.2304,
            lon = 121.4737,
            city = "涓婃捣",
            temp = 25.5,
            weather = "鏅?
        )
        receiver.saveLocationData(data)

        // 楠岃瘉璇诲彇
        assertTrue(receiver.hasValidLocation())
        assertEquals(31.2304, receiver.getLatitude(), 0.0001)
        assertEquals(121.4737, receiver.getLongitude(), 0.0001)
        assertEquals("涓婃捣", receiver.getCity())
        assertEquals(25.5, receiver.getTemp(), 0.01)
        assertEquals("鏅?, receiver.getWeather())
    }

    @Test
    fun `saveLocationData with NaN temp skips temp field`() {
        val receiver = BluetoothLocationReceiver(context)
        val data = BluetoothLocationReceiver.LocationData(
            lat = 31.2304,
            lon = 121.4737,
            city = "鍖椾含",
            temp = Double.NaN,
            weather = ""
        )
        receiver.saveLocationData(data)

        assertTrue(receiver.getTemp().isNaN())
        assertEquals("", receiver.getWeather())
    }

    @Test
    fun `getTemp returns NaN when no data`() {
        val receiver = BluetoothLocationReceiver(context)
        assertTrue(receiver.getTemp().isNaN())
    }

    @Test
    fun `getCity returns empty string when no data`() {
        val receiver = BluetoothLocationReceiver(context)
        assertEquals("", receiver.getCity())
    }
}
