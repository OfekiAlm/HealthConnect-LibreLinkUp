/*
 * Copyright (c) 2024 Sam Steele
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.c99.healthconnect_librelinkup;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lightweight, non-sensitive store for the most recent sync pipeline state so the UI can surface
 * success/failure information to the user. This intentionally avoids storing any personal health
 * data (PHI) or credentials; only timestamps and short, non-identifying status strings are kept.
 */
public final class SyncStatusStore {
    public static final String PREFS_NAME = "sync_state";

    public static final String KEY_LAST_ATTEMPT_AT = "last_attempt_at";
    public static final String KEY_LAST_SUCCESS_AT = "last_success_at";
    public static final String KEY_LAST_ERROR = "last_error";
    public static final String KEY_IS_SYNCING = "is_syncing";
    public static final String KEY_LOGIN_STATUS = "login_status";

    public static final String LOGIN_STATUS_OK = "ok";
    public static final String LOGIN_STATUS_EXPIRED = "expired";
    public static final String LOGIN_STATUS_NONE = "none";

    private SyncStatusStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void recordAttempt(Context context) {
        prefs(context).edit()
                .putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
                .putBoolean(KEY_IS_SYNCING, true)
                .apply();
    }

    public static void recordSuccess(Context context) {
        prefs(context).edit()
                .putLong(KEY_LAST_SUCCESS_AT, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .putBoolean(KEY_IS_SYNCING, false)
                .putString(KEY_LOGIN_STATUS, LOGIN_STATUS_OK)
                .apply();
    }

    public static void recordError(Context context, String message) {
        prefs(context).edit()
                .putString(KEY_LAST_ERROR, message == null ? "Unknown error" : message)
                .putBoolean(KEY_IS_SYNCING, false)
                .apply();
    }

    public static void recordLoginStatus(Context context, String status) {
        prefs(context).edit().putString(KEY_LOGIN_STATUS, status).apply();
    }

    public static long getLastAttemptAt(Context context) {
        return prefs(context).getLong(KEY_LAST_ATTEMPT_AT, 0);
    }

    public static long getLastSuccessAt(Context context) {
        return prefs(context).getLong(KEY_LAST_SUCCESS_AT, 0);
    }

    public static String getLastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, null);
    }

    public static String getLoginStatus(Context context) {
        return prefs(context).getString(KEY_LOGIN_STATUS, LOGIN_STATUS_NONE);
    }
}
