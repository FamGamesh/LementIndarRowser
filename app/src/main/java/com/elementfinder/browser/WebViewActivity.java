package com.elementfinder.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

public class WebViewActivity extends AppCompatActivity {
    
    private static final String TAG = "WebViewActivity";
    private WebView webView;
    private boolean isRecordingMacro = false;
    private List<String> recordedSelectors = new ArrayList<>();
    private List<String> recordedElementNames = new ArrayList<>();
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private boolean isLongPressTriggered = false;
    
    // UI elements for macro recording
    private LinearLayout macroControlPanel;
    private Button stopMacroButton;
    private Button viewSavedFilesButton;
    private Chronometer macroTimer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_webview);
            
            setupToolbar();
            setupMacroControls();
            initializeWebView();
            loadUrl();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void setupToolbar() {
        try {
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Element Finder Browser");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up toolbar", e);
        }
    }
    
    private void setupMacroControls() {
        macroControlPanel = findViewById(R.id.macro_control_panel);
        stopMacroButton = findViewById(R.id.stop_macro_button);
        viewSavedFilesButton = findViewById(R.id.view_saved_files_button);
        macroTimer = findViewById(R.id.macro_timer);
        
        stopMacroButton.setOnClickListener(v -> stopMacroRecording());
        viewSavedFilesButton.setOnClickListener(v -> showSavedFiles());
        
        // Initially hide macro controls
        macroControlPanel.setVisibility(View.GONE);
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView() {
        try {
            webView = findViewById(R.id.webview);
            
            if (webView == null) {
                throw new RuntimeException("WebView not found in layout");
            }
            
            WebSettings webSettings = webView.getSettings();
            
            // Enable JavaScript and DOM storage
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setDatabaseEnabled(true);
            webSettings.setAppCacheEnabled(true);
            
            // Advanced stealth settings
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);
            webSettings.setSupportZoom(true);
            webSettings.setDefaultTextEncodingName("utf-8");
            webSettings.setAllowFileAccess(false);
            webSettings.setAllowContentAccess(false);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            
            // Advanced stealth features
            webSettings.setSupportMultipleWindows(true);
            webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
            webSettings.setGeolocationEnabled(false);
            webSettings.setNeedInitialFocus(false);
            webSettings.setSaveFormData(false);
            webSettings.setSavePassword(false);
            
            // Set realistic Chrome User-Agent with latest version
            String chromeUserAgent = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
            webSettings.setUserAgentString(chromeUserAgent);
            
            // Enable advanced features
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                webSettings.setSafeBrowsingEnabled(false);
            }
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    Log.d(TAG, "Page started loading: " + url);
                }
                
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "Page finished loading: " + url);
                    
                    // Inject stealth and selector scripts with multiple attempts
                    injectStealthScript();
                    
                    // Wait a bit then inject selector scripts
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        injectSelectorScript();
                        
                        // Verify injection after another delay
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            verifyScriptInjection();
                            runOnUiThread(() -> {
                                Toast.makeText(WebViewActivity.this, "Page loaded. Long press elements to view selectors!", Toast.LENGTH_LONG).show();
                            });
                        }, 800);
                    }, 1200);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
                    Toast.makeText(WebViewActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT).show();
                }
                
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                    // Add stealth headers to all requests
                    return addStealthHeaders(request);
                }
                
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                    // Handle URL loading with stealth features
                    return false; // Let WebView handle the URL
                }
            });
            
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onReceivedTitle(WebView view, String title) {
                    super.onReceivedTitle(view, title);
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(title);
                    }
                }
                
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                    Log.d(TAG, "Console: " + consoleMessage.message() + " at " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                    return true;
                }
                
                @Override
                public void onPermissionRequest(android.webkit.PermissionRequest request) {
                    // Grant permissions for better compatibility
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        request.grant(request.getResources());
                    }
                }
            });
            
            webView.addJavascriptInterface(new SelectorJavaScriptInterface(), "Android");
            setupAdvancedTouchListener();
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing WebView", e);
            throw e;
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupAdvancedTouchListener() {
        webView.setOnTouchListener(new View.OnTouchListener() {
            private long touchStartTime = 0;
            private float startX, startY;
            private static final int LONG_PRESS_TIMEOUT = 2000;
            private static final float MOVEMENT_THRESHOLD = 10;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartTime = System.currentTimeMillis();
                            startX = event.getX();
                            startY = event.getY();
                            isLongPressTriggered = false;
                            
                            // Cancel any previous long press
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                            }
                            
                            longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    isLongPressTriggered = true;
                                    handleLongPressDetected(startX, startY);
                                }
                            };
                            longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                            break;
                            
                        case MotionEvent.ACTION_MOVE:
                            // Cancel long press if user moves finger too much
                            float deltaX = Math.abs(event.getX() - startX);
                            float deltaY = Math.abs(event.getY() - startY);
                            if (deltaX > MOVEMENT_THRESHOLD || deltaY > MOVEMENT_THRESHOLD) {
                                if (longPressRunnable != null) {
                                    longPressHandler.removeCallbacks(longPressRunnable);
                                }
                            }
                            break;
                            
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            long touchDuration = System.currentTimeMillis() - touchStartTime;
                            
                            // Cancel long press callback
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                            }
                            
                            // Handle macro recording for short taps (not long press)
                            if (!isLongPressTriggered && touchDuration < LONG_PRESS_TIMEOUT && isRecordingMacro) {
                                handleClickRecording(event.getX(), event.getY());
                            }
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in touch handler", e);
                }
                return false;
            }
        });
    }
    
    private void handleLongPressDetected(float x, float y) {
        Log.d(TAG, "Long press detected at: " + x + ", " + y);
        
        runOnUiThread(() -> {
            Toast.makeText(this, "Analyzing element...", Toast.LENGTH_SHORT).show();
        });
        
        // Multiple attempts to execute long press handling
        executeWithRetry(() -> {
            String jsCode = String.format(Locale.US,
                "(function() {" +
                "  try {" +
                "    console.log('Executing handleLongPress at: %f, %f');" +
                "    if (typeof window.handleLongPress === 'function') {" +
                "      window.handleLongPress(%f, %f);" +
                "      return 'success';" +
                "    } else {" +
                "      console.error('handleLongPress function not found');" +
                "      Android.showError('handleLongPress function not available');" +
                "      return 'function_not_found';" +
                "    }" +
                "  } catch (e) {" +
                "    console.error('Error in handleLongPress execution:', e);" +
                "    Android.showError('Error executing handleLongPress: ' + e.message);" +
                "    return 'error';" +
                "  }" +
                "})()", x, y, x, y);
            
            webView.evaluateJavascript(jsCode, result -> {
                Log.d(TAG, "Long press execution result: " + result);
                if (!"\"success\"".equals(result)) {
                    Log.w(TAG, "Long press execution failed, result: " + result);
                }
            });
        }, 3);
    }
    
    private void handleClickRecording(float x, float y) {
        if (!isRecordingMacro) return;
        
        Log.d(TAG, "Recording click at: " + x + ", " + y);
        
        executeWithRetry(() -> {
            String jsCode = String.format(Locale.US,
                "(function() {" +
                "  try {" +
                "    console.log('Executing recordClick at: %f, %f');" +
                "    if (typeof window.recordClick === 'function') {" +
                "      window.recordClick(%f, %f);" +
                "      return 'success';" +
                "    } else {" +
                "      console.error('recordClick function not found');" +
                "      Android.showError('recordClick function not available');" +
                "      return 'function_not_found';" +
                "    }" +
                "  } catch (e) {" +
                "    console.error('Error in recordClick execution:', e);" +
                "    Android.showError('Error executing recordClick: ' + e.message);" +
                "    return 'error';" +
                "  }" +
                "})()", x, y, x, y);
            
            webView.evaluateJavascript(jsCode, result -> {
                Log.d(TAG, "Click recording result: " + result);
            });
        }, 3);
    }
    
    private void executeWithRetry(Runnable action, int maxAttempts) {
        executeWithRetryInternal(action, maxAttempts, 0);
    }
    
    private void executeWithRetryInternal(Runnable action, int maxAttempts, int currentAttempt) {
        if (currentAttempt >= maxAttempts) {
            Log.w(TAG, "Max retry attempts reached");
            return;
        }
        
        try {
            action.run();
        } catch (Exception e) {
            Log.w(TAG, "Execution attempt " + (currentAttempt + 1) + " failed", e);
            
            // Retry after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                executeWithRetryInternal(action, maxAttempts, currentAttempt + 1);
            }, 200 * (currentAttempt + 1)); // Increasing delay
        }
    }
    
    private void loadUrl() {
        try {
            String url = getIntent().getStringExtra("url");
            if (url != null && !url.isEmpty()) {
                Log.d(TAG, "Loading URL: " + url);
                webView.loadUrl(url);
            } else {
                Toast.makeText(this, "No URL provided", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading URL", e);
            Toast.makeText(this, "Error loading URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void injectStealthScript() {
        try {
            String stealthScript = getStealthScript();
            webView.evaluateJavascript(stealthScript, result -> {
                Log.d(TAG, "Stealth script injected");
            });
        } catch (Exception e) {
            Log.e(TAG, "Error injecting stealth script", e);
        }
    }
    
    private String getStealthScript() {
        return "javascript:" +
            // Override navigator properties for stealth
            "if (!window.stealthInjected) {" +
            "  window.stealthInjected = true;" +
            "  " +
            "  // Override webdriver detection" +
            "  Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
            "  " +
            "  // Override automation flags" +
            "  window.chrome = { runtime: {} };" +
            "  Object.defineProperty(navigator, 'plugins', {" +
            "    get: () => [" +
            "      { name: 'Chrome PDF Plugin', length: 1 }," +
            "      { name: 'Chrome PDF Viewer', length: 1 }," +
            "      { name: 'Native Client', length: 1 }" +
            "    ]" +
            "  });" +
            "  " +
            "  // Override language and languages" +
            "  Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });" +
            "  Object.defineProperty(navigator, 'language', { get: () => 'en-US' });" +
            "  " +
            "  // Mock permissions" +
            "  if (navigator.permissions && navigator.permissions.query) {" +
            "    const originalQuery = navigator.permissions.query;" +
            "    navigator.permissions.query = (params) => {" +
            "      return params.name === 'notifications' ? " +
            "        Promise.resolve({ state: Notification.permission }) : originalQuery(params);" +
            "    };" +
            "  }" +
            "  " +
            "  console.log('Stealth features activated');" +
            "}";
    }
    
    private void injectSelectorScript() {
        try {
            String script = getSelectorScript();
            webView.evaluateJavascript(script, result -> {
                Log.d(TAG, "Selector script injection result: " + result);
                // Force script verification after injection
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    verifyScriptInjection();
                }, 500);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error injecting selector script", e);
        }
    }
    
    private void verifyScriptInjection() {
        webView.evaluateJavascript(
            "(typeof window.handleLongPress === 'function' && typeof window.recordClick === 'function')", 
            result -> {
                Log.d(TAG, "Script verification result: " + result);
                if (!"true".equals(result)) {
                    Log.w(TAG, "Scripts not properly loaded, re-injecting...");
                    // Re-inject if verification fails
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        injectSelectorScript();
                    }, 300);
                } else {
                    Log.d(TAG, "All scripts verified successfully");
                }
            }
        );
    }
    
    private WebResourceResponse addStealthHeaders(android.webkit.WebResourceRequest request) {
        try {
            // Add stealth headers to requests
            java.util.Map<String, String> headers = request.getRequestHeaders();
            
            // Add realistic headers
            if (!headers.containsKey("Accept")) {
                headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            }
            if (!headers.containsKey("Accept-Language")) {
                headers.put("Accept-Language", "en-US,en;q=0.5");
            }
            if (!headers.containsKey("Accept-Encoding")) {
                headers.put("Accept-Encoding", "gzip, deflate, br");
            }
            if (!headers.containsKey("DNT")) {
                headers.put("DNT", "1");
            }
            if (!headers.containsKey("Connection")) {
                headers.put("Connection", "keep-alive");
            }
            if (!headers.containsKey("Upgrade-Insecure-Requests")) {
                headers.put("Upgrade-Insecure-Requests", "1");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding stealth headers", e);
        }
        return null; // Let the WebView handle the request normally
    }
    
    private String getSelectorScript() {
        return "javascript:" +
            // Force script injection into global scope
            "(function() {" +
            "  'use strict';" +
            "  " +
            "  console.log('Element Finder Browser: Starting advanced selector script injection...');" +
            "  " +
            "  // Prevent double injection" +
            "  if (window.elementFinderAdvanced) {" +
            "    console.log('Advanced scripts already loaded');" +
            "    return;" +
            "  }" +
            "  " +
            "  window.elementFinderAdvanced = true;" +
            "  " +
            "  // Advanced element finder object" +
            "  window.elementFinderBrowser = window.elementFinderBrowser || {" +
            "    generateCSSSelector: function(element) {" +
            "      if (!element || element === document.documentElement || element === document.body) {" +
            "        return { selector: '', name: 'root element' };" +
            "      }" +
            "      " +
            "      try {" +
            "        var elementName = this.getElementName(element);" +
            "        " +
            "        // Priority 1: ID selector" +
            "        if (element.id && element.id.trim()) {" +
            "          var idSelector = '#' + CSS.escape(element.id);" +
            "          try {" +
            "            if (document.querySelectorAll(idSelector).length === 1) {" +
            "              return { selector: idSelector, name: elementName };" +
            "            }" +
            "          } catch (e) {" +
            "            console.warn('ID selector failed:', e);" +
            "          }" +
            "        }" +
            "        " +
            "        // Priority 2: Class selectors" +
            "        if (element.className && typeof element.className === 'string') {" +
            "          var classes = element.className.trim().split(/\\s+/).filter(function(c) { " +
            "            return c.length > 0 && !/^\\d/.test(c); " +
            "          });" +
            "          " +
            "          for (var i = 0; i < classes.length; i++) {" +
            "            try {" +
            "              var classSelector = '.' + CSS.escape(classes[i]);" +
            "              if (document.querySelectorAll(classSelector).length === 1) {" +
            "                return { selector: classSelector, name: elementName };" +
            "              }" +
            "            } catch (e) {" +
            "              console.warn('Class selector failed:', e);" +
            "            }" +
            "          }" +
            "          " +
            "          // Try multiple classes" +
            "          if (classes.length > 1) {" +
            "            try {" +
            "              var multiClassSelector = '.' + classes.slice(0, 2).map(CSS.escape).join('.');" +
            "              if (document.querySelectorAll(multiClassSelector).length <= 3) {" +
            "                return { selector: multiClassSelector, name: elementName };" +
            "              }" +
            "            } catch (e) {" +
            "              console.warn('Multi-class selector failed:', e);" +
            "            }" +
            "          }" +
            "        }" +
            "        " +
            "        // Priority 3: Attribute selectors" +
            "        var attributes = ['name', 'data-testid', 'data-id', 'type', 'role', 'aria-label'];" +
            "        for (var j = 0; j < attributes.length; j++) {" +
            "          var attr = attributes[j];" +
            "          if (element.hasAttribute(attr)) {" +
            "            var attrValue = element.getAttribute(attr);" +
            "            if (attrValue && attrValue.trim()) {" +
            "              try {" +
            "                var attrSelector = element.tagName.toLowerCase() + '[' + attr + '=\"' + attrValue + '\"]';" +
            "                if (document.querySelectorAll(attrSelector).length <= 2) {" +
            "                  return { selector: attrSelector, name: elementName };" +
            "                }" +
            "              } catch (e) {" +
            "                console.warn('Attribute selector failed:', e);" +
            "              }" +
            "            }" +
            "          }" +
            "        }" +
            "        " +
            "        // Priority 4: nth-child approach" +
            "        try {" +
            "          var parent = element.parentElement;" +
            "          if (parent) {" +
            "            var siblings = Array.from(parent.children).filter(function(child) {" +
            "              return child.tagName === element.tagName;" +
            "            });" +
            "            var index = siblings.indexOf(element) + 1;" +
            "            if (index > 0) {" +
            "              var nthSelector = element.tagName.toLowerCase() + ':nth-of-type(' + index + ')';" +
            "              return { selector: nthSelector, name: elementName };" +
            "            }" +
            "          }" +
            "        } catch (e) {" +
            "          console.warn('nth-child selector failed:', e);" +
            "        }" +
            "        " +
            "        // Fallback" +
            "        return { selector: element.tagName.toLowerCase(), name: elementName };" +
            "      } catch (e) {" +
            "        console.error('CSS selector generation error:', e);" +
            "        return { selector: 'error', name: 'unknown' };" +
            "      }" +
            "    }," +
            "    " +
            "    getElementName: function(element) {" +
            "      try {" +
            "        var name = element.tagName.toLowerCase();" +
            "        if (element.id) return name + ' (id: ' + element.id + ')';" +
            "        if (element.name) return name + ' (name: ' + element.name + ')';" +
            "        if (element.className && typeof element.className === 'string') {" +
            "          var firstClass = element.className.trim().split(/\\s+/)[0];" +
            "          if (firstClass) return name + ' (class: ' + firstClass + ')';" +
            "        }" +
            "        if (element.getAttribute('data-testid')) return name + ' (testid: ' + element.getAttribute('data-testid') + ')';" +
            "        if (element.textContent && element.textContent.trim().length > 0 && element.textContent.trim().length < 30) {" +
            "          return name + ' (\"' + element.textContent.trim().substring(0, 20) + '\")';" +
            "        }" +
            "        return name + ' element';" +
            "      } catch (e) {" +
            "        return 'unknown element';" +
            "      }" +
            "    }," +
            "    " +
            "    generateXPathSelector: function(element) {" +
            "      if (!element || element === document.documentElement || element === document.body) {" +
            "        return { selector: '', name: 'root element' };" +
            "      }" +
            "      " +
            "      try {" +
            "        var elementName = this.getElementName(element);" +
            "        var xpath = '';" +
            "        var current = element;" +
            "        " +
            "        while (current && current.nodeType === Node.ELEMENT_NODE && current !== document.documentElement) {" +
            "          var tagName = current.tagName.toLowerCase();" +
            "          var index = 1;" +
            "          " +
            "          var sibling = current.previousElementSibling;" +
            "          while (sibling) {" +
            "            if (sibling.tagName === current.tagName) {" +
            "              index++;" +
            "            }" +
            "            sibling = sibling.previousElementSibling;" +
            "          }" +
            "          " +
            "          xpath = '/' + tagName + '[' + index + ']' + xpath;" +
            "          current = current.parentElement;" +
            "        }" +
            "        " +
            "        return { selector: '/html' + xpath, name: elementName };" +
            "      } catch (e) {" +
            "        console.error('XPath generation error:', e);" +
            "        return { selector: 'error', name: 'unknown' };" +
            "      }" +
            "    }," +
            "    " +
            "    getBestSelector: function(element) {" +
            "      try {" +
            "        var cssResult = this.generateCSSSelector(element);" +
            "        var xpathResult = this.generateXPathSelector(element);" +
            "        " +
            "        var recommended = cssResult.selector;" +
            "        if (!recommended || recommended === 'error' || recommended.length === 0) {" +
            "          recommended = xpathResult.selector;" +
            "        }" +
            "        " +
            "        return {" +
            "          css: cssResult.selector," +
            "          xpath: xpathResult.selector," +
            "          recommended: recommended," +
            "          elementName: cssResult.name" +
            "        };" +
            "      } catch (e) {" +
            "        console.error('getBestSelector error:', e);" +
            "        return { css: 'error', xpath: 'error', recommended: 'error', elementName: 'unknown' };" +
            "      }" +
            "    }" +
            "  };" +
            "  " +
            "  // Define global functions" +
            "  window.handleLongPress = function(x, y) {" +
            "    try {" +
            "      console.log('handleLongPress executed at:', x, y);" +
            "      var element = document.elementFromPoint(x, y);" +
            "      console.log('Element at coordinates:', element);" +
            "      " +
            "      if (element && element !== document.documentElement && element !== document.body) {" +
            "        var selectors = window.elementFinderBrowser.getBestSelector(element);" +
            "        console.log('Generated selectors:', selectors);" +
            "        " +
            "        if (typeof Android !== 'undefined' && Android.showSelectorDialog) {" +
            "          Android.showSelectorDialog(JSON.stringify(selectors));" +
            "        } else {" +
            "          console.error('Android interface not available');" +
            "        }" +
            "      } else {" +
            "        console.log('No valid element found');" +
            "        if (typeof Android !== 'undefined' && Android.showError) {" +
            "          Android.showError('No clickable element found at coordinates');" +
            "        }" +
            "      }" +
            "    } catch (e) {" +
            "      console.error('Error in handleLongPress:', e);" +
            "      if (typeof Android !== 'undefined' && Android.showError) {" +
            "        Android.showError('Error analyzing element: ' + e.message);" +
            "      }" +
            "    }" +
            "  };" +
            "  " +
            "  window.recordClick = function(x, y) {" +
            "    try {" +
            "      console.log('recordClick executed at:', x, y);" +
            "      var element = document.elementFromPoint(x, y);" +
            "      " +
            "      if (element && element !== document.documentElement && element !== document.body) {" +
            "        var selectors = window.elementFinderBrowser.getBestSelector(element);" +
            "        console.log('Recording selectors:', selectors);" +
            "        " +
            "        if (typeof Android !== 'undefined' && Android.recordSelector) {" +
            "          Android.recordSelector(selectors.recommended || selectors.css || selectors.xpath, selectors.elementName);" +
            "        } else {" +
            "          console.error('Android recording interface not available');" +
            "        }" +
            "      }" +
            "    } catch (e) {" +
            "      console.error('Error in recordClick:', e);" +
            "      if (typeof Android !== 'undefined' && Android.showError) {" +
            "        Android.showError('Error recording click: ' + e.message);" +
            "      }" +
            "    }" +
            "  };" +
            "  " +
            "  console.log('✅ Element Finder Browser: Advanced selector script loaded successfully');" +
            "  console.log('Available functions: handleLongPress, recordClick');" +
            "  " +
            "  // Test function availability" +
            "  setTimeout(function() {" +
            "    console.log('Function test - handleLongPress:', typeof window.handleLongPress);" +
            "    console.log('Function test - recordClick:', typeof window.recordClick);" +
            "  }, 100);" +
            "  " +
            "})();";
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem recordItem = menu.findItem(R.id.action_record_macro);
        if (recordItem != null) {
            if (isRecordingMacro) {
                recordItem.setVisible(false); // Hide menu item when recording
            } else {
                recordItem.setVisible(true);
                recordItem.setTitle("Record Macro");
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        try {
            if (id == android.R.id.home) {
                onBackPressed();
                return true;
            } else if (id == R.id.action_record_macro) {
                startMacroRecording();
                return true;
            } else if (id == R.id.action_desktop_mode) {
                toggleDesktopMode();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in menu selection", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void startMacroRecording() {
        isRecordingMacro = true;
        recordedSelectors.clear();
        recordedElementNames.clear();
        
        // Show macro control panel
        macroControlPanel.setVisibility(View.VISIBLE);
        
        // Start timer
        macroTimer.setBase(SystemClock.elapsedRealtime());
        macroTimer.start();
        
        Toast.makeText(this, "Macro recording started - Click elements to record their selectors", Toast.LENGTH_LONG).show();
        invalidateOptionsMenu();
    }
    
    private void stopMacroRecording() {
        isRecordingMacro = false;
        
        // Stop timer
        macroTimer.stop();
        
        // Hide macro control panel
        macroControlPanel.setVisibility(View.GONE);
        
        saveMacroToFile();
        invalidateOptionsMenu();
    }
    
    private void toggleDesktopMode() {
        try {
            WebSettings webSettings = webView.getSettings();
            String currentUserAgent = webSettings.getUserAgentString();
            
            if (currentUserAgent.contains("Mobile")) {
                // Switch to advanced desktop stealth mode
                String stealthDesktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
                webSettings.setUserAgentString(stealthDesktopUserAgent);
                
                // Inject additional desktop stealth features
                webView.evaluateJavascript(
                    "javascript:" +
                    "Object.defineProperty(screen, 'width', { value: 1920 });" +
                    "Object.defineProperty(screen, 'height', { value: 1080 });" +
                    "Object.defineProperty(screen, 'availWidth', { value: 1920 });" +
                    "Object.defineProperty(screen, 'availHeight', { value: 1040 });" +
                    "Object.defineProperty(navigator, 'platform', { value: 'Win32' });" +
                    "Object.defineProperty(navigator, 'maxTouchPoints', { value: 0 });" +
                    "console.log('Advanced desktop stealth mode activated');",
                    null
                );
                
                Toast.makeText(this, "🖥️ Advanced Desktop Stealth Mode Enabled", Toast.LENGTH_SHORT).show();
            } else {
                // Switch back to mobile stealth mode
                String stealthMobileUserAgent = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
                webSettings.setUserAgentString(stealthMobileUserAgent);
                
                // Inject mobile stealth features
                webView.evaluateJavascript(
                    "javascript:" +
                    "Object.defineProperty(screen, 'width', { value: 393 });" +
                    "Object.defineProperty(screen, 'height', { value: 851 });" +
                    "Object.defineProperty(screen, 'availWidth', { value: 393 });" +
                    "Object.defineProperty(screen, 'availHeight', { value: 851 });" +
                    "Object.defineProperty(navigator, 'platform', { value: 'Linux armv8l' });" +
                    "Object.defineProperty(navigator, 'maxTouchPoints', { value: 5 });" +
                    "console.log('Mobile stealth mode activated');",
                    null
                );
                
                Toast.makeText(this, "📱 Mobile Stealth Mode Enabled", Toast.LENGTH_SHORT).show();
            }
            
            webView.reload();
        } catch (Exception e) {
            Log.e(TAG, "Error toggling stealth mode", e);
            Toast.makeText(this, "Error toggling stealth mode", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void saveMacroToFile() {
        if (recordedSelectors.isEmpty()) {
            Toast.makeText(this, "No selectors recorded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            File appDir = new File(getFilesDir(), "Element Finder Browser");
            if (!appDir.exists()) {
                appDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File macroFile = new File(appDir, "macro_" + timestamp + ".txt");
            
            FileWriter writer = new FileWriter(macroFile);
            writer.write("Element Finder Browser - Macro Recording\n");
            writer.write("Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n");
            writer.write("URL: " + webView.getUrl() + "\n");
            writer.write("Total selectors recorded: " + recordedSelectors.size() + "\n");
            writer.write("===========================================\n\n");
            
            for (int i = 0; i < recordedSelectors.size(); i++) {
                String elementName = i < recordedElementNames.size() ? recordedElementNames.get(i) : "unknown element";
                writer.write("Selector: " + recordedSelectors.get(i) + " : " + elementName + "\n");
            }
            
            writer.close();
            
            Toast.makeText(this, "Macro saved: " + macroFile.getName() + " (" + recordedSelectors.size() + " selectors)", Toast.LENGTH_LONG).show();
            
        } catch (IOException e) {
            Log.e(TAG, "Error saving macro", e);
            Toast.makeText(this, "Error saving macro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showSavedFiles() {
        try {
            File appDir = new File(getFilesDir(), "Element Finder Browser");
            File[] files = appDir.listFiles((dir, name) -> name.startsWith("macro_") && name.endsWith(".txt"));
            
            if (files == null || files.length == 0) {
                Toast.makeText(this, "No saved macro files found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Saved Macro Files (" + files.length + ")");
            
            String[] fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = files[i].getName() + " (" + formatFileSize(files[i].length()) + ")";
            }
            
            builder.setItems(fileNames, (dialog, which) -> {
                File selectedFile = files[which];
                openFileInExternalApp(selectedFile);
            });
            
            builder.setNegativeButton("Close", null);
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing saved files", e);
            Toast.makeText(this, "Error accessing saved files: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openFileInExternalApp(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, 
                getPackageName() + ".fileprovider", file);
            intent.setDataAndType(uri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            Intent chooser = Intent.createChooser(intent, "Open macro file with...");
            if (chooser.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                Toast.makeText(this, "No text editor app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening file", e);
            Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    public class SelectorJavaScriptInterface {
        @JavascriptInterface
        public void showSelectorDialog(String selectorsJson) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject selectors = new JSONObject(selectorsJson);
                        String css = selectors.optString("css", "");
                        String xpath = selectors.optString("xpath", "");
                        String recommended = selectors.optString("recommended", "");
                        String elementName = selectors.optString("elementName", "unknown element");
                        
                        showSelectorChoiceDialog(css, xpath, recommended, elementName);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing selectors", e);
                        Toast.makeText(WebViewActivity.this, "Error parsing selectors: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        @JavascriptInterface
        public void recordSelector(String selector, String elementName) {
            if (isRecordingMacro && selector != null && !selector.isEmpty()) {
                recordedSelectors.add(selector);
                recordedElementNames.add(elementName != null ? elementName : "unknown element");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WebViewActivity.this, 
                            "Selector recorded (" + recordedSelectors.size() + "): " + elementName, 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        
        @JavascriptInterface
        public void showError(String error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "JavaScript error: " + error);
                    Toast.makeText(WebViewActivity.this, "JS Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    private void showSelectorChoiceDialog(String css, String xpath, String recommended, String elementName) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Element Selectors - " + elementName);
            
            String message = "Recommended: " + recommended + "\n\n";
            if (!css.isEmpty() && !css.equals("error")) {
                message += "CSS Selector: " + css + "\n\n";
            }
            if (!xpath.isEmpty() && !xpath.equals("error")) {
                message += "XPath Selector: " + xpath;
            }
            
            builder.setMessage(message);
            
            builder.setPositiveButton("Copy Recommended", (dialog, which) -> {
                copyToClipboard("Recommended Selector", recommended);
            });
            
            if (!css.isEmpty() && !css.equals("error")) {
                builder.setNeutralButton("Copy CSS", (dialog, which) -> {
                    copyToClipboard("CSS Selector", css);
                });
            }
            
            if (!xpath.isEmpty() && !xpath.equals("error")) {
                builder.setNegativeButton("Copy XPath", (dialog, which) -> {
                    copyToClipboard("XPath Selector", xpath);
                });
            }
            
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing selector dialog", e);
            Toast.makeText(this, "Error showing selector dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyToClipboard(String label, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + " copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error copying to clipboard", e);
            Toast.makeText(this, "Error copying to clipboard", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onBackPressed() {
        try {
            if (isRecordingMacro) {
                // Ask user if they want to stop recording
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Stop Recording?");
                builder.setMessage("Macro recording is in progress. Do you want to stop and save?");
                builder.setPositiveButton("Stop & Save", (dialog, which) -> {
                    stopMacroRecording();
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        super.onBackPressed();
                    }
                });
                builder.setNegativeButton("Continue Recording", null);
                builder.show();
            } else if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onBackPressed", e);
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            if (longPressHandler != null && longPressRunnable != null) {
                longPressHandler.removeCallbacks(longPressRunnable);
            }
            if (macroTimer != null) {
                macroTimer.stop();
            }
            if (webView != null) {
                webView.removeJavascriptInterface("Android");
                webView.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
        super.onDestroy();
    }
}