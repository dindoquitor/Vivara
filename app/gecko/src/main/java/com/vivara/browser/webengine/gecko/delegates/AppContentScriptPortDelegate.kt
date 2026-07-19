package com.vivara.browser.webengine.gecko.delegates

import android.util.Log
import com.vivara.browser.AppContext
import com.vivara.browser.webengine.gecko.GeckoWebEngine
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

// Handles content script messages and cosmetic ad filtering for GeckoView.
class AppContentScriptPortDelegate(val port: WebExtension.Port, val webEngine: GeckoWebEngine): WebExtension.PortDelegate {

    override fun onPortMessage(message: Any, port: WebExtension.Port) {
        try {
            val msgJson = message as? JSONObject ?: return
            when (msgJson.optString("action")) {
                "getCosmeticCSS" -> {
                    val callback = webEngine.callback ?: return
                    val css = callback.getCosmeticCSS()
                    if (css.isNotEmpty()) {
                        sendCosmeticCSS(css)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDisconnect(port: WebExtension.Port) {
        Log.d(TAG, "onDisconnect")
        webEngine.appContentScriptPortDelegate = null
    }

    fun sendCosmeticCSS(css: String) {
        try {
            port.postMessage(
                JSONObject().put("action", "cosmetic")
                    .put("data", JSONObject().put("css", css))
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        val TAG: String = AppContentScriptPortDelegate::class.java.simpleName
    }
}
