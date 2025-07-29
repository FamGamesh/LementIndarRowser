package com.desktopbrowser.advanced;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;

public class CrashReportActivity extends Activity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String crashMessage = getIntent().getStringExtra("crash_message");
        String crashType = getIntent().getStringExtra("crash_type");
        
        showCrashDialog(crashType, crashMessage);
    }
    
    private void showCrashDialog(String crashType, String crashMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 App Crashed");
        
        // Create a scrollable text view for the error message
        ScrollView scrollView = new ScrollView(this);
        TextView messageView = new TextView(this);
        messageView.setPadding(50, 30, 50, 30);
        messageView.setTextSize(14);
        messageView.setText(
            "😞 The app encountered an unexpected error and crashed.\n\n" +
            "🔍 Error Details:\n" +
            "Type: " + (crashType != null ? crashType : "Unknown") + "\n" +
            "Message: " + (crashMessage != null ? crashMessage : "No details available") + "\n\n" +
            "📝 This crash has been automatically logged to help improve the app.\n\n" +
            "💡 What you can do:\n" +
            "• Restart the app and try again\n" +
            "• Avoid the action that caused the crash\n" +
            "• Check the error logs in Settings if needed\n\n" +
            "📧 If this keeps happening, please contact support with the error logs."
        );
        
        scrollView.addView(messageView);
        builder.setView(scrollView);
        
        builder.setPositiveButton("Restart App", (dialog, which) -> {
            restartApp();
        });
        
        builder.setNegativeButton("View Logs", (dialog, which) -> {
            showErrorLogs();
        });
        
        builder.setNeutralButton("Close App", (dialog, which) -> {
            finishAffinity();
            System.exit(0);
        });
        
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void restartApp() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            android.util.Log.e("CrashReportActivity", "Failed to restart app", e);
            finishAffinity();
            System.exit(0);
        }
    }
    
    private void showErrorLogs() {
        try {
            String logs = ErrorLogger.readLogFile(this);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📋 Error Logs");
            
            ScrollView scrollView = new ScrollView(this);
            TextView logsView = new TextView(this);
            logsView.setPadding(30, 30, 30, 30);
            logsView.setTextSize(12);
            logsView.setTypeface(android.graphics.Typeface.MONOSPACE);
            logsView.setText(logs);
            
            scrollView.addView(logsView);
            builder.setView(scrollView);
            
            builder.setPositiveButton("Close", null);
            builder.setNegativeButton("Clear Logs", (dialog, which) -> {
                ErrorLogger.clearLogFile(this);
                android.widget.Toast.makeText(this, "Error logs cleared", android.widget.Toast.LENGTH_SHORT).show();
            });
            
            builder.setNeutralButton("Back", (dialog, which) -> {
                showCrashDialog(getIntent().getStringExtra("crash_type"), 
                              getIntent().getStringExtra("crash_message"));
            });
            
            builder.show();
            
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Failed to load logs: " + e.getMessage(), 
                                        android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Prevent back button, force user to make a choice
        android.widget.Toast.makeText(this, "Please choose an option to continue", 
                                    android.widget.Toast.LENGTH_SHORT).show();
    }
}