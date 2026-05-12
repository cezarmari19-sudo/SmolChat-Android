package io.shubham0204.smollmandroid.logocaptcha

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun LogoCaptchaScreen(
    onSuccess: () -> Unit,
    viewModel: LogoCaptchaViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.init(ctx) }

    // FIX: LaunchedEffect separat pentru LOCKED ca să nu pornească două coroutine
    LaunchedEffect(state.phase) {
        if (state.phase == CaptchaPhase.SUCCESS) {
            onSuccess()
        }
    }

    // FIX: coroutine separată doar pentru timer LOCKED
    LaunchedEffect(state.phase) {
        if (state.phase == CaptchaPhase.LOCKED) {
            while (state.phase == CaptchaPhase.LOCKED) {
                delay(1000)
                viewModel.unlockIfReady(ctx)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // FIX: eliminat CaptchaPhase.CAPTCHA care nu există în enum
            when (state.phase) {
                CaptchaPhase.ADMIN_SETUP -> AdminSetupPhase(
                    onImagesPicked = { uris -> viewModel.adminSaveImages(ctx, uris) }
                )
                CaptchaPhase.SET_ORDER -> SetOrderPhase(
                    imagePaths = state.imagePaths,
                    currentOrder = state.currentOrder,
                    onSwap = { a, b -> viewModel.swapTiles(a, b) },
                    onSave = { viewModel.saveOrder(ctx) }
                )
                CaptchaPhase.VERIFY -> VerifyPhase(
                    imagePaths = state.imagePaths,
                    currentOrder = state.currentOrder,
                    attempts = state.attempts,
                    onSwap = { a, b -> viewModel.swapTiles(a, b) },
                    onVerify = { viewModel.verify(ctx) }
                )
                CaptchaPhase.LOCKED -> LockedPhase(lockedUntilMs = state.lockedUntilMs)
                CaptchaPhase.SUCCESS -> {
                    // handled în LaunchedEffect
                }
            }
        }

        // Buton admin ASCUNS — colț dreapta jos, fără text
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Transparent)
        ) {
            TextButton(
                onClick = { viewModel.onAdminButtonClick() },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("", fontSize = 1.sp)
            }
        }
    }

    if (state.showAdminCodeDialog) {
        AdminCodeDialog(
            hasError = state.adminCodeError,
            onConfirm = { code -> viewModel.submitAdminCode(code) },
            onDismiss = { viewModel.dismissAdminDialog() }
        )
    }
}

@Composable
fun AdminCodeDialog(
    hasError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cod admin", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Introdu codul") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = hasError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF7C5CFC),
                        focusedLabelColor = Color(0xFF7C5CFC),
                    )
                )
                if (hasError) {
                    Spacer(Modifier.height(4.dp))
                    Text("Cod incorect", color = Color(0xFFFC5C5C), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
            ) { Text("Intră") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anulează", color = Color(0xFF6B6B80)) }
        },
        containerColor = Color(0xFF13131A)
    )
}

@Composable
fun AdminSetupPhase(onImagesPicked: (List<Uri>) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) onImagesPicked(uris.take(6)) }

    Text("Panel Admin", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text("Selectează 6 logo-uri de pe telefon.", color = Color(0xFFAAAAAA), textAlign = TextAlign.Center)
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = { launcher.launch("image/*") },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
    ) {
        Text("📁  Alege 6 imagini", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SetOrderPhase(
    imagePaths: List<String>,
    currentOrder: List<Int>,
    onSwap: (Int, Int) -> Unit,
    onSave: () -> Unit
) {
    Text("Ziua 1", fontSize = 13.sp, color = Color(0xFF7C5CFC), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Alege ordinea ta", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text(
        "Trage logo-urile în ordinea pe care o vei memora.",
        color = Color(0xFFAAAAAA), fontSize = 14.sp, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))
    DraggableLogoGrid(imagePaths = imagePaths, currentOrder = currentOrder, onSwap = onSwap)
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
    ) {
        Text("💾  Salvează ordinea mea", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VerifyPhase(
    imagePaths: List<String>,
    currentOrder: List<Int>,
    attempts: Int,
    onSwap: (Int, Int) -> Unit,
    onVerify: () -> Boolean
) {
    // FIX: message ținut în rememberSaveable ca să supraviețuiască recompoziției
    var message by rememberSaveable { mutableStateOf("") }
    var isError by rememberSaveable { mutableStateOf(false) }

    Text("Verificare zilnică", fontSize = 13.sp, color = Color(0xFF7C5CFC), fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Reașează logo-urile", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text("Pune-le în ordinea originală.", color = Color(0xFFAAAAAA), fontSize = 14.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (i < attempts) Color(0xFFFC5C5C) else Color(0xFF22222E),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    if (message.isNotEmpty()) {
        Text(message, color = if (isError) Color(0xFFFC5C5C) else Color(0xFF5CFCA0), fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
    }

    DraggableLogoGrid(imagePaths = imagePaths, currentOrder = currentOrder, onSwap = onSwap)
    Spacer(Modifier.height(24.dp))

    Button(
        onClick = {
            val ok = onVerify()
            message = if (ok) "✅ Corect!" else "❌ Greșit, încearcă din nou."
            isError = !ok
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC))
    ) {
        Text("✓  Verifică ordinea", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LockedPhase(lockedUntilMs: Long) {
    var remaining by remember { mutableStateOf(0L) }
    LaunchedEffect(lockedUntilMs) {
        while (true) {
            remaining = maxOf(0L, lockedUntilMs - System.currentTimeMillis())
            if (remaining == 0L) break
            delay(1000)
        }
    }
    val minutes = remaining / 60000
    val seconds = (remaining % 60000) / 1000

    Text("🔒 Blocat", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFC5C5C))
    Spacer(Modifier.height(16.dp))
    Text("Ai greșit de 5 ori.", color = Color(0xFFAAAAAA), fontSize = 15.sp)
    Spacer(Modifier.height(24.dp))
    Text("%02d:%02d".format(minutes, seconds), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFC5C5C))
    Spacer(Modifier.height(8.dp))
    Text("minute rămase", color = Color(0xFF6B6B80), fontSize = 13.sp)
}

@Composable
fun DraggableLogoGrid(
    imagePaths: List<String>,
    currentOrder: List<Int>,
    onSwap: (Int, Int) -> Unit
) {
    // FIX: salvăm și dimensiunea tilei, nu doar poziția
    val tilePositions = remember { mutableStateMapOf<Int, Triple<Float, Float, Float>>() }
    var draggingIdx by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until 3) {
                    val pos = row * 3 + col
                    if (pos >= currentOrder.size) break
                    val imgIdx = currentOrder[pos]
                    val path = imagePaths.getOrNull(imgIdx) ?: ""

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .onGloballyPositioned { coords ->
                                val p = coords.positionInWindow()
                                // FIX: salvăm x, y și size reală a tilei
                                val size = coords.size.width.toFloat()
                                tilePositions[pos] = Triple(p.x, p.y, size)
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                2.dp,
                                if (draggingIdx == pos) Color(0xFF7C5CFC) else Color(0xFF22222E),
                                RoundedCornerShape(14.dp)
                            )
                            .background(Color(0xFF1A1A24))
                            .pointerInput(currentOrder) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingIdx = pos },
                                    onDragEnd = { draggingIdx = null },
                                    onDragCancel = { draggingIdx = null },
                                    onDrag = { change, _ ->
                                        val x = change.position.x + (tilePositions[pos]?.first ?: 0f)
                                        val y = change.position.y + (tilePositions[pos]?.second ?: 0f)
                                        // FIX: folosim size reală în loc de 300px hardcodat
                                        val target = tilePositions.entries.firstOrNull { (idx, t) ->
                                            idx != pos &&
                                            x > t.first && x < t.first + t.third &&
                                            y > t.second && y < t.second + t.third
                                        }?.key
                                        if (target != null) {
                                            onSwap(pos, target)
                                            draggingIdx = target
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (path.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(File(path)),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("🖼️", fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }
}