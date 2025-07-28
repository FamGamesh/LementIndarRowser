package com.desktopbrowser.advanced;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {
    
    private Switch desktopModeSwitch;
    private Switch javascriptSwitch;
    private Switch popupBlockerSwitch;
    private Switch stealthModeSwitch;
    private Switch autofillSwitch;
    private SharedPreferences preferences;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        setupToolbar();
        initializeViews();
        loadPreferences();
        setupListeners();
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
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}