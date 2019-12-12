/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

/////////////////////////////////////////////////////////////
// VehicleBusCAN:
//  Handles the setup/teardown of threads the control the CAN bus, and communications to/from
/////////////////////////////////////////////////////////////

// API TODO:
// cancel write (do I have to close socket from another thread?)
// cancel read (like when shutting down the socket)


package com.micronet.dsc.vbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class VehicleBusCAN {

    private static final String TAG = "ATS-VBS-CAN"; // for logging


    public static String BUS_NAME = "CAN";


    public static int DEFAULT_BITRATE = 250000; // a default to use if bitrate is not specified (and used as 1rst option for auto-detect)

    static final int SAFETY_MAX_OUTGOING_QUEUE_SIZE = 10; // just make sure this queue doesn't ever keep growing forever

    static CANWriteRunnable canWriteRunnable; // thread for writing
    static CANReadRunnable canReadRunnable; // thread for reading


    Handler callbackHandler = null; // the handler that the runnable will be posted to

    Runnable readyRxRunnable = null; // runnable to be posted to handler when the bus is ready for transmit/receive
    Runnable readyTxRunnable = null; // runnable to be posted to handler when the bus is ready for transmit/receive


    List<VehicleBusWrapper.CANFrame> incomingList = Collections.synchronizedList(new ArrayList<VehicleBusWrapper.CANFrame>());
    List<VehicleBusWrapper.CANFrame> outgoingList = Collections.synchronizedList(new ArrayList<VehicleBusWrapper.CANFrame>());


    VehicleBusWrapper busWrapper;
    VehicleBusDiscovery busDiscoverer;

    Context context;


    int confirmedBusBitrate = 0; // set to a bitrate that we know is working so we can skip listen-only mode



    public VehicleBusCAN(Context context) {
        busWrapper = VehicleBusWrapper.getInstance();
        busWrapper.isUnitTesting = false;
        this.context = context;

        busDiscoverer = new VehicleBusDiscovery(context, busWrapper, BUS_NAME);
    }

    public VehicleBusCAN(Context context, boolean isUnitTesting) {
        busWrapper = VehicleBusWrapper.getInstance();
        busWrapper.isUnitTesting = isUnitTesting;
        this.context = context;

        busDiscoverer = new VehicleBusDiscovery(context, busWrapper, BUS_NAME);
    }



    //////////////////////////////////////////////////////
    // start() : starts the threads to listen and send CAN frames
    //  CAN will start up in one of three modes:
    //      1) Auto-detect (if selected by the function parameter
    //      2) Confirmed (if we previously received frames at this bitrate and haven't switched bitrates or restarted app since)
    //      3) Unconfirmed (all others .. this will start up in listen mode until a frame is received)
    ///////////////////////////////////////////////////////
    public boolean start(int initial_bitrate, boolean auto_detect, VehicleBusWrapper.CANHardwareFilter[] hardwareFilters) {


        Log.v(TAG, "start() @ " + initial_bitrate + "kb " +
                (auto_detect ? "auto-detect " : (confirmedBusBitrate == initial_bitrate ? "normal " : "verify "))
            );

        // close any prior socket that still exists
        stop(); // stop any threads and sockets already running


        if (busWrapper.isUnitTesting) {
            // since we are unit testing and not on realy device, even creating the CanbusInterface will fail fatally,
            //  so we need to skip this in testing
            Log.e(TAG, "UnitTesting is on. Will not create CAN Interface");
            return false;
        }


        // we need to start up a bus at the intial_bitrate,
        // we should always start in listen-only mode?

        // if we are auto-detecting, then we will only stay on each bus for a set amount of time (5 sec)


        if (auto_detect) {
            // Auto-detect mode
            clearConfirmedBitRate(); // erase any prior confirmations of bitrate
            busDiscoverer.setCharacteristics(initial_bitrate, hardwareFilters);
            busDiscoverer.startDiscovery(busReadyReadOnlyCallback);
        } else
        if (confirmedBusBitrate == initial_bitrate) {
            // Confirmed mode

            // we know that this bitrate works since we've already used this bitrate
            // put our sockets into read & write mode
            busWrapper.setCharacteristics(false, initial_bitrate, hardwareFilters);
            busWrapper.start(BUS_NAME, busReadyReadWriteCallback, null);
        }
        else {
            // Unconfirmed mode

            // we do not know if this bitrate works since we have not used it
            // put our sockets in read-only mode
            clearConfirmedBitRate(); // erase any prior confirmations of bitrate
            busWrapper.setCharacteristics(true, initial_bitrate, hardwareFilters);
            busWrapper.start(BUS_NAME, busReadyReadOnlyCallback, null);
        }


        // register intents to receive Tx requests
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(VehicleBusConstants.BROADCAST_CAN_TX);
            context.registerReceiver(txReceiver, intentFilter);
            Log.v(TAG, "TX Receiver Registered");
        } catch (Exception e) {
            Log.e(TAG, "Could not register CAN Tx receiver");
        }


        return true;
    } // start()







    ///////////////////////////////////////////////////////
    // stop()
    //  called on shutdown
    ///////////////////////////////////////////////////////
    public void stop() {


        try {
            context.unregisterReceiver(txReceiver);
        } catch(Exception e) {
            // don't do anything
        }


        if (busDiscoverer.isInDiscovery()) {
            // stop discovery if it was previously started
            busDiscoverer.stopDiscovery();
        } else {
            busWrapper.stop(BUS_NAME);
        }

        if (canReadRunnable != null)
            canReadRunnable.cancelThread = true;
        if (canWriteRunnable != null)
            canWriteRunnable.cancelThread = true;

    } // stop()


    ///////////////////////////////////////////////////////
    // stopAll()
    //  just provides access to the wrapper's stopAll call,
    //  It is better to call this before stop() if we know we will be stopping all buses
    ///////////////////////////////////////////////////////
    public void stopAll() {
        busWrapper.stopAll();
    }


    ///////////////////////////////////////////////////////////
    // busReadyReadWriteCallback()
    //  This is called when a normal socket is ready
    ///////////////////////////////////////////////////////////
    private Runnable busReadyReadWriteCallback = new Runnable() {
        @Override
        public void run() {
            try {
//                Log.v(TAG, "busReadyReadWriteCallback()");
                startReading();
                startWriting();
//                Log.v(TAG, "busReadyReadWriteCallback() END");
            } catch (Exception e) {
                Log.e(TAG + ".busReadyReadWriteCallback", "Exception: " + e.toString(), e);
            }
        } // run()
    }; // busReadyReadWriteCallback()


    ///////////////////////////////////////////////////////////
    // busReadyReadOnlyCallback()
    //  This is called when a listen-only socket is ready
    ///////////////////////////////////////////////////////////
    private Runnable busReadyReadOnlyCallback = new Runnable() {
        @Override
        public void run() {
            try {
//                Log.v(TAG, "busReadyReadOnlyCallback ()");
                startReading();
            } catch (Exception e) {
                Log.e(TAG + ".busReadyReadOnlyCallback ", "Exception: " + e.toString(), e);
            }
        } // run()
    }; // busReadyReadOnlyCallback ()





    ///////////////////////////////////////////////////////////
    // startReading()
    //  starts a new read thread after a read thread is previously opened
    ///////////////////////////////////////////////////////////
    boolean startReading() {

        VehicleBusWrapper.CANSocket canSocket = null;

        canSocket = busWrapper.getCANSocket();

        if (canSocket == null) return false;

        // Safety: make sure we cancel any previous thread if we are starting a new one
        if (canReadRunnable != null)
            canReadRunnable.cancelThread = true;

        canReadRunnable = new CANReadRunnable(canSocket);

        // If we aren't unit testing, then start the thread
        if (!busWrapper.isUnitTesting) {
            Thread clientThread = new Thread(canReadRunnable);
            clientThread.start();
        }

        return true;
    } // startReading()


    ///////////////////////////////////////////////////////////
    // startWriting()
    //  starts a new write thread after a read thread is previously opened
    ///////////////////////////////////////////////////////////
    boolean startWriting() {

        VehicleBusWrapper.CANSocket canSocket = null;

        canSocket = busWrapper.getCANSocket();

        if ( canSocket == null) return false;

        // Safety: make sure we cancel any previous thread if we are starting a new one
        if (canWriteRunnable != null)
            canWriteRunnable.cancelThread = true;

        canWriteRunnable = new CANWriteRunnable(canSocket);

        // If we aren't unit testing, then start the thread
        if (!busWrapper.isUnitTesting) {
            Thread clientThread = new Thread(canWriteRunnable);
            clientThread.start();
        }

        return true;
    } // startWriting()


    ///////////////////////////////////////////////
    // isWriteReady()
    //  Are we capable of writing frames to the CAN bus
    //      (checks if the bus socket is setup, not that there is something at the other end at correct bitrate)
    ///////////////////////////////////////////////

    public boolean isWriteReady() {
        try {
            if ((canWriteRunnable != null) &&
                (canWriteRunnable.isReady)) return true;
        } catch (Exception e) {
            // DO nothing
        }

        return false;
    }

    ///////////////////////////////////////////////
    // isReadReady()
    //  Are we capable of reading frames from the CAN bus
    //      (checks if the bus socket is setup, not that there is something at the other end at correct bitrate)
    ///////////////////////////////////////////////
    public boolean isReadReady() {
        try {
            if ((canReadRunnable != null) &&
                    (canReadRunnable.isReady)) return true;
        } catch (Exception e) {
            // DO nothing
        }

        return false;
    }


    ///////////////////////////////////////////////////////
    // getBitrate()
    //  return the current bitrate from our wrapper
    ///////////////////////////////////////////////////////
    public int getBitrate() {
        return busWrapper.getCANBitrate();
    }





    ///////////////////////////////////////////////////////////////////
    // abortTransmits()
    //  stop attempting to send any Tx packets in progress (maybe our address was changed, etc..)
    ///////////////////////////////////////////////////////////////////
    void abortTransmits() {

        // TODO: kill any frames in the CAN queue (must happen within 50 ms)
        // Is this implemented in CAN API yet?

        // kill any frames in our queue
        synchronized (outgoingList) {
            outgoingList.clear();
        }
    } // abortTransmits




    ///////////////////////////////////////////////////////////////////
    // setConfirmedBitRate()
    //  sets the bitrate as confirmed so we don't need to start in listen mode next time
    ///////////////////////////////////////////////////////////////////
    void setConfirmedBitRate(int bitrate) {

        // remember that we are good at this bitrate now
        confirmedBusBitrate = bitrate;

        Log.v(TAG, "CAN bitrate " + confirmedBusBitrate + " is confirmed");

        // set it in non-volatile in case we get restarted.
        State state;
        state = new State(context);

        state.writeState(State.CAN_CONFIRMED_BITRATE, confirmedBusBitrate ); // this is our discovered bitrate
    }


    ///////////////////////////////////////////////////////////////////
    // clearConfirmedBitRate()
    //  clears the bitrate to unconfirmed so that we always start in listen mode
    ///////////////////////////////////////////////////////////////////
    void clearConfirmedBitRate() {


        // forget any confirmed bitrate that we have (this happens if we start at a different bitrate or turn on auto-detect)
        confirmedBusBitrate = 0;

        // set it in non-volatile in case we get restarted.
        State state;
        state = new State(context);

        state.writeState(State.CAN_CONFIRMED_BITRATE, confirmedBusBitrate ); // this is our discovered bitrate
    }


    ///////////////////////////////////////////////////////////////////
    // loadConfirmedBitRate()
    //  loads a confirmed status from file so we don't need to start in listen mode next time
    //  this is done when the service is "restarted" (due to crash), but not when it is started normal
    ///////////////////////////////////////////////////////////////////
    void loadConfirmedBitRate() {

        State state;
        state = new State(context);

        confirmedBusBitrate = state.readState(State.CAN_CONFIRMED_BITRATE); // this is our discovered bitrate

        Log.v(TAG, "Loaded confirmed CAN bitrate " + confirmedBusBitrate);
    }


    ///////////////////////////////////////////////////////////////////
    // receiveFrame() : called by CAN thread when something is received
    ///////////////////////////////////////////////////////////////////
    void receiveFrame(VehicleBusWrapper.CANFrame frame) {


        // Are we unconfirmed ?
        if (confirmedBusBitrate == 0) {
            // Yes, we were unconfirmed


            // Are we in discovery? If so, then consider ourselves discovered
            if (busDiscoverer.isInDiscovery()) {
                // this will stop discovery
                busDiscoverer.markDiscovered();
            }

            setConfirmedBitRate(busWrapper.getCANBitrate()); // remember that we are good at this bitrate;

            // restart everything in read/write mode
            busWrapper.setNormalMode();
            busWrapper.restart(BUS_NAME, busReadyReadWriteCallback, null);

        } else {
            // broadcast this frame to other applications
            broadcastRx(frame);
        }


    } // receiveFrame()


    ///////////////////////////////////////////////////////////////////
    // sendFrame() : safe to call from a different thread than the CAN threads
    //  queues a frame to be sent by the write thread
    ///////////////////////////////////////////////////////////////////
    void sendFrame(VehicleBusWrapper.CANFrame frame) {

        Log.vv(TAG, "SendFrame()");
        synchronized (outgoingList) {
            if (outgoingList.size() < SAFETY_MAX_OUTGOING_QUEUE_SIZE) {
                outgoingList.add(frame);
            }
        }
        Log.vv(TAG, "SendFrame() END");
    }


    ///////////////////////////////////////////////////////////////////
    // clearQueues()
    //  This is used in testing to clear the incoming and outgoing frames when starting a test
    ///////////////////////////////////////////////////////////////////
    void clearQueues() {
        synchronized (incomingList) {
            incomingList.clear();
        }

        synchronized (outgoingList) {
            outgoingList.clear();
        }
    } //clearQueues()




    // We need separate threads for sending and receiving data since both are blocking operations


    ////////////////////////////////////////////////////////
    // CANWriteRunnable : this is the code that runs on another thread and
    //  handles CAN writing to bus
    ////////////////////////////////////////////////////////
    class CANWriteRunnable implements Runnable {


        volatile boolean cancelThread = false;
        volatile boolean isClosed = false;
        volatile boolean isReady = false;
        //CanbusInterface canInterface;
        VehicleBusWrapper.CANSocket canWriteSocket;

        CANWriteRunnable(VehicleBusWrapper.CANSocket socket) {
//                CanbusInterface new_canInterface) {
            canWriteSocket = socket;
        }

        public void run() {

            VehicleBusWrapper.CANFrame outFrame = null;

            while (!cancelThread) {

                // remove anything in our outgoing queues and connections
                abortTransmits();

                if (!cancelThread) {
                    // Notify the main thread that we are ready for write

                    if ((callbackHandler != null) && (readyTxRunnable != null)) {
                        callbackHandler.post(readyTxRunnable);
                    }
                    Log.v(TAG, "CAN-Write thread ready");
                    isReady = true;

                }


                while (!cancelThread) {

                    // try and send a packet
                    outFrame = null;
                    // get what we need to send
                    synchronized (outgoingList) {
                        if (outgoingList.size() > 0) {
                            outFrame = outgoingList.get(0);
                            outgoingList.remove(0);
                        }
                    }
                    if (outFrame == null) {
                        android.os.SystemClock.sleep(5); // we can wait 5 ms if nothing to send.
                    } else {
                        Log.v(TAG, "frame --> " + String.format("%02x", outFrame.getId()) + " : " + Log.bytesToHex(outFrame.getData(), outFrame.getData().length));
                        try {
                            canWriteSocket.write(outFrame);
                            //Log.d(TAG, "Write Returns");
                        } catch (Exception e) {
                            // exceptions are expected if the interface is closed
                            Log.v(TAG, "Exception on write socket. Canceling Thread");
                            cancelThread = true;
                        }
                    }
                } // thread not canceled

            } // thread not cancelled

            isReady = false;
            Log.v(TAG, "CAN Write Thread terminated");
            isClosed = true;

        } // run
    } // CAN Write communications (runnable)





    ////////////////////////////////////////////////////////
    // CANRunnable : this is the code that runs on another thread and
    //  handles CAN sending and receiving
    ////////////////////////////////////////////////////////
    class CANReadRunnable implements Runnable {


        volatile boolean cancelThread = false;
        volatile boolean isClosed = false;
        volatile boolean isReady = false;

        //CanbusInterface canInterface;
        VehicleBusWrapper.CANSocket canReadSocket;

        CANReadRunnable(VehicleBusWrapper.CANSocket new_canSocket) {
//            CanbusInterface new_canInterface) {
            //canInterface = new_canInterface;
            canReadSocket = new_canSocket;
        }

        public void run() {


            while (!cancelThread) {

                // also remove anything that was incoming on last bus (so we know what bus it arrived on)
                synchronized (incomingList) {
                    incomingList.clear();
                }


                VehicleBusWrapper.CANFrame inFrame = null;


                if (!cancelThread) {
                    // Notify the main thread that we are ready for read
                    if ((callbackHandler != null) && (readyRxRunnable != null)) {
                        callbackHandler.post(readyRxRunnable);
                    }
                    Log.v(TAG, "CAN-Read thread ready" );
                    isReady = true;

                }

                while (!cancelThread)  {
                    // try and receive a packet
                    inFrame = null;
                    try {

                        //Log.v(TAG, "Reading... ");
                        inFrame = canReadSocket.read();
                        //Log.v(TAG, "Done Reading... ");

                    } catch (Exception e) {
                        // exceptions are expected if the interface is closed
                        Log.v(TAG, "Exception on read socket. Canceling Thread: " + e.getMessage());
                        cancelThread = true;
                    }


                    if (inFrame != null) {

                        Log.v(TAG, "frame  <-- " + String.format("%02x", inFrame.getId()) +
                                " : " +
                                Log.bytesToHex(inFrame.getData(), inFrame.getData().length));

                        receiveFrame(inFrame);

                    }

                } // thread not canceled


            } // thread not cancelled

            isReady = false;
            Log.v(TAG, "CAN Read Thread terminated");
            isClosed = true;

        } // run
    } // CAN Read communications (runnable)



    ///////////////////////////////////////////////
    // broadcastRx()
    //  send a local broadcast that we received a CAN frame from the bus
    ///////////////////////////////////////////////
    void broadcastRx(VehicleBusWrapper.CANFrame frame) {



        //synchronized (incomingList) {
        //  incomingList.add(inFrame);
        //} // sync

        // Notify the main thread that something is available in the incomingList
        //if ((callbackHandler != null) && (receiveRunnable != null)) {
        //    callbackHandler.post(receiveRunnable);
        //}


        long elapsedRealtime = SystemClock.elapsedRealtime(); // ms since boot

        Intent ibroadcast = new Intent();
        //ibroadcast.setPackage(VehicleBusConstants.PACKAGE_NAME_ATS);
        ibroadcast.setAction(VehicleBusConstants.BROADCAST_CAN_RX);


        ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_TIMESTAMP, elapsedRealtime); // ms since boot
        //ibroadcast.putExtra("password", VehicleBusService.BROADCAST_PASSWORD);
        ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_CAN_ID, frame.getId());
        ibroadcast.putExtra(VehicleBusConstants.BROADCAST_EXTRA_CAN_DATA, frame.getData());

        context.sendBroadcast(ibroadcast);
    } // broadcastRx



    ///////////////////////////////////////////////
    // TxReceiver()
    //  receive a local broadcast to transmit a CAN frame over the bus
    ///////////////////////////////////////////////
    TxReceiver txReceiver = new TxReceiver();
    class TxReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            try {

                Log.v(TAG, "Received TX");
/*
                String password = intent.getStringExtra("password");
                if ((password == null) || (!password.equals(VehicleBusService.BROADCAST_PASSWORD))) {
                    Log.e(TAG, "Received invalid CAN TX request");
                    return;
                }
*/
                int id = intent.getIntExtra(VehicleBusConstants.BROADCAST_EXTRA_CAN_ID, -1);
                byte[] data = intent.getByteArrayExtra(VehicleBusConstants.BROADCAST_EXTRA_CAN_DATA);

                if ((id != -1) && (data != null) && (data.length > 0)) {
                    VehicleBusWrapper.CANFrame frame = new VehicleBusWrapper.CANFrame(id, data, VehicleBusWrapper.CANFrameType.EXTENDED);
                    sendFrame(frame);
                }
            } catch (Exception e) {
                Log.e(TAG, ".txReceiver Exception : " + e.toString(), e);
            }

        }
    } // TxReceiver
} // CAN class
