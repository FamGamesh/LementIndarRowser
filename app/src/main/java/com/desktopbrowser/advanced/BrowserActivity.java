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
        private android.widget.SeekBar zoomSlider;
        private java.util.List<String> urlStack;
        private long lastInterstitialTime = 0;
        
        // Tab management
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
        
        // Initialize with first tab
        String initialUrl = getIntent().getStringExtra("url");
        if (initialUrl != null) {
            tabList.add(new TabInfo(initialUrl, "New Tab", true));
        }
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
        
        // New enhanced features
        tabsContainer = findViewById(R.id.tabs_container);
        zoomControlsContainer = findViewById(R.id.zoom_controls_container);
        showUrlStackButton = findViewById(R.id.btn_show_url_stack);
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
        webView.setDownloadListener(new AdvancedDownloadListener());
        
        // Custom zoom and scroll setup
        setupCustomZoomControls();
        setupCustomScrolling();
    }
    
    private void enableAdvancedDesktopMode() {
        WebSettings webSettings = webView.getSettings();
        
        // Advanced Desktop User Agent - completely undetectable
        String desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        webSettings.setUserAgentString(desktopUserAgent);
        
        // Advanced stealth settings
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        
        // Disable mobile-specific features
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(false);
        }
        
        // Force desktop viewport with comprehensive injection
        String stealthScript = 
            "javascript:(function() {" +
            "  // Remove existing viewport meta" +
            "  var existing = document.querySelector('meta[name=\"viewport\"]');" +
            "  if (existing) existing.remove();" +
            "  " +
            "  // Inject desktop viewport" +
            "  var meta = document.createElement('meta');" +
            "  meta.name = 'viewport';" +
            "  meta.content = 'width=1366, initial-scale=0.65, maximum-scale=3.0, user-scalable=yes';" +
            "  document.head.appendChild(meta);" +
            "  " +
            "  // Anti-detection CSS injection" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = '" +
            "    @media (max-width: 1365px) { body { min-width: 1366px !important; } }" +
            "    * { -webkit-text-size-adjust: 100% !important; -webkit-touch-callout: none !important; }" +
            "    body { zoom: 1 !important; min-width: 1366px !important; cursor: default !important; }" +
            "    html { -ms-touch-action: none !important; touch-action: none !important; }" +
            "    ::-webkit-scrollbar { width: 12px; height: 12px; }" +
            "    ::-webkit-scrollbar-track { background: #f1f1f1; }" +
            "    ::-webkit-scrollbar-thumb { background: #c1c1c1; border-radius: 6px; }" +
            "  ';" +
            "  document.head.appendChild(style);" +
            "  " +
            "  // Remove mobile detection classes" +
            "  document.documentElement.classList.remove('mobile', 'touch', 'android', 'phone', 'tablet');" +
            "  document.documentElement.classList.add('desktop', 'no-touch', 'windows', 'chrome');" +
            "  if (document.body) {" +
            "    document.body.classList.remove('mobile', 'touch', 'android', 'phone', 'tablet');" +
            "    document.body.classList.add('desktop', 'no-touch', 'windows', 'chrome');" +
            "  }" +
            "  " +
            "  // Override touch event handlers globally" +
            "  ['touchstart', 'touchend', 'touchmove', 'touchcancel'].forEach(function(event) {" +
            "    document.addEventListener(event, function(e) { e.stopImmediatePropagation(); }, true);" +
            "  });" +
            "})()";
        
        webView.loadUrl(stealthScript);
        
        // Inject comprehensive anti-detection immediately
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            injectAdvancedDesktopScript();
        }, 100);
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
            "  // === CRITICAL: DISABLE ALL TOUCH DETECTION ===" +
            "  Object.defineProperty(navigator, 'maxTouchPoints', { value: 0, writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'msMaxTouchPoints', { value: 0, writable: false, configurable: false });" +
            "  " +
            "  // Override touch capability detection" +
            "  if ('ontouchstart' in window) {" +
            "    delete window.ontouchstart;" +
            "  }" +
            "  if ('ontouchend' in window) {" +
            "    delete window.ontouchend;" +
            "  }" +
            "  if ('ontouchmove' in window) {" +
            "    delete window.ontouchmove;" +
            "  }" +
            "  " +
            "  // Override DocumentTouch completely" +
            "  if (typeof DocumentTouch !== 'undefined') {" +
            "    window.DocumentTouch = undefined;" +
            "  }" +
            "  " +
            "  // Override touch event creation" +
            "  const originalCreateEvent = document.createEvent;" +
            "  document.createEvent = function(eventType) {" +
            "    if (eventType.toLowerCase().includes('touch')) {" +
            "      throw new Error('TouchEvent not supported');" +
            "    }" +
            "    return originalCreateEvent.call(document, eventType);" +
            "  };" +
            "  " +
            "  // === NAVIGATOR PROPERTIES - COMPLETE WINDOWS DESKTOP SIMULATION ===" +
            "  Object.defineProperty(navigator, 'platform', { value: 'Win32', writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'oscpu', { value: 'Windows NT 10.0; Win64; x64', writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'hardwareConcurrency', { value: 8, writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'deviceMemory', { value: 8, writable: false, configurable: false });" +
            "  Object.defineProperty(navigator, 'cpuClass', { value: 'x86', writable: false, configurable: false });" +
            "  " +
            "  // Override mobile-specific properties" +
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
        
        // Advanced Desktop Zoom Controls
        zoomInButton.setOnClickListener(v -> {
            currentZoom = Math.min(currentZoom + 10, 200); // Max 200%
            applyZoom();
            showZoomControls();
        });
        
        zoomOutButton.setOnClickListener(v -> {
            currentZoom = Math.max(currentZoom - 10, 25); // Min 25%
            applyZoom();
            showZoomControls();
        });
        
        // Show URL Stack button
        if (showUrlStackButton != null) {
            showUrlStackButton.setOnClickListener(v -> showUrlStackDialog());
        }
        
        // Tab counter click to show tab switcher  
        if (tabCounterView != null) {
            tabCounterView.setOnClickListener(v -> showTabSwitcher());
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
    
    private void applyZoom() {
        // Apply zoom with desktop-optimized scaling
        float zoomFactor = currentZoom / 100.0f;
        webView.setScaleX(zoomFactor);
        webView.setScaleY(zoomFactor);
        
        // Update zoom level display
        updateZoomLevel();
        
        // Update zoom slider
        if (zoomSlider != null) {
            zoomSlider.setProgress((int) currentZoom);
        }
        
        // Show zoom controls when zooming
        showZoomControls();
        hideZoomControlsDelayed();
        
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
        try {
            if (webView != null) {
                // Save session before destroying (for app close recovery)
                saveCurrentSessionAsLast();
                
                // Proper WebView cleanup to prevent memory leaks
                webView.clearHistory();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.onPause();
                webView.removeAllViews();
                webView.destroyDrawingCache();
                webView.pauseTimers();
                webView.destroy();
                webView = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
        super.onDestroy();
    }
    
    @Override 
    protected void onPause() {
        super.onPause();
        try {
            if (webView != null) {
                webView.onPause();
                webView.pauseTimers();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during pause", e);
        }
    }
    
    // ==================== NEW ENHANCED FEATURES ====================
    
    private void setupZoomSlider() {
        if (zoomSlider != null) {
            zoomSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        currentZoom = Math.max(progress, 25); // Min 25%
                        applyZoom();
                        showZoomControls();
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
        SessionManager.BrowserSession session = new SessionManager.BrowserSession();
        String currentUrl = webView.getUrl();
        String currentTitle = webView.getTitle();
        
        if (currentUrl != null) {
            SessionManager.TabSession tabSession = sessionManager.createTabSession(webView, currentUrl, currentTitle);
            session.tabs.add(tabSession);
            sessionManager.saveRecentSession(session);
        }
    }
    
    private void saveCurrentSessionAsLast() {
        SessionManager.BrowserSession session = new SessionManager.BrowserSession();
        String currentUrl = webView.getUrl();
        String currentTitle = webView.getTitle();
        
        if (currentUrl != null) {
            SessionManager.TabSession tabSession = sessionManager.createTabSession(webView, currentUrl, currentTitle);
            session.tabs.add(tabSession);
            sessionManager.saveLastSession(session);
        }
    }
    
    private void restoreLastSession() {
        SessionManager.BrowserSession session = sessionManager.getLastSession();
        if (session != null && !session.tabs.isEmpty()) {
            SessionManager.TabSession firstTab = session.tabs.get(0);
            sessionManager.restoreWebView(webView, firstTab);
            Toast.makeText(this, "Last session restored", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void restoreRecentSession() {
        SessionManager.BrowserSession session = sessionManager.getRecentSession();
        if (session != null && !session.tabs.isEmpty()) {
            SessionManager.TabSession firstTab = session.tabs.get(0);
            if (firstTab.url != null && !firstTab.url.isEmpty()) {
                sessionManager.restoreWebView(webView, firstTab);
                Toast.makeText(this, "Recent session restored", Toast.LENGTH_SHORT).show();
            } else {
                // Fallback to Google if no valid URL
                loadNewUrl("https://www.google.com");
                Toast.makeText(this, "Session restored with default page", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Fallback to Google if no session data
            loadNewUrl("https://www.google.com");
            Toast.makeText(this, "No recent session found, loading default page", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showZoomControls() {
        if (zoomControlsContainer != null) {
            zoomControlsContainer.setVisibility(View.VISIBLE);
            zoomControlsContainer.animate().alpha(1.0f).setDuration(200);
        }
    }
    
    private void hideZoomControlsDelayed() {
        if (zoomControlsContainer != null) {
            zoomControlsContainer.postDelayed(() -> {
                zoomControlsContainer.animate().alpha(0.0f).setDuration(200)
                    .withEndAction(() -> zoomControlsContainer.setVisibility(View.GONE));
            }, 3000); // Hide after 3 seconds
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Check for 15-minute interstitial ad rule
        if (adManager.canShowInterstitial()) {
            adManager.showBrowsingInterstitial(this);
        }
    }
    
    private void showUrlStackDialog() {
        if (urlStack.isEmpty()) {
            Toast.makeText(this, "No URL history in current session", Toast.LENGTH_SHORT).show();
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("URL Stack - Session History");
        
        String[] urls = urlStack.toArray(new String[0]);
        builder.setItems(urls, (dialog, which) -> {
            String selectedUrl = urls[which];
            loadNewUrl(selectedUrl);
            Toast.makeText(this, "Loading: " + selectedUrl, Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Close", null);
        builder.show();
    }
    
    private void createNewTab() {
        // Add new tab to list
        String newTabUrl = "https://www.google.com";
        tabList.add(new TabInfo(newTabUrl, "Google", false));
        tabCount = tabList.size();
        
        // Update counter
        updateTabCounter();
        
        // Load URL in current WebView (simplified implementation)
        loadNewUrl(newTabUrl);
        Toast.makeText(this, "New tab created (" + tabCount + " tabs)", Toast.LENGTH_SHORT).show();
    }
    
    private void updateTabCounter() {
        if (tabCountText != null) {
            tabCountText.setText(String.valueOf(tabCount));
        }
    }
    
    private void showTabSwitcher() {
        if (tabList.isEmpty()) {
            Toast.makeText(this, "No tabs available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Inflate tab switcher layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_tab_switcher, null);
        builder.setView(dialogView);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Set transparent background for custom styling
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Handle buttons
        Button newTabButton = dialogView.findViewById(R.id.btn_new_tab_dialog);
        Button closeButton = dialogView.findViewById(R.id.btn_close_tab_switcher);
        
        newTabButton.setOnClickListener(v -> {
            dialog.dismiss();
            createNewTab();
        });
        
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        // Create tab list dynamically
        androidx.recyclerview.widget.RecyclerView tabsRecyclerView = dialogView.findViewById(R.id.tabs_recycler_view);
        
        // Simple implementation - create LinearLayout with tab items
        LinearLayout tabListContainer = new LinearLayout(this);
        tabListContainer.setOrientation(LinearLayout.VERTICAL);
        
        for (int i = 0; i < tabList.size(); i++) {
            TabInfo tab = tabList.get(i);
            
            TextView tabItem = new TextView(this);
            String tabText = (i + 1) + ". " + (tab.title != null ? tab.title : "Tab") + 
                           (tab.isActive ? " (Active)" : "");
            tabItem.setText(tabText);
            tabItem.setTextColor(getResources().getColor(android.R.color.white));
            tabItem.setTextSize(16);
            tabItem.setPadding(16, 12, 16, 12);
            tabItem.setBackground(getResources().getDrawable(R.drawable.button_background));
            tabItem.setClickable(true);
            
            // Set margin
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            tabItem.setLayoutParams(params);
            
            // Handle tab click
            final int tabIndex = i;
            tabItem.setOnClickListener(v -> {
                dialog.dismiss();
                switchToTab(tabIndex);
            });
            
            tabListContainer.addView(tabItem);
        }
        
        // Replace RecyclerView with LinearLayout
        ((LinearLayout) tabsRecyclerView.getParent()).removeView(tabsRecyclerView);
        ((LinearLayout) dialogView).addView(tabListContainer, 1);
        
        dialog.show();
    }
    
    private void switchToTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabList.size()) {
            // Mark all tabs as inactive
            for (TabInfo tab : tabList) {
                tab.isActive = false;
            }
            
            // Mark selected tab as active
            TabInfo selectedTab = tabList.get(tabIndex);
            selectedTab.isActive = true;
            
            // Load the tab's URL
            if (selectedTab.url != null && !selectedTab.url.isEmpty()) {
                loadNewUrl(selectedTab.url);
                Toast.makeText(this, "Switched to: " + selectedTab.title, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void expandAddressBar() {
        // Hide navigation buttons when address bar is focused
        if (backButton != null) backButton.setVisibility(View.GONE);
        if (forwardButton != null) forwardButton.setVisibility(View.GONE);
        if (refreshButton != null) refreshButton.setVisibility(View.GONE);
        if (homeButton != null) homeButton.setVisibility(View.GONE);
        if (desktopModeButton != null) desktopModeButton.setVisibility(View.GONE);
        
        // Animate address bar expansion
        addressBar.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(200)
            .start();
    }
    
    private void collapseAddressBar() {
        // Show navigation buttons when address bar loses focus
        if (backButton != null) backButton.setVisibility(View.VISIBLE);
        if (forwardButton != null) forwardButton.setVisibility(View.VISIBLE);
        if (refreshButton != null) refreshButton.setVisibility(View.VISIBLE);
        if (homeButton != null) homeButton.setVisibility(View.VISIBLE);
        if (desktopModeButton != null) desktopModeButton.setVisibility(View.VISIBLE);
        
        // Animate address bar collapse
        addressBar.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .start();
    }
    
    private void updateCurrentTabInfo(String url, String title) {
        // Update the active tab's information
        for (TabInfo tab : tabList) {
            if (tab.isActive) {
                tab.url = url;
                tab.title = title != null ? title : "Loading...";
                break;
            }
        }
    }
}