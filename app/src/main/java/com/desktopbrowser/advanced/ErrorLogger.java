package com.desktopbrowser.advanced;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ErrorLogger {
    
    private static final String LOG_FILE_NAME = "app_error_logs.txt";
    private static final String TAG = "ErrorLogger";
    
    public static void logError(Context context, String errorType, Throwable throwable) {
        try {
            String logEntry = createLogEntry(errorType, throwable);
            saveLogToFile(context, logEntry);
            android.util.Log.e(TAG, "Error logged to file: " + errorType, throwable);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to log error to file", e);
        }
    }
    
    public static void logError(Context context, String errorType, String errorMessage) {
        try {
            String logEntry = createLogEntry(errorType, errorMessage);
            saveLogToFile(context, logEntry);
            android.util.Log.e(TAG, "Error logged to file: " + errorType + " - " + errorMessage);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to log error to file", e);
        }
    }
    
    private static String createLogEntry(String errorType, Throwable throwable) {
        StringBuilder logEntry = new StringBuilder();
        
        // Add timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        
        logEntry.append("=====================================\n");
        logEntry.append("CRASH REPORT - ").append(timestamp).append("\n");
        logEntry.append("=====================================\n");
        logEntry.append("Error Type: ").append(errorType).append("\n");
        logEntry.append("App Version: 1.0\n");
        logEntry.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
        logEntry.append("Device Model: ").append(Build.MODEL).append("\n");
        logEntry.append("Device Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        logEntry.append("SDK Version: ").append(Build.VERSION.SDK_INT).append("\n");
        logEntry.append("\n");
        
        if (throwable != null) {
            logEntry.append("Exception: ").append(throwable.getClass().getSimpleName()).append("\n");
            logEntry.append("Message: ").append(throwable.getMessage()).append("\n");
            logEntry.append("\nStack Trace:\n");
            
            // Get full stack trace
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            throwable.printStackTrace(pw);
            logEntry.append(sw.toString());
        }
        
        logEntry.append("\n=====================================\n\n");
        
        return logEntry.toString();
    }
    
    private static String createLogEntry(String errorType, String errorMessage) {
        StringBuilder logEntry = new StringBuilder();
        
        // Add timestamp
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        
        logEntry.append("=====================================\n");
        logEntry.append("ERROR REPORT - ").append(timestamp).append("\n");
        logEntry.append("=====================================\n");
        logEntry.append("Error Type: ").append(errorType).append("\n");
        logEntry.append("App Version: 1.0\n");
        logEntry.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
        logEntry.append("Device Model: ").append(Build.MODEL).append("\n");
        logEntry.append("Device Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        logEntry.append("SDK Version: ").append(Build.VERSION.SDK_INT).append("\n");
        logEntry.append("\n");
        logEntry.append("Error Message: ").append(errorMessage).append("\n");
        logEntry.append("\n=====================================\n\n");
        
        return logEntry.toString();
    }
    
    private static void saveLogToFile(Context context, String logEntry) throws IOException {
        // Save to internal storage (app-specific directory)
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        
        // Create file if it doesn't exist
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
        
        // Append to file
        FileWriter writer = new FileWriter(logFile, true); // true for append mode
        writer.write(logEntry);
        writer.close();
        
        android.util.Log.i(TAG, "Error log saved to: " + logFile.getAbsolutePath());
    }
    
    public static String getLogFilePath(Context context) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        return logFile.getAbsolutePath();
    }
    
    public static boolean logFileExists(Context context) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        return logFile.exists() && logFile.length() > 0;
    }
    
    public static String readLogFile(Context context) {
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            if (!logFile.exists()) {
                return "No error logs found.";
            }
            
            java.io.FileInputStream fis = new java.io.FileInputStream(logFile);
            java.io.InputStreamReader isr = new java.io.InputStreamReader(fis);
            java.io.BufferedReader br = new java.io.BufferedReader(isr);
            
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            br.close();
            return content.toString();
            
        } catch (IOException e) {
            android.util.Log.e(TAG, "Failed to read log file", e);
            return "Failed to read error logs: " + e.getMessage();
        }
    }
    
    public static void clearLogFile(Context context) {
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            if (logFile.exists()) {
                logFile.delete();
                android.util.Log.i(TAG, "Error log file cleared");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to clear log file", e);
        }
    }
}