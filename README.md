# PDF ツールキット (PDF Toolkit)

端末内で完結する（オフライン）Android 用 PDF・画像・OCR 処理アプリ。外部 API を
使わず、SAF で選んだファイルだけを読み取り、結果は新規ファイルとして書き出します
（既存ファイルは破壊しません）。

## 機能

| 機能 | 説明 | 実装 |
| --- | --- | --- |
| PDF 分解（ページ抽出） | 選択ページを 1 つの PDF、または 1 ページずつに | PDFBox `importPage` |
| PDF 統合（結合） | 複数 PDF を順序指定で結合 | PDFBox `PDFMergerUtility` |
| PDF 並べ替え | ページ順序変更・削除・複製 | PDFBox `importPage` |
| PDF 画像化 | 各ページを PNG / JPEG（DPI 指定可）へ | `android.graphics.pdf.PdfRenderer` |
| 画像 → PDF 化 | 複数画像を順序指定して 1 PDF に | PDFBox `JPEGFactory` |
| OCR / テキスト抽出 | 埋め込みテキストと OCR を**区別**して JSON 出力 | PDFTextStripper + Tesseract |

- 単一ページ・複数ページ・ファイル全体のいずれにも対応。
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

1. **同梱**: `app/src/main/assets/tessdata/` に `jpn.traineddata` / `eng.traineddata`
   を置いて再ビルド（初回起動時に端末内へ展開）。取得先は
   [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)。
2. **実行時取込**: OCR 画面の「学習データを取り込む」から、端末内の `tessdata` フォルダを
   SAF で選択（`*.traineddata` をコピー）。

モデルが無い場合でもアプリはビルド・起動でき、OCR は «モデル未検出» として安全に失敗し、
UI に案内を表示します。

## OCR エンジンの差し替え / 拡張ポイント

`OcrEngine` インターフェースを実装し、`di/OcrModule` に 1 行 `@Binds @IntoSet` を足すだけ。

- `TesseractOcrEngine` — 実装済み（オフライン）。
- `PaddleOcrEngine` — 拡張ポイント（Paddle-Lite モデルの組込手順をコメントに明記）。
- `LlmVisionOcrEngine` — **ローカル LLM (GGUF) Vision エンコーダ**の拡張ポイント。
  llama.cpp の multimodal (llava/clip) への JNI ブリッジ、`externalNativeBuild`
  (CMake + NDK) での `.so` ビルド手順をコメントに明記。

## ビルド

CI（`.github/workflows/android.yml`）と同じツールチェーンで検証済み:

- **Gradle 8.7** / **Android Gradle Plugin 8.6.1** / **JDK 17**
- Kotlin 2.0.20 / Compose BOM 2024.09 / Hilt 2.52（KSP）
- compileSdk 35 / minSdk 24 / targetSdk 35

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
| UI | Jetpack Compose (Material3), Navigation Compose |
| DI | Hilt (+ hilt-work, hilt-navigation-compose) |
| 非同期 | Kotlin Coroutines, WorkManager |
| JSON | kotlinx.serialization |
| 画像表示 | Coil |

> Tesseract4Android は JitPack 配布のため、`settings.gradle` の
> `dependencyResolutionManagement.repositories` に該当グループのみ限定して
> `https://jitpack.io` を追加しています。
