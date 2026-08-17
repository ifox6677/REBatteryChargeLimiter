package io.github.zhangjq.bcl

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.zhangjq.bcl.Constants.ACTION_AUTO_SHUTDOWN_EVALUATE
import io.github.zhangjq.bcl.Constants.AUTO_SHUTDOWN_COUNTDOWN_START
import io.github.zhangjq.bcl.Constants.AUTO_SHUTDOWN_LOW_BATT_NOTIF_POSTED
import io.github.zhangjq.bcl.Constants.AUTO_SHUTDOWN_NOTIFICATION_CHANNEL_ID
import io.github.zhangjq.bcl.Constants.DEFAULT_SHUTDOWN_THRESHOLD_PC
import io.github.zhangjq.bcl.Constants.MAX_ALLOWED_SHUTDOWN_THRESHOLD_PC
import io.github.zhangjq.bcl.Constants.MIN_ALLOWED_SHUTDOWN_THRESHOLD_PC
import io.github.zhangjq.bcl.Constants.PRECISION_ZONE_MARGIN
import io.github.zhangjq.bcl.Constants.SHUTDOWN_DELAY_SECONDS
import io.github.zhangjq.bcl.Constants.SHUTDOWN_WAKE_60_S
import io.github.zhangjq.bcl.Constants.SHUTDOWN_WAKE_9_MIN
import io.github.zhangjq.bcl.Constants.SHUTDOWN_WAKE_20_MIN
import io.github.zhangjq.bcl.Constants.SHUTDOWN_WAKE_45_MIN
import io.github.zhangjq.bcl.Constants.SHUTDOWN_WAKE_60_MIN
import io.github.zhangjq.bcl.activities.MainActivity
import io.github.zhangjq.bcl.settings.PrefsFragment
import kotlin.math.ceil

/**
 * Short-lived service implementing the low battery auto shutdown monitoring.
 *
 * The service never stays resident: every wake-up (boot, power connect or
 * disconnect, or the scheduled alarm) starts it once, it reads the current
 * battery level, picks the next wake-up interval by level band and re-arms
 * the alarm. It then stops itself. No foreground notification is required,
 * so the feature consumes no power while the device is not monitored.
 */
class AutoShutdownService : Service() {

    private val prefs by lazy(LazyThreadSafetyMode.NONE) { Utils.getPrefs(this) }
    private val notificationManager by lazy(LazyThreadSafetyMode.NONE) { NotificationManagerCompat.from(this) }
    private val alarmManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    /** RTC time of the currently armed alarm, 0 if none is armed */
    private var nextAlarmAt = 0L

    override fun onCreate() {
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null, ACTION_AUTO_SHUTDOWN_EVALUATE -> evaluate()
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
        return START_NOT_STICKY
    }

    private fun evaluate() {
        if (!prefs.getBoolean(PrefsFragment.KEY_AUTO_SHUTDOWN_ENABLED, false)) {
            cancelWork()
            stopSelf()
            return
        }

        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent == null) {
            stopSelf()
            return
        }
        val level = Utils.getBatteryLevel(batteryIntent)
        val threshold = getThreshold()

        // while charging (with the skip option enabled) the feature is
        // suspended: no alarm and no notification are left behind, the
        // power disconnect broadcast re-arms the schedule
        if (isCharging(batteryIntent)
            && prefs.getBoolean(PrefsFragment.KEY_AUTO_SHUTDOWN_SKIP_WHEN_CHARGING, true)
        ) {
            cancelWork()
            stopSelf()
            return
        }

        if (level < threshold) {
            handleCountdown(level, threshold)
        } else {
            if (getCountdownStart() != 0L) {
                setCountdownStart(0L)
            }
            scheduleNext(level)
            updateNotification(level, threshold, nextAlarmAt, null)
        }
        stopSelf()
    }

    private fun handleCountdown(level: Int, threshold: Int) {
        var countdownStart = getCountdownStart()
        if (countdownStart == 0L) {
            countdownStart = SystemClock.elapsedRealtime()
            setCountdownStart(countdownStart)
        }
        val remaining = countdownStart + SHUTDOWN_DELAY_SECONDS * 1000 - SystemClock.elapsedRealtime()
        if (remaining <= 0) {
            Log.i(TAG, "Battery stayed below $threshold% for $SHUTDOWN_DELAY_SECONDS s, shutting down")
            Utils.shutdownDevice {
                // shutdown failed (e.g. root denied): reset the countdown so
                // the next wake-up retries instead of triggering instantly
                setCountdownStart(0L)
            }
            scheduleNext(level)
            updateNotification(level, threshold, nextAlarmAt, 0L)
        } else {
            scheduleAtRtc(System.currentTimeMillis() + remaining, exact = true)
            updateNotification(level, threshold, nextAlarmAt, remaining)
        }
    }

    private fun scheduleNext(level: Int) {
        val (delayMs, exact) = when {
            level >= 80 -> SHUTDOWN_WAKE_60_MIN to false
            level >= 60 -> SHUTDOWN_WAKE_45_MIN to false
            level >= 30 -> SHUTDOWN_WAKE_20_MIN to false
            level >= getThreshold() + PRECISION_ZONE_MARGIN -> SHUTDOWN_WAKE_9_MIN to true
            else -> SHUTDOWN_WAKE_60_S to true
        }
        scheduleAtRtc(System.currentTimeMillis() + delayMs, exact)
    }

    private fun scheduleAtRtc(fireAt: Long, exact: Boolean) {
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact && canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmPendingIntent(this))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmPendingIntent(this))
        }
        nextAlarmAt = fireAt
    }

    private fun getThreshold(): Int {
        return prefs.getInt(PrefsFragment.KEY_AUTO_SHUTDOWN_THRESHOLD, DEFAULT_SHUTDOWN_THRESHOLD_PC)
            .coerceIn(MIN_ALLOWED_SHUTDOWN_THRESHOLD_PC, MAX_ALLOWED_SHUTDOWN_THRESHOLD_PC)
    }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0
    }

    private fun updateNotification(level: Int, threshold: Int, nextAlarmAt: Long?, countdownMs: Long?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                AUTO_SHUTDOWN_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
                .setName(getString(R.string.auto_shutdown_notification_channel))
                .build()
        )
        val lowBattNotifPosted = prefs.getBoolean(AUTO_SHUTDOWN_LOW_BATT_NOTIF_POSTED, false)
        if (level < Constants.LOW_BATTERY_NOTIF_THRESHOLD_PC) {
            if (lowBattNotifPosted) {
                // static low battery notification already shown: do not rebuild
                return
            }
            setLowBattNotifPosted(true)
            postNotification(
                getString(R.string.auto_shutdown_notif_title),
                getString(R.string.auto_shutdown_low_battery_notif, threshold)
            )
            return
        }
        if (lowBattNotifPosted) {
            setLowBattNotifPosted(false)
        }
        val now = System.currentTimeMillis()
        val text = when {
            countdownMs != null -> getString(
                R.string.auto_shutdown_countdown_notif, threshold, ceil(countdownMs / 1000.0).toLong()
            )
            nextAlarmAt == null || nextAlarmAt - now <= SHUTDOWN_WAKE_60_S -> getString(
                R.string.auto_shutdown_tracking_notif, threshold
            )
            else -> getString(
                R.string.auto_shutdown_next_wake_notif,
                ceil((nextAlarmAt - now) / 60_000.0).toLong(),
                threshold
            )
        }
        postNotification(getString(R.string.auto_shutdown_notif_title), text)
    }

    private fun postNotification(title: String, text: String) {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(this, AUTO_SHUTDOWN_NOTIFICATION_CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(NOTIFY_ID, notification)
    }

    private fun cancelWork() {
        alarmManager.cancel(alarmPendingIntent(this))
        notificationManager.cancel(NOTIFY_ID)
        setCountdownStart(0L)
        setLowBattNotifPosted(false)
    }

    private fun getCountdownStart(): Long {
        return prefs.getLong(AUTO_SHUTDOWN_COUNTDOWN_START, 0L)
    }

    private fun setCountdownStart(value: Long) {
        prefs.edit().putLong(AUTO_SHUTDOWN_COUNTDOWN_START, value).apply()
    }

    private fun setLowBattNotifPosted(value: Boolean) {
        prefs.edit().putBoolean(AUTO_SHUTDOWN_LOW_BATT_NOTIF_POSTED, value).apply()
    }

    override fun onDestroy() {
        isRunning = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private val TAG = AutoShutdownService::class.java.simpleName
        private const val NOTIFY_ID = 2

        /**
         * Returns whether the service is running right now
         *
         * @return Whether service is running
         */
        var isRunning = false
            private set

        /**
         * Pending intent used for every scheduled wake-up. Equal intents
         * cancel each other, so the same builder is used for set and cancel.
         */
        fun alarmPendingIntent(context: Context): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getService(
                context, 0,
                Intent(context, AutoShutdownService::class.java).setAction(ACTION_AUTO_SHUTDOWN_EVALUATE),
                flags
            )
        }

        /**
         * Cancels the scheduled alarm and the notification, independent of
         * whether the service is currently running.
         */
        fun cancelScheduledWork(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(alarmPendingIntent(context))
            NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
            Utils.getPrefs(context).edit()
                .putLong(AUTO_SHUTDOWN_COUNTDOWN_START, 0L)
                .putBoolean(AUTO_SHUTDOWN_LOW_BATT_NOTIF_POSTED, false)
                .apply()
        }
    }
}
