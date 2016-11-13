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

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.mokee.os.Build;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

public class UpdatingService extends Service {

    private StatsUpdateTask mTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        Log.d(Utilities.TAG, "User has opted in -- updating.");

        if (mTask == null || mTask.getStatus() == AsyncTask.Status.FINISHED) {
            mTask = new StatsUpdateTask();
            mTask.execute();
        }

        return Service.START_REDELIVER_INTENT;
    }

    private class StatsUpdateTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            final Context context = UpdatingService.this;
            String deviceId = Build.getUniqueID(context);
            String deviceVersion = Build.VERSION;
            String deviceFlashTime = String.valueOf(getSharedPreferences(ReportingServiceManager.ANONYMOUS_PREF, 0).getLong(ReportingServiceManager.ANONYMOUS_FLASH_TIME, 0));

            Log.d(Utilities.TAG, "SERVICE: Device ID=" + deviceId);
            Log.d(Utilities.TAG, "SERVICE: Device Version=" + deviceVersion);
            Log.d(Utilities.TAG, "SERVICE: Device Flash Time=" + deviceFlashTime);

            // update to the mkstats service
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost("http://stats.mokeedev.com/index.php/Submit/updatev2");
            boolean success = false;

            try {
                List<NameValuePair> kv = new ArrayList<NameValuePair>(1);
                kv.add(new BasicNameValuePair("device_hash", deviceId));
                kv.add(new BasicNameValuePair("device_version", deviceVersion));
                kv.add(new BasicNameValuePair("device_flash_time", deviceFlashTime));
                httpPost.setEntity(new UrlEncodedFormEntity(kv));
                httpClient.execute(httpPost);

                success = true;
            } catch (Exception e) {
                Log.e(Utilities.TAG, "Could not update stats checkin", e);
            }

            return success;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            final Context context = UpdatingService.this;
            long interval;

            if (result) {
                String versionCode = Utilities.getVersionCode();
                final SharedPreferences prefs = getSharedPreferences(ReportingServiceManager.ANONYMOUS_PREF, 0);
                prefs.edit().putLong(ReportingServiceManager.ANONYMOUS_LAST_CHECKED,
                        System.currentTimeMillis()).putString(ReportingServiceManager.ANONYMOUS_VERSION_CODE, versionCode).apply();
                // use set interval
                interval = 0;
            } else {
                // error, try again in 3 hours
                interval = 3L * 60L * 60L * 1000L;
            }

            ReportingServiceManager.setAlarm(context, interval);
            stopSelf();
        }
    }
}
