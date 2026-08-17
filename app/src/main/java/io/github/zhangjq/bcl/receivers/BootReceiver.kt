package io.github.zhangjq.bcl.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.topjohnwu.superuser.Shell
import io.github.zhangjq.bcl.Constants
import io.github.zhangjq.bcl.Utils

/**
 * Created by Michael on 20.04.2017.
 *
 * Triggered when the phone finished booting or the app was updated.
 * Checks whether power supply is attached and starts the foreground service if necessary.
 * Also restarts the auto shutdown service if the feature is enabled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action
            || Intent.ACTION_MY_PACKAGE_REPLACED == intent.action
        ) {
            Utils.setVoltageThreshold(null, true, context, null)
            Utils.startServiceIfLimitEnabled(context)
            Utils.startAutoShutdownServiceIfEnabled(context)
            Shell.cmd("cat ${Utils.getVoltageFile()}").submit {
                if (it.out.size != 0) {
                    Utils.getSettings(context).edit().putString(Constants.DEFAULT_VOLTAGE_LIMIT, it.out[0]).apply()
                }
            }
        }
    }
}
