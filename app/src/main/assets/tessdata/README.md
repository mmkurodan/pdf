# Tesseract 学習データ (traineddata) の配置場所

このフォルダに OCR 用の学習データを置くと、初回起動時に端末内へ展開され、
完全オフラインで OCR が動作します（外部通信は行いません）。

## 必要なファイル
- `jpn.traineddata` （日本語）
- `eng.traineddata` （英語）
- 追加言語があれば `<lang>.traineddata`

## 入手方法（開発時にビルドへ同梱する場合）
公式リポジトリから取得し、このフォルダに置いてから再ビルドしてください。
高速版 (tessdata_fast) が端末には適しています。

    https://github.com/tesseract-ocr/tessdata_fast

例:
    app/src/main/assets/tessdata/jpn.traineddata
    app/src/main/assets/tessdata/eng.traineddata

## 実行時に取り込む場合
学習データを同梱していなくてもアプリはビルド・起動できます。
その場合は OCR 画面の「学習データを取り込む」から、端末内の tessdata フォルダを
SAF で選択すると、`jpn.traineddata` などが端末内へコピーされます。

> バイナリの学習データはこのリポジトリには含めていません（サイズが大きいため）。
> 同梱しない場合、OCR エンジンは «モデル未検出» として安全に失敗し、UI に案内を表示します。
