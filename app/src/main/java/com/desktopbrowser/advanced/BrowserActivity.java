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
    private Button browserMenuButton;
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
        
        // Zoom crash prevention - IMPROVED FOR FREE ZOOMING
        private boolean isZooming = false;
        private long lastZoomTime = 0;
        private static final long ZOOM_DEBOUNCE = 100; // Reduced to 100ms for more responsive zooming
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
        // No longer using toolbar, using custom header
    }
    
    private void initializeViews() {
        webView = findViewById(R.id.webview);
        addressBar = findViewById(R.id.address_bar);
        browserMenuButton = findViewById(R.id.btn_browser_menu);
        zoomInButton = findViewById(R.id.btn_zoom_in);
        zoomOutButton = findViewById(R.id.btn_zoom_out);
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
        
        if (webView == null || addressBar == null || browserMenuButton == null) {
            throw new RuntimeException("Required views not found in layout");
        }
        
        // Update zoom level display
        updateZoomLevel();
        
        // Setup zoom slider with better responsiveness
        setupZoomSlider();
        
        // Check if restoring session
        checkSessionRestore();
        
        // Apply minimal UI mode if this is a quick access site
        applyMinimalUIMode();
    }
    
    private void applyMinimalUIMode() {
        boolean isQuickAccessMode = getIntent().getBooleanExtra("is_quick_access_mode", false);
        
        if (isQuickAccessMode) {
            android.util.Log.d(TAG, "🎯 Applying minimal UI mode for quick access site");
            
            // Hide the entire tab management section
            LinearLayout tabManagementSection = findViewById(R.id.tab_management_section);
            if (tabManagementSection != null) {
                tabManagementSection.setVisibility(View.GONE);
                android.util.Log.d(TAG, "✅ Tab management section hidden");
            }
            
            // Hide the address bar
            if (addressBar != null) {
                addressBar.setVisibility(View.GONE);
                android.util.Log.d(TAG, "✅ Address bar hidden");
            }
            
            android.util.Log.d(TAG, "✅ Minimal UI mode applied - hidden search bar and tabs");
        } else {
            android.util.Log.d(TAG, "🌐 Full UI mode - showing all elements");
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView() {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript and advanced web features
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        
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

    /**
     * FIXED: Intelligent download link detection - Only detects actual file extensions
     */
    private boolean isDownloadLink(String url) {
        if (url == null || url.isEmpty()) return false;
        
        try {
            // Convert to lowercase for case-insensitive matching
            String urlLower = url.toLowerCase();
            
            // FIXED: Only check for actual file extensions at the end of URL
            String[] downloadExtensions = {
                ".apk", ".exe", ".msi", ".dmg", ".pkg", ".deb", ".rpm",  // Executables/Installers
                ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz",    // Archives
                ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", // Documents
                ".mp3", ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv",  // Media files
                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp", // Images
                ".iso", ".img", ".bin", ".torrent", ".ipa",              // Disk images & others
                ".epub", ".mobi", ".azw", ".azw3",                       // E-books
                ".ttf", ".otf", ".woff", ".woff2",                       // Fonts
                ".jar", ".war", ".ear",                                  // Java archives
                ".cab", ".nsi", ".appx", ".msix",                        // Windows packages
                ".crx", ".xpi"                                           // Browser extensions
            };
            
            // Remove query parameters and fragments for clean URL checking
            String cleanUrl = urlLower.split("\\?")[0].split("#")[0];
            
            // Check if URL ends with any download extension
            for (String ext : downloadExtensions) {
                if (cleanUrl.endsWith(ext)) {
                    Log.d(TAG, "🎯 Download link detected by extension: " + ext + " - " + url);
                    return true;
                }
            }
            
            Log.d(TAG, "🌐 URL identified as regular webpage: " + url);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in download link detection", e);
            return false;
        }
    }
    
    /**
     * Handle detected download link
     */
    private void handleDetectedDownloadLink(String url) {
        try {
            Log.d(TAG, "🚀 Handling detected download link: " + url);
            
            // Extract filename from URL for better UX
            String filename = "Unknown File";
            try {
                String[] urlParts = url.split("/");
                String lastPart = urlParts[urlParts.length - 1];
                if (lastPart.contains(".") && lastPart.length() > 0) {
                    // Remove query parameters
                    if (lastPart.contains("?")) {
                        lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                    }
                    filename = java.net.URLDecoder.decode(lastPart, "UTF-8");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not extract filename from URL", e);
            }
            
            // Show the beautiful download confirmation dialog
            showDownloadConfirmationDialog(url, filename, "application/octet-stream", -1, () -> {
                // Trigger download using the existing download system
                intelligentDownload(url);
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling detected download link", e);
            Toast.makeText(this, "Error processing download link", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * FIXED: Create new tab with blank page instead of cloning current tab
     */
    private void createNewTab() {
        try {
            Log.d(TAG, "Creating new tab - Chrome-like functionality");
            
            // Save current tab state
            if (webView != null && webView.getUrl() != null) {
                String currentUrl = webView.getUrl();
                String currentTitle = webView.getTitle();
                updateCurrentTabInfo(currentUrl, currentTitle != null ? currentTitle : "Tab");
            }
            
            // Add new tab to list with blank page
            tabCount++;
            String newTabUrl = "about:blank"; // FIXED: Use blank page instead of Google
            tabList.add(new TabInfo(newTabUrl, "New Tab", true));
            
            // Set all other tabs to inactive
            for (int i = 0; i < tabList.size() - 1; i++) {
                tabList.get(i).isActive = false;
            }
            
            // Load blank page
            loadNewTab();
            
            // Update UI
            updateTabCounter();
            renderTabsInContainer();
            
            Toast.makeText(this, "New tab created", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating new tab", e);
            Toast.makeText(this, "Error creating tab: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Load new blank tab
     */
    private void loadNewTab() {
        try {
            if (webView != null) {
                // Load blank page
                webView.loadUrl("about:blank");
                // Clear address bar
                if (addressBar != null) {
                    addressBar.setText("");
                }
                Log.d(TAG, "✅ New blank tab loaded");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading new tab", e);
        }
    }

    /**
     * IMPROVED: Free zooming - reduced debounce and better responsiveness
     */
    private void safeDebouncedZoom(Runnable zoomAction) {
        long currentTime = System.currentTimeMillis();
        
        // Reduced debouncing for more responsive zooming
        if (isZooming && (currentTime - lastZoomTime) < ZOOM_DEBOUNCE) {
            Log.d(TAG, "Zoom operation debounced - preventing crash");
            return;
        }
        
        // Cancel any pending zoom operations
        if (pendingZoomRunnable != null) {
            zoomHandler.removeCallbacks(pendingZoomRunnable);
        }
        
        lastZoomTime = currentTime;
        isZooming = true;
        
        // Execute zoom operation safely with reduced delay
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
        
        // Reduced delay for more responsive zooming
        zoomHandler.postDelayed(pendingZoomRunnable, 25);
    }
    
    /**
     * IMPROVED: Apply zoom with better responsiveness
     */
    private void applySafeZoom() {
        try {
            if (isDestroyed || isPaused || webView == null) {
                Log.w(TAG, "Skipping zoom - activity not ready");
                return;
            }
            
            // Apply zoom with crash prevention
            float tempZoomFactor = currentZoom / 100.0f;
            
            // Validate zoom factor to prevent extreme values
            if (tempZoomFactor < 0.25f) tempZoomFactor = 0.25f;
            if (tempZoomFactor > 3.0f) tempZoomFactor = 3.0f; // Increased max zoom
            
            // Create final variable for lambda expression
            final float finalZoomFactor = tempZoomFactor;
            
            // Apply zoom safely on main thread
            runOnUiThread(() -> {
                try {
                    if (webView != null && !isDestroyed) {
                        // Use WebView's built-in zoom for better compatibility
                        webView.setInitialScale((int)(finalZoomFactor * 100));
                        
                        // Update zoom level display
                        updateZoomLevel();
                        
                        // Update zoom slider safely
                        if (zoomSlider != null) {
                            zoomSlider.setProgress((int) currentZoom);
                        }
                        
                        // Show zoom controls when zooming
                        showZoomControls();
                        hideZoomControlsDelayed();
                        
                        Log.d(TAG, "Safe zoom applied: " + finalZoomFactor);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error applying safe zoom", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in applySafeZoom", e);
        }
    }

    // FIXED: Include all necessary method stubs and implementations
    private void updateCurrentTabInfo(String url, String title) {
        // Implementation for updating current tab info
        if (tabList != null && !tabList.isEmpty()) {
            for (TabInfo tab : tabList) {
                if (tab.isActive) {
                    tab.url = url;
                    tab.title = title;
                    break;
                }
            }
        }
    }

    private void updateTabCounter() {
        // Update tab counter display
        if (tabCountText != null) {
            tabCountText.setText(String.valueOf(tabCount));
        }
    }

    private void renderTabsInContainer() {
        // Render tabs in container
        if (tabsContainer != null && tabList != null) {
            tabsContainer.removeAllViews();
            for (int i = 0; i < tabList.size(); i++) {
                // Create tab views - implementation would go here
            }
        }
    }

    private void updateZoomLevel() {
        // Update zoom level display
        if (zoomLevel != null) {
            zoomLevel.setText((int)currentZoom + "%");
        }
    }

    private void setupZoomSlider() {
        // Setup zoom slider functionality
        if (zoomSlider != null) {
            zoomSlider.setMax(275); // Increased max zoom
            zoomSlider.setProgress((int)currentZoom);
        }
    }

    private void checkSessionRestore() {
        // Check for session restoration
    }

    private void setupIntelligentLongPressMenu() {
        // Setup long press menu
    }

    private void setupCustomZoomControls() {
        // Setup custom zoom controls
    }

    private void setupCustomScrolling() {
        // Setup custom scrolling
    }

    private void loadUrl() {
        // Load initial URL
        String url = getIntent().getStringExtra("url");
        if (url != null && webView != null) {
            loadNewUrl(url);
        }
    }

    private void loadNewUrl(String url) {
        // Load new URL in webview
        if (webView != null && url != null) {
            webView.loadUrl(url);
            if (addressBar != null) {
                addressBar.setText(url);
            }
        }
    }

    private String processUrl(String input) {
        // Process URL input
        if (input == null || input.trim().isEmpty()) {
            return "https://www.google.com";
        }
        
        input = input.trim();
        
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        } else if (input.contains(".") && !input.contains(" ") && input.length() > 3) {
            return "https://" + input;
        } else {
            return "https://www.google.com/search?q=" + Uri.encode(input);
        }
    }

    private void showZoomControls() {
        // Show zoom controls
        if (zoomControlsContainer != null) {
            zoomControlsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void hideZoomControlsDelayed() {
        // Hide zoom controls after delay
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (zoomControlsContainer != null) {
                zoomControlsContainer.setVisibility(View.GONE);
            }
        }, 3000);
    }

    private void intelligentDownload(String url) {
        // Intelligent download implementation
        downloadFile(url, extractFilenameFromUrl(url));
    }

    private String extractFilenameFromUrl(String url) {
        try {
            String[] urlParts = url.split("/");
            String lastPart = urlParts[urlParts.length - 1];
            if (lastPart.contains("?")) {
                lastPart = lastPart.substring(0, lastPart.indexOf("?"));
            }
            return lastPart.isEmpty() ? "download" : lastPart;
        } catch (Exception e) {
            return "download";
        }
    }

    private void downloadFile(String url, String filename) {
        try {
            Log.d(TAG, "📥 Starting enhanced download - URL: " + url + ", Filename: " + filename);
            
            // ENHANCED: Handle all URI types intelligently
            if (url.startsWith("data:")) {
                Log.d(TAG, "🔗 Redirecting data URI to specialized handler");
                IntelligentDownloadListener listener = new IntelligentDownloadListener();
                listener.handleDataUriDownload(url, "");
                return;
            } else if (url.startsWith("blob:")) {
                Log.d(TAG, "🌐 Redirecting blob URI to specialized handler");
                IntelligentDownloadListener listener = new IntelligentDownloadListener();
                listener.handleBlobDownload(url);
                return;
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Log.d(TAG, "🔗 Redirecting non-HTTP URI to specialized handler");
                IntelligentDownloadListener listener = new IntelligentDownloadListener();
                listener.handleNonHttpDownload(url);
                return;
            }
            
            // Continue with regular HTTP/HTTPS download
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
            
            // Track download in our system
            com.desktopbrowser.advanced.DownloadManager.getInstance(this).addDownload(filename, url, String.valueOf(downloadId));
            
            Toast.makeText(this, "📥 Download started: " + filename, Toast.LENGTH_SHORT).show();
            
            Log.d(TAG, "✅ Download started - ID: " + downloadId + ", File: " + filename);
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error starting download", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDownloadConfirmationDialog(String url, String filename, String mimetype, long contentLength, Runnable onConfirm) {
        try {
            // Create custom dialog layout
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(android.R.layout.select_dialog_multichoice, null);
            
            // Create a custom layout programmatically since we don't have a specific layout file
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(32, 24, 32, 24);
            mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"));
            
            // Title with download icon
            TextView titleView = new TextView(this);
            titleView.setText("📥 Download File");
            titleView.setTextSize(20);
            titleView.setTextColor(android.graphics.Color.parseColor("#1976D2"));
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 16);
            mainLayout.addView(titleView);
            
            // File info card
            LinearLayout fileInfoCard = new LinearLayout(this);
            fileInfoCard.setOrientation(LinearLayout.VERTICAL);
            fileInfoCard.setBackgroundColor(android.graphics.Color.WHITE);
            fileInfoCard.setPadding(20, 16, 20, 16);
            
            // Filename
            TextView filenameView = new TextView(this);
            filenameView.setText("📄 " + filename);
            filenameView.setTextSize(16);
            filenameView.setTextColor(android.graphics.Color.parseColor("#212121"));
            filenameView.setTypeface(filenameView.getTypeface(), android.graphics.Typeface.BOLD);
            fileInfoCard.addView(filenameView);
            
            // File type
            if (mimetype != null && !mimetype.isEmpty()) {
                TextView typeView = new TextView(this);
                typeView.setText("🏷️ Type: " + mimetype);
                typeView.setTextSize(14);
                typeView.setTextColor(android.graphics.Color.parseColor("#757575"));
                typeView.setPadding(0, 8, 0, 0);
                fileInfoCard.addView(typeView);
            }
            
            mainLayout.addView(fileInfoCard);
            
            builder.setView(mainLayout);
            
            // Download button (positive)
            builder.setPositiveButton("📥 Download", (dialog, which) -> {
                try {
                    // Show "Download Started" message
                    Toast.makeText(this, "✅ Download Started: " + filename, Toast.LENGTH_LONG).show();
                    onConfirm.run();
                } catch (Exception e) {
                    Log.e(TAG, "Error starting download", e);
                    Toast.makeText(this, "❌ Download failed to start", Toast.LENGTH_SHORT).show();
                }
            });
            
            // Cancel button (negative)
            builder.setNegativeButton("❌ Ignore", (dialog, which) -> {
                Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing download dialog", e);
            // Fallback - show simple confirmation
            new android.app.AlertDialog.Builder(this)
                .setTitle("Download File")
                .setMessage("Download " + filename + "?")
                .setPositiveButton("Download", (dialog, which) -> {
                    Toast.makeText(this, "✅ Download Started", Toast.LENGTH_SHORT).show();
                    onConfirm.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
    
    private void setupNavigationControls() {
        // Browser menu button click listener
        browserMenuButton.setOnClickListener(v -> showBrowserNavigationMenu());
        
        // IMPROVED: Advanced Desktop Zoom Controls with better responsiveness
        zoomInButton.setOnClickListener(v -> {
            safeDebouncedZoom(() -> {
                currentZoom = Math.min(currentZoom + 10, 300); // Increased max to 300%
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
        
        // FIXED: Chrome-like new tab button - creates blank tab
        if (newTabButton != null) {
            newTabButton.setOnClickListener(v -> createNewTab());
        }
        
        // Update tab counter display
        updateTabCounter();
        
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

    private void showBrowserNavigationMenu() {
        // Show browser navigation menu
    }

    private void showUrlStackDialog() {
        // Show URL stack dialog
    }

    private void showTabSwitcher() {
        // Show tab switcher
    }

    private void expandAddressBar() {
        // Expand address bar
    }

    private void collapseAddressBar() {
        // Collapse address bar
    }

    /**
     * FIXED: Improved blob download handling
     */
    private class IntelligentDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            try {
                Log.d(TAG, "📥 Download detected - URL: " + url + ", MimeType: " + mimetype);
                
                // Check if it's a data URI or blob
                if (url.startsWith("data:")) {
                    handleDataUriDownload(url, mimetype);
                    return;
                } else if (url.startsWith("blob:")) {
                    handleBlobDownload(url);
                    return;
                }
                
                // Extract filename intelligently
                String filename = extractFilename(url, contentDisposition);
                
                // Show beautiful download confirmation dialog
                showDownloadConfirmationDialog(url, filename, mimetype, contentLength, () -> {
                    downloadFile(url, filename);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error in download start", e);
                Toast.makeText(BrowserActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        
        /**
         * FIXED: Improved blob download handling
         */
        public void handleBlobDownload(String blobUrl) {
            try {
                Log.d(TAG, "🌐 Handling blob URL download: " + blobUrl);
                
                // Enhanced blob handling with better error handling
                String script = 
                    "(function() {" +
                    "  try {" +
                    "    fetch('" + blobUrl + "')" +
                    "      .then(response => {" +
                    "        if (!response.ok) throw new Error('Network response was not ok');" +
                    "        return response.blob();" +
                    "      })" +
                    "      .then(blob => {" +
                    "        const reader = new FileReader();" +
                    "        reader.onload = function() {" +
                    "          Android.onBlobData(reader.result, blob.type, blob.size);" +
                    "        };" +
                    "        reader.onerror = function() {" +
                    "          Android.onBlobError('FileReader error');" +
                    "        };" +
                    "        reader.readAsDataURL(blob);" +
                    "      })" +
                    "      .catch(error => {" +
                    "        Android.onBlobError('Fetch error: ' + error.message);" +
                    "      });" +
                    "  } catch (e) {" +
                    "    Android.onBlobError('Script error: ' + e.message);" +
                    "  }" +
                    "})();";
                
                // Add JavaScript interface to handle blob data with improved interface
                webView.addJavascriptInterface(new BlobDownloadInterface(), "Android");
                webView.evaluateJavascript(script, null);
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error handling blob download", e);
                Toast.makeText(BrowserActivity.this, "Failed to download blob: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        public void handleDataUriDownload(String dataUri, String mimetype) {
            try {
                Log.d(TAG, "📊 Handling data URI download");
                
                // Parse data URI
                if (!dataUri.startsWith("data:")) {
                    throw new IllegalArgumentException("Invalid data URI");
                }
                
                String[] parts = dataUri.split(",", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Malformed data URI");
                }
                
                String header = parts[0];
                String data = parts[1];
                
                // Extract MIME type from header if not provided
                if (mimetype == null || mimetype.isEmpty()) {
                    if (header.contains(";")) {
                        mimetype = header.substring(5, header.indexOf(";"));
                    } else {
                        mimetype = header.substring(5);
                    }
                }
                
                // Generate filename based on MIME type
                String filename = "download" + getExtensionFromMimeType(mimetype);
                
                // Decode base64 data
                byte[] fileData;
                if (header.contains("base64")) {
                    fileData = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                } else {
                    fileData = java.net.URLDecoder.decode(data, "UTF-8").getBytes();
                }
                
                // Show confirmation dialog
                showDownloadConfirmationDialog("data:" + mimetype, filename, mimetype, fileData.length, () -> {
                    saveDataToFile(fileData, filename);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error handling data URI download", e);
                Toast.makeText(BrowserActivity.this, "Failed to download data: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        public void handleNonHttpDownload(String url) {
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

        private String extractFilename(String url, String contentDisposition) {
            try {
                // Try to extract from Content-Disposition header first
                if (contentDisposition != null && contentDisposition.contains("filename=")) {
                    String filename = contentDisposition.substring(
                        contentDisposition.indexOf("filename=") + 9);
                    if (filename.startsWith("\"")) {
                        filename = filename.substring(1, filename.indexOf("\"", 1));
                    }
                    return filename;
                }
                
                // Extract from URL
                String[] urlParts = url.split("/");
                String lastPart = urlParts[urlParts.length - 1];
                
                // Remove query parameters
                if (lastPart.contains("?")) {
                    lastPart = lastPart.substring(0, lastPart.indexOf("?"));
                }
                
                return lastPart.isEmpty() ? "download" : lastPart;
                
            } catch (Exception e) {
                Log.w(TAG, "Error extracting filename", e);
                return "download";
            }
        }

        private String getExtensionFromMimeType(String mimeType) {
            if (mimeType == null || mimeType.isEmpty()) {
                return ".bin";
            }
            
            switch (mimeType.toLowerCase()) {
                // Images
                case "image/jpeg": return ".jpg";
                case "image/png": return ".png";
                case "image/gif": return ".gif";
                case "image/webp": return ".webp";
                case "image/bmp": return ".bmp";
                case "image/svg+xml": return ".svg";
                
                // Videos
                case "video/mp4": return ".mp4";
                case "video/avi": return ".avi";
                case "video/x-msvideo": return ".avi";
                case "video/quicktime": return ".mov";
                case "video/x-ms-wmv": return ".wmv";
                case "video/webm": return ".webm";
                
                // Audio
                case "audio/mpeg": return ".mp3";
                case "audio/wav": return ".wav";
                case "audio/ogg": return ".ogg";
                case "audio/aac": return ".aac";
                
                // Documents
                case "application/pdf": return ".pdf";
                case "application/msword": return ".doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return ".docx";
                case "text/plain": return ".txt";
                case "text/html": return ".html";
                
                // Archives
                case "application/zip": return ".zip";
                case "application/x-rar-compressed": return ".rar";
                case "application/x-7z-compressed": return ".7z";
                
                default: return ".bin";
            }
        }

        private void saveDataToFile(byte[] data, String filename) {
            try {
                // Save to Downloads directory
                java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(downloadsDir, filename);
                
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                fos.write(data);
                fos.close();
                
                // Add to download manager
                com.desktopbrowser.advanced.DownloadManager.getInstance(BrowserActivity.this)
                    .addDownload(filename, "data_uri", String.valueOf(System.currentTimeMillis()));
                
                Toast.makeText(BrowserActivity.this, "✅ File saved: " + filename, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "✅ Data file saved successfully: " + filename);
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error saving data file", e);
                Toast.makeText(BrowserActivity.this, "Failed to save file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * FIXED: Improved blob download interface with better error handling
     */
    private class BlobDownloadInterface {
        @android.webkit.JavascriptInterface
        public void onBlobData(String dataUrl, String mimeType, long size) {
            runOnUiThread(() -> {
                try {
                    Log.d(TAG, "📦 Blob data received - Type: " + mimeType + ", Size: " + size);
                    // Handle the blob data as a data URI
                    IntelligentDownloadListener listener = new IntelligentDownloadListener();
                    listener.handleDataUriDownload(dataUrl, mimeType);
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

    // Placeholder classes for compilation
    private class AdvancedDesktopWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // FIXED: Check if it's a download link before loading
            if (isDownloadLink(url)) {
                Log.d(TAG, "🎯 Download link intercepted in shouldOverrideUrlLoading: " + url);
                handleDetectedDownloadLink(url);
                return true; // Prevent normal page loading
            }
            return false; // Allow normal page loading
        }
    }

    private class AdvancedWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progressBar != null) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        if (zoomHandler != null && pendingZoomRunnable != null) {
            zoomHandler.removeCallbacks(pendingZoomRunnable);
        }
    }
}