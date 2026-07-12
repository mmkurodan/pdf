package com.micklab.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PdfToolsInstrumentedTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun appContextHasExpectedPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertThat(context.packageName).isEqualTo("com.micklab.pdf")
    }
}
