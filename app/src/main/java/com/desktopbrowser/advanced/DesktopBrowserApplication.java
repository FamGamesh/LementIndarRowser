package com.desktopbrowser.advanced;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class DesktopBrowserApplication extends Application {
    
    private static final String TAG = "DesktopBrowserApp";
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Store the default exception handler
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        // Set up custom exception handler for crash logging
        Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(this));
        
        android.util.Log.i(TAG, "DesktopBrowser Application initialized with crash logging");
    }
    
    private class CustomExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        
        public CustomExceptionHandler(Context context) {
            this.context = context;
        }
        
        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            try {
                // Log the crash to file
                ErrorLogger.logError(context, "FATAL_CRASH", throwable);
                
                // Show error dialog to user on main thread
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    new Handler(Looper.getMainLooper()).post(() -> showCrashDialog(throwable));
                } else {
                    showCrashDialog(throwable);
                }
                
                // Small delay to ensure file writing and dialog display
                Thread.sleep(2000);
                
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error in custom exception handler", e);
            } finally {
                // Call the default handler to properly terminate the app
                if (defaultExceptionHandler != null) {
                    defaultExceptionHandler.uncaughtException(thread, throwable);
                } else {
                    System.exit(2);
                }
            }
        }
        
        private void showCrashDialog(Throwable throwable) {
            try {
                Intent intent = new Intent(context, CrashReportActivity.class);
                intent.putExtra("crash_message", throwable.getMessage() != null ? throwable.getMessage() : "Unknown error occurred");
                intent.putExtra("crash_type", throwable.getClass().getSimpleName());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to show crash dialog", e);
                // Fallback: show simple toast if possible
                try {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        android.widget.Toast.makeText(context, 
                            "App crashed: " + (throwable.getMessage() != null ? throwable.getMessage() : "Unknown error"), 
                            android.widget.Toast.LENGTH_LONG).show();
                    }
                } catch (Exception toastError) {
                    android.util.Log.e(TAG, "Even toast failed", toastError);
                }
            }
        }
    }
}