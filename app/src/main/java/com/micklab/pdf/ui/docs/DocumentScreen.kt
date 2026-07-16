package com.micklab.pdf.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.micklab.pdf.ui.common.SectionCard
import com.micklab.pdf.ui.common.ToolScaffold
import com.micklab.pdf.ui.navigation.PdfDestination

/** Static in-app documents (manual / privacy / licenses), each copyable to the clipboard. */
@Composable
fun DocumentScreen(destination: PdfDestination, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val text = documentText(destination)

    ToolScaffold(title = destination.title, onBack = onBack) { padding ->
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
                Text("  本文をコピー")
            }
            SectionCard(title = destination.title) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun documentText(destination: PdfDestination): String = when (destination) {
    PdfDestination.MANUAL -> MANUAL
    PdfDestination.PRIVACY -> PRIVACY
    PdfDestination.LICENSES -> LICENSES
    else -> ""
}

private val MANUAL = """
■ 操作マニュアル

【PDF 作成と編集】
・PDF 編集: PDF を選ぶ（または「白紙から作成」）→ テキスト/画像を追加、既存の文字をタップして編集・削除。プレビュー上でドラッグして移動、色・サイズを変更できます。
・「決定」を押すと、その時点の編集を一時 PDF に反映して実際の見た目で表示します（レイヤーは確定）。「適用して保存」で出力します。
・元のファイルは変更しません（常に新規保存）。

【PDF 変換と構成】
・分解: 選んだページを取り出して新しい PDF に。
・統合: 複数の PDF を 1 つに結合。
・並べ替え: ページ順を入れ替え。
・画像化: 各ページを PNG/JPEG に（DPI 指定可）。
・画像→PDF: 複数画像を順序指定で 1 つの PDF に。

【OCR / AI-OCR】
・OCR/テキスト抽出: 埋め込みテキストと OCR を区別して抽出。
・サマリ: ファイル全体・ページ単位を LLM で要約。
・エンジン(Tesseract / PaddleOCR)や LLM 接続は「環境設定」で設定します。

【環境設定】
・OCR モデルの取得、LLM 接続、各種ドキュメントを確認できます。

※ テキスト追加・編集には日本語フォント(Noto Sans JP)の取得が必要です（初回のみ通信、以後オフライン）。
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
