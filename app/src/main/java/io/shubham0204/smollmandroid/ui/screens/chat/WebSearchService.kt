package io.shubham0204.smollmandroid.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WebSearchService {
    private val client = OkHttpClient()
    
    // Folosim DuckDuckGo Instant Answer API (gratuit, fără API key)
    suspend fun search(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            
            val json = JSONObject(body)
            val abstract = json.optString("AbstractText", "")
            val answer = json.optString("Answer", "")
            val relatedTopics = json.optJSONArray("RelatedTopics")
            
            val sb = StringBuilder()
            if (answer.isNotBlank()) sb.appendLine("Răspuns direct: $answer")
            if (abstract.isNotBlank()) sb.appendLine(abstract)
            
            // Adaugă primele 3 rezultate conexe
            relatedTopics?.let { topics ->
                for (i in 0 until minOf(3, topics.length())) {
                    val topic = topics.optJSONObject(i)
                    val text = topic?.optString("Text", "") ?: ""
                    if (text.isNotBlank()) sb.appendLine("• $text")
                }
            }
            
            sb.toString().trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    // Detectează dacă întrebarea necesită căutare web
    fun needsWebSearch(query: String): Boolean {
        val webKeywords = listOf(
            "ce este", "cine este", "când", "unde", "cum", "de ce",
            "știri", "acum", "recent", "ultima", "cel mai nou",
            "what is", "who is", "when", "where", "how", "why",
            "news", "latest", "current", "today", "2024", "2025", "2026"
        )
        val lowerQuery = query.lowercase()
        return webKeywords.any { lowerQuery.contains(it) }
    }
}