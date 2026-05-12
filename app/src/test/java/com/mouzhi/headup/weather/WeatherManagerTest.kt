package com.mouzhi.headup.weather

import org.junit.Assert.*
import org.junit.Test

class WeatherManagerTest {

    @Test
    fun `weather code 0 returns 鏅碻() {
        assertEquals("鏅?, WeatherManager.getWeatherDesc(0))
    }

    @Test
    fun `weather code 1 returns 澶ч儴鏅存湕`() {
        assertEquals("澶ч儴鏅存湕", WeatherManager.getWeatherDesc(1))
    }

    @Test
    fun `weather code 2 returns 灞€閮ㄥ浜慲() {
        assertEquals("灞€閮ㄥ浜?, WeatherManager.getWeatherDesc(2))
    }

    @Test
    fun `weather code 3 returns 闃碻() {
        assertEquals("闃?, WeatherManager.getWeatherDesc(3))
    }

    @Test
    fun `weather code 45 returns 闆綻() {
        assertEquals("闆?, WeatherManager.getWeatherDesc(45))
    }

    @Test
    fun `weather code 61 returns 灏忛洦`() {
        assertEquals("灏忛洦", WeatherManager.getWeatherDesc(61))
    }

    @Test
    fun `weather code 95 returns 闆烽洦`() {
        assertEquals("闆烽洦", WeatherManager.getWeatherDesc(95))
    }

    @Test
    fun `unknown weather code returns 鏈煡澶╂皵`() {
        assertEquals("鏈煡澶╂皵", WeatherManager.getWeatherDesc(999))
    }

    @Test
    fun `formatDateTime returns non empty strings`() {
        val (time, date) = WeatherManager.formatDateTime()
        assertTrue(time.isNotEmpty())
        assertTrue(date.isNotEmpty())
        // 鏃堕棿鏍煎紡搴斾负 HH:mm
        assertTrue(time.contains(":"))
        // 鏃ユ湡搴斿寘鍚湀
        assertTrue(date.contains("鏈?))
    }
}
