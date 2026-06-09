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

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Centralised, user-visible notifications for the sync pipeline. All messages are convenience
 * status notifications only and never contain glucose values or other personal health data.
 */
public final class Notifications {
    public static final String CHANNEL_ID = "sync_status";
    public static final int ID_SYNC_FAILURE = 1001;
    public static final int ID_LOGIN_EXPIRED = 1002;

    private Notifications() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sync status",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Informational alerts about glucose sync status");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void notify(Context context, int id, String title, String text) {
        ensureChannel(context);
        if (!canPostNotifications(context)) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    public static void notifySyncFailure(Context context, String reason) {
        String text = reason == null || reason.isEmpty()
                ? "Glucose sync failed. Tap to open the app and check your connection."
                : "Glucose sync failed: " + reason + ". Tap to open the app.";
        notify(context, ID_SYNC_FAILURE, "Glucose sync failed", text);
    }

    public static void notifyLoginExpired(Context context) {
        notify(context, ID_LOGIN_EXPIRED, "LibreLinkUp login expired",
                "Please open the app and sign in again to keep syncing glucose readings.");
    }

    public static void clearLoginExpired(Context context) {
        NotificationManagerCompat.from(context).cancel(ID_LOGIN_EXPIRED);
    }

    public static void clearSyncFailure(Context context) {
        NotificationManagerCompat.from(context).cancel(ID_SYNC_FAILURE);
    }
}
