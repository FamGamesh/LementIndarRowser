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
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);
            webSettings.setSupportZoom(true);
            webSettings.setDefaultTextEncodingName("utf-8");
            webSettings.setAllowFileAccess(false);
            webSettings.setAllowContentAccess(false);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    // Add delay to ensure page is fully loaded
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        injectSelectorScript();
                        Toast.makeText(WebViewActivity.this, "Page loaded. Long press elements to view selectors!", Toast.LENGTH_LONG).show();
                    }, 1000);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView error: " + description);
                    Toast.makeText(WebViewActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT).show();
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
            });
            
            webView.addJavascriptInterface(new SelectorJavaScriptInterface(), "Android");
            setupTouchListener();
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing WebView", e);
            throw e;
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListener() {
        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            isLongPressTriggered = false;
                            final float downX = event.getX();
                            final float downY = event.getY();
                            
                            longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    isLongPressTriggered = true;
                                    Log.d(TAG, "Long press detected at: " + downX + ", " + downY);
                                    
                                    // Execute JavaScript to find element at coordinates
                                    String jsCode = String.format(
                                        "try { " +
                                        "  var element = document.elementFromPoint(%f, %f); " +
                                        "  if (element) { " +
                                        "    handleLongPress(%f, %f); " +
                                        "  } else { " +
                                        "    Android.showError('No element found at coordinates'); " +
                                        "  } " +
                                        "} catch(e) { " +
                                        "  Android.showError('Error: ' + e.message); " +
                                        "}", downX, downY, downX, downY);
                                    
                                    webView.evaluateJavascript(jsCode, result -> {
                                        Log.d(TAG, "JavaScript execution result: " + result);
                                    });
                                    
                                    // Show visual feedback
                                    runOnUiThread(() -> {
                                        Toast.makeText(WebViewActivity.this, "Analyzing element...", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            };
                            longPressHandler.postDelayed(longPressRunnable, 2000);
                            break;
                            
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                            }
                            
                            // Handle macro recording for normal clicks
                            if (!isLongPressTriggered && isRecordingMacro) {
                                final float upX = event.getX();
                                final float upY = event.getY();
                                
                                String jsCode = String.format(
                                    "try { " +
                                    "  recordClick(%f, %f); " +
                                    "} catch(e) { " +
                                    "  Android.showError('Record error: ' + e.message); " +
                                    "}", upX, upY);
                                
                                webView.evaluateJavascript(jsCode, null);
                            }
                            break;
                            
                        case MotionEvent.ACTION_MOVE:
                            // Cancel long press on movement
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                            }
                            break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in touch handler", e);
                }
                return false; // Allow WebView to handle the touch as well
            }
        });
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
    
    private void injectSelectorScript() {
        try {
            String script = getSelectorScript();
            webView.evaluateJavascript(script, result -> {
                Log.d(TAG, "Selector script injected, result: " + result);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error injecting script", e);
        }
    }
    
    private String getSelectorScript() {
        return "javascript:" +
            "console.log('Element Finder Browser: Selector script loading...');" +
            "var elementFinderBrowser = {" +
            "  generateCSSSelector: function(element) {" +
            "    if (!element) return { selector: '', name: 'unknown' };" +
            "    " +
            "    try {" +
            "      var elementName = this.getElementName(element);" +
            "      " +
            "      // Priority 1: ID selector" +
            "      if (element.id) {" +
            "        var idSelector = '#' + element.id;" +
            "        if (document.querySelectorAll(idSelector).length === 1) {" +
            "          return { selector: idSelector, name: elementName };" +
            "        }" +
            "      }" +
            "      " +
            "      // Priority 2: Class selector with uniqueness check" +
            "      if (element.className && typeof element.className === 'string') {" +
            "        var classes = element.className.split(' ').filter(function(c) { return c.length > 0; });" +
            "        for (var i = 0; i < classes.length; i++) {" +
            "          var classSelector = '.' + classes[i];" +
            "          if (document.querySelectorAll(classSelector).length === 1) {" +
            "            return { selector: classSelector, name: elementName };" +
            "          }" +
            "        }" +
            "        " +
            "        // Try combination of classes" +
            "        if (classes.length > 1) {" +
            "          var combinedClass = '.' + classes.join('.');" +
            "          if (document.querySelectorAll(combinedClass).length === 1) {" +
            "            return { selector: combinedClass, name: elementName };" +
            "          }" +
            "        }" +
            "      }" +
            "      " +
            "      // Priority 3: Attribute-based selectors" +
            "      var attributes = ['name', 'type', 'value', 'placeholder', 'title', 'alt'];" +
            "      for (var j = 0; j < attributes.length; j++) {" +
            "        var attr = attributes[j];" +
            "        if (element.hasAttribute(attr)) {" +
            "          var attrValue = element.getAttribute(attr);" +
            "          if (attrValue) {" +
            "            var attrSelector = element.tagName.toLowerCase() + '[' + attr + '=\"' + attrValue + '\"]';" +
            "            if (document.querySelectorAll(attrSelector).length === 1) {" +
            "              return { selector: attrSelector, name: elementName };" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "      " +
            "      // Priority 4: nth-child selector" +
            "      var parent = element.parentElement;" +
            "      if (parent) {" +
            "        var siblings = Array.from(parent.children);" +
            "        var index = siblings.indexOf(element) + 1;" +
            "        var nthSelector = element.tagName.toLowerCase() + ':nth-child(' + index + ')';" +
            "        return { selector: nthSelector, name: elementName };" +
            "      }" +
            "      " +
            "      return { selector: element.tagName.toLowerCase(), name: elementName };" +
            "    } catch (e) {" +
            "      console.error('CSS Selector generation error:', e);" +
            "      return { selector: 'error', name: 'unknown' };" +
            "    }" +
            "  }," +
            "  " +
            "  getElementName: function(element) {" +
            "    try {" +
            "      if (element.id) return element.tagName.toLowerCase() + ' (id: ' + element.id + ')';" +
            "      if (element.name) return element.tagName.toLowerCase() + ' (name: ' + element.name + ')';" +
            "      if (element.className && typeof element.className === 'string') {" +
            "        var firstClass = element.className.split(' ')[0];" +
            "        if (firstClass) return element.tagName.toLowerCase() + ' (class: ' + firstClass + ')';" +
            "      }" +
            "      if (element.placeholder) return element.tagName.toLowerCase() + ' (placeholder: ' + element.placeholder + ')';" +
            "      if (element.textContent && element.textContent.trim().length > 0 && element.textContent.trim().length < 50) {" +
            "        return element.tagName.toLowerCase() + ' (text: ' + element.textContent.trim().substring(0, 30) + ')';" +
            "      }" +
            "      return element.tagName.toLowerCase() + ' element';" +
            "    } catch (e) {" +
            "      return 'unknown element';" +
            "    }" +
            "  }," +
            "  " +
            "  generateXPathSelector: function(element) {" +
            "    if (!element) return { selector: '', name: 'unknown' };" +
            "    " +
            "    try {" +
            "      var elementName = this.getElementName(element);" +
            "      var xpath = '';" +
            "      var current = element;" +
            "      " +
            "      while (current && current.nodeType === Node.ELEMENT_NODE) {" +
            "        var tagName = current.tagName.toLowerCase();" +
            "        var index = 1;" +
            "        " +
            "        if (current.previousSibling) {" +
            "          var sibling = current.previousSibling;" +
            "          while (sibling) {" +
            "            if (sibling.nodeType === Node.ELEMENT_NODE && sibling.tagName === current.tagName) {" +
            "              index++;" +
            "            }" +
            "            sibling = sibling.previousSibling;" +
            "          }" +
            "        }" +
            "        " +
            "        xpath = '/' + tagName + '[' + index + ']' + xpath;" +
            "        current = current.parentElement;" +
            "      }" +
            "      " +
            "      return { selector: xpath, name: elementName };" +
            "    } catch (e) {" +
            "      console.error('XPath generation error:', e);" +
            "      return { selector: 'error', name: 'unknown' };" +
            "    }" +
            "  }," +
            "  " +
            "  getBestSelector: function(element) {" +
            "    try {" +
            "      var cssResult = this.generateCSSSelector(element);" +
            "      var xpathResult = this.generateXPathSelector(element);" +
            "      " +
            "      return {" +
            "        css: cssResult.selector," +
            "        xpath: xpathResult.selector," +
            "        recommended: cssResult.selector.length > 0 && cssResult.selector !== 'error' ? cssResult.selector : xpathResult.selector," +
            "        elementName: cssResult.name" +
            "      };" +
            "    } catch (e) {" +
            "      console.error('getBestSelector error:', e);" +
            "      return { css: 'error', xpath: 'error', recommended: 'error', elementName: 'unknown' };" +
            "    }" +
            "  }" +
            "};" +
            "" +
            "function handleLongPress(x, y) {" +
            "  try {" +
            "    console.log('handleLongPress called with coordinates:', x, y);" +
            "    var element = document.elementFromPoint(x, y);" +
            "    console.log('Element found:', element);" +
            "    " +
            "    if (element) {" +
            "      var selectors = elementFinderBrowser.getBestSelector(element);" +
            "      console.log('Selectors generated:', selectors);" +
            "      Android.showSelectorDialog(JSON.stringify(selectors));" +
            "    } else {" +
            "      Android.showError('No element found at coordinates');" +
            "    }" +
            "  } catch (e) {" +
            "    console.error('Error in handleLongPress:', e);" +
            "    Android.showError('Error in handleLongPress: ' + e.message);" +
            "  }" +
            "}" +
            "" +
            "function recordClick(x, y) {" +
            "  try {" +
            "    console.log('recordClick called with coordinates:', x, y);" +
            "    var element = document.elementFromPoint(x, y);" +
            "    if (element) {" +
            "      var selectors = elementFinderBrowser.getBestSelector(element);" +
            "      console.log('Recording selector:', selectors);" +
            "      Android.recordSelector(selectors.recommended, selectors.elementName);" +
            "    }" +
            "  } catch (e) {" +
            "    console.error('Error in recordClick:', e);" +
            "    Android.showError('Error in recordClick: ' + e.message);" +
            "  }" +
            "}" +
            "" +
            "console.log('Element Finder Browser: Selector script loaded successfully');";
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
                String desktopUserAgent = currentUserAgent.replace("Mobile", "X11; Linux x86_64");
                webSettings.setUserAgentString(desktopUserAgent);
                Toast.makeText(this, "Desktop mode enabled", Toast.LENGTH_SHORT).show();
            } else {
                webSettings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
                Toast.makeText(this, "Mobile mode enabled", Toast.LENGTH_SHORT).show();
            }
            
            webView.reload();
        } catch (Exception e) {
            Log.e(TAG, "Error toggling desktop mode", e);
            Toast.makeText(this, "Error toggling desktop mode", Toast.LENGTH_SHORT).show();
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