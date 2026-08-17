package io.github.zhangjq.bcl

/**
 * Created by Michael on 26.03.2017.
 *
 * This class holds constants for internal use that are not shown to the user
 */

object Constants {
    const val SETTINGS = "Settings"
    const val SETTINGS_VERSION = "SettingsVersion"

    const val FILE_KEY = "ctrl_file"
    const val CHARGE_ON_KEY = "charge_on"
    const val CHARGE_OFF_KEY = "charge_off"

    const val DEFAULT_FILE = "/sys/class/power_supply/battery/charging_enabled"
    const val DEFAULT_ENABLED = "1"
    const val DEFAULT_DISABLED = "0"

    const val LIMIT = "limit"
    const val MIN = "min"
    const val CHARGE_LIMIT_ENABLED = "enable"
    const val DISABLE_CHARGE_NOW = "disable_charge_now"
    const val NOTIFICATION_LIVE = "notificationLive"
    const val AUTO_RESET_STATS = "auto_reset_stats"

    const val LIMIT_BY_VOLTAGE = "limit_by_voltage"
    const val DEFAULT_VOLTAGE_LIMIT = "default_voltage_limit"
    const val CUSTOM_VOLTAGE_LIMIT = "custom_voltage_limit"
//    const val CURRENT_VOLTAGE_LIMIT = "current_voltage_limit"

    // ms after reaching limit, where the "unplug" event is recognized as power cut instead of action unplugging
    const val POWER_CHANGE_TOLERANCE_MS: Long = 3000
    const val CHARGING_CHANGE_TOLERANCE_MS: Long = 500
    const val MAX_BACK_OFF_TIME: Long = 30000

    const val MAX_ALLOWED_LIMIT_PC: Int = 100
    const val DEFAULT_LIMIT_PC: Int = 80
    const val MIN_ALLOWED_LIMIT_PC: Int = 40

    // low battery auto shutdown
    const val DEFAULT_SHUTDOWN_THRESHOLD_PC: Int = 5
    const val MIN_ALLOWED_SHUTDOWN_THRESHOLD_PC: Int = 1
    const val MAX_ALLOWED_SHUTDOWN_THRESHOLD_PC: Int = 20
    const val SHUTDOWN_DELAY_SECONDS: Long = 30

    // adaptive heartbeat schedule for the non-resident auto shutdown service
    const val PRECISION_ZONE_MARGIN: Int = 5
    const val SHUTDOWN_WAKE_60_MIN: Long = 60L * 60 * 1000
    const val SHUTDOWN_WAKE_45_MIN: Long = 45L * 60 * 1000
    const val SHUTDOWN_WAKE_20_MIN: Long = 20L * 60 * 1000
    const val SHUTDOWN_WAKE_9_MIN: Long = 9L * 60 * 1000
    const val SHUTDOWN_WAKE_60_S: Long = 60_000L
    const val ACTION_AUTO_SHUTDOWN_EVALUATE = BuildConfig.APPLICATION_ID + ".action.AUTO_SHUTDOWN_EVALUATE"
    const val AUTO_SHUTDOWN_COUNTDOWN_START = "auto_shutdown_countdown_start"
    const val LOW_BATTERY_NOTIF_THRESHOLD_PC: Int = 20
    const val AUTO_SHUTDOWN_LOW_BATT_NOTIF_POSTED = "auto_shutdown_low_batt_notif_posted"

    //voltage thresholds in mV, inclusive
    const val DEFAULT_VOLTAGE_FILE = "/sys/class/power_supply/battery/voltage_max"
    const val MIN_VOLTAGE_THRESHOLD_MV = "3700"
    const val DEFAULT_VOLTAGE_THRESHOLD_MV = "4100"
    const val MAX_VOLTAGE_THRESHOLD_MV = "4400"

    const val INTENT_TOGGLE_ACTION = BuildConfig.APPLICATION_ID + ".action.TOGGLE"
    const val INTENT_DISABLE_ACTION = BuildConfig.APPLICATION_ID + ".action.DISABLE"
    const val INTENT_CHANGE_LIMIT_ACTION = BuildConfig.APPLICATION_ID + ".action.CHANGE_LIMIT"
    const val FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".action.FOREGROUND_SERVICE"
    const val AUTO_SHUTDOWN_NOTIFICATION_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".action.AUTO_SHUTDOWN"

    const val SAVED_PATH_DATA = "saved_ctrl_path_data"
    const val SAVED_ENABLED_DATA = "saved_ctrl_enabled_data"
    const val SAVED_DISABLED_DATA = "saved_ctrl_disabled_data"

    const val LIGHT = "light"
    const val DARK = "dark"
    const val BLACK = "black"
}
