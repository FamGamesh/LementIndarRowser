package com.desktopbrowser.advanced;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    
    private ListView historyListView;
    private TextView emptyView;
    private HistoryManager historyManager;
    private List<HistoryManager.HistoryItem> historyItems;
    private HistoryAdapter adapter;
    private AdManager adManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        
        // Initialize AdManager
        adManager = AdManager.getInstance(this);
        
        setupToolbar();
        initializeViews();
        setupAds();
        loadHistory();
    }
    
    private void setupAds() {
        android.widget.LinearLayout mainContainer = findViewById(R.id.history_main_container);
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
            getSupportActionBar().setTitle("Browsing History");
        }
    }
    
    private void initializeViews() {
        historyListView = findViewById(R.id.history_list_view);
        emptyView = findViewById(R.id.empty_view);
        historyManager = HistoryManager.getInstance(this);
        
        historyListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                HistoryManager.HistoryItem item = historyItems.get(position);
                openUrl(item.getUrl());
            }
        });
        
        historyListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                HistoryManager.HistoryItem item = historyItems.get(position);
                showDeleteDialog(item, position);
                return true;
            }
        });
    }
    
    private void loadHistory() {
        historyItems = historyManager.getAllHistory();
        
        if (historyItems.isEmpty()) {
            historyListView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            historyListView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            
            adapter = new HistoryAdapter();
            historyListView.setAdapter(adapter);
        }
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
    
    private void showDeleteDialog(HistoryManager.HistoryItem item, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete History Item")
                .setMessage("Remove \"" + item.getTitle() + "\" from history?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    historyManager.removeHistoryItem(item.getId());
                    historyItems.remove(position);
                    adapter.notifyDataSetChanged();
                    
                    if (historyItems.isEmpty()) {
                        historyListView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    
                    Toast.makeText(this, "History item deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.history_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_clear_history) {
            showClearHistoryDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All History")
                .setMessage("This will remove all browsing history. This action cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    historyManager.clearHistory();
                    historyItems.clear();
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    historyListView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private class HistoryAdapter extends ArrayAdapter<HistoryManager.HistoryItem> {
        
        public HistoryAdapter() {
            super(HistoryActivity.this, R.layout.item_history, historyItems);
        }
        
        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_history, parent, false);
            }
            
            HistoryManager.HistoryItem item = historyItems.get(position);
            
            TextView titleView = view.findViewById(R.id.history_title);
            TextView urlView = view.findViewById(R.id.history_url);
            TextView timeView = view.findViewById(R.id.history_time);
            TextView visitCountView = view.findViewById(R.id.visit_count);
            
            titleView.setText(item.getTitle());
            urlView.setText(item.getUrl());
            
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault());
            timeView.setText(sdf.format(new Date(item.getTimestamp())));
            
            if (item.getVisitCount() > 1) {
                visitCountView.setText(item.getVisitCount() + " visits");
                visitCountView.setVisibility(View.VISIBLE);
            } else {
                visitCountView.setVisibility(View.GONE);
            }
            
            return view;
        }
    }
}