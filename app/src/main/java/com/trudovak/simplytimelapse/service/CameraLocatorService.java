package com.trudovak.simplytimelapse.service;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.trudovak.simplytimelapse.R;
import com.trudovak.simplytimelapse.camera.ServerDevice;
import com.trudovak.simplytimelapse.camera.SimpleRemoteApi;
import com.trudovak.simplytimelapse.utils.Logger;

public class CameraLocatorService extends IntentService {
    private static final Logger logger = new Logger(CameraLocatorService.class);

    private final static int SSDP_RECEIVE_TIMEOUT = 10000; // msec
    private final static int PACKET_BUFFER_SIZE = 1024;
    private final static int SSDP_PORT = 1900;
    private final static int SSDP_MX = 1;
    private final static String SSDP_ADDR = "239.255.255.250";
    private final static String SSDP_ST = "urn:schemas-sony-com:service:ScalarWebAPI:1";

    public final static String CAMERA_FOUND = "com.trudovak.simplytimelapse.cameraFound";
    public final static String CAMERA_SEARCH_FAILED = "com.trudovak.simplytimelapse.cameraNotFound";

    public final static String CAMERA_URL = "url";
    public final static String CAMERA_NAME = "name";

    public final static String ERROR_MSG_ID = "msg_id";

    private final Set<String> availableApiSet = new HashSet<String>();

    public CameraLocatorService() {
        super("CameraLocatorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        findCamera();
    }

    private void findCamera() {
        final String ssdpRequest = "M-SEARCH * HTTP/1.1\r\n" + String.format("HOST: %s:%d\r\n", SSDP_ADDR, SSDP_PORT)
                + "MAN: \"ssdp:discover\"\r\n" + String.format("MX: %d\r\n", SSDP_MX)
                + String.format("ST: %s\r\n", SSDP_ST) + "\r\n";
        final byte[] sendData = ssdpRequest.getBytes();

        // Send Datagram packets
        DatagramSocket socket = null;
        DatagramPacket receivePacket = null;
        DatagramPacket packet;
        try {
            socket = new DatagramSocket();
            byte[] array = new byte[PACKET_BUFFER_SIZE];
            receivePacket = new DatagramPacket(array, array.length);
            InetSocketAddress iAddress = new InetSocketAddress(SSDP_ADDR, SSDP_PORT);
            packet = new DatagramPacket(sendData, sendData.length, iAddress);
            // send 3 times
            logger.info("search() Send Datagram packet 3 times.");
            socket.send(packet);
            Thread.sleep(100);
            socket.send(packet);
            Thread.sleep(100);
            socket.send(packet);
        } catch (InterruptedException e) {
            // do nothing.
        } catch (SocketException e) {
            logger.error(e, "search() DatagramSocket error");
            onErrorFinished(R.string.msg_error_connection);
            return;
        } catch (IOException e) {
            logger.error(e, "search() IOException:");
            onErrorFinished(R.string.msg_error_connection);
            return;
        }

        //if (socket == null || receivePacket == null) {
        //    return;
        //}
        try {
            // Receive reply packets
            long startTime = System.currentTimeMillis();
            List<String> foundDevices = new ArrayList<String>();
            while (true) {
                try {
                    socket.setSoTimeout(SSDP_RECEIVE_TIMEOUT);
                    socket.receive(receivePacket);
                    String ssdpReplyMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    String ddUsn = findParameterValue(ssdpReplyMessage, "USN");

                    /*
                     * There is possibility to receive multiple packets from a individual server.
                     */
                    if (!foundDevices.contains(ddUsn)) {
                        String ddLocation = findParameterValue(ssdpReplyMessage, "LOCATION");
                        foundDevices.add(ddUsn);

                        // Fetch Device Description XML and parse it.
                        ServerDevice device = ServerDevice.fetch(ddLocation);
                        if (device != null) {
                            int errorCode = checkAndInitDevice(device);
                            if (0 == errorCode) {
                                onDeviceFound(device);
                                break;
                            } else {
                                onErrorFinished(errorCode);
                            }
                        }
                    }
                } catch (InterruptedIOException e) {
                    logger.debug(e, "search() Timeout.");
                    onErrorFinished(R.string.msg_error_connection);
                    break;
                } catch (IOException e) {
                    logger.warn(e, "search() IOException.");
                    onErrorFinished(R.string.msg_error_connection);
                    break;
                }
                if (SSDP_RECEIVE_TIMEOUT < System.currentTimeMillis() - startTime) {
                    onErrorFinished(R.string.msg_error_connection);
                    break;
                }
            }
        } finally {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void onErrorFinished(int msgId) {
        // Send Error Broadcast to client
        Intent intent = new Intent(CAMERA_SEARCH_FAILED);
        // Add error details
        intent.putExtra(ERROR_MSG_ID, msgId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void onDeviceFound(ServerDevice device) {
        String cameraUrl = device.getDDUrl();
        String cameraName = device.getFriendlyName();
        // Send broadcast intend with camera url and name
        Intent intent = new Intent(CAMERA_FOUND);
        intent.putExtra(CAMERA_NAME, cameraName);
        intent.putExtra(CAMERA_URL, cameraUrl);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

    /**
     * Checks a discovered device for compatibility and initializes some basic parameters
     * @param device the settings for connecting ot the camera device
     * @return zero if compatible else error message code is returned
     */
    private int checkAndInitDevice(ServerDevice device) {
        // Called by non-UI thread.
        logger.debug(">> Search device found: {}", device.getFriendlyName());
        // Only register one device
        SimpleRemoteApi remoteApi = new SimpleRemoteApi(device);

        // Initialize the camera
        try {
            JSONObject replyJson;

            // getAvailableApiList
            replyJson = remoteApi.getAvailableApiList();
            loadAvailableApiList(replyJson);

            // check version of the server device
            if (isApiAvailable("getApplicationInfo")) {
                logger.debug("onDeviceFound(): getApplicationInfo()");
                replyJson = remoteApi.getApplicationInfo();
                if (!isSupportedServerVersion(replyJson)) {
                    logger.debug("The device found is not supported.");
                    return R.string.msg_error_non_supported_device;
                }
            } else {
                // never happens;
                logger.error("getApplicationInfo API is not available during initialization.");
                return R.string.msg_error_non_supported_device;
            }

            // startRecMode if necessary.
            if (isApiAvailable("startRecMode")) {
                logger.debug("onDeviceFound(): startRecMode()");
                remoteApi.startRecMode();

                // Call again.
                replyJson = remoteApi.getAvailableApiList();
                loadAvailableApiList(replyJson);
            }

            if (isApiAvailable("setPostviewImageSize")) {
                // Set preview to small size to avoid problems
                logger.debug("onDeviceFound(): setPostviewImageSize()");
                remoteApi.setPostviewImageSize("2M");
            }

            logger.debug("onDeviceFound(): completed.");
            return 0;
        } catch (IOException e) {
            logger.warn(e, "onDeviceFound: IOException: ");
            return R.string.msg_error_connection;
        } catch (IllegalStateException e) {
            logger.warn(e, "onDeviceFound: IllegalStateException" );
            return R.string.msg_unregognized_device;
        }
    }

    /*
     * Find a value string from message line as below. (ex.) "ST: XXXXX-YYYYY-ZZZZZ" ->
     * "XXXXX-YYYYY-ZZZZZ"
     */
    private static String findParameterValue(String ssdpMessage, String paramName) {
        String name = paramName;
        if (!name.endsWith(":")) {
            name = name + ":";
        }
        int start = ssdpMessage.indexOf(name) + name.length();
        int end = ssdpMessage.indexOf("\r\n", start);
        if (start != -1 && end != -1) {
            String val = ssdpMessage.substring(start, end);
            return val.trim();
        }
        return null;
    }

    // Retrieve a list of APIs that are available at present.
    private void loadAvailableApiList(JSONObject replyJson) {
        synchronized (availableApiSet) {
            availableApiSet.clear();
            try {
                JSONArray resultArrayJson = replyJson.getJSONArray("result");
                JSONArray apiListJson = resultArrayJson.getJSONArray(0);
                for (int i = 0; i < apiListJson.length(); i++) {
                    availableApiSet.add(apiListJson.getString(i));
                }
            } catch (JSONException e) {
                logger.warn(e, "loadAvailableApiList: JSON format error.");
            }
        }
    }

    // Check if the indicated API is available at present.
    private boolean isApiAvailable(String apiName) {
        boolean isAvailable;
        synchronized (availableApiSet) {
            isAvailable = availableApiSet.contains(apiName);
        }
        return isAvailable;
    }

    // Check if the version of the server is supported in this application.
    private boolean isSupportedServerVersion(JSONObject replyJson) {
        try {
            JSONArray resultArrayJson = replyJson.getJSONArray("result");
            String version = resultArrayJson.getString(1);
            String[] separated = version.split("\\.");
            int major = Integer.valueOf(separated[0]);
            if (2 <= major) {
                return true;
            }
        } catch (JSONException e) {
            logger.warn(e, "isSupportedServerVersion: JSON format error.");
        } catch (NumberFormatException e) {
            logger.warn(e, "isSupportedServerVersion: Number format error.");
        }
        return false;
    }

}
