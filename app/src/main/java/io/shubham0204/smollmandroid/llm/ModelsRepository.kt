/*
 * Copyright (C) 2024 Shubham Panchal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shubham0204.smollmandroid.llm

import android.content.Context
import io.shubham0204.smollmandroid.data.AppDB
import io.shubham0204.smollmandroid.data.LLMModel
import io.shubham0204.smollmandroid.ui.screens.manage_asr.ASRModel
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import java.io.File

@Single
class ModelsRepository(private val context: Context, private val appDB: AppDB) {
    init {
        for (model in appDB.getModelsList()) {
            if (!File(model.path).exists()) {
                deleteModel(model.id)
            }
        }
    }

    companion object {
        fun checkIfModelsDownloaded(context: Context): Boolean {
            context.filesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gguf")) {
                    return true
                }
            }
            return false
        }
    }

    fun getModelFromId(id: Long): LLMModel = appDB.getModel(id)

    fun getAvailableModels(): Flow<List<LLMModel>> = appDB.getModels()

    fun getAvailableModelsList(): List<LLMModel> = appDB.getModelsList()

    fun deleteModel(id: Long) {
        appDB.getModel(id).also {
            File(it.path).delete()
            appDB.deleteModel(it.id)
        }
    }

    fun isSpeech2TextModelDownloaded(asrModel: ASRModel): Boolean {
        return File(context.filesDir, asrModel.name).exists()
    }
fun getRecommendedModelUrl(context: Context): String {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    // availMem = RAM liber efectiv (nu totalMem!)
    val availRamGb = memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)

    return when {
        availRamGb >= 15.0 -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q8_0.gguf"
        availRamGb >= 14.0 -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q5_K_M.gguf"
        availRamGb >= 13.0 -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q5_K_M.gguf"
        availRamGb >= 12.0 -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q4_K_M.gguf"
        availRamGb >= 11.0 -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q4_K_M.gguf"
        availRamGb >= 10.0 -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q4_0.gguf"
        availRamGb >= 9.0  -> "https://huggingface.co/bartowski/google_gemma-3-12b-it-GGUF/resolve/main/google_gemma-3-12b-it-Q4_0.gguf"
        availRamGb >= 8.0  -> "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"
        availRamGb >= 7.0  -> "https://huggingface.co/bartowski/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q8_0.gguf"
        availRamGb >= 6.0  -> "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_0.gguf"
        availRamGb >= 5.0  -> "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q5_K_M.gguf"
        availRamGb >= 4.0  -> "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf"
        availRamGb >= 3.0  -> "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf"
        availRamGb >= 2.0  -> "https://huggingface.co/bartowski/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
        availRamGb >= 1.0  -> "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        else               -> "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
    }
}
} 
