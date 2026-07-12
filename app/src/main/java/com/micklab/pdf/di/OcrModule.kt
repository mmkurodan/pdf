package com.micklab.pdf.di

import com.micklab.pdf.domain.ocr.LlmVisionOcrEngine
import com.micklab.pdf.domain.ocr.OcrEngine
import com.micklab.pdf.domain.ocr.PaddleOcrEngine
import com.micklab.pdf.domain.ocr.TesseractOcrEngine
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Contributes every OCR backend into a single `Set<OcrEngine>` consumed by
 * [com.micklab.pdf.domain.ocr.OcrEngineRegistry]. Adding a new engine = one
 * more `@Binds @IntoSet` line here; the rest of the app is untouched. This is
 * the DI expression of the "OCR モジュールは差し替え可能" requirement.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @IntoSet
    abstract fun bindTesseract(engine: TesseractOcrEngine): OcrEngine

    @Binds
    @IntoSet
    abstract fun bindPaddle(engine: PaddleOcrEngine): OcrEngine

    @Binds
    @IntoSet
    abstract fun bindLlmVision(engine: LlmVisionOcrEngine): OcrEngine
}
