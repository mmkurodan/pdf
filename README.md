# OCR API Server（OCR API サーバー）

PDF や画像を受け取り、**文字情報を JSON で返す OCR API** を提供する Android アプリ。
OCR は端末内で実行し（Tesseract / PaddleOCR / ローカル LLM Vision）、API サーバーは
バックグラウンドで**常時稼働**します。他アプリからは Intent で API を有効化できます。
画面からの直接実行（OCR・要約・AI プロンプト）と PDF ツール（分解・統合・並べ替え・
画像変換・編集）も引き続き同じアプリ内で使えます。

- パッケージ: `com.micklab.pdf` / 既定ポート: `8765`
- アイコン原版: `app_icon.jpeg`（1024px）

## 画面構成

ホームは companion の LLM サーバーアプリ（`com.micklab.llama`）と同じ並びです。

| 位置 | 内容 |
| --- | --- |
| ① OCR API サーバー | 状態表示、「API を開始 / 停止」、ポート、接続先エンドポイント（タップでコピー）、バッテリー最適化の除外、「環境設定」「終了」 |
| ② OCR / AI-OCR | OCR / テキスト抽出、PDF サマリ、AI プロンプト（画面からの直接実行） |
| ③ PDF 変換と構成 | 分解、統合、並べ替え、PDF 画像化、画像 → PDF 化 |
| ④ PDF 作成と編集 | PDF 編集 |
| ⑤ 環境設定 | OCR 設定・モデル管理、言語、操作マニュアル、プライバシーポリシー、権利表記 |
| ⑥ 処理状況 / ログ | API の開始・停止・リクエスト・エラーの履歴 |

②〜⑥ は見出しタップで開閉します。

## OCR API

```
POST http://<端末IP>:<ポート>/ocr      multipart/form-data
GET  http://<端末IP>:<ポート>/health   → {"status":"ok"}
```

| フィールド | 必須 | 説明 |
| --- | --- | --- |
| `file` | ○ | PDF または画像（PNG / JPEG など） |
| `engine` | | `tesseract`（既定） / `paddleocr` / `llm` |
| `lang` | | 言語コード。`eng`（既定）、`jpn,eng` のように複数指定可 |
| `dpi` | | PDF のレンダリング解像度 36〜600（既定 150） |

```bash
curl -X POST http://192.168.1.10:8765/ocr \
  -F "file=@doc.pdf" -F "engine=tesseract" -F "lang=jpn,eng"
```

```json
{
  "pages": [ {
    "page": 1, "text": "…", "confidence": 0.92, "source": "OCR",
    "width": 1080, "height": 2400,
    "blocks": [ { "text": "…", "confidence": 0.95,
                  "box": { "left": 40, "top": 120, "right": 620, "bottom": 168 } } ]
  } ],
  "engine": "Tesseract", "languages": ["jpn", "eng"], "dpi": 150, "pageCount": 1
}
```

`blocks` は認識した行ごとのテキストと位置です。`box` は OCR した画像（`width` × `height`、
PDF は `dpi` でレンダリングした後のサイズ）上のピクセル座標で、原点は左上です。Tesseract と
PaddleOCR は `box` を返し、位置を持たない LLM Vision では `box` を省略します。

エラーは HTTP 400 / 500 と `{"error":"…"}`。認証はありません。信頼できる LAN 内でのみ
利用し、外部ネットワークには公開しないでください。

### 常時稼働

`OcrApiService`（foreground service, `specialUse`）が `OcrApiServer` を保持します。
一度有効にすると「有効」という意思を保存し、明示的に停止するまで再開し続けます。

| 契機 | 動作 |
| --- | --- |
| プロセス Kill | `START_STICKY` で OS が再起動 |
| タスクをスワイプで終了 | `onTaskRemoved` で再開を要求 |
| 端末再起動 | `BootReceiver`（`BOOT_COMPLETED`）で再開 |
| 上記で戻らない場合 | `WatchdogReceiver`（約 60 秒間隔のアラーム）が未稼働を検知して再開 |
| アプリ起動 | `MainActivity` が有効状態を再確認して再開 |
| OCR 処理中 | WakeLock + Wi-Fi ロックを保持（画面オフでも処理継続） |

停止するのは「API を停止」「終了」、通知の「停止」、他アプリからの `STOP_SERVICE` のみ。

制約: Android 12 以降はバックグラウンドからの foreground service 開始が制限されるため、
ウォッチドッグによる復帰は**バッテリー最適化の除外**（ホームのボタン）が前提です。
OS / メーカー独自機能による強制停止（force-stop）はアラームも消えるため復帰できません。

### 他アプリからの有効化

サービスは `exported` です。明示的 Intent を `startForegroundService()` で送ると、
本アプリの画面を開かずに API を開始/停止できます。

| 項目 | 値 |
| --- | --- |
| component | `com.micklab.pdf` / `com.micklab.pdf.api.OcrApiService` |
| 開始 action | `com.micklab.pdf.START_SERVICE`（任意: int extra `port`、1024〜65535。省略時は保存済みポート） |
| 停止 action | `com.micklab.pdf.STOP_SERVICE` |

```kotlin
// 呼び出し側 AndroidManifest.xml（Android 11+）:
// <queries><package android:name="com.micklab.pdf" /></queries>
val intent = Intent()
    .setClassName("com.micklab.pdf", "com.micklab.pdf.api.OcrApiService")
    .setAction("com.micklab.pdf.START_SERVICE")
    .putExtra("port", 8765)
ContextCompat.startForegroundService(context, intent)
// 起動確認: GET http://127.0.0.1:8765/health
```

```bash
adb shell am start-foreground-service -n com.micklab.pdf/.api.OcrApiService \
  -a com.micklab.pdf.START_SERVICE --ei port 8765
```

呼び出し側はフォアグラウンド（画面表示中）である必要があります。Android 13 以降で
本アプリに通知権限が無い場合もサービスは動作しますが、通知は表示されません
（一度アプリを開いて「API を開始」を押すと権限を確認します）。

## 機能（画面からの直接実行 / PDF ツール）

| 機能 | 説明 | 実装 |
| --- | --- | --- |
| PDF 分解（ページ抽出） | 選択ページを 1 つの PDF、または 1 ページずつに | PDFBox `importPage` |
| PDF 統合（結合） | 複数 PDF を順序指定で結合 | PDFBox `PDFMergerUtility` |
| PDF 並べ替え | ページ順序変更・削除・複製 | PDFBox `importPage` |
| PDF 画像化 | 各ページを PNG / JPEG（DPI 指定可）へ | `android.graphics.pdf.PdfRenderer` |
| 画像 → PDF 化 | 複数画像を順序指定して 1 PDF に | PDFBox `JPEGFactory` |
| OCR / テキスト抽出 | 埋め込みテキストと OCR を**区別**して JSON 出力 | PDFTextStripper + Tesseract |
| PDF サマリ（要約） | **ファイル全体＋ページごと**を LLM で要約。**OCR→LLM** と **LLM Vision 直接**の 2 方式 | SummarizeDocumentUseCase + LlmClient |

- 単一ページ・複数ページ・ファイル全体のいずれにも対応。
- ページ操作は**サムネイル（ミニ画像）**で操作: 分割・画像化は**タップで選択**、
  並べ替えは**長押しドラッグ**（↑↓ でも可）。Merge は各 PDF の**先頭ページ**を表示。
- サムネイルは**遅延読み込み**（`PdfThumbnailLoader` + LRU キャッシュ）で、
  多ページ PDF でも可視ページのみ描画。
- OCR 結果は **JSON** と **テキストのみ** を切り替えて表示・コピーできる。
- OCR は日本語・英語を含む多言語対応（`jpn+eng` など）。
- **埋め込みテキストレイヤー**が存在するページは `EMBEDDED_TEXT_LAYER`、OCR 結果は
  `OCR` として `TextSource` で区別（下記 JSON 参照）。
- ダークモード対応 / 日本語 UI / 全処理を Logcat に出力。

## アーキテクチャ (MVVM + UseCase + Repository, DI = Hilt)

```
ui (Compose) ──▶ ViewModel ──▶ UseCase ──▶ Repository / PdfWorkspace / OcrEngine
   画面           状態管理        処理統合        SAF入出力 / PDFBox / OCR
```

- **UI 層**: Jetpack Compose（Material3、ダークモード、動的カラー）。SAF ピッカーで入出力選択。
- **ViewModel 層**: `StateFlow` で非同期処理と UI 状態を管理（Coroutine）。
- **UseCase 層**: PDF/画像/OCR 処理を統合。大容量 PDF はテンポラリファイル + scratch
  メモリ設定でストリーム処理。
- **Repository 層**: `FileRepository` が SAF (ContentResolver / DocumentFile) を隠蔽。
- **Worker**: `PdfProcessingWorker` (`@HiltWorker`, `CoroutineWorker`) で OCR を
  バックグラウンド実行（Coroutine + Worker 併用）。
- **DI**: Hilt。OCR エンジンは Hilt マルチバインディング (`@IntoSet`) で**差し替え可能**。

### パッケージ構成
```
com.micklab.pdf
├── PdfToolsApp.kt            # @HiltAndroidApp, PDFBox 初期化, WorkManager 設定
├── api/                     # OcrApiServer(HTTP), OcrApiService(常時稼働), Boot/Watchdog, OcrApiState
├── MainActivity.kt          # 単一 Activity + Compose
├── core/                    # DispatcherProvider, OperationState
├── di/                      # AppModule, DataModule, DispatchersModule, OcrModule
├── data/repository/         # FileRepository (SAF)
├── domain/
│   ├── model/               # OcrModels(JSON), PdfModels
│   ├── ocr/                 # OcrEngine 抽象 + Tesseract / Paddle / LLM
│   ├── pdf/                 # PdfWorkspace, PageRangeParser
│   └── usecase/             # Split/Merge/Reorder/PdfToImages/ImagesToPdf/ExtractText…
├── worker/                  # PdfProcessingWorker (@HiltWorker)
└── ui/                      # theme, navigation, common, home, split, merge, …, ocr
```

## OCR 出力 (JSON)

`ExtractDocumentTextUseCase` は `DocumentTextResult` を返し、`kotlinx.serialization`
で JSON 化されます（OCR 画面でコピー可能）。

```json
{
  "fileName": "sample.pdf",
  "pageCount": 2,
  "engine": "Tesseract (jpn+eng)",
  "languages": ["jpn", "eng"],
  "createdAtEpochMs": 1700000000000,
  "pages": [
    { "pageIndex": 0, "pageNumber": 1, "source": "EMBEDDED_TEXT_LAYER", "text": "…" },
    { "pageIndex": 1, "pageNumber": 2, "source": "OCR", "text": "…",
      "averageConfidence": 0.92,
      "blocks": [ { "text": "…", "confidence": 0.92, "boundingBox": {"left":0,"top":0,"right":100,"bottom":40} } ] }
  ]
}
```

## OCR モデル（オフライン）

Tesseract の学習データはリポジトリに含めていません（サイズが大きいため）。次のいずれかで配置します:

1. **アプリ内ダウンロード（推奨）**: OCR 画面の「選択中の言語モデルをダウンロード」で、
   公式 [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) から選択言語の
   `*.traineddata` を取得（初回のみ通信、`INTERNET` 権限使用）。取得後は完全オフライン。
   例: jpn 約 2.4MB / eng 約 4.1MB。原子的書き込み（temp→rename）で破損を防止。
2. **同梱**: `app/src/main/assets/tessdata/` に `jpn.traineddata` / `eng.traineddata`
   を置いて再ビルド（初回起動時に端末内へ展開）。
3. **端末内フォルダから取込**: OCR 画面の「端末内のフォルダから取り込む」から、
   `tessdata` フォルダを SAF で選択（`*.traineddata` をコピー、通信なし）。

モデルが無い場合でもアプリはビルド・起動でき、OCR が必要なページで «モデル未検出» として
明示エラーを表示します（無言で空文字にはしません）。

## OCR エンジンの差し替え / 拡張ポイント

`OcrEngine` インターフェースを実装し、`di/OcrModule` に 1 行 `@Binds @IntoSet` を足すだけ。

- `TesseractOcrEngine` — 実装済み（オフライン）。公式 tessdata_fast をアプリ内 DL 可。
- `LlmVisionOcrEngine` — **実装済み**。**Ollama `/api/chat`** と **OpenAI 互換
  `/v1/chat/completions`** の両対応で、ページ画像(base64)を送って `gemma3:4b` 等の
  Vision モデルで OCR。接続先(URL/モデル/APIキー)は OCR 画面で設定・接続確認できます。
  llamachat の LLM API 方式を取り込み、`/root/llama` の端末内サーバや任意の LLM
  サーバに接続可能（HTTP のため `usesCleartextTraffic=true` / `INTERNET`）。
  モデルは **`/api/tags`（OpenAI 互換は `/v1/models`）から取得して選択**（既定値は
  `default`、一覧から実モデルを選ぶ運用）。HTTP は `LlmClient` に集約し、OCR と
  PDF サマリで共有。
- `PaddleOcrEngine` — **実装済み（ONNX Runtime）**。PP-OCR の ONNX モデル
  （det `ch_PP-OCRv4_det` + 日本語 rec `japan_rec_crnn` + `japan_dict`）をアプリ内
  ダウンロードし、`PaddleOcrPipeline` で **detection → 連結成分ボックス抽出 →
  recognition → CTC デコード** を実行（オンデバイス・完全オフライン・日本語＋英数字）。
  ※ 検出後処理は回転矩形＋unclip の代わりに軸平行の連結成分（OpenCV 非依存）で
  近似しているため、横書き文書向けです。

## ビルド

CI（`.github/workflows/android.yml`）と同じツールチェーンで検証済み:

- **Gradle 8.11.1** / **Android Gradle Plugin 8.10.1** / **JDK 17**
- Kotlin 2.0.20 / Compose BOM 2024.09 / Hilt 2.52（KSP）
- compileSdk 36 / minSdk 24 / targetSdk 36

```bash
# デバッグ APK
./gradlew assembleDebug

# 単体テスト
./gradlew testDebugUnitTest

# リリース（署名は app/keystore.jks と KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD）
./gradlew assembleRelease bundleRelease
```

`test-before-aapt2.sh` はコンパイル → AAPT2 → `assembleDebug` → `testDebugUnitTest`
を一括検証します:

```bash
./test-before-aapt2.sh app --full --apk-debug
```

## テスト

- **単体 (JVM)**: `PageRangeParserTest`（ページ指定パーサ）、`DocumentTextResultJsonTest`
  （JSON ラウンドトリップ / 埋め込み・OCR の区別）。
- **計装 (androidTest)**: `HiltTestRunner` + Hilt 計装テスト、`HomeScreenComposeTest`
  （Compose UI）。

## 依存ライブラリ（主なもの）

| 用途 | ライブラリ |
| --- | --- |
| PDF 操作 | `com.tom-roush:pdfbox-android`（Apache PDFBox の Android 版） |
| OCR | `cz.adaptech.tesseract4android:tesseract4android`（JitPack、オフライン） |
| OCR (PaddleOCR) | `com.microsoft.onnxruntime:onnxruntime-android`（PP-OCR ONNX 推論） |
| UI | Jetpack Compose (Material3), Navigation Compose |
| DI | Hilt (+ hilt-work, hilt-navigation-compose) |
| 非同期 | Kotlin Coroutines, WorkManager |
| JSON | kotlinx.serialization |
| 画像表示 | Coil |

> Tesseract4Android は JitPack 配布のため、`settings.gradle` の
> `dependencyResolutionManagement.repositories` に該当グループのみ限定して
> `https://jitpack.io` を追加しています。
