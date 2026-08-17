package io.github.zhangjq.bcl.receivers

import android.content.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import io.github.zhangjq.bcl.Constants.CHARGING_CHANGE_TOLERANCE_MS
import io.github.zhangjq.bcl.Constants.DEFAULT_LIMIT_PC
import io.github.zhangjq.bcl.Constants.LIMIT
import io.github.zhangjq.bcl.Constants.MAX_BACK_OFF_TIME
import io.github.zhangjq.bcl.Constants.MIN
import io.github.zhangjq.bcl.Constants.SETTINGS
import io.github.zhangjq.bcl.ForegroundService
import io.github.zhangjq.bcl.R
import io.github.zhangjq.bcl.Utils
import io.github.zhangjq.bcl.settings.PrefsFragment


/**
 * Created by Michael on 01.04.2017.
 *
 * Dynamically created receiver for battery events. Only registered if power supply is attached.
 */
class BatteryReceiver(private val service: ForegroundService) : BroadcastReceiver() {

    private var chargedToLimit = false
    private var lastState = -1
    private var limitPercentage: Int = 0
    private var rechargePercentage: Int = 0
    private val prefs = Utils.getPrefs(service.baseContext)
    private var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val settings = service.getSharedPreferences(SETTINGS, 0)

    init {
        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                LIMIT, MIN -> {
                    reset(sharedPreferences)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        settings.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        reset(settings)
    }

    private fun reset(settings: SharedPreferences) {
        chargedToLimit = false
        lastState = -1
        backOffTime = CHARGING_CHANGE_TOLERANCE_MS
        limitPercentage = settings.getInt(LIMIT, DEFAULT_LIMIT_PC)
        rechargePercentage = settings.getInt(MIN, limitPercentage - 2)
        // manually fire onReceive() to update state if service is enabled
        onReceive(service, service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!)
    }

    /**
     * Remembers the new state and returns whether the state was changed
     *
     * @param newState the new state
     * @return whether the state has changed
     */
    private fun switchState(newState: Int): Boolean {
        val oldState = lastState
        lastState = newState
        return oldState != newState
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ignore events while trying to fix charging state, see below
        if (Utils.isChangePending(backOffTime * 2)) {
            return
        }

        val batteryLevel = Utils.getBatteryLevel(intent)
        val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        // The state is anchored at plug-in (the service restarts and lastState is reset):
        //  - below the recharge threshold we start charging and keep it on through the
        //    [recharge, limit) window until the limit is reached,
        //  - at or above the recharge threshold we do not charge at all until the level
        //    drops below it.
        if (batteryLevel >= limitPercentage) {
            if (switchState(CHARGE_STOP)) {
                Log.d("Charging State", "CHARGE_STOP " + this.hashCode())
                // remember that we let the device charge until limit at least once
                chargedToLimit = true
                // active auto reset on service shutdown
                service.enableAutoReset()
                Utils.changeState(service, Utils.CHARGE_OFF)

                if (preferences.getBoolean(PrefsFragment.KEY_DISABLE_AUTO_RECHARGE, false)) {
                    Utils.stopService(service, false)
                }

                // set the "maintain" notification, this must not change from now
                service.setNotificationTitle(
                    service.getString(R.string.maintaining_x_to_y, rechargePercentage, limitPercentage)
                )
            } else if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING
                && prefs.getBoolean(PrefsFragment.KEY_ENFORCE_CHARGE_LIMIT, true)
            ) {
                //Double the back off time with every unsuccessful round up to MAX_BACK_OFF_TIME
                backOffTime = (backOffTime * 2).coerceAtMost(MAX_BACK_OFF_TIME)
                Log.d(
                    "Charging State",
                    "Fixing state w. CHARGE_ON/CHARGE_OFF " + this.hashCode() + " (Delay: $backOffTime)"
                )
                // if the device did not stop charging, try to "cycle" the state to fix this
                Utils.changeState(service, Utils.CHARGE_ON)
                // schedule the charging stop command to be executed after CHARGING_CHANGE_TOLERANCE_MS
                val service = this.service
                handler.postDelayed({ Utils.changeState(service, Utils.CHARGE_OFF) }, backOffTime)
            } else {
                backOffTime = CHARGING_CHANGE_TOLERANCE_MS
            }
        } else if (batteryLevel < rechargePercentage) {
            // below the recharge threshold: start charging
            if (switchState(CHARGE_REFRESH)) {
                Log.d("Charging State", "CHARGE_REFRESH " + this.hashCode())
                // from now on we may charge up to the limit again
                chargedToLimit = false
                service.setNotificationTitle(service.getString(R.string.waiting_until_x, limitPercentage))
                Utils.changeState(service, Utils.CHARGE_ON)
            }
        } else if (lastState == CHARGE_REFRESH) {
            // charging session started below the recharge threshold (recorded at plug-in):
            // keep charging through the [recharge, limit) window until the limit is reached
        } else {
            // inside the [recharge, limit) window without an active charging session
            // (plugged in at or above the recharge threshold, or draining after the limit
            // was reached): keep the charge off and wait for the level to drop
            if (switchState(CHARGE_IDLE)) {
                Log.d("Charging State", "CHARGE_IDLE " + this.hashCode())
                Utils.changeState(service, Utils.CHARGE_OFF)
                service.setNotificationTitle(service.getString(R.string.battery_full_no_charge))
            }
        }

        // update battery status information and rebuild notification
        // service.setNotificationContentText(Utils.getBatteryInfo(service, intent, useFahrenheit))
        service.updateNotification()
    }

    fun detach(context: Context) {
        // unregister the listener that listens for relevant change events
        prefs.unregisterOnSharedPreferenceChangeListener(this.preferenceChangeListener)
        Utils.getSettings(context)
            .unregisterOnSharedPreferenceChangeListener(this.preferenceChangeListener)
        // technically not necessary, but it prevents inlining of this required field
        // see end of https://developer.android.com/guide/topics/ui/settings.html#Listening
        this.preferenceChangeListener = null
    }

    companion object {
        private const val CHARGE_STOP = 1
        private const val CHARGE_REFRESH = 2
        private const val CHARGE_IDLE = 3

        private val handler = Handler(Looper.getMainLooper())
        internal var backOffTime = CHARGING_CHANGE_TOLERANCE_MS
    }

}
