package com.desktopbrowser.advanced;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import android.widget.GridLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {
    
    private EditText urlEditText;
    private Button browseButton;
    private GridLayout quickAccessGrid;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        try {
            initializeViews();
            setupClickListeners();
            setupQuickAccess();
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void initializeViews() {
        urlEditText = findViewById(R.id.url_edit_text);
        browseButton = findViewById(R.id.browse_button);
        quickAccessGrid = findViewById(R.id.quick_access_grid);
        
        if (urlEditText == null || browseButton == null) {
            throw new RuntimeException("Failed to find required views");
        }
    }
    
    private void setupClickListeners() {
        browseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleBrowse();
            }
        });
        
        // Handle enter key press in URL field
        urlEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                handleBrowse();
                return true;
            }
            return false;
        });
        
        // Navigation buttons
        findViewById(R.id.btn_history).setOnClickListener(v -> openHistory());
        findViewById(R.id.btn_bookmarks).setOnClickListener(v -> openBookmarks());
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
    }
    
    private void setupQuickAccess() {
        String[][] quickSites = {
            {"Google", "https://www.google.com"},
            {"Stack Overflow", "https://stackoverflow.com"},
            {"Reddit", "https://www.reddit.com"},
            {"Wikipedia", "https://www.wikipedia.org"},
            {"GitHub", "https://github.com"},
            {"YouTube", "https://www.youtube.com"}
        };
        
        for (String[] site : quickSites) {
            addQuickAccessSite(site[0], site[1]);
        }
    }
    
    private void addQuickAccessSite(String name, String url) {
        CardView card = new CardView(this);
        card.setCardElevation(8);
        card.setRadius(12);
        card.setUseCompatPadding(true);
        card.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            card.setForeground(getDrawable(android.R.drawable.list_selector_background));
        } else {
            card.setForeground(getResources().getDrawable(android.R.drawable.list_selector_background));
        }
        
        Button button = new Button(this);
        button.setText(name);
        button.setBackgroundResource(R.drawable.quick_access_background);
        button.setPadding(16, 16, 16, 16);
        
        card.addView(button);
        
        card.setOnClickListener(v -> openUrl(url));
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(8, 8, 8, 8);
        
        card.setLayoutParams(params);
        quickAccessGrid.addView(card);
    }
    
    private void handleBrowse() {
        try {
            String input = urlEditText.getText().toString().trim();
            
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter a URL or search term", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String url = processInput(input);
            openUrl(url);
            
        } catch (Exception e) {
            Toast.makeText(this, "Error opening browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private String processInput(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        } else if (input.contains(".") && !input.contains(" ") && input.length() > 3) {
            return "https://" + input;
        } else {
            return "https://www.google.com/search?q=" + android.net.Uri.encode(input);
        }
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
    
    private void openHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }
    
    private void openBookmarks() {
        Intent intent = new Intent(this, BookmarksActivity.class);
        startActivity(intent);
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}