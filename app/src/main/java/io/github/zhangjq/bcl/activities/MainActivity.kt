package io.github.zhangjq.bcl.activities

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.Shell
import io.github.zhangjq.bcl.BuildConfig
import io.github.zhangjq.bcl.Constants.SETTINGS_VERSION
import io.github.zhangjq.bcl.R
import io.github.zhangjq.bcl.Utils
import io.github.zhangjq.bcl.settings.CtrlFileHelper
import io.github.zhangjq.bcl.settings.PrefsFragment
import io.github.zhangjq.bcl.settings.SettingsActivity


class MainActivity : AppCompatActivity() {
    private var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Utils.getPrefs(this)
        Utils.setTheme(this, true)
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        setTitle(R.string.app_name)

        // Exit immediately if no root support
        if (!Shell.getShell().isRoot) {
            showNoRootDialog()
            return
        }
        updateSettingsVersion()
        checkForControlFiles()
        whitelistIfFirstStart()
        // re-arm the auto shutdown schedule in case it was killed (e.g. force stop)
        Utils.startAutoShutdownServiceIfEnabled(this)
        // Load main fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, MainFragment())
            .commit()
    }

    private fun showNoRootDialog() {
        MaterialAlertDialogBuilder(this@MainActivity)
            .setMessage(R.string.root_denied)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }.show()
    }

    private fun checkForControlFiles() {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        if (!prefs.contains(PrefsFragment.KEY_CONTROL_FILE)) {
            CtrlFileHelper.validateFiles(this) {
                var found = false
                for (cf in Utils.getCtrlFiles(this@MainActivity)) {
                    if (cf.isValid) {
                        Utils.setCtrlFile(this@MainActivity, cf)
                        found = true
                        break
                    }
                }
                if (!found) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setMessage(R.string.device_not_supported)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }.show()
                }
            }
        }
    }

    private fun updateSettingsVersion() {
        val settingsVersion = prefs.getInt(SETTINGS_VERSION, 0)
        var versionCode = 0L
        try {
            versionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
        } catch (e: PackageManager.NameNotFoundException) {
            Log.wtf(TAG, e)
        }

        if (settingsVersion < versionCode) {
            // update the settings version
            prefs.edit().putInt(SETTINGS_VERSION, versionCode.toInt()).apply()
        }
    }

    private fun whitelistIfFirstStart() {
        if (!prefs.getBoolean(getString(R.string.previously_started), false)) {
            // whitelist App for Doze Mode
            Shell.cmd("dumpsys deviceidle whitelist +${BuildConfig.APPLICATION_ID}").submit {
                if (it.isSuccess) {
                    prefs.edit().putBoolean(getString(R.string.previously_started), true).apply()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    override fun onDestroy() {
        Utils.getPrefs(baseContext)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        // technically not necessary, but it prevents inlining of this required field
        // see end of https://developer.android.com/guide/topics/ui/settings.html#Listening
        preferenceChangeListener = null
        super.onDestroy()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val MSG_UPDATE_VOLTAGE_THRESHOLD = 1
        const val VOLTAGE_THRESHOLD = "voltageThreshold"
    }
}
