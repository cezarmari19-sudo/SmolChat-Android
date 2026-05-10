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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.util.Date
import kotlin.math.pow

private const val LOGTAG = "[SmolLMAndroid-Kt]"
private val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

private val findThinkHtmlBlockRegex = Regex("<blockquote><i><h6>[\\s\\S]*?</i></h6></blockquote>")
internal fun String.stripThinkingForClipboard() = findThinkHtmlBlockRegex.replace(this, "").trim()

sealed class ChatScreenUIEvent {
    sealed class ChatEvents {
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

        data class StartAudioTranscription(val onLineComplete: (String) -> Unit) :
            ChatScreenUIEvent()

        data object StopAudioTranscription : ChatScreenUIEvent()
    }

    sealed class FolderEvents {
        data class UpdateChatFolder(val newFolderId: Long) : ChatScreenUIEvent()

        data class AddFolder(val folderName: String) : ChatScreenUIEvent()

        data class UpdateFolder(val folder: Folder) : ChatScreenUIEvent()

        data class DeleteFolder(val folderId: Long) : ChatScreenUIEvent()

        data class DeleteFolderWithChats(val folderId: Long) : ChatScreenUIEvent()
    }

    sealed class DialogEvents {
        data class ToggleChangeFolderDialog(val visible: Boolean) : ChatScreenUIEvent()

        data class ToggleSelectModelListDialog(val visible: Boolean) : ChatScreenUIEvent()

        data class ToggleMoreOptionsPopup(val visible: Boolean) : ChatScreenUIEvent()

        data class ToggleTaskListBottomList(val visible: Boolean) : ChatScreenUIEvent()

        data object ToggleRAMUsageLabel : ChatScreenUIEvent()

        data class ShowContextLengthUsageDialog(val chat: Chat) : ChatScreenUIEvent()
    }
}

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

@KoinViewModel
class ChatScreenViewModel(
    val context: Context,
    val appDB: AppDB,
    val modelsRepository: ModelsRepository,
    val smolLMManager: SmolLMManager,
    val audioTranscriptionService: AudioTranscriptionService,
    val mdRenderer: MDRenderer,
    val sharedPrefStore: SharedPrefStore
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
    private var activityManager: ActivityManager

    init {
        setupCollectors()
        loadModel()
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    fun loadModel(onComplete: (ModelLoadingState) -> Unit = {}) {
        val chat = _uiState.value.chat
        val model = modelsRepository.getModelFromId(chat.llmModelId)
        if (chat.llmModelId == -1L) {
            _uiState.update { it.copy(showSelectModelListDialog = true) }
        } else {
            _uiState.update { it.copy(modelLoadingState = ModelLoadingState.IN_PROGRESS) }
            smolLMManager.load(
                chat,
                model.path,
                SmolLM.InferenceParams(
                    chat.minP,
                    chat.temperature,
                    !chat.isTask,
                    chat.contextSize.toLong(),
                    chat.chatTemplate.takeIf { it.isNotBlank() && ("{%" in it || "{{" in it) },
                    chat.nThreads,
                    chat.useMmap,
                    chat.useMlock,
                ),
                onError = { e ->
                    _uiState.update { it.copy(modelLoadingState = ModelLoadingState.FAILURE) }
                    onComplete(ModelLoadingState.FAILURE)
                    createAlertDialog(
                        dialogTitle = context.getString(R.string.dialog_err_title),
                        dialogText = context.getString(R.string.dialog_err_text, e.message),
                        dialogPositiveButtonText =
                            context.getString(R.string.dialog_err_change_model),
                        onPositiveButtonClick = {
                            onEvent(
                                ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(
                                    visible = true
                                )
                            )
                        },
                        dialogNegativeButtonText = context.getString(R.string.dialog_err_close),
                        onNegativeButtonClick = {},
                    )
                },
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            modelLoadingState = ModelLoadingState.SUCCESS,
                            memoryUsage =
                                if (it.memoryUsage != null) {
                                    getCurrentMemoryUsage()
                                } else {
                                    null
                                },
                        )
                    }
                    onComplete(ModelLoadingState.SUCCESS)
                },
            )
        }
    }

    fun unloadModel(): Boolean =
        if (!smolLMManager.isInferenceOn) {
            smolLMManager.unload()
            _uiState.update { it.copy(modelLoadingState = ModelLoadingState.NOT_LOADED) }
            true
        } else {
            false
        }

    @SuppressLint("StringFormatMatches")
    fun onEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog -> {
                _uiState.update { it.copy(showSelectModelListDialog = event.visible) }
            }

            is ChatScreenUIEvent.DialogEvents.ToggleMoreOptionsPopup -> {
                _uiState.update { it.copy(showMoreOptionsPopup = event.visible) }
            }

            is ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList -> {
                _uiState.update { it.copy(showTasksBottomSheet = event.visible) }
            }

            is ChatScreenUIEvent.DialogEvents.ToggleChangeFolderDialog -> {
                _uiState.update { it.copy(showChangeFolderDialog = event.visible) }
            }

            ChatScreenUIEvent.DialogEvents.ToggleRAMUsageLabel -> {
                _uiState.update {
                    it.copy(
                        memoryUsage =
                            if (it.memoryUsage != null) {
                                null
                            } else {
                                getCurrentMemoryUsage()
                            }
                    )
                }
            }

            is ChatScreenUIEvent.DialogEvents.ShowContextLengthUsageDialog -> {
                createAlertDialog(
                    dialogTitle = context.getString(R.string.dialog_ctx_usage_title),
                    dialogText =
                        context.getString(
                            R.string.dialog_ctx_usage_text,
                            event.chat.contextSizeConsumed,
                            event.chat.contextSize,
                        ),
                    dialogPositiveButtonText = context.getString(R.string.dialog_ctx_usage_close),
                    onPositiveButtonClick = {},
                    dialogNegativeButtonText = null,
                    onNegativeButtonClick = null,
                )
            }

            is ChatScreenUIEvent.FolderEvents.UpdateChatFolder -> {
                appDB.updateChat(_uiState.value.chat.copy(folderId = event.newFolderId))
            }

            is ChatScreenUIEvent.FolderEvents.AddFolder -> {
                appDB.addFolder(event.folderName)
            }

            is ChatScreenUIEvent.FolderEvents.UpdateFolder -> {
                appDB.updateFolder(event.folder)
            }

            is ChatScreenUIEvent.FolderEvents.DeleteFolder -> {
                appDB.deleteFolder(event.folderId)
            }

            is ChatScreenUIEvent.FolderEvents.DeleteFolderWithChats -> {
                appDB.deleteFolderWithChats(event.folderId)
            }

            is ChatScreenUIEvent.ChatEvents.UpdateChatModel -> {
                updateChatLLMParams(event.model.id, event.model.chatTemplate)
                loadModel()
                onEvent(ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(visible = false))
            }

            is ChatScreenUIEvent.ChatEvents.DeleteModel -> {
                deleteModel(event.model.id)
                Toast.makeText(
                    context,
                    context.getString(R.string.chat_model_deleted, event.model.name),
                    Toast.LENGTH_LONG,
                ).show()
            }

            ChatScreenUIEvent.ChatEvents.LoadChatModel -> {}

            is ChatScreenUIEvent.ChatEvents.SendUserQuery -> {
                sendUserQuery(event.query)
            }

            ChatScreenUIEvent.ChatEvents.StopGeneration -> {
                stopGeneration()
            }

            is ChatScreenUIEvent.ChatEvents.OnTaskSelected -> {
                modelsRepository.getModelFromId(event.task.modelId).let { model ->
                    val newTask =
                        appDB.addChat(
                            chatName = event.task.name,
                            chatTemplate = model.chatTemplate,
                            systemPrompt = event.task.systemPrompt,
                            llmModelId = event.task.modelId,
                            isTask = true,
                        )
                    switchChat(newTask)
                    onEvent(
                        ChatScreenUIEvent.DialogEvents.ToggleTaskListBottomList(visible = false)
                    )
                }
            }

            is ChatScreenUIEvent.ChatEvents.OnMessageEdited -> {
                deleteMessage(event.oldMessage.id)
                if (!event.lastMessage.isUserMessage) {
                    deleteMessage(event.lastMessage.id)
                }
                appDB.addUserMessage(event.chatId, event.newMessageText)
                unloadModel()
                loadModel(
                    onComplete = {
                        if (it == ModelLoadingState.SUCCESS) {
                            sendUserQuery(event.newMessageText, addMessageToDB = false)
                        }
                    }
                )
            }

            is ChatScreenUIEvent.ChatEvents.OnDeleteChat -> {
                createAlertDialog(
                    dialogTitle = context.getString(R.string.dialog_title_delete_chat),
                    dialogText =
                        context.getString(R.string.dialog_text_delete_chat, event.chat.name),
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
            }

            is ChatScreenUIEvent.ChatEvents.OnDeleteChatMessages -> {
                createAlertDialog(
                    dialogTitle = context.getString(R.string.chat_options_clear_messages),
                    dialogText = context.getString(R.string.chat_options_clear_messages_text),
                    dialogPositiveButtonText = context.getString(R.string.dialog_pos_clear),
                    dialogNegativeButtonText = context.getString(R.string.dialog_neg_cancel),
                    onPositiveButtonClick = {
                        deleteChatMessages(event.chat)
                        unloadModel()
                        loadModel(
                            onComplete = {
                                if (it == ModelLoadingState.SUCCESS) {
                                    Toast.makeText(
                                        context,
                                        "Chat '${event.chat.name}' cleared",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        )
                    },
                    onNegativeButtonClick = {},
                )
            }

            ChatScreenUIEvent.ChatEvents.NewChat -> {
                val chatCount = appDB.getChatsCount()
                val newChat = appDB.addChat(chatName = "Untitled ${chatCount + 1}")
                switchChat(newChat)
            }

            is ChatScreenUIEvent.ChatEvents.SwitchChat -> {
                switchChat(event.chat)
            }

            is ChatScreenUIEvent.ChatEvents.UpdateChatSettings -> {
                val newChat = event.settings.toChat(event.existingChat)
                _uiState.update { it.copy(chat = newChat) }
                appDB.updateChat(newChat)
                unloadModel()
                loadModel()
            }

            is ChatScreenUIEvent.ChatEvents.StartBenchmark -> {
                smolLMManager.benchmark { result -> event.onResult(result) }
            }

            is ChatScreenUIEvent.ChatEvents.StartAudioTranscription -> {
                _uiState.update {
                    it.copy(
                        audioTranscriptionUIState = AudioTranscriptionUIState(
                            true,
                            isAvailable = true
                        )
                    )
                }
                val asrModelName = sharedPrefStore.get(
                    SETTING_KEY_SPEECH2TEXT_CURR_MODEL_NAME,
                    SETTING_DEF_VALUE_SPEECH2TEXT_CURR_MODEL_NAME
                )
                val asrModel = availableASRModels.first { it.name == asrModelName }
                val error =
                    audioTranscriptionService.startTranscription(asrModel) { transcription ->
                        _uiState.update {
                            it.copy(
                                audioTranscriptionUIState = AudioTranscriptionUIState(
                                    false,
                                    isAvailable = true
                                )
                            )
                        }
                        event.onLineComplete(transcription)
                    }
                if (error is AudioTranscriptionService.Error.AudioRecordingPermissionNotGranted) {
                }
            }

            is ChatScreenUIEvent.ChatEvents.StopAudioTranscription -> {
                _uiState.update {
                    it.copy(
                        audioTranscriptionUIState = AudioTranscriptionUIState(
                            false,
                            isAvailable = true
                        )
                    )
                }
                audioTranscriptionService.stopTranscription()
            }
        }
    }

    private fun initializeUIState(): ChatScreenUIState {
        val defaultChat = appDB.loadDefaultChat()
        val isSpeech2TextEnabled = sharedPrefStore.get(
            SETTING_KEY_SPEECH2TEXT_ENABLED,
            SETTING_DEF_VALUE_SPEECH2_TEXT_ENABLED
        )
        val audioTranscriptionUIState = AudioTranscriptionUIState(
            isAvailable = isSpeech2TextEnabled
        )
        return ChatScreenUIState(
            chat = defaultChat,
            audioTranscriptionUIState = audioTranscriptionUIState
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
                                    llmModel = modelsRepository.getModelFromId(uiState.chat.llmModelId)
                                )
                            )
                        }
                    }
            }
            launch {
                sharedPrefStore.sharedPrefStoreChanges.collect { prefKey ->
                    if (prefKey == SETTING_KEY_SPEECH2TEXT_ENABLED) {
                        audioTranscriptionService.stopTranscription()
                        val isSpeech2TextEnabled = sharedPrefStore.get(
                            SETTING_KEY_SPEECH2TEXT_ENABLED,
                            SETTING_DEF_VALUE_SPEECH2_TEXT_ENABLED
                        )
                        _uiState.update {
                            it.copy(
                                audioTranscriptionUIState = AudioTranscriptionUIState(
                                    isAvailable = isSpeech2TextEnabled,
                                    isRecording = false
                                )
                            )
                        }
                    } else if (prefKey == SETTING_KEY_SPEECH2TEXT_CURR_MODEL_NAME) {
                        audioTranscriptionService.stopTranscription()
                        _uiState.update {
                            it.copy(
                                audioTranscriptionUIState = AudioTranscriptionUIState(
                                    isAvailable = true,
                                    isRecording = false
                                )
                            )
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

    private suspend fun searchWeb(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = java.net.URL(
                    "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
                )
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    LOGD("Web search HTTP error: $responseCode")
                    connection.disconnect()
                    return@withContext ""
                }

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = org.json.JSONObject(response)
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
                LOGD("Web search failed: ${e.message}")
                ""
            }
        }
    }

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
            // TEST: web search dezactivat temporar
            // Dacă loop-ul dispare cu această linie, problema e în searchWeb()
            val webContext = ""

            val finalQuery = if (webContext.isNotBlank()) {
                "[Informații recente de pe internet]\n$webContext\n\n[Întrebarea utilizatorului]\n$query"
            } else {
                query
            }

            LOGD("Starting inference. Prompt length: ${finalQuery.length}")

            smolLMManager.getResponse(
                finalQuery,
                responseTransform = {
                    findThinkTagRegex.replace(it) { matchResult ->
                        "<blockquote><i><h6>${matchResult.groupValues[1].trim()}</i></h6></blockquote>"
                    }
                },
                onPartialResponseGenerated = { resp ->
                    _uiState.update { it.copy(renderedPartialResponse = mdRenderer.render(resp)) }
                },
                onSuccess = { response ->
                    LOGD("Inference success")
                    val updatedChat = chat.copy(contextSizeConsumed = response.contextLengthUsed)
                    _uiState.update {
                        it.copy(
                            chat = updatedChat,
                            isGeneratingResponse = false,
                            renderedPartialResponse = null,
                            responseGenerationsSpeed = response.generationSpeed,
                            responseGenerationTimeSecs = response.generationTimeSecs,
                            memoryUsage = if (it.memoryUsage != null) getCurrentMemoryUsage() else null,
                        )
                    }
                    appDB.updateChat(updatedChat)
                },
                onCancelled = {
                    LOGD("Inference cancelled")
                    _uiState.update {
                        it.copy(
                            isGeneratingResponse = false,
                            renderedPartialResponse = null
                        )
                    }
                },
                onError = { exception ->
                    LOGD("Inference error: ${exception.message}")
                    _uiState.update {
                        it.copy(
                            isGeneratingResponse = false,
                            renderedPartialResponse = null
                        )
                    }
                    createAlertDialog(
                        dialogTitle = "An error occurred",
                        dialogText = "The app is unable to process the query. The error message is: ${exception.message}",
                        dialogPositiveButtonText = "Change model",
                        onPositiveButtonClick = {
                            onEvent(
                                ChatScreenUIEvent.DialogEvents.ToggleSelectModelListDialog(
                                    visible = true
                                )
                            )
                        },
                        dialogNegativeButtonText = "Close",
                        onNegativeButtonClick = {},
                    )
                },
            )
        }
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
    }

    private fun getCurrentMemoryUsage(): Pair<Float, Float> {
        val memoryInfo = MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = memoryInfo.totalMem / 1024.0.pow(3.0)
        val usedMemory = (memoryInfo.totalMem - memoryInfo.availMem) / 1024.0.pow(3.0)
        return Pair(usedMemory.toFloat(), totalMemory.toFloat())
    }
}