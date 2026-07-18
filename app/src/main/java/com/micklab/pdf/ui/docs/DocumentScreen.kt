package com.micklab.pdf.ui.docs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.micklab.pdf.R
import com.micklab.pdf.ui.ads.AdConsent
import com.micklab.pdf.ui.ads.ConsentManager
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.navigation.PdfDestination

/** Static in-app documents (manual / privacy / licenses), each copyable to the clipboard. */
@Composable
fun DocumentScreen(destination: PdfDestination, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val english = LocalConfiguration.current.locales[0].language == "en"
    val text = documentText(destination, english)

    ToolScaffold(title = stringResource(destination.titleRes), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                Icon(Icons.Default.ContentCopy, null)
                Text("  " + stringResource(R.string.doc_copy_body))
            }
            if (destination == PdfDestination.PRIVACY && AdConsent.privacyOptionsRequired) {
                val activity = LocalContext.current.findActivity()
                OutlinedButton(onClick = { activity?.let { ConsentManager.showPrivacyOptions(it) } }) {
                    Icon(Icons.Default.PrivacyTip, null)
                    Text("  " + stringResource(R.string.doc_ad_privacy_options))
                }
            }
            SectionCard(title = stringResource(destination.titleRes)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun documentText(destination: PdfDestination, english: Boolean): String = when (destination) {
    PdfDestination.MANUAL -> if (english) MANUAL_EN else MANUAL
    PdfDestination.PRIVACY -> if (english) PRIVACY_EN else PRIVACY
    PdfDestination.LICENSES -> if (english) LICENSES_EN else LICENSES
    else -> ""
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val MANUAL = """
■ 操作マニュアル

── 共通 ──
・ホームは「PDF 作成と編集 / PDF 変換と構成 / OCR・AI-OCR / 環境設定」の 4 カテゴリです。カテゴリをタップすると各機能が表示されます。
・入力ファイルは読み取りのみで、元ファイルは変更しません。結果は常に新しいファイルとして保存します。
・出力先は各画面の「出力先」で選べます。未指定なら端末の「Download/PDFToolkit」に保存します。

── PDF 作成と編集 ──
■ PDF 編集（テキスト・画像の追加／既存文字の編集）
1) 「PDF を選択」で編集する PDF を開くか、「白紙から新規作成」で空の A4 を作成します。
2) プレビュー下部の「◀ 前／次 ▶」でページを移動します。
3) テキストを追加: 「テキスト」欄に文字を入力（改行可）→ サイズ・色を選び「追加」。プレビュー上に置かれるのでドラッグで移動します。
4) 画像を追加: 「画像」で画像を選ぶとプレビューに配置されます。ドラッグで移動できます。
5) 既存の文字を編集: プレビュー上の文字をタップすると選択されます。「置換後の文」を入力、または「元の文字を削除」を選びます。サイズ・色も変更できます。
   ・同じ書体・同じ文字集合ならその場で置換します。
   ・表示できない文字・移動・サイズ/色の変更時は、元のサイズ・色を引き継いで文全体を描き直します（日本語フォントを使用）。
6) レイヤー: 追加・編集した項目は「レイヤー」に一覧表示されます。行をタップで選択、× で取消できます。
7) 「決定」を押すと、その時点の編集を一時 PDF に反映し、実際の見た目でプレビューを更新します（重ね描きのシミュレーションではありません）。
8) 「適用して保存」で最終的な PDF を出力します。
※ テキストの追加・編集には日本語フォント(Noto Sans JP)の取得が必要です（初回のみ通信、以後オフライン）。フォント未取得なら「フォントを取得」を押してください。

── PDF 変換と構成 ──
・分解（ページ抽出）: PDF を開き、抽出したいページを選択 → 1 つにまとめる／ページごとに分ける、を選んで出力。
・統合（結合）: 複数の PDF を選び、順序を整えて 1 つに結合します。
・並べ替え: サムネイルをドラッグしてページ順を入れ替え、新しい PDF として保存します。
・PDF 画像化: 各ページを PNG / JPEG に変換します。DPI（解像度）を指定できます。
・画像 → PDF 化: 複数の画像を選び、順序を指定して 1 つの PDF にまとめます。

── OCR / AI-OCR ──
・OCR / テキスト抽出: PDF・画像から文字を抽出します。埋め込みテキスト（元からある文字）と OCR（画像認識）を区別して JSON でも出力します。エンジンは Tesseract / PaddleOCR / ローカル LLM Vision から選べます。大きな文書はバックグラウンド実行が可能です。
・PDF サマリ（要約）: ファイル全体・ページごとを LLM で要約します。「OCR→LLM」または「Vision（ページ画像を直接 LLM へ）」を選べます。
・エンジンのモデル取得や LLM の接続先は「環境設定 → OCR 設定・モデル管理」で行います。

── 環境設定 ──
・OCR 設定・モデル管理: Tesseract / PaddleOCR のモデル取得、日本語フォントの取得、LLM(Ollama / OpenAI 互換)の接続 URL・モデル選択・接続確認。
・操作マニュアル / プライバシーポリシー / 権利表記: 本ドキュメント類（各画面で本文をコピーできます）。

── オフラインについて ──
・PDF・画像処理は端末内で完結します。通信するのは「OCR モデル・フォントの初回ダウンロード」と「LLM を利用する場合の設定サーバへの送信」だけです（既定の LLM 接続先は端末内 127.0.0.1）。
""".trimIndent()

private val PRIVACY = """
■ プライバシーポリシー

・本アプリの PDF・画像・OCR の処理は、原則として端末内で完結します。ファイルの内容を当方のサーバへ送信・収集することはありません。
・ネットワーク通信を行うのは次の場合のみです:
  - OCR モデル（Tesseract / PaddleOCR）や日本語フォントの初回ダウンロード。
  - 「ローカル LLM Vision」/「サマリ」を利用する場合の、設定した LLM サーバへのリクエスト。既定の接続先は端末内(127.0.0.1)ですが、外部サーバを指定した場合はページ画像や抽出テキストがそのサーバへ送信されます。送信先はご自身の設定に依存します。
・入力ファイルは読み取りのみで、元ファイルを書き換えることはありません。出力は常に新規ファイルとして作成します。
・アプリ独自の解析・広告・トラッキングは行いません。

ご不明点はアプリ提供元へお問い合わせください。
""".trimIndent()

private val LICENSES = """
■ 権利表記・オープンソースライセンス

本アプリは以下のオープンソースソフトウェア/フォントを利用しています。各ライセンスの全文は各プロジェクトの配布物をご参照ください。

・Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
・Tesseract OCR (tesseract4android) — Apache License 2.0
・ONNX Runtime (onnxruntime-android) — MIT License
・PaddleOCR モデル (PP-OCR) — Apache License 2.0
・Noto Sans JP フォント — SIL Open Font License 1.1
・AndroidX / Jetpack Compose / Material Components — Apache License 2.0
・Dagger Hilt — Apache License 2.0
・Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
・Coil — Apache License 2.0

Noto Sans JP は SIL OFL 1.1 に基づき、アプリへの同梱および PDF への埋め込みが許諾されています。
""".trimIndent()

private val MANUAL_EN = """
■ User Manual

── Common ──
• The home screen has 4 categories: "Create & edit PDF / Convert & compose PDF / OCR & AI-OCR / Settings". Tap a category to see its tools.
• Input files are read-only; the original is never modified. Results are always saved as new files.
• Choose the output location in each screen's "Output folder". If unset, files are saved to "Download/PDFToolkit" on the device.

── Create & edit PDF ──
■ Edit PDF (add text/images, edit existing text)
1) Open a PDF with "Choose PDF", or make an empty A4 with "Start from blank".
2) Move between pages with "◀ Prev / Next ▶" below the preview.
3) Add text: type into the "Text" field (line breaks allowed), pick size/color, then "Add". It is placed on the preview; drag to move it.
4) Add an image: choose one under "Image" and it is placed on the preview. Drag to move it.
5) Edit existing text: tap text on the preview to select it. Enter "Replacement text", or choose "Delete the original text". You can also change size/color.
   • If the font and character set match, it is replaced in place.
   • For characters that can't be shown, moves, or size/color changes, the whole run is redrawn, keeping the original size and color (using the Japanese font).
6) Layers: added/edited items are listed under "Layers". Tap a row to select it, or × to remove it.
7) Tap "Apply" to bake the current edits into a temporary PDF and refresh the preview with the real appearance (not an overlay simulation).
8) "Apply and save" outputs the final PDF.
* Adding/editing text needs the Japanese font (Noto Sans JP) — downloaded once, offline afterward. If it isn't present, tap "Get the font".

── Convert & compose PDF ──
• Split (extract pages): open a PDF, select the pages to extract, then choose "combine into one" or "one per page" and export.
• Merge: choose several PDFs, arrange the order, and combine into one.
• Reorder: drag thumbnails to change the page order and save as a new PDF.
• PDF to images: convert each page to PNG / JPEG. You can set the DPI (resolution).
• Images to PDF: choose several images, set the order, and combine into one PDF.

── OCR / AI-OCR ──
• OCR / text extraction: extract text from PDFs/images. Embedded text (already in the file) and OCR (image recognition) are distinguished, and can also be exported as JSON. Engines: Tesseract / PaddleOCR / local LLM Vision. Large documents can run in the background.
• PDF summary: summarize the whole file or per page with an LLM. Choose "OCR→LLM" or "Vision (page images sent directly to the LLM)".
• Get engine models and set the LLM connection under "Settings → OCR settings and models".

── Settings ──
• OCR settings and models: download Tesseract / PaddleOCR models and the Japanese font, and set the LLM (Ollama / OpenAI-compatible) connection URL, model, and connection test.
• User manual / Privacy policy / Licenses: these documents (you can copy the body on each screen).

── About offline use ──
• PDF and image processing run entirely on the device. The only network use is "the first download of OCR models / fonts" and "sending to the configured server when using an LLM" (the default LLM endpoint is 127.0.0.1 on the device).
""".trimIndent()

private val PRIVACY_EN = """
■ Privacy Policy

• This app's PDF, image, and OCR processing complete on the device as a rule. File contents are never sent to or collected by our servers.
• Network communication happens only in these cases:
  - The first download of OCR models (Tesseract / PaddleOCR) or the Japanese font.
  - Requests to the LLM server you configured when using "local LLM Vision" / "summary". The default endpoint is on-device (127.0.0.1), but if you specify an external server, page images and extracted text are sent to that server. The destination depends on your own settings.
• Input files are read-only; the original file is never rewritten. Output is always created as a new file.
• The app performs no proprietary analytics, ads, or tracking.

For questions, please contact the app provider.
""".trimIndent()

private val LICENSES_EN = """
■ Credits and Open-Source Licenses

This app uses the following open-source software/fonts. For the full text of each license, please refer to each project's distribution.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• PaddleOCR models (PP-OCR) — Apache License 2.0
• Noto Sans JP font — SIL Open Font License 1.1
• AndroidX / Jetpack Compose / Material Components — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

Noto Sans JP is bundled in the app and embedded into PDFs under SIL OFL 1.1.
""".trimIndent()
