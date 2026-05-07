package io.shubham0204.smollmandroid.ui.screens.model_download

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Star
import io.shubham0204.smollmandroid.data.LLMModel

@Preview
@Composable
fun PreviewPopularModelsList() {
    PopularModelsList(selectedModelIndex = 0, onModelSelected = {})
}

@Composable
fun PopularModelsList(selectedModelIndex: Int?, onModelSelected: (Int) -> Unit) {
    val context = LocalContext.current
    val recommendedIndex = getRecommendedModelIndex(context)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        popularModelsList.forEachIndexed { idx, model ->
            val isSelected = idx == selectedModelIndex
            val isRecommended = idx == recommendedIndex
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModelSelected(idx) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else if (isRecommended) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                         else if (isRecommended) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                         else null
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            FeatherIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else if (isRecommended) {
                        Icon(
                            FeatherIcons.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isRecommended) {
                            Text(
                                text = "⭐ Recomandat pentru telefonul tău",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getPopularModel(index: Int?): LLMModel? = if (index != null) popularModelsList[index] else null

private val popularModelsList =
    listOf(
        LLMModel(
            name = "Llama 3.2 1B Q4 (sub 3GB RAM)",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        ),
        LLMModel(
            name = "Llama 3.2 3B Q4 (3-5GB RAM)",
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        ),
        LLMModel(
            name = "Llama 3.2 3B Q8 (6-8GB RAM)",
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q8_0.gguf",
        ),
        LLMModel(
            name = "Llama 3.2 8B Q4 (8GB+ RAM)",
            url = "https://huggingface.co/bartowski/Llama-3.2-8B-Instruct-GGUF/resolve/main/Llama-3.2-8B-Instruct-Q4_K_M.gguf",
        ),
    )