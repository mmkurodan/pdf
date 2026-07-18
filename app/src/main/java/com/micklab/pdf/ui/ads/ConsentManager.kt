package com.micklab.pdf.ui.ads

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.micklab.pdf.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Compose-observable ad-consent state, updated by [ConsentManager]. */
object AdConsent {
    /** True once consent is resolved and the Mobile Ads SDK may request ads. */
    var canRequestAds by mutableStateOf(false)
        internal set

    /** True when the user must be offered a way to change consent (EEA "manage options"). */
    var privacyOptionsRequired by mutableStateOf(false)
        internal set
}

/**
 * Drives Google's User Messaging Platform (UMP) consent flow.
 *
 * In regulated regions (EEA/UK) it shows a consent form before ads load; in
 * other regions it is effectively a no-op and ads load normally. Ads must not
 * be requested until [AdConsent.canRequestAds] turns true.
 */
object ConsentManager {

    private val adsInitTriggered = AtomicBoolean(false)

    /**
     * Requests the latest consent status, shows the form if required, and invokes
     * [onCanRequestAds] exactly once — when ads first become allowed — so the
     * caller can initialize the Mobile Ads SDK.
     */
    fun gatherConsent(activity: Activity, onCanRequestAds: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        val params = ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // Debug settings only affect devices whose hashed ID is listed
                    // below. Add your device's ID (printed in logcat by the SDK) to
                    // preview the EEA form during development.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            // .addTestDeviceHashedId("TEST-DEVICE-HASHED-ID")
                            .build(),
                    )
                }
            }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    // A form error is ignored: we fall back to the stored consent.
                    applyState(consentInformation, onCanRequestAds)
                }
            },
            {
                // Update failed (e.g. offline): honour any previously stored consent.
                applyState(consentInformation, onCanRequestAds)
            },
        )

        // A returning, already-consented user can load ads without waiting.
        applyState(consentInformation, onCanRequestAds)
    }

    private fun applyState(consentInformation: ConsentInformation, onCanRequestAds: () -> Unit) {
        AdConsent.privacyOptionsRequired =
            consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consentInformation.canRequestAds()) {
            AdConsent.canRequestAds = true
            if (adsInitTriggered.compareAndSet(false, true)) onCanRequestAds()
        }
    }

    /** Re-opens the consent form so the user can change their choice. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { /* error ignored */ }
    }
}
