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

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Receives "sync now" requests sent from the watch (for example when the user taps the glucose
 * complication or tile) and enqueues an immediate one-time sync on the phone.
 */
public class PhoneSyncRequestListenerService extends WearableListenerService {
    private static final String TAG = "PhoneSyncListener";
    public static final String PATH_SYNC_NOW = "/sync_now";

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        if (PATH_SYNC_NOW.equals(messageEvent.getPath())) {
            Log.i(TAG, "Received sync-now request from wearable");
            LibreLinkUp.syncNow(getApplicationContext());
        }
    }
}
