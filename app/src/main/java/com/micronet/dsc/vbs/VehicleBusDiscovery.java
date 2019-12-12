/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

/////////////////////////////////////////////////////////////
// VehicleBusDiscovery:
//  Helps discover which CAN bitrate - if any -- is connected

//  This is called from VehicleBusCAN and acts like an intermediary to control VehicleBusWrapper

/////////////////////////////////////////////////////////////


package com.micronet.dsc.vbs;


import android.content.Context;
import android.os.Handler;



public class VehicleBusDiscovery {


    private static final String TAG = "ATS-VBS-Discover"; // for logging


    public static final int DISCOVER_BUS_WAIT_MS = 5000; // wait 5 seconds on each bus  (must be > the 3+seconds it can take to switch bus speeds)
    public static final int DISCOVER_MAX_WINDOWS = 3; // We must limit the number of bitrate switches since each check creates threads that may not ever go away
                                                      // an odd number ensures we end up on bus we started on (most likely to be the correct bus


    // stages
    public static final int DISCOVERY_STAGE_OFF = 0;
    public static final int DISCOVERY_STAGE_250 = 1;
    public static final int DISCOVERY_STAGE_500 = 2;

    int discoveryStage = DISCOVERY_STAGE_OFF;

    Context context; // main service context
    Handler mainHandler; // handler for timers
    VehicleBusWrapper busWrapper; // provides actual control of the bus for changing the speeds



    /// Things set by the calling class
    int initial_bitrate = VehicleBusCAN.DEFAULT_BITRATE;
    VehicleBusWrapper.CANHardwareFilter[] hardwareFilters; // this must be set to something, otherwise no packets will be received and thus bus not discovered
    String BUS_NAME; // the name of the bus we are discovering on, passed to the wrapper


    //callbacks that are passed to the wrapper
    Runnable busDiscoverReadyCallback; // called when a discover socket is setup and we should start thread to listen for packets


    int on_window_num = 0; // how many times we've checked across all buses (we need to limit this b/c it can create threads)


    public VehicleBusDiscovery(Context context, VehicleBusWrapper busWrapper, String bus_name) {
        this.context = context;
        mainHandler  = new Handler();
        this.busWrapper = busWrapper;
        this.BUS_NAME = bus_name;
    }


    ///////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////
    //
    // Public Methods
    //
    ///////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////
    //  setCharacteristics()
    //      call this before starting discovery
    //  hwFilters : determines what qualifies as a successful discovery .. this must be set to something
    ////////////////////////////////////////////////////////
    public void setCharacteristics(int initial_bitrate, VehicleBusWrapper.CANHardwareFilter[] hwFilters) {
        this.initial_bitrate = initial_bitrate;
        this.hardwareFilters = hwFilters;
    }


    ////////////////////////////////////////////////////////
    // startDiscovery()
    //  attempt to discover the presence of a J1939 bus
    // Parameters
    //  initial_bitrate: if given, start listening with this
    ////////////////////////////////////////////////////////
    public boolean startDiscovery(Runnable busDiscoverReadyCallback) {

        Log.v(TAG, "Starting CAN bitrate Discovery");

        on_window_num = 0; // we haven't checked any so far

        // remember what to call each time when a new discover socket is ready
        this.busDiscoverReadyCallback = busDiscoverReadyCallback;

        // setup the first socket
        changeBitrate(initial_bitrate);
        busWrapper.start(BUS_NAME, busDiscoverReadyCallback, null);

        // and wait a certain amount of time on this socket before switching bitrates
        mainHandler.postDelayed(discoverBusTask, DISCOVER_BUS_WAIT_MS); // try again in five seconds

        return true;
    } // discoverBus()


    ///////////////////////////////////////////////////////////////
    // stopDiscovery()
    //  aborts all discovery on the bus
    ///////////////////////////////////////////////////////////////
    public void stopDiscovery() {

        Log.v(TAG, "Aborting Discovery");

        if (discoveryStage == DISCOVERY_STAGE_OFF) return; // we were not in process of discovering

        if (mainHandler != null) {
            mainHandler.removeCallbacks(discoverBusTask); // remove any pending timers
        }
        discoveryStage = DISCOVERY_STAGE_OFF; // turn off discovery

        // and kill off the socket we started to discover
        busWrapper.stop(BUS_NAME);

        on_window_num = 0; // safety
    } // stopDiscovery



    ///////////////////////////////////////////////////////////////
    // isInDiscovery()
    //  mark the current bus as discovered
    ///////////////////////////////////////////////////////////////
    public boolean isInDiscovery() {
        if (discoveryStage != DISCOVERY_STAGE_OFF)
            return true;
        else
            return false;
    }



    ///////////////////////////////////////////////////////////////
    // markDiscovered()
    //  mark the current bus as discovered
    ///////////////////////////////////////////////////////////////
    public void markDiscovered() {


        int discovered_bitrate = busWrapper.getCANBitrate();

        Log.v(TAG, "Discovered CAN bitrate " + discovered_bitrate);

        if (mainHandler != null)
            mainHandler.removeCallbacks(discoverBusTask); // remove any pending timers


        if (discoveryStage == DISCOVERY_STAGE_OFF) return; // we were not in process of discovering

        // remember what we discovered in case we are restarted

        State state;
        state = new State(context);

        state.writeState(State.CAN_BITRATE, discovered_bitrate ); // this is our discovered bitrate
        state.writeState(State.FLAG_CAN_AUTODETECT, 0); // no longer in auto-detect


        // remember we are not discovering
        discoveryStage = DISCOVERY_STAGE_OFF;


    } // markDiscovered()




    ///////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////
    //
    //  Private Methods
    //
    ///////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////
    // changeBitrate()
    //  changes the bitrate -- will not take effect until the next time bus is (re-)started
    ///////////////////////////////////////////////////////////////
    void changeBitrate(int new_bitrate) {
        if (new_bitrate == 500000) {
            discoveryStage = DISCOVERY_STAGE_500;
        } else {
            new_bitrate = 250000;
            discoveryStage = DISCOVERY_STAGE_250;
        }

        // restart on new bit rate, keep with discovery callbacks
        busWrapper.setCharacteristics(true, new_bitrate, hardwareFilters);
    }






    ///////////////////////////////////////////////////////////////
    // discoverBusTask()
    //  task that executes after listening on a bus for a given amount of time to listen on next bus
    ///////////////////////////////////////////////////////////////
    Runnable discoverBusTask = new Runnable() {

        @Override
        public void run() {
            try {


                on_window_num++;

                if (on_window_num >= DISCOVER_MAX_WINDOWS) {
                    Log.d(TAG, "Max discover windows (" + on_window_num  + ") reached, staying on bitrate");
                    // don't re-up the time-out
                    return;
                }


                Log.d(TAG, "Discover window # " + on_window_num + " expired, switching bitrate");
                switch (discoveryStage) {
                    case DISCOVERY_STAGE_250:
                        changeBitrate(500000); // change to 500kb next
                        break;
                    case DISCOVERY_STAGE_500:
                    default:
                        changeBitrate(250000); // change to 250kb next
                        break;
                }

                // restart the buses at the new bitrate
                busWrapper.restart(null, null, null); // don't change any callbacks

            } catch(Exception e) {
                Log.e(TAG + ".discoverBusTask", "Exception: " + e.toString(), e);
            }
            if (discoveryStage != DISCOVERY_STAGE_OFF)
                mainHandler.postDelayed(discoverBusTask, DISCOVER_BUS_WAIT_MS); // expire again in five seconds
        }
    }; // discoverBusTask()


} // class VehicleBusDiscovery
