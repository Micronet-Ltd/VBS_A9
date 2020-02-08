/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package com.micronet.dsc.vbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * VehicleBusAlarmReceiver receives broadcasts for VBS service to start.
 */
public class VehicleBusAlarmReceiver extends BroadcastReceiver {
    public static final String TAG = "ATS-VBS-AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(VehicleBusConstants.SERVICE_ACTION_START)) {
            Log.d(TAG, "Restart Service Alarm");

            // Send intent to service (and start if needed)
            Intent i = new Intent(context, VehicleBusService.class);
            i.setAction(VehicleBusConstants.SERVICE_ACTION_START);
            context.startForegroundService(i);
        }
    }
}
