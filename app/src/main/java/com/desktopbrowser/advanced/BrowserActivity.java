package com.desktopbrowser.advanced;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class BrowserActivity extends AppCompatActivity {
    
    private static final String TAG = "BrowserActivity";
    private WebView webView;
    private EditText addressBar;
    private ImageButton backButton, forwardButton, refreshButton, homeButton;
    private ProgressBar progressBar;
    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private boolean isDesktopMode = true; // Default to desktop mode for advanced browsing
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_browser);
            
            initializeManagers();
            setupToolbar();
            initializeViews();
            initializeWebView();
            setupNavigationControls();
            loadUrl();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void initializeManagers() {
        bookmarkManager = BookmarkManager.getInstance(this);
        historyManager = HistoryManager.getInstance(this);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("");
        }
    }
    
    private void initializeViews() {
        webView = findViewById(R.id.webview);
        addressBar = findViewById(R.id.address_bar);
        backButton = findViewById(R.id.btn_back);
        forwardButton = findViewById(R.id.btn_forward);
        refreshButton = findViewById(R.id.btn_refresh);
        homeButton = findViewById(R.id.btn_home);
        progressBar = findViewById(R.id.progress_bar);
        
        if (webView == null || addressBar == null) {
            throw new RuntimeException("Required views not found in layout");
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView() {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript and advanced web features
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);
        
        // Desktop browsing optimizations
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        
        // Security and compatibility settings
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        
        // Advanced desktop mode setup
        enableAdvancedDesktopMode();
        
        webView.setWebViewClient(new AdvancedWebViewClient());
        webView.setWebChromeClient(new AdvancedWebChromeClient());
        webView.setDownloadListener(new AdvancedDownloadListener());
    }
    
    private void enableAdvancedDesktopMode() {
        WebSettings webSettings = webView.getSettings();
        
        // Set realistic desktop user agent
        String desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        webSettings.setUserAgentString(desktopUserAgent);
        
        // Desktop viewport settings
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webSettings.setInitialScale(1);
        webSettings.setMinimumFontSize(1);
        webSettings.setMinimumLogicalFontSize(1);
        webSettings.setDefaultFontSize(16);
        webSettings.setDefaultFixedFontSize(13);
        
        // Advanced compatibility flags
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(false);
        }
    }
    
    private void injectAdvancedDesktopScript() {
        String script = 
            "javascript:" +
            "(function() {" +
            "  'use strict';" +
            "  " +
            "  // Override screen properties for desktop simulation" +
            "  Object.defineProperty(screen, 'width', { value: 1920, configurable: false });" +
            "  Object.defineProperty(screen, 'height', { value: 1080, configurable: false });" +
            "  Object.defineProperty(screen, 'availWidth', { value: 1920, configurable: false });" +
            "  Object.defineProperty(screen, 'availHeight', { value: 1040, configurable: false });" +
            "  Object.defineProperty(screen, 'colorDepth', { value: 24, configurable: false });" +
            "  Object.defineProperty(screen, 'pixelDepth', { value: 24, configurable: false });" +
            "  " +
            "  // Override navigator properties" +
            "  Object.defineProperty(navigator, 'platform', { value: 'Win32', configurable: false });" +
            "  Object.defineProperty(navigator, 'maxTouchPoints', { value: 0, configurable: false });" +
            "  Object.defineProperty(navigator, 'hardwareConcurrency', { value: 8, configurable: false });" +
            "  " +
            "  // Override window properties" +
            "  Object.defineProperty(window, 'outerWidth', { value: 1920, configurable: false });" +
            "  Object.defineProperty(window, 'outerHeight', { value: 1080, configurable: false });" +
            "  Object.defineProperty(window, 'innerWidth', { value: 1920, configurable: false });" +
            "  Object.defineProperty(window, 'innerHeight', { value: 969, configurable: false });" +
            "  " +
            "  // Disable webdriver detection" +
            "  Object.defineProperty(navigator, 'webdriver', { value: undefined, configurable: false });" +
            "  " +
            "  // Mock plugins for desktop browser" +
            "  Object.defineProperty(navigator, 'plugins', {" +
            "    value: [" +
            "      { name: 'Chrome PDF Plugin', length: 1 }," +
            "      { name: 'Chrome PDF Viewer', length: 1 }," +
            "      { name: 'Native Client', length: 1 }," +
            "      { name: 'Widevine Content Decryption Module', length: 1 }" +
            "    ]," +
            "    configurable: false" +
            "  });" +
            "  " +
            "  // Override CSS media queries" +
            "  if (window.matchMedia) {" +
            "    const originalMatchMedia = window.matchMedia;" +
            "    window.matchMedia = function(query) {" +
            "      if (query.includes('hover')) return { matches: true, media: query };" +
            "      if (query.includes('pointer: coarse')) return { matches: false, media: query };" +
            "      if (query.includes('pointer: fine')) return { matches: true, media: query };" +
            "      return originalMatchMedia.call(window, query);" +
            "    };" +
            "  }" +
            "  " +
            "  // Force desktop rendering" +
            "  document.documentElement.style.setProperty('--viewport-width', '1920px', 'important');" +
            "  " +
            "  console.log('✅ Advanced Desktop Browser mode activated');" +
            "})();";
        
        webView.evaluateJavascript(script, null);
    }
    
    private void setupNavigationControls() {
        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        
        forwardButton.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });
        
        refreshButton.setOnClickListener(v -> webView.reload());
        
        homeButton.setOnClickListener(v -> {
            finish(); // Return to main activity
        });
        
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            String url = addressBar.getText().toString().trim();
            if (!url.isEmpty()) {
                loadNewUrl(processUrl(url));
                return true;
            }
            return false;
        });
    }
    
    private String processUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        } else if (url.contains(".") && !url.contains(" ")) {
            return "https://" + url;
        } else {
            return "https://www.google.com/search?q=" + Uri.encode(url);
        }
    }
    
    private void loadUrl() {
        String url = getIntent().getStringExtra("url");
        if (url != null && !url.isEmpty()) {
            loadNewUrl(url);
        } else {
            Toast.makeText(this, "No URL provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void loadNewUrl(String url) {
        webView.loadUrl(url);
        addressBar.setText(url);
    }
    
    private class AdvancedWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            addressBar.setText(url);
            updateNavigationButtons();
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            updateNavigationButtons();
            
            // Inject advanced desktop scripts
            injectAdvancedDesktopScript();
            
            // Add to history
            String title = view.getTitle();
            if (title != null && !title.isEmpty()) {
                historyManager.addHistoryItem(title, url);
            }
        }
        
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, "WebView error: " + description);
            Toast.makeText(BrowserActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT).show();
        }
    }
    
    private class AdvancedWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
        }
        
        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (getSupportActionBar() != null && title != null) {
                getSupportActionBar().setTitle(title.length() > 30 ? title.substring(0, 30) + "..." : title);
            }
        }
        
        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            Log.d(TAG, "Console: " + consoleMessage.message());
            return true;
        }
    }
    
    private class AdvancedDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(BrowserActivity.this, "Cannot download: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void updateNavigationButtons() {
        backButton.setEnabled(webView.canGoBack());
        forwardButton.setEnabled(webView.canGoForward());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem bookmarkItem = menu.findItem(R.id.action_bookmark);
        if (bookmarkItem != null) {
            String currentUrl = webView.getUrl();
            if (currentUrl != null && bookmarkManager.isBookmarked(currentUrl)) {
                bookmarkItem.setIcon(R.drawable.ic_bookmark_filled);
                bookmarkItem.setTitle("Remove Bookmark");
            } else {
                bookmarkItem.setIcon(R.drawable.ic_bookmark_border);
                bookmarkItem.setTitle("Add Bookmark");
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_bookmark) {
            toggleBookmark();
            return true;
        } else if (id == R.id.action_desktop_mode) {
            toggleDesktopMode();
            return true;
        } else if (id == R.id.action_history) {
            openHistory();
            return true;
        } else if (id == R.id.action_bookmarks) {
            openBookmarks();
            return true;
        } else if (id == R.id.action_settings) {
            openSettings();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void toggleBookmark() {
        String url = webView.getUrl();
        String title = webView.getTitle();
        
        if (url != null) {
            if (bookmarkManager.isBookmarked(url)) {
                bookmarkManager.removeBookmark(url);
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
            } else {
                bookmarkManager.addBookmark(new Bookmark(title != null ? title : "Untitled", url));
                Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show();
            }
            invalidateOptionsMenu();
        }
    }
    
    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        
        WebSettings webSettings = webView.getSettings();
        if (isDesktopMode) {
            enableAdvancedDesktopMode();
            Toast.makeText(this, "🖥️ Advanced Desktop Mode Enabled", Toast.LENGTH_SHORT).show();
        } else {
            // Mobile mode
            String mobileUserAgent = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
            webSettings.setUserAgentString(mobileUserAgent);
            Toast.makeText(this, "📱 Mobile Mode Enabled", Toast.LENGTH_SHORT).show();
        }
        
        webView.reload();
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
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}