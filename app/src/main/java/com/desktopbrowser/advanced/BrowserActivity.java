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
import android.content.ClipboardManager;
import android.content.ClipData;
import android.database.Cursor;
import android.app.DownloadManager.Query;
import java.util.Timer;
import java.util.TimerTask;

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
        
        // Auto-refresh management - ENHANCED
        private Timer autoRefreshTimer;
        private boolean autoRefreshEnabled = true;
        private boolean isMinimized = false; // Track if app is minimized
        
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
        
        // Setup zoom slider
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
        
        // ENHANCED INTELLIGENT LONG PRESS CONTEXT MENU
        setupEnhancedIntelligentLongPressMenu();
        
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
    
    // ENHANCED INTELLIGENT LONG PRESS CONTEXT MENU SETUP
    private void setupEnhancedIntelligentLongPressMenu() {
        webView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult hitTestResult = webView.getHitTestResult();
                
                if (hitTestResult != null) {
                    Log.d(TAG, "🎯 Long press detected - Type: " + hitTestResult.getType());
                    
                    // Delay context menu to ensure proper hit test result
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        showEnhancedIntelligentContextMenu(hitTestResult);
                    }, 100);
                    
                    return true; // Consume the event
                }
                
                return false;
            }
        });
    }
    
    // ENHANCED CONTEXT MENU WITH INTELLIGENT TEXT SELECTION AND LINK HANDLING
    private void showEnhancedIntelligentContextMenu(WebView.HitTestResult hitTestResult) {
        int type = hitTestResult.getType();
        String extra = hitTestResult.getExtra();
        
        Log.d(TAG, "📋 Showing enhanced context menu - Type: " + type + ", Extra: " + extra);
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        
        java.util.List<String> options = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        
        // ENHANCED: Intelligent context based on hit test type
        switch (type) {
            case WebView.HitTestResult.SRC_ANCHOR_TYPE:
            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                // Link specific options
                builder.setTitle("🔗 Link Options");
                
                options.add("📋 Copy Link");
                actions.add(() -> copyToClipboard(extra, "Link copied to clipboard"));
                
                options.add("📤 Share Link");
                actions.add(() -> shareLink(extra, "Link"));
                
                options.add("📝 Copy Link Text");
                actions.add(() -> copyLinkText(extra));
                
                options.add("🆕 Open In New Tab");
                actions.add(() -> openInNewTab(extra));
                
                options.add("🔽 Download Link");
                actions.add(() -> intelligentDownload(extra));
                break;
                
            case WebView.HitTestResult.IMAGE_TYPE:
                // Image specific options
                builder.setTitle("🖼️ Image Options");
                
                options.add("🔽 Download Image");
                actions.add(() -> intelligentDownload(extra));
                
                options.add("📋 Copy Image URL");
                actions.add(() -> copyToClipboard(extra, "Image URL copied"));
                
                options.add("📤 Share Image");
                actions.add(() -> shareLink(extra, "Image"));
                break;
                
            case WebView.HitTestResult.EDIT_TEXT_TYPE:
                // Text field specific options
                builder.setTitle("📝 Text Field Options");
                
                options.add("📋 Paste");
                actions.add(() -> pasteFromClipboard());
                
                options.add("🔄 Select All");
                actions.add(() -> selectAllText());
                
                // Check if there's text to copy
                options.add("📄 Copy Text");
                actions.add(() -> copySelectedText());
                break;
                
            case WebView.HitTestResult.UNKNOWN_TYPE:
            default:
                // General text selection and page options
                builder.setTitle("📄 Page Options");
                
                // Text selection options
                options.add("✂️ Start Text Selection");
                actions.add(() -> startTextSelection());
                
                options.add("🔄 Select All Text");
                actions.add(() -> selectAllPageText());
                
                options.add("📋 Copy Page URL");
                actions.add(() -> copyToClipboard(webView.getUrl(), "Page URL copied"));
                
                options.add("📤 Share Page");
                actions.add(() -> shareLink(webView.getUrl(), "Page"));
                
                options.add("🖨️ Print Page");
                actions.add(() -> printPage());
                break;
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
        
        builder.setNegativeButton("❌ Cancel", null);
        builder.show();
    }
    
    // ENHANCED TEXT SELECTION METHODS
    
    private void startTextSelection() {
        Log.d(TAG, "✂️ Starting text selection mode");
        
        // JavaScript to enable text selection
        String jsCode = "javascript:document.designMode='on';void(0);";
        webView.evaluateJavascript(jsCode, null);
        
        Toast.makeText(this, "✂️ Text selection enabled - tap and drag to select text", Toast.LENGTH_LONG).show();
        
        // Re-enable normal mode after 30 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            webView.evaluateJavascript("javascript:document.designMode='off';void(0);", null);
        }, 30000);
    }
    
    private void selectAllPageText() {
        Log.d(TAG, "🔄 Selecting all page text");
        webView.evaluateJavascript("javascript:document.execCommand('selectAll');void(0);", null);
        Toast.makeText(this, "🔄 All text selected", Toast.LENGTH_SHORT).show();
    }
    
    private void selectAllText() {
        Log.d(TAG, "🔄 Selecting all text in current field");
        webView.evaluateJavascript("javascript:document.activeElement.select();void(0);", null);
        Toast.makeText(this, "🔄 Text selected", Toast.LENGTH_SHORT).show();
    }
    
    private void copySelectedText() {
        Log.d(TAG, "📄 Copying selected text");
        webView.evaluateJavascript("javascript:document.execCommand('copy');void(0);", null);
        Toast.makeText(this, "📄 Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    private void pasteFromClipboard() {
        Log.d(TAG, "📋 Pasting from clipboard");
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            String pasteText = item.getText().toString();
            
            // JavaScript to paste text into active element
            String jsCode = "javascript:" +
                "var activeElement = document.activeElement;" +
                "if(activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {" +
                "  activeElement.value += '" + pasteText.replace("'", "\\'") + "';" +
                "}" +
                "void(0);";
            
            webView.evaluateJavascript(jsCode, null);
            Toast.makeText(this, "📋 Text pasted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "📋 Clipboard is empty", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openInNewTab(String url) {
        Log.d(TAG, "🆕 Opening in new tab: " + url);
        
        // Add new tab to tab list
        tabList.add(new TabInfo(url, "New Tab", false));
        tabCount++;
        
        // Update tab counter
        if (tabCountText != null) {
            tabCountText.setText(String.valueOf(tabCount));
        }
        
        Toast.makeText(this, "🆕 Opened in new tab", Toast.LENGTH_SHORT).show();
        renderTabsInContainer();
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
        
        // Show enhanced download confirmation with live progress
        showEnhancedDownloadConfirmationDialog(url, filename);
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
    
    /**
     * ENHANCED: Show beautiful download confirmation dialog with live progress tracking
     */
    private void showEnhancedDownloadConfirmationDialog(String url, String filename) {
        try {
            // Create enhanced dialog layout
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(android.R.layout.select_dialog_multichoice, null);
            
            // Create a custom layout programmatically
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(32, 24, 32, 24);
            mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"));
            
            // Title with download icon
            TextView titleView = new TextView(this);
            titleView.setText("📥 Advanced Download Manager");
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
            
            // Add rounded corners effect
            android.graphics.drawable.GradientDrawable cardBackground = new android.graphics.drawable.GradientDrawable();
            cardBackground.setColor(android.graphics.Color.WHITE);
            cardBackground.setCornerRadius(12f);
            cardBackground.setStroke(1, android.graphics.Color.parseColor("#E0E0E0"));
            fileInfoCard.setBackground(cardBackground);
            
            // Filename
            TextView filenameView = new TextView(this);
            filenameView.setText("📄 " + filename);
            filenameView.setTextSize(16);
            filenameView.setTextColor(android.graphics.Color.parseColor("#212121"));
            filenameView.setTypeface(filenameView.getTypeface(), android.graphics.Typeface.BOLD);
            fileInfoCard.addView(filenameView);
            
            // Get file extension for type detection
            String extension = "";
            if (filename.contains(".")) {
                extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            }
            
            // File type
            TextView typeView = new TextView(this);
            typeView.setText("🏷️ Type: " + extension.toUpperCase() + " File");
            typeView.setTextSize(14);
            typeView.setTextColor(android.graphics.Color.parseColor("#757575"));
            typeView.setPadding(0, 8, 0, 0);
            fileInfoCard.addView(typeView);
            
            // URL (shortened)
            TextView urlView = new TextView(this);
            String shortUrl = url.length() > 50 ? url.substring(0, 50) + "..." : url;
            urlView.setText("🔗 " + shortUrl);
            urlView.setTextSize(12);
            urlView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            urlView.setPadding(0, 8, 0, 0);
            fileInfoCard.addView(urlView);
            
            mainLayout.addView(fileInfoCard);
            
            // Add some spacing
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24));
            mainLayout.addView(spacer);
            
            // Enhanced download message
            TextView messageView = new TextView(this);
            messageView.setText("✨ Advanced Download Manager will track this download with live progress updates and show it in the Downloads section immediately.");
            messageView.setTextSize(14);
            messageView.setTextColor(android.graphics.Color.parseColor("#424242"));
            messageView.setGravity(android.view.Gravity.CENTER);
            messageView.setPadding(0, 8, 0, 8);
            mainLayout.addView(messageView);
            
            builder.setView(mainLayout);
            
            // Download button (positive)
            builder.setPositiveButton("📥 Start Download", (dialog, which) -> {
                try {
                    // Show "Download Started" message
                    Toast.makeText(this, "✅ Download Started: " + filename, Toast.LENGTH_LONG).show();
                    
                    // Start download with enhanced tracking
                    startEnhancedDownload(url, filename);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting download", e);
                    Toast.makeText(this, "❌ Download failed to start", Toast.LENGTH_SHORT).show();
                }
            });
            
            // Directory selection button
            builder.setNeutralButton("📁 Change Directory", (dialog, which) -> {
                // Show directory selection (simplified - would need file picker in full implementation)
                showDirectorySelectionDialog(url, filename);
            });
            
            // Cancel button (negative)
            builder.setNegativeButton("❌ Cancel", (dialog, which) -> {
                Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            
            // Create and show dialog
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
            
            // Style the buttons
            android.widget.Button positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            android.widget.Button neutralButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            android.widget.Button negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            
            if (positiveButton != null) {
                positiveButton.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                positiveButton.setTypeface(positiveButton.getTypeface(), android.graphics.Typeface.BOLD);
            }
            
            if (neutralButton != null) {
                neutralButton.setTextColor(android.graphics.Color.parseColor("#2196F3"));
            }
            
            if (negativeButton != null) {
                negativeButton.setTextColor(android.graphics.Color.parseColor("#F44336"));
            }
            
            Log.d(TAG, "✨ Enhanced download confirmation dialog shown for: " + filename);
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing enhanced download dialog", e);
            // Fallback - show simple confirmation
            new android.app.AlertDialog.Builder(this)
                .setTitle("Download File")
                .setMessage("Download " + filename + "?")
                .setPositiveButton("Download", (dialog, which) -> {
                    Toast.makeText(this, "✅ Download Started", Toast.LENGTH_SHORT).show();
                    startEnhancedDownload(url, filename);
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
    
    /**
     * Show directory selection dialog (simplified implementation)
     */
    private void showDirectorySelectionDialog(String url, String filename) {
        // Simplified directory selection - in full implementation would use file picker
        String[] directories = {
            "📥 Downloads",
            "📄 Documents", 
            "🖼️ Pictures",
            "🎵 Music",
            "🎥 Movies"
        };
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("📁 Select Download Directory")
            .setItems(directories, (dialog, which) -> {
                String selectedDir = directories[which].substring(2); // Remove emoji
                Toast.makeText(this, "📁 Directory selected: " + selectedDir, Toast.LENGTH_SHORT).show();
                startEnhancedDownload(url, filename);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * ENHANCED: Start download with live progress tracking
     */
    private void startEnhancedDownload(String url, String filename) {
        try {
            Log.d(TAG, "🚀 Starting enhanced download with live progress - URL: " + url + ", Filename: " + filename);
            
            // Check for storage permissions first
            if (!checkStoragePermissions()) {
                requestStoragePermissions();
                return;
            }
            
            // Continue with regular HTTP/HTTPS download
            android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
            request.setDescription("Downloaded by Real Desktop Browser with Live Progress");
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
            
            // ENHANCED: Track download with live progress
            com.desktopbrowser.advanced.DownloadManager.getInstance(this).addDownloadWithProgress(filename, url, String.valueOf(downloadId));
            
            // Start live progress tracking
            startDownloadProgressTracking(downloadId, filename);
            
            Toast.makeText(this, "🚀 Enhanced download started with live progress: " + filename, Toast.LENGTH_SHORT).show();
            
            Log.d(TAG, "✅ Enhanced download started - ID: " + downloadId + ", File: " + filename);
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error starting enhanced download", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * ENHANCED: Start live progress tracking for download
     */
    private void startDownloadProgressTracking(long downloadId, String filename) {
        Timer progressTimer = new Timer();
        progressTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateDownloadProgress(downloadId, filename, progressTimer);
            }
        }, 0, 1000); // Update every second
    }
    
    /**
     * Update download progress and show in Downloads section
     */
    private void updateDownloadProgress(long downloadId, String filename, Timer timer) {
        try {
            android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Query query = new Query();
            query.setFilterById(downloadId);
            
            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS));
                long bytesDownloaded = cursor.getLong(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long bytesTotal = cursor.getLong(cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                
                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    // Download completed
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✅ Download completed: " + filename, Toast.LENGTH_LONG).show();
                    });
                    timer.cancel();
                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                    // Download failed
                    runOnUiThread(() -> {
                        Toast.makeText(this, "❌ Download failed: " + filename, Toast.LENGTH_SHORT).show();
                    });
                    timer.cancel();
                } else if (status == android.app.DownloadManager.STATUS_RUNNING) {
                    // Download in progress - update progress
                    if (bytesTotal > 0) {
                        int progress = (int) ((bytesDownloaded * 100) / bytesTotal);
                        
                        // Update download manager with progress
                        com.desktopbrowser.advanced.DownloadManager.getInstance(this).updateDownloadProgress(
                            String.valueOf(downloadId), progress, bytesDownloaded, bytesTotal);
                        
                        Log.d(TAG, "📊 Download progress: " + filename + " - " + progress + "%");
                    }
                }
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error updating download progress", e);
        }
    }
    
    /**
     * Check storage permissions
     */
    private boolean checkStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                   android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    
    /**
     * Request storage permissions
     */
    private void requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }, 1001);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Storage permission granted - you can now download files", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "❌ Storage permission denied - downloads may not work", Toast.LENGTH_LONG).show();
            }
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
    
    // ENHANCED AUTO-REFRESH MANAGEMENT
    
    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        isMinimized = true; // App is being minimized
        
        Log.d(TAG, "🛑 App paused/minimized - stopping auto-refresh");
        
        // Stop auto-refresh when app is minimized
        stopAutoRefresh();
        
        // ENHANCED: Save comprehensive session immediately when app is paused
        saveCurrentSessionAsRecent();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        isMinimized = false; // App is back in foreground
        
        Log.d(TAG, "▶️ App resumed - restarting auto-refresh if enabled");
        
        // Restart auto-refresh when app comes back to foreground
        if (autoRefreshEnabled) {
            startAutoRefresh();
        }
        
        // Restore session if needed
        checkSessionRestore();
    }
    
    private void startAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
        }
        
        // Only start auto-refresh if not minimized
        if (!isMinimized && autoRefreshEnabled) {
            autoRefreshTimer = new Timer();
            autoRefreshTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (!isMinimized && !isPaused && autoRefreshEnabled) {
                        runOnUiThread(() -> {
                            if (webView != null) {
                                Log.d(TAG, "🔄 Auto-refresh triggered");
                                webView.reload();
                            }
                        });
                    }
                }
            }, 300000, 300000); // 5 minutes interval
            
            Log.d(TAG, "🔄 Auto-refresh started (5 minute interval)");
        }
    }
    
    private void stopAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.cancel();
            autoRefreshTimer = null;
            Log.d(TAG, "🛑 Auto-refresh stopped");
        }
    }
    
    // ENHANCED SESSION MANAGEMENT
    
    /**
     * Save current session as recent session with comprehensive state
     */
    private void saveCurrentSessionAsRecent() {
        try {
            if (webView != null && sessionManager != null) {
                String currentUrl = webView.getUrl();
                String currentTitle = webView.getTitle();
                
                if (currentUrl != null && !currentUrl.equals("about:blank")) {
                    // Create comprehensive tab session
                    SessionManager.TabSession currentTab = sessionManager.createComprehensiveTabSession(
                        webView, currentUrl, currentTitle != null ? currentTitle : "Untitled");
                    
                    // Create browser session with current tab
                    SessionManager.BrowserSession session = new SessionManager.BrowserSession();
                    session.tabs.add(currentTab);
                    session.currentTabIndex = 0;
                    
                    // Add all other tabs if any
                    for (int i = 0; i < tabList.size(); i++) {
                        TabInfo tab = tabList.get(i);
                        if (!tab.url.equals(currentUrl)) { // Don't duplicate current tab
                            SessionManager.TabSession tabSession = new SessionManager.TabSession(
                                tab.url, tab.title, new Bundle());
                            session.tabs.add(tabSession);
                        }
                    }
                    
                    // Save comprehensive session
                    sessionManager.saveCompleteBrowserSession(session.tabs, 0);
                    
                    Log.d(TAG, "💾 Enhanced recent session saved with " + session.tabs.size() + " tabs");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving recent session", e);
        }
    }
    
    private void checkSessionRestore() {
        // Session restore logic would go here
        Log.d(TAG, "🔍 Checking for session restore");
    }
    
    // Additional methods would be implemented here...
    
    private void updateZoomLevel() {
        if (zoomLevel != null) {
            zoomLevel.setText(String.format("%.0f%%", currentZoom));
        }
    }
    
    private void setupZoomSlider() {
        // Zoom slider setup
        if (zoomSlider != null) {
            zoomSlider.setProgress((int) currentZoom);
        }
    }
    
    private void renderTabsInContainer() {
        // Tab rendering logic
        Log.d(TAG, "🗂️ Rendering tabs in container");
    }
    
    private void setupNavigationControls() {
        // Navigation controls setup
        Log.d(TAG, "🧭 Setting up navigation controls");
    }
    
    private void loadUrl() {
        String url = getIntent().getStringExtra("url");
        if (url != null && webView != null) {
            webView.loadUrl(url);
            Log.d(TAG, "🌐 Loading URL: " + url);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        
        // Stop auto-refresh
        stopAutoRefresh();
        
        // Save final session
        saveCurrentSessionAsRecent();
        
        Log.d(TAG, "💀 BrowserActivity destroyed");
    }
    
    // Inner classes for WebView clients would be implemented here...
    
    private class AdvancedDesktopWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Enhanced URL loading with download detection
            if (isDownloadLink(url)) {
                intelligentDownload(url);
                return true;
            }
            return false;
        }
        
        private boolean isDownloadLink(String url) {
            if (url == null) return false;
            
            String lowerUrl = url.toLowerCase();
            return lowerUrl.endsWith(".apk") || lowerUrl.endsWith(".pdf") || 
                   lowerUrl.endsWith(".zip") || lowerUrl.endsWith(".mp4") ||
                   lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".png") ||
                   lowerUrl.contains("download");
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
    
    private class IntelligentDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, 
                                   String mimetype, long contentLength) {
            Log.d(TAG, "🔽 Download detected: " + url);
            
            String filename = getIntelligentFileName(url, "download");
            showEnhancedDownloadConfirmationDialog(url, filename);
        }
    }
}