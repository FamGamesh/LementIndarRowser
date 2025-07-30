package com.desktopbrowser.advanced;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DownloadsActivity extends AppCompatActivity {
    
    private static final String TAG = "DownloadsActivity";
    
    private LinearLayout mainContainer;
    private RecyclerView downloadsRecyclerView;
    private TextView emptyStateText;
    private AdManager adManager;
    private DownloadManager downloadManager;
    private List<DownloadItem> downloadItems;
    private DownloadsAdapter adapter;
    
    // ENHANCED: Live progress tracking
    private Timer progressUpdateTimer;
    private Handler uiHandler;
    private boolean isActivityActive = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        
        try {
            setupToolbar();
            initializeViews();
            setupAds();
            loadDownloads();
            
            // ENHANCED: Start live progress updates
            startLiveProgressUpdates();
            
            Log.d(TAG, "✅ Enhanced Downloads activity initialized successfully with live progress tracking");
        } catch (Exception e) {
            Log.e(TAG, "💥 Error initializing downloads activity", e);
            Toast.makeText(this, "Error loading downloads", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("📥 Downloads");
        }
    }
    
    private void initializeViews() {
        mainContainer = findViewById(R.id.main_container);
        downloadsRecyclerView = findViewById(R.id.downloads_recycler_view);
        emptyStateText = findViewById(R.id.empty_state_text);
        
        // Initialize managers and handlers
        adManager = AdManager.getInstance(this);
        downloadManager = DownloadManager.getInstance(this);
        downloadItems = new ArrayList<>();
        uiHandler = new Handler(Looper.getMainLooper());
        
        // Setup RecyclerView
        downloadsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        if (mainContainer == null || downloadsRecyclerView == null) {
            throw new RuntimeException("Required views not found in downloads layout");
        }
    }
    
    private void setupAds() {
        // Add banner ads to top and bottom of downloads section
        adManager.addBannerAdToLayout(this, mainContainer, true);  // Top ad
        adManager.addBannerAdToLayout(this, mainContainer, false); // Bottom ad
        
        Log.d(TAG, "📱 Banner ads added to downloads section");
    }
    
    // ENHANCED: Load downloads with live progress support
    private void loadDownloads() {
        try {
            Log.d(TAG, "🔍 Loading downloads from storage with live progress support");
            downloadItems = downloadManager.getAllDownloads();
            
            // ENHANCED: Also scan Downloads directory for files not in our database
            scanDownloadsDirectory();
            
            if (downloadItems.isEmpty()) {
                // Show empty state
                downloadsRecyclerView.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.VISIBLE);
                emptyStateText.setText("📥 No downloads yet\n\nDownloaded files will appear here with live progress tracking");
                Log.d(TAG, "📭 No downloads found - showing empty state");
            } else {
                // Show downloads list with enhanced adapter
                downloadsRecyclerView.setVisibility(View.VISIBLE);
                emptyStateText.setVisibility(View.GONE);
                
                adapter = new DownloadsAdapter(downloadItems, this);
                downloadsRecyclerView.setAdapter(adapter);
                
                Log.d(TAG, "✅ Loaded " + downloadItems.size() + " downloads with live progress support");
                
                // Log active downloads
                int activeDownloads = 0;
                for (DownloadItem item : downloadItems) {
                    if (item.isActive) {
                        activeDownloads++;
                    }
                }
                Log.d(TAG, "📊 Active downloads: " + activeDownloads + "/" + downloadItems.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "💥 Error loading downloads", e);
            // Show error state
            downloadsRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("❌ Error loading downloads\n\nPlease try again later");
        }
    }
    
    // ENHANCED: Start live progress updates
    private void startLiveProgressUpdates() {
        if (progressUpdateTimer != null) {
            progressUpdateTimer.cancel();
        }
        
        progressUpdateTimer = new Timer();
        progressUpdateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isActivityActive) {
                    updateLiveProgress();
                }
            }
        }, 1000, 2000); // Update every 2 seconds
        
        Log.d(TAG, "🔄 Live progress updates started");
    }
    
    // ENHANCED: Update live progress for all active downloads
    private void updateLiveProgress() {
        try {
            List<DownloadItem> activeDownloads = downloadManager.getActiveDownloads();
            
            if (!activeDownloads.isEmpty()) {
                Log.d(TAG, "📊 Updating live progress for " + activeDownloads.size() + " active downloads");
                
                // Update UI on main thread
                uiHandler.post(() -> {
                    try {
                        if (adapter != null) {
                            // Refresh the downloads list to show updated progress
                            downloadItems.clear();
                            downloadItems.addAll(downloadManager.getAllDownloads());
                            adapter.notifyDataSetChanged();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating progress UI", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating live progress", e);
        }
    }
    
    // ENHANCED: Stop live progress updates
    private void stopLiveProgressUpdates() {
        if (progressUpdateTimer != null) {
            progressUpdateTimer.cancel();
            progressUpdateTimer = null;
            Log.d(TAG, "🛑 Live progress updates stopped");
        }
    }
    
    // FIXED: Scan Downloads directory for files that might not be in our database
    private void scanDownloadsDirectory() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                File[] files = downloadsDir.listFiles();
                if (files != null) {
                    Log.d(TAG, "🔍 Scanning Downloads directory, found " + files.length + " files");
                    
                    for (File file : files) {
                        if (file.isFile()) {
                            // Check if this file is already in our download list
                            boolean alreadyTracked = false;
                            for (DownloadItem item : downloadItems) {
                                if (file.getAbsolutePath().equals(item.filepath)) {
                                    alreadyTracked = true;
                                    break;
                                }
                            }
                            
                            // If not tracked, add it to our list
                            if (!alreadyTracked) {
                                Log.d(TAG, "📄 Found untracked download: " + file.getName());
                                addUnTrackedDownload(file);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "💥 Error scanning Downloads directory", e);
        }
    }
    
    // Add untracked download to our list
    private void addUnTrackedDownload(File file) {
        try {
            DownloadManager.FileTypeInfo typeInfo = downloadManager.intelligentFileTypeDetection(
                "file://" + file.getAbsolutePath(), file.getName());
            
            DownloadItem item = new DownloadItem();
            item.url = "file://" + file.getAbsolutePath();
            item.filename = file.getName();
            item.filepath = file.getAbsolutePath();
            item.downloadId = String.valueOf(System.currentTimeMillis());
            item.fileSize = file.length();
            item.downloadTime = file.lastModified();
            item.fileType = typeInfo.category;
            item.fileIcon = typeInfo.icon;
            item.fileDescription = typeInfo.description;
            
            // Set as completed since it's already downloaded
            item.downloadProgress = 100;
            item.downloadStatus = "✅ Completed";
            item.isActive = false;
            
            downloadItems.add(0, item); // Add to beginning of list
            Log.d(TAG, "✅ Added untracked download: " + file.getName());
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error adding untracked download", e);
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
    
    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        
        // ENHANCED: Refresh downloads and restart live updates when returning to activity
        loadDownloads();
        startLiveProgressUpdates();
        
        Log.d(TAG, "🔄 Downloads activity resumed - refreshing downloads list with live progress");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        
        // Stop live updates when activity is paused to save battery
        stopLiveProgressUpdates();
        
        Log.d(TAG, "⏸️ Downloads activity paused - stopping live progress updates");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityActive = false;
        
        // ENHANCED: Clean up resources
        stopLiveProgressUpdates();
        
        Log.d(TAG, "💀 Downloads activity destroyed - cleaned up live progress resources");
    }
    
    // ENHANCED: Public method to refresh downloads (can be called from other activities)
    public void refreshDownloads() {
        uiHandler.post(() -> {
            loadDownloads();
        });
    }
    
    // ENHANCED: Get count of active downloads
    public int getActiveDownloadsCount() {
        int count = 0;
        for (DownloadItem item : downloadItems) {
            if (item.isActive) {
                count++;
            }
        }
        return count;
    }
    
    // ENHANCED: Get total downloads count
    public int getTotalDownloadsCount() {
        return downloadItems.size();
    }
}