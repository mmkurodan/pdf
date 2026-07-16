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
