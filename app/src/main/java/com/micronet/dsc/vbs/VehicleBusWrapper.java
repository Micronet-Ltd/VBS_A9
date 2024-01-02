/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

/////////////////////////////////////////////////////////////
// VehicleBusWrapper:
//  1) Extension: provides extra methods that can be used by CAN sub-classes
//  2) Resource Sharing: allows setup of the singleton interfaces and sockets needed for joint access to can library by CAN
//  3) Normalization: Provides intermediate layer for access to library so no other classes call library methods directly.
/////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import static com.micronet.dsc.vbs.VehicleBusService.service;


/**
 * Created by dschmidt on 2/18/16.
 */
public class VehicleBusWrapper{
    public static final String TAG = "ATS-VBS-Wrap";

    static int canNumber;
    static boolean isUnitTesting = true; // we don't actually open sockets when unit testing


    // Singleton methods: makes this class a singleton
    private static VehicleBusWrapper instance = null;
    private CanSocket mSocket;

    private VehicleBusWrapper() {}
    public static VehicleBusWrapper getInstance() {
        if(instance == null) {
            instance = new VehicleBusWrapper();
        }
        return instance;
    }


    ///////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////


    // basic handler for posting
    Handler callbackHandler = new Handler(Looper.getMainLooper());


    // We need a list of which bus types are currently actively used.
    //  We'll shut down the socket when nobody needs it.

    ArrayList<String> instanceNames = new ArrayList<>();


    // A class to hold callbacks so we can let others know when their requested socket is ready or when it has gone away
    private static class callbackStruct {
        String busName;
        Runnable callback;

        public callbackStruct(String name, Runnable cb) {
            busName = name;
            callback = cb;
        }
    }

    ArrayList<callbackStruct> callbackArrayReady = new ArrayList<>();
    ArrayList<callbackStruct> callbackArrayTerminated = new ArrayList<>();


    // Create a new class for thread where startup/shutdown work will be performed
    BusSetupRunnable busSetupRunnable = new BusSetupRunnable();




    ///////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////
    //
    // Functions to be called from outside this class
    //
    ///////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////
    // setCharacteristics()
    //  set details for the CAN, call this before starting a CAN bus
    //////////////////////////////////////////////////
    public boolean setCharacteristics(boolean listen_only, int bitrate, int[] ids, int[] masks) { // Todo: Should I include canNumber in here? since I

        // will take effect on the next bus stop/start cycle
        busSetupRunnable.setCharacteristics(listen_only, bitrate, ids, masks);
        return true;
    } // setCharacteristics()


    //////////////////////////////////////////////////
    // setCharacteristics()
    //  set details for the CAN, call this before starting a CAN bus
    //////////////////////////////////////////////////
    public boolean setNormalMode() {

        // will take effect on the next bus stop/start cycle
        busSetupRunnable.setNormalMode();
        return true;
    } // setCharacteristics()


    //////////////////////////////////////////////////
    // start()
    //   startup a bus
    //   name: "CAN"
    //////////////////////////////////////////////////
    public boolean start(String name, Runnable readyCallback, Runnable terminatedCallback) {


        if (isUnitTesting) {
            // since we are unit testing and not on realy device, even creating the CanbusInterface will fail fatally,
            //  so we need to skip this in testing
            Log.e(TAG, "UnitTesting is on. Will not start bus.");
            return false;
        }

        // If we are already setup, then call ready right away
        if (busSetupRunnable == null) {
            Log.e(TAG, "busSetupRunnable is null!! Cannot start.");
            return false;
        }

        if (!instanceNames.isEmpty()) {
            if (instanceNames.contains(name)) {
                //Log.d(TAG, "" + name + " previously started. Start Ignored -- must stop first.");
                return false;
            }
        }


        Log.d(TAG, "Starting for " + name);
        // If we are ready, then just call back, otherwise start the thread.


        // add this bus to the list of running instances and add any callbacks
        instanceNames.add(name);
        addInstanceCallbacks(name, readyCallback, terminatedCallback);




        if (busSetupRunnable.isSetup()) {
            busSetupRunnable.teardown();
        }

        //String names = "";
        //for (String iname : instanceNames) {
        //    names = names + iname + " ";
        //}
        //Log.v(TAG, "Names open = " + names);

        if (!instanceNames.contains("CAN")) { // CAN was not started
            busSetupRunnable.setDefaultCharacteristics(); // this puts us in listen mode and also filters out all rx CAN packets
        }

        // since we haven't already, we should set-up now
        return busSetupRunnable.setup();
    } // start()


    //////////////////////////////////////////////////
    // stop()
    //  stop a bus
    //   name: "CAN"
    //////////////////////////////////////////////////
    public void stop(String name) {


        if (isUnitTesting) {
            // since we are unit testing and not on realy device, even creating the CanbusInterface will fail fatally,
            //  so we need to skip this in testing
            Log.e(TAG, "UnitTesting is on. Will not stop bus.");
            return;
        }


        if (!instanceNames.contains(name)) {
            //Log.d(TAG, "" + name + " never started. Stop ignored -- must start first");
            return;
        }

        Log.d(TAG, "Stopping for " + name);

        // remove from list of active buses and remove all callbacks for the bus
        instanceNames.remove(name);
        removeInstanceCallbacks(name);


        // we MUST teardown, even if we are not the last bus, because that is the only
        //  way we can get any waiting socket reads to error and complete.

        if (busSetupRunnable != null)
            busSetupRunnable.teardown();

        // If we still have buses remaining, we must re-setup and call the ready callbacks again

        if (!instanceNames.isEmpty()) {
            Log.d(TAG, " Restarting for other buses");
            if (busSetupRunnable != null) {
                busSetupRunnable.setup(); // this will also call callback array
            }

        }

    } // stop()


    //////////////////////////////////////////////////
    // stopAll()
    //  stops ALL buses .. this should be used instead of stop() if we know that we will be stopping all buses
    //      b/c this will prevent re-formation of any buses that you are not explicitly stopping in the regular stop() call
    //////////////////////////////////////////////////
    public void stopAll() {
        Log.d(TAG, "Stopping All buses");


        // remove from list of active buses and remove all callbacks
        instanceNames.clear();
        clearInstanceCallbacks();

        // teardown the socket & interface
        if (busSetupRunnable != null)
            busSetupRunnable.teardown();
    }

    //////////////////////////////////////////////////
    // restart()
    //  restarts the bus
    //////////////////////////////////////////////////
    public boolean restart(String replaceCallbacksName,
                           Runnable newReadyCallback,
                           Runnable newTerminatedCallback) {

        if (isUnitTesting) {
            // since we are unit testing and not on real device, even creating the CanbusInterface will fail fatally,
            //  so we need to skip this in testing
            Log.e(TAG, "UnitTesting is on. Will not restart bus.");
            return false;
        }

        if (busSetupRunnable == null) {
            Log.e(TAG, "busSetupRunnable is null!! Cannot restart.");
            return false;
        }

        Log.d(TAG, "Restarting buses");

        // If we are ready, then just call back, otherwise start the thread.

        if (replaceCallbacksName != null) {
            removeInstanceCallbacks(replaceCallbacksName);
            addInstanceCallbacks(replaceCallbacksName, newReadyCallback, newTerminatedCallback);
        }


        // we must teardown and restart the interface

        if (busSetupRunnable != null)
            busSetupRunnable.teardown();

        // Sleep to avoid filter dropping issue.
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (busSetupRunnable != null) {
            busSetupRunnable.setup(); // this will also call callback array
        }


        return true;

    } // restart()


    ///////////////////////////////////////////////////
    // getCANSocket()
    //  return the socket that this wrapper created
    ///////////////////////////////////////////////////
    public CanSocket getCANSocket() {
        return mSocket;
    } // getCANSocket()


    ///////////////////////////////////////////////////
    // getCANBitrate()
    //  return the bitrate for can that is being used (0 if no bitrate in use)
    ///////////////////////////////////////////////////
    public int getCANBitrate() {
        if (busSetupRunnable == null) return 0; // no bitrate -- class doesnt even exit
        if (!busSetupRunnable.isSetup()) return 0; // no bitrate -- socket wasn't even created yet

        return busSetupRunnable.bitrate;
    }




    ///////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////
    //
    // Actual Background work of setting up or tearing down a bus
    //      These are private: Do not call these from outside this class
    //
    ///////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////



    //////////////////////////////////////////////////
    // addInstanceCallbacks()
    //      adds callbacks for a particular bus name (like when shutting down that bus)
    //////////////////////////////////////////////////
    void addInstanceCallbacks(String name, Runnable readyCB, Runnable terminatedCB) {

        removeInstanceCallbacks(name); // we need to remove the old ones for that bus, before adding the new ones

        if (readyCB != null) {
            callbackStruct readyStruct = new callbackStruct(name, readyCB);
            callbackArrayReady.add(readyStruct);
        }

        if (terminatedCB != null) {
            callbackStruct terminatedStruct = new callbackStruct(name, terminatedCB);
            callbackArrayTerminated.add(terminatedStruct);
        }
    }



    //////////////////////////////////////////////////
    // removeInstanceCallbacks()
    //      removes the callbacks for a particular bus name (like when shutting down that bus)
    //////////////////////////////////////////////////
    void removeInstanceCallbacks(String name) {
        // remove callbacks for this bus
        Iterator<callbackStruct> it = callbackArrayReady.iterator();
        while (it.hasNext()) {
            if (it.next().busName.equals(name)) {
                it.remove();
                // If you know it's unique, you could `break;` here
            }
        }

        it = callbackArrayTerminated.iterator();
        while (it.hasNext()) {
            if (it.next().busName.equals(name)) {
                it.remove();
                // If you know it's unique, you could `break;` here
            }
        }

    }


    //////////////////////////////////////////////////
    // clearInstanceCallbacks()
    //  removes ALL callbacks for ALL buses (like when shutting down ALL buses)
    //////////////////////////////////////////////////
    void clearInstanceCallbacks() {
        callbackArrayReady.clear();
        callbackArrayTerminated.clear();
    }

    //////////////////////////////////////////////////
    // callbackNowReady()
    //  calls the ready callbacks to let others know their socket is ready
    //////////////////////////////////////////////////
    void callbackNowReady() {
        if (callbackHandler != null) {
            for (callbackStruct cs : callbackArrayReady) {
                // make sure there is only one of these calls in the post queue at any given time
                callbackHandler.removeCallbacks(cs.callback);
                callbackHandler.post(cs.callback);
            }
        }
    } // callbackNowReady()

    //////////////////////////////////////////////////
    // callbackNowTerminated()
    //  calls the terminated callbacks to let others know their socket has gone away
    //////////////////////////////////////////////////
    void callbackNowTerminated() {
        if (callbackHandler != null) {
            for (callbackStruct cs : callbackArrayTerminated) {
                // make sure there is only one of these calls in the post queue at any given time
                callbackHandler.removeCallbacks(cs.callback);
                callbackHandler.post(cs.callback);
            }
        }
    } // callbackNowTerminated()




    ////////////////////////////////////////////////////////
    // BusSetupRunnable :
    // this sets up or tears down the socket + interface
    //  It is separated into own class so it can be run on its own thread for testing.
    ////////////////////////////////////////////////////////
    class BusSetupRunnable {
        volatile boolean isClosed = false;
        volatile boolean isSocketReady = false;
        boolean listen_only = true; // default listen_only
        int bitrate = 250000; // default bit rate
        int[] ids = null;
        int[] masks = null;

        int socketNum, idxNum;
        Class<?> canServiceClass;
        Method bitrateMethod;
        Method mode;
        Method link;
        Method open;
        Method bind;
        Method close;
        Method config;
        Method send;
        Method receiveMsg;
        Method mask;
        Object canService;


        BusSetupRunnable() {
            setDefaultCharacteristics();
            setReflection();

        }
        @SuppressLint("PrivateApi")
        private void setReflection(){
            try {
                canServiceClass = Class.forName("com.android.server.net.CanbusService");
                bitrateMethod = canServiceClass.getDeclaredMethod("bitrate", int.class);
                mode = canServiceClass.getDeclaredMethod("mode", String.class);
                link = canServiceClass.getDeclaredMethod("link", String.class);
                open = canServiceClass.getDeclaredMethod("open", String.class);
                bind = canServiceClass.getDeclaredMethod("bind", String.class, int.class);
                close = canServiceClass.getDeclaredMethod("close", int.class);
                config = canServiceClass.getDeclaredMethod("config", String.class, IntBuffer.class, IntBuffer.class, int.class, int.class, int.class, int.class, int.class);
                send = canServiceClass.getDeclaredMethod("send", int.class,int.class,int.class,byte[].class);
                receiveMsg =canServiceClass.getDeclaredMethod("recvmsg", int.class, int.class, IntBuffer.class,
                        IntBuffer.class, LongBuffer.class, IntBuffer.class, ByteBuffer.class);
                mask=canServiceClass.getDeclaredMethod("mask",IntBuffer.class,IntBuffer.class);
                canService =canServiceClass.getConstructor().newInstance();
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                     InvocationTargetException | java.lang.InstantiationException e) {
                e.printStackTrace();
            }
        }


        public void setNormalMode() {
            listen_only = false;
        }



        public void setCharacteristics(boolean new_listen_only, int new_bitrate, int[] ids, int[] masks) {
            // these take effect at next Setup()
            listen_only = new_listen_only;
            bitrate = new_bitrate;
            this.ids=ids;
            this.masks=masks;
        }


        void setDefaultFilters() {
            // create default filters to block all CAN packets that arent all 0s
            ids=new int[2];
            masks=new int[2];
            ids[0]=0x80000000;
            masks[0]=0x3FFFFFF;
            masks[1]=0x7FF;
        }


        public void setDefaultCharacteristics() {
            // these take effect at next Setup()
            listen_only = true;
            bitrate = 250000;
            canNumber = 2;
            setDefaultFilters(); // block everything
        }


        public boolean isSetup() {
            return isSocketReady;
        }

        // setup() : External call to setup the bus
        public boolean setup() {


            /*
            // Do the setup in a separate thread:
            Thread setupThread = new Thread(busSetupRunnable);
            setupThread.start();
            */
            return doInternalSetup();
        }

        // teardown () : External call to teardown the bus
        public void teardown() {

            doInternalTeardown(canNumber);

            // do the teardown in a separate thread:
            // cancelThread = true;
        }


        /** @noinspection DataFlowIssue*/
        ///////////////////////////////////////////
        // doInternalSetup()
        //  does all setup steps
        //  returns true if setup was successful, otherwise false
        ///////////////////////////////////////////
        boolean doInternalSetup() {
            try {
                IntBuffer hwFilter= ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer();
                IntBuffer hwMask =ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asIntBuffer();
                IntBuffer swIds = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
                IntBuffer swFilter = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();

                hwFilter.put(0).put(0).put(0).put(0).put(0).put(0);
                hwFilter.rewind();
                hwMask.put(0).put(0);
                hwMask.rewind();


                link.invoke(canService, "down");
                SystemClock.sleep(200);

                bitrateMethod.invoke(canService, bitrate);
                SystemClock.sleep(200);

                mode.invoke(canService, listen_only ? "listen-only" : "normal");
                SystemClock.sleep(200);

                android.util.Log.e("MASK", String.valueOf(mask.invoke(canService, hwMask, hwFilter)));
                SystemClock.sleep(200);


                link.invoke(canService, "up");
                SystemClock.sleep(200);

                socketNum = (int) open.invoke(canService, "can0");
                idxNum = (int) bind.invoke(canService, "can0", socketNum);
                config.invoke(canService, "can0", swIds, swFilter, 0, 0, socketNum, 0, 0);
                mSocket = new CanSocket(socketNum,idxNum,canService,send,receiveMsg);
                return true;
            } catch (Exception e){
                e.printStackTrace();
                return false;
            }
        } // doInternalSetup()

        /////////////////////////////////////////////
        // doInternalTeardown()
        //  does all teardown steps
        /////////////////////////////////////////////
        void doInternalTeardown(int canNumber) {
            try {
                close.invoke(canService, socketNum);
                link.invoke(canService, "down");
            } catch (Exception e){
                e.printStackTrace();
            }
            mSocket=null;

            isSocketReady = false;
            // Notify the main threads that our socket is terminated
            callbackNowTerminated();
        } // doInternalTeardown()
    } // BusSetupRunnable

} // VehicleBusCommWrapper
