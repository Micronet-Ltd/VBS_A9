/*
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package com.micronet.dsc.vbs;

/**
 * VehicleBusConstants contains constants used by VBS to communicate with ATS.
 */
public final class VehicleBusConstants {

    //////////////////////////////////////
    // Packages and Permissions
    //////////////////////////////////////

    public static final String PACKAGE_NAME_ATS = "com.micronet.dsc.ats";

    //////////////////////////////////////
    // Actions for VBS service
    //////////////////////////////////////

    public static final String SERVICE_ACTION_ALARM = "com.micronet.dsc.vbs.alarm"; // deprecated

    // Action : START : start up CAN
    public static final String SERVICE_ACTION_START = "com.micronet.dsc.vbs.start";

    // Action : RESTART : restart all buses that were running with the same parameters they were running with
    //  This is useful when VBS crashes
    public static final String SERVICE_ACTION_RESTART = "com.micronet.dsc.vbs.restart";

    // Action: STOP : STOP CAN
    public static final String SERVICE_ACTION_STOP = "com.micronet.dsc.vbs.stop";


    //////////////////////////////////////
    // Extras for the VBS service
    //////////////////////////////////////

    // Extra: "bus" (String): Defines the bus type you are acting on.
    //        Use with START, RESTART, and STOP Actions
    public static final String SERVICE_EXTRA_BUS = "bus";

    // Extra: "bitrate" (int): Defines the initial CAN bitrate. May be 250000 or 500000
    //      use with START action only
    public static final String SERVICE_EXTRA_BITRATE = "bitrate";

    // Extra: "autoDetect" (boolean): If true, then VBS will switch from CAN initial bitrate in attempt to auto-detect one.
    //      use with START action only
    public static final String SERVICE_EXTRA_AUTODETECT = "autoDetect";

    // Extra: "skipVerify" (boolean): If true, then VBS will go straight to normal CAN mode. Otherwise it starts in listen-mode to verify bitrate.
    //      useful for avoiding the 3-5 second communication gap associated with entering/exiting listen-only mode
    //      use with START action only
    public static final String SERVICE_EXTRA_SKIPVERIFY = "skipVerify";

    // Extra: "hardwareFilterIds" (array of ints). Defines which CAN frames will be listened for. Effects both regular reads and auto-detects.
    //      use with START action only
    public static final String SERVICE_EXTRA_HARDWAREFILTER_IDS = "hardwareFilterIds";

    // Extra: "hardwareFilterMasks" (array of ints). Masks corresponding to the CAN frame Ids
    //      use with START action only
    public static final String SERVICE_EXTRA_HARDWAREFILTER_MASKS = "hardwareFilterMasks";

    //  Extra: "canPortNumber" (int). Defines which port that user wants to open for CanBus. Can1 = 2 / Can2 = 3.
    //      use with START action only
    public static final String SERVICE_EXTRA_CAN_NUMBER = "canNumber";

    //  Extra: "flowControl" (boolean). Defines whether flow control should be used from /sdcard/VBS/config.xml.
    //      use with START action only
    public static final String SERVICE_EXTRA_FLOW_CONTROL = "flowControl";


    //////////////////////////////////////
    // Broadcasts To or From the VBS Service
    //////////////////////////////////////

    // Broadcast: canrx : contains a Received CAN packet from the bus
    public static final String BROADCAST_CAN_RX = "com.micronet.dsc.vbs.canrx";

    // Broadcast: cantx : broadcast this to ask VBS to transmit a CAN packet on the bus
    public static final String BROADCAST_CAN_TX = "com.micronet.dsc.vbs.cantx";

    // Broadcast: status : sent regularly by VBS with the status of the buses. Useful for telling if VBS crashed
    public static final String BROADCAST_STATUS = "com.micronet.dsc.vbs.status";

    //////////////////////////////////////
    // Broadcasts Extras To or From the VBS Service
    //////////////////////////////////////

    // Extra "canrx" (boolean): are we able to receive on CAN yet?
    public static final String BROADCAST_EXTRA_STATUS_CANRX = "canrx";
    // Extra "cantx" (boolean): are we able to transmit on CAN yet?
    public static final String BROADCAST_EXTRA_STATUS_CANTX = "cantx";
    // Extra "canBitrate" (int): what is the can bitrate ? Useful after auto-detection
    public static final String BROADCAST_EXTRA_STATUS_CANBITRATE ="canBitrate";
    // Extra "canNumber" (int): what is the can number?
    public static final String BROADCAST_EXTRA_STATUS_CANNUMBER ="canNumber";


    // Extra "elapsedRealtime" (long): contains the time that VBS received the packet
    public static final String BROADCAST_EXTRA_TIMESTAMP ="elapsedRealtime";

    // Extra "id" (int): Contains the frame ID to rx/tx
    public static final String BROADCAST_EXTRA_CAN_ID = "id";
    // Extra "data" (byte array): Contains the data for the frame rx/tx
    public static final String BROADCAST_EXTRA_CAN_DATA ="data";

}
