//////////////////////////////////////////////////////////////////
// This contains the normalization between different HW API implementations
//////////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;

import com.micronet.canbus.CanbusFilter;
import com.micronet.canbus.CanbusFlowControl;
import com.micronet.canbus.CanbusFrameType;
import com.micronet.canbus.CanbusInterface;
import com.micronet.canbus.CanbusSocket;

class VehicleBusHW {
    public static final String TAG = "ATS-VBS-HW";

    static final int CAN_PORT1 = 2; // value 2 = CAN1
    static final int CAN_PORT2 = 3; // value 3 = CAN2

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
     * Canbus Frame Wrapper for Canbus Frames on the Tab8.
     */
    public static class CANFrame extends com.micronet.canbus.CanbusFramePort1 {
        public CANFrame(int id, byte[] data, CANFrameType type) {
            super(id, data, CANFrameType.upcast(type));
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

    /**Can Frame wrapper for can2**/
    public static class CAN2Frame extends com.micronet.canbus.CanbusFramePort2{
        public CAN2Frame(int id, byte[] data, CANFrameType type){
            super(id, data, CANFrameType.upcast(type));
        }

        public static CAN2Frame downcast(com.micronet.canbus.CanbusFramePort2 mFrame){
            return new CAN2Frame(mFrame.getId(), mFrame.getData(), CANFrameType.downcast(mFrame.getType()));
        }

        public int getId(){
            return super.getId();
        }

        public byte[] getData(){
            return super.getData();
        }
    }

    public static class CANSocket {
        CanbusSocket socket;

        public CANSocket(SocketWrapper in) {
            if (in != null) {
                socket = in.canbusSocket;
            } else  {
                socket = null;
            }
        }

        public CANFrame read() {
            return CANFrame.downcast(socket.readPort1());
        }


        public void write(CANFrame frame) {
            // Todo: write1939Port2(frame)
            socket.write1939Port1(frame);
        }

        /**
         * Read and write for Can2
         * **/
        public CAN2Frame readCan2(){
            return CAN2Frame.downcast(socket.readPort2());
        }

        public void writeCan2(CAN2Frame frame){
            socket.write1939Port2(frame);
        }
    } // CANSocket

    public enum CANFrameType {
        STANDARD,
        EXTENDED;

        public static CANFrameType downcast(com.micronet.canbus.CanbusFrameType mFrame) {
            if (mFrame == com.micronet.canbus.CanbusFrameType.EXTENDED) return EXTENDED;
            return STANDARD;
        }

        public static com.micronet.canbus.CanbusFrameType upcast(CANFrameType frame) {
            if (frame == EXTENDED) return com.micronet.canbus.CanbusFrameType.EXTENDED;
            return com.micronet.canbus.CanbusFrameType.STANDARD;
        }
    }

    public static class CANHardwareFilter  extends CanbusFilter {
        public static CanbusFilter[] upcast(CanbusFilter[] canHardwareFilters) {
            CanbusFilter canbusfilterArray[] = new CanbusFilter[canHardwareFilters.length];

            for (int i = 0; i < canHardwareFilters.length; i++) {
                new CanbusFilter(canHardwareFilters[0].getId(), canHardwareFilters[0].getMask(), canHardwareFilters[0].getFilterMaskType());
            }

            return canbusfilterArray;
        }

        public CANHardwareFilter(int id, int mask, CANFrameType type) {
            super(id, mask, type.ordinal());
        }
    }

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

    private void showHardwareFilters(CANHardwareFilter[] hardwareFilters) {
        StringBuilder filter_str = new StringBuilder();

        int i = 0;
        for (CanbusFilter filter : hardwareFilters) {
            filter_str.append("Filter " + i + ": x" + String.format("%X", filter.getId()) + ", M:x" + String.format("%X", filter.getMask()) + ", T:" + filter.getFilterMaskType() + "\n");
            i++;
        }

        Log.d(TAG, "Filters = {\n" + filter_str.toString() + "}");
    }

    InterfaceWrapper createInterface(int canNumber, boolean listen_only, int bitrate, CANHardwareFilter[] hardwareFilters) {
        CanbusInterface canInterface;
        //Todo: Adjusted parameter for adding CanNumber recognition, make sure to make changes wherever this method get called.

        Log.v(TAG, "createInterface: new()");
        try {
            canInterface = new CanbusInterface();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create new CanbusInterface() " + e.toString());
            return null;
        }

        // Set up flow control and filters.
        CanbusFilter[] filterArray = setFilters(hardwareFilters);
        CanbusFlowControl[] flowControlMessages = setFlowControlMessages();

        int bitrate_kb = bitrate / 1000;

        /**Create Can1 Interface**/
        if(canNumber == CAN_PORT1){
        Log.v(TAG, "createInterface: create(" + listen_only + "," +
                bitrate_kb + ",true, filterArray," + CAN_PORT1 + ",flowControlMessages)");

        Log.d(TAG, "bitrate = " + bitrate);
        try {
            canInterface.create(listen_only,
                    bitrate,
                    true,
                    filterArray, //
                    CAN_PORT1,
                    flowControlMessages);

        } catch (Exception e) {
            Log.e(TAG, "Can1: Unable to call create(" + listen_only +") | CanNumber = (" +  canNumber+ ") for CanbusInterface()" + e.toString());
            // TODO Tell ATS create failed.
            return null;
            }
        }
        else if(canNumber == CAN_PORT2){
            Log.v(TAG, "createInterface: create(" + listen_only + "," +
                    bitrate_kb + ",true, filterArray," + CAN_PORT2 + ",flowControlMessages)");
            try{
                canInterface.create(listen_only,
                        bitrate,
                        true,
                        filterArray,
                        CAN_PORT2,
                        flowControlMessages);

            }catch(Exception e){
                Log.e(TAG, "Can2: Unable to call create(" + listen_only +") | CanNumber = (" +  canNumber+ ") for CanbusInterface()" + e.toString());
                return null;
            }
        }

        Log.d(TAG, "Interface created @ " + bitrate + "kb " + (listen_only ? "READ-ONLY" : "READ-WRITE"));
        return new InterfaceWrapper(canInterface);
    } // createInterface()

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
        // we have to discard when opening a socket at a new bitrate, but this causes a 3 second gap in frame reception

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
            Log.e(TAG, "Can1: Socket Closed");
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
                Log.e(TAG, "Can2: Socket Closed");
            }catch(Exception e){
                Log.e(TAG, "Exception closeSocket()" + e.toString(), e);
            }
        }
    } // closeSocket();


    //////////////////////////////////////////////////////////////////
    // isJ1708Supported()
    //  does the hardware support J1708 ?
    //////////////////////////////////////////////////////////////////
    public static boolean isJ1708Supported() {

        return false; // Never Supported

    } // isJ1708Supported?


    private CanbusFlowControl[] setFlowControlMessages(){
        return null;
    }

    private CanbusFilter[] setFilters(CANHardwareFilter[] hardwareFilters) {
        CanbusFilter[] filterArray = null;
        if (hardwareFilters != null) {
            showHardwareFilters(hardwareFilters);

            filterArray = new CanbusFilter[hardwareFilters.length];
            for (int i = 0 ; i < hardwareFilters.length; i++) {
                filterArray[i] = new CanbusFilter(hardwareFilters[i].getId(), hardwareFilters[i].getMask(), hardwareFilters[i].getFilterMaskType());
            }
        }

        return filterArray;
    }
} // VehicleBusHW
