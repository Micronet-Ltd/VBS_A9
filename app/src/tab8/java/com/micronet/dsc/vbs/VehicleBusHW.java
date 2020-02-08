//////////////////////////////////////////////////////////////////
// This contains the normalization between different HW API implementations
//////////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;

import android.graphics.Color;
import android.support.v4.app.NotificationCompat;

import com.micronet.canbus.CanbusException;
import com.micronet.canbus.CanbusFilter;
import com.micronet.canbus.CanbusFlowControl;
import com.micronet.canbus.CanbusFramePort2;
import com.micronet.canbus.CanbusFrameType;
import com.micronet.canbus.CanbusInterface;
import com.micronet.canbus.CanbusSocket;

import java.util.ArrayList;
import java.util.Locale;

import static com.micronet.dsc.vbs.VehicleBusService.service;

class VehicleBusHW {
    public static final String TAG = "ATS-VBS-HW";

    private static final int CAN_PORT1 = 2; // value 2 = CAN1
    private static final int CAN_PORT2 = 3; // value 3 = CAN2

    ///////////////////////////////////////////
    ///////////////////////////////////////////
    /////// Abstraction Classes for HW ////////
    ///////////////////////////////////////////
    ///////////////////////////////////////////

    /**
     * Hardware Abstraction Wrapper for Canbus Interface on the Tab8.
     */
    public static class InterfaceWrapper extends CanbusInterface {
        public CanbusInterface canbusInterface;

        public InterfaceWrapper(CanbusInterface i) {
            canbusInterface = i;
        }
    }

    /**
     * Hardware Abstraction Wrapper for Canbus Socket on the Tab8.
     */
    public static class SocketWrapper {
        public CanbusSocket canbusSocket;

        public SocketWrapper(CanbusSocket s) {
            canbusSocket = s;
        }
    }

    /**
     * Hardware Abstraction Wrapper for Canbus 1 Frame on the Tab8.
     */
    public static class CANFrame extends com.micronet.canbus.CanbusFramePort1 {
        public CANFrame(int id, byte[] data, CANFrameType type) {
            super(id, data, CANFrameType.upcast(type));
        }

        public CANFrame(CanbusFramePort2 frame) {
            super(frame.getId(), frame.getData(), frame.getType());
        }

        public static CANFrame downcast(com.micronet.canbus.CanbusFramePort1 mFrame) {
            return new CANFrame(mFrame.getId(), mFrame.getData(), CANFrameType.downcast(mFrame.getType()));
        }

        public int getId() {
            return super.getId();
        }

        public byte[] getData() {
            return super.getData();
        }

    }

    /**
     * Hardware Abstraction Wrapper for Canbus 2 Frame on the Tab8.
     */
    public static class CAN2Frame extends com.micronet.canbus.CanbusFramePort2 {
        public CAN2Frame(int id, byte[] data, CANFrameType type){
            super(id, data, CANFrameType.upcast(type));
        }

        public CAN2Frame(CANFrame frame) {
            super(frame.getId(), frame.getData(), frame.getType());
        }

        public static CAN2Frame downcast(com.micronet.canbus.CanbusFramePort2 mFrame) {
            return new CAN2Frame(mFrame.getId(), mFrame.getData(), CANFrameType.downcast(mFrame.getType()));
        }

        public int getId() {
            return super.getId();
        }

        public byte[] getData() {
            return super.getData();
        }
    }

    /**
     * Hardware Abstraction Wrapper for Canbus Socket on the Tab8.
     */
    public static class CANSocket {
        CanbusSocket socket;
        int canNumber;

        public CANSocket(SocketWrapper in, int port) {
            if (in != null) {
                socket = in.canbusSocket;
            } else  {
                socket = null;
            }

            canNumber = port;
        }

        public CANFrame read() {
            if (canNumber == CAN_PORT1) {
                return CANFrame.downcast(socket.readPort1());
            } else {
                return new CANFrame(socket.readPort2());
            }
        }

        public void write(CANFrame frame) {
            if (canNumber == CAN_PORT1) {
                socket.write1939Port1(frame);
            } else {
                socket.write1939Port2(new CAN2Frame(frame));
            }
        }
    } // CANSocket

    /**
     * Hardware Abstraction Wrapper for Canbus Frame Type on the Tab8.
     */
    public enum CANFrameType {
        STANDARD,
        EXTENDED;

        public static CANFrameType downcast(CanbusFrameType mFrame) {
            if (mFrame == CanbusFrameType.EXTENDED) return EXTENDED;
            return STANDARD;
        }

        public static CanbusFrameType upcast(CANFrameType frame) {
            if (frame == EXTENDED) return CanbusFrameType.EXTENDED;
            return CanbusFrameType.STANDARD;
        }

        public static CANFrameType integerConversion(int type) {
            return type == 0 ? STANDARD : EXTENDED;
        }

        public static int integerConversion(CANFrameType type) {
            return type == STANDARD ? 0 : 1;
        }
    }

    /**
     * Hardware Abstraction Wrapper for Canbus Filter on the Tab8.
     */
    public static class CANHardwareFilter extends CanbusFilter {

        /**
         * Convert from abstracted filter to Vehicle Bus Library filter on the Tab8.
         */
        public static CanbusFilter[] upcast(CANHardwareFilter[] canHardwareFilters) {
            CanbusFilter[] canbusfilterArray = new CanbusFilter[canHardwareFilters.length];

            for (int i = 0; i < canHardwareFilters.length; i++) {
                canbusfilterArray[i] = new CanbusFilter(canHardwareFilters[i].getId(), canHardwareFilters[i].getMask(), canHardwareFilters[i].getFilterMaskType());
            }

            return canbusfilterArray;
        }

        public CANHardwareFilter(int id, int mask, CANFrameType type) {
            super(id, mask, type.ordinal());
        }
    }

    /**
     * Hardware Abstraction Wrapper for Canbus Flow Control on the Tab8.
     */
    public static class CANFlowControl extends CanbusFlowControl {

        /**
         * Convert from abstracted flow control to Vehicle Bus Library flow control on the Tab8.
         */
        public static CanbusFlowControl[] upcast(CANFlowControl[] canFlowControls) {
            CanbusFlowControl[] canbusFlowControls = new CanbusFlowControl[canFlowControls.length];

            for (int i = 0; i < canFlowControls.length; i++) {
                canbusFlowControls[i] = new CanbusFlowControl(canFlowControls[i].getSearchId(), canFlowControls[i].getResponseId(), canFlowControls[i].getFlowMessageType(), canFlowControls[i].getFlowDataLength(), canFlowControls[i].getDataBytes());
            }

            return canbusFlowControls;
        }

        public CANFlowControl(int searchId, int responseId, byte[] data, CANFrameType type) {
            super(searchId, responseId, type.ordinal(), data.length, data);
        }
    }

    ///////////////////////////////////////////////////
    ///////////////////////////////////////////////////
    /////// Functions for Interface Abstraction ///////
    ///////////////////////////////////////////////////
    ///////////////////////////////////////////////////

    /**
     * Hardware Abstraction for creating an interface on the Tab8.
     */
    InterfaceWrapper createInterface(int canNumber, boolean listen_only, int bitrate, CANHardwareFilter[] hardwareFilters, ArrayList<VehicleBusHW.CANFlowControl> flowControls) {
        Log.v(TAG, "createInterface: new()");

        CanbusInterface canInterface;
        try {
            canInterface = new CanbusInterface();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create new CanbusInterface() " + e.toString());
            // TODO Need to handle this failure correctly.
            return null;
        }

        // Get filters and flow controls
        CanbusFilter[] filterArray = setFilters(hardwareFilters);
        CanbusFlowControl[] flowControlMessages = setFlowControlMessages(flowControls);

        Log.v(TAG, String.format(Locale.getDefault(), "createInterface: create(%b, %d, true, filterArray, %d, flowControlMessages)",
                listen_only, bitrate / 1000, canNumber));
        Log.d(TAG, "bitrate = " + bitrate);

        // Try to create interface.
        try {
            canInterface.create(listen_only, bitrate,true, filterArray, canNumber, flowControlMessages);
        } catch (CanbusException e) {
            Log.e(TAG, "Can" +(canNumber-1)+ ": Unable to call create(" + listen_only +") | CanNumber = (" +  canNumber+ ") for CanbusInterface()" + e.toString() + ". Num=" + e.getErrorCode());

            switch (e.getErrorCode()) {
                case CanbusException.INVALID_PARAMETERS:
                    service.updateForegroundNotification("Error creating Canbus Interface for CAN "+(canNumber-1)+". Invalid parameters.", Color.RED, NotificationCompat.PRIORITY_DEFAULT);
                    break;
                case CanbusException.PORT_DOESNT_EXIST:
                    service.updateForegroundNotification("Error creating Canbus Interface for CAN "+(canNumber-1)+". Port doesn't exist.", Color.RED, NotificationCompat.PRIORITY_DEFAULT);
                    break;
                default:
                    service.updateForegroundNotification("Error creating Canbus Interface for CAN "+(canNumber-1)+".", Color.RED, NotificationCompat.PRIORITY_DEFAULT);
                    break;
            }

            return null;
        }

        service.updateForegroundNotification("CAN " +(canNumber-1)+ ": Created Interface @" + (bitrate/1000) + "k " + (listen_only ? "in read only mode. Running.": "in normal mode. Running."), Color.GREEN, NotificationCompat.PRIORITY_DEFAULT);
        Log.d(TAG, "Interface created @ " + bitrate + "kb " + (listen_only ? "READ-ONLY" : "READ-WRITE"));
        return new InterfaceWrapper(canInterface);
    } // createInterface()

    /**
     * Hardware abstraction for removing interface on the Tab8.
     */
    void removeInterface(int canNumber, InterfaceWrapper wrappedInterface) {

        if(canNumber == CAN_PORT1){
            try {
                wrappedInterface.canbusInterface.removeCAN1();
            } catch (Exception e) {
                Log.e(TAG, "Can1: Unable to remove CanbusInterface() " + e.toString());
            }
        }else if (canNumber == CAN_PORT2){

            try {
                wrappedInterface.canbusInterface.removeCAN2();
            } catch (Exception e) {
                Log.e(TAG, "Can2: Unable to remove CanbusInterface() " + e.toString());
            }
        }
    } // removeInterface()

    SocketWrapper createSocket(int canNumber, InterfaceWrapper wrappedInterface) {
        CanbusSocket socket = null;

        // create a new socket for Can1.
        if(canNumber == CAN_PORT1) {
            try {
                socket = wrappedInterface.canbusInterface.createSocketCAN1();
                if (socket == null) {
                    Log.e(TAG, "Socket not created .. returned NULL");
                    return null;
                }else{
                    Log.d(TAG, "Can1: Socket created: " + socket);
                }
                // set socket options here
            } catch (Exception e) {
                Log.e(TAG, "Exception creating Socket: " + e.toString(), e);
                return null;
            }
        }else if(canNumber == CAN_PORT2){ //create a new socket for Can2.
            try{
                socket = wrappedInterface.canbusInterface.createSocketCAN2();
                if(socket == null){
                    Log.e(TAG, "Socket not created .. return NULL");
                    return null;
                }else{
                    Log.d(TAG, "Can2: Socket created: " + socket);
                }
            }catch(Exception e){
                Log.e(TAG, "Exception creating Socket: " + e.toString(), e);
            }
        }

        return new SocketWrapper(socket);
    } // createSocket()


    boolean openSocket(int canNumber, SocketWrapper wrappedSocket, boolean discardBuffer) {
        if(canNumber == CAN_PORT1) {// Opening socket for Can1
            try {
                wrappedSocket.canbusSocket.openCan1();
                Log.d(TAG, "Can1: Socket Opened");
            } catch (Exception e) {
                Log.e(TAG, "Exception opening Socket: " + e.toString(), e);
                return false;
            }
        }else if (canNumber == CAN_PORT2){// Opening socket for Can2
            try{
                wrappedSocket.canbusSocket.openCan2();
                Log.d(TAG, "Can2: Socket Opened");
            }catch(Exception e){
                Log.e(TAG, "Exception opening Socket: " + e.toString(), e);
                return false;
            }
        }

        // We have to discard when opening a socket at a new bitrate, but this causes a 3 second gap in frame reception
        if (discardBuffer) {
            try {
                wrappedSocket.canbusSocket.discardInBuffer();
                Log.d(TAG, "Socket discarded");
            } catch (Exception e) {
                Log.e(TAG, "Exception discarding Socket buffer: " + e.toString(), e);
                return false;
            }
        }

        return true;
    } // openSocket


    void closeSocket(int canNumber, SocketWrapper wrappedSocket) {

        // close the socket
        if(canNumber == CAN_PORT1){
            try {
                Log.d(TAG, "Trying to close the socket..");
                if (wrappedSocket.canbusSocket != null)
                    wrappedSocket.canbusSocket.close1939Port1();
                wrappedSocket.canbusSocket = null;
                wrappedSocket = null;
                Log.d(TAG, "Can1: Socket Closed");
            } catch (Exception e) {
                Log.e(TAG, "Exception closeSocket()" + e.toString(), e);
            }
        }else if(canNumber == CAN_PORT2){
            try {
                Log.d(TAG, "Trying to close the socket..");
                if (wrappedSocket.canbusSocket != null)
                    wrappedSocket.canbusSocket.close1939Port2();
                wrappedSocket.canbusSocket = null;
                wrappedSocket = null;
                Log.d(TAG, "Can2: Socket Closed");
            }catch(Exception e){
                Log.e(TAG, "Exception closeSocket()" + e.toString(), e);
            }
        }
    } // closeSocket();

    /**
     * Converts abstracted CANHardwareFilter[] to Vehicle Bus Library CanbusFlowControl[].
     */
    private CanbusFlowControl[] setFlowControlMessages(ArrayList<VehicleBusHW.CANFlowControl> flowControls){
        if (flowControls != null) {
            // Display and return VBL flow controls.
            showFlowControls(flowControls);
            return CANFlowControl.upcast(flowControls.toArray(new CANFlowControl[0]));
        } else {
            return null;
        }
    }

    /**
     * Converts abstracted CANHardwareFilter[] to Vehicle Bus Library CanbusFilter[].
     */
    private CanbusFilter[] setFilters(CANHardwareFilter[] hardwareFilters) {
        if (hardwareFilters != null) {
            // Display and return VBL filters.
            showHardwareFilters(hardwareFilters);
            return CANHardwareFilter.upcast(hardwareFilters);
        } else {
            return null;
        }
    }

    /**
     * Display filters in logcat.
     */
    private void showHardwareFilters(CANHardwareFilter[] hardwareFilters) {
        StringBuilder filter_str = new StringBuilder();

        int i = 0;
        for (CanbusFilter filter : hardwareFilters) {
            filter_str.append("Filter " + i + ": x" + String.format("%X", filter.getId()) + ", M:x" + String.format("%X", filter.getMask()) + ", T:" + filter.getFilterMaskType() + "\n");
            i++;
        }

        Log.d(TAG, "Filters = {\n" + filter_str.toString() + "}");
    }

    /**
     * Display flow controls in logcat.
     */
    private void showFlowControls(ArrayList<VehicleBusHW.CANFlowControl> flowControls) {
        StringBuilder flowControlStr = new StringBuilder();

        int i = 0;
        for (CANFlowControl flowControl : flowControls) {
            flowControlStr.append(String.format(Locale.getDefault(), "Flow Control %d: searchId-%X, responseId-%X, T-%d, Length-%d\n",
                    i++, flowControl.getSearchId(), flowControl.getResponseId(), flowControl.getFlowMessageType(), flowControl.getFlowDataLength()));
        }

        Log.d(TAG, "Flow Controls = {\n" + flowControlStr.toString() + "}");
    }

    ////////////////////////////////////////////
    ////////////////////////////////////////////
    //////////////// J1708 Code ////////////////
    ////////////////////////////////////////////
    ////////////////////////////////////////////

    // TODO Implement all J1708 code.
    public static class J1708Frame  {
        public J1708Frame(int priority, int id, byte[] data) {
            // do Nothing
        }

        public static J1708Frame downcast(com.micronet.canbus.J1708Frame mFrame) {
            return null; // do Nothing
        }

        public int getId() {
            return 0; // do Nothing
        }

        public int getPriority() {
            return 0; // do Nothing
        }

        public byte[] getData() {
            return null; // do Nothing
        }

    }

    public static class J1708Socket {

        public J1708Socket(SocketWrapper in) {
            // do Nothing
        }

        public J1708Frame readJ1708() {
            // do Nothing
            return null;
        }

        public void writeJ1708(J1708Frame frame) {
            // do Nothing
        }
    }

    public static boolean isJ1708Supported() {

        return false; // Never Supported

    }
} // VehicleBusHW
