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

package org.c99.healthconnect_librelinkup.complication

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.c99.healthconnect_librelinkup.DataLayerListenerService
import org.c99.healthconnect_librelinkup.R
import org.c99.healthconnect_librelinkup.RefreshReceiver

class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData(Icon.createWithResource(this, R.drawable.water_drop), "99")
    }

    @SuppressLint("DefaultLocale")
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        try {
            val glucose = getSharedPreferences("glucose", MODE_PRIVATE)
            if (glucose != null && glucose.contains(DataLayerListenerService.GLUCOSE_KEY)) {
                val icon = when (glucose.getInt(DataLayerListenerService.TREND_ARROW_KEY, -1)) {
                    1 -> Icon.createWithResource(this, R.drawable.arrow_down)
                    2 -> Icon.createWithResource(this, R.drawable.arrow_down_right)
                    3 -> Icon.createWithResource(this, R.drawable.arrow_right)
                    4 -> Icon.createWithResource(this, R.drawable.arrow_up_right)
                    5 -> Icon.createWithResource(this, R.drawable.arrow_up)
                    else -> Icon.createWithResource(this, R.drawable.water_drop)
                }

                val text = if (glucose.getInt(DataLayerListenerService.UNITS_KEY, 1) == 1) {
                    String.format("%.0f", glucose.getFloat(DataLayerListenerService.GLUCOSE_KEY, 0f))
                } else {
                    String.format("%.1f", glucose.getFloat(DataLayerListenerService.GLUCOSE_KEY, 0f))
                }
                return createComplicationData(icon, text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Never render blank: show a clear "no data" placeholder that the user can tap to refresh.
        return createComplicationData(
            Icon.createWithResource(this, R.drawable.water_drop),
            "--",
            "No glucose data yet"
        )
    }

    private fun refreshTapAction(): PendingIntent {
        val intent = Intent(this, RefreshReceiver::class.java).apply {
            action = RefreshReceiver.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createComplicationData(icon: Icon, glucose: String, description: String = glucose) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(glucose).build(),
            contentDescription = PlainComplicationText.Builder(description).build()
        )
        .setMonochromaticImage(MonochromaticImage.Builder(image = icon).build())
        .setTapAction(refreshTapAction())
        .build()
}