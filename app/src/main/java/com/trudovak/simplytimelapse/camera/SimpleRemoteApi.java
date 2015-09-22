/*
 * Copyright 2013 Sony Corporation
 */

package com.trudovak.simplytimelapse.camera;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.trudovak.simplytimelapse.camera.ServerDevice.ApiService;
import com.trudovak.simplytimelapse.camera.utils.SimpleHttpClient;
import com.trudovak.simplytimelapse.utils.Logger;

/**
 * Simple Camera Remote API wrapper class. (JSON based API <--> Java API)
 */
public class SimpleRemoteApi {
    private static final Logger logger = new Logger(SimpleRemoteApi.class);

    // API server device you want to send requests.
    private final ServerDevice mTargetServer;

    // Request ID of API calling. This will be counted up by each API calling.
    private int mRequestId;

    /**
     * Constructor.
     *
     * @param target
     *            server device of Remote API
     */
    public SimpleRemoteApi(ServerDevice target) {
        mTargetServer = target;
        mRequestId = 1;
    }

    // Retrieves Action List URL from Server information.
    private String findActionListUrl(String service) {
        List<ApiService> services = mTargetServer.getApiServices();
        for (ApiService apiService : services) {
            if (apiService.getName().equals(service)) {
                return apiService.getActionListUrl();
            }
        }
        throw new IllegalStateException("actionUrl not found.");
    }

    // Request ID. Counted up after calling.
    private int id() {
        return mRequestId++;
    }

    // Camera Service APIs

    public JSONObject getAvailableApiList() throws IOException {
        return invoke("camera", "getAvailableApiList", new JSONArray());
    }

    public JSONObject getApplicationInfo() throws IOException {
        return invoke("camera", "getApplicationInfo", new JSONArray());
    }

    public JSONObject getShootMode() throws IOException {
        return invoke("camera", "getShootMode", new JSONArray());
    }

    public JSONObject setShootMode(String shootMode) throws IOException {
        return invoke("camera", "setShootMode", new JSONArray().put(shootMode));
    }

    public JSONObject getAvailableShootMode() throws IOException {
        return invoke("camera", "getAvailableShootMode", new JSONArray());
    }

    public JSONObject getSupportedShootMode() throws IOException {
        return invoke("camera", "getSupportedShootMode", new JSONArray());
    }

    public JSONObject startLiveview() throws IOException {
        return invoke("camera", "startLiveview", new JSONArray());
    }

    public JSONObject stopLiveview() throws IOException {
        return invoke("camera", "stopLiveview", new JSONArray());
    }

    public JSONObject startRecMode() throws IOException {
        return invoke("camera", "startRecMode", new JSONArray());
    }

    public JSONObject stopRecMode() throws IOException {
        return invoke("camera", "stopRecMode", new JSONArray());
    }

    public JSONObject actTakePicture() throws IOException {
        return actTakePicture(SimpleHttpClient.DEFAULT_READ_TIMEOUT);
    }

    public JSONObject actTakePicture(int timeout) throws IOException {
        return invoke("camera", "actTakePicture", new JSONArray(), timeout);
    }

    public JSONObject awaitTakePicture(int timeout) throws IOException {
        return invoke("camera", "awaitTakePicture", new JSONArray(), timeout);
    }

    public JSONObject startMovieRec() throws IOException {
        return invoke("camera", "startMovieRec", new JSONArray());
    }

    public JSONObject stopMovieRec() throws IOException {
        return invoke("camera", "stopMovieRec", new JSONArray());
    }

    public JSONObject getEvent(boolean longPollingFlag) throws IOException {
        return invoke("camera", "getEvent", new JSONArray(), (longPollingFlag) ? 20000 : 8000);
    }

    /**
     * Sets the post view image size
     *
     * @param postViewImageSize
     *            currently (june 2014) accepted values are "Original" and "2M"
     * @return JSON object
     * @throws IOException
     */
    public JSONObject setPostviewImageSize(String postViewImageSize) throws IOException {
        String method = "setPostviewImageSize";
        JSONArray params = new JSONArray().put(postViewImageSize);
        final String service = "camera";
        return invoke(service, method, params);
    }

    /**
     * Calls act enable methods to unlock API access
     *
     * @return result of enabling operations
     */
    public JSONObject actEnableMethods(String developerName, String sg, String methods, String developerID)
            throws IOException {
        String service = "accessControl";
        String method = "actEnableMethods";
        try {
            JSONObject parameters = new JSONObject().put("developerName", developerName).put("sg", sg)
                    .put("methods", methods).put("developerID", developerID);
            JSONArray params = new JSONArray();
            params.put(parameters);

            return invoke(service, method, params);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private JSONObject invoke(final String service, String method, JSONArray params) throws IOException {
        return invoke(service, method, params, SimpleHttpClient.DEFAULT_READ_TIMEOUT);
    }

    private JSONObject invoke(final String service, String method, JSONArray params, int timeout) throws IOException {
        try {
            JSONObject requestJson = new JSONObject().put("method", method).put("params", params).put("id", id())
                    .put("version", "1.0");
            String url = findActionListUrl(service) + "/" + service;

            logger.debug("Request: {}", requestJson);
            String responseJson = SimpleHttpClient.httpPost(url, requestJson.toString(), timeout);
            logger.debug("Response: {}", responseJson);

            return new JSONObject(responseJson);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
