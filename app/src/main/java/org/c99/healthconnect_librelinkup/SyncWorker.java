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
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.BloodGlucoseRecord;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.response.InsertRecordsResponse;
import androidx.health.connect.client.units.BloodGlucose;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.AvailabilityException;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private static final int MAX_RETRIES = 3;

    private final LibreLinkUp libreLinkUp;
    private final HealthConnectClient healthConnectClient;

    private final String GLUCOSE_KEY = "org.c99.healthconnect_librelinkup.glucose";
    private final String TREND_ARROW_KEY = "org.c99.healthconnect_librelinkup.trendArrow";
    private final String COLOR_KEY = "org.c99.healthconnect_librelinkup.color";
    private final String UNITS_KEY = "org.c99.healthconnect_librelinkup.units";
    private final String TIMESTAMP_KEY = "org.c99.healthconnect_librelinkup.timestamp";

    public SyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        libreLinkUp = new LibreLinkUp(context);
        healthConnectClient = HealthConnectClient.getOrCreate(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SyncStatusStore.recordAttempt(context);
        Log.i(TAG, "Starting glucose sync (attempt " + (getRunAttemptCount() + 1) + ")");

        LibreLinkUp.ConnectionsResult result;
        try {
            result = libreLinkUp.connections();
        } catch (IOException e) {
            // Network/transport failure: transient, so retry with backoff before giving up.
            Log.w(TAG, "Network error while fetching connections: " + e.getClass().getSimpleName());
            return failOrRetry(context, "network error", false);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while fetching connections: " + e.getClass().getSimpleName());
            return failOrRetry(context, "unexpected error", false);
        }

        // Detect an expired/invalid session. The connections endpoint returns a non-zero status or an
        // error payload when the auth token is no longer valid.
        if (result == null || result.status != 0 || result.data == null || result.data.isEmpty()) {
            String message = (result != null && result.error != null && result.error.message != null)
                    ? result.error.message
                    : "no data";
            Log.w(TAG, "Connections request returned no usable data (status="
                    + (result == null ? "null" : result.status) + ")");
            boolean looksLikeAuth = result == null || result.status != 0;
            return failOrRetry(context, message, looksLikeAuth);
        }

        // Only refresh the stored auth ticket when the server actually returned a new, valid one.
        // Previously this was assigned unconditionally, which wiped a valid token whenever the
        // response omitted a ticket and broke every subsequent sync.
        if (result.ticket != null && result.ticket.token != null && !result.ticket.token.isEmpty()) {
            libreLinkUp.setAuthTicket(result.ticket);
        }

        try {
            LibreLinkUp.GlucoseMeasurement gm = result.data.get(0).glucoseMeasurement;
            if (gm == null) {
                Log.w(TAG, "Connection contained no glucose measurement");
                return failOrRetry(context, "no glucose reading available", false);
            }

            writeToHealthConnect(gm);
            sendToWearable(result.data.get(0));

            SyncStatusStore.recordSuccess(context);
            Notifications.clearSyncFailure(context);
            Notifications.clearLoginExpired(context);
            Log.i(TAG, "Glucose sync completed successfully");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Failed to process glucose reading: " + e.getClass().getSimpleName());
            return failOrRetry(context, "could not process reading", false);
        }
    }

    /**
     * Records the failure, notifies the user when appropriate, and decides whether to retry. Auth
     * failures are not retried (the credentials must be refreshed by the user first); transient
     * failures are retried with WorkManager's exponential backoff up to {@link #MAX_RETRIES}.
     */
    private Result failOrRetry(Context context, String reason, boolean authFailure) {
        SyncStatusStore.recordError(context, reason);

        if (authFailure) {
            SyncStatusStore.recordLoginStatus(context, SyncStatusStore.LOGIN_STATUS_EXPIRED);
            Notifications.notifyLoginExpired(context);
            return Result.failure();
        }

        if (getRunAttemptCount() < MAX_RETRIES) {
            Log.i(TAG, "Scheduling retry with backoff (" + (getRunAttemptCount() + 1) + "/" + MAX_RETRIES + ")");
            return Result.retry();
        }

        Notifications.notifySyncFailure(context, reason);
        return Result.failure();
    }

    private void writeToHealthConnect(LibreLinkUp.GlucoseMeasurement gm) {
        ZonedDateTime time;
        if (gm.FactoryTimestamp != null) {
            try {
                // Attempt to parse as a datetime string
                time = ZonedDateTime.parse(gm.FactoryTimestamp + " +0000", DateTimeFormatter.ofPattern("M/d/y h:m:s a Z"));
            } catch (DateTimeParseException e) {
                // If parsing fails, assume it's a long timestamp
                try {
                    long timestampMillis = Long.parseLong(gm.FactoryTimestamp);
                    time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault());
                } catch (NumberFormatException nfe) {
                    time = ZonedDateTime.now(); // Fallback to current time
                }
            }
        } else {
            time = ZonedDateTime.now(); // Fallback to current time
        }

        BloodGlucoseRecord r = new BloodGlucoseRecord(
                Instant.from(time),
                time.getOffset(),
                BloodGlucose.milligramsPerDeciliter(gm.ValueInMgPerDl),
                BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                0,
                BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                new Metadata("", new DataOrigin(getApplicationContext().getPackageName()), Instant.from(time), null, 0, null, 0)
        );
        healthConnectClient.insertRecords(Collections.singletonList(r), new Continuation<InsertRecordsResponse>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {
            }
        });
    }

    private void sendToWearable(LibreLinkUp.Connection connection) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext())
                != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            return;
        }
        try {
            DataClient dc = Wearable.getDataClient(getApplicationContext());
            Task<Void> t = GoogleApiAvailability.getInstance().checkApiAvailability(dc);
            Tasks.await(t);

            LibreLinkUp.GlucoseMeasurement gm = connection.glucoseMeasurement;
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/glucose");
            putDataMapReq.getDataMap().putFloat(GLUCOSE_KEY, gm.Value);
            putDataMapReq.getDataMap().putInt(COLOR_KEY, gm.MeasurementColor);
            putDataMapReq.getDataMap().putInt(TREND_ARROW_KEY, gm.TrendArrow);
            putDataMapReq.getDataMap().putInt(UNITS_KEY, gm.GlucoseUnits);
            putDataMapReq.getDataMap().putString(TIMESTAMP_KEY, gm.FactoryTimestamp);
            // Force the data item to change so the watch always receives an update, even when the
            // glucose value happens to match the previous reading.
            putDataMapReq.getDataMap().putLong("org.c99.healthconnect_librelinkup.syncedAt", System.currentTimeMillis());
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            Tasks.await(dc.putDataItem(putDataReq));
            Log.i(TAG, "Pushed latest reading to wearable");
        } catch (Exception e) {
            // Wearable API not available; this is non-fatal for phone syncing.
            Log.w(TAG, "Wearable sync skipped: " + e.getClass().getSimpleName());
        }
    }
}
