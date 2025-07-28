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
    private ImageButton zoomInButton, zoomOutButton, desktopModeButton;
    private TextView zoomLevel;
    private ProgressBar progressBar;
    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private boolean isDesktopMode = true; // Default to desktop mode for advanced browsing
    private float currentZoom = 65; // Start at 65% for desktop view
    
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
        zoomInButton = findViewById(R.id.btn_zoom_in);
        zoomOutButton = findViewById(R.id.btn_zoom_out);
        desktopModeButton = findViewById(R.id.btn_desktop_mode);
        zoomLevel = findViewById(R.id.zoom_level);
        progressBar = findViewById(R.id.progress_bar);
        
        if (webView == null || addressBar == null) {
            throw new RuntimeException("Required views not found in layout");
        }
        
        // Update zoom level display
        updateZoomLevel();
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView() {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript and advanced web features
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        // Note: setAppCacheEnabled() was deprecated and removed in API 33+
        
        // Advanced Desktop Browser Settings
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        
        // Desktop viewport configuration - Force desktop layout
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webSettings.setMinimumFontSize(8);  // Smaller fonts like desktop
        webSettings.setMinimumLogicalFontSize(8);
        webSettings.setDefaultFontSize(16);  // Desktop standard
        webSettings.setDefaultFixedFontSize(13);
        webSettings.setTextZoom(100);  // 100% zoom like desktop
        
        // Security and compatibility settings
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        
        // Media and advanced features
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(false);
        }
        
        // Enable hardware acceleration for smooth performance
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // Advanced desktop mode setup
        enableAdvancedDesktopMode();
        
        webView.setWebViewClient(new AdvancedDesktopWebViewClient());
        webView.setWebChromeClient(new AdvancedWebChromeClient());
        webView.setDownloadListener(new AdvancedDownloadListener());
        
        // Custom zoom and scroll setup
        setupCustomZoomControls();
        setupCustomScrolling();
    }
    
    private void enableAdvancedDesktopMode() {
        WebSettings webSettings = webView.getSettings();
        
        // Advanced Desktop User Agent with all desktop capabilities
        String desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        webSettings.setUserAgentString(desktopUserAgent);
        
        // Force desktop viewport width (1366px is common desktop resolution)
        String viewportMeta = "width=1366, initial-scale=0.6, maximum-scale=3.0, user-scalable=yes";
        
        // Custom CSS and JS injection for true desktop experience
        String desktopScript = 
            "javascript:(function() {" +
            "  var meta = document.createElement('meta');" +
            "  meta.name = 'viewport';" +
            "  meta.content = '" + viewportMeta + "';" +
            "  var head = document.getElementsByTagName('head')[0];" +
            "  if (head) { head.appendChild(meta); }" +
            "  " +
            "  // Override navigator properties for desktop simulation" +
            "  Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; } });" +
            "  Object.defineProperty(navigator, 'userAgent', { get: function() { return '" + desktopUserAgent + "'; } });" +
            "  Object.defineProperty(screen, 'width', { get: function() { return 1366; } });" +
            "  Object.defineProperty(screen, 'height', { get: function() { return 768; } });" +
            "  Object.defineProperty(screen, 'availWidth', { get: function() { return 1366; } });" +
            "  Object.defineProperty(screen, 'availHeight', { get: function() { return 728; } });" +
            "  " +
            "  // Remove mobile-specific CSS classes and add desktop classes" +
            "  document.documentElement.classList.remove('mobile', 'touch', 'android', 'phone');" +
            "  document.documentElement.classList.add('desktop', 'no-touch', 'windows');" +
            "  if (document.body) {" +
            "    document.body.classList.remove('mobile', 'touch', 'android', 'phone');" +
            "    document.body.classList.add('desktop', 'no-touch', 'windows');" +
            "  }" +
            "  " +
            "  // Override touch events to simulate mouse" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = '* { -webkit-touch-callout: none; -webkit-user-select: text; } " +
            "  body { cursor: default !important; } " +
            "  a, button, input, select { cursor: pointer !important; } " +
            "  ::-webkit-scrollbar { width: 12px; } " +
            "  ::-webkit-scrollbar-track { background: #f1f1f1; } " +
            "  ::-webkit-scrollbar-thumb { background: #888; border-radius: 6px; } " +
            "  ::-webkit-scrollbar-thumb:hover { background: #555; }';" +
            "  head.appendChild(style);" +
            "})()";
        
        webView.loadUrl(desktopScript);
    }
    
    private void setupCustomZoomControls() {
        // Custom zoom implementation
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        
        // Set zoom range for desktop-like experience
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.FAR); // Desktop-like zoom
        }
    }
    
    private void setupCustomScrolling() {
        // Enable smooth scrolling
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(false);
        
        // Custom scroll behavior for desktop-like experience
        webView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                // Implement smooth scrolling behavior
                if (Math.abs(scrollY - oldScrollY) > 50) {
                    // Large scroll detected, make it smoother
                    webView.smoothScrollBy(0, (scrollY - oldScrollY) / 2);
                }
            }
        });
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
    
    private void enhancePageInteraction() {
        // Advanced page interaction enhancements
        String enhancementScript = 
            "javascript:" +
            "(function() {" +
            "  'use strict';" +
            "  " +
            "  // Add desktop-style scrollbars" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = '" +
            "    ::-webkit-scrollbar { width: 12px; height: 12px; }" +
            "    ::-webkit-scrollbar-track { background: #f1f1f1; }" +
            "    ::-webkit-scrollbar-thumb { background: #c1c1c1; border-radius: 6px; }" +
            "    ::-webkit-scrollbar-thumb:hover { background: #a8a8a8; }" +
            "    ::-webkit-scrollbar-corner { background: #f1f1f1; }" +
            "    body { cursor: default !important; }" +
            "    a, button, input[type=\"button\"], input[type=\"submit\"] { cursor: pointer !important; }" +
            "    input[type=\"text\"], input[type=\"email\"], input[type=\"password\"], textarea { cursor: text !important; }" +
            "  ';" +
            "  document.head.appendChild(style);" +
            "  " +
            "  // Enhance mouse wheel scrolling" +
            "  document.addEventListener('wheel', function(e) {" +
            "    if (e.ctrlKey) {" +
            "      e.preventDefault();" +
            "      var delta = e.deltaY > 0 ? 0.9 : 1.1;" +
            "      document.body.style.zoom = (parseFloat(document.body.style.zoom) || 1) * delta;" +
            "    }" +
            "  }, { passive: false });" +
            "  " +
            "  // Add right-click context menu simulation" +
            "  document.addEventListener('contextmenu', function(e) {" +
            "    e.preventDefault();" +
            "    console.log('Desktop context menu triggered');" +
            "  });" +
            "  " +
            "  console.log('✅ Page interaction enhanced for desktop');" +
            "})();";
        
        webView.evaluateJavascript(enhancementScript, null);
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
        
        // Advanced Desktop Zoom Controls
        zoomInButton.setOnClickListener(v -> {
            currentZoom = Math.min(currentZoom + 10, 200); // Max 200%
            applyZoom();
        });
        
        zoomOutButton.setOnClickListener(v -> {
            currentZoom = Math.max(currentZoom - 10, 25); // Min 25%
            applyZoom();
        });
        
        desktopModeButton.setOnClickListener(v -> {
            toggleDesktopMode();
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
    
    private void applyZoom() {
        // Apply zoom with desktop-optimized scaling
        float zoomFactor = currentZoom / 100.0f;
        webView.setScaleX(zoomFactor);
        webView.setScaleY(zoomFactor);
        
        // Update zoom level display
        updateZoomLevel();
        
        // Inject zoom adjustment script for better rendering
        String zoomScript = 
            "javascript:(function() {" +
            "  document.body.style.zoom = '" + zoomFactor + "';" +
            "  document.documentElement.style.setProperty('--browser-zoom', '" + zoomFactor + "');" +
            "})()";
        
        webView.evaluateJavascript(zoomScript, null);
    }
    
    private void updateZoomLevel() {
        if (zoomLevel != null) {
            zoomLevel.setText(Math.round(currentZoom) + "%");
        }
    }
    
    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        
        if (isDesktopMode) {
            // Enable advanced desktop mode
            enableAdvancedDesktopMode();
            desktopModeButton.setImageResource(R.drawable.ic_desktop_browser);
            Toast.makeText(this, "🖥️ Advanced Desktop Mode Enabled", Toast.LENGTH_SHORT).show();
        } else {
            // Switch to mobile mode
            WebSettings webSettings = webView.getSettings();
            webSettings.setUserAgentString(null); // Default mobile user agent
            desktopModeButton.setImageResource(R.drawable.ic_settings);
            Toast.makeText(this, "📱 Mobile Mode Enabled", Toast.LENGTH_SHORT).show();
        }
        
        // Reload current page to apply changes
        webView.reload();
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
    
    private class AdvancedDesktopWebViewClient extends WebViewClient {
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
            
            // Inject advanced desktop scripts for true desktop experience
            injectAdvancedDesktopScript();
            
            // Force desktop layout and viewport
            String viewportScript = 
                "javascript:(function() {" +
                "  var existing = document.querySelector('meta[name=\"viewport\"]');" +
                "  if (existing) existing.remove();" +
                "  var meta = document.createElement('meta');" +
                "  meta.name = 'viewport';" +
                "  meta.content = 'width=1366, initial-scale=0.65, maximum-scale=3.0, user-scalable=yes';" +
                "  document.head.appendChild(meta);" +
                "  " +
                "  // Force desktop responsive breakpoints" +
                "  var style = document.createElement('style');" +
                "  style.innerHTML = '" +
                "    @media (max-width: 1365px) { body { min-width: 1366px !important; } }" +
                "    * { -webkit-text-size-adjust: 100% !important; }" +
                "    body { zoom: 1 !important; min-width: 1366px !important; }" +
                "  ';" +
                "  document.head.appendChild(style);" +
                "})()";
            
            view.evaluateJavascript(viewportScript, null);
            
            // Add custom zoom and scroll enhancements
            enhancePageInteraction();
            
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
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Handle desktop-style link opening
            view.loadUrl(url);
            return true;
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