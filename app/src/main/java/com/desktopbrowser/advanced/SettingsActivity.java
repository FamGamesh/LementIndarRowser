package com.desktopbrowser.advanced;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;

public class SettingsActivity extends AppCompatActivity {
    
    private Switch desktopModeSwitch;
    private Switch javascriptSwitch;
    private Switch popupBlockerSwitch;
    private Switch stealthModeSwitch;
    private Switch autofillSwitch;
    private SharedPreferences preferences;
    private AdManager adManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Initialize AdManager
        adManager = AdManager.getInstance(this);
        
        setupToolbar();
        initializeViews();
        setupAds();
        loadPreferences();
        setupListeners();
    }
    
    private void setupAds() {
        android.widget.LinearLayout mainContainer = findViewById(R.id.settings_main_container);
        if (mainContainer != null) {
            // Add banner ads at top and bottom
            adManager.addBannerAdToLayout(this, mainContainer, true);  // Top ad
            adManager.addBannerAdToLayout(this, mainContainer, false); // Bottom ad
        }
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Browser Settings");
        }
    }
    
    private void initializeViews() {
        desktopModeSwitch = findViewById(R.id.switch_desktop_mode);
        javascriptSwitch = findViewById(R.id.switch_javascript);
        popupBlockerSwitch = findViewById(R.id.switch_popup_blocker);
        stealthModeSwitch = findViewById(R.id.switch_stealth_mode);
        autofillSwitch = findViewById(R.id.switch_autofill);
        
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Add error logs management button
        Button errorLogsButton = findViewById(R.id.btn_error_logs);
        if (errorLogsButton != null) {
            errorLogsButton.setOnClickListener(v -> showErrorLogsDialog());
        }
    }
    
    private void loadPreferences() {
        desktopModeSwitch.setChecked(preferences.getBoolean("desktop_mode", true));
        javascriptSwitch.setChecked(preferences.getBoolean("javascript_enabled", true));
        popupBlockerSwitch.setChecked(preferences.getBoolean("popup_blocker", true));
        stealthModeSwitch.setChecked(preferences.getBoolean("stealth_mode", true));
        autofillSwitch.setChecked(preferences.getBoolean("autofill_enabled", false));
    }
    
    private void setupListeners() {
        desktopModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("desktop_mode", isChecked).apply();
        });
        
        javascriptSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("javascript_enabled", isChecked).apply();
        });
        
        popupBlockerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("popup_blocker", isChecked).apply();
        });
        
        stealthModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("stealth_mode", isChecked).apply();
        });
        
        autofillSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("autofill_enabled", isChecked).apply();
        });
    }
    
    private void showErrorLogsDialog() {
        try {
            if (!ErrorLogger.logFileExists(this)) {
                Toast.makeText(this, "No error logs found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String logs = ErrorLogger.readLogFile(this);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📋 Error Logs");
            
            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            android.widget.TextView logsView = new android.widget.TextView(this);
            logsView.setPadding(30, 30, 30, 30);
            logsView.setTextSize(12);
            logsView.setTypeface(android.graphics.Typeface.MONOSPACE);
            logsView.setText(logs);
            
            scrollView.addView(logsView);
            builder.setView(scrollView);
            
            builder.setPositiveButton("Close", null);
            builder.setNegativeButton("Clear Logs", (dialog, which) -> {
                ErrorLogger.clearLogFile(this);
                Toast.makeText(this, "Error logs cleared", Toast.LENGTH_SHORT).show();
            });
            
            builder.setNeutralButton("Share", (dialog, which) -> {
                shareErrorLogs(logs);
            });
            
            builder.show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load logs: " + e.getMessage(), 
                          Toast.LENGTH_LONG).show();
            ErrorLogger.logError(this, "SETTINGS_ERROR", e);
        }
    }
    
    private void shareErrorLogs(String logs) {
        try {
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Desktop Browser Error Logs");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, logs);
            startActivity(android.content.Intent.createChooser(shareIntent, "Share Error Logs"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share logs: " + e.getMessage(), 
                          Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}