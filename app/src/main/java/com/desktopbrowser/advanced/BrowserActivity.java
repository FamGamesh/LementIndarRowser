package com.desktopbrowser.advanced;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
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
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class BrowserActivity extends AppCompatActivity {
    
    private static final String TAG = "BrowserActivity";
    
    // Tab information class
    public static class TabInfo {
        public String url;
        public String title;
        public boolean isActive;
        
        public TabInfo(String url, String title, boolean isActive) {
            this.url = url;
            this.title = title;
            this.isActive = isActive;
        }
    }
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
    
        // Tab management
        private SessionManager sessionManager;
        private AdManager adManager;
        private LinearLayout tabsContainer;
        private LinearLayout zoomControlsContainer;
        private Button showUrlStackButton;
        private Button newTabButton; // Chrome-like new tab button
        private android.widget.SeekBar zoomSlider;
        private java.util.List<String> urlStack;
        private long lastInterstitialTime = 0;
        
        // Tab management - Enhanced Chrome-like functionality
        private int tabCount = 1;
        private View tabCounterView;
        private TextView tabCountText;
        private java.util.List<TabInfo> tabList;
        
        // Prevent multiple operations
        private boolean isRefreshing = false;
        private long lastRefreshTime = 0;
        private static final long REFRESH_DEBOUNCE = 2000; // 2 seconds debounce
        
        private boolean isNavigating = false;
        private long lastNavigationTime = 0;
        private static final long NAVIGATION_DEBOUNCE = 1000; // 1 second debounce
        
        // Lifecycle management
        private boolean isPaused = false;
        private boolean isDestroyed = false;
        
        // Zoom crash prevention
        private boolean isZooming = false;
        private long lastZoomTime = 0;
        private static final long ZOOM_DEBOUNCE = 300; // 300ms debounce for zoom
        private Handler zoomHandler;
        private Runnable pendingZoomRunnable;
    
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
        sessionManager = SessionManager.getInstance(this);
        adManager = AdManager.getInstance(this);
        urlStack = new java.util.ArrayList<>();
        tabList = new java.util.ArrayList<>();
        zoomHandler = new Handler(Looper.getMainLooper());
        
        // Initialize with first tab
        String initialUrl = getIntent().getStringExtra("url");
        if (initialUrl != null) {
            tabList.add(new TabInfo(initialUrl, "New Tab", true));
        }
        
        // Initialize tab container rendering
        android.os.Handler initHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        initHandler.postDelayed(() -> {
            renderTabsInContainer();
        }, 500);
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
        
        // Enhanced tab management features
        tabsContainer = findViewById(R.id.tabs_container);
        zoomControlsContainer = findViewById(R.id.zoom_controls_container);
        showUrlStackButton = findViewById(R.id.btn_show_url_stack);
        newTabButton = findViewById(R.id.btn_new_tab); // Chrome-like new tab button
        zoomSlider = findViewById(R.id.zoom_slider);
        
        // Tab counter setup
        tabCounterView = findViewById(R.id.tab_counter);
        tabCountText = tabCounterView.findViewById(R.id.tab_count_text);
        
        if (webView == null || addressBar == null) {
            throw new RuntimeException("Required views not found in layout");
        }
        
        // Update zoom level display
        updateZoomLevel();
        
        // Setup zoom slider
        setupZoomSlider();
        
        // Check if restoring session
        checkSessionRestore();
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
        
        // CRITICAL: Set initial scale to make content appear desktop-sized
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setDefaultZoom(WebSettings.ZoomDensity.FAR); // Desktop-like zoom
        }
        
        // Force desktop viewport dimensions
        webView.setInitialScale(50); // 50% initial scale to show full desktop layout
        
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
        webView.setDownloadListener(new IntelligentDownloadListener());
        
        // INTELLIGENT LONG PRESS CONTEXT MENU
        setupIntelligentLongPressMenu();
        
        // Custom zoom and scroll setup
        setupCustomZoomControls();
        setupCustomScrolling();
    }
    
    private void enableAdvancedDesktopMode() {
        WebSettings webSettings = webView.getSettings();
        
        // Advanced Desktop User Agent - completely undetectable
        String desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        webSettings.setUserAgentString(desktopUserAgent);
        
        Log.d(TAG, "🖥️ Advanced Desktop Mode: User Agent set to undetectable Chrome desktop");
        
        // Enhanced viewport configuration
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false); // Hide default zoom controls
        
        // CRITICAL Desktop Simulation Settings
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // Force far zoom density (desktop style)
            webSettings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
            
            // Enable hardware acceleration for smooth desktop-like scrolling
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        
        // Enable file downloads with proper headers
        webView.setDownloadListener(new IntelligentDownloadListener());
    }
    
    // INTELLIGENT LONG PRESS CONTEXT MENU SETUP
    private void setupIntelligentLongPressMenu() {
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult hitTestResult = webView.getHitTestResult();
                
                if (hitTestResult != null) {
                    Log.d(TAG, "🎯 Long press detected - Type: " + hitTestResult.getType());
                    
                    // Delay context menu to ensure proper hit test result
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        showIntelligentContextMenu(hitTestResult);
                    }, 100);
                    
                    return true; // Consume the event
                }
                
                return false;
            }
        });
    }
    
    private void showIntelligentContextMenu(WebView.HitTestResult hitTestResult) {
        int type = hitTestResult.getType();
        String extra = hitTestResult.getExtra();
        
        Log.d(TAG, "📋 Showing context menu - Type: " + type + ", Extra: " + extra);
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Page Options");
        
        java.util.List<String> options = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        
        // Download option - intelligent file detection
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
            type == WebView.HitTestResult.IMAGE_TYPE ||
            (extra != null && (extra.contains("http") || extra.contains("."))) ) {
            
            options.add("🔽 Download (Intelligent)");
            actions.add(() -> intelligentDownload(extra != null ? extra : webView.getUrl()));
        }
        
        // Print whole page
        options.add("🖨️ Print (Whole Page)");
        actions.add(() -> printPage());
        
        // Copy link options
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || 
            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            
            options.add("📋 Copy Link");
            actions.add(() -> copyToClipboard(extra, "Link copied to clipboard"));
            
            options.add("📤 Share Link");
            actions.add(() -> shareLink(extra, "Link"));
            
            options.add("📝 Copy Link Text");
            actions.add(() -> copyLinkText(extra));
        }
        
        // Current page options
        if (type == WebView.HitTestResult.UNKNOWN_TYPE || options.isEmpty()) {
            options.add("📋 Copy Page URL");
            actions.add(() -> copyToClipboard(webView.getUrl(), "Page URL copied"));
            
            options.add("📤 Share Page");
            actions.add(() -> shareLink(webView.getUrl(), "Page"));
        }
        
        String[] optionsArray = options.toArray(new String[0]);
        
        builder.setItems(optionsArray, (dialog, which) -> {
            try {
                actions.get(which).run();
            } catch (Exception e) {
                Log.e(TAG, "Error executing context menu action", e);
                Toast.makeText(BrowserActivity.this, "Action failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void intelligentDownload(String url) {
        Log.d(TAG, "🎯 Intelligent download requested for: " + url);
        
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "No downloadable content detected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Extract intelligent filename
        String filename = getIntelligentFileName(url, "download");
        
        Log.d(TAG, "📁 Intelligent filename: " + filename);
        
        // Start download
        downloadFile(url, filename);
    }
    
    private void copyToClipboard(String text, String message) {
        if (text != null) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Browser", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void shareLink(String url, String type) {
        if (url != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, type + " from Real Desktop Browser");
            startActivity(Intent.createChooser(shareIntent, "Share " + type));
        }
    }
    
    private void copyLinkText(String url) {
        // Extract readable text from URL
        if (url != null) {
            String linkText = url.replaceAll("https?://", "").replaceAll("/", " > ");
            copyToClipboard(linkText, "Link text copied");
        }
    }
    
    private String getIntelligentFileName(String url, String defaultName) {
        if (url == null) return defaultName;
        
        try {
            // Extract filename from URL
            String filename = url.substring(url.lastIndexOf('/') + 1);
            
            // Remove query parameters
            if (filename.contains("?")) {
                filename = filename.substring(0, filename.indexOf("?"));
            }
            
            // If no extension, try to determine from URL context
            if (!filename.contains(".")) {
                if (url.contains("pdf")) filename += ".pdf";
                else if (url.contains("jpg") || url.contains("jpeg")) filename += ".jpg";
                else if (url.contains("png")) filename += ".png";
                else if (url.contains("gif")) filename += ".gif";
                else if (url.contains("mp4")) filename += ".mp4";
                else if (url.contains("zip")) filename += ".zip";
                else filename += ".html";
            }
            
            return filename.isEmpty() ? defaultName : filename;
            
        } catch (Exception e) {
            Log.w(TAG, "Error extracting filename", e);
            return defaultName;
        }
    }
    
    private void printPage() {
        Log.d(TAG, "🖨️ Print page requested");
        
        // Android's built-in print functionality
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            android.print.PrintManager printManager = (android.print.PrintManager) getSystemService(Context.PRINT_SERVICE);
            
            String jobName = "Real Desktop Browser - " + (webView.getTitle() != null ? webView.getTitle() : "Page");
            android.print.PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
            
            printManager.print(jobName, printAdapter, new android.print.PrintAttributes.Builder().build());
        } else {
            Toast.makeText(this, "Print requires Android 4.4 or higher", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void downloadFile(String url, String filename) {
        try {
            Log.d(TAG, "📥 Starting download - URL: " + url + ", Filename: " + filename);
            
            android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
            request.setDescription("Downloaded by Real Desktop Browser");
            request.setTitle(filename);
            
            // Set headers to mimic desktop browser
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
            
            // Set download location
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            
            // Allow download over mobile and WiFi
            request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI | android.app.DownloadManager.Request.NETWORK_MOBILE);
            
            // Show in downloads UI
            request.setVisibleInDownloadsUi(true);
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            long downloadId = downloadManager.enqueue(request);
            
            // Track download in our system - Fixed method call with String downloadId
            com.desktopbrowser.advanced.DownloadManager.getInstance(this).addDownload(filename, url, String.valueOf(downloadId));
            
            Toast.makeText(this, "📥 Download started: " + filename, Toast.LENGTH_SHORT).show();
            
            Log.d(TAG, "✅ Download started - ID: " + downloadId + ", File: " + filename);
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error starting download", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
                    webView.scrollBy(0, (scrollY - oldScrollY) / 2);
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
            "  // === COMPREHENSIVE DESKTOP SIMULATION ===" +
            "  " +
            "  // Override screen properties with exact desktop values" +
            "  Object.defineProperty(screen, 'width', { value: 1920, writable: false, configurable: false });" +
            "  Object.defineProperty(screen, 'height', { value: 1080, writable: false, configurable: false });" +
            "  Object.defineProperty(screen, 'availWidth', { value: 1920, writable: false, configurable: false });" +
            "  Object.defineProperty(screen, 'availHeight', { value: 1040, writable: false, configurable: false });" +
            "  Object.defineProperty(screen, 'colorDepth', { value: 24, writable: false, configurable: false });" +
            "  Object.defineProperty(screen, 'pixelDepth', { value: 24, writable: false, configurable: false });" +
            "  " +
            "  // === NAVIGATOR PROPERTIES OVERRIDE ===" +
            "  Object.defineProperty(navigator, 'platform', { value: 'Win32', writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'oscpu', { value: 'Windows NT 10.0; Win64; x64', writable: false, configurable: false });" +
            "  " +
            "  // === CRITICAL TOUCH ELIMINATION ===" +
            "  Object.defineProperty(navigator, 'maxTouchPoints', { value: 0, writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'msMaxTouchPoints', { value: 0, writable: false, configurable: false });" +
            "  " +
            "  // Remove ALL mobile-specific properties" +
            "  Object.defineProperty(navigator, 'standalone', { value: undefined, writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'vibrate', { value: undefined, writable: false, configurable: false });" +
            "  " +
            "  // === WINDOW PROPERTIES ===" +
            "  Object.defineProperty(window, 'outerWidth', { value: 1920, writable: false, configurable: false });" +
            "  Object.defineProperty(window, 'outerHeight', { value: 1080, writable: false, configurable: false });" +
            "  Object.defineProperty(window, 'innerWidth', { value: 1366, writable: false, configurable: false });" +
            "  Object.defineProperty(window, 'innerHeight', { value: 969, writable: false, configurable: false });" +
            "  Object.defineProperty(window, 'devicePixelRatio', { value: 1, writable: false, configurable: false });" +
            "  " +
            "  // === ORIENTATION OVERRIDE - FORCE LANDSCAPE ===" +
            "  if (screen.orientation) {" +
            "    Object.defineProperty(screen.orientation, 'type', { value: 'landscape-primary', writable: false });" +
            "    Object.defineProperty(screen.orientation, 'angle', { value: 0, writable: false });" +
            "  }" +
            "  Object.defineProperty(window, 'orientation', { value: undefined, writable: false });" +
            "  " +
            "  // === DISABLE WEBDRIVER DETECTION ===" +
            "  Object.defineProperty(navigator, 'webdriver', { value: undefined, writable: false, configurable: false });" +
            "  Object.defineProperty(window, 'chrome', { " +
            "    value: { runtime: {}, loadTimes: function(){}, csi: function(){} }, " +
            "    writable: false, configurable: false " +
            "  });" +
            "  " +
            "  // === MOCK DESKTOP PLUGINS ===" +
            "  Object.defineProperty(navigator, 'plugins', {" +
            "    value: Object.freeze([" +
            "      Object.freeze({ name: 'Chrome PDF Plugin', length: 1, 0: { type: 'application/pdf' } })," +
            "      Object.freeze({ name: 'Chrome PDF Viewer', length: 1, 0: { type: 'application/pdf' } })," +
            "      Object.freeze({ name: 'Native Client', length: 1, 0: { type: 'application/x-nacl' } })," +
            "      Object.freeze({ name: 'Widevine Content Decryption Module', length: 1, 0: { type: 'application/x-ppapi-widevine-cdm' } })" +
            "    ])," +
            "    writable: false, configurable: false" +
            "  });" +
            "  " +
            "  // === OVERRIDE CSS MEDIA QUERIES COMPLETELY ===" +
            "  const originalMatchMedia = window.matchMedia;" +
            "  window.matchMedia = function(query) {" +
            "    const lowerQuery = query.toLowerCase();" +
            "    " +
            "    // Force desktop-style hover support" +
            "    if (lowerQuery.includes('hover') && lowerQuery.includes('hover')) return { matches: true, media: query, addListener: function(){}, removeListener: function(){} };" +
            "    " +
            "    // Force fine pointer (mouse)" +
            "    if (lowerQuery.includes('pointer') && lowerQuery.includes('coarse')) return { matches: false, media: query, addListener: function(){}, removeListener: function(){} };" +
            "    if (lowerQuery.includes('pointer') && lowerQuery.includes('fine')) return { matches: true, media: query, addListener: function(){}, removeListener: function(){} };" +
            "    " +
            "    // Block touch-related queries" +
            "    if (lowerQuery.includes('touch')) return { matches: false, media: query, addListener: function(){}, removeListener: function(){} };" +
            "    " +
            "    // Force desktop width" +
            "    if (lowerQuery.includes('max-width') && lowerQuery.includes('768')) return { matches: false, media: query, addListener: function(){}, removeListener: function(){} };" +
            "    if (lowerQuery.includes('min-width') && lowerQuery.includes('1024')) return { matches: true, media: query, addListener: function(){}, removeListener: function(){} };" +
            "    " +
            "    return originalMatchMedia.call(window, query);" +
            "  };" +
            "  " +
            "  // === BATTERY API REMOVAL (MOBILE INDICATOR) ===" +
            "  if (navigator.getBattery) {" +
            "    Object.defineProperty(navigator, 'getBattery', { value: undefined, writable: false });" +
            "  }" +
            "  if (navigator.battery) {" +
            "    Object.defineProperty(navigator, 'battery', { value: undefined, writable: false });" +
            "  }" +
            "  " +
            "  // === GEOLOCATION OVERRIDE ===" +
            "  if (navigator.geolocation) {" +
            "    const originalGetCurrentPosition = navigator.geolocation.getCurrentPosition;" +
            "    navigator.geolocation.getCurrentPosition = function(success, error, options) {" +
            "      // Simulate desktop-style geolocation request" +
            "      if (error) error({ code: 1, message: 'User denied geolocation' });" +
            "    };" +
            "  }" +
            "  " +
            "  // === WEBGL FINGERPRINT OVERRIDE ===" +
            "  const getParameter = WebGLRenderingContext.prototype.getParameter;" +
            "  WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
            "    // Override GPU info to match desktop" +
            "    if (parameter === 37445) return 'Intel Inc.';" +
            "    if (parameter === 37446) return 'Intel(R) HD Graphics 620';" +
            "    return getParameter.call(this, parameter);" +
            "  };" +
            "  " +
            "  // === CANVAS FINGERPRINT PROTECTION ===" +
            "  const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;" +
            "  HTMLCanvasElement.prototype.toDataURL = function() {" +
            "    // Add slight noise to prevent canvas fingerprinting" +
            "    const ctx = this.getContext('2d');" +
            "    if (ctx) {" +
            "      const imgData = ctx.getImageData(0, 0, this.width, this.height);" +
            "      for (let i = 0; i < imgData.data.length; i += 4) {" +
            "        imgData.data[i] += Math.floor(Math.random() * 3) - 1;" +
            "        imgData.data[i + 1] += Math.floor(Math.random() * 3) - 1;" +
            "        imgData.data[i + 2] += Math.floor(Math.random() * 3) - 1;" +
            "      }" +
            "      ctx.putImageData(imgData, 0, 0);" +
            "    }" +
            "    return originalToDataURL.call(this);" +
            "  };" +
            "  " +
            "  // === TIMEZONE OVERRIDE ===" +
            "  const originalDateTimeFormat = Intl.DateTimeFormat;" +
            "  Intl.DateTimeFormat = function(...args) {" +
            "    args[1] = args[1] || {};" +
            "    args[1].timeZone = args[1].timeZone || 'America/New_York';" +
            "    return new originalDateTimeFormat(...args);" +
            "  };" +
            "  " +
            "  console.log('🛡️ Advanced Anti-Detection Desktop Mode Activated');" +
            "  console.log('📊 Touchscreen: FALSE | Platform: Win32 | Hover: TRUE');" +
            "})();";
        
        webView.evaluateJavascript(script, null);
    }
    
    private void injectImmediateStealthScript() {
        try {
            String immediateScript = 
                "javascript:" +
                "(function() {" +
                "  'use strict';" +
                "  " +
                "  // Critical overrides for instant stealth" +
                "  Object.defineProperty(screen, 'width', { value: 1920, writable: false, configurable: false });" +
                "  Object.defineProperty(screen, 'height', { value: 1080, writable: false, configurable: false });" +
                "  Object.defineProperty(screen, 'availWidth', { value: 1920, writable: false, configurable: false });" +
                "  Object.defineProperty(screen, 'availHeight', { value: 1040, writable: false, configurable: false });" +
                "  Object.defineProperty(screen, 'colorDepth', { value: 24, writable: false, configurable: false });" +
                "  Object.defineProperty(screen, 'pixelDepth', { value: 24, writable: false, configurable: false });" +
                "  " +
                "  // CRITICAL: Override orientation IMMEDIATELY" +
                "  Object.defineProperty(screen, 'orientation', { " +
                "    value: { type: 'landscape-primary', angle: 0 }, " +
                "    writable: false, configurable: false " +
                "  });" +
                "  Object.defineProperty(window, 'orientation', { value: 90, writable: false, configurable: false });" +
                "  " +
                "  // Override window dimensions INSTANTLY" +
                "  Object.defineProperty(window, 'outerWidth', { value: 1920, writable: false, configurable: false });" +
                "  Object.defineProperty(window, 'outerHeight', { value: 1080, writable: false, configurable: false });" +
                "  Object.defineProperty(window, 'innerWidth', { value: 1920, writable: false, configurable: false });" +
                "  Object.defineProperty(window, 'innerHeight', { value: 969, writable: false, configurable: false });" +
                "  Object.defineProperty(window, 'screen', { " +
                "    value: { " +
                "      width: 1920, height: 1080, availWidth: 1920, availHeight: 1040, " +
                "      colorDepth: 24, pixelDepth: 24, " +
                "      orientation: { type: 'landscape-primary', angle: 0 } " +
                "    }, " +
                "    writable: false, configurable: false " +
                "  });" +
                "  " +
                "  // CRITICAL: Device pixel ratio override" +
                "  Object.defineProperty(window, 'devicePixelRatio', { value: 1, writable: false, configurable: false });" +
                "  " +
                "  // INSTANT touch detection elimination" +
                "  Object.defineProperty(navigator, 'maxTouchPoints', { value: 0, writable: false, configurable: false });" +
                "  Object.defineProperty(navigator, 'msMaxTouchPoints', { value: 0, writable: false, configurable: false });" +
                "  " +
                "  console.log('⚡ IMMEDIATE stealth injection completed');" +
                "})();";

            webView.evaluateJavascript(immediateScript, null);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting stealth script", e);
        }
    }
    
    private void enhancePageInteraction() {
        // Advanced page interaction enhancements with anti-detection
        String enhancementScript = 
            "javascript:" +
            "(function() {" +
            "  'use strict';" +
            "  " +
            "  // === FINAL ANTI-DETECTION LAYER ===" +
            "  " +
            "  // Block touch event registration completely" +
            "  const originalAddEventListener = EventTarget.prototype.addEventListener;" +
            "  EventTarget.prototype.addEventListener = function(type, listener, options) {" +
            "    if (type.toLowerCase().includes('touch')) {" +
            "      console.log('🚫 Blocked touch event registration:', type);" +
            "      return; // Block touch event listeners" +
            "    }" +
            "    return originalAddEventListener.call(this, type, listener, options);" +
            "  };" +
            "  " +
            "  // Override hasFeature for touch detection" +
            "  if (document.implementation && document.implementation.hasFeature) {" +
            "    const originalHasFeature = document.implementation.hasFeature;" +
            "    document.implementation.hasFeature = function(feature, version) {" +
            "      if (feature.toLowerCase().includes('touch')) return false;" +
            "      return originalHasFeature.call(this, feature, version);" +
            "    };" +
            "  }" +
            "  " +
            "  // Override 'in' operator for touch detection" +
            "  Object.defineProperty(window, 'Touch', { value: undefined, configurable: false });" +
            "  Object.defineProperty(window, 'TouchEvent', { value: undefined, configurable: false });" +
            "  Object.defineProperty(window, 'TouchList', { value: undefined, configurable: false });" +
            "  " +
            "  // Add desktop-style interaction enhancements" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = '" +
            "    ::-webkit-scrollbar { width: 12px; height: 12px; }" +
            "    ::-webkit-scrollbar-track { background: #f1f1f1; border-radius: 6px; }" +
            "    ::-webkit-scrollbar-thumb { background: #c1c1c1; border-radius: 6px; }" +
            "    ::-webkit-scrollbar-thumb:hover { background: #a8a8a8; }" +
            "    ::-webkit-scrollbar-corner { background: #f1f1f1; }" +
            "    * { -webkit-tap-highlight-color: transparent !important; }" +
            "    body { cursor: default !important; -webkit-user-select: text !important; }" +
            "    a, button, input[type=\"button\"], input[type=\"submit\"], [onclick] { cursor: pointer !important; }" +
            "    input[type=\"text\"], input[type=\"email\"], input[type=\"password\"], textarea { cursor: text !important; }" +
            "    html, body { -ms-touch-action: none !important; touch-action: none !important; }" +
            "  ';" +
            "  document.head.appendChild(style);" +
            "  " +
            "  // Enhanced mouse wheel scrolling with zoom control" +
            "  document.addEventListener('wheel', function(e) {" +
            "    if (e.ctrlKey) {" +
            "      e.preventDefault();" +
            "      var delta = e.deltaY > 0 ? 0.9 : 1.1;" +
            "      var currentZoom = parseFloat(document.body.style.zoom) || 1;" +
            "      var newZoom = Math.min(Math.max(currentZoom * delta, 0.25), 3.0);" +
            "      document.body.style.zoom = newZoom;" +
            "      console.log('🔍 Zoom level:', Math.round(newZoom * 100) + '%');" +
            "    }" +
            "  }, { passive: false });" +
            "  " +
            "  // Comprehensive form interaction override" +
            "  document.addEventListener('focus', function(e) {" +
            "    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {" +
            "      e.target.style.outline = '2px solid #4285f4';" +
            "    }" +
            "  }, true);" +
            "  " +
            "  document.addEventListener('blur', function(e) {" +
            "    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {" +
            "      e.target.style.outline = 'none';" +
            "    }" +
            "  }, true);" +
            "  " +
            "  // Final stealth confirmation" +
            "  setTimeout(function() {" +
            "    console.log('🛡️ STEALTH STATUS:');" +
            "    console.log('   Touchscreen: ' + (navigator.maxTouchPoints === 0 ? 'DISABLED ✅' : 'DETECTED ❌'));" +
            "    console.log('   Platform: ' + navigator.platform + ' ✅');" +
            "    console.log('   Hover Support: ' + (window.matchMedia('(hover: hover)').matches ? 'ENABLED ✅' : 'DISABLED ❌'));" +
            "    console.log('   User Agent: Desktop Chrome ✅');" +
            "  }, 1000);" +
            "  " +
            "  console.log('🎯 Advanced Desktop Interaction Layer Activated');" +
            "})();";
        
        webView.evaluateJavascript(enhancementScript, null);
    }
    
    private void setupNavigationControls() {
        backButton.setOnClickListener(v -> {
            // Add debouncing for navigation
            long currentTime = System.currentTimeMillis();
            if (isNavigating || (currentTime - lastNavigationTime) < NAVIGATION_DEBOUNCE) {
                return;
            }
            
            if (webView.canGoBack()) {
                lastNavigationTime = currentTime;
                isNavigating = true;
                webView.goBack();
            }
        });
        
        forwardButton.setOnClickListener(v -> {
            // Add debouncing for navigation
            long currentTime = System.currentTimeMillis();
            if (isNavigating || (currentTime - lastNavigationTime) < NAVIGATION_DEBOUNCE) {
                return;
            }
            
            if (webView.canGoForward()) {
                lastNavigationTime = currentTime;
                isNavigating = true;
                webView.goForward();
            }
        });
        
        refreshButton.setOnClickListener(v -> {
            // Add debouncing to prevent multiple concurrent refreshes
            long currentTime = System.currentTimeMillis();
            if (isRefreshing || (currentTime - lastRefreshTime) < REFRESH_DEBOUNCE) {
                Log.d(TAG, "Refresh already in progress or too recent, ignoring");
                return;
            }
            
            lastRefreshTime = currentTime;
            isRefreshing = true;
            
            try {
                webView.reload();
                Toast.makeText(BrowserActivity.this, "Refreshing page...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing page", e);
                isRefreshing = false;
            }
        });
        
        homeButton.setOnClickListener(v -> {
            // Save current session as "recent session" before going home
            saveCurrentSessionAsRecent();
            
            // Return to main activity with flag
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("returning_from_browser", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
        
        // Advanced Desktop Zoom Controls with crash prevention
        zoomInButton.setOnClickListener(v -> {
            safeDebouncedZoom(() -> {
                currentZoom = Math.min(currentZoom + 10, 200); // Max 200%
                applySafeZoom();
                showZoomControls();
            });
        });
        
        zoomOutButton.setOnClickListener(v -> {
            safeDebouncedZoom(() -> {
                currentZoom = Math.max(currentZoom - 10, 25); // Min 25%
                applySafeZoom();
                showZoomControls();
            });
        });
        
        // Show URL Stack button
        if (showUrlStackButton != null) {
            showUrlStackButton.setOnClickListener(v -> showUrlStackDialog());
        }
        
        // Tab counter click to show tab switcher  
        if (tabCounterView != null) {
            tabCounterView.setOnClickListener(v -> showTabSwitcher());
        }
        
        // Chrome-like new tab button
        if (newTabButton != null) {
            newTabButton.setOnClickListener(v -> createNewTab());
        }
        
        // Update tab counter display
        updateTabCounter();
        
        desktopModeButton.setOnClickListener(v -> {
            toggleDesktopMode();
        });
        
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            String url = addressBar.getText().toString().trim();
            if (!url.isEmpty()) {
                loadNewUrl(processUrl(url));
                addressBar.clearFocus(); // Hide keyboard and collapse address bar
                return true;
            }
            return false;
        });
        
        // Address bar focus handling for expansion
        addressBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                expandAddressBar();
            } else {
                collapseAddressBar();
            }
        });
    }
    
    // Safe debounced zoom to prevent crashes
    private void safeDebouncedZoom(Runnable zoomAction) {
        long currentTime = System.currentTimeMillis();
        
        // Prevent multiple concurrent zoom operations
        if (isZooming || (currentTime - lastZoomTime) < ZOOM_DEBOUNCE) {
            Log.d(TAG, "Zoom operation debounced - preventing crash");
            return;
        }
        
        // Cancel any pending zoom operations
        if (pendingZoomRunnable != null) {
            zoomHandler.removeCallbacks(pendingZoomRunnable);
        }
        
        lastZoomTime = currentTime;
        isZooming = true;
        
        // Execute zoom operation safely
        pendingZoomRunnable = () -> {
            try {
                if (!isDestroyed && !isPaused && webView != null) {
                    zoomAction.run();
                    Log.d(TAG, "Safe zoom operation completed");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during safe zoom operation", e);
            } finally {
                isZooming = false;
            }
        };
        
        // Delay execution slightly to prevent UI thread blocking
        zoomHandler.postDelayed(pendingZoomRunnable, 50);
    }
    
    private void applySafeZoom() {
        try {
            if (isDestroyed || isPaused || webView == null) {
                Log.w(TAG, "Skipping zoom - activity not ready");
                return;
            }
            
            // Apply zoom with crash prevention
            float zoomFactor = currentZoom / 100.0f;
            
            // Validate zoom factor to prevent extreme values
            if (zoomFactor < 0.25f) zoomFactor = 0.25f;
            if (zoomFactor > 2.0f) zoomFactor = 2.0f;
            
            // Apply zoom safely on main thread
            runOnUiThread(() -> {
                try {
                    if (webView != null && !isDestroyed) {
                        webView.setScaleX(zoomFactor);
                        webView.setScaleY(zoomFactor);
                        
                        // Update zoom level display
                        updateZoomLevel();
                        
                        // Update zoom slider safely
                        if (zoomSlider != null) {
                            zoomSlider.setProgress((int) currentZoom);
                        }
                        
                        // Show zoom controls when zooming
                        showZoomControls();
                        hideZoomControlsDelayed();
                        
                        Log.d(TAG, "Safe zoom applied: " + zoomFactor);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error applying safe zoom", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in applySafeZoom", e);
        }
    }
    
    // Legacy method - now redirects to safe version
    private void applyZoom() {
        applySafeZoom();
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
            // Check if we need to restore a session instead
            boolean restoreSession = getIntent().getBooleanExtra("restore_session", false);
            if (!restoreSession) {
                Toast.makeText(this, "No URL provided", Toast.LENGTH_SHORT).show();
                finish();
            }
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
            
            // CRITICAL: Inject stealth code IMMEDIATELY when page starts loading
            injectImmediateStealthScript();
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            updateNavigationButtons();
            
            // Reset refresh flag
            isRefreshing = false;
            isNavigating = false;
            
            // Update current tab info
            String title = view.getTitle();
            updateCurrentTabInfo(url, title);
            
            // Update tab container display
            renderTabsInContainer();
            
            // Add URL to stack for session history
            if (url != null && !urlStack.contains(url)) {
                urlStack.add(url);
                // Keep only last 20 URLs to avoid memory issues
                if (urlStack.size() > 20) {
                    urlStack.remove(0);
                }
            }
            
            // Inject additional desktop scripts after page loads
            injectAdvancedDesktopScript();
            
            // Force desktop layout and viewport
            String viewportScript = 
                "javascript:(function() {" +
                "  var existing = document.querySelector('meta[name=\"viewport\"]');" +
                "  if (existing) existing.remove();" +
                "  var meta = document.createElement('meta');" +
                "  meta.name = 'viewport';" +
                "  meta.content = 'width=1920, initial-scale=0.5, maximum-scale=3.0, user-scalable=yes';" +
                "  document.head.appendChild(meta);" +
                "})()";
            
            view.evaluateJavascript(viewportScript, null);
            
            // Add custom zoom and scroll enhancements
            enhancePageInteraction();
            
            // Add to history
            if (title != null && !title.isEmpty()) {
                historyManager.addHistoryItem(title, url);
            }
        }
        
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            progressBar.setVisibility(View.GONE);
            
            // Reset refresh flag on error
            isRefreshing = false;
            isNavigating = false;
            
            Log.e(TAG, "WebView error: " + description);
            Toast.makeText(BrowserActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Inject stealth immediately for new URLs
            injectImmediateStealthScript();
            view.loadUrl(url);
            return true;
        }
        
        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            // Inject stealth for every resource load to catch early detection scripts
            injectImmediateStealthScript();
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
    
    private class IntelligentDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            try {
                Log.d(TAG, "🎯 Intelligent download triggered for: " + url);
                
                // Handle different URI schemes
                if (url.startsWith("data:")) {
                    handleDataUriDownload(url, mimetype);
                    return;
                } else if (url.startsWith("blob:")) {
                    handleBlobDownload(url);
                    return;
                } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    // Handle other schemes like ftp:, file:, etc.
                    handleNonHttpDownload(url);
                    return;
                }
                
                // Extract filename intelligently for HTTP/HTTPS
                String filename = getIntelligentFileName(url, "file");
                
                // If we can get better filename from contentDisposition, use it
                if (contentDisposition != null && contentDisposition.contains("filename=")) {
                    try {
                        String extractedName = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9);
                        if (extractedName.startsWith("\"")) {
                            extractedName = extractedName.substring(1, extractedName.indexOf("\"", 1));
                        }
                        if (!extractedName.isEmpty()) {
                            filename = extractedName;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not extract filename from contentDisposition", e);
                    }
                }
                
                Log.d(TAG, "📁 Final filename: " + filename);
                
                // Start download with intelligent tracking
                downloadFile(url, filename);
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error in intelligent download listener", e);
                Toast.makeText(BrowserActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        
        private void handleDataUriDownload(String dataUri, String mimetype) {
            try {
                Log.d(TAG, "🔗 Handling data URI download");
                
                // Parse data URI: data:[<mediatype>][;base64],<data>
                if (!dataUri.startsWith("data:")) {
                    throw new IllegalArgumentException("Invalid data URI");
                }
                
                String[] parts = dataUri.substring(5).split(",", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid data URI format");
                }
                
                String header = parts[0];
                String data = parts[1];
                
                // Determine if it's base64 encoded
                boolean isBase64 = header.contains("base64");
                
                // Extract MIME type
                String actualMimeType = mimetype;
                if (header.contains(";")) {
                    actualMimeType = header.split(";")[0];
                } else if (!header.isEmpty()) {
                    actualMimeType = header;
                }
                
                // Generate appropriate filename
                String extension = getExtensionFromMimeType(actualMimeType);
                String filename = "download_" + System.currentTimeMillis() + "." + extension;
                
                // Decode data
                byte[] fileData;
                if (isBase64) {
                    fileData = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                } else {
                    fileData = data.getBytes("UTF-8");
                }
                
                // Save file
                saveDataToFile(filename, fileData);
                
                Toast.makeText(BrowserActivity.this, "Data file downloaded: " + filename, Toast.LENGTH_LONG).show();
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error handling data URI download", e);
                Toast.makeText(BrowserActivity.this, "Failed to download data file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        
        private void handleBlobDownload(String blobUrl) {
            try {
                Log.d(TAG, "🌐 Handling blob URL download");
                
                // For blob URLs, we need to extract the data using JavaScript
                String script = 
                    "(function() {" +
                    "  fetch('" + blobUrl + "')" +
                    "    .then(response => response.blob())" +
                    "    .then(blob => {" +
                    "      const reader = new FileReader();" +
                    "      reader.onload = function() {" +
                    "        Android.onBlobData(reader.result);" +
                    "      };" +
                    "      reader.readAsDataURL(blob);" +
                    "    })" +
                    "    .catch(error => Android.onBlobError(error.toString()));" +
                    "})();";
                
                // Add JavaScript interface to handle blob data
                webView.addJavascriptInterface(new BlobDownloadInterface(), "Android");
                webView.evaluateJavascript(script, null);
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error handling blob download", e);
                Toast.makeText(BrowserActivity.this, "Failed to download blob: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        
        private void handleNonHttpDownload(String url) {
            try {
                Log.d(TAG, "🔗 Handling non-HTTP download: " + url);
                
                if (url.startsWith("ftp://")) {
                    // Handle FTP downloads
                    Toast.makeText(BrowserActivity.this, "FTP downloads not supported yet", Toast.LENGTH_LONG).show();
                } else if (url.startsWith("file://")) {
                    // Handle local file access
                    Toast.makeText(BrowserActivity.this, "Local file access restricted", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(BrowserActivity.this, "Unsupported download scheme: " + url.split("://")[0], Toast.LENGTH_LONG).show();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error handling non-HTTP download", e);
                Toast.makeText(BrowserActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        
        private String getExtensionFromMimeType(String mimeType) {
            if (mimeType == null || mimeType.isEmpty()) {
                return "bin";
            }
            
            switch (mimeType.toLowerCase()) {
                case "image/jpeg":
                case "image/jpg":
                    return "jpg";
                case "image/png":
                    return "png";
                case "image/gif":
                    return "gif";
                case "image/webp":
                    return "webp";
                case "image/svg+xml":
                    return "svg";
                case "text/html":
                    return "html";
                case "text/css":
                    return "css";
                case "text/javascript":
                case "application/javascript":
                    return "js";
                case "application/json":
                    return "json";
                case "text/plain":
                    return "txt";
                case "application/pdf":
                    return "pdf";
                case "video/mp4":
                    return "mp4";
                case "audio/mp3":
                case "audio/mpeg":
                    return "mp3";
                default:
                    return "bin";
            }
        }
        
        private void saveDataToFile(String filename, byte[] data) throws Exception {
            // Save to Downloads directory
            java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            
            java.io.File file = new java.io.File(downloadsDir, filename);
            
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(data);
                fos.flush();
            }
            
            // Add to download manager tracking
            DownloadManager downloadManager = DownloadManager.getInstance(BrowserActivity.this);
            downloadManager.addDownload(filename, "data-uri", String.valueOf(System.currentTimeMillis()));
            
            Log.d(TAG, "✅ Data file saved: " + file.getAbsolutePath());
        }
    }
    
    // JavaScript interface for blob downloads
    private class BlobDownloadInterface {
        @android.webkit.JavascriptInterface
        public void onBlobData(String dataUrl) {
            runOnUiThread(() -> {
                try {
                    // Handle the blob data as a data URI
                    IntelligentDownloadListener listener = new IntelligentDownloadListener();
                    listener.handleDataUriDownload(dataUrl, "application/octet-stream");
                } catch (Exception e) {
                    Log.e(TAG, "Error processing blob data", e);
                    Toast.makeText(BrowserActivity.this, "Error processing blob data", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        @android.webkit.JavascriptInterface
        public void onBlobError(String error) {
            runOnUiThread(() -> {
                Log.e(TAG, "Blob download error: " + error);
                Toast.makeText(BrowserActivity.this, "Blob download failed: " + error, Toast.LENGTH_LONG).show();
            });
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
    protected void onDestroy() {
        isDestroyed = true;
        
        try {
            Log.d(TAG, "BrowserActivity onDestroy - comprehensive cleanup");
            
            // Cancel any pending zoom operations
            if (zoomHandler != null && pendingZoomRunnable != null) {
                zoomHandler.removeCallbacks(pendingZoomRunnable);
            }
            
            if (webView != null) {
                // Save session before destroying (for app close recovery)
                saveCurrentSessionAsLast();
                
                // Comprehensive WebView cleanup to prevent memory leaks and freezing
                webView.clearHistory();
                webView.clearCache(true);
                webView.clearFormData();
                webView.loadUrl("about:blank");
                
                // Pause and destroy properly
                webView.onPause();
                webView.removeAllViews();
                webView.destroyDrawingCache();
                webView.pauseTimers();
                
                // Final destruction
                webView.destroy();
                webView = null;
                
                Log.d(TAG, "WebView destroyed and cleaned up properly");
            }
            
            // Clear references to prevent memory leaks
            tabList = null;
            urlStack = null;
            zoomHandler = null;
            pendingZoomRunnable = null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
        
        super.onDestroy();
    }
    
    @Override 
    protected void onPause() {
        super.onPause();
        isPaused = true;
        
        try {
            Log.d(TAG, "BrowserActivity onPause - proper lifecycle management");
            
            if (webView != null) {
                // Pause WebView properly to prevent freezing
                webView.onPause();
                webView.pauseTimers();
                
                // Save current state to prevent data loss
                saveCurrentBrowserState();
            }
            
            // Clear any active operations
            isRefreshing = false;
            isNavigating = false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during pause", e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        
        try {
            Log.d(TAG, "BrowserActivity onResume - restoring from pause");
            
            if (webView != null && !isDestroyed) {
                // Resume WebView properly
                webView.onResume();
                webView.resumeTimers();
                
                // Restore any necessary state
                restoreCurrentBrowserState();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during resume", e);
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        try {
            Log.d(TAG, "BrowserActivity onStop - saving session state");
            
            // Save comprehensive session state when app is stopped
            saveCurrentSessionAsLast();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during stop", e);
        }
    }
    
    @Override
    protected void onRestart() {
        super.onRestart();
        
        try {
            Log.d(TAG, "BrowserActivity onRestart - recovering from stop");
            
            // Reinitialize if needed
            if (webView != null && !isDestroyed) {
                // Refresh current page to ensure it's still responsive
                String currentUrl = webView.getUrl();
                if (currentUrl != null && !currentUrl.equals("about:blank")) {
                    // Only reload if we have a valid URL
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isDestroyed && webView != null) {
                            webView.reload();
                        }
                    }, 500);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during restart", e);
        }
    }
    
    private void saveCurrentBrowserState() {
        try {
            if (webView != null && sessionManager != null) {
                String currentUrl = webView.getUrl();
                String currentTitle = webView.getTitle();
                
                if (currentUrl != null && !currentUrl.equals("about:blank")) {
                    // Save current browsing state to SharedPreferences for quick recovery
                    sessionManager.saveTemporaryState(currentUrl, currentTitle, currentZoom);
                    Log.d(TAG, "Browser state saved: " + currentUrl);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving browser state", e);
        }
    }
    
    private void restoreCurrentBrowserState() {
        try {
            if (sessionManager != null) {
                // This is a lightweight restore for pause/resume scenarios
                // Full session restore is handled separately
                Log.d(TAG, "Browser state restored from pause");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring browser state", e);
        }
    }
    
    // ==================== CHROME-LIKE TAB MANAGEMENT ====================
    
    private void createNewTab() {
        try {
            Log.d(TAG, "Creating new tab - Chrome-like functionality");
            
            // Save current tab state
            if (webView != null && webView.getUrl() != null) {
                String currentUrl = webView.getUrl();
                String currentTitle = webView.getTitle();
                updateCurrentTabInfo(currentUrl, currentTitle != null ? currentTitle : "Tab");
            }
            
            // Add new tab to list
            tabCount++;
            String newTabUrl = "https://www.google.com"; // Default new tab URL
            tabList.add(new TabInfo(newTabUrl, "New Tab", true));
            
            // Set all other tabs to inactive
            for (int i = 0; i < tabList.size() - 1; i++) {
                tabList.get(i).isActive = false;
            }
            
            // Load new tab
            loadNewUrl(newTabUrl);
            
            // Update UI
            updateTabCounter();
            renderTabsInContainer();
            
            Toast.makeText(this, "New tab created", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating new tab", e);
            Toast.makeText(this, "Error creating tab: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void renderTabsInContainer() {
        if (tabsContainer == null) return;
        
        try {
            tabsContainer.removeAllViews();
            
            for (int i = 0; i < tabList.size(); i++) {
                TabInfo tab = tabList.get(i);
                View tabView = createTabView(tab, i);
                tabsContainer.addView(tabView);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error rendering tabs", e);
        }
    }
    
    private View createTabView(TabInfo tab, int index) {
        // Create tab view similar to Chrome
        LinearLayout tabView = new LinearLayout(this);
        tabView.setOrientation(LinearLayout.HORIZONTAL);
        tabView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tabView.setPadding(12, 6, 8, 6);
        tabView.setBackgroundResource(tab.isActive ? R.drawable.button_background : R.drawable.feature_card_background);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(4, 0, 4, 0);
        tabView.setLayoutParams(params);
        
        // Tab title
        TextView titleView = new TextView(this);
        titleView.setText(tab.title.length() > 12 ? tab.title.substring(0, 12) + "..." : tab.title);
        titleView.setTextSize(10);
        titleView.setTextColor(tab.isActive ? 
            getResources().getColor(android.R.color.white) : 
            getResources().getColor(R.color.text_primary));
        titleView.setMaxWidth(100);
        titleView.setSingleLine(true);
        
        // Close button (like Chrome)
        Button closeButton = new Button(this);
        closeButton.setText("×");
        closeButton.setTextSize(12);
        closeButton.setTextColor(tab.isActive ? 
            getResources().getColor(android.R.color.white) : 
            getResources().getColor(R.color.text_primary));
        closeButton.setBackground(null);
        closeButton.setPadding(8, 0, 8, 0);
        closeButton.setMinWidth(0);
        closeButton.setMinHeight(0);
        
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(24, 24);
        closeButton.setLayoutParams(closeParams);
        
        // Click listeners
        tabView.setOnClickListener(v -> switchToTab(index));
        closeButton.setOnClickListener(v -> closeTab(index));
        
        tabView.addView(titleView);
        tabView.addView(closeButton);
        
        return tabView;
    }
    
    private void switchToTab(int index) {
        try {
            if (index < 0 || index >= tabList.size()) return;
            
            Log.d(TAG, "Switching to tab: " + index);
            
            // Save current tab state
            if (webView != null && webView.getUrl() != null) {
                updateCurrentTabInfo(webView.getUrl(), webView.getTitle());
            }
            
            // Set all tabs to inactive
            for (TabInfo tab : tabList) {
                tab.isActive = false;
            }
            
            // Activate selected tab
            TabInfo selectedTab = tabList.get(index);
            selectedTab.isActive = true;
            
            // Load tab URL
            if (selectedTab.url != null && !selectedTab.url.isEmpty()) {
                loadNewUrl(selectedTab.url);
            }
            
            // Update UI
            renderTabsInContainer();
            
        } catch (Exception e) {
            Log.e(TAG, "Error switching tab", e);
        }
    }
    
    private void closeTab(int index) {
        try {
            if (index < 0 || index >= tabList.size()) return;
            
            Log.d(TAG, "Closing tab: " + index);
            
            // Don't allow closing the last tab
            if (tabList.size() <= 1) {
                Toast.makeText(this, "Cannot close the last tab", Toast.LENGTH_SHORT).show();
                return;
            }
            
            boolean wasActive = tabList.get(index).isActive;
            tabList.remove(index);
            tabCount--;
            
            // If we closed the active tab, activate another one
            if (wasActive) {
                int newActiveIndex = Math.min(index, tabList.size() - 1);
                if (newActiveIndex >= 0) {
                    tabList.get(newActiveIndex).isActive = true;
                    TabInfo activeTab = tabList.get(newActiveIndex);
                    if (activeTab.url != null) {
                        loadNewUrl(activeTab.url);
                    }
                }
            }
            
            // Update UI
            updateTabCounter();
            renderTabsInContainer();
            
            Toast.makeText(this, "Tab closed", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error closing tab", e);
        }
    }
    
    private void updateCurrentTabInfo(String url, String title) {
        try {
            // Find and update the active tab
            for (TabInfo tab : tabList) {
                if (tab.isActive) {
                    tab.url = url;
                    tab.title = title != null ? title : "Tab";
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating current tab info", e);
        }
    }
    
    // ==================== END CHROME-LIKE TAB MANAGEMENT ====================
    
    private void setupZoomSlider() {
        if (zoomSlider != null) {
            zoomSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // Use safe debounced zoom for slider changes
                        safeDebouncedZoom(() -> {
                            currentZoom = Math.max(progress, 25); // Min 25%
                            applySafeZoom();
                            showZoomControls();
                        });
                    }
                }
                
                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                    showZoomControls();
                }
                
                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                    // Hide zoom controls after 3 seconds of inactivity
                    hideZoomControlsDelayed();
                }
            });
        }
    }
    
    private void checkSessionRestore() {
        boolean restoreSession = getIntent().getBooleanExtra("restore_session", false);
        if (restoreSession) {
            String sessionType = getIntent().getStringExtra("session_type");
            if ("last".equals(sessionType)) {
                restoreLastSession();
            } else if ("recent".equals(sessionType)) {
                restoreRecentSession();
            }
        }
    }
    
    private void saveCurrentSessionAsRecent() {
        android.util.Log.d(TAG, "Saving current session as recent");
        SessionManager.BrowserSession session = new SessionManager.BrowserSession();
        
        try {
            // Save all tabs from tabList
            if (tabList != null && !tabList.isEmpty()) {
                android.util.Log.d(TAG, "Saving " + tabList.size() + " tabs to recent session");
                for (TabInfo tab : tabList) {
                    if (tab.url != null && !tab.url.isEmpty()) {
                        SessionManager.TabSession tabSession = new SessionManager.TabSession(
                            tab.url, 
                            tab.title != null ? tab.title : "Tab", 
                            null // WebView state - will be enhanced
                        );
                        session.tabs.add(tabSession);
                    }
                }
                
                // Also save current WebView if active
                String currentUrl = webView.getUrl();
                String currentTitle = webView.getTitle();
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    // Check if current URL is already in the list
                    boolean currentUrlExists = false;
                    for (SessionManager.TabSession existingTab : session.tabs) {
                        if (currentUrl.equals(existingTab.url)) {
                            currentUrlExists = true;
                            break;
                        }
                    }
                    
                    // Add current WebView as a tab if not already present
                    if (!currentUrlExists) {
                        SessionManager.TabSession currentTabSession = new SessionManager.TabSession(
                            currentUrl, 
                            currentTitle != null ? currentTitle : "Current Tab", 
                            null
                        );
                        session.tabs.add(currentTabSession);
                        android.util.Log.d(TAG, "Added current WebView to recent session: " + currentUrl);
                    }
                }
            } else {
                // No tabs in list, save current WebView
                String currentUrl = webView.getUrl();
                String currentTitle = webView.getTitle();
                if (currentUrl != null && !currentUrl.isEmpty()) {
                    SessionManager.TabSession currentTabSession = new SessionManager.TabSession(
                        currentUrl, 
                        currentTitle != null ? currentTitle : "Current Tab", 
                        null
                    );
                    session.tabs.add(currentTabSession);
                    android.util.Log.d(TAG, "No tabs in list, saved current WebView to recent session: " + currentUrl);
                }
            }
            
            session.timestamp = System.currentTimeMillis();
            sessionManager.saveRecentSession(session);
            android.util.Log.d(TAG, "Recent session saved successfully with " + session.tabs.size() + " tabs");
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error saving recent session", e);
        }
    }
    
    private void saveCurrentSessionAsLast() {
        android.util.Log.d(TAG, "Saving current session as last session");
        SessionManager.BrowserSession session = new SessionManager.BrowserSession();
        
        try {
            // Save all tabs from tabList
            if (tabList != null && !tabList.isEmpty()) {
                for (TabInfo tab : tabList) {
                    if (tab.url != null && !tab.url.isEmpty()) {
                        SessionManager.TabSession tabSession = new SessionManager.TabSession(
                            tab.url, 
                            tab.title != null ? tab.title : "Tab", 
                            null
                        );
                        session.tabs.add(tabSession);
                    }
                }
            }
            
            // Always save current WebView
            String currentUrl = webView.getUrl();
            String currentTitle = webView.getTitle();
            if (currentUrl != null && !currentUrl.isEmpty()) {
                SessionManager.TabSession currentTabSession = new SessionManager.TabSession(
                    currentUrl, 
                    currentTitle != null ? currentTitle : "Current Tab", 
                    null
                );
                session.tabs.add(currentTabSession);
            }
            
            session.timestamp = System.currentTimeMillis();
            sessionManager.saveLastSession(session);
            android.util.Log.d(TAG, "Last session saved successfully");
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error saving last session", e);
        }
    }
    
    private void restoreLastSession() {
        android.util.Log.d(TAG, "Restoring last session");
        try {
            SessionManager.BrowserSession session = sessionManager.getLastSession();
            if (session != null && !session.tabs.isEmpty()) {
                restoreSession(session);
            } else {
                android.util.Log.d(TAG, "No last session found");
                Toast.makeText(this, "No previous session found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error restoring last session", e);
            Toast.makeText(this, "Error restoring session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void restoreRecentSession() {
        android.util.Log.d(TAG, "Restoring recent session");
        try {
            SessionManager.BrowserSession session = sessionManager.getRecentSession();
            if (session != null && !session.tabs.isEmpty()) {
                restoreSession(session);
            } else {
                android.util.Log.d(TAG, "No recent session found");
                Toast.makeText(this, "No recent session found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error restoring recent session", e);
            Toast.makeText(this, "Error restoring session: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void restoreSession(SessionManager.BrowserSession session) {
        android.util.Log.d(TAG, "Restoring session with " + session.tabs.size() + " tabs");
        
        if (session.tabs.isEmpty()) {
            Toast.makeText(this, "Session is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Clear current tab list and URL stack
        tabList.clear();
        urlStack.clear();
        
        // Load first tab in WebView
        SessionManager.TabSession firstTab = session.tabs.get(0);
        if (firstTab.url != null && !firstTab.url.isEmpty()) {
            loadNewUrl(firstTab.url);
            
            // Add all tabs to tab list
            for (SessionManager.TabSession tabSession : session.tabs) {
                if (tabSession.url != null && !tabSession.url.isEmpty()) {
                    TabInfo tabInfo = new TabInfo(
                        tabSession.url, 
                        tabSession.title != null ? tabSession.title : "Restored Tab",
                        tabSession == firstTab // First tab is active
                    );
                    tabList.add(tabInfo);
                    urlStack.add(tabSession.url);
                }
            }
            
            // Update tab counter
            tabCount = tabList.size();
            updateTabCounter();
            
            android.util.Log.d(TAG, "Session restored successfully with " + tabList.size() + " tabs");
            Toast.makeText(this, "Session restored with " + tabList.size() + " tabs", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateTabCounter() {
        if (tabCountText != null) {
            tabCountText.setText(String.valueOf(tabCount));
        }
    }
    
    private void showZoomControls() {
        if (zoomControlsContainer != null) {
            zoomControlsContainer.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideZoomControlsDelayed() {
        if (zoomControlsContainer != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (zoomControlsContainer != null) {
                    zoomControlsContainer.setVisibility(View.GONE);
                }
            }, 3000);
        }
    }
    
    private void expandAddressBar() {
        // Implementation for address bar expansion
        if (addressBar != null) {
            addressBar.setSelection(addressBar.getText().length());
        }
    }
    
    private void collapseAddressBar() {
        // Implementation for address bar collapse
        // This could hide soft keyboard and adjust layout
    }
    
    private void showUrlStackDialog() {
        if (urlStack.isEmpty()) {
            Toast.makeText(this, "No URL history available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("URL Stack (" + urlStack.size() + " items)");
        
        String[] urlArray = urlStack.toArray(new String[0]);
        builder.setItems(urlArray, (dialog, which) -> {
            String selectedUrl = urlArray[which];
            loadNewUrl(selectedUrl);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showTabSwitcher() {
        if (tabList.isEmpty()) {
            Toast.makeText(this, "No tabs available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Tab Switcher (" + tabList.size() + " tabs)");
        
        String[] tabTitles = new String[tabList.size()];
        for (int i = 0; i < tabList.size(); i++) {
            TabInfo tab = tabList.get(i);
            tabTitles[i] = (tab.isActive ? "► " : "") + (tab.title != null ? tab.title : "Tab") + 
                          "\n" + (tab.url != null ? tab.url : "");
        }
        
        builder.setItems(tabTitles, (dialog, which) -> {
            TabInfo selectedTab = tabList.get(which);
            if (selectedTab.url != null && !selectedTab.url.isEmpty()) {
                // Mark all tabs as inactive
                for (TabInfo tab : tabList) {
                    tab.isActive = false;
                }
                // Mark selected tab as active
                selectedTab.isActive = true;
                
                loadNewUrl(selectedTab.url);
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}