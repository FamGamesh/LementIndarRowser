package com.desktopbrowser.advanced;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
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

public class DownloadsActivity extends AppCompatActivity {
    
    private static final String TAG = "DownloadsActivity";
    
    private LinearLayout mainContainer;
    private RecyclerView downloadsRecyclerView;
    private TextView emptyStateText;
    private AdManager adManager;
    private DownloadManager downloadManager;
    private List<DownloadItem> downloadItems;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        
        try {
            setupToolbar();
            initializeViews();
            setupAds();
            loadDownloads();
            
            Log.d(TAG, "✅ Downloads activity initialized successfully");
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
        
        // Initialize managers
        adManager = AdManager.getInstance(this);
        downloadManager = DownloadManager.getInstance(this);
        downloadItems = new ArrayList<>();
        
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
    
    // FIXED: Improved download loading with better file detection
    private void loadDownloads() {
        try {
            Log.d(TAG, "🔍 Loading downloads from storage");
            downloadItems = downloadManager.getAllDownloads();
            
            // FIXED: Also scan Downloads directory for files not in our database
            scanDownloadsDirectory();
            
            if (downloadItems.isEmpty()) {
                // Show empty state
                downloadsRecyclerView.setVisibility(View.GONE);
                emptyStateText.setVisibility(View.VISIBLE);
                emptyStateText.setText("📥 No downloads yet\n\nDownloaded files will appear here");
                Log.d(TAG, "📭 No downloads found - showing empty state");
            } else {
                // Show downloads list
                downloadsRecyclerView.setVisibility(View.VISIBLE);
                emptyStateText.setVisibility(View.GONE);
                
                DownloadsAdapter adapter = new DownloadsAdapter(downloadItems, this);
                downloadsRecyclerView.setAdapter(adapter);
                
                Log.d(TAG, "✅ Loaded " + downloadItems.size() + " downloads");
            }
        } catch (Exception e) {
            Log.e(TAG, "💥 Error loading downloads", e);
            // Show error state
            downloadsRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("❌ Error loading downloads\n\nPlease try again later");
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
        // FIXED: Refresh downloads when returning to activity
        loadDownloads();
        Log.d(TAG, "🔄 Downloads activity resumed - refreshing downloads list");
    }
}