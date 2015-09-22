package com.trudovak.simplytimelapse.utils;

import java.text.MessageFormat;

import android.util.Log;

public class Logger {
	final static int MIN_LOG_LEVEL = 1; 
	String tag;

	public Logger(Class<?> t) {
		tag = t.getSimpleName();
	}

	public void trace(String msg, Object... args) {
		if (isTraceEnabled()) {
			Log.v(tag, MessageFormat.format(msg, args));
		}
	}

	public void trace(Throwable t, String msg, Object... args) {
		if (isTraceEnabled()) {
			Log.v(tag, MessageFormat.format(msg, args), t);
		}
	}

	public boolean isTraceEnabled() {
		return isLoggable(tag, Log.VERBOSE);
	}

	public void debug(String msg, Object... args) {
		if (isDebugEnabled()) {
			Log.d(tag, MessageFormat.format(msg, args));
		}
	}

	public void debug(Throwable t, String msg, Object... args) {
		if (isDebugEnabled()) {
			Log.d(tag, MessageFormat.format(msg, args), t);
		}
	}

	public boolean isDebugEnabled() {
		return isLoggable(tag, Log.DEBUG);
	}

	public void info(String msg, Object... args) {
		if (isInfoEnabled()) {
			Log.i(tag, MessageFormat.format(msg, args));
		}
	}

	public void info(Throwable t, String msg, Object... args) {
		if (isInfoEnabled()) {
			Log.i(tag, MessageFormat.format(msg, args), t);
		}
	}

	public boolean isInfoEnabled() {
		return isLoggable(tag, Log.INFO);
	}

	public void warn(String msg, Object... args) {
		if (isWarnEnabled()) {
			Log.w(tag, MessageFormat.format(msg, args));
		}
	}

	public void warn(Throwable t, String msg, Object... args) {
		if (isWarnEnabled()) {
			Log.w(tag, MessageFormat.format(msg, args), t);
		}
	}

	public boolean isWarnEnabled() {
		return isLoggable(tag, Log.WARN);
	}

	public void error(String msg, Object... args) {
		if (isErrorEnabled()) {
			Log.e(tag, MessageFormat.format(msg, args));
		}
	}

	public void error(Throwable t, String msg, Object... args) {
		if (isErrorEnabled()) {
			Log.e(tag, MessageFormat.format(msg, args), t);
		}
	}

	public boolean isErrorEnabled() {
		return isLoggable(tag, Log.ERROR);
	}

	public void fatal(String msg, Object... args) {
		if (isFatalEnabled()) {
			Log.wtf(tag, MessageFormat.format(msg, args));
		}
	}

	public void fatal(Throwable t, String msg, Object... args) {
		if (isFatalEnabled()) {
			Log.wtf(tag, MessageFormat.format(msg, args), t);
		}
	}

	public boolean isFatalEnabled() {
		return isLoggable(tag, Log.ASSERT);
	}

	private boolean isLoggable(String tag, int level) {
		return MIN_LOG_LEVEL < level || Log.isLoggable(tag, level);
	}
}
