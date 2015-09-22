package com.trudovak.simplytimelapse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import android.app.Application;
import android.graphics.Bitmap;

public class TimelapseApp extends Application {
	private final AtomicInteger desiredInterval = new AtomicInteger(0);
	private final AtomicInteger desiredFrames = new AtomicInteger(0);
	// Store float in int as described in JDK docs
	private final AtomicInteger interval = new AtomicInteger(
			Float.floatToIntBits(0f));
	private final AtomicInteger remainingFrames = new AtomicInteger(0);
	private final AtomicInteger total = new AtomicInteger(0);
	private final AtomicReference<Bitmap> pictureReference = new AtomicReference<Bitmap>();
	private final AtomicBoolean timelapseActive = new AtomicBoolean();
	private final AtomicReference<String> cameraUrl = new AtomicReference<String>(
			null);
	private final AtomicReference<String> cameraName = new AtomicReference<String>(
			null);

	public AtomicReference<Bitmap> getPictureReference() {
		return pictureReference;
	}

	public boolean isTimelapseActive() {
		return timelapseActive.get();
	}

	public void setTimelapseActive(boolean isActive) {
		timelapseActive.set(isActive);
	}

	public String getCameraUrl() {
		return cameraUrl.get();
	}

	public void setCameraUrl(String cameraUrl) {
		this.cameraUrl.set(cameraUrl);
	}

	public String getCameraName() {
		return cameraName.get();
	}

	public void setCameraName(String cameraName) {
		this.cameraName.set(cameraName);
	}

	public int getTotal() {
		return total.get();
	}

	public void setTotal(int total) {
		this.total.set(total);
	}

	public int getRemainingFrames() {
		return remainingFrames.get();
	}

	public void setRemainingFrames(int remainingFrames) {
		this.remainingFrames.set(remainingFrames);
	}

	public float getInterval() {
		return Float.intBitsToFloat(interval.get());
	}

	public void setInterval(float interval) {
		this.interval.set(Float.floatToIntBits(interval));
	}

	public int getDesiredFrames() {
		return this.desiredFrames.get();
	}

	public void setDesiredFrames(int frames) {
		this.desiredFrames.set(frames);
	}

	public int getDesiredInterval() {
		return this.desiredInterval.get();
	}

	public void setDesiredInterval(int interval) {
		this.desiredInterval.set(interval);
	}
}
