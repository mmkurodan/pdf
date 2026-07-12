package com.micklab.pdf.domain.ocr

import com.micklab.pdf.domain.model.OcrEngineType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an [OcrEngine] by [OcrEngineType]. Engines are contributed via Hilt
 * multibindings (`@IntoSet`), so adding a new backend is a one-line binding in
 * [com.micklab.pdf.di.OcrModule] with no change here.
 */
@Singleton
class OcrEngineRegistry @Inject constructor(
    engines: Set<@JvmSuppressWildcards OcrEngine>,
) {
    private val byType: Map<OcrEngineType, OcrEngine> = engines.associateBy { it.type }

    /** All engines the app knows about, in a stable display order. */
    val engineTypes: List<OcrEngineType> =
        OcrEngineType.entries.filter { it in byType.keys }

    fun engine(type: OcrEngineType): OcrEngine =
        byType[type] ?: error("No OCR engine registered for $type")

    suspend fun isAvailable(type: OcrEngineType, languages: List<String>): Boolean =
        byType[type]?.isAvailable(languages) ?: false
}
