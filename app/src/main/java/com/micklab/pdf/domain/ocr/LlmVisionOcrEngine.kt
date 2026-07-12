package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import com.micklab.pdf.domain.model.OcrEngineType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local LLM vision-encoder backend — pluggable extension point for a fully
 * on-device multimodal model (no external API).
 *
 * ## Where to plug a GGUF model in
 * This is the single place to integrate a local vision-language model. A typical
 * llama.cpp-based path:
 *
 * 1. Ship a GGUF pair under `assets/models/` (or download once to filesDir):
 *    - a vision projector / mmproj file (e.g. `mmproj-*.gguf`)
 *    - the language model weights (e.g. `*-Q4_K_M.gguf`)
 *    A small VLM such as a Qwen2-VL / MiniCPM-V / SmolVLM build works on-device.
 *
 * 2. Add a JNI bridge to llama.cpp's multimodal API (`llava`/`clip`):
 *    ```
 *    external fun nativeInit(modelPath: String, mmprojPath: String): Long
 *    external fun nativeRecognize(ctx: Long, rgba: IntArray, w: Int, h: Int,
 *                                 prompt: String): String
 *    external fun nativeFree(ctx: Long)
 *    ```
 *    Build the .so via `externalNativeBuild { cmake { ... } }` in build.gradle,
 *    pointing at the llama.cpp CMake project (NDK is already available: see the
 *    SDK ndk/ folder).
 *
 * 3. In [recognize], embed the page [bitmap], prompt the model with something
 *    like "Extract all text verbatim, preserving reading order", parse the
 *    response into [OcrBlock]s, and return an [OcrPageOutcome].
 *
 * Alternative runtimes that also fit here: MLC-LLM (TVM), MediaPipe LLM
 * Inference, or ONNX Runtime with a quantized encoder. The [OcrEngine] contract
 * stays the same for all of them.
 */
@Singleton
class LlmVisionOcrEngine @Inject constructor() : OcrEngine {

    override val type: OcrEngineType = OcrEngineType.LLM_VISION

    override suspend fun isAvailable(languages: List<String>): Boolean = false

    override suspend fun recognize(bitmap: Bitmap, languages: List<String>): OcrPageOutcome {
        // TODO(local-llm): initialize the GGUF model lazily and run inference.
        throw OcrEngineNotImplementedException(type)
    }
}
