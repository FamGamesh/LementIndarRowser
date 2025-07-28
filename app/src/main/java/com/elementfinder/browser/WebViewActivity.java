package com.elementfinder.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private boolean isLongPressTriggered = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_webview);
            
            setupToolbar();
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
            
            // Additional safety settings
            webSettings.setAllowFileAccess(false);
            webSettings.setAllowContentAccess(false);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    try {
                        injectSelectorScript();
                    } catch (Exception e) {
                        Log.e(TAG, "Error injecting selector script", e);
                    }
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
                            longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    isLongPressTriggered = true;
                                    float x = event.getX();
                                    float y = event.getY();
                                    webView.evaluateJavascript(
                                        "handleLongPress(" + x + ", " + y + ");", null);
                                }
                            };
                            longPressHandler.postDelayed(longPressRunnable, 2000);
                            break;
                            
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
                            }
                            if (!isLongPressTriggered && isRecordingMacro) {
                                float x = event.getX();
                                float y = event.getY();
                                webView.evaluateJavascript(
                                    "recordClick(" + x + ", " + y + ");", null);
                            }
                            break;
                            
                        case MotionEvent.ACTION_MOVE:
                            if (longPressRunnable != null) {
                                longPressHandler.removeCallbacks(longPressRunnable);
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
            webView.evaluateJavascript(script, null);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting script", e);
        }
    }
    
    private String getSelectorScript() {
        return "javascript:" +
            "var elementFinderBrowser = {" +
            "  generateCSSSelector: function(element) {" +
            "    if (!element) return '';" +
            "    " +
            "    try {" +
            "      // Priority 1: ID selector" +
            "      if (element.id) {" +
            "        var idSelector = '#' + element.id;" +
            "        if (document.querySelectorAll(idSelector).length === 1) {" +
            "          return idSelector;" +
            "        }" +
            "      }" +
            "      " +
            "      // Priority 2: Class selector with uniqueness check" +
            "      if (element.className) {" +
            "        var classes = element.className.split(' ').filter(function(c) { return c.length > 0; });" +
            "        for (var i = 0; i < classes.length; i++) {" +
            "          var classSelector = '.' + classes[i];" +
            "          if (document.querySelectorAll(classSelector).length === 1) {" +
            "            return classSelector;" +
            "          }" +
            "        }" +
            "        " +
            "        // Try combination of classes" +
            "        if (classes.length > 1) {" +
            "          var combinedClass = '.' + classes.join('.');" +
            "          if (document.querySelectorAll(combinedClass).length === 1) {" +
            "            return combinedClass;" +
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
            "          var attrSelector = element.tagName.toLowerCase() + '[' + attr + '=\"' + attrValue + '\"]';" +
            "          if (document.querySelectorAll(attrSelector).length === 1) {" +
            "            return attrSelector;" +
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
            "        return nthSelector;" +
            "      }" +
            "      " +
            "      return element.tagName.toLowerCase();" +
            "    } catch (e) {" +
            "      return element.tagName ? element.tagName.toLowerCase() : 'unknown';" +
            "    }" +
            "  }," +
            "  " +
            "  generateXPathSelector: function(element) {" +
            "    if (!element) return '';" +
            "    " +
            "    try {" +
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
            "      return xpath;" +
            "    } catch (e) {" +
            "      return '/unknown';" +
            "    }" +
            "  }," +
            "  " +
            "  getBestSelector: function(element) {" +
            "    try {" +
            "      var cssSelector = this.generateCSSSelector(element);" +
            "      var xpathSelector = this.generateXPathSelector(element);" +
            "      " +
            "      return {" +
            "        css: cssSelector," +
            "        xpath: xpathSelector," +
            "        recommended: cssSelector.length > 0 ? cssSelector : xpathSelector" +
            "      };" +
            "    } catch (e) {" +
            "      return { css: 'error', xpath: 'error', recommended: 'error' };" +
            "    }" +
            "  }" +
            "};" +
            "" +
            "function handleLongPress(x, y) {" +
            "  try {" +
            "    var element = document.elementFromPoint(x, y);" +
            "    if (element) {" +
            "      var selectors = elementFinderBrowser.getBestSelector(element);" +
            "      Android.showSelectorDialog(JSON.stringify(selectors));" +
            "    }" +
            "  } catch (e) {" +
            "    console.error('Error in handleLongPress:', e);" +
            "  }" +
            "}" +
            "" +
            "function recordClick(x, y) {" +
            "  try {" +
            "    var element = document.elementFromPoint(x, y);" +
            "    if (element) {" +
            "      var selectors = elementFinderBrowser.getBestSelector(element);" +
            "      Android.recordSelector(selectors.recommended);" +
            "    }" +
            "  } catch (e) {" +
            "    console.error('Error in recordClick:', e);" +
            "  }" +
            "}";
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
                recordItem.setTitle("Stop Macro");
            } else {
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
                toggleMacroRecording();
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
    
    private void toggleMacroRecording() {
        if (isRecordingMacro) {
            stopMacroRecording();
        } else {
            startMacroRecording();
        }
    }
    
    private void startMacroRecording() {
        isRecordingMacro = true;
        recordedSelectors.clear();
        Toast.makeText(this, "Macro recording started", Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }
    
    private void stopMacroRecording() {
        isRecordingMacro = false;
        saveMacroToFile();
        Toast.makeText(this, "Macro recording stopped and saved", Toast.LENGTH_SHORT).show();
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
            writer.write("===========================================\n\n");
            
            for (int i = 0; i < recordedSelectors.size(); i++) {
                writer.write("Step " + (i + 1) + ": " + recordedSelectors.get(i) + "\n");
            }
            
            writer.close();
            
            Toast.makeText(this, "Macro saved: " + macroFile.getName(), Toast.LENGTH_LONG).show();
            
        } catch (IOException e) {
            Log.e(TAG, "Error saving macro", e);
            Toast.makeText(this, "Error saving macro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
                        
                        showSelectorChoiceDialog(css, xpath, recommended);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing selectors", e);
                        Toast.makeText(WebViewActivity.this, "Error parsing selectors", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        @JavascriptInterface
        public void recordSelector(String selector) {
            if (isRecordingMacro && selector != null && !selector.isEmpty()) {
                recordedSelectors.add(selector);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WebViewActivity.this, "Selector recorded (" + recordedSelectors.size() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
    
    private void showSelectorChoiceDialog(String css, String xpath, String recommended) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Element Selectors");
            
            String message = "Recommended: " + recommended + "\n\n";
            if (!css.isEmpty()) {
                message += "CSS Selector: " + css + "\n\n";
            }
            if (!xpath.isEmpty()) {
                message += "XPath Selector: " + xpath;
            }
            
            builder.setMessage(message);
            
            builder.setPositiveButton("Copy Recommended", (dialog, which) -> {
                copyToClipboard("Recommended Selector", recommended);
            });
            
            if (!css.isEmpty()) {
                builder.setNeutralButton("Copy CSS", (dialog, which) -> {
                    copyToClipboard("CSS Selector", css);
                });
            }
            
            if (!xpath.isEmpty()) {
                builder.setNegativeButton("Copy XPath", (dialog, which) -> {
                    copyToClipboard("XPath Selector", xpath);
                });
            }
            
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing selector dialog", e);
            Toast.makeText(this, "Error showing selector dialog", Toast.LENGTH_SHORT).show();
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
            if (webView != null && webView.canGoBack()) {
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