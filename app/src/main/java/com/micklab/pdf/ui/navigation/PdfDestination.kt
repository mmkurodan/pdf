package com.micklab.pdf.ui.navigation

/** All navigable destinations. */
enum class PdfDestination(val route: String, val title: String) {
    HOME("home", "PDF ツールキット"),
    SPLIT("split", "PDF 分解（ページ抽出）"),
    MERGE("merge", "PDF 統合（結合）"),
    REORDER("reorder", "PDF 並べ替え"),
    PDF_TO_IMAGE("pdf_to_image", "PDF 画像化"),
    IMAGE_TO_PDF("image_to_pdf", "画像 → PDF 化"),
    EDIT("edit", "PDF 編集（テキスト・画像を追加）"),
    OCR("ocr", "OCR / テキスト抽出"),
    SUMMARY("summary", "PDF サマリ（要約）"),
    OCR_SETTINGS("ocr_settings", "OCR 設定・モデル管理"),
    MANUAL("manual", "操作マニュアル"),
    PRIVACY("privacy", "プライバシーポリシー"),
    LICENSES("licenses", "権利表記・ライセンス"),
}
