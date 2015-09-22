package com.trudovak.simplytimelapse;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.trudovak.simplytimelapse.service.CameraLocatorService;
import com.trudovak.simplytimelapse.service.TimelapseService;
import com.trudovak.simplytimelapse.utils.Logger;

public class MainActivity extends Activity {
	private static final int MINIMAL_INTERVAL = 2;

	private static final Logger logger = new Logger(MainActivity.class);

	Handler handler;
	private Button scanButton;
	private Button startTimelapseButton;
	private Button stopTimelapseButton;

	private TextView status;
	private TextView framesText;
	private TextView intervalText;
	private TextView timelapseStatus;

	private ImageView preview;

	private final OnCompleteReceiver completionReceiver = new OnCompleteReceiver();
	private final ProgressReceiver progressReceiver = new ProgressReceiver();
	private final CameraLocatorReceiver cameraLocatorReceiver = new CameraLocatorReceiver();

	private static class CameraLocatorReceiver extends BroadcastReceiver {

		private MainActivity activity;
		private TimelapseApp app;

		@Override
		public void onReceive(Context context, Intent intent) {
			logger.debug("CameraLocatorReceiver::OnReceive: {}",
					intent.getAction());
			if (CameraLocatorService.CAMERA_SEARCH_FAILED.equals(intent.getAction())) {
				app.setCameraUrl("");
				app.setCameraName("");
				Toast.makeText(activity,
						activity.getText(R.string.msg_error_connection),
						Toast.LENGTH_LONG).show();
			} else if (CameraLocatorService.CAMERA_FOUND.equals(intent.getAction()) ) {
				app.setCameraName(intent
						.getStringExtra(CameraLocatorService.CAMERA_NAME));
				app.setCameraUrl(intent
						.getStringExtra(CameraLocatorService.CAMERA_URL));
			}
			activity.setUiState();
		}

		public void attach(MainActivity activity, TimelapseApp app) {
			this.activity = activity;
			this.app = app;
		}

		public void detach() {
			this.activity = null;
			this.app = null;
		}

	}

	private static class OnCompleteReceiver extends BroadcastReceiver {
		private TextView timelapseStatus;
		private String completeMsg;
		private String errorMsg;
		MainActivity activity;

		@Override
		public void onReceive(Context context, Intent intent) {
			logger.debug("OnCompleteReceiver::OnReceive: {}",
					intent.getAction());
			String msg = this.completeMsg;
			if (TimelapseService.TIMELAPSE_ERROR_BROADCAST.equals(intent.getAction())) {
				msg = String.format(this.errorMsg,
						intent.getStringExtra(TimelapseService.ERROR_MESSAGE));
			}
			timelapseStatus.setText(msg);
			activity.setUiState();
		}

		public void attach(TextView timelapseStatus, String completeMsg,
				String errorMsg, MainActivity activity) {
			this.timelapseStatus = timelapseStatus;
			this.completeMsg = completeMsg;
			this.errorMsg = errorMsg;
			this.activity = activity;
		}

		public void detach() {
			this.activity = null;
			this.timelapseStatus = null;
		}

	}

	private static class ProgressReceiver extends BroadcastReceiver {

		private MainActivity activity;

		@Override
		public void onReceive(Context context, Intent intent) {
			logger.debug("ProgressReceiver::OnReceive");
			activity.setUiState();
		}

		public void attach(MainActivity activity) {
			this.activity = activity;
		}

		public void detach() {
			this.activity = null;
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logger.debug("onCreate start.");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		handler = new Handler();

		scanButton = (Button) findViewById(R.id.btnScan);
		status = (TextView) findViewById(R.id.lblStatus);
		timelapseStatus = (TextView) findViewById(R.id.timelapseStatus);

		preview = (ImageView) findViewById(R.id.preview);

		startTimelapseButton = (Button) findViewById(R.id.btnStart);
		stopTimelapseButton = (Button) findViewById(R.id.btnStop);
		framesText = (TextView) findViewById(R.id.frames);
		intervalText = (TextView) findViewById(R.id.interval);


		logger.debug("onCreate complete.");
	}

	@Override
	protected void onStop() {
		logger.trace("onStop called");
		super.onStop();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				completionReceiver);
		completionReceiver.detach();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				progressReceiver);
		progressReceiver.detach();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				cameraLocatorReceiver);
		cameraLocatorReceiver.detach();
	}
	
	@Override
	protected void onStart() {
		logger.trace("onStart called");
		super.onStart();
		
		completionReceiver.attach(timelapseStatus,
				getString(R.string.msg_timelapse_complete),
				getString(R.string.msg_timelapse_error), this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(TimelapseService.TIMELAPSE_COMPLETE_BROADCAST);
		filter.addAction(TimelapseService.TIMELAPSE_ERROR_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(
				completionReceiver, filter);

		progressReceiver.attach(this);
		filter = new IntentFilter();
		filter.addAction(TimelapseService.TIMELAPSE_PROGRESS_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(
				progressReceiver, filter);

		cameraLocatorReceiver.attach(this, (TimelapseApp) getApplication());
		filter = new IntentFilter();
		filter.addAction(CameraLocatorService.CAMERA_FOUND);
		filter.addAction(CameraLocatorService.CAMERA_SEARCH_FAILED);
		LocalBroadcastManager.getInstance(this).registerReceiver(
				cameraLocatorReceiver, filter);

	}

	@Override
	protected void onResume() {
		super.onResume();
		logger.debug("onResume start.");

		scanButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onScan();
			}
		});

		startTimelapseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				OnStartTimelapse();

			}
		});

		stopTimelapseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				OnStopTimelapse();
			}
		});

		setUiState();

		logger.debug("onResume complete.");
	}

	private void setUiState() {
		TimelapseApp app = (TimelapseApp) getApplication();
		String cameraUrl = app.getCameraUrl();
		if (cameraUrl != null && cameraUrl.length() > 0) {
			status.setText(String.format(getText(R.string.msg_connected_to)
					.toString(), app.getCameraName()));
			if (app.isTimelapseActive()) {
				// Timelapse in progress
				scanButton.setEnabled(false);
				startTimelapseButton.setEnabled(false);
				stopTimelapseButton.setEnabled(true);
				int remainingFrames = app.getRemainingFrames();
				float interval = app.getInterval();
				timelapseStatus.setText(String.format(
						getText(R.string.msg_timelapse_progress).toString(),
						remainingFrames, interval));
				Bitmap img = app.getPictureReference().get();
				if (img != null) {
					preview.setImageBitmap(img);
				}
				
				framesText.setText( Integer.toString(app.getDesiredFrames()));
				intervalText.setText( Integer.toString(app.getDesiredInterval()));
				
			} else {
				// Camera located timelapse not running
				scanButton.setEnabled(true);
				startTimelapseButton.setEnabled(true);
				stopTimelapseButton.setEnabled(false);
				timelapseStatus.setText("");
			}
		} else {
			status.setText(getText(R.string.not_connected)
					.toString());
			// Initial state - no connection to camera - DO nothing
			scanButton.setEnabled(true);
			startTimelapseButton.setEnabled(false);
			stopTimelapseButton.setEnabled(false);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void onScan() {
		// Scan for camera
		Intent intent = new Intent(this, CameraLocatorService.class);
		startService(intent);
	}

	private void OnStartTimelapse() {
		// Read the timelapse parameters
		int interval, frames;
		try {
			interval = Integer.parseInt(intervalText.getText().toString());
			frames = Integer.parseInt(framesText.getText().toString());
		} catch (NumberFormatException e) {
			logger.debug(e, "Cannot validate inputs");
			Toast.makeText(this, getText(R.string.msg_numbers_required),
					Toast.LENGTH_LONG).show();
			return;
		}
		if (interval < MINIMAL_INTERVAL) {
			logger.debug("The minimum interval between shots is 3 seconds");
			Toast.makeText(
					this,
					String.format(getText(R.string.msg_minimal_interval)
							.toString(), MINIMAL_INTERVAL), Toast.LENGTH_LONG)
					.show();
			return;
		}

		if (frames < 1) {
			logger.debug("Cannot validate inputs. Frames must be above 0");
			Toast.makeText(this, getText(R.string.msg_invalid_frames),
					Toast.LENGTH_LONG).show();
			return;
		}
		TimelapseApp app = (TimelapseApp) getApplication();
		
		app.setDesiredFrames(frames);
		app.setDesiredInterval(interval);
		
		
		// Create intent and start background job
		Intent intent = new Intent(this, TimelapseService.class);
		intent.setAction(TimelapseService.START_TIMELAPSE);
		intent.putExtra(TimelapseService.INTERVAL_PARAM, interval);
		intent.putExtra(TimelapseService.FRAMES_PARAM, frames);
		intent.putExtra(TimelapseService.CAMERA_URL_PARAM,
				((TimelapseApp) getApplication()).getCameraUrl());
		WakefulBroadcastReceiver.startWakefulService(this, intent);

		// Disable the start & scan button
		startTimelapseButton.setEnabled(false);
		scanButton.setEnabled(false);
		// Enable the stop button
		stopTimelapseButton.setEnabled(true);
	}

	private void OnStopTimelapse() {
		Intent intent = new Intent(this, TimelapseService.class);
		intent.setAction(TimelapseService.STOP_TIMELAPSE);
		startService(intent);
	}
}
