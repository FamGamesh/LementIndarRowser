package com.elementfinder.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    
    private EditText searchEditText;
    private Button searchButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        try {
            initializeViews();
            setupClickListeners();
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void initializeViews() {
        searchEditText = findViewById(R.id.search_edit_text);
        searchButton = findViewById(R.id.search_button);
        
        if (searchEditText == null || searchButton == null) {
            throw new RuntimeException("Failed to find required views");
        }
    }
    
    private void setupClickListeners() {
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSearch();
            }
        });
        
        // Handle enter key press in search field
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                handleSearch();
                return true;
            }
            return false;
        });
    }
    
    private void handleSearch() {
        try {
            String searchQuery = searchEditText.getText().toString().trim();
            
            if (searchQuery.isEmpty()) {
                Toast.makeText(this, "Please enter a URL or search term", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String url = processSearchQuery(searchQuery);
            
            // Add validation for URL
            if (url == null || url.isEmpty()) {
                Toast.makeText(this, "Invalid URL or search term", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
            intent.putExtra("url", url);
            
            // Add error handling for intent
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Error opening browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private String processSearchQuery(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return null;
            }
            
            query = query.trim();
            
            // Check if it's already a full URL
            if (query.startsWith("http://") || query.startsWith("https://")) {
                return query;
            } 
            // Check if it looks like a domain (contains dot and no spaces)
            else if (query.contains(".") && !query.contains(" ") && query.length() > 3) {
                return "https://" + query;
            } 
            // Otherwise treat as search query
            else {
                return "https://www.google.com/search?q=" + android.net.Uri.encode(query);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "https://www.google.com/search?q=" + query.replace(" ", "+");
        }
    }
}