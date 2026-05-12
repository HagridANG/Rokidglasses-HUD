package com.mouzhi.headup.companion

import org.junit.Assert.*
import org.junit.Test

class WeatherPushWorkerTest {

    private fun getWeatherDesc(code: Int): String {
        return WeatherPushWorker.getWeatherDesc(code)
    }

    @Test
    fun `code 0 returns 鏅碻() {
        assertEquals("鏅?, getWeatherDesc(0))
    }

    @Test
    fun `code 1 returns 澶氫簯`() {
        assertEquals("澶氫簯", getWeatherDesc(1))
    }

    @Test
    fun `code 2 returns 澶氫簯`() {
        assertEquals("澶氫簯", getWeatherDesc(2))
    }

    @Test
    fun `code 3 returns 澶氫簯`() {
        assertEquals("澶氫簯", getWeatherDesc(3))
    }

    @Test
    fun `code 45 returns 闆綻() {
        assertEquals("闆?, getWeatherDesc(45))
    }

    @Test
    fun `code 51 returns 灏忛洦`() {
        assertEquals("灏忛洦", getWeatherDesc(51))
    }

    @Test
    fun `code 61 returns 闆╜() {
        assertEquals("闆?, getWeatherDesc(61))
    }

    @Test
    fun `code 71 returns 闆猔() {
        assertEquals("闆?, getWeatherDesc(71))
    }

    @Test
    fun `code 95 returns 闆烽洦`() {
        assertEquals("闆烽洦", getWeatherDesc(95))
    }

    @Test
    fun `unknown code returns 鏈煡`() {
        assertEquals("鏈煡", getWeatherDesc(999))
    }
}
