package com.micronet.dsc.vbs;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class StateTest {

    public static final String TAG = "StateTest";

    Context context;
    State state;

    public StateTest() {}

    @Test
    public void writeStateFlowControls() {
        ArrayList<VehicleBusHW.CANFlowControl> flowControls = new ArrayList<>();
        byte[] data = new byte[] {(byte) 0x84, (byte) 0x2a, (byte) 0x46, (byte) 0x84, (byte) 0x16, (byte) 0x84};
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));
        flowControls.add(new VehicleBusHW.CANFlowControl(0x18FEE000, 0x18FEE018, data, VehicleBusHW.CANFrameType.integerConversion(1)));

        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        state = new State(context);

        // Write flow controls to sharedprefs and then read to compare if they are the same.
        state.writeStateFlowControls(flowControls);
        ArrayList<VehicleBusHW.CANFlowControl> savedFlowControls = state.readStateFlowControls();

        // Compare two arraylists.
        compareFlowControls(flowControls, savedFlowControls);
    }

    @Test
    public void writeStateFlowControlsNull() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        state = new State(context);

        // Write null for flow controls and should return null.
        state.writeStateFlowControls(null);
        ArrayList<VehicleBusHW.CANFlowControl> savedFlowControls = state.readStateFlowControls();

        // Assert that saved flow controls are null.
        assertNull(savedFlowControls);
    }

    private boolean compareFlowControls(ArrayList<VehicleBusHW.CANFlowControl> arr1, ArrayList<VehicleBusHW.CANFlowControl> arr2) {
        showFlowControls(arr1);
        showFlowControls(arr2);

        if (arr1.size() != arr2.size()) fail();

        int i = 0;
        for (VehicleBusHW.CANFlowControl flowControl1: arr1) {
            VehicleBusHW.CANFlowControl flowControl2 = arr2.get(i);

            if (flowControl1.getSearchId() != flowControl2.getSearchId()) fail();
            if (flowControl1.getResponseId() != flowControl2.getResponseId()) fail();
            if (flowControl1.getFlowMessageType() != flowControl2.getFlowMessageType()) fail();

            Log.d(TAG, "Arr1 Bytes: " + Arrays.toString(flowControl1.getDataBytes()) + "\nArr2 Bytes: " + Arrays.toString(flowControl2.getDataBytes()));

            if (!Arrays.equals(flowControl1.getDataBytes(), flowControl2.getDataBytes())) fail();

            i++;
        }

        return true;
    }

    private void showFlowControls(ArrayList<VehicleBusHW.CANFlowControl> flowControls) {
        StringBuilder flowControlStr = new StringBuilder();

        int i = 0;
        for (VehicleBusHW.CANFlowControl flowControl : flowControls) {
            flowControlStr.append(String.format(Locale.getDefault(), "Flow Control %d: searchId-%X, responseId-%X, T-%d, Length-%d \n",
                    i++, flowControl.getSearchId(), flowControl.getResponseId(), flowControl.getFlowMessageType(), flowControl.getFlowDataLength()));
        }

        Log.d(TAG, "Flow Controls = {\n" + flowControlStr.toString() + "}");
    }
}