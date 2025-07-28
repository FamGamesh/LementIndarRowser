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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookmarksActivity extends AppCompatActivity {
    
    private ListView bookmarksListView;
    private TextView emptyView;
    private BookmarkManager bookmarkManager;
    private List<Bookmark> bookmarks;
    private BookmarkAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        
        setupToolbar();
        initializeViews();
        loadBookmarks();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Bookmarks");
        }
    }
    
    private void initializeViews() {
        bookmarksListView = findViewById(R.id.bookmarks_list_view);
        emptyView = findViewById(R.id.empty_view);
        bookmarkManager = BookmarkManager.getInstance(this);
        
        bookmarksListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bookmark bookmark = bookmarks.get(position);
                openUrl(bookmark.getUrl());
            }
        });
        
        bookmarksListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Bookmark bookmark = bookmarks.get(position);
                showDeleteDialog(bookmark, position);
                return true;
            }
        });
    }
    
    private void loadBookmarks() {
        bookmarks = bookmarkManager.getAllBookmarks();
        
        if (bookmarks.isEmpty()) {
            bookmarksListView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            bookmarksListView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            
            adapter = new BookmarkAdapter();
            bookmarksListView.setAdapter(adapter);
        }
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
    
    private void showDeleteDialog(Bookmark bookmark, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Bookmark")
                .setMessage("Remove \"" + bookmark.getTitle() + "\" from bookmarks?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    bookmarkManager.removeBookmark(bookmark.getId());
                    bookmarks.remove(position);
                    adapter.notifyDataSetChanged();
                    
                    if (bookmarks.isEmpty()) {
                        bookmarksListView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    
                    Toast.makeText(this, "Bookmark deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bookmarks_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private class BookmarkAdapter extends ArrayAdapter<Bookmark> {
        
        public BookmarkAdapter() {
            super(BookmarksActivity.this, R.layout.item_bookmark, bookmarks);
        }
        
        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_bookmark, parent, false);
            }
            
            Bookmark bookmark = bookmarks.get(position);
            
            TextView titleView = view.findViewById(R.id.bookmark_title);
            TextView urlView = view.findViewById(R.id.bookmark_url);
            TextView timeView = view.findViewById(R.id.bookmark_time);
            
            titleView.setText(bookmark.getTitle());
            urlView.setText(bookmark.getUrl());
            
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            timeView.setText(sdf.format(new Date(bookmark.getTimestamp())));
            
            return view;
        }
    }
}