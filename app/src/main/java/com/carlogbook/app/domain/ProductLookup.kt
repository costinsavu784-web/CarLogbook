package com.carlogbook.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Turns a retail barcode (EAN/UPC/GTIN) into a product name using free public databases.
 * Returns null when the code is not a GTIN, nothing is found, or the phone is offline.
 */
object ProductLookup {

    data class Product(val name: String, val brand: String, val model: String) {
        /** "Mann HU 8007 z Oil filter": brand first unless the name already starts with it. */
        val displayName: String
            get() = if (brand.isNotBlank() && !name.contains(brand, ignoreCase = true)) "$brand $name".trim() else name.trim()
    }

    /** EAN-8, UPC-A, EAN-13, GTIN-14: all digits, these lengths. Part numbers from Code 128 are not. */
    fun looksLikeGtin(code: String): Boolean =
        code.length in setOf(8, 12, 13, 14) && code.all { it.isDigit() }

    suspend fun lookup(code: String): Product? = withContext(Dispatchers.IO) {
        if (!looksLikeGtin(code)) return@withContext null
        runCatching { upcItemDb(code) }.getOrNull()
            ?: runCatching { openProductsFacts(code) }.getOrNull()
    }

    /** Free "trial" endpoint: 100 lookups a day per IP, no key. */
    private fun upcItemDb(code: String): Product? {
        val json = get("https://api.upcitemdb.com/prod/trial/lookup?upc=${URLEncoder.encode(code, "UTF-8")}") ?: return null
        val root = JSONObject(json)
        if (root.optString("code") != "OK") return null
        val item = root.optJSONArray("items")?.optJSONObject(0) ?: return null
        val title = item.optString("title").trim()
        if (title.isEmpty()) return null
        return Product(title, item.optString("brand").trim(), item.optString("model").trim())
    }

    private fun openProductsFacts(code: String): Product? {
        val json = get("https://world.openproductsfacts.org/api/v2/product/$code.json?fields=product_name,brands") ?: return null
        val root = JSONObject(json)
        if (root.optInt("status") != 1) return null
        val p = root.optJSONObject("product") ?: return null
        val name = p.optString("product_name").trim()
        if (name.isEmpty()) return null
        return Product(name, p.optString("brands").substringBefore(',').trim(), "")
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CarLogbook-Android")
        }
        try {
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
