//////////////////////////////////////////////////////////////////
// This contains the normalization between different HW API implementations
//////////////////////////////////////////////////////////////////

package com.micronet.dsc.vbs;


import com.micronet.canbus.CanbusFlowControl;
import com.micronet.canbus.CanbusHardwareFilter;
import com.micronet.canbus.CanbusInterface;
import com.micronet.canbus.CanbusSocket;

class VehicleBusHW {
    public static final String TAG = "ATS-VBS-HW";



    public static class InterfaceWrapper extends CanbusInterface {
        public CanbusInterface canbusInterface;

        public InterfaceWrapper(CanbusInterface i) {
            canbusInterface = i;
        }
    }

    public static class SocketWrapper {
        public CanbusSocket canbusSocket;

        public SocketWrapper(CanbusSocket s) {
            canbusSocket = s;
        }
    }


    ///////////////////////////////////////////////////////////
    // Intermediate Access Layer:
    //  Wrappers for classes/methods in canbus library that will be needed by other classes
    ///////////////////////////////////////////////////////////
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

    };

    public static class CANSocket {

        com.micronet.canbus.CanbusSocket socket;

        public CANSocket(SocketWrapper in) {
            if (in != null)
                socket = in.canbusSocket;
            else socket = null;
        }

        public CANFrame read() {

            return CANFrame.downcast(socket.readPort1());
        }

        public void write(CANFrame frame) {
            socket.write1939Port1(frame);
        }
    } // CANSocket

    public enum CANFrameType {
        EXTENDED,
        STANDARD;

        public static CANFrameType downcast(com.micronet.canbus.CanbusFrameType mFrame) {
            if (mFrame == com.micronet.canbus.CanbusFrameType.EXTENDED) return EXTENDED;
            return STANDARD;
        }

        public static com.micronet.canbus.CanbusFrameType upcast(CANFrameType frame) {
            if (frame == EXTENDED) return com.micronet.canbus.CanbusFrameType.EXTENDED;
            return com.micronet.canbus.CanbusFrameType.STANDARD;
        }

    }

    public static class CANHardwareFilter  extends com.micronet.canbus.CanbusHardwareFilter {

/*
        public static int[] createMasksArray(int mask, int length) {
            int[] masks = new int[length];
            for (int i = 0; i < length; i++) {
                masks[i] = mask;
            }
            return masks;
        }
*/


        public static com.micronet.canbus.CanbusHardwareFilter[] upcast(CANHardwareFilter[] canHardwareFilters) {
            CanbusHardwareFilter canbusfilterArray[] = new CanbusHardwareFilter[canHardwareFilters.length];

            for (int i = 0; i < canHardwareFilters.length; i++) {
                new CanbusHardwareFilter(canHardwareFilters[0].getIds(), canHardwareFilters[0].getMask(), canHardwareFilters[0].getFilterMaskType());
            }

            return canbusfilterArray;
        }

        public static int[] createTypesArray(VehicleBusWrapper.CANFrameType type, int length) {
            int[] types = new int[length];
            for (int i = 0; i < length; i++) {
                types[i] = (type == CANFrameType.EXTENDED ?
                        com.micronet.canbus.CanbusHardwareFilter.EXTENDED :
                        com.micronet.canbus.CanbusHardwareFilter.STANDARD);
            }
            return types;
        }


        public static int[] createMasksArray(int mask) {
            int[] masks = new int[1];
            masks[0] = mask;
            return masks;

        }
/*
        public static int[] createTypesArray(VehicleBusWrapper.CANFrameType type) {
            int[] types = new int[1];
            types[0] = (type == CANFrameType.EXTENDED ?
                    com.micronet.canbus.CanbusHardwareFilter.EXTENDED :
                    com.micronet.canbus.CanbusHardwareFilter.STANDARD);
            return types;
        }
*/
        public CANHardwareFilter(int[] ids, int mask, VehicleBusWrapper.CANFrameType type) {

            super(ids, createMasksArray(mask), createTypesArray(type, ids.length));
            //super(ids, createMasksArray(mask, ids.length), createTypesArray(type, ids.length));

        }
    };





    // J1708 does not exist


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

    };

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

    } // J1708Socket()







    ///////////////////////////////////////////////////////////
    // Internal Access Layer:
    //  Wrappers for classes/methods in canbus library that will be needed only by VehicleBusWrapper class
    ///////////////////////////////////////////////////////////


    static final int CAN_PORT1 = 2; // value 2 = CAN1
    static final int CAN_PORT2 = 3; // value 3 = CAN2


    /**
     * Display the hardware filters to log file
     *
     */
    String showFilterIds(int[] ids) {
        String filter_str = "";
        for (int id : ids) {
            filter_str += "x" + String.format("%X", id) + " ";
        }
        return filter_str;
    }

    String showFilterMasks(int[] masks) {
        String filter_str = "";
        for (int id : masks) {
            filter_str += "M:x" + String.format("%X", id) + " ";
        }
        return filter_str;
    }

    String showFilterTypes(int[] types) {
        String filter_str = "";
        for (int typ : types) {
            filter_str += "T:" + typ + " ";
        }
        return filter_str;
    }


    void showHardwareFilters(CANHardwareFilter[] hardwareFilters) {
        String filter_str = "";
        for (CanbusHardwareFilter filter : hardwareFilters) {
            int[] ids = filter.getIds();
            int[] masks = filter.getMask();
            int[] types = filter.getFilterMaskType();
            filter_str += " (";

            filter_str += showFilterIds(ids);
            filter_str += showFilterMasks(masks);
            filter_str += showFilterTypes(types);

            filter_str += ") ";
        }

        Log.d(TAG, "Filters = " + filter_str);


    }


    InterfaceWrapper createInterface(boolean listen_only, int bitrate, CANHardwareFilter[] hardwareFilters) {




        CanbusInterface canInterface = null;


        Log.v(TAG, "createInterface: new()");
        try {
            canInterface = new CanbusInterface();
        } catch (Exception e) {
            Log.e(TAG, "Unable to create new CanbusInterface() " + e.toString());
            return null;
        }



        // Set up the filters

        CanbusHardwareFilter[] filterArray = setFilters(hardwareFilters);



        // TEMP: Set up FlowControl


        //CanbusFlowControl[] flowControlMessages = new CanbusFlowControl[1];

        //byte[] data1=new byte[]{0x10,0x34,0x56,0x78,0x1f,0x2f,0x3f,0x4f};

        //int[] searchIdArray1 = new int[]{0x18FEE000};
        //int[] responseIdArray1 = new int[]{0x18FEE018};
        //int[] typeArray1 = new int[]{CanbusFlowControl.EXTENDED};
        //int[] dataLengthArray1 = new int[]{8};
        //byte[][] dataArray1 = new byte[][]{data1};

        //flowControlMessages[0] = new CanbusFlowControl(searchIdArray1 ,responseIdArray1, typeArray1, dataLengthArray1, dataArray1);

        //int[] searchIdArray2 = new int[]{0x1CECFF00};
        //int[] responseIdArray2 = new int[]{0x1CECFF1C};
        //int[] typeArray2 = new int[]{CanbusFlowControl.EXTENDED};
        //int[] dataLengthArray2 = new int[]{8};
        //byte[][] dataArray2 = new byte[][]{data1};

        //flowControlMessages[1] = new CanbusFlowControl(searchIdArray2 ,responseIdArray2, typeArray2, dataLengthArray2, dataArray2);







        // we must first set listening only mode before creating it as listen-only
//        try {
//            canInterface.setListeningMode(listen_only);
//        } catch (Exception e) {
//            Log.e(TAG, "Unable to set mode for CanbusInterface() " + e.toString());
//            return null;
//        }



        int bitrate_kb = bitrate / 1000;

        CanbusFlowControl[] flowControlMessages = setFlowControlMessages();

        Log.v(TAG, "createInterface: create(" + listen_only + "," +
                bitrate_kb + ",true, filterArray," + CAN_PORT1 + ",flowControlMessages)");
        try {
            canInterface.create(listen_only,
                    bitrate ,
                    true,
                    filterArray, //
                    CAN_PORT1,
                    flowControlMessages);

        } catch (Exception e) {
            Log.e(TAG, "Unable to call create(" + listen_only + ") for CanbusInterface() " + e.toString());
            return null;
        }


        // We must set bitrate and listening mode both before and after creating the interface.
        // We would prefer to always set before, but that doesn't always work

//        Log.v(TAG, "createInterface: setBitrate(" + bitrate + ", null," + CAN_PORT + ")");
//        try {
//
//            canInterface.setBitrate(bitrate, null, CAN_PORT );
//
//        } catch (Exception e) {
//            Log.e(TAG, "Unable to set bitrate for CanbusInterface() " + e.toString());
//            return null;
//        }




//        try {
//            canInterface.setListeningMode(listen_only);
//        } catch (Exception e) {
//            Log.e(TAG, "Unable to set mode for CanbusInterface() " + e.toString());
//            return null;
//        }


        // Set the bitrate again since it doesn't work to set this before creating interface first time after power-up
        // We are in listen mode, so it shouldn't be a problem to open at wrong bitrate
//        try {
//            canInterface.setBitrate(bitrate);
//        } catch (Exception e) {
//            Log.e(TAG, "Unable to set bitrate for CanbusInterface() " + e.toString());
//            return null;
//        }




        Log.d(TAG, "Interface created @ " + bitrate + "kb " + (listen_only ? "READ-ONLY" : "READ-WRITE"));


        return new InterfaceWrapper(canInterface);
    } // createInterface()


    void removeInterface(InterfaceWrapper wrappedInterface) {
        try {
            wrappedInterface.canbusInterface.removeCAN1();
        } catch (Exception e) {
            Log.e(TAG, "Unable to remove CanbusInterface() " + e.toString());
        }
    } // removeInterface()


    SocketWrapper createSocket(InterfaceWrapper wrappedInterface) {

        CanbusSocket socket = null;

        // open a new socket.
        try {
            socket = wrappedInterface.canbusInterface.createSocketCAN1();
            if (socket == null) {
                Log.e(TAG, "Socket not created .. returned NULL");
                return null;
            }
            // set socket options here
        } catch (Exception e) {
            Log.e(TAG, "Exception creating Socket: "  + e.toString(), e);
            return null;
        }
        return new SocketWrapper(socket);
    } // createSocket()


    boolean openSocket(SocketWrapper wrappedSocket, boolean discardBuffer) {
        try {
            wrappedSocket.canbusSocket.openCan1();
        } catch (Exception e) {
            Log.e(TAG, "Exception opening Socket: " +  e.toString(), e);
            return false;
        }

        // we have to discard when opening a socket at a new bitrate, but this causes a 3 second gap in frame reception

        if (discardBuffer) {
            try {
                wrappedSocket.canbusSocket.discardInBuffer();
            } catch (Exception e) {
                Log.e(TAG, "Exception discarding Socket buffer: " + e.toString(), e);
                return false;
            }
        }

        return true;
    } // openSocket


    void closeSocket(SocketWrapper wrappedSocket) {
        // close the socket
        try {
            if (wrappedSocket.canbusSocket != null)
                wrappedSocket.canbusSocket.close1939Port1();
            wrappedSocket.canbusSocket = null;
            wrappedSocket = null;
        } catch (Exception e) {
            Log.e(TAG, "Exception closeSocket()" + e.toString(), e);
        }
    } // closeSocket();


    //////////////////////////////////////////////////////////////////
    // isJ1708Supported()
    //  does the hardware support J1708 ?
    //////////////////////////////////////////////////////////////////
    public static boolean isJ1708Supported() {

        return false; // Never Supported

    } // isJ1708Supported?



    //////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////
    // Private Methods
    //////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////

    /**
     *
     * @return
     */
    private CanbusFlowControl[] setFlowControlMessages(){

        return null;
/*
        CanbusFlowControl[] flowControlMessages = new CanbusFlowControl[1];

        byte[] data1=new byte[]{0x10,0x34,0x56,0x78,0x1f,0x2f,0x3f,0x4f};

        flowControlMessages[0] = new CanbusFlowControl(0x18FFFFFF,0x18FFFFFF,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[0] = new CanbusFlowControl(0x18FEE000,0x18FEE018,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[1] = new CanbusFlowControl(0x1CECFF00,0x1CECFF1C,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[2] = new CanbusFlowControl(0x18FEE300,0x18FEE318,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[3] = new CanbusFlowControl(0x18FEE400,0x18FEE418,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[4] = new CanbusFlowControl(0x18FEE500,0x18FEE518,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[5] = new CanbusFlowControl(0x1CECEE00,0x1CECEE1C,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[6] = new CanbusFlowControl(0x1CECCC00,0x1CECCC00,CanbusFlowControl.EXTENDED,8,data1);
        //flowControlMessages[7] = new CanbusFlowControl(0x1CECAA00,0x1CECAA00,CanbusFlowControl.EXTENDED,8,data1);
        return flowControlMessages;
*/
    }



    private CanbusHardwareFilter[] setFilters(CANHardwareFilter[] hardwareFilters) {
        CanbusHardwareFilter[] filterArray = null;
        if (hardwareFilters != null) {
//            try {
//                canInterface.setFilters(hardwareFilters);
//            } catch (Exception e) {
//                Log.e(TAG, "Unable to set filters for CanbusInterface() " + e.toString());
//                try {
//                    canInterface.removeCAN1();
//                } catch (Exception e2) {
//                    Log.e(TAG, "Unable to remove CanbusInterface() " + e2.toString());
//                }
//                return null;
//            }


            showHardwareFilters(hardwareFilters);



            int ids[] = new int[hardwareFilters.length];
            int masks[] = new int[hardwareFilters.length];
            int types[] = new int[hardwareFilters.length];

            for (int i = 0 ; i < hardwareFilters.length; i++) {
                    ids[i] = hardwareFilters[i].getIds()[0];
                    masks[i] = hardwareFilters[i].getMask()[0];
                    types[i] = hardwareFilters[i].getFilterMaskType()[0];
            }

            filterArray = new CanbusHardwareFilter[1];
            //for (int i =0; i < hardwareFilters.length; i++) {
            filterArray[0] = new CanbusHardwareFilter(ids, masks, types);

            String str = "filterArray [" + 0 + "] = " +
                showFilterIds(filterArray[0].getIds()) +
                showFilterMasks(filterArray[0].getMask()) +
                showFilterTypes(filterArray[0].getFilterMaskType());

            Log.i(TAG, str);
            //}


            //filterArray[0] = hardwareFilters[0];
            //filterArray = CANHardwareFilter.upcast(hardwareFilters),



        }

        return filterArray;

    }


} // VehicleBusHW
