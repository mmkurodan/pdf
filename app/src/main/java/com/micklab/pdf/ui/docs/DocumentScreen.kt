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
    val lang = LocalConfiguration.current.locales[0].language
    val text = documentText(destination, lang)

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

private fun documentText(destination: PdfDestination, lang: String): String = when (destination) {
    PdfDestination.MANUAL -> manualForLang(lang)
    PdfDestination.PRIVACY -> privacyForLang(lang)
    PdfDestination.LICENSES -> licensesForLang(lang)
    else -> ""
}

private fun manualForLang(lang: String) = when (lang) {
    "ja" -> MANUAL
    "fr" -> MANUAL_FR
    "de" -> MANUAL_DE
    "es" -> MANUAL_ES
    "it" -> MANUAL_IT
    "pt" -> MANUAL_PT
    "zh" -> MANUAL_ZH
    "ko" -> MANUAL_KO
    else -> MANUAL_EN
}

private fun privacyForLang(lang: String) = when (lang) {
    "ja" -> PRIVACY
    "fr" -> PRIVACY_FR
    "de" -> PRIVACY_DE
    "es" -> PRIVACY_ES
    "it" -> PRIVACY_IT
    "pt" -> PRIVACY_PT
    "zh" -> PRIVACY_ZH
    "ko" -> PRIVACY_KO
    else -> PRIVACY_EN
}

private fun licensesForLang(lang: String) = when (lang) {
    "ja" -> LICENSES
    "fr" -> LICENSES_FR
    "de" -> LICENSES_DE
    "es" -> LICENSES_ES
    "it" -> LICENSES_IT
    "pt" -> LICENSES_PT
    "zh" -> LICENSES_ZH
    "ko" -> LICENSES_KO
    else -> LICENSES_EN
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
■ PDF 編集（テキスト・画像・図形・描画の追加／既存文字の編集）
1) 「PDF を選択」で編集する PDF を開くか、「白紙から新規作成」で用紙サイズ・背景色を指定して空ページを作成します。
   ・既存 PDF を開いた時／新規作成時は、PDF の仕様上、テキストの追加・変更・移動でフォントや書式が元と変わる場合がある旨の注意が表示されます。
2) プレビュー下部の「◀ 前／次 ▶」でページを移動します。
3) テキストを追加: 「テキスト」欄に文字を入力（改行可）→ サイズ・色・スタイル・フォントを選び「追加」。プレビュー上に置かれるのでドラッグで移動します。
4) 画像を追加: 「画像」で画像を選ぶとプレビューに配置されます。ドラッグで移動、拡大縮小・回転できます。
5) 図形を追加: 「図形」で四角／楕円、枠線色・塗り・線の太さを選び、キャンバス上をドラッグして描きます。
6) 描画: 「描画」でブラシの色・太さを選び、フリーハンドで描きます。
7) 背景色: 「背景」で色を選び「適用」すると、ページ全体に背景色を敷きます。
8) 既存の文字を編集: プレビュー上の文字をタップすると選択されます。「置換後の文」を入力、または「元の文字を削除」を選びます。サイズ・色・スタイル・フォントも変更できます。
   ・同じ書体・同じ文字集合ならその場で置換します。
   ・表示できない文字・移動・サイズ/色/フォント/スタイルの変更時は、元の書式（フォント・サイズ・色・スタイル）を引き継いで文全体を描き直します。明示的に変更しない限り元の見た目を保ちます。
9) レイヤー: 追加・編集した項目は「レイヤー」に一覧表示されます。行をタップで選択、▲▼で重なり順の変更、× で削除できます。細い描画はタップ選択が難しいため、レイヤー一覧から選べます。
10) 「決定」を押すと、その時点の編集を一時 PDF に反映し、実際の見た目でプレビューを更新します。図形・描画は「決定」後もレイヤーとして残り、いつでも選択・並べ替え・削除できます（最終保存時にまとめて反映）。
11) 「適用して保存」で最終的な PDF を出力します。編集を保存せずに前の画面へ戻ろうとすると、確認が表示されます。
※ テキストの追加・編集には埋め込みフォントの取得が必要です。書体は Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2（すべて SIL OFL）から選べ、テキストごとに指定できます。各書体は初回のみ通信し以後オフライン。未取得の書体は編集画面や「環境設定 → OCR 設定・モデル管理」から取得できます。アラビア語・ヘブライ語(右→左)は入力時に自動で連結整形・双方向並べ替えを行います。

── PDF 変換と構成 ──
・分解（ページ抽出）: PDF を開き、抽出したいページを選択 → 1 つにまとめる／ページごとに分ける、を選んで出力。
・統合（結合）: 複数の PDF を選び、順序を整えて 1 つに結合します。
・並べ替え: サムネイルをドラッグしてページ順を入れ替え、新しい PDF として保存します。
・PDF 画像化: 各ページを PNG / JPEG に変換します。DPI（解像度）を指定できます。
・画像 → PDF 化: 複数の画像を選び、順序を指定して 1 つの PDF にまとめます。

── OCR / AI-OCR ──
・OCR / テキスト抽出: PDF・画像から文字を抽出します。埋め込みテキスト（元からある文字）と OCR（画像認識）を区別して JSON でも出力します。エンジンは Tesseract / PaddleOCR から選べます。Tesseract は日本語・英語・中国語(簡体/繁体)・韓国語・ギリシャ語・ロシア語(キリル)・アラビア語・ヘブライ語・数式(equ)に対応し、PaddleOCR は日本語・英語・中国語(簡体)・韓国語に対応します（各言語モデルは初回のみ取得）。大きな文書はバックグラウンド実行が可能です。
・PDF サマリ（要約）: ファイル全体・ページごとを LLM で要約します。「OCR→LLM」または「Vision（ページ画像を直接 LLM へ）」を選べます。
・エンジンのモデル取得や LLM の接続先は「環境設定 → OCR 設定・モデル管理」で行います。OCR モデルが未取得のまま起動した場合は、設定画面への案内が表示されます（「今後表示しない」で非表示にできます）。

── 環境設定 ──
・OCR 設定・モデル管理: Tesseract / PaddleOCR の言語モデル取得（日本語・英語・中国語・韓国語）、編集用フォント（複数書体）の取得、LLM(Ollama / OpenAI 互換)の接続 URL・モデル選択・接続確認。順序は Tesseract → PaddleOCR → LLM です。
・操作マニュアル / プライバシーポリシー / 権利表記: 本ドキュメント類（各画面で本文をコピーできます）。

── オフラインについて ──
・PDF・画像処理は端末内で完結します。通信するのは「OCR モデル・フォントの初回ダウンロード」と「LLM を利用する場合の設定サーバへの送信」だけです（既定の LLM 接続先は端末内 127.0.0.1）。

── エキスパートモード / ローカル OCR API ──
「環境設定 → OCR 設定・モデル管理」の「エキスパートモード」を有効にすると、この端末が OCR API サーバーになります。同じ LAN 上のPC・他端末から OCR リクエストを送れます。

■ エンドポイント
  POST http://<端末IP>:<ポート>/ocr
  POST http://127.0.0.1:<ポート>/ocr  （端末内からの場合）

■ リクエスト形式
  Content-Type: multipart/form-data

  フィールド:
    file   (必須) 画像ファイル（PNG/JPEG/WebP/BMP）または PDF
    engine (省略可) tesseract（既定）| paddleocr | llm
    lang   (省略可) 言語コード、カンマ区切りで複数指定可（既定: eng）
             例: eng / jpn / jpn,eng / chi_sim / kor

  ※ engine=tesseract で lang=jpn を使うには Tesseract 日本語モデルが必要です。
  ※ engine=paddleocr / llm も同様に、各エンジンのモデル・設定が必要です。

■ レスポンス（成功時 HTTP 200）
  Content-Type: application/json
  {
    "pages": [
      {
        "page": 1,
        "text": "認識されたテキスト",
        "confidence": 0.95,
        "source": "OCR"
      }
    ],
    "engine": "Tesseract",
    "languages": ["jpn"],
    "pageCount": 1
  }

■ エラーレスポンス
  HTTP 400: {"error":"Missing 'file' field"}    — file フィールドが未指定
  HTTP 400: {"error":"Could not decode image"}  — 画像デコード失敗
  HTTP 400: {"error":"Unknown engine: xxx"}     — engine 名が不正
  HTTP 500: {"error":"..."}                     — OCR 処理エラー

■ 使用例（curl）

# JPEG 画像を英語 Tesseract で OCR
curl -X POST http://192.168.1.10:8765/ocr \
     -F "file=@document.jpg" \
     -F "engine=tesseract" \
     -F "lang=eng"

# PDF を日本語+英語 Tesseract で OCR（複数ページ対応）
curl -X POST http://192.168.1.10:8765/ocr \
     -F "file=@scan.pdf;type=application/pdf" \
     -F "lang=jpn,eng"

# PaddleOCR で中国語
curl -X POST http://192.168.1.10:8765/ocr \
     -F "file=@chinese.png" \
     -F "engine=paddleocr" \
     -F "lang=chi_sim"

# LLM Vision（engine=llm）— OCR 設定で LLM を設定済みの場合
curl -X POST http://127.0.0.1:8765/ocr \
     -F "file=@image.jpg" \
     -F "engine=llm" \
     -F "lang=jpn"

■ Python 使用例
import requests

with open("document.pdf", "rb") as f:
    resp = requests.post(
        "http://192.168.1.10:8765/ocr",
        files={"file": ("document.pdf", f, "application/pdf")},
        data={"engine": "tesseract", "lang": "jpn,eng"},
    )
result = resp.json()
for page in result["pages"]:
    print(f"Page {page['page']}: {page['text'][:80]}...")

■ ヘルスチェック
  GET http://<端末IP>:<ポート>/health
  → {"status":"ok"}

■ 注意事項
・このAPI は信頼できるローカルネットワーク内でのみ使用してください。認証はありません。
・大きな PDF は処理に時間がかかります（1ページあたり数秒〜数十秒）。
・プロセス Kill 対策として通知権限が必要です。通知からサーバーを停止できます。
・端末のスリープ中は処理が中断される場合があります。
""".trimIndent()

private val PRIVACY = """
■ プライバシーポリシー

・本アプリの PDF・画像・OCR の処理は、原則として端末内で完結します。ファイルの内容を当方のサーバへ送信・収集することはありません。
・ネットワーク通信を行うのは次の場合のみです:
  - OCR モデル（Tesseract / PaddleOCR）やフォントの初回ダウンロード。
  - 「ローカル LLM Vision」/「サマリ」を利用する場合の、設定した LLM サーバへのリクエスト。既定の接続先は端末内(127.0.0.1)ですが、外部サーバを指定した場合はページ画像や抽出テキストがそのサーバへ送信されます。送信先はご自身の設定に依存します。
  - 広告の表示。本アプリは Google AdMob を利用して広告を配信します。
・入力ファイルは読み取りのみで、元ファイルを書き換えることはありません。出力は常に新規ファイルとして作成します。
・広告について: 本アプリは Google AdMob（提供: Google LLC）を利用します。広告配信のため、Google は広告識別子(AAID)・IP アドレス・端末情報・利用状況などを取得・利用することがあります。これらは第三者である Google により Google のプライバシーポリシーに基づいて扱われます。ファイルの内容そのものが広告目的で送信されることはありません。
  - Google によるデータの取り扱い: https://policies.google.com/technologies/partner-sites
  - 広告のパーソナライズは、端末の「設定 → Google → 広告」から広告 ID のリセット/オプトアウトで制限できます。
・上記の広告配信を除き、アプリ独自の解析・トラッキングは行いません。

ご不明点はアプリ提供元へお問い合わせください。
""".trimIndent()

private val LICENSES = """
■ 権利表記・オープンソースライセンス

本アプリは以下のオープンソースソフトウェア/フォントを利用しています。各ライセンスの全文は各プロジェクトの配布物をご参照ください。

・Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
・Tesseract OCR (tesseract4android) — Apache License 2.0
・ONNX Runtime (onnxruntime-android) — MIT License
・PaddleOCR モデル (PP-OCR / RapidOCR 配布) — Apache License 2.0
・Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — Google の利用規約に従います
・フォント（すべて SIL Open Font License 1.1）:
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
・AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
・Dagger Hilt — Apache License 2.0
・Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
・Coil — Apache License 2.0

上記フォントはいずれも SIL OFL 1.1 に基づき、アプリへの同梱および PDF への埋め込み（有償アプリを含む）が許諾されています。
""".trimIndent()

private val MANUAL_EN = """
■ User Manual

── Common ──
• The home screen has 4 categories: "Create & edit PDF / Convert & compose PDF / OCR & AI-OCR / Settings". Tap a category to see its tools.
• Input files are read-only; the original is never modified. Results are always saved as new files.
• Choose the output location in each screen's "Output folder". If unset, files are saved to "Download/PDFToolkit" on the device.

── Create & edit PDF ──
■ Edit PDF (add text/images/shapes/drawing, edit existing text)
1) Open a PDF with "Choose PDF", or make an empty page with "Start from blank" (choose page size and background color).
   • When opening an existing PDF or creating a new one, a note warns that — because of how the PDF format works — adding, changing, or moving text may alter the font or formatting.
2) Move between pages with "◀ Prev / Next ▶" below the preview.
3) Add text: type into the "Text" field (line breaks allowed), pick size/color/style/font, then "Add". It is placed on the preview; drag to move it.
4) Add an image: choose one under "Image" and it is placed on the preview. Drag to move it; you can scale and rotate it.
5) Add a shape: under "Shape" pick rectangle/oval, stroke color, fill, and line width, then drag on the canvas to draw it.
6) Draw: under "Draw" pick a brush color and width, then draw freehand.
7) Background: under "Background" pick a color and "Apply" to fill the whole page behind the content.
8) Edit existing text: tap text on the preview to select it. Enter "Replacement text", or choose "Delete the original text". You can also change size/color/style/font.
   • If the font and character set match, it is replaced in place.
   • For characters that can't be shown, moves, or size/color/font/style changes, the whole run is redrawn keeping its original formatting (font/size/color/style); the original look is preserved unless you change it explicitly.
9) Layers: added/edited items are listed under "Layers". Tap a row to select it, ▲▼ to change stacking order, or × to remove it. Thin drawings are hard to tap, so select them from this list.
10) Tap "Apply" to bake the current edits into a temporary PDF and refresh the preview with the real appearance. Shapes and drawings stay as layers after "Apply" (still selectable/reorderable/removable) and are flattened only on the final save.
11) "Apply and save" outputs the final PDF. Leaving with unsaved changes asks for confirmation first.
* Adding/editing text needs an embedded font. Choose from Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2 (all SIL OFL), per text run. Each font downloads once, then works offline; get missing ones from the editor or "Settings → OCR settings and models". Arabic/Hebrew (right-to-left) are shaped and reordered automatically.

── Convert & compose PDF ──
• Split (extract pages): open a PDF, select the pages to extract, then choose "combine into one" or "one per page" and export.
• Merge: choose several PDFs, arrange the order, and combine into one.
• Reorder: drag thumbnails to change the page order and save as a new PDF.
• PDF to images: convert each page to PNG / JPEG. You can set the DPI (resolution).
• Images to PDF: choose several images, set the order, and combine into one PDF.

── OCR / AI-OCR ──
• OCR / text extraction: extract text from PDFs/images. Embedded text (already in the file) and OCR (image recognition) are distinguished, and can also be exported as JSON. Engines: Tesseract / PaddleOCR. Tesseract recognizes Japanese, English, Simplified/Traditional Chinese, Korean, Greek, Russian (Cyrillic), Arabic, Hebrew, and math (equ); PaddleOCR covers Japanese, English, Simplified Chinese, and Korean (each language model downloads on demand). Large documents can run in the background.
• PDF summary: summarize the whole file or per page with an LLM. Choose "OCR→LLM" or "Vision (page images sent directly to the LLM)".
• Get engine models and set the LLM connection under "Settings → OCR settings and models". If you start the app with no OCR model downloaded, a prompt offers to open Settings (dismissable with "don't show again").

── Settings ──
• OCR settings and models: download Tesseract / PaddleOCR language models (Japanese, English, Chinese, Korean) and the editing fonts, and set the LLM (Ollama / OpenAI-compatible) connection URL, model, and connection test. Order is Tesseract → PaddleOCR → LLM.
• User manual / Privacy policy / Licenses: these documents (you can copy the body on each screen).

── About offline use ──
• PDF and image processing run entirely on the device. The only network use is "the first download of OCR models / fonts" and "sending to the configured server when using an LLM" (the default LLM endpoint is 127.0.0.1 on the device).

── Expert Mode / Local OCR API ──
Enable "Expert Mode" in Settings → OCR Settings to turn this device into an OCR API server. Other devices on the same LAN can then send OCR requests to it.

■ Endpoint
  POST http://<device-IP>:<port>/ocr
  POST http://127.0.0.1:<port>/ocr   (from the same device)

■ Request format
  Content-Type: multipart/form-data

  Fields:
    file   (required) Image (PNG/JPEG/WebP/BMP) or PDF
    engine (optional) tesseract (default) | paddleocr | llm
    lang   (optional) Language code(s), comma-separated (default: eng)
             e.g.: eng / jpn / jpn,eng / chi_sim / kor

  Note: engine=tesseract with lang=jpn requires the Tesseract Japanese model.
  Note: engine=paddleocr/llm also require the respective model/settings.

■ Response (HTTP 200)
  Content-Type: application/json
  {
    "pages": [
      {
        "page": 1,
        "text": "Recognized text here",
        "confidence": 0.95,
        "source": "OCR"
      }
    ],
    "engine": "Tesseract",
    "languages": ["eng"],
    "pageCount": 1
  }

■ Error responses
  HTTP 400: {"error":"Missing 'file' field"}    — no file field
  HTTP 400: {"error":"Could not decode image"}  — invalid image data
  HTTP 400: {"error":"Unknown engine: xxx"}     — bad engine name
  HTTP 500: {"error":"..."}                     — OCR processing error

■ Examples (curl)

# OCR a JPEG with English Tesseract
curl -X POST http://192.168.1.10:8765/ocr \
     -F "file=@document.jpg" \
     -F "engine=tesseract" \
     -F "lang=eng"

# OCR a PDF with Japanese+English Tesseract (multi-page)
curl -X POST http://192.168.1.10:8765/ocr \
     -F "file=@scan.pdf;type=application/pdf" \
     -F "lang=jpn,eng"

# PaddleOCR with Simplified Chinese
curl -X POST http://192.168.1.10:8765/ocr \
     -F "file=@chinese.png" \
     -F "engine=paddleocr" \
     -F "lang=chi_sim"

# LLM Vision (requires LLM configured in settings)
curl -X POST http://127.0.0.1:8765/ocr \
     -F "file=@image.jpg" \
     -F "engine=llm" \
     -F "lang=jpn"

■ Python example
import requests

with open("document.pdf", "rb") as f:
    resp = requests.post(
        "http://192.168.1.10:8765/ocr",
        files={"file": ("document.pdf", f, "application/pdf")},
        data={"engine": "tesseract", "lang": "eng"},
    )
result = resp.json()
for page in result["pages"]:
    print(f"Page {page['page']}: {page['text'][:80]}...")

■ Health check
  GET http://<device-IP>:<port>/health
  → {"status":"ok"}

■ Notes
• Use this API only on trusted local networks — there is no authentication.
• Large PDFs may take several seconds to minutes to process.
• Notification permission is required to keep the service alive after a process kill.
  You can stop the server from the notification.
• Processing may be interrupted if the device goes to sleep.
""".trimIndent()

private val PRIVACY_EN = """
■ Privacy Policy

• This app's PDF, image, and OCR processing complete on the device as a rule. File contents are never sent to or collected by our servers.
• Network communication happens only in these cases:
  - The first download of OCR models (Tesseract / PaddleOCR) or fonts.
  - Requests to the LLM server you configured when using "local LLM Vision" / "summary". The default endpoint is on-device (127.0.0.1), but if you specify an external server, page images and extracted text are sent to that server. The destination depends on your own settings.
  - Serving ads: this app uses Google AdMob to deliver ads.
• Input files are read-only; the original file is never rewritten. Output is always created as a new file.
• Advertising: this app uses Google AdMob (provided by Google LLC). To serve ads, Google may collect and use the advertising identifier (AAID), IP address, device information, and usage data. This data is handled by Google as a third party under Google's privacy policy. The contents of your files are never sent for advertising purposes.
  - How Google uses data: https://policies.google.com/technologies/partner-sites
  - You can limit ad personalization by resetting/opting out of your advertising ID under "Settings → Google → Ads" on your device.
• Other than the ad delivery above, the app performs no proprietary analytics or tracking.

For questions, please contact the app provider.
""".trimIndent()

private val LICENSES_EN = """
■ Credits and Open-Source Licenses

This app uses the following open-source software/fonts. For the full text of each license, please refer to each project's distribution.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• PaddleOCR models (PP-OCR / distributed via RapidOCR) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — governed by Google's terms
• Fonts (all SIL Open Font License 1.1):
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

The fonts above are bundled in the app and embedded into PDFs under SIL OFL 1.1 (including in a paid app).
""".trimIndent()

// ── French ────────────────────────────────────────────────────────────────────

private val MANUAL_FR = """
■ Manuel d'utilisation

── Général ──
• L'écran d'accueil comporte 4 catégories : « Créer et modifier un PDF / Convertir et composer un PDF / OCR et AI-OCR / Paramètres ». Appuyez sur une catégorie pour accéder à ses outils.
• Les fichiers source sont en lecture seule ; l'original n'est jamais modifié. Les résultats sont toujours enregistrés comme de nouveaux fichiers.
• Choisissez le dossier de sortie dans « Dossier de sortie » sur chaque écran. Par défaut, les fichiers sont enregistrés dans « Download/PDFToolkit » sur l'appareil.

── Créer et modifier un PDF ──
■ Modifier un PDF (ajouter texte/images/formes/dessin, modifier le texte existant)
1) Ouvrez un PDF avec « Choisir un PDF » ou créez une page vierge avec « Partir d'une page vierge » (choisissez la taille et la couleur de fond).
   • À l'ouverture d'un PDF existant ou à la création d'un nouveau, un avertissement indique qu'en raison du format PDF, l'ajout, la modification ou le déplacement de texte peut modifier la police ou la mise en forme.
2) Naviguez entre les pages avec « ◀ Préc. / Suiv. ▶ » sous l'aperçu.
3) Ajouter du texte : saisissez dans le champ « Texte » (retours à la ligne autorisés), choisissez taille/couleur/style/police, puis « Ajouter ». Il est placé dans l'aperçu ; glissez pour le déplacer.
4) Ajouter une image : choisissez-la sous « Image » et elle est placée dans l'aperçu. Glissez pour la déplacer ; vous pouvez la redimensionner et la faire pivoter.
5) Ajouter une forme : sous « Forme » choisissez rectangle/ovale, la couleur du contour, le remplissage et l'épaisseur, puis glissez sur le canevas pour la tracer.
6) Dessin : sous « Dessin » choisissez la couleur et l'épaisseur du pinceau, puis dessinez à main levée.
7) Fond : sous « Fond » choisissez une couleur et « Appliquer » pour remplir toute la page derrière le contenu.
8) Modifier le texte existant : appuyez sur du texte dans l'aperçu pour le sélectionner. Entrez un « Texte de remplacement » ou choisissez « Supprimer le texte original ». La taille, la couleur, le style et la police peuvent aussi être modifiés.
   • Si la police et le jeu de caractères correspondent, le remplacement se fait en place.
   • Pour les caractères non affichables, déplacements ou modifications de taille/couleur/police/style, toute la séquence est recréée en conservant sa mise en forme d'origine (police/taille/couleur/style) ; l'aspect d'origine est préservé sauf modification explicite.
9) Calques : les éléments ajoutés/modifiés sont listés dans « Calques ». Appuyez sur une ligne pour la sélectionner, ▲▼ pour changer l'ordre de superposition, × pour la supprimer. Les dessins fins étant difficiles à toucher, sélectionnez-les depuis cette liste.
10) « Appliquer » intègre les modifications courantes dans un PDF temporaire et rafraîchit l'aperçu avec le rendu réel. Les formes et les dessins restent des calques après « Appliquer » (toujours sélectionnables/réordonnables/supprimables) et ne sont aplatis qu'à l'enregistrement final.
11) « Appliquer et enregistrer » génère le PDF final. Quitter avec des modifications non enregistrées demande d'abord une confirmation.
* L'ajout/modification de texte nécessite des polices intégrées. Choisissez parmi Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2 (toutes SIL OFL). Chaque police se télécharge une seule fois puis fonctionne hors ligne.

── Convertir et composer un PDF ──
• Scinder (extraire des pages) : ouvrez un PDF, sélectionnez les pages, choisissez « Combiner en un PDF » ou « Un PDF par page » puis exportez.
• Fusionner : choisissez plusieurs PDFs, ordonnez-les et combinez-les en un.
• Réorganiser : glissez les miniatures pour changer l'ordre des pages et enregistrez comme nouveau PDF.
• PDF en images : convertissez chaque page en PNG / JPEG avec le DPI souhaité.
• Images en PDF : choisissez plusieurs images, définissez l'ordre et combinez-les en un PDF.

── OCR / AI-OCR ──
• OCR / Extraction de texte : extrait le texte des PDFs/images. Le texte intégré et le texte reconnu par OCR sont distingués et peuvent être exportés en JSON. Moteurs : Tesseract / PaddleOCR. Les documents volumineux peuvent être traités en arrière-plan.
• Résumé PDF : résumez le fichier entier ou page par page avec un LLM (OCR→LLM ou Vision).
• Téléchargez les modèles et configurez la connexion LLM dans « Paramètres → Paramètres OCR et modèles ».

── Paramètres ──
• Paramètres OCR et modèles : téléchargez les modèles Tesseract/PaddleOCR et les polices d'édition, configurez la connexion LLM (Ollama / compatible OpenAI).
• Manuel / Politique de confidentialité / Licences : ces documents (le corps peut être copié sur chaque écran).

── À propos de l'utilisation hors ligne ──
• Le traitement PDF et image s'effectue entièrement sur l'appareil. Le réseau n'est utilisé que pour le premier téléchargement des modèles/polices et pour les requêtes LLM (le point de terminaison LLM par défaut est 127.0.0.1 sur l'appareil).

── Mode Expert / API OCR locale ──
Activez « Mode Expert » dans Paramètres → Paramètres OCR pour transformer cet appareil en serveur API OCR. Les autres appareils du même réseau LAN peuvent lui envoyer des requêtes OCR.

■ Point de terminaison
  POST http://<IP-appareil>:<port>/ocr
  POST http://127.0.0.1:<port>/ocr   (depuis le même appareil)

■ Format de requête
  Content-Type: multipart/form-data
  Champs :
    file   (obligatoire) Image (PNG/JPEG/WebP/BMP) ou PDF
    engine (optionnel)  tesseract (défaut) | paddleocr | llm
    lang   (optionnel)  Code(s) de langue, séparés par des virgules (défaut : eng)
             ex. : eng / jpn / jpn,eng / chi_sim / kor

■ Réponse (HTTP 200)
  { "pages":[{"page":1,"text":"...","confidence":0.95,"source":"OCR"}],
    "engine":"Tesseract","languages":["eng"],"pageCount":1 }

■ Erreurs : HTTP 400/500 avec {"error":"..."}

■ Exemple curl
curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=eng"

■ Exemple Python
import requests
with open("doc.pdf","rb") as f:
    r = requests.post("http://192.168.1.10:8765/ocr",
        files={"file":("doc.pdf",f,"application/pdf")},
        data={"engine":"tesseract","lang":"eng"})
print(r.json())

■ Vérification de l'état : GET http://<IP>:<port>/health → {"status":"ok"}
■ Notes : Utiliser uniquement sur un réseau local de confiance (pas d'authentification).
""".trimIndent()

private val PRIVACY_FR = """
■ Politique de confidentialité

• Le traitement des PDFs, images et OCR de cette application s'effectue en principe sur l'appareil. Le contenu des fichiers n'est jamais envoyé à nos serveurs ni collecté par ceux-ci.
• Les communications réseau n'ont lieu que dans les cas suivants :
  - Premier téléchargement des modèles OCR (Tesseract / PaddleOCR) ou des polices.
  - Requêtes vers le serveur LLM configuré lors de l'utilisation de « LLM Vision local » / « Résumé ». Le point de terminaison par défaut est sur l'appareil (127.0.0.1), mais si vous spécifiez un serveur externe, les images de page et le texte extrait lui sont envoyés. La destination dépend de vos propres paramètres.
  - Affichage de publicités : cette application utilise Google AdMob pour diffuser des publicités.
• Les fichiers source sont en lecture seule ; le fichier original n'est jamais modifié. La sortie est toujours créée comme un nouveau fichier.
• Publicité : cette application utilise Google AdMob (fourni par Google LLC). Pour diffuser des publicités, Google peut collecter et utiliser l'identifiant publicitaire (AAID), l'adresse IP, les informations sur l'appareil et les données d'utilisation. Ces données sont traitées par Google en tant que tiers conformément à la politique de confidentialité de Google. Le contenu de vos fichiers n'est jamais envoyé à des fins publicitaires.
  - Utilisation des données par Google : https://policies.google.com/technologies/partner-sites
  - Vous pouvez limiter la personnalisation des publicités en réinitialisant/désactivant votre identifiant publicitaire dans « Paramètres → Google → Publicités » sur votre appareil.
• En dehors de la diffusion de publicités ci-dessus, l'application n'effectue aucune analyse ni aucun suivi propriétaires.

Pour toute question, veuillez contacter le fournisseur de l'application.
""".trimIndent()

private val LICENSES_FR = """
■ Crédits et licences open-source

Cette application utilise les logiciels open-source et polices suivants. Pour le texte complet de chaque licence, veuillez consulter la distribution de chaque projet.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• Modèles PaddleOCR (PP-OCR / distribués via RapidOCR) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — régi par les conditions de Google
• Polices (toutes sous SIL Open Font License 1.1) :
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

Les polices ci-dessus sont intégrées dans l'application et incorporées dans les PDFs sous SIL OFL 1.1 (y compris dans une application payante).
""".trimIndent()

// ── German ────────────────────────────────────────────────────────────────────

private val MANUAL_DE = """
■ Benutzerhandbuch

── Allgemein ──
• Der Startbildschirm hat 4 Kategorien: „PDF erstellen und bearbeiten / PDF konvertieren und zusammenstellen / OCR und KI-OCR / Einstellungen". Tippen Sie auf eine Kategorie, um ihre Werkzeuge anzuzeigen.
• Quelldateien sind schreibgeschützt; das Original wird nie geändert. Ergebnisse werden immer als neue Dateien gespeichert.
• Wählen Sie den Ausgabeordner in „Ausgabeordner" auf jedem Bildschirm. Standardmäßig wird in „Download/PDFToolkit" auf dem Gerät gespeichert.

── PDF erstellen und bearbeiten ──
■ PDF bearbeiten (Text/Bilder/Formen/Zeichnung hinzufügen, vorhandenen Text bearbeiten)
1) Öffnen Sie ein PDF mit „PDF auswählen" oder erstellen Sie eine leere Seite mit „Leer beginnen" (Seitengröße und Hintergrundfarbe wählen).
   • Beim Öffnen eines vorhandenen PDFs oder beim Neuerstellen weist ein Hinweis darauf hin, dass sich bedingt durch das PDF-Format beim Hinzufügen, Ändern oder Verschieben von Text Schrift oder Formatierung ändern können.
2) Navigieren Sie mit „◀ Zurück / Weiter ▶" unter der Vorschau zwischen den Seiten.
3) Text hinzufügen: Geben Sie Text in das Feld „Text" ein (Zeilenumbrüche erlaubt), wählen Sie Größe/Farbe/Stil/Schrift, dann „Hinzufügen". Es wird in der Vorschau platziert; ziehen Sie zum Verschieben.
4) Bild hinzufügen: Wählen Sie eines unter „Bild" und es wird in der Vorschau platziert. Ziehen Sie zum Verschieben; Skalieren und Drehen ist möglich.
5) Form hinzufügen: Wählen Sie unter „Form" Rechteck/Oval, Linienfarbe, Füllung und Linienstärke und ziehen Sie dann auf der Arbeitsfläche, um sie zu zeichnen.
6) Zeichnen: Wählen Sie unter „Zeichnen" Pinselfarbe und -stärke und zeichnen Sie frei Hand.
7) Hintergrund: Wählen Sie unter „Hintergrund" eine Farbe und „Anwenden", um die ganze Seite hinter dem Inhalt zu füllen.
8) Vorhandenen Text bearbeiten: Tippen Sie auf Text in der Vorschau, um ihn auszuwählen. Geben Sie „Ersatztext" ein oder wählen Sie „Originaltext löschen". Größe, Farbe, Stil und Schrift können ebenfalls geändert werden.
   • Bei übereinstimmender Schrift und Zeichensatz erfolgt der Ersatz an der gleichen Stelle.
   • Bei nicht darstellbaren Zeichen, Verschiebungen oder Größen-/Farb-/Schrift-/Stiländerungen wird die gesamte Sequenz neu generiert und behält ihre ursprüngliche Formatierung (Schrift/Größe/Farbe/Stil); das ursprüngliche Aussehen bleibt erhalten, sofern Sie es nicht ausdrücklich ändern.
9) Ebenen: Hinzugefügte/bearbeitete Elemente werden unter „Ebenen" aufgelistet. Tippen Sie auf eine Zeile zum Auswählen, ▲▼ zum Ändern der Stapelreihenfolge, × zum Entfernen. Dünne Zeichnungen sind schwer anzutippen – wählen Sie sie über diese Liste.
10) „Anwenden" integriert die aktuellen Änderungen in ein temporäres PDF und aktualisiert die Vorschau mit dem echten Aussehen. Formen und Zeichnungen bleiben nach „Anwenden" als Ebenen erhalten (weiterhin auswählbar/umsortierbar/entfernbar) und werden erst beim endgültigen Speichern zusammengeführt.
11) „Anwenden und speichern" erzeugt das endgültige PDF. Beim Verlassen mit nicht gespeicherten Änderungen wird zuerst eine Bestätigung verlangt.
* Das Hinzufügen/Bearbeiten von Text erfordert eingebettete Schriftarten. Wählen Sie aus Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2 (alle SIL OFL). Jede Schrift wird einmal heruntergeladen, dann offline nutzbar.

── PDF konvertieren und zusammenstellen ──
• Aufteilen (Seiten extrahieren): Öffnen Sie ein PDF, wählen Sie Seiten, wählen Sie „Zu einem PDF zusammenführen" oder „Ein PDF pro Seite" und exportieren Sie.
• Zusammenführen: Wählen Sie mehrere PDFs, ordnen Sie sie an und kombinieren Sie sie zu einem.
• Neu anordnen: Ziehen Sie Miniaturansichten, um die Seitenreihenfolge zu ändern und als neues PDF zu speichern.
• PDF in Bilder: Konvertieren Sie jede Seite in PNG / JPEG mit dem gewünschten DPI.
• Bilder in PDF: Wählen Sie mehrere Bilder, legen Sie die Reihenfolge fest und kombinieren Sie sie zu einem PDF.

── OCR / KI-OCR ──
• OCR / Textextraktion: Extrahiert Text aus PDFs/Bildern. Eingebetteter Text und erkannter Text werden unterschieden und können als JSON exportiert werden. Engines: Tesseract / PaddleOCR. Große Dokumente können im Hintergrund verarbeitet werden.
• PDF-Zusammenfassung: Fassen Sie die gesamte Datei oder seitenweise mit einem LLM zusammen.
• Laden Sie Modelle herunter und konfigurieren Sie die LLM-Verbindung unter „Einstellungen → OCR-Einstellungen und Modelle".

── Einstellungen ──
• OCR-Einstellungen und Modelle: Tesseract/PaddleOCR-Modelle und Bearbeitungsschriften herunterladen, LLM-Verbindung konfigurieren.
• Handbuch / Datenschutzrichtlinie / Lizenzen: Diese Dokumente (Inhalt auf jedem Bildschirm kopierbar).

── Offline-Nutzung ──
• PDF- und Bildverarbeitung läuft vollständig auf dem Gerät. Netzwerk wird nur für den ersten Modell-/Schrift-Download und LLM-Anfragen verwendet.

── Expertenmodus / Lokale OCR-API ──
Aktivieren Sie „Expertenmodus" in Einstellungen → OCR-Einstellungen, um dieses Gerät zu einem OCR-API-Server zu machen.

■ Endpunkt
  POST http://<Geräte-IP>:<Port>/ocr
  POST http://127.0.0.1:<Port>/ocr   (vom gleichen Gerät)

■ Anforderungsformat: multipart/form-data
  Felder: file (erforderlich), engine (optional, Standard: tesseract), lang (optional, Standard: eng)

■ Antwort (HTTP 200): {"pages":[...],"engine":"...","languages":[...],"pageCount":1}
■ Fehler: HTTP 400/500 mit {"error":"..."}
■ Beispiel: curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=deu"
■ Zustandsprüfung: GET http://<IP>:<Port>/health → {"status":"ok"}
■ Hinweis: Nur in vertrauenswürdigen lokalen Netzwerken verwenden (keine Authentifizierung).
""".trimIndent()

private val PRIVACY_DE = """
■ Datenschutzrichtlinie

• Die PDF-, Bild- und OCR-Verarbeitung dieser App erfolgt grundsätzlich auf dem Gerät. Dateiinhalte werden niemals an unsere Server gesendet oder von diesen gesammelt.
• Netzwerkkommunikation findet nur in folgenden Fällen statt:
  - Erstmaliger Download von OCR-Modellen (Tesseract / PaddleOCR) oder Schriften.
  - Anfragen an den konfigurierten LLM-Server bei Verwendung von „Lokales LLM Vision" / „Zusammenfassung". Der Standard-Endpunkt ist auf dem Gerät (127.0.0.1); wenn Sie einen externen Server angeben, werden Seitenbilder und extrahierter Text an diesen gesendet. Das Ziel hängt von Ihren eigenen Einstellungen ab.
  - Werbeanzeige: Diese App verwendet Google AdMob zur Auslieferung von Werbung.
• Quelldateien sind schreibgeschützt; die Originaldatei wird nie überschrieben. Die Ausgabe wird immer als neue Datei erstellt.
• Werbung: Diese App verwendet Google AdMob (bereitgestellt von Google LLC). Zur Auslieferung von Werbung kann Google die Werbe-ID (AAID), IP-Adresse, Geräteinformationen und Nutzungsdaten erheben und verwenden. Diese Daten werden von Google als Drittpartei gemäß der Google-Datenschutzrichtlinie verarbeitet. Die Inhalte Ihrer Dateien werden nie zu Werbezwecken gesendet.
  - Datenverwendung durch Google: https://policies.google.com/technologies/partner-sites
  - Sie können die Personalisierung von Werbung einschränken, indem Sie Ihre Werbe-ID unter „Einstellungen → Google → Werbung" zurücksetzen/deaktivieren.
• Abgesehen von der oben genannten Werbeauslieferung führt die App keine eigene Analyse oder kein Tracking durch.

Bei Fragen wenden Sie sich bitte an den App-Anbieter.
""".trimIndent()

private val LICENSES_DE = """
■ Danksagungen und Open-Source-Lizenzen

Diese App verwendet die folgende Open-Source-Software und Schriften. Den vollständigen Lizenztext finden Sie in der jeweiligen Projektverteilung.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• PaddleOCR-Modelle (PP-OCR / verteilt über RapidOCR) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — gemäß Google-Nutzungsbedingungen
• Schriften (alle SIL Open Font License 1.1):
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

Die oben genannten Schriften sind in der App gebündelt und unter SIL OFL 1.1 in PDFs eingebettet (auch in einer kostenpflichtigen App).
""".trimIndent()

// ── Spanish ───────────────────────────────────────────────────────────────────

private val MANUAL_ES = """
■ Manual de usuario

── General ──
• La pantalla de inicio tiene 4 categorías: «Crear y editar PDF / Convertir y componer PDF / OCR e IA-OCR / Ajustes». Toca una categoría para ver sus herramientas.
• Los archivos de entrada son de solo lectura; el original nunca se modifica. Los resultados siempre se guardan como nuevos archivos.
• Elige la carpeta de salida en «Carpeta de salida» de cada pantalla. Por defecto se guarda en «Download/PDFToolkit» del dispositivo.

── Crear y editar PDF ──
■ Editar PDF (añadir texto/imágenes/formas/dibujo, editar texto existente)
1) Abre un PDF con «Elegir PDF» o crea una página en blanco con «Empezar en blanco» (elige tamaño de página y color de fondo).
   • Al abrir un PDF existente o crear uno nuevo, un aviso indica que, debido al formato PDF, añadir, cambiar o mover texto puede alterar la fuente o el formato.
2) Navega entre páginas con «◀ Ant. / Sig. ▶» bajo la vista previa.
3) Añadir texto: escribe en el campo «Texto» (saltos de línea permitidos), elige tamaño/color/estilo/fuente, luego «Añadir». Se coloca en la vista previa; arrastra para mover.
4) Añadir imagen: elígela en «Imagen» y se coloca en la vista previa. Arrastra para mover; puedes escalar y rotar.
5) Añadir forma: en «Forma» elige rectángulo/óvalo, color de borde, relleno y grosor, luego arrastra en el lienzo para dibujarla.
6) Dibujar: en «Dibujo» elige el color y el grosor del pincel y dibuja a mano alzada.
7) Fondo: en «Fondo» elige un color y «Aplicar» para rellenar toda la página detrás del contenido.
8) Editar texto existente: toca texto en la vista previa para seleccionarlo. Introduce «Texto de reemplazo» o elige «Eliminar el texto original». También puedes cambiar tamaño/color/estilo/fuente.
   • Si la fuente y el juego de caracteres coinciden, se reemplaza en el lugar.
   • Para caracteres no mostrables, movimientos o cambios de tamaño/color/fuente/estilo, toda la secuencia se regenera conservando su formato original (fuente/tamaño/color/estilo); el aspecto original se mantiene salvo que lo cambies explícitamente.
9) Capas: los elementos añadidos/editados aparecen en «Capas». Toca una fila para seleccionarla, ▲▼ para cambiar el orden de apilamiento, × para eliminarla. Los dibujos finos son difíciles de tocar; selecciónalos desde esta lista.
10) «Aplicar» integra las ediciones actuales en un PDF temporal y actualiza la vista previa con el aspecto real. Las formas y los dibujos permanecen como capas tras «Aplicar» (aún seleccionables/reordenables/eliminables) y solo se aplanan al guardar definitivamente.
11) «Aplicar y guardar» genera el PDF final. Salir con cambios sin guardar pide confirmación primero.
* Añadir/editar texto requiere fuentes integradas. Elige entre Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2 (todas SIL OFL). Cada fuente se descarga una vez y luego funciona sin conexión.

── Convertir y componer PDF ──
• Dividir (extraer páginas): abre un PDF, selecciona páginas, elige «Combinar en un PDF» o «Un PDF por página» y exporta.
• Combinar: elige varios PDFs, ordénalos y combínalos en uno.
• Reordenar: arrastra miniaturas para cambiar el orden de páginas y guarda como nuevo PDF.
• PDF a imágenes: convierte cada página a PNG / JPEG con el DPI deseado.
• Imágenes a PDF: elige varias imágenes, establece el orden y combínalas en un PDF.

── OCR / IA-OCR ──
• OCR / Extracción de texto: extrae texto de PDFs/imágenes. El texto incrustado y el reconocido por OCR se distinguen y pueden exportarse como JSON. Motores: Tesseract / PaddleOCR. Los documentos grandes pueden procesarse en segundo plano.
• Resumen PDF: resume el archivo completo o página por página con un LLM.
• Descarga modelos y configura la conexión LLM en «Ajustes → Ajustes y modelos OCR».

── Ajustes ──
• Ajustes y modelos OCR: descarga modelos Tesseract/PaddleOCR y fuentes de edición, configura la conexión LLM.
• Manual / Política de privacidad / Licencias: estos documentos (el cuerpo puede copiarse en cada pantalla).

── Uso sin conexión ──
• El procesamiento de PDF e imágenes se realiza completamente en el dispositivo. La red solo se usa para el primer download de modelos/fuentes y solicitudes LLM.

── Modo Experto / API OCR local ──
Activa «Modo Experto» en Ajustes → Ajustes OCR para convertir este dispositivo en un servidor API OCR.

■ Endpoint: POST http://<IP-dispositivo>:<puerto>/ocr
■ Formato: multipart/form-data — campos: file (obligatorio), engine (opcional), lang (opcional)
■ Respuesta: {"pages":[...],"engine":"...","languages":[...],"pageCount":1}
■ Ejemplo: curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=spa"
■ Comprobación: GET http://<IP>:<puerto>/health → {"status":"ok"}
■ Nota: Solo usar en redes locales de confianza (sin autenticación).
""".trimIndent()

private val PRIVACY_ES = """
■ Política de privacidad

• El procesamiento de PDF, imágenes y OCR de esta aplicación se realiza en principio en el dispositivo. El contenido de los archivos nunca se envía a nuestros servidores ni es recopilado por ellos.
• La comunicación de red solo ocurre en los siguientes casos:
  - Primera descarga de modelos OCR (Tesseract / PaddleOCR) o fuentes.
  - Solicitudes al servidor LLM configurado al usar «LLM Vision local» / «Resumen». El endpoint predeterminado está en el dispositivo (127.0.0.1); si especificas un servidor externo, las imágenes de página y el texto extraído se envían a ese servidor. El destino depende de tu propia configuración.
  - Visualización de anuncios: esta aplicación usa Google AdMob para mostrar anuncios.
• Los archivos de entrada son de solo lectura; el archivo original nunca se sobreescribe. La salida siempre se crea como un nuevo archivo.
• Publicidad: esta aplicación usa Google AdMob (proporcionado por Google LLC). Para servir anuncios, Google puede recopilar y usar el identificador publicitario (AAID), dirección IP, información del dispositivo y datos de uso. Estos datos son tratados por Google como tercero según la política de privacidad de Google. El contenido de tus archivos nunca se envía con fines publicitarios.
  - Uso de datos por Google: https://policies.google.com/technologies/partner-sites
  - Puedes limitar la personalización de anuncios reseteando/desactivando tu ID publicitario en «Ajustes → Google → Anuncios» de tu dispositivo.
• Más allá de la entrega de anuncios indicada, la aplicación no realiza ninguna analítica ni seguimiento propio.

Para consultas, contacta al proveedor de la aplicación.
""".trimIndent()

private val LICENSES_ES = """
■ Créditos y licencias de código abierto

Esta aplicación usa el siguiente software de código abierto y fuentes. Para el texto completo de cada licencia, consulta la distribución de cada proyecto.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• Modelos PaddleOCR (PP-OCR / distribuidos vía RapidOCR) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — sujeto a los términos de Google
• Fuentes (todas bajo SIL Open Font License 1.1):
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

Las fuentes anteriores están incluidas en la aplicación e integradas en PDFs bajo SIL OFL 1.1 (incluso en una aplicación de pago).
""".trimIndent()

// ── Italian ───────────────────────────────────────────────────────────────────

private val MANUAL_IT = """
■ Manuale utente

── Generale ──
• La schermata iniziale ha 4 categorie: «Crea e modifica PDF / Converti e componi PDF / OCR e AI-OCR / Impostazioni». Tocca una categoria per vedere i suoi strumenti.
• I file sorgente sono in sola lettura; l'originale non viene mai modificato. I risultati vengono sempre salvati come nuovi file.
• Scegli la cartella di output in «Cartella di output» su ogni schermata. Per impostazione predefinita i file vengono salvati in «Download/PDFToolkit» sul dispositivo.

── Crea e modifica PDF ──
■ Modifica PDF (aggiungi testo/immagini/forme/disegno, modifica testo esistente)
1) Apri un PDF con «Scegli PDF» o crea una pagina vuota con «Inizia da pagina bianca» (scegli dimensione della pagina e colore di sfondo).
   • All'apertura di un PDF esistente o alla creazione di uno nuovo, un avviso indica che, a causa del formato PDF, aggiungere, modificare o spostare testo può alterare il carattere o la formattazione.
2) Naviga tra le pagine con «◀ Prec. / Succ. ▶» sotto l'anteprima.
3) Aggiungere testo: digita nel campo «Testo» (a capo consentiti), scegli dimensione/colore/stile/font, poi «Aggiungi». Viene posizionato nell'anteprima; trascina per spostarlo.
4) Aggiungere immagine: sceglila sotto «Immagine» e viene posizionata nell'anteprima. Trascina per spostarla; puoi ridimensionarla e ruotarla.
5) Aggiungere forma: in «Forma» scegli rettangolo/ovale, colore del bordo, riempimento e spessore, poi trascina sulla tela per disegnarla.
6) Disegno: in «Disegno» scegli colore e spessore del pennello e disegna a mano libera.
7) Sfondo: in «Sfondo» scegli un colore e «Applica» per riempire l'intera pagina dietro il contenuto.
8) Modificare testo esistente: tocca il testo nell'anteprima per selezionarlo. Inserisci «Testo sostitutivo» o scegli «Elimina testo originale». Dimensione, colore, stile e font possono essere cambiati.
   • Se font e set di caratteri corrispondono, il testo viene sostituito in loco.
   • Per caratteri non visualizzabili, spostamenti o cambi di dimensione/colore/font/stile, l'intera sequenza viene rigenerata conservando la formattazione originale (font/dimensione/colore/stile); l'aspetto originale è mantenuto salvo modifica esplicita.
9) Livelli: gli elementi aggiunti/modificati sono elencati in «Livelli». Tocca una riga per selezionarla, ▲▼ per cambiare l'ordine di sovrapposizione, × per rimuoverla. I disegni sottili sono difficili da toccare: selezionali da questo elenco.
10) «Applica» integra le modifiche correnti in un PDF temporaneo e aggiorna l'anteprima con l'aspetto reale. Forme e disegni restano come livelli dopo «Applica» (ancora selezionabili/riordinabili/rimovibili) e vengono uniti solo al salvataggio finale.
11) «Applica e salva» genera il PDF finale. Uscendo con modifiche non salvate viene chiesta prima una conferma.
* Aggiungere/modificare testo richiede font incorporati. Scegli tra Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2 (tutti SIL OFL). Ogni font viene scaricato una volta e poi funziona offline.

── Converti e componi PDF ──
• Dividi (estrai pagine): apri un PDF, seleziona le pagine, scegli «Combina in un unico PDF» o «Un PDF per pagina» ed esporta.
• Unisci: scegli più PDF, ordinali e combinali in uno.
• Riordina: trascina le miniature per cambiare l'ordine delle pagine e salva come nuovo PDF.
• PDF in immagini: converti ogni pagina in PNG / JPEG con il DPI desiderato.
• Immagini in PDF: scegli più immagini, imposta l'ordine e combinale in un PDF.

── OCR / AI-OCR ──
• OCR / Estrazione testo: estrae testo da PDF/immagini distinguendo testo incorporato e OCR (esportabile come JSON). Motori: Tesseract / PaddleOCR. I documenti grandi possono essere elaborati in background.
• Riepilogo PDF: riepiloga il file intero o pagina per pagina con un LLM.
• Scarica modelli e configura la connessione LLM in «Impostazioni → Impostazioni e modelli OCR».

── Impostazioni ──
• Impostazioni e modelli OCR: scarica modelli Tesseract/PaddleOCR e font di editing, configura la connessione LLM.
• Manuale / Informativa sulla privacy / Licenze: questi documenti (il corpo può essere copiato su ogni schermata).

── Utilizzo offline ──
• L'elaborazione di PDF e immagini avviene interamente sul dispositivo. La rete viene usata solo per il primo download di modelli/font e le richieste LLM.

── Modalità Esperto / API OCR locale ──
Abilita «Modalità Esperto» in Impostazioni → Impostazioni OCR per trasformare questo dispositivo in un server API OCR.

■ Endpoint: POST http://<IP-dispositivo>:<porta>/ocr
■ Formato: multipart/form-data — campi: file (obbligatorio), engine (opzionale), lang (opzionale)
■ Risposta: {"pages":[...],"engine":"...","languages":[...],"pageCount":1}
■ Esempio: curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=ita"
■ Stato: GET http://<IP>:<porta>/health → {"status":"ok"}
■ Nota: Usare solo su reti locali attendibili (nessuna autenticazione).
""".trimIndent()

private val PRIVACY_IT = """
■ Informativa sulla privacy

• L'elaborazione di PDF, immagini e OCR di questa app avviene in linea di principio sul dispositivo. Il contenuto dei file non viene mai inviato ai nostri server né raccolto da essi.
• La comunicazione di rete avviene solo nei seguenti casi:
  - Primo download di modelli OCR (Tesseract / PaddleOCR) o font.
  - Richieste al server LLM configurato durante l'utilizzo di «LLM Vision locale» / «Riepilogo». L'endpoint predefinito è sul dispositivo (127.0.0.1); se specifichi un server esterno, le immagini di pagina e il testo estratto vengono inviati a quel server. La destinazione dipende dalle tue impostazioni.
  - Visualizzazione di annunci: questa app utilizza Google AdMob per erogare annunci pubblicitari.
• I file sorgente sono in sola lettura; il file originale non viene mai sovrascritto. L'output viene sempre creato come nuovo file.
• Pubblicità: questa app utilizza Google AdMob (fornito da Google LLC). Per erogare annunci, Google può raccogliere e utilizzare l'identificatore pubblicitario (AAID), l'indirizzo IP, le informazioni sul dispositivo e i dati di utilizzo. Questi dati sono trattati da Google come terza parte ai sensi della politica sulla privacy di Google. Il contenuto dei tuoi file non viene mai inviato a fini pubblicitari.
  - Utilizzo dei dati da parte di Google: https://policies.google.com/technologies/partner-sites
  - Puoi limitare la personalizzazione degli annunci reimpostando/disattivando il tuo ID pubblicitario in «Impostazioni → Google → Annunci» sul tuo dispositivo.
• Oltre all'erogazione di annunci sopra indicata, l'app non esegue analisi o tracciamento proprietari.

Per domande, contatta il fornitore dell'applicazione.
""".trimIndent()

private val LICENSES_IT = """
■ Crediti e licenze open-source

Questa app utilizza i seguenti software open-source e font. Per il testo completo di ogni licenza, consulta la distribuzione di ciascun progetto.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• Modelli PaddleOCR (PP-OCR / distribuiti tramite RapidOCR) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — disciplinato dai termini di Google
• Font (tutti sotto SIL Open Font License 1.1):
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

I font di cui sopra sono inclusi nell'app e incorporati nei PDF ai sensi di SIL OFL 1.1 (anche in un'app a pagamento).
""".trimIndent()

// ── Portuguese ────────────────────────────────────────────────────────────────

private val MANUAL_PT = """
■ Manual do usuário

── Geral ──
• A tela inicial tem 4 categorias: «Criar e editar PDF / Converter e compor PDF / OCR e IA-OCR / Configurações». Toque em uma categoria para ver suas ferramentas.
• Os arquivos de entrada são somente leitura; o original nunca é modificado. Os resultados são sempre salvos como novos arquivos.
• Escolha a pasta de saída em «Pasta de saída» em cada tela. Por padrão, os arquivos são salvos em «Download/PDFToolkit» no dispositivo.

── Criar e editar PDF ──
■ Editar PDF (adicionar texto/imagens/formas/desenho, editar texto existente)
1) Abra um PDF com «Escolher PDF» ou crie uma página em branco com «Começar em branco» (escolha o tamanho da página e a cor de fundo).
   • Ao abrir um PDF existente ou criar um novo, um aviso informa que, devido ao formato PDF, adicionar, alterar ou mover texto pode alterar a fonte ou a formatação.
2) Navegue entre páginas com «◀ Ant. / Próx. ▶» abaixo da pré-visualização.
3) Adicionar texto: digite no campo «Texto» (quebras de linha permitidas), escolha tamanho/cor/estilo/fonte, depois «Adicionar». É colocado na pré-visualização; arraste para mover.
4) Adicionar imagem: escolha em «Imagem» e ela é colocada na pré-visualização. Arraste para mover; é possível redimensionar e girar.
5) Adicionar forma: em «Forma» escolha retângulo/oval, cor do traço, preenchimento e espessura, depois arraste na tela para desenhá-la.
6) Desenho: em «Desenho» escolha a cor e a espessura do pincel e desenhe à mão livre.
7) Fundo: em «Fundo» escolha uma cor e «Aplicar» para preencher a página inteira atrás do conteúdo.
8) Editar texto existente: toque no texto da pré-visualização para selecioná-lo. Insira «Texto de substituição» ou escolha «Excluir o texto original». Tamanho, cor, estilo e fonte também podem ser alterados.
   • Se a fonte e o conjunto de caracteres corresponderem, o texto é substituído no lugar.
   • Para caracteres não exibíveis, movimentos ou alterações de tamanho/cor/fonte/estilo, toda a sequência é regenerada mantendo a formatação original (fonte/tamanho/cor/estilo); a aparência original é preservada, a menos que você a altere explicitamente.
9) Camadas: os itens adicionados/editados são listados em «Camadas». Toque em uma linha para selecioná-la, ▲▼ para mudar a ordem de empilhamento, × para removê-la. Desenhos finos são difíceis de tocar; selecione-os por esta lista.
10) «Aplicar» integra as edições atuais em um PDF temporário e atualiza a pré-visualização com a aparência real. Formas e desenhos permanecem como camadas após «Aplicar» (ainda selecionáveis/reordenáveis/removíveis) e só são achatados no salvamento final.
11) «Aplicar e salvar» gera o PDF final. Sair com alterações não salvas pede confirmação primeiro.
* Adicionar/editar texto requer fontes integradas. Escolha entre Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2 (todas SIL OFL). Cada fonte é baixada uma vez e depois funciona offline.

── Converter e compor PDF ──
• Dividir (extrair páginas): abra um PDF, selecione páginas, escolha «Combinar em um PDF» ou «Um PDF por página» e exporte.
• Mesclar: escolha vários PDFs, ordene-os e combine-os em um.
• Reordenar: arraste miniaturas para mudar a ordem das páginas e salve como novo PDF.
• PDF para imagens: converta cada página em PNG / JPEG com o DPI desejado.
• Imagens para PDF: escolha várias imagens, defina a ordem e combine-as em um PDF.

── OCR / IA-OCR ──
• OCR / Extração de texto: extrai texto de PDFs/imagens distinguindo texto incorporado de OCR (exportável como JSON). Motores: Tesseract / PaddleOCR. Documentos grandes podem ser processados em segundo plano.
• Resumo PDF: resuma o arquivo inteiro ou página por página com um LLM.
• Baixe modelos e configure a conexão LLM em «Configurações → Configurações e modelos OCR».

── Configurações ──
• Configurações e modelos OCR: baixe modelos Tesseract/PaddleOCR e fontes de edição, configure a conexão LLM.
• Manual / Política de privacidade / Licenças: estes documentos (o corpo pode ser copiado em cada tela).

── Uso offline ──
• O processamento de PDF e imagens é feito inteiramente no dispositivo. A rede só é usada para o primeiro download de modelos/fontes e solicitações LLM.

── Modo Especialista / API OCR local ──
Ative «Modo Especialista» em Configurações → Configurações OCR para transformar este dispositivo em um servidor API OCR.

■ Endpoint: POST http://<IP-dispositivo>:<porta>/ocr
■ Formato: multipart/form-data — campos: file (obrigatório), engine (opcional), lang (opcional)
■ Resposta: {"pages":[...],"engine":"...","languages":[...],"pageCount":1}
■ Exemplo: curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=por"
■ Verificação: GET http://<IP>:<porta>/health → {"status":"ok"}
■ Nota: Usar apenas em redes locais confiáveis (sem autenticação).
""".trimIndent()

private val PRIVACY_PT = """
■ Política de privacidade

• O processamento de PDF, imagens e OCR deste aplicativo é realizado em princípio no dispositivo. O conteúdo dos arquivos nunca é enviado aos nossos servidores nem coletado por eles.
• A comunicação de rede ocorre apenas nos seguintes casos:
  - Primeiro download de modelos OCR (Tesseract / PaddleOCR) ou fontes.
  - Solicitações ao servidor LLM configurado ao usar «LLM Vision local» / «Resumo». O endpoint padrão está no dispositivo (127.0.0.1); se você especificar um servidor externo, imagens de página e texto extraído serão enviados a esse servidor. O destino depende de suas próprias configurações.
  - Exibição de anúncios: este aplicativo usa o Google AdMob para veicular anúncios.
• Os arquivos de entrada são somente leitura; o arquivo original nunca é sobrescrito. A saída é sempre criada como um novo arquivo.
• Publicidade: este aplicativo usa o Google AdMob (fornecido pelo Google LLC). Para veicular anúncios, o Google pode coletar e usar o identificador de publicidade (AAID), endereço IP, informações do dispositivo e dados de uso. Esses dados são tratados pelo Google como terceiro de acordo com a política de privacidade do Google. O conteúdo dos seus arquivos nunca é enviado para fins publicitários.
  - Uso de dados pelo Google: https://policies.google.com/technologies/partner-sites
  - Você pode limitar a personalização de anúncios redefinindo/desativando seu ID de publicidade em «Configurações → Google → Anúncios» no seu dispositivo.
• Além da veiculação de anúncios acima, o aplicativo não realiza nenhuma análise ou rastreamento próprios.

Para perguntas, entre em contato com o provedor do aplicativo.
""".trimIndent()

private val LICENSES_PT = """
■ Créditos e licenças de código aberto

Este aplicativo usa os seguintes softwares de código aberto e fontes. Para o texto completo de cada licença, consulte a distribuição de cada projeto.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• Modelos PaddleOCR (PP-OCR / distribuídos via RapidOCR) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — regido pelos termos do Google
• Fontes (todas sob SIL Open Font License 1.1):
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

As fontes acima estão incluídas no aplicativo e incorporadas em PDFs sob SIL OFL 1.1 (inclusive em um aplicativo pago).
""".trimIndent()

// ── Chinese (Simplified) ──────────────────────────────────────────────────────

private val MANUAL_ZH = """
■ 用户手册

── 通用 ──
• 主屏幕有 4 个分类：「创建与编辑 PDF / 转换与整合 PDF / OCR 与 AI-OCR / 设置」。点击分类可查看对应工具。
• 输入文件为只读，原文件不会被修改。结果始终保存为新文件。
• 在每个界面的「输出文件夹」中选择保存位置。默认保存到设备的「Download/PDFToolkit」。

── 创建与编辑 PDF ──
■ 编辑 PDF（添加文字/图像/图形/绘图，编辑现有文字）
1) 通过「选择 PDF」打开 PDF，或通过「从空白页开始」创建空白页（可指定页面大小与背景色）。
   • 打开现有 PDF 或新建时会出现提示：由于 PDF 格式的特性，添加、修改或移动文字可能会改变字体或格式。
2) 用预览下方的「◀ 上一页 / 下一页 ▶」在页面间导航。
3) 添加文字：在「文字」字段输入文字（允许换行），选择大小/颜色/样式/字体，然后点「添加」。文字出现在预览中，可拖动移动。
4) 添加图像：在「图像」中选择图像，将其放置在预览中，可拖动移动，并可缩放和旋转。
5) 添加图形：在「图形」中选择矩形/椭圆、边框颜色、填充和线宽，然后在画布上拖动绘制。
6) 绘图：在「绘图」中选择画笔颜色和粗细，然后自由手绘。
7) 背景色：在「背景」中选择颜色并「应用」，为整页铺上内容后方的背景色。
8) 编辑现有文字：点击预览中的文字将其选中。输入「替换文字」或选择「删除原始文字」。也可更改大小/颜色/样式/字体。
   • 若字符集和字体匹配，则就地替换。
   • 对于无法显示的字符、移动或大小/颜色/字体/样式更改，将重新生成整个文字行并保留其原始格式（字体/大小/颜色/样式）；除非明确更改，否则保持原始外观。
9) 图层：已添加/编辑的项目列于「图层」中。点击行选择，▲▼ 调整层叠顺序，× 删除。细的绘图难以点选，可从此列表中选择。
10) 「应用」将当前编辑融入临时 PDF 并以真实外观刷新预览。图形和绘图在「应用」后仍作为图层保留（可继续选择/重排/删除），仅在最终保存时才合并。
11) 「应用并保存」输出最终 PDF。若有未保存的更改而尝试返回，会先弹出确认。
* 添加/编辑文字需要内嵌字体。可选择 Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2（均为 SIL OFL）。每种字体仅下载一次，之后可离线使用。

── 转换与整合 PDF ──
• 拆分（提取页面）：打开 PDF，选择页面，选「合并为一个 PDF」或「每页一个 PDF」后导出。
• 合并：选择多个 PDF，整理顺序，合并为一个。
• 重新排序：拖动缩略图更改页面顺序，保存为新 PDF。
• PDF 转图像：将每页转换为 PNG / JPEG，可设置 DPI。
• 图像转 PDF：选择多张图像，设置顺序，合并为一个 PDF。

── OCR / AI-OCR ──
• OCR / 文字提取：从 PDF/图像中提取文字，区分内嵌文字与 OCR，可导出为 JSON。引擎：Tesseract / PaddleOCR。大型文档可在后台处理。
• PDF 摘要：用 LLM 对整个文件或逐页进行摘要。
• 在「设置 → OCR 设置与模型」中下载模型并配置 LLM 连接。

── 设置 ──
• OCR 设置与模型：下载 Tesseract/PaddleOCR 模型和编辑字体，配置 LLM 连接。
• 手册 / 隐私政策 / 许可证：本文档（每个界面均可复制正文）。

── 离线使用 ──
• PDF 和图像处理完全在设备上完成。网络仅用于模型/字体的首次下载和 LLM 请求（默认 LLM 端点为设备本地 127.0.0.1）。

── 专家模式 / 本地 OCR API ──
在设置 → OCR 设置中启用「专家模式」，将本设备变为 OCR API 服务器，局域网内的其他设备可向其发送 OCR 请求。

■ 端点：POST http://<设备IP>:<端口>/ocr
■ 格式：multipart/form-data — 字段：file（必填）、engine（可选）、lang（可选）
■ 响应：{"pages":[...],"engine":"...","languages":[...],"pageCount":1}
■ 示例：curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=chi_sim"
■ 健康检查：GET http://<IP>:<端口>/health → {"status":"ok"}
■ 注意：仅在受信任的局域网中使用（无身份验证）。
""".trimIndent()

private val PRIVACY_ZH = """
■ 隐私政策

• 本应用的 PDF、图像和 OCR 处理原则上在设备上完成。文件内容不会发送到我们的服务器或被收集。
• 仅在以下情况下进行网络通信：
  - 首次下载 OCR 模型（Tesseract / PaddleOCR）或字体。
  - 使用「本地 LLM Vision」/「摘要」时向您配置的 LLM 服务器发送请求。默认端点在设备本地（127.0.0.1），但如果您指定了外部服务器，页面图像和提取文字将发送到该服务器。目标取决于您自己的设置。
  - 广告展示：本应用使用 Google AdMob 投放广告。
• 输入文件为只读；原文件不会被覆写。输出始终创建为新文件。
• 广告：本应用使用 Google AdMob（由 Google LLC 提供）。为投放广告，Google 可能收集和使用广告标识符（AAID）、IP 地址、设备信息和使用数据。这些数据由 Google 作为第三方根据 Google 隐私政策处理。您的文件内容不会因广告目的而发送。
  - Google 的数据使用：https://policies.google.com/technologies/partner-sites
  - 您可以在设备「设置 → Google → 广告」中重置/选择退出广告 ID 来限制广告个性化。
• 除上述广告投放外，应用不进行任何自有分析或追踪。

如有疑问，请联系应用提供商。
""".trimIndent()

private val LICENSES_ZH = """
■ 版权说明与开源许可证

本应用使用了以下开源软件/字体。各许可证全文请参阅各项目的发行版。

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• PaddleOCR 模型 (PP-OCR / 通过 RapidOCR 分发) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — 受 Google 条款约束
• 字体（均为 SIL Open Font License 1.1）：
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

上述字体在 SIL OFL 1.1 许可下打包于应用中并嵌入 PDF（包括付费应用）。
""".trimIndent()

// ── Korean ────────────────────────────────────────────────────────────────────

private val MANUAL_KO = """
■ 사용자 매뉴얼

── 공통 ──
• 홈 화면에는 4개 카테고리가 있습니다: 「PDF 만들기 및 편집 / PDF 변환 및 구성 / OCR 및 AI-OCR / 설정」. 카테고리를 탭하면 해당 도구가 표시됩니다.
• 입력 파일은 읽기 전용이며, 원본 파일은 변경되지 않습니다. 결과는 항상 새 파일로 저장됩니다.
• 각 화면의 「출력 폴더」에서 저장 위치를 선택하세요. 미설정 시 기기의 「Download/PDFToolkit」에 저장됩니다.

── PDF 만들기 및 편집 ──
■ PDF 편집(텍스트/이미지/도형/그리기 추가, 기존 텍스트 편집)
1) 「PDF 선택」으로 PDF를 열거나 「빈 페이지에서 시작」으로 빈 페이지를 만드세요(용지 크기와 배경색 지정).
   • 기존 PDF를 열거나 새로 만들 때, PDF 형식의 특성상 텍스트를 추가·변경·이동하면 글꼴이나 서식이 달라질 수 있다는 안내가 표시됩니다.
2) 미리 보기 아래의 「◀ 이전 / 다음 ▶」으로 페이지를 이동합니다.
3) 텍스트 추가: 「텍스트」 필드에 입력(줄 바꿈 허용), 크기/색상/스타일/폰트 선택 후 「추가」. 미리 보기에 배치되며 드래그로 이동 가능합니다.
4) 이미지 추가: 「이미지」에서 이미지를 선택하면 미리 보기에 배치됩니다. 드래그로 이동하고 확대/축소·회전할 수 있습니다.
5) 도형 추가: 「도형」에서 사각형/타원, 선 색상, 채우기, 선 두께를 선택한 뒤 캔버스를 드래그하여 그립니다.
6) 그리기: 「그리기」에서 브러시 색상과 두께를 선택해 자유롭게 그립니다.
7) 배경색: 「배경」에서 색을 선택하고 「적용」하면 페이지 전체 내용 뒤에 배경색을 깝니다.
8) 기존 텍스트 편집: 미리 보기의 텍스트를 탭하여 선택합니다. 「대체 텍스트」를 입력하거나 「원본 텍스트 삭제」를 선택하세요. 크기/색상/스타일/폰트도 변경할 수 있습니다.
   • 문자 집합과 폰트가 일치하면 같은 위치에 대체됩니다.
   • 표시할 수 없는 문자, 이동, 크기/색상/폰트/스타일 변경 시에는 전체 텍스트 줄이 재생성되며 원본 서식(폰트/크기/색상/스타일)을 유지합니다. 명시적으로 변경하지 않는 한 원래 모습이 유지됩니다.
9) 레이어: 추가/편집한 항목이 「레이어」에 나열됩니다. 행을 탭하여 선택, ▲▼로 겹침 순서 변경, ×로 삭제합니다. 가는 그리기는 탭 선택이 어려우므로 이 목록에서 선택하세요.
10) 「적용」을 누르면 현재 편집이 임시 PDF에 반영되고 실제 모습으로 미리 보기가 업데이트됩니다. 도형과 그리기는 「적용」 후에도 레이어로 남아 선택·순서 변경·삭제할 수 있으며, 최종 저장 시에만 병합됩니다.
11) 「적용 및 저장」으로 최종 PDF를 출력합니다. 저장하지 않은 변경이 있는 상태로 돌아가려 하면 먼저 확인을 요청합니다.
* 텍스트 추가/편집에는 내장 폰트가 필요합니다. Noto Sans JP / Noto Serif JP / M PLUS Rounded 1c / Zen Kaku Gothic New / Klee One / Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR / Noto Sans Arabic / Noto Sans Hebrew / Noto Sans Math / Noto Sans Symbols 2(모두 SIL OFL) 중에서 선택하세요. 각 폰트는 최초 한 번만 다운로드되며 이후 오프라인으로 사용 가능합니다.

── PDF 변환 및 구성 ──
• 분할(페이지 추출): PDF를 열고 페이지를 선택한 뒤 「하나의 PDF로 합치기」 또는 「페이지당 PDF 하나」를 선택하여 내보냅니다.
• 병합: 여러 PDF를 선택하고 순서를 정한 뒤 하나로 병합합니다.
• 재정렬: 썸네일을 드래그하여 페이지 순서를 바꾸고 새 PDF로 저장합니다.
• PDF를 이미지로: 각 페이지를 PNG / JPEG로 변환합니다. DPI를 지정할 수 있습니다.
• 이미지를 PDF로: 여러 이미지를 선택하고 순서를 지정하여 하나의 PDF로 합칩니다.

── OCR / AI-OCR ──
• OCR / 텍스트 추출: PDF/이미지에서 텍스트를 추출합니다. 내장 텍스트와 OCR를 구분하며 JSON으로도 출력할 수 있습니다. 엔진: Tesseract / PaddleOCR. 대용량 문서는 백그라운드 실행이 가능합니다.
• PDF 요약: LLM으로 전체 파일 또는 페이지별 요약을 생성합니다.
• 모델 다운로드 및 LLM 연결 설정은 「설정 → OCR 설정 및 모델」에서 합니다.

── 설정 ──
• OCR 설정 및 모델: Tesseract/PaddleOCR 모델과 편집용 폰트를 다운로드하고 LLM 연결을 설정합니다.
• 매뉴얼 / 개인정보 처리방침 / 라이선스: 본 문서(각 화면에서 본문 복사 가능).

── 오프라인 사용 ──
• PDF 및 이미지 처리는 기기 내에서 완결됩니다. 네트워크는 모델/폰트의 최초 다운로드와 LLM 요청 시에만 사용됩니다(기본 LLM 엔드포인트는 기기 내 127.0.0.1).

── 전문가 모드 / 로컬 OCR API ──
설정 → OCR 설정에서 「전문가 모드」를 활성화하면 이 기기가 OCR API 서버가 됩니다.

■ 엔드포인트: POST http://<기기IP>:<포트>/ocr
■ 형식: multipart/form-data — 필드: file(필수), engine(선택), lang(선택)
■ 응답: {"pages":[...],"engine":"...","languages":[...],"pageCount":1}
■ 예시: curl -X POST http://192.168.1.10:8765/ocr -F "file=@doc.pdf" -F "lang=kor"
■ 상태 확인: GET http://<IP>:<포트>/health → {"status":"ok"}
■ 주의: 신뢰할 수 있는 로컬 네트워크에서만 사용하세요(인증 없음).
""".trimIndent()

private val PRIVACY_KO = """
■ 개인정보 처리방침

• 이 앱의 PDF, 이미지, OCR 처리는 원칙적으로 기기 내에서 완결됩니다. 파일 내용은 당사 서버로 전송되거나 수집되지 않습니다.
• 네트워크 통신은 다음의 경우에만 발생합니다:
  - OCR 모델(Tesseract / PaddleOCR) 또는 폰트의 최초 다운로드.
  - 「로컬 LLM Vision」/「요약」 사용 시 설정한 LLM 서버로의 요청. 기본 엔드포인트는 기기 내(127.0.0.1)이지만, 외부 서버를 지정한 경우 페이지 이미지와 추출된 텍스트가 해당 서버로 전송됩니다. 목적지는 사용자의 설정에 따릅니다.
  - 광고 표시: 이 앱은 광고를 제공하기 위해 Google AdMob을 사용합니다.
• 입력 파일은 읽기 전용이며, 원본 파일은 절대 덮어쓰지 않습니다. 출력은 항상 새 파일로 생성됩니다.
• 광고: 이 앱은 Google AdMob(Google LLC 제공)을 사용합니다. 광고 제공을 위해 Google은 광고 식별자(AAID), IP 주소, 기기 정보, 사용 데이터를 수집하고 사용할 수 있습니다. 이 데이터는 Google의 개인정보 처리방침에 따라 제3자인 Google이 처리합니다. 파일 내용은 광고 목적으로 전송되지 않습니다.
  - Google의 데이터 사용: https://policies.google.com/technologies/partner-sites
  - 기기의 「설정 → Google → 광고」에서 광고 ID를 재설정/옵트아웃하여 광고 개인화를 제한할 수 있습니다.
• 위의 광고 제공을 제외하고, 앱은 자체 분석이나 추적을 수행하지 않습니다.

문의 사항은 앱 제공자에게 연락하세요.
""".trimIndent()

private val LICENSES_KO = """
■ 크레딧 및 오픈소스 라이선스

이 앱은 다음 오픈소스 소프트웨어/폰트를 사용합니다. 각 라이선스 전문은 각 프로젝트의 배포물을 참조하세요.

• Apache PDFBox (pdfbox-android / tom-roush) — Apache License 2.0
• Tesseract OCR (tesseract4android) — Apache License 2.0
• ONNX Runtime (onnxruntime-android) — MIT License
• PaddleOCR 모델 (PP-OCR / RapidOCR 경유 배포) — Apache License 2.0
• Google Mobile Ads SDK (play-services-ads) / User Messaging Platform (UMP) — Google 약관 적용
• 폰트(모두 SIL Open Font License 1.1):
  - Noto Sans JP / Noto Serif JP
  - Noto Sans / Noto Sans SC / Noto Sans TC / Noto Sans KR
  - Noto Sans Arabic / Noto Sans Hebrew
  - Noto Sans Math / Noto Sans Symbols 2
  - M PLUS Rounded 1c
  - Zen Kaku Gothic New
  - Klee One
• AndroidX / Jetpack Compose / Material Components / WorkManager — Apache License 2.0
• Dagger Hilt — Apache License 2.0
• Kotlin / Kotlin Coroutines / kotlinx.serialization — Apache License 2.0
• Coil — Apache License 2.0

위의 폰트는 SIL OFL 1.1에 따라 앱에 포함되고 PDF에 내장됩니다(유료 앱 포함).
""".trimIndent()
