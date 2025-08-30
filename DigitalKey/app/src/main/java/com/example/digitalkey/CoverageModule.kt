package com.example.digitalkey

import android.util.Log

class CoverageModule {
    fun covTest1(a: Int): Int {
        if (a > 100){
            Log.d("CoverageModule", "covTest1")
        }
        return a
    }

    fun covTest2(a: Int, b: Int): Int {
        if (a > 100 && b > 100){
            Log.d("CoverageModule", "covTest2_true")
        } else {
            Log.d("CoverageModule", "covTest2_false")
        }
        return a + b
    }

    fun covTest3(a: Int): Int {
        if (a > 100){
            Log.d("CoverageModule", "covTest2_true")
        } else {
            Log.d("CoverageModule", "covTest2_false")
        }
        return a
    }

    fun covTest4(): Int {
        Log.d("CoverageModule", "covTest3")
        return 0
    }
}