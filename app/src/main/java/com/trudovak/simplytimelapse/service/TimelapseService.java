package com.trudovak.simplytimelapse.service;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.trudovak.simplytimelapse.MainActivity;
import com.trudovak.simplytimelapse.R;
import com.trudovak.simplytimelapse.TimelapseApp;
import com.trudovak.simplytimelapse.TimelapseRunner;
import com.trudovak.simplytimelapse.TimelapseRunner.TimelapseListener;
import com.trudovak.simplytimelapse.camera.ServerDevice;
import com.trudovak.simplytimelapse.camera.SimpleRemoteApi;
import com.trudovak.simplytimelapse.utils.Logger;

public class TimelapseService extends Service implements TimelapseListener {
    private static final Logger logger = new Logger(TimelapseService.class);

    public static final String CAMERA_URL_PARAM = "CAMERA_URL";

    public static final String FRAMES_PARAM = "FRAMES";

    public static final String INTERVAL_PARAM = "INTERVAL";

    public static final String START_TIMELAPSE = "START_TIMELAPSE";

    public static final String STOP_TIMELAPSE = "STOP_TIMELAPSE";

    public static final String TIMELAPSE_COMPLETE_BROADCAST = "com.trudovak.simplytimelapse.stopBoradcast";

    public static final String TIMELAPSE_ERROR_BROADCAST = "com.trudovak.simplytimelapse.errorBoradcast";

    public static final String TIMELAPSE_PROGRESS_BROADCAST = "com.trudovak.simplytimelapse.progressBoradcast";

    public static final String ERROR_MESSAGE = "msg";

    private static final int MAX_THREADS = 2;

    protected static final String THREAD_NAME = "camera-service-%d";
    protected int threadId = 1;

    protected Intent startIntent;
    protected int totalFrames;

    TimelapseRunner runner;

    private static final int NOTIFICATION_ID = 40403;

    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(MAX_THREADS, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(String.format(THREAD_NAME, threadId++));
            return t;
        }
    });


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(START_TIMELAPSE)) {
            startIntent = null;
            // Start timelapse
            startTimelapse(intent.getStringExtra(CAMERA_URL_PARAM), intent.getIntExtra(INTERVAL_PARAM, 3),
                    intent.getIntExtra(FRAMES_PARAM, 2), intent);

        } else if (intent.getAction().equals(STOP_TIMELAPSE)) {
            // Stop timelapse - cancel the recurrent task
            stopTimelapse();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startTimelapse(final String url, final int interval, final int frames, final Intent intent) {
        executor.execute(new Runnable() {

            @Override
            public void run() {
                ServerDevice device = ServerDevice.fetch(url);
                if (device != null) {
                    synchronized (TimelapseService.this) {
                        if (runner != null && runner.isRunning()) {
                            // Timelapse in progress
                            // Signal the client and go.
                            logger.warn("Timelapse in progress! Cannot start new one");
                            OnError(getString(R.string.msg_timelapse_in_progress));
                        } else {
                            logger.debug("Creating timelapse runner");
                            SimpleRemoteApi api = new SimpleRemoteApi(device);
                            runner = new TimelapseRunner(executor, api, TimelapseService.this);
                            ((TimelapseApp) getApplication()).setTimelapseActive(true);
                            try {
                                runner.startTimelapse(frames, interval, TimeUnit.SECONDS);
                                // Keep the intent so we can stop when needed
                                startIntent = intent;
                                // Set foreground
                                totalFrames = frames;
                                startForeground();
                            } catch (RuntimeException e) {
                                logger.error(e, "Failed to schedule the timelapse");
                                // Make sure state is reset if things go south
                                ((TimelapseApp) getApplication()).setTimelapseActive(false);
                                throw e;
                            }
                        }
                    }
                } else {
                    // Cannot init connection to device
                    logger.warn("Cannot start timelapse from URL {}", url);
                    OnError(getString(R.string.msg_error_connection));
                }
            }
        });
    }

    private void stopTimelapse() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (TimelapseService.this) {
                    if (runner != null && runner.isRunning()) {
                        logger.debug("Timelapse in progress stopping on user request");
                        runner.stopTimelapse();
                    } else {
                        logger.warn("No timelapse in progress!");
                        stopSelf();
                    }
                }
            }
        });
    }

    @Override
    public void OnComplete() {
        ((TimelapseApp) getApplication()).setTimelapseActive(false);
        logger.info("Timelapse Complete!");
        Intent intent = new Intent(TIMELAPSE_COMPLETE_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        completeTimelapse(intent);
    }

    @Override
    public void OnError(String msg) {
        logger.info("Timelapse Errror! {}", msg);
        Intent intent = new Intent(TIMELAPSE_ERROR_BROADCAST);
        intent.putExtra(ERROR_MESSAGE, msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        completeTimelapse(intent);
    }

    private void completeTimelapse(Intent intent) {
        // Remove from foreground
        stopForeground(true);
        if (startIntent != null) {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
        runner = null;
        stopSelf();
    }

    @Override
    public void OnPicture(Bitmap picture, int remainingFrames, long actualInterval, TimeUnit unit) {
        logger.info("Timelapse picture taken. Remaining {0}. Interval between last two frames was {1}ms",
                remainingFrames, actualInterval);
        long intervalMillis = TimeUnit.MILLISECONDS.convert(actualInterval, unit);
        float interval = (float) (intervalMillis / 1000.0);
        TimelapseApp timelapseApp = (TimelapseApp) getApplication();
		timelapseApp.getPictureReference().set(picture);
		timelapseApp.setInterval(interval);
		timelapseApp.setTotal(totalFrames);
		timelapseApp.setRemainingFrames(remainingFrames);
		
        Intent intent = new Intent(TIMELAPSE_PROGRESS_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);


        updateNotification(totalFrames - remainingFrames, totalFrames, interval);
    }

    private void startForeground() {
        startForeground(NOTIFICATION_ID, getMyActivityNotification("", 0, totalFrames));
    }

    @SuppressWarnings("deprecation")
    private Notification getMyActivityNotification(String text, int progress, int total) {
        // The PendingIntent to launch our activity if the user selects
        // this notification
        CharSequence title = getText(R.string.app_name);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        return new Notification.Builder(this).setContentTitle(title).setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher).setContentIntent(contentIntent)
                .setProgress(total, progress, false).getNotification();

    }

    /**
     * this is the method that can be called to update the Notification
     */
    private void updateNotification(int progress, int total, float interval) {

        String text = String.format(getString(R.string.msg_timelapse_progress), total - progress, interval);

        Notification notification = getMyActivityNotification(text, progress, total);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

}
