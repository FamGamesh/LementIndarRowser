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

        // ENHANCED: Auto-refresh management with minimization detection
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
        if (tabCounterView != null) {
            tabCountText = tabCounterView.findViewById(R.id.tab_count_text);
        }

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

        // ENHANCED: INTELLIGENT LONG PRESS CONTEXT MENU WITH TEXT SELECTION
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

    // ENHANCED: INTELLIGENT LONG PRESS CONTEXT MENU SETUP WITH TEXT SELECTION
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

    // ENHANCED: Context menu with intelligent text selection and link handling
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

    // ENHANCED: Text selection methods
    private void startTextSelection() {
        Log.d(TAG, "✂️ Starting text selection mode");
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
        tabList.add(new TabInfo(url, "New Tab", false));
        tabCount++;
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

        // Start enhanced download with live progress
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
        if (url != null) {
            String linkText = url.replaceAll("https?://", "").replaceAll("/", " > ");
            copyToClipboard(linkText, "Link text copied");
        }
    }

    private String getIntelligentFileName(String url, String defaultName) {
        if (url == null) return defaultName;

        try {
            String filename = url.substring(url.lastIndexOf('/') + 1);
            if (filename.contains("?")) {
                filename = filename.substring(0, filename.indexOf("?"));
            }
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            android.print.PrintManager printManager = (android.print.PrintManager) getSystemService(Context.PRINT_SERVICE);
            String jobName = "Real Desktop Browser - " + (webView.getTitle() != null ? webView.getTitle() : "Page");
            android.print.PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
            printManager.print(jobName, printAdapter, new android.print.PrintAttributes.Builder().build());
        } else {
            Toast.makeText(this, "Print requires Android 4.4 or higher", Toast.LENGTH_SHORT).show();
        }
    }

    // ENHANCED: Download confirmation dialog with live progress tracking
    private void showEnhancedDownloadConfirmationDialog(String url, String filename) {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(32, 24, 32, 24);
            mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"));
            
            TextView titleView = new TextView(this);
            titleView.setText("📥 Advanced Download Manager");
            titleView.setTextSize(20);
            titleView.setTextColor(android.graphics.Color.parseColor("#1976D2"));
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
            titleView.setGravity(android.view.Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 16);
            mainLayout.addView(titleView);
            
            LinearLayout fileInfoCard = new LinearLayout(this);
            fileInfoCard.setOrientation(LinearLayout.VERTICAL);
            fileInfoCard.setBackgroundColor(android.graphics.Color.WHITE);
            fileInfoCard.setPadding(20, 16, 20, 16);
            
            android.graphics.drawable.GradientDrawable cardBackground = new android.graphics.drawable.GradientDrawable();
            cardBackground.setColor(android.graphics.Color.WHITE);
            cardBackground.setCornerRadius(12f);
            cardBackground.setStroke(1, android.graphics.Color.parseColor("#E0E0E0"));
            fileInfoCard.setBackground(cardBackground);
            
            TextView filenameView = new TextView(this);
            filenameView.setText("📄 " + filename);
            filenameView.setTextSize(16);
            filenameView.setTextColor(android.graphics.Color.parseColor("#212121"));
            filenameView.setTypeface(filenameView.getTypeface(), android.graphics.Typeface.BOLD);
            fileInfoCard.addView(filenameView);
            
            String extension = "";
            if (filename.contains(".")) {
                extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            }
            
            TextView typeView = new TextView(this);
            typeView.setText("🏷️ Type: " + extension.toUpperCase() + " File");
            typeView.setTextSize(14);
            typeView.setTextColor(android.graphics.Color.parseColor("#757575"));
            typeView.setPadding(0, 8, 0, 0);
            fileInfoCard.addView(typeView);
            
            TextView urlView = new TextView(this);
            String shortUrl = url.length() > 50 ? url.substring(0, 50) + "..." : url;
            urlView.setText("🔗 " + shortUrl);
            urlView.setTextSize(12);
            urlView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
            urlView.setPadding(0, 8, 0, 0);
            fileInfoCard.addView(urlView);
            
            mainLayout.addView(fileInfoCard);
            
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24));
            mainLayout.addView(spacer);
            
            TextView messageView = new TextView(this);
            messageView.setText("✨ Download will be tracked with live progress and shown in Downloads section immediately.");
            messageView.setTextSize(14);
            messageView.setTextColor(android.graphics.Color.parseColor("#424242"));
            messageView.setGravity(android.view.Gravity.CENTER);
            messageView.setPadding(0, 8, 0, 8);
            mainLayout.addView(messageView);
            
            builder.setView(mainLayout);
            
            builder.setPositiveButton("📥 Start Download", (dialog, which) -> {
                try {
                    Toast.makeText(this, "✅ Download Started: " + filename, Toast.LENGTH_LONG).show();
                    startEnhancedDownload(url, filename);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting download", e);
                    Toast.makeText(this, "❌ Download failed to start", Toast.LENGTH_SHORT).show();
                }
            });
            
            builder.setNegativeButton("❌ Cancel", (dialog, which) -> {
                Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            
            android.app.AlertDialog dialog = builder.create();
            dialog.show();
            
            Log.d(TAG, "✨ Enhanced download confirmation dialog shown for: " + filename);
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing enhanced download dialog", e);
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

    // ENHANCED: Start download with live progress tracking
    private void startEnhancedDownload(String url, String filename) {
        try {
            Log.d(TAG, "🚀 Starting enhanced download with live progress - URL: " + url + ", Filename: " + filename);
            
            android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
            request.setDescription("Downloaded by Real Desktop Browser with Live Progress");
            request.setTitle(filename);
            
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI | android.app.DownloadManager.Request.NETWORK_MOBILE);
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

    // ENHANCED: Start live progress tracking for download
    private void startDownloadProgressTracking(long downloadId, String filename) {
        Timer progressTimer = new Timer();
        progressTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateDownloadProgress(downloadId, filename, progressTimer);
            }
        }, 0, 1000); // Update every second
    }

    // Update download progress and show in Downloads section
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
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✅ Download completed: " + filename, Toast.LENGTH_LONG).show();
                    });
                    timer.cancel();
                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "❌ Download failed: " + filename, Toast.LENGTH_SHORT).show();
                    });
                    timer.cancel();
                } else if (status == android.app.DownloadManager.STATUS_RUNNING) {
                    if (bytesTotal > 0) {
                        int progress = (int) ((bytesDownloaded * 100) / bytesTotal);
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
                "  Object.defineProperty(navigator, 'maxTouchPoints', { value: 0, writable: false, configurable: false });" +
                "  Object.defineProperty(navigator, 'platform', { value: 'Win32', writable: false, configurable: false });" +
                "  " +
                "  // Immediate CSS override for desktop detection" +
                "  const originalMatchMedia = window.matchMedia;" +
                "  window.matchMedia = function(query) {" +
                "    const lowerQuery = query.toLowerCase();" +
                "    if (lowerQuery.includes('hover') && lowerQuery.includes('hover')) return { matches: true, media: query, addListener: function(){}, removeListener: function(){} };" +
                "    if (lowerQuery.includes('pointer') && lowerQuery.includes('coarse')) return { matches: false, media: query, addListener: function(){}, removeListener: function(){} };" +
                "    if (lowerQuery.includes('pointer') && lowerQuery.includes('fine')) return { matches: true, media: query, addListener: function(){}, removeListener: function(){} };" +
                "    if (lowerQuery.includes('touch')) return { matches: false, media: query, addListener: function(){}, removeListener: function(){} };" +
                "    return originalMatchMedia.call(window, query);" +
                "  };" +
                "  " +
                "  console.log('⚡ Immediate stealth mode activated');" +
                "})();";

            webView.evaluateJavascript(immediateScript, null);
            Log.d(TAG, "⚡ Immediate stealth script injected for instant desktop mode");

        } catch (Exception e) {
            Log.w(TAG, "Could not inject immediate stealth script", e);
        }
    }

    private class AdvancedDesktopWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            // Inject immediate stealth script as soon as page starts loading
            injectImmediateStealthScript();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            Log.d(TAG, "🎯 Page finished loading: " + url);

            // Wait a moment for page to stabilize, then inject comprehensive script
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                injectAdvancedDesktopScript();
            }, 500);

            // Update navigation state
            updateNavigationButtons();

            // Add to history
            String title = view.getTitle();
            if (historyManager != null && url != null) {
                historyManager.addHistoryItem(url, title != null ? title : "Untitled");
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Intelligent download detection
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
                   lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".doc") ||
                   lowerUrl.endsWith(".docx") || lowerUrl.endsWith(".xls") ||
                   lowerUrl.endsWith(".xlsx") || lowerUrl.endsWith(".ppt") ||
                   lowerUrl.endsWith(".pptx") || lowerUrl.endsWith(".exe") ||
                   lowerUrl.contains("download") || lowerUrl.contains("attachment");
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

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            Log.d(TAG, "📑 Page title received: " + title);
        }
    }

    private class IntelligentDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                   String mimetype, long contentLength) {
            try {
                Log.d(TAG, "🔽 Download detected via listener: " + url);
                Log.d(TAG, "📄 Content-Disposition: " + contentDisposition);
                Log.d(TAG, "🏷️ MIME type: " + mimetype);
                Log.d(TAG, "📊 Content length: " + contentLength);

                // Extract intelligent filename
                String filename = "download";

                // Try to extract filename from content-disposition header
                if (contentDisposition != null && contentDisposition.contains("filename")) {
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

                // Fallback to URL-based filename
                if (filename.equals("download")) {
                    filename = getIntelligentFileName(url, "download_" + System.currentTimeMillis());
                }

                Log.d(TAG, "📁 Final filename: " + filename);

                // Show beautiful confirmation dialog before downloading
                String finalFilename = filename;
                showEnhancedDownloadConfirmationDialog(url, finalFilename);

            } catch (Exception e) {
                Log.e(TAG, "💥 Error in intelligent download listener", e);
                Toast.makeText(BrowserActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateNavigationButtons() {
        // Navigation buttons have been moved to the three-lines menu system
        // Check if buttons exist before trying to update them (for backwards compatibility)
        if (backButton != null) {
            backButton.setEnabled(webView.canGoBack());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(webView.canGoForward());
        }
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
        
        if (isDesktopMode) {
            enableAdvancedDesktopMode();
            Toast.makeText(this, "Desktop mode enabled", Toast.LENGTH_SHORT).show();
        } else {
            // Switch to mobile mode
            WebSettings webSettings = webView.getSettings();
            webSettings.setUserAgentString(null); // Reset to default mobile user agent
            Toast.makeText(this, "Mobile mode enabled", Toast.LENGTH_SHORT).show();
        }
        
        // Reload current page to apply changes
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

    // ENHANCED: Auto-refresh management - FIXED to pause when minimized
    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        isMinimized = true; // App is being minimized

        Log.d(TAG, "🛑 App paused/minimized - stopping auto-refresh");

        // ENHANCED: Stop auto-refresh when app is minimized
        stopAutoRefresh();

        // ENHANCED: Save comprehensive session immediately when app is paused
        saveCurrentSessionAsRecent();

        try {
            if (webView != null) {
                // Pause WebView properly to prevent freezing
                webView.onPause();
                webView.pauseTimers();
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
        isMinimized = false; // App is back in foreground

        Log.d(TAG, "▶️ App resumed - restarting auto-refresh if enabled");

        // ENHANCED: Restart auto-refresh when app comes back to foreground
        if (autoRefreshEnabled) {
            startAutoRefresh();
        }

        try {
            if (webView != null && !isDestroyed) {
                // Resume WebView properly
                webView.onResume();
                webView.resumeTimers();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during resume", e);
        }
    }

    // ENHANCED: Auto-refresh methods
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
                            if (webView != null && !isDestroyed) {
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

    // ENHANCED: Session management - save current session as recent
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

    @Override
    protected void onDestroy() {
        isDestroyed = true;

        try {
            Log.d(TAG, "BrowserActivity onDestroy - comprehensive cleanup");

            // ENHANCED: Stop auto-refresh
            stopAutoRefresh();

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

    private void loadNewUrl(String url) {
        if (webView != null && url != null && !url.trim().isEmpty()) {
            // Add to history
            if (historyManager != null) {
                historyManager.addHistoryItem(url, "Loading...");
            }

            // Add to URL stack
            if (!urlStack.contains(url)) {
                urlStack.add(0, url);
                // Keep only last 50 URLs
                if (urlStack.size() > 50) {
                    urlStack = urlStack.subList(0, 50);
                }
            }

            // Load URL in WebView
            webView.loadUrl(url);

            Log.d(TAG, "🌐 Loading URL: " + url);
        }
    }

    private void loadUrl() {
        String url = getIntent().getStringExtra("url");
        if (url != null && !url.trim().isEmpty()) {
            loadNewUrl(url);
        } else {
            // Load default page
            loadNewUrl("https://www.google.com");
        }
    }

    private void updateZoomLevel() {
        if (zoomLevel != null) {
            zoomLevel.setText(String.format("%.0f%%", currentZoom));
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

            // ENHANCED: Mark tab as closed in session management before removing
            TabInfo tabToClose = tabList.get(index);
            if (tabToClose.url != null) {
                sessionManager.markTabAsClosed(tabToClose.url);
                Log.d(TAG, "Marked tab as closed in session management: " + tabToClose.url);
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

            // ENHANCED: Save updated session after tab closure
            try {
                saveCurrentSessionAsRecent();
                Log.d(TAG, "Session updated after tab closure");
            } catch (Exception sessionError) {
                Log.e(TAG, "Error saving session after tab closure", sessionError);
            }

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

    private void applySafeZoom() {
        try {
            if (webView != null && !isDestroyed) {
                webView.setInitialScale((int) currentZoom);
                updateZoomLevel();

                // Update zoom slider if available
                if (zoomSlider != null) {
                    zoomSlider.setProgress((int) currentZoom);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying zoom", e);
        }
    }

    private void safeDebouncedZoom(Runnable zoomAction) {
        if (zoomHandler != null) {
            if (pendingZoomRunnable != null) {
                zoomHandler.removeCallbacks(pendingZoomRunnable);
            }

            pendingZoomRunnable = zoomAction;
            zoomHandler.postDelayed(pendingZoomRunnable, ZOOM_DEBOUNCE);
        }
    }

    private void setupNavigationControls() {
        // Setup browser menu button
        if (browserMenuButton != null) {
            browserMenuButton.setOnClickListener(v -> showBrowserMenu());
        }

        // Setup zoom controls
        if (zoomInButton != null) {
            zoomInButton.setOnClickListener(v -> zoomIn());
        }

        if (zoomOutButton != null) {
            zoomOutButton.setOnClickListener(v -> zoomOut());
        }

        // Setup tab controls
        if (newTabButton != null) {
            newTabButton.setOnClickListener(v -> createNewTab());
        }

        if (showUrlStackButton != null) {
            showUrlStackButton.setOnClickListener(v -> showUrlStackDialog());
        }

        if (tabCounterView != null) {
            tabCounterView.setOnClickListener(v -> showTabSwitcher());
        }
    }

    private void showBrowserMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, browserMenuButton);
        popup.getMenuInflater().inflate(R.menu.browser_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
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
            return false;
        });

        popup.show();
    }

    private void zoomIn() {
        currentZoom = Math.min(currentZoom + 10, 200); // Max 200%
        applySafeZoom();
    }

    private void zoomOut() {
        currentZoom = Math.max(currentZoom - 10, 25); // Min 25%
        applySafeZoom();
    }

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
