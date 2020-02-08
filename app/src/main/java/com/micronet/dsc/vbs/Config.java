package com.micronet.dsc.vbs;

import android.annotation.SuppressLint;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import static com.micronet.dsc.vbs.VehicleBusService.TAG;

public class Config {
    @SuppressLint("SdCardPath")
    private static final String CONFIG_FILE_PATH = "/sdcard/VBS/configuration.xml";

    // Tags
    private static final String PORT_TAG = "port";
    private static final String BAUDRATE_TAG = "baudrate";
    private static final String LISTEN_ONLY_TAG = "listenonly";
    private static final String AUTOBAUD_TAG = "autobaud";
    private static final String TERMINATION_TAG = "termination";
    private static final String FILTERS_TAG = "filters";
    private static final String FILTER_TAG = "filter";
    private static final String FLOW_CONTROLS_TAG = "flowcontrols";
    private static final String FLOW_CONTROL_TAG = "flowcontrol";

    // General attributes
    private static final String NAME_ATTRIBUTE = "name";
    private static final String VAL_ATTRIBUTE = "val";
    private static final String TYPE_ATTRIBUTE = "type";

    // Filter attributes
    private static final String ID_ATTRIBUTE = "id";
    private static final String MASK_ATTRIBUTE = "mask";

    // Flow control attributes
    private static final String SEARCH_ID_ATTRIBUTE = "searchId";
    private static final String RESPONSE_ID_ATTRIBUTE = "responseId";
    private static final String DATA_ATTRIBUTE = "data";

    private static ArrayList<PortConfig> portConfigs;

    // Maps from port 2/3 to 1/2 to match config.
    static ArrayList<VehicleBusHW.CANFlowControl> getFlowControls(int canbusPort){
        PortConfig config = getPortConfig("CAN" + (canbusPort-1));
        if (config != null) {
            return config.flowControls;
        }

        return null;
    }

    // Can be CAN1, CAN2, or J1708
    private static PortConfig getPortConfig(String port) {
        // Read current config file
        readConfigFile();

        for (PortConfig config: portConfigs) {
            if (port.equalsIgnoreCase(config.name)){
                return config;
            }
        }

        return null;
    }

    private static void readConfigFile() {
        portConfigs = new ArrayList<>();

        File file = new File(CONFIG_FILE_PATH);
        if (!file.exists()) return;

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new FileInputStream(CONFIG_FILE_PATH), null);

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                int eventType = parser.getEventType();
                String name = parser.getName();

                if(eventType == XmlPullParser.START_TAG && PORT_TAG.equals(name)) {
                    portConfigs.add(readPort(parser));
                }
            }
        } catch (Exception e) {
            Log.e(TAG,e.toString());
        }
    }

    private static PortConfig readPort(XmlPullParser parser) throws IOException, XmlPullParserException {
        // Ensure we are at a port tag
        parser.require(XmlPullParser.START_TAG, null, PORT_TAG);

        PortConfig portConfig = new PortConfig();
        portConfig.name = parser.getAttributeValue(null, NAME_ATTRIBUTE);

        parser.next();
        int eventType = parser.getEventType();
        while(!(eventType == XmlPullParser.END_TAG && PORT_TAG.equals(parser.getName()))) {
            String name = parser.getName();

            if (parser.getEventType() == XmlPullParser.END_TAG && BAUDRATE_TAG.equals(name)) {
                portConfig.baud = Integer.parseInt(parser.getAttributeValue(null, VAL_ATTRIBUTE));
            } else if (parser.getEventType() == XmlPullParser.END_TAG && LISTEN_ONLY_TAG.equals(name)) {
                portConfig.silentMode = Boolean.parseBoolean(parser.getAttributeValue(null, VAL_ATTRIBUTE));
            } else if (parser.getEventType() == XmlPullParser.END_TAG && AUTOBAUD_TAG.equals(name)) {
                portConfig.autobaud = Boolean.parseBoolean(parser.getAttributeValue(null, VAL_ATTRIBUTE));
            } else if (parser.getEventType() == XmlPullParser.END_TAG && TERMINATION_TAG.equals(name)) {
                portConfig.termination = Boolean.parseBoolean(parser.getAttributeValue(null, VAL_ATTRIBUTE));
            } else if (parser.getEventType() == XmlPullParser.START_TAG && FILTERS_TAG.equals(name)) {
                getFilters(parser, portConfig.filters);
            } else if (parser.getEventType() == XmlPullParser.START_TAG && FLOW_CONTROLS_TAG.equals(name)) {
                getFlowControl(parser, portConfig.flowControls);
            }

            eventType = parser.next();
        }

//        portConfig.toString();
        return portConfig;
    }

    private static void getFilters(XmlPullParser parser, ArrayList<VehicleBusHW.CANHardwareFilter> filters) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        while(!(eventType == XmlPullParser.END_TAG && FILTERS_TAG.equals(parser.getName()))) {
            if (parser.getEventType() == XmlPullParser.END_TAG && FILTER_TAG.equals(parser.getName())) {
                int id = Integer.decode(parser.getAttributeValue(null, ID_ATTRIBUTE));
                int mask = Integer.decode(parser.getAttributeValue(null, MASK_ATTRIBUTE));
                int type = Integer.parseInt(parser.getAttributeValue(null, TYPE_ATTRIBUTE));

                filters.add(new VehicleBusHW.CANHardwareFilter(id, mask, VehicleBusHW.CANFrameType.integerConversion(type)));
            }

            eventType = parser.next();
        }
    }

    private static void getFlowControl(XmlPullParser parser, ArrayList<VehicleBusHW.CANFlowControl> flowControls) throws XmlPullParserException, IOException {
        Log.d(TAG, "About to parse flow control.");

        int eventType = parser.getEventType();
        while(!(eventType == XmlPullParser.END_TAG && FLOW_CONTROLS_TAG.equals(parser.getName()))) {
            if (parser.getEventType() == XmlPullParser.END_TAG && FLOW_CONTROL_TAG.equals(parser.getName())) {
                int searchId = Integer.decode(parser.getAttributeValue(null, SEARCH_ID_ATTRIBUTE));
                int responseId = Integer.decode(parser.getAttributeValue(null, RESPONSE_ID_ATTRIBUTE));
                byte[] data = parseBytes(parser.getAttributeValue(null, DATA_ATTRIBUTE));
                int type = Integer.decode(parser.getAttributeValue(null, TYPE_ATTRIBUTE));

                flowControls.add(new VehicleBusHW.CANFlowControl(searchId, responseId, data, VehicleBusHW.CANFrameType.integerConversion(type)));
            }

            eventType = parser.next();
        }
    }

    private static byte[] parseBytes(String data) {
        String[] strArr = data.replaceAll(" ", "").split(",");
        byte[] bytes = new byte[strArr.length];
        Log.d(TAG, "Parsing data bytes.");

        for (int i = 0; i < strArr.length; i++) {
            bytes[i] = Integer.decode(strArr[i]).byteValue();
        }

        return bytes;
    }

    static class PortConfig {
        String name;
        int baud = 0;
        boolean silentMode = false;
        boolean autobaud = false;
        boolean termination = false;
        ArrayList<VehicleBusHW.CANHardwareFilter> filters = new ArrayList<>();
        ArrayList<VehicleBusHW.CANFlowControl> flowControls = new ArrayList<>();

        PortConfig(){}

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Port " + name + ": baud-" + baud + ", silentMode-" + silentMode + ", autobaud-" + autobaud + ", termination-" + termination + "\nFilters {\n");
            for (VehicleBusHW.CANHardwareFilter filter: filters) {
                sb.append("    " + String.format("0x08%X", filter.getId()) + ", " + String.format("0x08%X", filter.getMask()) + ", " + filter.getFilterMaskType() + "\n");
            }
            sb.append("}\nFlow controls {\n");
            for (VehicleBusHW.CANFlowControl flow: flowControls) {
                sb.append("    " + String.format("0x%08X", flow.getSearchId()) + ", " + String.format("0x%08X", flow.getResponseId()) + ", " + flow.getFlowMessageType() + "\n");
            }
            sb.append("}");

            Log.d(TAG, sb.toString());
            return sb.toString();
        }
    }
}
