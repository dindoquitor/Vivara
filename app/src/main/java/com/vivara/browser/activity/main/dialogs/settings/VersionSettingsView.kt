package com.vivara.browser.activity.main.dialogs.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.Html
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ScrollView
import androidx.webkit.WebViewCompat
import com.vivara.browser.AppContext
import com.vivara.browser.BuildConfig
import com.vivara.browser.R
import com.vivara.browser.Vivara
import com.vivara.browser.activity.IncognitoModeMainActivity
import com.vivara.browser.activity.main.AutoUpdateModel
import com.vivara.browser.activity.main.MainActivity
import com.vivara.browser.activity.main.SettingsModel
import com.vivara.browser.databinding.ViewSettingsVersionBinding
import com.vivara.browser.utils.activemodel.ActiveModelsRepository
import com.vivara.browser.utils.activity
import com.vivara.browser.webengine.WebEngineFactory
import androidx.core.net.toUri

@SuppressLint("SetTextI18n")
class VersionSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    companion object {
        private const val URL_SUPPORT_AUTHOR = "https://buymeacoffee.com/playpixelpro"
        private const val URL_LICENSE =
            "" // CHANGEME: set your LICENSE.md URL
        private const val URL_PRIVACY_POLICY =
            "" // CHANGEME: set your PRIVACY.md URL
    }
    private var vb = ViewSettingsVersionBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var config = AppContext.provideConfig()
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var autoUpdateModel = ActiveModelsRepository.get(AutoUpdateModel::class, activity!!)
    var callback: Callback? = null

    interface Callback {
        fun onNeedToCloseSettings()
    }

    init {
        vb.tvVersion.text = context.getString(R.string.version_s, BuildConfig.VERSION_NAME)

        vb.tvBuildFlavor.text = context.getString(
            R.string.build_flavor_s,
            BuildConfig.FLAVOR_appstore,
            BuildConfig.FLAVOR_webengine
        )

        val engineVersion = "Engine: " + WebEngineFactory.getWebEngineVersionString()
        vb.tvWebViewVersion.text = engineVersion

        vb.tvLink.text = Html.fromHtml("<p><u></u></p>",
            Html.FROM_HTML_MODE_LEGACY) // CHANGEME: set your GitHub repo URL
        vb.tvLink.setOnClickListener {
            loadUrl(vb.tvLink.text.toString())
        }

        vb.tvSupportAuthor.text = context.getString(R.string.support_the_author)
        vb.tvSupportAuthor.setOnClickListener {
            loadUrl(URL_SUPPORT_AUTHOR)
        }

        vb.tvLicense.text = context.getString(R.string.view_license)
        vb.tvLicense.setOnClickListener {
            loadUrl(URL_LICENSE)
        }

        vb.tvPrivacy.text = context.getString(R.string.privacy_policy)
        vb.tvPrivacy.setOnClickListener {
            loadUrl(URL_PRIVACY_POLICY)
        }

        vb.tvUkraine.setOnClickListener {
            loadUrl("") // CHANGEME: set your custom URL or remove this button
        }

        if (BuildConfig.BUILT_IN_AUTO_UPDATE) {
            vb.chkAutoCheckUpdates.isChecked = autoUpdateModel.needAutoCheckUpdates

            vb.chkAutoCheckUpdates.setOnCheckedChangeListener { _, isChecked ->
                autoUpdateModel.saveAutoCheckUpdates(isChecked)

                updateUIVisibility()
            }

            vb.spUpdateChannel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    val selectedChannel =
                        autoUpdateModel.updateChecker.versionCheckResult!!.availableChannels[position]
                    if (selectedChannel == config.updateChannel) return
                    config.updateChannel = selectedChannel
                    autoUpdateModel.checkUpdate(true) {
                        updateUIVisibility()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {

                }
            }

            vb.btnUpdate.setOnClickListener {
                callback?.onNeedToCloseSettings()
                autoUpdateModel.showUpdateDialogIfNeeded(activity as MainActivity, true)
            }

            updateUIVisibility()
        } else {
            vb.chkAutoCheckUpdates.visibility = View.INVISIBLE
        }
    }

    private fun loadUrl(url: String) {
        callback?.onNeedToCloseSettings()
        val activityClass = if (settingsModel.config.incognitoMode)
            IncognitoModeMainActivity::class.java else MainActivity::class.java
        val intent = Intent(activity, activityClass)
        intent.data = url.toUri()
        activity?.startActivity(intent)
    }

    private fun updateUIVisibility() {
        if (autoUpdateModel.updateChecker.versionCheckResult == null) {
            autoUpdateModel.checkUpdate(false) {
                if (autoUpdateModel.updateChecker.versionCheckResult != null) {
                    updateUIVisibility()
                }
            }
            return
        }

        vb.tvUpdateChannel.visibility = if (autoUpdateModel.needAutoCheckUpdates) View.VISIBLE else View.INVISIBLE
        vb.spUpdateChannel.visibility = if (autoUpdateModel.needAutoCheckUpdates) View.VISIBLE else View.INVISIBLE

        if (autoUpdateModel.needAutoCheckUpdates) {
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                autoUpdateModel.updateChecker.versionCheckResult!!.availableChannels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            vb.spUpdateChannel.adapter = adapter
            val selected = autoUpdateModel.updateChecker.versionCheckResult!!.availableChannels.indexOf(config.updateChannel)
            if (selected != -1) {
                val tmp = vb.spUpdateChannel.onItemSelectedListener
                vb.spUpdateChannel.onItemSelectedListener = null
                vb.spUpdateChannel.setSelection(selected)
                vb.spUpdateChannel.onItemSelectedListener = tmp
            }
        }

        val hasUpdate = autoUpdateModel.updateChecker.hasUpdate()
        vb.tvNewVersion.visibility = if (autoUpdateModel.needAutoCheckUpdates && hasUpdate) View.VISIBLE else View.INVISIBLE
        vb.btnUpdate.visibility = if (autoUpdateModel.needAutoCheckUpdates && hasUpdate) View.VISIBLE else View.INVISIBLE
        if (hasUpdate) {
            vb.tvNewVersion.text = context.getString(R.string.new_version_available_s, autoUpdateModel.updateChecker.versionCheckResult!!.latestVersionName)
        }
    }
}