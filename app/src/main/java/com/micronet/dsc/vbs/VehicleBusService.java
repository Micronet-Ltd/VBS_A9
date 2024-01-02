/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

/////////////////////////////////////////////////////////////
// VBusService:
//  Handles communications with hardware regarding CAN,
//  can be started in a separate process (since interactions with the API are prone to jam or crash -- and take down the whole process when they do)
//
//  See VehicleBusConstants file for a list of actions and extras for this service
//
/////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class VehicleBusService extends Service {

    public static final String TAG = "ATS-VBS";
    public static final int BROADCAST_STATUS_DELAY_MS = 1000; // every 1 s

    private static final int NOTFICATION_ID = 444444;
    private static final String APP_NAME = "VBS";
    private static final String VBS_CHANNEL_DESCRIPTION = "VBS description channel.";
    private static final String VBS_NOTIFICATION_DESCRIPTION = "is starting...";
    private static final String CHANNEL_ID = "com.micronet.dsc.vbs_notifications";
    private static final String CAN_LABEL = "CAN";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;

    static int processId = 0;
    static int CAN_NUMBER = 0;

    Handler mainHandler = null;
    VehicleBusCAN my_can;

    boolean hasStartedCAN = false;
    boolean isUnitTesting = false;

    static VehicleBusService service = null;

    public static boolean sentPermissionRequest = false;

    public VehicleBusService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Service Created: VBS device=SC600" + " version=" + BuildConfig.VERSION_NAME);
        processId = android.os.Process.myPid();
        mainHandler  = new Handler();
        service = this;
    }

    private boolean arePermissionsGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Check to make sure config file can be read.
        if (!arePermissionsGranted() && !sentPermissionRequest) {
            // Start activity to request for permissions.
            // This should only happen once per install.
            sentPermissionRequest = true;
            intent.setClass(this, VehicleBusActivity.class);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Sending request to check for permissions.");
            stopSelf();
            return START_NOT_STICKY;
        } else if (!arePermissionsGranted()) {
            Log.d(TAG, "Permissions are still waiting to be granted.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // If intent is null, then load from file.
        if (intent == null) {
            if (!startFromFile()) return START_NOT_STICKY;
            return START_NOT_STICKY;
        }

        // Check if action is null.
        String action = intent.getAction();
        if (action == null) {
            Log.e(TAG, "Error, an action is required for service.");
            return START_NOT_STICKY;
        }

        // If action is to restart then load from file.
        if (action.equals(VehicleBusConstants.SERVICE_ACTION_RESTART)) { // TODO Errors here with ATS in some cases.
            Log.i(TAG, "Vehicle Bus Service Restarting");
            setForeground(); // TODO do we need this here? Does it cause issues having it here?
            if (!startFromFile()) return START_NOT_STICKY;
            return START_NOT_STICKY;
        }

        // Check if bus is null.
        String bus = intent.getStringExtra(VehicleBusConstants.SERVICE_EXTRA_BUS);
        if (bus == null) {
            Log.e(TAG, "Error, cannot start/stop service. Must designate bus extra.");
            return START_NOT_STICKY;
        }

        if (action.equals(VehicleBusConstants.SERVICE_ACTION_START)) {
            Log.i(TAG, "Vehicle Bus Service Starting: " + bus);
            setForeground();

            if (bus.equals(CAN_LABEL)) {
                // Get all extras from broadcast.
                int bitrate = intent.getIntExtra(VehicleBusConstants.SERVICE_EXTRA_BITRATE, VehicleBusCAN.DEFAULT_BITRATE);
                boolean auto_detect = intent.getBooleanExtra(VehicleBusConstants.SERVICE_EXTRA_AUTODETECT, false);
                boolean skip_verify = intent.getBooleanExtra(VehicleBusConstants.SERVICE_EXTRA_SKIPVERIFY, false);
                boolean flowControl = intent.getBooleanExtra(VehicleBusConstants.SERVICE_EXTRA_FLOW_CONTROL, false);
                int canNumber = intent.getIntExtra(VehicleBusConstants.SERVICE_EXTRA_CAN_NUMBER, VehicleBusCAN.DEFAULT_CAN_NUMBER);
                int[] ids = intent.getIntArrayExtra(VehicleBusConstants.SERVICE_EXTRA_HARDWAREFILTER_IDS);
                int[] masks = intent.getIntArrayExtra(VehicleBusConstants.SERVICE_EXTRA_HARDWAREFILTER_MASKS);

                CAN_NUMBER = canNumber; // Setting the CAN_NUMBER to match the canNumber, this is used for other classes
                Log.d(TAG, "CAN_NUMBER = " + CAN_NUMBER);

                ArrayList<VehicleBusHW.CANFlowControl> flowControls = null;
                if (flowControl) {
                    flowControls = Config.getFlowControls(canNumber);
                    if (flowControls != null) {
                        Log.d(TAG, "Using flow controls from configuration.xml.");
                    } else {
                        Log.d(TAG, "Flow controls are null.");
                    }
                }

                // Remember Canbus settings.
                saveCAN(true, bitrate, auto_detect, ids, masks, canNumber, flowControls);

                // Start Canbus.
                stopCAN(false);
                startCAN(bitrate, skip_verify, auto_detect, ids, masks, canNumber,false, flowControls);
            }
        } else if (action.equals(VehicleBusConstants.SERVICE_ACTION_STOP)) {
            Log.i(TAG, "Vehicle Bus Service Stopped: " + bus);

            if (bus.equals(CAN_LABEL)) {

                saveCAN(false, 0, false, null, null, 0, null); // Todo: addCanBus. Ask about this, do I need anything else to tell the service to close canPort?
                setBackground();
                stopSelf(); // nothing on, stop everything and exit
            }
        }

        return START_NOT_STICKY; // since this is being monitored elsewhere and since we can't start_sticky (null intent)
        // rely on something else to restart us when down.
    } // OnStartCommand()

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed

        // OnDestroy() is NOT guaranteed to be called, and won't be for instance
        //  if the app is updated
        //  if ths system needs RAM quickly
        //  if the user force-stops the application

        Log.v(TAG, "Destroying Service");
        stopCAN(false);
    } // OnDestroy()

    /**
     * Start up the buses based on saved state information.
     * @return true if this was successful, false if there was a problem.
     */
    boolean startFromFile() {
        Log.i(TAG, "Vehicle Bus Service Starting: (From Saved File)" );

        State state = new State(getApplicationContext());
        boolean enCan = state.readStateBool(State.FLAG_CAN_ON) ;

        // Check if both engines are off.
        if (!enCan) {
            Log.i(TAG, "All buses are set to off. stopping service");
            stopSelf(); // nothing on, stop everything and exit
        }

        // first, try to stop all running buses at the same time
        stopAll();

        // Enable Canbus.
        if (enCan) {
            // Read all saved state configurations of port.
            int bitrate = state.readState(State.CAN_BITRATE);
            if (bitrate == 0) bitrate = VehicleBusCAN.DEFAULT_BITRATE;
            boolean auto_detect = state.readStateBool(State.FLAG_CAN_AUTODETECT);
            int canNumber = state.readState(State.CAN_NUMBER);
            String idstring = state.readStateString(State.CAN_FILTER_IDS);
            String maskstring = state.readStateString(State.CAN_FILTER_MASKS);

            String[] idsplits = idstring.split(",");
            String[] masksplits = maskstring.split(",");

            int[] ids = new int[idsplits.length];
            int[] masks = new int[masksplits.length];
            for (int i=0; i < idsplits.length && i < masksplits.length; i++) {
                try {
                    ids[i] = Integer.parseInt(idsplits[i]);
                    masks[i] = Integer.parseInt(masksplits[i]);
                } catch (Exception e) {
                    Log.e(TAG, "CAN Masks or IDs are not a number! Aborting start.");
                    return false; // we can't start, not sure what to do, we don't want to start without any filters
                }
            }

            ArrayList<VehicleBusHW.CANFlowControl> flowControls = state.readStateFlowControls();

            startCAN(bitrate, false, auto_detect, ids, masks, canNumber,true, flowControls);
        }

        return true;
    } // startFromFile()

    ////////////////////////////////////////////////////////////////
    // stopAll()
    //  stop the CAN bus if running
    ////////////////////////////////////////////////////////////////
    void stopAll() {
        // call the underlying wrapper's stopAll()

        // my_can is only used to provide us access to the underlying wrapper stopAll() call
        // make sure we can use either one, because one or the other might be null if that bus is not running.

        if (my_can != null) {
            my_can.stopAll();
        }

        // stop all buses
        stopCAN(true);
    } //stopAll()

    ////////////////////////////////////////////////////////////////
    // saveCAN()
    // save CAN information to file so we can load it up on restart.
    ////////////////////////////////////////////////////////////////
    void saveCAN(boolean enabled, int bitrate, boolean auto_detect, int[] ids, int masks[], int canNumber, ArrayList<VehicleBusHW.CANFlowControl> flowControls) {
        Context context = getApplicationContext();
        State state = new State(context);

        state.writeState(State.FLAG_CAN_ON, ( enabled ?  1 : 0));
        if (enabled) {
            // Save more info about the CAN.
            state.writeState(State.CAN_BITRATE, bitrate);
            state.writeState(State.FLAG_CAN_AUTODETECT, (auto_detect ? 1 : 0));
            state.writeState(State.CAN_NUMBER, canNumber);

            String idstring = "";
            String maskstring = "";
            if ((ids != null) && (masks != null)) {
                for (int id : ids) {
                    if (!idstring.isEmpty()) {
                        idstring += ',';
                    }
                    idstring += id;
                }

                for (int mask : masks) {
                    if (!maskstring.isEmpty()) {
                        maskstring += ',';
                    }
                    maskstring += mask;
                }
            }
            state.writeStateString(State.CAN_FILTER_IDS, idstring);
            state.writeStateString(State.CAN_FILTER_MASKS, maskstring);
            state.writeStateFlowControls(flowControls);
        }
    }

    ////////////////////////////////////////////////////////////////
    // startCAN()
    //  Start up the CAN bus with the given parameters
    //  bitrate: the starting bitrate, e.g. 250000 or 500000
    //  auto_detect: if true then will switch between bitrates until one is detected
    //  skip_verify: if true then do not start in listen-only mode
    //  ids[] : CAN IDs to use for filters
    //  masks[] : corresponding can masks to use with the ids
    //  load_last_confirmed: whether or not we should load the last confirmed bitrate from file
    //      when service receives the "restart" action, then we will load this from file, otherwise we only use what is in memory
    ////////////////////////////////////////////////////////////////
    void startCAN(int bitrate, boolean skip_verify, boolean auto_detect, int[] ids, int masks[], int canNumber, boolean load_last_confirmed, ArrayList<VehicleBusHW.CANFlowControl> flowControls) {
        Log.d(TAG, "+startCAN():");

        if (hasStartedCAN) {
            Log.w(TAG, "CAN already started. Ignoring subsequent Start.");
        }

        hasStartedCAN = true; // don't start again
        Context context = getApplicationContext();

        // create the combined filters
        VehicleBusWrapper.CANHardwareFilter[] canHardwareFilters = createCombinedFilters(ids, masks);


        my_can = new VehicleBusCAN(context, isUnitTesting);

        if (load_last_confirmed) {
            my_can.loadConfirmedBitRate();
            my_can.loadConfirmedCanNumber();
        }

        if (skip_verify) {
            // we've requested to treat this bitrate as confirmed
            my_can.setConfirmedBitRate(bitrate);
            my_can.setConfirmedCanNumber(canNumber);
        }

        if (!my_can.start(bitrate, auto_detect, canHardwareFilters, canNumber, flowControls)) {
            Log.e(TAG, "Error starting bus with bus wrapper.");
            return;
        }

        if (mainHandler != null) {
            Log.d(TAG, "Starting status broadcasts");
            mainHandler.postDelayed(statusTask, BROADCAST_STATUS_DELAY_MS); // broadcast a status every 1s
        }

        Log.d(TAG, "-startCAN():");
    } // startCAN()

    ////////////////////////////////////////////////////////////////
    // stopCAN()
    //  stop the CAN bus
    ////////////////////////////////////////////////////////////////
    void stopCAN(boolean show_error) {


        if (!hasStartedCAN) {
            if (show_error) {
                Log.w(TAG, "CAN not started. Ignoring Stop.");
            }
            return;
        }



        // remove callbacks first in case the stop() jams
        mainHandler.removeCallbacks(statusTask);


        if (my_can != null) {
            my_can.stop();
        }

        hasStartedCAN = false;

    } // stopCAN()

    void forceStopCAN() {

        // remove callbacks first in case the stop() jams
        mainHandler.removeCallbacks(statusTask);

        if (my_can != null) {
            my_can.stop();
        }

        hasStartedCAN = false;

    } // forceStopCAN()

    ///////////////////////////////////////////////////////////////
    // createCombinedFilters()
    //  take the ids and masks passed to this and combine into CanBusHardwareFilter
    ///////////////////////////////////////////////////////////////
    VehicleBusWrapper.CANHardwareFilter[] createCombinedFilters(int[] ids, int[] masks) {


        if ((ids == null) || (masks == null) ||
                (ids.length == 0) || (masks.length == 0)) {
            Log.e(TAG, "Error -- no can filters specified");
            return null;
        }

        int count = ids.length;
        VehicleBusWrapper.CANHardwareFilter[] canHardwareFilters = new VehicleBusWrapper.CANHardwareFilter[count];

        for (int i = 0; i < masks.length && i < ids.length; i++) {
            canHardwareFilters[i] =
                    new VehicleBusWrapper.CANHardwareFilter(ids[i], masks[i], VehicleBusWrapper.CANFrameType.EXTENDED);
        }

        return canHardwareFilters;


    } // createCombinedFilters()

    ////////////////////////////////////////////
    ////////////////////////////////////////////
    ///////// Notification Functions ///////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////

    /**
     * Move service to the foreground and display icon.
     */
    void setForeground() {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, APP_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setDescription(VBS_CHANNEL_DESCRIPTION);
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(notificationChannel);

        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(APP_NAME)
                .setContentText(VBS_NOTIFICATION_DESCRIPTION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(null);

        startForeground(NOTFICATION_ID, builder.build());
        Log.d(TAG, "VBS set to foreground.");
    }

    /**
     * Update notification description.
     */
    void updateForegroundNotification(String description, int color, int priority) {
        builder.setContentText(description);
        builder.setColor(color);
        builder.setPriority(priority);
        notificationManager.notify(NOTFICATION_ID, builder.build());
    }

    /**
     * Move service to the background and remove icon.
     */
    private void setBackground() {
        stopForeground(true); // true = remove notification
    }

    ///////////////////////////////////////////////////////////////
    // broadcastStatus()
    //  sends an android broadcast with the status of all buses
    ///////////////////////////////////////////////////////////////
    void broadcastStatus() {

        Context context = getApplicationContext();

        Intent ibroadcast = new Intent();
        Log.v(TAG, "Sending Status to " + VehicleBusConstants.PACKAGE_NAME_ATS);

        //ibroadcast.setPackage(VehicleBusConstants.PACKAGE_NAME_ATS);
        ibroadcast.setAction(VehicleBusConstants.BROADCAST_STATUS);


        long elapsedRealtime = SystemClock.elapsedRealtime(); // ms since boot
        ibroadcast.putExtra("elapsedRealtime", elapsedRealtime); // ms since boot
        //ibroadcast.putExtra("password", VehicleBusService.BROADCAST_PASSWORD);

        //ibroadcast.putExtra("processId", processId); // so this can be killed?

        if (my_can != null) { // safety
            ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_STATUS_CANRX, my_can.isReadReady());
            ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_STATUS_CANTX, my_can.isWriteReady());
            ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_STATUS_CANBITRATE, my_can.getBitrate());
            ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_STATUS_CANNUMBER, my_can.getCanNumber());
        }

        context.sendBroadcast(ibroadcast);

    } // broadcastStatus()

    ///////////////////////////////////////////////////////////////
    // statusTask()
    //  Timer to broadcast that we are still alive
    ///////////////////////////// //////////////////////////////////
    private Runnable statusTask = new Runnable() {

        int count = 0;
        @Override
        public void run() {
            try {
/*
                count++;
                if (count > 20) {
                    Log.d(TAG, "FAKE JAM VBUS");
                    Thread.sleep(20000);
                }
*/
                //Log.d(TAG, "statusTask()");
                broadcastStatus();
                if (mainHandler != null)
                    mainHandler.postDelayed(statusTask, BROADCAST_STATUS_DELAY_MS); // broadcast a status every 1s
            } catch (Exception e) {
                Log.e(TAG + ".statusTask", "Exception: " + e.toString(), e);
            }
        }
    }; // statusTask()


} // class VehicleBusService
