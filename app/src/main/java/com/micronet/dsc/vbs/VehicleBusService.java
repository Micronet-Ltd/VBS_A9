/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

/////////////////////////////////////////////////////////////
// VBusService:
//  Handles communications with hardware regarding CAN and J1708
//  can be started in a separate process (since interactions with the API are prone to jam or crash -- and take down the whole process when they do)
//
//  See VehicleBusConstants file for a list of actions and extras for this service
//
/////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;


import java.util.Arrays;


public class VehicleBusService extends Service {

    public static final String TAG = "ATS-VBS";

    public static final int BROADCAST_STATUS_DELAY_MS = 1000; // every 1 s



    // These are the possible buses that are monitored here
    static final int VBUS_CAN = 1;
    static final int VBUS_J1708 = 2;






    int processId = 0;
    boolean hasStartedCAN = false;
    boolean hasStartedJ1708 = false;


    Handler mainHandler = null;

    VehicleBusJ1708 my_j1708;
    VehicleBusCAN my_can;


    boolean isUnitTesting = false;

    public VehicleBusService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    @Override
    public void onCreate() {
        android.util.Log.i(TAG, "Service Created: VBS device=" + BuildConfig.BUILD_DEVICE + " version=" + BuildConfig.VERSION_NAME);
        processId = android.os.Process.myPid();
        mainHandler  = new Handler();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String bus;
        String action;




        if (intent == null) {
            // load up our saved settings from file and use those
            if (!startFromFile()) return START_NOT_STICKY;
            return START_NOT_STICKY;
        } // intent was null .. load from file


        action = intent.getAction();

        if (action.equals(VehicleBusConstants.SERVICE_ACTION_RESTART)) {
            android.util.Log.i(TAG, "Vehicle Bus Service Restarting");
            if (!startFromFile()) return START_NOT_STICKY;
            return START_NOT_STICKY;

        }


        bus = intent.getStringExtra(VehicleBusConstants.SERVICE_EXTRA_BUS);

        if (bus == null) {
            Log.e(TAG, "cannot start/stop service. must designate bus");
            return START_NOT_STICKY;
        }



        if (action.equals(VehicleBusConstants.SERVICE_ACTION_START)) {
            android.util.Log.i(TAG, "Vehicle Bus Service Starting: " + bus);


            // if bus is J1708, then filter out all CAN messages so we don't get any before starting.

            if (bus.equals("J1708")) {
                // remember that this was our last request
                saveJ1708(true);

                stopJ1708(false);
                startJ1708();
            }
            else
            if (bus.equals("CAN")) {
                int bitrate = intent.getIntExtra(VehicleBusConstants.SERVICE_EXTRA_BITRATE, VehicleBusCAN.DEFAULT_BITRATE);
                boolean auto_detect = intent.getBooleanExtra(VehicleBusConstants.SERVICE_EXTRA_AUTODETECT, false);
                boolean skip_verify = intent.getBooleanExtra(VehicleBusConstants.SERVICE_EXTRA_SKIPVERIFY, false);

                int[] ids = intent.getIntArrayExtra(VehicleBusConstants.SERVICE_EXTRA_HARDWAREFILTER_IDS);
                int[] masks = intent.getIntArrayExtra(VehicleBusConstants.SERVICE_EXTRA_HARDWAREFILTER_MASKS);

                // remember that this was our last request
                saveCAN(true, bitrate, auto_detect, ids, masks);

                // and do it
                stopCAN(false);
                startCAN(bitrate, skip_verify, auto_detect, ids, masks, false);
            }


        } else
        if (action.equals(VehicleBusConstants.SERVICE_ACTION_STOP)) {
            android.util.Log.i(TAG, "Vehicle Bus Service Stopped: " + bus);

            // ignore J1708 requests for now, J1708 is stopped same time as CAN
            if (bus.equals("CAN")) {

                saveCAN(false, 0, false, null, null);
                if (!isAnythingElseOn(VBUS_CAN)) {
                    stopSelf(); // nothing on, stop everything and exit
                } else {
                    stopCAN(true); // just stop the CAN
                }
            }
            else
            if (bus.equals("J1708")) {
                saveJ1708(false);

                if (!isAnythingElseOn(VBUS_J1708)) {
                    stopSelf(); // nothing on, stop everything and exit
                } else {
                    stopJ1708(true); // just stop the J1708
                }
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

        android.util.Log.v(TAG, "Destroying Service");
        stopJ1708(false);
        stopCAN(false);

    } // OnDestroy()




    ////////////////////////////////////////////////////////////////
    // startFromFile()
    //  start up the buses based on saved state information
    //  return true if this was successful, false if there was a problem
    ////////////////////////////////////////////////////////////////
    boolean startFromFile() {
        android.util.Log.i(TAG, "Vehicle Bus Service Starting: (From Saved File)" );

        State state = new State(getApplicationContext());

        boolean enCan = state.readStateBool(State.FLAG_CAN_ON) ;
        boolean enJ1708 = state.readStateBool(State.FLAG_J1708_ON);

        if ((!enCan) && (!enJ1708)) {
            android.util.Log.i(TAG, "All buses are set to off. stopping service");
            stopSelf(); // nothing on, stop everything and exit
        }

        // first, try to stop all running buses at the same time
        stopAll();

        if (enCan) { // enable CAN bus .. always do this before J1708 to prevent re-creating CAN bus when starting J1708
            int bitrate = state.readState(State.CAN_BITRATE);
            if (bitrate == 0) bitrate = VehicleBusCAN.DEFAULT_BITRATE;
            boolean auto_detect = state.readStateBool(State.FLAG_CAN_AUTODETECT);

            String idstring = state.readStateString(State.CAN_FILTER_IDS);
            String maskstring = state.readStateString(State.CAN_FILTER_MASKS);

            String[] idsplits = idstring.split(",");
            String[] masksplits = maskstring.split(",");

            int ids[] = new int[idsplits.length];
            int masks[] = new int[masksplits.length];

            for (int i=0; i < idsplits.length && i < masksplits.length; i++) {
                try {
                    ids[i] = Integer.parseInt(idsplits[i]);
                    masks[i] = Integer.parseInt(masksplits[i]);
                } catch (Exception e) {
                    Log.e(TAG, "CAN Masks or IDs are not a number!. Aborting start.");
                    return false; // we can't start, not sure what to do, we don't want to start without any filters
                }
            }

            startCAN(bitrate, false, auto_detect, ids, masks, true);
        }

        if (enJ1708) { // enable J1708 bus now b/c it can get tacked onto CAN.
            startJ1708();
        }

        return true;
    } // startFromFile()




    ////////////////////////////////////////////////////////////////
    // isAnythingElseOn()
    //  returns true if any other bus is on, other than the one specified
    ////////////////////////////////////////////////////////////////
    boolean isAnythingElseOn(int bus) {
        switch (bus) {
            case VBUS_CAN:
                return hasStartedJ1708;
            case VBUS_J1708:
                return hasStartedCAN;
            default:
                return hasStartedCAN || hasStartedJ1708;
        }
    } // isAnythingElseOn()


    ////////////////////////////////////////////////////////////////
    // stopAll()
    //  stop both the CAN and the J1708 bus if they are running
    //  this is more efficient than the stopCAN() or stopJ1708() if we know that we will be stopping both buses at the same time.
    ////////////////////////////////////////////////////////////////
    void stopAll() {

        // call the underlying wrapper's stopAll()

        // my_can or my_j1708 is only used to provide us access to the underlying wrapper stopAll() call
        // make sure we can use either one, because one or the other might be null if that bus is not running.

        if (my_can != null) {
            my_can.stopAll();
        }
        else
        if (my_j1708 != null) {
            my_j1708.stopAll();
        }

        // stop all buses
        stopJ1708(true);
        stopCAN(true);

    } //stopAll()


    ////////////////////////////////////////////////////////////////
    // saveCAN()
    // save CAN information to file so we can load it up on restart.
    ////////////////////////////////////////////////////////////////
    void saveCAN(boolean enabled, int bitrate, boolean auto_detect, int[] ids, int masks[]) {
        Context context = getApplicationContext();

        State state;
        state = new State(context);

        state.writeState(State.FLAG_CAN_ON, ( enabled ?  1 : 0));

        if (enabled) {
            // save more info about the CAN
            state.writeState(State.CAN_BITRATE, bitrate);
            state.writeState(State.FLAG_CAN_AUTODETECT, (auto_detect ? 1 : 0));

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
    void startCAN(int bitrate, boolean skip_verify, boolean auto_detect, int[] ids, int masks[], boolean load_last_confirmed) {
        Log.d(TAG, "+startCAN():");

        if (hasStartedCAN) {
            Log.w(TAG, "CAN already started. Ignoring subsequent Start.");
        }

        hasStartedCAN = true; // don't start again
        Context context = getApplicationContext();

/*
        // We CANNOT Start CAN after J1708. in this case must stop and restart J1708
        //  That way we re-create the interface and socket to use correct filters, bitrate, etc.
        boolean stoppedJ1708 = false;
        if (hasStartedJ1708) {
            stoppedJ1708 = true;
            stopJ1708(false);
        }
*/
        // create the combined filters
        VehicleBusWrapper.CANHardwareFilter[] canHardwareFilters = createCombinedFilters(ids, masks);


        my_can = new VehicleBusCAN(context, isUnitTesting);

        if (load_last_confirmed)
            my_can.loadConfirmedBitRate();

        if (skip_verify) {
            // we've requested to treat this bitrate as confirmed
            my_can.setConfirmedBitRate(bitrate);
        }

        my_can.start(bitrate, auto_detect, canHardwareFilters);

/*
        // now we need to restart J1708 again if we previously stopped it
        if (stoppedJ1708) {
            startJ1708();
        }
*/

        if (!isAnythingElseOn(VBUS_CAN)) {
            // if we haven't started J1708, we need to start status broadcasts

            if (mainHandler != null) {
                Log.d(TAG, "Starting status broadcasts");
                mainHandler.postDelayed(statusTask, BROADCAST_STATUS_DELAY_MS); // broadcast a status every 1s
            }
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
        if (!isAnythingElseOn(VBUS_CAN)) {
            mainHandler.removeCallbacks(statusTask);
        }

        if (my_can != null) {
            my_can.stop();
        }

        hasStartedCAN = false;

    } // stopCAN()


    ////////////////////////////////////////////////////////////////
    // saveJ1708()
    //  save status of the J1708 bus to non-volatile memory
    ////////////////////////////////////////////////////////////////
    void saveJ1708(boolean enabled) {

        Context context = getApplicationContext();

        // remember this to file
        State state = new State(context);
        state.writeState(State.FLAG_J1708_ON, (enabled ? 1 : 0));


    }



    ////////////////////////////////////////////////////////////////
    // startJ1708()
    //  start the J1708 bus
    ////////////////////////////////////////////////////////////////
    void startJ1708() {


        if (hasStartedJ1708) {
            Log.w(TAG, "J1708 already started. Ignoring Start.");
        }

        if (!VehicleBusJ1708.isSupported()) {
            Log.e(TAG, "J1708 not supported");
            return;
        }

        hasStartedJ1708 = true; // don't start again
        Context context = getApplicationContext();

        my_j1708 = new VehicleBusJ1708(context, isUnitTesting);
        my_j1708.start();

        if (!isAnythingElseOn(VBUS_J1708)) {
            // if we haven't already started CAN, we need to start status broadcasts
            if (mainHandler != null) {
                mainHandler.postDelayed(statusTask, BROADCAST_STATUS_DELAY_MS); // broadcast a status every 1s
            }
        }
    } // startJ1708()

    ////////////////////////////////////////////////////////////////
    // stopJ1708()
    //  stop the J1708 bus
    ////////////////////////////////////////////////////////////////
    void stopJ1708(boolean show_error) {


        if (!hasStartedJ1708) {
            if (show_error) {
                Log.w(TAG, "J1708 not started. Ignoring Stop.");
            }
            return;
        }


        // remove callbacks first in case stop() jams
        if (!isAnythingElseOn(VBUS_J1708)) {
            mainHandler.removeCallbacks(statusTask);
        }

        if (my_j1708 != null) {
            my_j1708.stop();
        }

        hasStartedJ1708 = false;

    } // stopJ1708()



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
                 new VehicleBusWrapper.CANHardwareFilter(new int[] {ids[i]},masks[i], VehicleBusWrapper.CANFrameType.EXTENDED);
        }

        return canHardwareFilters;


    } // createCombinedFilters()


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
        }

        if (my_j1708 != null) { // safety
            ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_STATUS_J1708RX, my_j1708.isReadReady());
            ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_STATUS_J1708TX, my_j1708.isWriteReady());
        }

        context.sendBroadcast(ibroadcast);

    } // broadcastStatus()

    ///////////////////////////////////////////////////////////////
    // statusTask()
    //  Timer to broadcast that we are still alive
    ///////////////////////////////////////////////////////////////
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
