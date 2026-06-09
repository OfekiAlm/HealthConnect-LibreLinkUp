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

package org.c99.healthconnect_librelinkup

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.Wearable
import org.c99.healthconnect_librelinkup.complication.GlucoseComplicationService
import org.c99.healthconnect_librelinkup.tile.GlucoseTileService

/**
 * Handles "refresh now" taps coming from the watch complication or tile. It asks the phone to run an
 * immediate sync (via the data layer) and refreshes the local tile and complication so the user gets
 * visible feedback even before new data arrives.
 */
class RefreshReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REFRESH = "org.c99.healthconnect_librelinkup.action.REFRESH"
        private const val PATH_SYNC_NOW = "/sync_now"
        private const val TAG = "RefreshReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()

        // Request the phone companion to sync immediately.
        Wearable.getNodeClient(appContext).connectedNodes
            .addOnSuccessListener { nodes ->
                val messageClient = Wearable.getMessageClient(appContext)
                for (node in nodes) {
                    messageClient.sendMessage(node.id, PATH_SYNC_NOW, ByteArray(0))
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Could not reach phone for sync: ${e.javaClass.simpleName}")
            }
            .addOnCompleteListener {
                pending.finish()
            }

        // Refresh local surfaces for immediate visual feedback.
        try {
            ComplicationDataSourceUpdateRequester.create(
                appContext,
                ComponentName(appContext, GlucoseComplicationService::class.java)
            ).requestUpdateAll()
            TileService.getUpdater(appContext).requestUpdate(GlucoseTileService::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh tile/complication: ${e.javaClass.simpleName}")
        }
    }
}
