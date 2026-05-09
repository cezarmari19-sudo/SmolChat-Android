package io.shubham0204.smollmandroid.ui.screens.model_download

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.shubham0204.hf_model_hub_api.HFModelInfo
import io.shubham0204.hf_model_hub_api.HFModelTree
import io.shubham0204.smollm.GGUFReader
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollmandroid.R
import io.shubham0204.smollmandroid.ui.components.AppAlertDialog
import io.shubham0204.smollmandroid.ui.components.AppBarTitleText
import io.shubham0204.smollmandroid.ui.components.AppProgressDialog
import io.shubham0204.smollmandroid.ui.screens.chat.ChatActivity
import io.shubham0204.smollmandroid.ui.theme.SmolLMAndroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import java.io.File

class DownloadModelActivity : ComponentActivity() {
    private var openChatScreen: Boolean = true
    private val viewModel: DownloadModelsViewModel by inject()

    private fun registerModelInDatabase(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ggufReader = GGUFReader()
                ggufReader.load(file.absolutePath)
                val contextSize = ggufReader.getContextSize() ?: SmolLM.DefaultInferenceParams.contextSize
                val chatTemplate = ggufReader.getChatTemplate() ?: SmolLM.DefaultInferenceParams.chatTemplate

                viewModel.appDB.addModel(
                    file.name,
                    "",
                    file.absolutePath,
                    contextSize.toInt(),
                    chatTemplate,
                )

                withContext(Dispatchers.Main) {
                    openChatActivity()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    openChatActivity()
                }
            }
        }
    }

    @Serializable
    data class ViewModelRoute(
        val modelId: String,
        val modelInfo: HFModelInfo.ModelInfo,
        val modelFiles: List<HFModelTree.HFModelFile>,
    )

    @Serializable
    object HfModelSelectRoute

    @Serializable
    object DownloadModelRoute

    private fun getRecommendedModelUrl(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
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

    private fun resolveDownloadedFile(localUri: String, downloadId: Long): File? {
        return try {
            val uri = Uri.parse(localUri)
            if (uri.scheme == "file") {
                File(uri.path!!)
            } else {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val pathIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                    val path = cursor.getString(pathIndex)
                    cursor.close()
                    if (path != null) File(path) else null
                } else {
                    cursor.close()
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openChatScreen = intent.extras?.getBoolean("openChatScreen") ?: true

        if (viewModel.appDB.getModelsList().isNotEmpty()) {
            openChatActivity()
            return
        }

        setContent {
            SmolLMAndroidTheme {
                AutoDownloadScreen()
            }
        }
    }

    @Composable
    private fun AutoDownloadScreen() {
        var statusText by remember { mutableStateOf("Se inițializează...") }

        LaunchedEffect(Unit) {
            val modelUrl = getRecommendedModelUrl()
            // variabilă locală — nu mai există race condition cu câmpul din Activity
            var currentDownloadId = viewModel.enqueueDownload(modelUrl)
            statusText = "Descărcare pornită..."

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            while (true) {
                delay(3000)
                val query = DownloadManager.Query().setFilterById(currentDownloadId)
                val cursor = downloadManager.query(query)

                if (!cursor.moveToFirst()) {
                    cursor.close()
                    statusText = "Aștept descărcarea..."
                    continue
                }

                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))

                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        val total = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val downloaded = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        statusText = if (total > 0) {
                            "Se descarcă... ${(downloaded * 100 / total).toInt()}%"
                        } else {
                            "Se descarcă..."
                        }
                        cursor.close()
                    }

                    DownloadManager.STATUS_PAUSED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        )
                        statusText = "Pauză (motiv: $reason) - aștept..."
                        cursor.close()
                    }

                    DownloadManager.STATUS_PENDING -> {
                        statusText = "În așteptare..."
                        cursor.close()
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val localUri = cursor.getString(
                            cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        )
                        cursor.close()
                        statusText = "Înregistrare model..."

                        val file = resolveDownloadedFile(localUri, currentDownloadId)
                        if (file != null && file.exists()) {
                            registerModelInDatabase(file)
                        } else {
                            val fileName = modelUrl.substringAfterLast('/')
                            val fallback = File(
                                Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                                ),
                                fileName
                            )
                            registerModelInDatabase(
                                if (fallback.exists()) fallback else File(localUri)
                            )
                        }
                        break
                    }

                    DownloadManager.STATUS_FAILED -> {
                        cursor.close()
                        statusText = "Eșuat, reîncerc..."
                        delay(2000)
                        // currentDownloadId local — query-ul următor va folosi noul ID corect
                        currentDownloadId = viewModel.enqueueDownload(modelUrl)
                    }

                    else -> cursor.close()
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text("Se descarcă modelul AI...")
                Text(statusText)
            }
        }
    }

    private fun openChatActivity() {
        if (openChatScreen) {
            Intent(this, ChatActivity::class.java).apply {
                startActivity(this)
                finish()
            }
        } else {
            finish()
        }
    }

    private enum class AddNewModelStep {
        ImportModel,
        DownloadModel,
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AddNewModelScreen(onHFModelSelectClick: () -> Unit) {
        var addNewModelStep by remember { mutableStateOf(AddNewModelStep.DownloadModel) }
        SmolLMAndroidTheme {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { AppBarTitleText(stringResource(R.string.add_new_model_title)) }
                    )
                },
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (addNewModelStep) {
                        AddNewModelStep.ImportModel -> {
                            ImportModelScreen(
                                onPrevSectionClick = {
                                    addNewModelStep = AddNewModelStep.DownloadModel
                                },
                                checkGGUFFile = ::checkGGUFFile,
                                copyModelFile = { modelFileUri ->
                                    viewModel.copyModelFile(
                                        modelFileUri,
                                        onComplete = { openChatActivity() },
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            )
                        }
                        AddNewModelStep.DownloadModel -> {
                            DownloadModelScreen(
                                onHFModelSelectClick = onHFModelSelectClick,
                                onNextSectionClick = {
                                    addNewModelStep = AddNewModelStep.ImportModel
                                },
                                onDownloadModelClick = { selectedPopularModelIndex ->
                                    viewModel.downloadModelFromIndex(selectedPopularModelIndex)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            )
                        }
                    }
                }
                AppProgressDialog()
                AppAlertDialog()
            }
        }
    }

    private fun checkGGUFFile(uri: Uri): Boolean {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val ggufMagicNumberBytes = ByteArray(4)
            inputStream.read(ggufMagicNumberBytes)
            return ggufMagicNumberBytes.contentEquals(byteArrayOf(71, 71, 85, 70))
        }
        return false
    }
}