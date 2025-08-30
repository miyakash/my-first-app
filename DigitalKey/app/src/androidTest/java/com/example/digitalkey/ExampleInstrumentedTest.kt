package com.example.digitalkey

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.digitalkey", appContext.packageName)

    }

    @Test
    fun covTest1_1Function() {
        CoverageModule().covTest1(101)
    }

    @Test
    fun covTest1_2Function() {
        CoverageModule().covTest1(99)
    }

    @Test
    fun covTest2_1Function() {
        CoverageModule().covTest2(101, 101)
    }

    @Test
    fun covTest2_2Function() {
        CoverageModule().covTest2(99, 99)
    }

    @Test
    fun covTest2_3Function() {
        CoverageModule().covTest2(101, 99)
    }

    @Test
    fun covTest3_1Function() {
        CoverageModule().covTest3(101)
    }

    @Test
    fun covTest3_2Function() {
        CoverageModule().covTest3(99)
    }

    @Test
    fun covTest4Function() {
        CoverageModule().covTest4()
    }

}