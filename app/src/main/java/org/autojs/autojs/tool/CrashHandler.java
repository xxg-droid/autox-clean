package org.autojs.autojs.tool;

/**
 * Created by Stardust on 2017/2/2.
 * Modified: Bugly 上报已移除，崩溃只在本地处理（显示错误报告），不向任何服务器上传。
 */


import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.stardust.app.GlobalAppContext;

import org.autojs.autoxjs.BuildConfig;
import org.mozilla.javascript.RhinoException;

import com.stardust.view.accessibility.AccessibilityService;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Map;

public class CrashHandler implements UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static int crashCount = 0;
    private static long firstCrashMillis = 0;
    private final Class<?> mErrorReportClass;
    private UncaughtExceptionHandler mSystemHandler;

    public CrashHandler(Class<?> errorReportClass) {
        this.mErrorReportClass = errorReportClass;
        mSystemHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "Uncaught Exception", ex);
        if (thread != Looper.getMainLooper().getThread()) {
            return;
        }
        AccessibilityService service = AccessibilityService.Companion.getInstance();
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.d(TAG, "disable service: " + service);
            service.disableSelf();
        } else {
            Log.d(TAG, "cannot disable service: " + service);
        }
        if (BuildConfig.DEBUG) {
            mSystemHandler.uncaughtException(thread, ex);
        } else {
            try {
                if (!crashTooManyTimes()) {
                    String msg = ex.getClass().getName() + ": " + ex.getMessage();
                    startErrorReportActivity(msg, Log.getStackTraceString(ex));
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            mSystemHandler.uncaughtException(thread, ex);
        }
    }

    private void startErrorReportActivity(String msg, String detail) {
        Intent intent = new Intent(GlobalAppContext.get(), this.mErrorReportClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("message", msg);
        intent.putExtra("error", detail);
        GlobalAppContext.get().startActivity(intent);
    }

    private boolean crashTooManyTimes() {
        if (crashIntervalTooLong()) {
            resetCrashCount();
            return false;
        }
        crashCount++;
        return crashCount >= 5;
    }

    private void resetCrashCount() {
        firstCrashMillis = System.currentTimeMillis();
        crashCount = 0;
    }

    private boolean crashIntervalTooLong() {
        return System.currentTimeMillis() - firstCrashMillis > 3000;
    }


}
