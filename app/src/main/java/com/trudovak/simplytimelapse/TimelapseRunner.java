package com.trudovak.simplytimelapse;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.trudovak.simplytimelapse.camera.SimpleRemoteApi;
import com.trudovak.simplytimelapse.utils.Logger;

public class TimelapseRunner implements Runnable {

    public interface TimelapseListener {
        void OnComplete();

        void OnError(String msg);

        void OnPicture(Bitmap picture, int remainingFrames, long actualInterval, TimeUnit unit);
    }

    // private static String TAG = "TimelapseRunner";

    private static final Logger logger = new Logger(TimelapseRunner.class);

    protected static final int SOCKET_TIMEOUT_MS = 60000;

    private final ScheduledExecutorService scheduledExecutorService;
    private final SimpleRemoteApi remoteApi;
    private final TimelapseListener listener;

    private int remainingFrames;
    private int interval;
    private ScheduledFuture<?> handle;
    private volatile boolean running = false;

    private long frameStart;

    public TimelapseRunner(ScheduledExecutorService scheduledExecutorService, SimpleRemoteApi remoteApi,
            TimelapseListener listener) {
        super();
        this.scheduledExecutorService = scheduledExecutorService;
        this.remoteApi = remoteApi;
        this.listener = listener;
    }

    public synchronized void startTimelapse(int frames, int interval, TimeUnit unit) {
        if (TimeUnit.MILLISECONDS.convert(interval, unit) < 1000) {
            throw new IllegalArgumentException("Cannot run timelapse at faster rate than 1 seconds per frame");
        }
        if (running) {
            throw new IllegalStateException("TimelapseRunner is already running.");
        }
        this.remainingFrames = frames;
        this.interval = interval;
        running = true;
        frameStart = System.currentTimeMillis();
        handle = scheduledExecutorService.scheduleAtFixedRate(this, 0, interval, unit);
    }

    public synchronized void stopTimelapse() {
        if (running) {
            handle.cancel(false);
            listener.OnComplete();
            running = false;
        }
    }

    public synchronized boolean isRunning() {
        return this.running;
    }

    public synchronized long getRemainingFrames() {
        return running ? remainingFrames : 0;
    }

    public synchronized long getInterval() {
        return running ? this.interval : 1;
    }

    @Override
    public synchronized void run() {
        JSONObject response;
        try {
            final long interval = System.currentTimeMillis() - frameStart;
            String uriValue = null;
            frameStart = System.currentTimeMillis();
            response = remoteApi.actTakePicture(SOCKET_TIMEOUT_MS);

            // In case of long exposure handle the error and call awaitTakePicture
            // {
            // "id": 42,
            // "error": [
            // 40403,
            // "Long shooting"
            // ]
            // }
            // In case of very long exposure awaitTakePicture will return
            // {
            // "id": 42,
            // "error": [
            // 40403,
            // "Not Finished"
            // ]
            // }
            // In the end we see
            // {
            // "id": 42,
            // "result": [
            // [
            // "http://192.168.122.1:8080/postview/pict20140719_114631_0.JPG"
            // ]
            // ]
            // }
            try {
                int errorCode = parseErrorCode(response);
                while (errorCode == 40403) {
                    response = remoteApi.awaitTakePicture(SOCKET_TIMEOUT_MS);
                    errorCode = parseErrorCode(response);
                }

                JSONArray result = response.getJSONArray("result");
                result = result.getJSONArray(0);
                uriValue = result.getString(0);
            } catch (JSONException e) {
                logger.error(e, "JSON error taking frame. Stopping timelapse. JSON is {}", response);
                // Ignore JSON Errors
            }
            remainingFrames--;
            if (uriValue != null && remainingFrames > 0) {
                final String uri = uriValue;
                scheduledExecutorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = null;
                        InputStream in;
                        try {
                            in = new java.net.URL(uri).openStream();
                            bitmap = BitmapFactory.decodeStream(in);
                        } catch (MalformedURLException e) {
                            logger.debug("Cannot load the image from: " + uri);
                        } catch (IOException e) {
                            logger.debug("Cannot load image due to I/O error from:" + uri);
                        }
                        listener.OnPicture(bitmap, remainingFrames, interval, TimeUnit.MILLISECONDS);
                    }
                });
            }

            if (remainingFrames <= 0) {
                stopTimelapse();
            }
        } catch (IOException e) {
            logger.error(e, "Error taking frame. Stopping timelapse");
            handle.cancel(false);
            listener.OnError(e.getMessage());
            running = false;
        }

    }

    private int parseErrorCode(JSONObject response) throws JSONException {
        if(response.has("error")){
            JSONArray error = response.getJSONArray("error");
            return error.getInt(0);
        }
        return 0;
    }
}
