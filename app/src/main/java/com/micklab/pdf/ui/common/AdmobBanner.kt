package com.micklab.pdf.ui.common

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.micklab.pdf.ui.ads.AdConsent

/** トップメニューに置く AdMob バナー。AndroidView で AdView をラップする。 */
@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
    // 現在はテスト用バナーID。リリース前に本番ID(ca-app-pub-3062461524889254/2124819628)へ戻すこと。
    adUnitId: String = "ca-app-pub-3940256099942544/6300978111",
) {
    // UMP 同意で広告リクエストが許可されるまでは AdView を生成・読込しない。
    if (!AdConsent.canRequestAds) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build()) // AdRequest → loadAd
            }
        },
    )
}
