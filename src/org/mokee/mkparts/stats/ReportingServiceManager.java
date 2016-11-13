/*
 * Copyright (C) 2014-2016 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.mkparts.stats;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.mokee.utils.MoKeeUtils;
import android.os.UserHandle;
import android.util.Log;

public class ReportingServiceManager extends BroadcastReceiver {

    public static final String ACTION_LAUNCH_SERVICE =
            "org.mokee.mkparts.action.TRIGGER_REPORT_METRICS";

    protected static final String ANONYMOUS_PREF = "mokee_stats";

    protected static final String ANONYMOUS_FLASH_TIME = "pref_anonymous_flash_time";

    protected static final String ANONYMOUS_LAST_CHECKED = "pref_anonymous_checked_in";

    protected static final String ANONYMOUS_VERSION_CODE = "pref_anonymous_version_code";

    private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;
    private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;
    private static final long UPDATE_INTERVAL = 1L * MILLIS_PER_DAY;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            setAlarm(ctx, 0);
        } else {
            launchService(ctx);
        }
    }

    public static void setAlarm(Context ctx, long millisFromNow) {
        SharedPreferences prefs = ctx.getSharedPreferences(ANONYMOUS_PREF, 0);

        if (millisFromNow <= 0) {
            long lastSynced = prefs.getLong(ANONYMOUS_LAST_CHECKED, 0);
            if (lastSynced == 0) {
                launchService(ctx);
                return;
            }
            millisFromNow = (lastSynced + UPDATE_INTERVAL) - System.currentTimeMillis();
        }

        Intent intent = new Intent(ACTION_LAUNCH_SERVICE);
        intent.setClass(ctx, ReportingServiceManager.class);

        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millisFromNow,
            PendingIntent.getBroadcast(ctx, 0, intent, 0));
        Log.d(Utilities.TAG, "Next sync attempt in : " + millisFromNow / MILLIS_PER_HOUR + " hours");
    }

    public static void launchService(Context ctx) {
        if (MoKeeUtils.isOnline(ctx)) {
            final SharedPreferences prefs = ctx.getSharedPreferences(ANONYMOUS_PREF, 0);
            long lastSynced = prefs.getLong(ANONYMOUS_LAST_CHECKED, 0);
            String versionCode = Utilities.getVersionCode();
            String prefVersionCode = prefs.getString(ANONYMOUS_VERSION_CODE, versionCode);

            boolean shouldSync = false;
            if (lastSynced == 0) {
                shouldSync = true;
            } else if (System.currentTimeMillis() - lastSynced >= UPDATE_INTERVAL) {
                shouldSync = true;
            } else if (!versionCode.equals(prefVersionCode)) {
                shouldSync = true;
            }
            if (shouldSync) {
                Intent sIntent = new Intent();
                if (prefs.getLong(ANONYMOUS_FLASH_TIME, 0) == 0) {
                    sIntent.setClass(ctx, ReportingService.class);
                    ctx.startServiceAsUser(sIntent, UserHandle.OWNER);
                } else {
                    sIntent.setClass(ctx, UpdatingService.class);
                    ctx.startServiceAsUser(sIntent, UserHandle.OWNER);
                }
            } else {
                setAlarm(ctx, 0);
            }
        }
    }
}
