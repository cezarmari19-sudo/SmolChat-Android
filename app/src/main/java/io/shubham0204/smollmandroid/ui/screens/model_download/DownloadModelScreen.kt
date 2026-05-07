package io.shubham0204.smollmandroid.ui.screens.model_download

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import io.shubham0204.smollmandroid.ui.components.AppSpacer4W

@Preview
@Composable
private fun PreviewDownloadModelScreen() {
    DownloadModelScreen(
        onDownloadModelClick = {},
        onNextSectionClick = {},
        onHFModelSelectClick = {},
    )
}

@Composable
fun DownloadModelScreen(
    onHFModelSelectClick: () -> Unit,
    onNextSectionClick: () -> Unit,
    onDownloadModelClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recommendedIndex = remember { getRecommendedModelIndex(context) }
    var selectedPopularModelIndex by rememberSaveable { mutableStateOf<Int?>(recommendedIndex) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Alege modelul AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Modelul recomandat pentru telefonul tău e selectat automat ⭐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PopularModelsList(
            selectedModelIndex = selectedPopularModelIndex,
            onModelSelected = { selectedPopularModelIndex = it },
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedPopularModelIndex != null,
            onClick = { onDownloadModelClick(selectedPopularModelIndex!!) },
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(FeatherIcons.Download, contentDescription = null)
            AppSpacer4W()
            Text("Descarcă modelul selectat")
        }
    }
}

fun getRecommendedModelIndex(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    return when {
        totalRamGb >= 8.0 -> 3
        totalRamGb >= 6.0 -> 2
        totalRamGb >= 4.0 -> 1
        else -> 0
    }
}