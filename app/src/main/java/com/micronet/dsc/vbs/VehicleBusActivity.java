package com.micronet.dsc.vbs;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

public class VehicleBusActivity extends Activity {

    public static final String TAG = "VBS-Activity";
    public static final int PERMISSIONS_REQUEST_CODE = 384020;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (permissions.length > 0 &&
                    permissions[0].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                finishSetup();
            } else {
                Log.e(TAG, "Permissions not granted by user. Not starting VBS. Exiting.");
                Toast.makeText(getApplicationContext(), "Permissions not granted. Not starting VBS service.", Toast.LENGTH_SHORT).show();
                // Set to false so permission request will pop up again.
                VehicleBusService.sentPermissionRequest = false;
                finish();
            }
        }
    }

    public void finishSetup() {
        Context context = getApplicationContext();
        Intent i = getIntent();
        i.setClass(context, VehicleBusService.class);
        Log.d(TAG, "Intent action: " + i.getAction());
        context.startForegroundService(i);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
