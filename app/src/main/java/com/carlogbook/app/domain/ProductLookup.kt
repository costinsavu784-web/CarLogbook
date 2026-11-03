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
        /**
         * Short, readable name. Database titles are often long marketing strings
         * ("MANN-FILTER HU 8007 z Oil Filter, for BMW 5 Series…"): keep up to the first comma or dash
         * clause, cap the length, and put the brand in front if it is not already there.
         */
        val displayName: String
            get() {
                var n = name.trim()
                    .substringBefore(" - ")
                    .substringBefore(" | ")
                    .substringBefore(", ")
                if (n.length > 60) n = n.take(60).substringBeforeLast(' ')
                if (brand.isNotBlank() && !n.contains(brand, ignoreCase = true)) n = "$brand $n"
                return n.trim()
            }
    }

    /**
     * True when an EAN/UPC is a retailer's internal label rather than a manufacturer code: GS1 keeps
     * prefixes 02, 04 and 20-29 for in-store use, and some prefixes (such as 525) are not assigned at all.
     * Such codes will never be found online and are useless as evidence of the product.
     */
    fun looksLikeStoreLabel(code: String): Boolean {
        if (!looksLikeGtin(code)) return false
        val ean = if (code.length == 12) "0$code" else code
        if (ean.length != 13) return false
        val p2 = ean.substring(0, 2).toInt()
        val p3 = ean.substring(0, 3).toInt()
        return p2 == 2 || p2 == 4 || p2 in 20..29 || p3 in 522..527 || p3 in 532..534 || p3 in 536..538
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
