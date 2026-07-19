package com.vivara.browser.activity.main

import android.net.Uri
import android.widget.Toast
import com.brave.adblock.AdBlockClient
import com.brave.adblock.AdBlockClient.FilterOption
import com.brave.adblock.Utils
import com.vivara.browser.AppContext
import com.vivara.browser.Config
import com.vivara.browser.Vivara
import com.vivara.browser.utils.activemodel.ActiveModel
import com.vivara.browser.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.*

class AdblockModel : ActiveModel() {
    companion object {
        val TAG: String = AdblockModel::class.java.simpleName

        const val SERIALIZED_LIST_FILE = "adblock_ser.dat"
        const val COSMETIC_CSS_FILE = "adblock_cosmetic.css"
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 7 // 7 days

        private const val MAX_CSS_BATCH_SIZE = 4000 // max selectors per CSS rule to avoid JS string limits
    }

    private var client: AdBlockClient? = null
    @Volatile
    private var cosmeticCSS: String = ""
    val clientLoading = ObservableValue(false)
    val config = AppContext.provideConfig()

    init {
        loadAdBlockList(false)
    }

    fun getCosmeticCSS(): String = cosmeticCSS

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean) = modelScope.launch {
        if (clientLoading.value) return@launch
        val checkDate = Calendar.getInstance()
        checkDate.timeInMillis = config.adBlockListLastUpdate
        checkDate.add(Calendar.MINUTE, AUTO_UPDATE_INTERVAL_MINUTES)
        val now = Calendar.getInstance()
        val needUpdate = forceReload || checkDate.before(now)
        clientLoading.value = true

        val client = AdBlockClient()
        var success = false
        val allCosmeticSelectors = mutableListOf<String>()

        withContext(Dispatchers.IO) ioContext@ {
            val serializedFile = File(Vivara.instance.filesDir, SERIALIZED_LIST_FILE)
            val cosmeticFile = File(Vivara.instance.filesDir, COSMETIC_CSS_FILE)

            if (!needUpdate && serializedFile.exists() && client.deserialize(serializedFile.absolutePath)) {
                success = true
                // Load cached cosmetic CSS
                if (cosmeticFile.exists()) {
                    cosmeticCSS = cosmeticFile.readText()
                }
                return@ioContext
            }

            // Build list of URLs: defaults + user's custom URL if different
            val urlsToLoad = Config.DEFAULT_ADBLOCK_LIST_URLS.toMutableList()
            val userUrl = config.adBlockListURL.value
            if (userUrl.isNotBlank() && userUrl != Config.DEFAULT_ADBLOCK_LIST_URL &&
                !Config.DEFAULT_ADBLOCK_LIST_URLS.contains(userUrl)) {
                urlsToLoad.add(userUrl)
            }

            var anyParsed = false
            for (url in urlsToLoad) {
                try {
                    val filterText = URL(url).openConnection().apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                    }.inputStream.bufferedReader().use { it.readText() }

                    // Prepend user custom rules before each list parse
                    val customRules = config.adBlockCustomRules.trim()
                    val combinedText = if (customRules.isNotEmpty()) {
                        customRules + "\n" + filterText
                    } else {
                        filterText
                    }

                    if (client.parse(combinedText)) {
                        anyParsed = true
                    }

                    // Extract cosmetic selectors
                    allCosmeticSelectors.addAll(extractCosmeticSelectors(filterText))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Also parse custom rules alone if no lists were loaded
            val customRules = config.adBlockCustomRules.trim()
            if (customRules.isNotEmpty() && !anyParsed) {
                client.parse(customRules)
                allCosmeticSelectors.addAll(extractCosmeticSelectors(customRules))
            }

            success = anyParsed || customRules.isNotEmpty()
            if (success) {
                try { client.serialize(serializedFile.absolutePath) } catch (_: Exception) {}
                cosmeticCSS = buildCosmeticCSS(allCosmeticSelectors)
                try { cosmeticFile.writeText(cosmeticCSS) } catch (_: Exception) {}
            }
        }

        this@AdblockModel.client = client
        config.adBlockListLastUpdate = now.timeInMillis
        if (!success) {
            Toast.makeText(Vivara.instance, "Error loading ad-blocker list", Toast.LENGTH_SHORT).show()
        }
        clientLoading.value = false
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        val client = client ?: return false
        val baseHost = baseUri.host ?: return false
        val filterOption = mapRequestToFilterOption(url, type)
        return try {
            client.matches(url.toString(), filterOption, baseHost)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extract element hiding selectors from filter text.
     * Handles: ##.selector (global), domain.com##.selector (domain-specific — skipped for simplicity).
     * Handles #@# exceptions.
     */
    private fun extractCosmeticSelectors(filterText: String): List<String> {
        val selectors = mutableListOf<String>()
        for (line in filterText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) continue

            // #@# = exception, skip
            if (trimmed.contains("#@#")) continue

            val hashIndex = trimmed.indexOf("##")
            if (hashIndex < 0) continue

            if (hashIndex == 0) {
                // Global rule: ##.selector
                val selector = trimmed.substring(2).trim()
                if (selector.isNotEmpty()) {
                    selectors.add(selector)
                }
            }
            // Domain-specific rules (domain.com##.selector) are skipped for now
        }
        return selectors
    }

    /**
     * Build batched CSS rules from selectors.
     * Splits into multiple CSS rules to avoid overly long strings.
     */
    private fun buildCosmeticCSS(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""
        val unique = selectors.distinct()
        val sb = StringBuilder()
        var batch = mutableListOf<String>()
        var batchLen = 0

        for (selector in unique) {
            if (batchLen + selector.length > MAX_CSS_BATCH_SIZE) {
                sb.append(batch.joinToString(","))
                sb.append("{display:none!important}")
                batch = mutableListOf()
                batchLen = 0
            }
            batch.add(selector)
            batchLen += selector.length + 1
        }
        if (batch.isNotEmpty()) {
            sb.append(batch.joinToString(","))
            sb.append("{display:none!important}")
        }
        return sb.toString()
    }

    private fun mapRequestToFilterOption(url: Uri?, type: String?): FilterOption {
        if (type != null) {
            if (type == "image" || type.contains("image/")) {
                return FilterOption.IMAGE
            }
            if (type == "style" || type.contains("/css")) {
                return FilterOption.CSS
            }
            if (type == "script" || type.contains("javascript")) {
                return FilterOption.SCRIPT
            }
            if (type.contains("video/")) {
                return FilterOption.OBJECT
            }
        }
        if (url != null) {
            if (Utils.uriHasExtension(url, "css")) {
                return FilterOption.CSS
            }
            if (Utils.uriHasExtension(url, "js")) {
                return FilterOption.SCRIPT
            }
            if (Utils.uriHasExtension(
                    url,
                    "png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "tiff"
                )
            ) {
                return FilterOption.IMAGE
            }
            if (Utils.uriHasExtension(url, "mp4", "mov", "avi")) {
                return FilterOption.OBJECT
            }
        }
        return FilterOption.UNKNOWN
    }
}
