package com.elementfinder.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
        
        initializeViews();
        setupClickListeners();
    }
    
    private void initializeViews() {
        searchEditText = findViewById(R.id.search_edit_text);
        searchButton = findViewById(R.id.search_button);
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
            handleSearch();
            return true;
        });
    }
    
    private void handleSearch() {
        String searchQuery = searchEditText.getText().toString().trim();
        
        if (searchQuery.isEmpty()) {
            Toast.makeText(this, "Please enter a URL or search term", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String url = processSearchQuery(searchQuery);
        
        Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
    
    private String processSearchQuery(String query) {
        // Check if it's a URL
        if (query.startsWith("http://") || query.startsWith("https://")) {
            return query;
        } else if (query.contains(".") && !query.contains(" ")) {
            // Likely a domain without protocol
            return "https://" + query;
        } else {
            // It's a search query, use Google search
            return "https://www.google.com/search?q=" + query.replace(" ", "+");
        }
    }
}