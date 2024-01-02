/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package com.micronet.dsc.vbs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

/**
 * State contains saved state information flags.
 * <p>
 * For example, flags that have long reset periods or cannot just be figured out again soon after reboot.
 */
public class State {
    private static final String TAG = "ATS-VBS-State";
    private static final String FILENAMEKEY = "state";

    // I/O
    public static final int FLAG_CAN_ON = 201;
    public static final int FLAG_CAN_LISTENONLY = 202; // deprecated in favor of AUTODETECT
    public static final int CAN_BITRATE = 203;
    public static final int CAN_FILTER_IDS = 204;
    public static final int CAN_FILTER_MASKS = 205;
    public static final int FLAG_CAN_AUTODETECT = 206;
    public static final int CAN_CONFIRMED_BITRATE = 207;    // prior bitrate that was confirmed (so we don't need listen only)
    public static final int CAN_CONFIRMED_NUMBER = 208;
    public static final int CAN_NUMBER = 209;
    public static final int CAN_FLOW_CONTROLS = 211;

    Context context;
    SharedPreferences sharedPref;
    Gson gson;

    public State(Context context) {
        this.context = context;
        sharedPref = context.getSharedPreferences(FILENAMEKEY, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * Deletes all state settings and restores factory default.
     */
    public void clearAll() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.clear().commit();
    }

    /**
     * Writes an int value for the given state setting. Returns true if successful, else false.
     */
    public boolean writeState(final int state_id, final int new_value) {
        try {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(Integer.toString(state_id), new_value);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: writeState() " + e.toString(), e);
        }
        return true;
    }

    /**
     * Writes an byte array for the given state setting. Returns true if successful, else false.
     */
    public boolean writeStateArray(final int state_id, final byte[] new_value) {
        try {
            SharedPreferences.Editor editor = sharedPref.edit();
            String newString;

            newString = Log.bytesToHex(new_value, new_value.length);
            editor.putString(Integer.toString(state_id), newString);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: writeStateArray() " + e.toString(), e);
        }
        return true;
    }

    /**
     * Writes an long value for the given state setting. Returns true if successful, else false.
     */
    public boolean writeStateLong(final int state_id, final long new_value) {
        try {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putLong(Integer.toString(state_id), new_value);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: writeStateLong() " + e.toString(), e);
        }

        return true;
    }

    /**
     * Writes an String value for the given state setting. Returns true if successful, else false.
     */
    public boolean writeStateString(final int state_id, final String new_value) {
        try {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(Integer.toString(state_id), new_value);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: writeStateString() " + e.toString(), e);
        }

        return true;
    }

    /**
     * Writes a Flow Control Array for the given state setting. Returns true if successful, else false.
     */
    public boolean writeStateFlowControls(final ArrayList<VehicleBusHW.CANFlowControl> flowControls) {
        try {
            SharedPreferences.Editor editor = sharedPref.edit();
            if (flowControls == null) {
                editor.putString(Integer.toString(CAN_FLOW_CONTROLS), "");
            } else {
                String objStr = gson.toJson(flowControls);
                editor.putString(Integer.toString(CAN_FLOW_CONTROLS), objStr);
            }

            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Exception: writeStateFlowControls() " + e.toString(), e);
            return false;
        }

        return true;
    }

    /**
     * Returns an String value for the given state. If state doesn't exist, returns "".
     */
    public ArrayList<VehicleBusHW.CANFlowControl> readStateFlowControls() {
        String jsonStr = sharedPref.getString(Integer.toString(CAN_FLOW_CONTROLS), "");

        if (!TextUtils.isEmpty(jsonStr)) {
            return gson.fromJson(jsonStr, new TypeToken<List<VehicleBusHW.CANFlowControl>>(){}.getType());
        } else {
            return null;
        }
    }

    /**
     * Returns an int value for the given state. If state doesn't exist, returns 0.
     */
    public int readState(int state_id) {
        return sharedPref.getInt(Integer.toString(state_id), 0);
    }

    /**
     * Returns an long value for the given state. If state doesn't exist, returns 0.
     */
    public long readStateLong(int state_id) {
        return sharedPref.getLong(Integer.toString(state_id), 0);
    }

    /**
     * Returns an String value for the given state. If state doesn't exist, returns "".
     */
    public String readStateString(int state_id) {
        return sharedPref.getString(Integer.toString(state_id), "");
    }

    /**
     * Returns an bool value for the given state. If state doesn't exist, returns false.
     */
    public boolean readStateBool(int state_id) {
        int value = sharedPref.getInt(Integer.toString(state_id), 0);
        if (value == 0) return false;
        return true;
    }

    /**
     * Returns an byte array for the given state. If state doesn't exist, returns null.
     */
    public byte[] readStateArray(int state_id) {
        String value = sharedPref.getString(Integer.toString(state_id), "");
        if (value.isEmpty()) return null;

        byte[] array = Log.hexToBytes(value);

        return array;
    }
}
