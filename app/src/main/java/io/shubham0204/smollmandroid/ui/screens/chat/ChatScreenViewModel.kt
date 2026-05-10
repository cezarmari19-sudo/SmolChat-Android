/*
Copyright (C) 2024 Shubham Panchal

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.shubham0204.smollmandroid.ui.screens.chat

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.text.Spanned
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollmandroid.R
import io.shubham0204.smollmandroid.data.AppDB
import io.shubham0204.smollmandroid.data.Chat
import io.shubham0204.smollmandroid.data.ChatMessage
import io.shubham0204.smollmandroid.data.Folder
import io.shubham0204.smollmandroid.data.LLMModel
import io.shubham0204.smollmandroid.data.SharedPrefStore
import io.shubham0204.smollmandroid.data.Task
import io.shubham0204.smollmandroid.llm.ModelsRepository
import io.shubham0204.smollmandroid.llm.SmolLMManager
import io.shubham0204.smollmandroid.llm.speech2text.AudioTranscriptionService
import io.shubham0204.smollmandroid.ui.components.createAlertDialog
import io.shubham0204.smollmandroid.ui.screens.manage_asr.SETTING_DEF_VALUE_SPEECH2TEXT_CURR_MODEL_NAME
import io.shubham0204.smollmandroid.ui.screens.manage_asr.SETTING_DEF_VALUE_SPEECH2_TEXT_ENABLED
import io.shubham0204.smollmandroid.ui.screens.manage_asr.SETTING_KEY_SPEECH2TEXT_CURR_MODEL_NAME
import io.shubham0204.smollmandroid.ui.screens.manage_asr.SETTING_KEY_SPEECH2TEXT_ENABLED
import io.shubham0204.smollmandroid.ui.screens.manage_asr.availableASRModels
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

private const val LOGTAG = "[SmolLMAndroid-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }
private val LOGE: (String, Throwable?) -> Unit = { msg, t ->
    if (t != null) Log.e(LOGTAG, msg, t) else Log.e(LOGTAG, msg)
}

// Regex pentru blocuri <think>
private val findThinkHtmlBlockRegex = Regex("<blockquote><i><h6>[\\s\\S]*?</i></h6></blockquote>")
internal fun String.stripThinkingForClipboard() = findThinkHtmlBlockRegex.replace(this, "").trim()

// ─────────────────────────────────────────────────────────────────────────────
// UI EVENTS  (structura originală păstrată, fără sealed class imbricat greșit)
// ─────────────────────────────────────────────────────────────────────────────
sealed class ChatScreenUIEvent {

    // Chat events
    data class UpdateChatModel(val model: LLMModel) : ChatScreenUIEvent()
    data object LoadChatModel : ChatScreenUIEvent()
    data class DeleteModel(val model: LLMModel) : ChatScreenUIEvent()
    data class SendUserQuery(val query: String) : ChatScreenUIEvent()
    data object StopGeneration : ChatScreenUIEvent()
    data class OnTaskSelected(val task: Task) : ChatScreenUIEvent()
    data class OnMessageEdited(
        val chatId: Long,
        val oldMessage: ChatMessage,
        val lastMessage: ChatMessage,
        val newMessageText: String,
    ) : ChatScreenUIEvent()
    data class OnDeleteChat(val chat: Chat) : ChatScreenUIEvent()
    data class OnDeleteChatMessages(val chat: Chat) : ChatScreenUIEvent()
    data object NewChat : ChatScreenUIEvent()
    data class SwitchChat(val chat: Chat) : ChatScreenUIEvent()
    data class UpdateChatSettings(val settings: EditableChatSettings, val existingChat: Chat) :
        ChatScreenUIEvent()
    data class StartBenchmark(val onResult: (String) -> Unit) : ChatScreenUIEvent()
    data class StartAudioTranscription(val onLineComplete: (String) -> Unit) : ChatScreenUIEvent()
    data object StopAudioTranscription : ChatScreenUIEvent()

    // Folder events
    data class UpdateChatFolder(val newFolderId: Long) : ChatScreenUIEvent()
    data class AddFolder(val folderName: String) : ChatScreenUIEvent()
    data class UpdateFolder(val folder: Folder) : ChatScreenUIEvent()
    data class DeleteFolder(val folderId: Long) : ChatScreenUIEvent()
    data class DeleteFolderWithChats(val folderId: Long) : ChatScreenUIEvent()

    // Dialog events
    data class ToggleChangeFolderDialog(val visible: Boolean) : ChatScreenUIEvent()
    data class ToggleSelectModelListDialog(val visible: Boolean) : ChatScreenUIEvent()
    data class ToggleMoreOptionsPopup(val visible: Boolean) : ChatScreenUIEvent()
    data class ToggleTaskListBottomList(val visible: Boolean) : ChatScreenUIEvent()
    data object ToggleRAMUsageLabel : ChatScreenUIEvent()
    data class ShowContextLengthUsageDialog(val chat: Chat) : ChatScreenUIEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────
data class AudioTranscriptionUIState(
    val isRecording: Boolean = false,
    val isAvailable: Boolean = false,
)

data class ChatScreenUIState(
    val chat: Chat = Chat(),
    val isGeneratingResponse: Boolean = false,
    val renderedPartialResponse: Spanned? = null,
    val modelLoadingState: ChatScreenViewModel.ModelLoadingState =
        ChatScreenViewModel.ModelLoadingState.NOT_LOADED,
    val responseGenerationsSpeed: Float? = null,
    val responseGenerationTimeSecs: Int? = null,
    val memoryUsage: Pair<Float, Float>? = null,
    val folders: ImmutableList<Folder> = emptyList<Folder>().toImmutableList(),
    val chats: ImmutableList<Chat> = emptyList<Chat>().toImmutableList(),
    val models: ImmutableList<LLMModel> = emptyList<LLMModel>().toImmutableList(),
    val messages: ImmutableList<ChatMessage> = emptyList<ChatMessage>().toImmutableList(),
    val tasks: ImmutableList<Task> = emptyList<Task>().toImmutableList(),
    val benchmarkResult: String? = null,
    val audioTranscriptionUIState: AudioTranscriptionUIState = AudioTranscriptionUIState(),
    val showChangeFolderDialog: Boolean = false,
    val showSelectModelListDialog: Boolean = false,
    val showMoreOptionsPopup: Boolean = false,
    val showTasksBottomSheet: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// VIEW MODEL
// ─────────────────────────────────────────────────────────────────────────────
@KoinViewModel
class ChatScreenViewModel(
    val context: Context,
    val appDB: AppDB,
    val modelsRepository: ModelsRepository,
    val smolLMManager: SmolLMManager,
    val audioTranscriptionService: AudioTranscriptionService,
    val mdRenderer: MDRenderer,
    val sharedPrefStore: SharedPrefStore,
) : ViewModel() {

    enum class ModelLoadingState {
        NOT_LOADED,
        IN_PROGRESS,
        SUCCESS,
        FAILURE,
    }

    private val _uiState = MutableStateFlow(initializeUIState())
    val uiState: StateFlow<ChatScreenUIState> = _uiState

    var questionTextDefaultVal: String? = null

    private val findThinkTagRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    private lateinit var activityManager: ActivityManager

    // ── Guard împotriva load-urilor multiple simultane ──────────────────────
    private var loadModelJob: Job? = null
    private val isModelLoading = AtomicBoolean(false)

    /**
     * Path-ul modelului care este EFECTIV încărcat în SmolLMManager.
     * null = niciun model încărcat.
     */
    private var currentLoadedModelPath: String? = null

    init {
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        setupCollectors()
        loadModel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD MODEL  (refactorizat complet)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Încarcă modelul asociat chat-ului curent.
     *
     * Reguli de siguranță:
     * 1. Dacă același model e deja încărcat cu succes → skip complet.
     * 2. Dacă un alt load rulează → îl anulăm înainte de a porni unul nou.
     * 3. Verificăm că fișierul există pe disk înainte de a apela SmolLMManager.
     * 4. Un delay mic (300 ms) după unload permite SmolLMManager să se reseteze.
     * 5. AtomicBoolean previne intrarea a două coroutine simultan.
     */
    fun loadModel(onComplete: (ModelLoadingState) -> Unit = {}) {
        val chat = _uiState.value.chat

        // Niciun model selectat → arată dialogul de selecție
        if (chat.llmModelId == -1L) {
            LOGD("loadModel: no model selected for chat ${chat.id}")
            _uiState.update { it.copy(showSelectModelListDialog = true) }
            onComplete(ModelLoadingState.NOT_LOADED)
            return
        }

        // Verifică dacă modelul există în repository
        val model = try {
            modelsRepository.getModelFromId(chat.llmModelId)
        } catch (e: Exception) {
            LOGE("loadModel: model id=${chat.llmModelId} not found in DB", e)
            _uiState.update { it.copy(modelLoadingState = ModelLoadingState.FAILURE) }
            onComplete(ModelLoadingState.FAILURE)
            return
        }

        // Dacă exact același model e deja încărcat cu succes → skip
        if (
            currentLoadedModelPath == model.path &&
            _uiState.value.modelLoadingState == ModelLoadingState.SUCCESS
        ) {
            LOGD("loadModel: model already loaded at ${model.path}, skipping")
            onComplete(ModelLoadingState.SUCCESS)
            return
        }

        // Anulează orice load anterior în curs
        loadModelJob?.cancel()
        LOGD("loadModel: starting load for model '${model.name}' path=${model.path}")

        loadModelJob = viewModelScope.launch {
            // Previne intrarea simultană a două coroutine
            if (!isModelLoading.compareAndSet(false, true)) {
                LOGD("loadModel: already loading, skip duplicate call")
                return@launch
            }

            try {
                // FIX #3 – verificare existență fișier înainte de load
                if (!File(model.path).exists()) {
                    LOGE("loadModel: file not found at ${model.path}", null)
                    _uiState.update { it.copy(modelLoadingState = ModelLoadingState.FAILURE) }
                    onComplete(ModelLoadingState.FAILURE)
                    showModelFileNotFoundDialog()
                    return@launch
                }

                // Unload model anterior + mică pauză de stabilizare
                if (currentLoadedModelPath != null) {
                    LOGD("loadModel: unloading previous model")
                    smolLMManager.unload()
                    currentLoadedModelPath = null
                    delay(300L)
                }

                _uiState.update { it.copy(modelLoadingState = ModelLoadingState.IN_PROGRESS) }

                smolLMManager.load(
                    chat,
                    model.path,
                    SmolLM.InferenceParams(
                        chat.minP,
                        chat.temperature,
                        !chat.isTask,
                        chat.contextSize.toLong(),
                        chat.chatTemplate.takeIf {
                            it.isNotBlank() && ("{%" in it || "{{" in it)
                        },
                        chat.nThreads,
                        chat.useMmap,
                        chat.useMlock,
                    ),
                    onError = { e ->
                        LOGE("loadModel: SmolLMManager.load() failed", e)
                        currentLoadedModelPath = null
                        _uiState.update {
                            it.copy(modelLoadingState = ModelLoadingState.FAILURE)
                        }
                        onComplete(ModelLoadingState.FAILURE)
                        showModelLoadErrorDialog(e)
                    },
                    onSuccess = {
                        LOGD("loadModel: SUCCESS for ${model.path}")
                        currentLoadedModelPath = model.path
                        _uiState.update {
                            it.copy(
                                modelLoadingState = ModelLoadingState.SUCCESS,
                                memoryUsage = if (it.memoryUsage != null) {
                                    getCurrentMemoryUsage()
                                } else null,
                            )
                        }
                        onComplete(ModelLoadingState.SUCCESS)
                    },
                )
            } catch (e: CancellationException) {
                // Normal – job anulat de un load mai nou
                LOGD("loadModel: job cancelled (new load requested)")
            } catch (e: Exception) {
                LOGE("loadModel: unexpected exception", e)
                currentLoadedModelPath = null
                _uiState.update { it.copy(modelLoadingState = ModelLoadingState.FAILURE) }
                onComplete(ModelLoadingState.FAILURE)
            } finally {
                isModelLoading.set(false)
            }
        }
    }

    /**
     * Unload sigur – nu face nimic dacă inferența rulează deja.
     * Resetează currentLoadedModelPath.
     */
    fun unloadModel(): Boolean =
        if (!smolLMManager.isInferenceOn) {
            smolLMManager.unload()
            currentLoadedModelPath = null
            _uiState.update { it.copy(modelLoadingState = ModelLoadingState.NOT_LOADED) }
            LOGD("unloadModel: success")
            true
        } else {
            LOGD("unloadModel: inference running, skipping")
            false
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DIALOG HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun showModelLoadErrorDialog(e: Exception) {
        createAlertDialog(
            dialogTitle = context.getString(R.string.dialog_err_title),
            dialogText = context.getString(R.string.dialog_err_text, e.message),
            dialogPositiveButtonText = context.getString(R.string.dialog_err_change_model),
            onPositiveButtonClick = {
                onEvent(ToggleSelectModelListDialog(visible = true))
            },
            dialogNegativeButtonText = context.getString(R.string.dialog_err_close),
            onNegativeButtonClick = {},
        )
    }

    private fun showModelFileNotFoundDialog() {
        createAlertDialog(
            dialogTitle = context.getString(R.string.dialog_err_title),
            dialogText = "Fișierul modelului nu a fost găsit pe dispozitiv. " +
                "Alege un alt model sau descarcă modelul din nou.",
            dialogPositiveButtonText = context.getString(R.string.dialog_err_change_model),
            onPositiveButtonClick = {
                onEvent(ToggleSelectModelListDialog(visible = true))
            },
            dialogNegativeButtonText = context.getString(R.string.dialog_err_close),
            onNegativeButtonClick = {},
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RAM DETECTION  (FIX #6 – calcul corect)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returnează RAM-ul DISPONIBIL (liber) în GB.
     * Util pentru alegerea automată a modelului.
     */
    fun getAvailableRamGb(): Double {
        val memoryInfo = MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / 1024.0.pow(3.0)
    }

    /**
     * Returnează (ramUtilizat, ramTotal) în GB.
     * FIX #6: usedMemory = totalMem - availMem, nu availMem.
     */
    private fun getCurrentMemoryUsage(): Pair<Float, Float> {
        val memoryInfo = MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = memoryInfo.totalMem / 1024.0.pow(3.0)
        val usedMemory = (memoryInfo.totalMem - memoryInfo.availMem) / 1024.0.pow(3.0)
        return Pair(usedMemory.toFloat(), totalMemory.toFloat())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WEB SEARCH  (FIX #4 + #5 – timeout + fallback robust)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Caută pe DuckDuckGo cu timeout strict de 5 secunde.
     * Dacă API-ul nu răspunde sau returnează date goale → returnează "".
     * Nu blochează niciodată inferența.
     */
    private suspend fun searchWeb(query: String): String {
        // withTimeoutOrNull returnează null dacă expiră → tratăm ca empty
        val result = withTimeoutOrNull(5_000L) {
            withContext(Dispatchers.IO) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                    val url = java.net.URL(
                        "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
                    )
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 4_000

                    val responseText = try {
                        connection.inputStream.bufferedReader().readText()
                    } finally {
                        connection.disconnect()
                    }

                    val json = org.json.JSONObject(responseText)
                    val results = StringBuilder()

                    val abstract = json.optString("AbstractText", "")
                    if (abstract.isNotBlank()) {
                        results.appendLine("• $abstract")
                    }

                    val relatedTopics = json.optJSONArray("RelatedTopics")
                    if (relatedTopics != null) {
                        var count = 0
                        for (i in 0 until relatedTopics.length()) {
                            if (count >= 3) break
                            val topic = relatedTopics.optJSONObject(i) ?: continue
                            val text = topic.optString("Text", "")
                            if (text.isNotBlank()) {
                                results.appendLine("• $text")
                                count++
                            }
                        }
                    }

                    results.toString().trim()
                } catch (e: Exception) {
                    LOGE("searchWeb: failed for query='$query'", e)
                    ""
                }
            }
        }

        if (result == null) {
            LOGD("searchWeb: timeout for query='$query'")
        }
        return result ?: ""
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEND USER QUERY
    // ─────────────────────────────────────────────────────────────────────────

    private fun sendUserQuery(query: String, addMessageToDB: Boolean = true) {
        val chat = uiState.value.chat
        chat.dateUsed = Date()
        appDB.updateChat(chat)

        if (chat.isTask) {
            appDB.deleteMessages(chat.id)
        }

        if (addMessageToDB) {
            appDB.addUserMessage(chat.id, query)
        }

        _uiState.update { it.copy(isGeneratingResponse = true, renderedPartialResponse = null) }

        viewModelScope.launch {
            // Căutare web cu timeout – nu blochează dacă eșuează
            val webContext = searchWeb(query)
            val finalQuery = if (webContext.isNotBlank()) {
                "[Informații recente de pe internet]\n$webContext\n\n[Întrebarea utilizatorului]\n$query"
            } else {
                query
            }

            smolLMManager.getResponse(
                finalQuery,
                responseTransform = { resp ->
                    findThinkTagRegex.replace(resp) { matchResult ->
                        "<blockquote><i><h6>${matchResult.groupValues[1].trim()}</i></h6></blockquote>"
                    }
                },
                onPartialResponseGenerated = { resp ->
                    _uiState.update {
                        it.copy(renderedPartialResponse = mdRenderer.render(resp))
                    }
                },
                onSuccess = { response ->
                    val updatedChat = chat.copy(contextSizeConsumed = response.contextLengthUsed)
                    _uiState.update {
                        it.copy(
                            chat = updatedChat,
                            isGeneratingResponse = false,
                            responseGenerationsSpeed = response.generationSpeed,
                            responseGenerationTimeSecs = response.generationTimeSecs,
                            memoryUsage = if (it.memoryUsage != null) {
                                getCurrentMemoryUsage()
                            } else null,
                        )
                    }
                    appDB.updateChat(updatedChat)
                },
                onCancelled = {
                    _uiState.update { it.copy(isGeneratingResponse = false) }
                },
                onError = { exception ->
                    LOGE("sendUserQuery: getResponse error", exception)
                    _uiState.update { it.copy(isGeneratingResponse = false) }
                    createAlertDialog(
                        dialogTitle = "An error occurred",
                        dialogText = "The app is unable to process the query. " +
                            "Error: ${exception.message}",
                        dialogPositiveButtonText = "Change model",
                        onPositiveButtonClick = {
                            onEvent(ToggleSelectModelListDialog(visible = true))
                        },
                        dialogNegativeButtonText = "Close",
                        onNegativeButtonClick = {},
                    )
                },
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ON EVENT  (structura originală păstrată, cu alias-uri pentru claritate)
    // ─────────────────────────────────────────────────────────────────────────

    // Alias-uri locale pentru a evita referințele lungi
    private fun ToggleSelectModelListDialog(visible: Boolean) =
        ChatScreenUIEvent.ToggleSelectModelListDialog(visible)

    @SuppressLint("StringFormatMatches")
    fun onEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.ToggleSelectModelListDialog ->
                _uiState.update { it.copy(showSelectModelListDialog = event.visible) }

            is ChatScreenUIEvent.ToggleMoreOptionsPopup ->
                _uiState.update { it.copy(showMoreOptionsPopup = event.visible) }

            is ChatScreenUIEvent.ToggleTaskListBottomList ->
                _uiState.update { it.copy(showTasksBottomSheet = event.visible) }

            is ChatScreenUIEvent.ToggleChangeFolderDialog ->
                _uiState.update { it.copy(showChangeFolderDialog = event.visible) }

            ChatScreenUIEvent.ToggleRAMUsageLabel ->
                _uiState.update {
                    it.copy(
                        memoryUsage = if (it.memoryUsage != null) null
                        else getCurrentMemoryUsage()
                    )
                }

            is ChatScreenUIEvent.ShowContextLengthUsageDialog ->
                createAlertDialog(
                    dialogTitle = context.getString(R.string.dialog_ctx_usage_title),
                    dialogText = context.getString(
                        R.string.dialog_ctx_usage_text,
                        event.chat.contextSizeConsumed,
                        event.chat.contextSize,
                    ),
                    dialogPositiveButtonText = context.getString(R.string.dialog_ctx_usage_close),
                    onPositiveButtonClick = {},
                    dialogNegativeButtonText = null,
                    onNegativeButtonClick = null,
                )

            is ChatScreenUIEvent.UpdateChatFolder ->
                appDB.updateChat(_uiState.value.chat.copy(folderId = event.newFolderId))

            is ChatScreenUIEvent.AddFolder ->
                appDB.addFolder(event.folderName)

            is ChatScreenUIEvent.UpdateFolder ->
                appDB.updateFolder(event.folder)

            is ChatScreenUIEvent.DeleteFolder ->
                appDB.deleteFolder(event.folderId)

            is ChatScreenUIEvent.DeleteFolderWithChats ->
                appDB.deleteFolderWithChats(event.folderId)

            is ChatScreenUIEvent.UpdateChatModel -> {
                updateChatLLMParams(event.model.id, event.model.chatTemplate)
                loadModel()
                onEvent(ToggleSelectModelListDialog(visible = false))
            }

            is ChatScreenUIEvent.DeleteModel -> {
                deleteModel(event.model.id)
                Toast.makeText(
                    context,
                    context.getString(R.string.chat_model_deleted, event.model.name),
                    Toast.LENGTH_LONG,
                ).show()
            }

            ChatScreenUIEvent.LoadChatModel -> { /* no-op, handled by loadModel() */ }

            is ChatScreenUIEvent.SendUserQuery ->
                sendUserQuery(event.query)

            ChatScreenUIEvent.StopGeneration ->
                stopGeneration()

            is ChatScreenUIEvent.OnTaskSelected -> {
                val model = modelsRepository.getModelFromId(event.task.modelId)
                val newTask = appDB.addChat(
                    chatName = event.task.name,
                    chatTemplate = model.chatTemplate,
                    systemPrompt = event.task.systemPrompt,
                    llmModelId = event.task.modelId,
                    isTask = true,
                )
                switchChat(newTask)
                onEvent(ChatScreenUIEvent.ToggleTaskListBottomList(visible = false))
            }

            is ChatScreenUIEvent.OnMessageEdited -> {
                deleteMessage(event.oldMessage.id)
                if (!event.lastMessage.isUserMessage) {
                    deleteMessage(event.lastMessage.id)
                }
                appDB.addUserMessage(event.chatId, event.newMessageText)
                // Unload + reload sigur, cu callback după success
                if (unloadModel()) {
                    loadModel(onComplete = { state ->
                        if (state == ModelLoadingState.SUCCESS) {
                            sendUserQuery(event.newMessageText, addMessageToDB = false)
                        }
                    })
                } else {
                    LOGD("OnMessageEdited: could not unload (inference running), skipping reload")
                }
            }

            is ChatScreenUIEvent.OnDeleteChat ->
                createAlertDialog(
                    dialogTitle = context.getString(R.string.dialog_title_delete_chat),
                    dialogText = context.getString(
                        R.string.dialog_text_delete_chat, event.chat.name
                    ),
                    dialogPositiveButtonText = context.getString(R.string.dialog_pos_delete),
                    dialogNegativeButtonText = context.getString(R.string.dialog_neg_cancel),
                    onPositiveButtonClick = {
                        deleteChat(event.chat)
                        Toast.makeText(
                            context,
                            "Chat '${event.chat.name}' deleted",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onNegativeButtonClick = {},
                )

            is ChatScreenUIEvent.OnDeleteChatMessages ->
                createAlertDialog(
                    dialogTitle = context.getString(R.string.chat_options_clear_messages),
                    dialogText = context.getString(R.string.chat_options_clear_messages_text),
                    dialogPositiveButtonText = context.getString(R.string.dialog_pos_clear),
                    dialogNegativeButtonText = context.getString(R.string.dialog_neg_cancel),
                    onPositiveButtonClick = {
                        deleteChatMessages(event.chat)
                        if (unloadModel()) {
                            loadModel(onComplete = { state ->
                                if (state == ModelLoadingState.SUCCESS) {
                                    Toast.makeText(
                                        context,
                                        "Chat '${event.chat.name}' cleared",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            })
                        }
                    },
                    onNegativeButtonClick = {},
                )

            ChatScreenUIEvent.NewChat -> {
                val chatCount = appDB.getChatsCount()
                val newChat = appDB.addChat(chatName = "Untitled ${chatCount + 1}")
                switchChat(newChat)
            }

            is ChatScreenUIEvent.SwitchChat ->
                switchChat(event.chat)

            is ChatScreenUIEvent.UpdateChatSettings -> {
                val newChat = event.settings.toChat(event.existingChat)
                _uiState.update { it.copy(chat = newChat) }
                appDB.updateChat(newChat)
                // Reîncarcă modelul NUMAI dacă parametrii de model s-au schimbat
                // (unload + load sigur)
                if (unloadModel()) {
                    loadModel()
                } else {
                    LOGD("UpdateChatSettings: inference running, settings will apply on next load")
                }
            }

            is ChatScreenUIEvent.StartBenchmark ->
                smolLMManager.benchmark { result -> event.onResult(result) }

            is ChatScreenUIEvent.StartAudioTranscription -> {
                _uiState.update {
                    it.copy(
                        audioTranscriptionUIState = AudioTranscriptionUIState(
                            isRecording = true,
                            isAvailable = true,
                        )
                    )
                }
                val asrModelName = sharedPrefStore.get(
                    SETTING_KEY_SPEECH2TEXT_CURR_MODEL_NAME,
                    SETTING_DEF_VALUE_SPEECH2TEXT_CURR_MODEL_NAME,
                )
                val asrModel = availableASRModels.first { it.name == asrModelName }
                val error = audioTranscriptionService.startTranscription(asrModel) { transcription ->
                    _uiState.update {
                        it.copy(
                            audioTranscriptionUIState = AudioTranscriptionUIState(
                                isRecording = false,
                                isAvailable = true,
                            )
                        )
                    }
                    event.onLineComplete(transcription)
                }
                if (error is AudioTranscriptionService.Error.AudioRecordingPermissionNotGranted) {
                    LOGE("StartAudioTranscription: permission not granted", null)
                    _uiState.update {
                        it.copy(
                            audioTranscriptionUIState = AudioTranscriptionUIState(
                                isRecording = false,
                                isAvailable = true,
                            )
                        )
                    }
                }
            }

            is ChatScreenUIEvent.StopAudioTranscription -> {
                _uiState.update {
                    it.copy(
                        audioTranscriptionUIState = AudioTranscriptionUIState(
                            isRecording = false,
                            isAvailable = true,
                        )
                    )
                }
                audioTranscriptionService.stopTranscription()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun initializeUIState(): ChatScreenUIState {
        val defaultChat = appDB.loadDefaultChat()
        val isSpeech2TextEnabled = sharedPrefStore.get(
            SETTING_KEY_SPEECH2TEXT_ENABLED,
            SETTING_DEF_VALUE_SPEECH2_TEXT_ENABLED,
        )
        return ChatScreenUIState(
            chat = defaultChat,
            audioTranscriptionUIState = AudioTranscriptionUIState(
                isAvailable = isSpeech2TextEnabled,
            ),
        )
    }

    private fun setupCollectors() {
        viewModelScope.launch {
            launch {
                appDB.getChats().collect { chats ->
                    _uiState.update { it.copy(chats = chats.toImmutableList()) }
                }
            }
            launch {
                appDB.getFolders().collect { folders ->
                    _uiState.update { it.copy(folders = folders.toImmutableList()) }
                }
            }
            launch {
                appDB.getTasks().collect { tasks ->
                    _uiState.update {
                        it.copy(
                            tasks = tasks.map { task ->
                                task.copy(
                                    modelName = modelsRepository.getModelFromId(task.modelId).name
                                )
                            }.toImmutableList()
                        )
                    }
                }
            }
            launch {
                appDB.getModels().collect { models ->
                    _uiState.update { it.copy(models = models.toImmutableList()) }
                }
            }
            launch {
                _uiState
                    .map { it.chat }
                    .distinctUntilChanged()
                    .collectLatest { chat ->
                        appDB.getMessages(chat.id).collect { chatMessages ->
                            _uiState.update {
                                it.copy(
                                    messages = chatMessages.map { chatMessage ->
                                        chatMessage.renderedMessage =
                                            mdRenderer.render(chatMessage.message)
                                        chatMessage
                                    }.toImmutableList()
                                )
                            }
                        }
                    }
            }
            launch {
                _uiState
                    .map { it.chat }
                    .distinctUntilChanged()
                    .collectLatest { chat ->
                        _uiState.update { uiState ->
                            uiState.copy(
                                chat = uiState.chat.copy(
                                    llmModel = modelsRepository.getModelFromId(
                                        uiState.chat.llmModelId
                                    )
                                )
                            )
                        }
                    }
            }
            launch {
                sharedPrefStore.sharedPrefStoreChanges.collect { prefKey ->
                    when (prefKey) {
                        SETTING_KEY_SPEECH2TEXT_ENABLED -> {
                            audioTranscriptionService.stopTranscription()
                            val enabled = sharedPrefStore.get(
                                SETTING_KEY_SPEECH2TEXT_ENABLED,
                                SETTING_DEF_VALUE_SPEECH2_TEXT_ENABLED,
                            )
                            _uiState.update {
                                it.copy(
                                    audioTranscriptionUIState = AudioTranscriptionUIState(
                                        isAvailable = enabled,
                                        isRecording = false,
                                    )
                                )
                            }
                        }
                        SETTING_KEY_SPEECH2TEXT_CURR_MODEL_NAME -> {
                            audioTranscriptionService.stopTranscription()
                            _uiState.update {
                                it.copy(
                                    audioTranscriptionUIState = AudioTranscriptionUIState(
                                        isAvailable = true,
                                        isRecording = false,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateChatLLMParams(modelId: Long, chatTemplate: String) {
        val newChat = _uiState.value.chat.copy(llmModelId = modelId, chatTemplate = chatTemplate)
        _uiState.update { it.copy(chat = newChat) }
        appDB.updateChat(newChat)
    }

    private fun deleteMessage(messageId: Long) {
        appDB.deleteMessage(messageId)
    }

    private fun stopGeneration() {
        smolLMManager.stopResponseGeneration()
        _uiState.update { it.copy(isGeneratingResponse = false, renderedPartialResponse = null) }
    }

    private fun switchChat(chat: Chat) {
        stopGeneration()
        _uiState.update { it.copy(chat = chat) }
        loadModel()
    }

    private fun deleteChat(chat: Chat) {
        stopGeneration()
        appDB.deleteChat(chat)
        appDB.deleteMessages(chat.id)
        switchChat(appDB.loadDefaultChat())
    }

    private fun deleteChatMessages(chat: Chat) {
        stopGeneration()
        appDB.deleteMessages(chat.id)
    }

    private fun deleteModel(modelId: Long) {
        modelsRepository.deleteModel(modelId)
        val newChat = _uiState.value.chat.copy(llmModelId = -1)
        _uiState.update { it.copy(chat = newChat) }
        currentLoadedModelPath = null
    }
}