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
    
    private void loadDownloads() {
        try {
            Log.d(TAG, "🔍 Loading downloads from storage");
            downloadItems = downloadManager.getAllDownloads();
            
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
        // Refresh downloads when returning to activity
        loadDownloads();
        Log.d(TAG, "🔄 Downloads activity resumed - refreshing downloads list");
    }
}