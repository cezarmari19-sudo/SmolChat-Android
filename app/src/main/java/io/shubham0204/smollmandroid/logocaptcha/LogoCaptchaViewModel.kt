package io.shubham0204.smollmandroid.logocaptcha

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

enum class CaptchaPhase {
    SET_ORDER,
    VERIFY,
    LOCKED,
    SUCCESS,
    ADMIN_SETUP
}

data class CaptchaUIState(
    val phase: CaptchaPhase = CaptchaPhase.SET_ORDER,
    val imagePaths: List<String> = emptyList(),
    val currentOrder: List<Int> = emptyList(),
    val attempts: Int = 0,
    val lockedUntilMs: Long = 0L,
    val showAdminCodeDialog: Boolean = false,
    val adminCodeError: Boolean = false,
)

class LogoCaptchaViewModel : ViewModel() {

    private val _state = MutableStateFlow(CaptchaUIState())
    val state: StateFlow<CaptchaUIState> = _state

    fun init(ctx: Context) {
        val paths = LogoCaptchaData.getImagePaths(ctx)
        val order = LogoCaptchaData.getCorrectOrder(ctx)
        val attempts = LogoCaptchaData.getAttempts(ctx)
        val lockedUntil = LogoCaptchaData.getLockedUntil(ctx)
        val lastVerified = LogoCaptchaData.getLastVerified(ctx)
        val now = System.currentTimeMillis()

        val phase = when {
            lockedUntil > now -> CaptchaPhase.LOCKED
            paths.isEmpty() -> CaptchaPhase.SET_ORDER
            order.isEmpty() -> CaptchaPhase.SET_ORDER
            now - lastVerified < 24 * 60 * 60 * 1000L -> CaptchaPhase.SUCCESS
            else -> CaptchaPhase.VERIFY
        }

        val initOrder = if (paths.isNotEmpty())
            paths.indices.toList().shuffled()
        else emptyList()

        _state.update {
            it.copy(
                phase = phase,
                imagePaths = paths,
                currentOrder = if (phase == CaptchaPhase.VERIFY) initOrder else paths.indices.toList(),
                attempts = attempts,
                lockedUntilMs = lockedUntil
            )
        }
    }

    fun onAdminButtonClick() {
        _state.update { it.copy(showAdminCodeDialog = true, adminCodeError = false) }
    }

    fun dismissAdminDialog() {
        _state.update { it.copy(showAdminCodeDialog = false, adminCodeError = false) }
    }

    fun submitAdminCode(code: String) {
        if (code == "Gduebdyueb") {
            _state.update { it.copy(showAdminCodeDialog = false, phase = CaptchaPhase.ADMIN_SETUP) }
        } else {
            _state.update { it.copy(adminCodeError = true) }
        }
    }

    fun adminSaveImages(ctx: Context, uris: List<Uri>) {
        val paths = uris.take(6).map { uri ->
            val dest = File(
                ctx.filesDir,
                "logo_${System.currentTimeMillis()}_${uri.lastPathSegment?.takeLast(10)}.jpg"
            )
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        }
        LogoCaptchaData.saveImagePaths(ctx, paths)
        _state.update {
            it.copy(
                imagePaths = paths,
                currentOrder = paths.indices.toList(),
                phase = CaptchaPhase.SET_ORDER
            )
        }
    }

    fun swapTiles(from: Int, to: Int) {
        val newOrder = _state.value.currentOrder.toMutableList()
        val tmp = newOrder[from]
        newOrder[from] = newOrder[to]
        newOrder[to] = tmp
        _state.update { it.copy(currentOrder = newOrder) }
    }

    fun saveOrder(ctx: Context) {
        val order = _state.value.currentOrder
        LogoCaptchaData.saveCorrectOrder(ctx, order)
        LogoCaptchaData.saveLastVerified(ctx)
        _state.update { it.copy(phase = CaptchaPhase.SUCCESS) }
    }

    fun verify(ctx: Context): Boolean {
        val correct = LogoCaptchaData.getCorrectOrder(ctx)
        val current = _state.value.currentOrder
        val isOk = correct == current

        if (isOk) {
            LogoCaptchaData.saveLastVerified(ctx)
            LogoCaptchaData.saveAttempts(ctx, 0)
            _state.update { it.copy(phase = CaptchaPhase.SUCCESS, attempts = 0) }
        } else {
            val newAttempts = _state.value.attempts + 1
            LogoCaptchaData.saveAttempts(ctx, newAttempts)
            if (newAttempts >= 5) {
                val until = System.currentTimeMillis() + 60 * 60 * 1000L
                LogoCaptchaData.setLockedUntil(ctx, until)
                LogoCaptchaData.saveAttempts(ctx, 0)
                _state.update {
                    it.copy(
                        phase = CaptchaPhase.LOCKED,
                        lockedUntilMs = until,
                        attempts = 0
                    )
                }
            } else {
                val shuffled = _state.value.imagePaths.indices.toList().shuffled()
                _state.update { it.copy(attempts = newAttempts, currentOrder = shuffled) }
            }
        }
        return isOk
    }

    fun unlockIfReady(ctx: Context) {
        if (System.currentTimeMillis() > _state.value.lockedUntilMs) {
            LogoCaptchaData.setLockedUntil(ctx, 0L)
            val shuffled = _state.value.imagePaths.indices.toList().shuffled()
            _state.update { it.copy(phase = CaptchaPhase.VERIFY, currentOrder = shuffled) }
        }
    }
}