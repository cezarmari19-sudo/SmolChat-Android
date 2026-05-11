package io.shubham0204.smollmandroid.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WebSearchService {
    private val client = OkHttpClient()

    suspend fun search(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val results = Regex("<a class=\"result__snippet\"[^>]*>(.*?)</a>")
                .findAll(body)
                .take(3)
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            results.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

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