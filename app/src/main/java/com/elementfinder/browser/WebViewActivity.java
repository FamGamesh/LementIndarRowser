package com.elementfinder.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    
    private WebView webView;
    private boolean isRecordingMacro = false;
    private List<String> recordedSelectors = new ArrayList<>();
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private boolean isLongPressTriggered = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        
        setupToolbar();
        initializeWebView();
        loadUrl();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Element Finder Browser");
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private void initializeWebView() {
        webView = findViewById(R.id.webview);
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        
        // Set user agent for desktop mode initially disabled
        webSettings.setUserAgentString(webSettings.getUserAgentString());
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSelectorScript();
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new SelectorJavaScriptInterface(), "Android");
        
        setupTouchListener();
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListener() {
        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
                            // Record normal click for macro
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
                return false;
            }
        });
    }
    
    private void loadUrl() {
        String url = getIntent().getStringExtra("url");
        if (url != null) {
            webView.loadUrl(url);
        }
    }
    
    private void injectSelectorScript() {
        String script = getSelectorScript();
        webView.evaluateJavascript(script, null);
    }
    
    private String getSelectorScript() {
        return "javascript:" +
            "var elementFinderBrowser = {" +
            "  generateCSSSelector: function(element) {" +
            "    if (!element) return '';" +
            "    " +
            "    // Priority 1: ID selector" +
            "    if (element.id) {" +
            "      var idSelector = '#' + element.id;" +
            "      if (document.querySelectorAll(idSelector).length === 1) {" +
            "        return idSelector;" +
            "      }" +
            "    }" +
            "    " +
            "    // Priority 2: Class selector with uniqueness check" +
            "    if (element.className) {" +
            "      var classes = element.className.split(' ').filter(c => c.length > 0);" +
            "      for (var i = 0; i < classes.length; i++) {" +
            "        var classSelector = '.' + classes[i];" +
            "        if (document.querySelectorAll(classSelector).length === 1) {" +
            "          return classSelector;" +
            "        }" +
            "      }" +
            "      " +
            "      // Try combination of classes" +
            "      if (classes.length > 1) {" +
            "        var combinedClass = '.' + classes.join('.');" +
            "        if (document.querySelectorAll(combinedClass).length === 1) {" +
            "          return combinedClass;" +
            "        }" +
            "      }" +
            "    }" +
            "    " +
            "    // Priority 3: Attribute-based selectors" +
            "    var attributes = ['name', 'type', 'value', 'placeholder', 'title', 'alt'];" +
            "    for (var attr of attributes) {" +
            "      if (element.hasAttribute(attr)) {" +
            "        var attrValue = element.getAttribute(attr);" +
            "        var attrSelector = element.tagName.toLowerCase() + '[' + attr + '=\"' + attrValue + '\"]';" +
            "        if (document.querySelectorAll(attrSelector).length === 1) {" +
            "          return attrSelector;" +
            "        }" +
            "      }" +
            "    }" +
            "    " +
            "    // Priority 4: nth-child selector" +
            "    var parent = element.parentElement;" +
            "    if (parent) {" +
            "      var siblings = Array.from(parent.children);" +
            "      var index = siblings.indexOf(element) + 1;" +
            "      var nthSelector = element.tagName.toLowerCase() + ':nth-child(' + index + ')';" +
            "      var parentSelector = this.generateCSSSelector(parent);" +
            "      if (parentSelector) {" +
            "        return parentSelector + ' > ' + nthSelector;" +
            "      }" +
            "    }" +
            "    " +
            "    return element.tagName.toLowerCase();" +
            "  }," +
            "  " +
            "  generateXPathSelector: function(element) {" +
            "    if (!element) return '';" +
            "    " +
            "    var xpath = '';" +
            "    var current = element;" +
            "    " +
            "    while (current && current.nodeType === Node.ELEMENT_NODE) {" +
            "      var tagName = current.tagName.toLowerCase();" +
            "      var index = 1;" +
            "      " +
            "      if (current.previousSibling) {" +
            "        var sibling = current.previousSibling;" +
            "        while (sibling) {" +
            "          if (sibling.nodeType === Node.ELEMENT_NODE && sibling.tagName === current.tagName) {" +
            "            index++;" +
            "          }" +
            "          sibling = sibling.previousSibling;" +
            "        }" +
            "      }" +
            "      " +
            "      xpath = '/' + tagName + '[' + index + ']' + xpath;" +
            "      current = current.parentElement;" +
            "    }" +
            "    " +
            "    return xpath;" +
            "  }," +
            "  " +
            "  getBestSelector: function(element) {" +
            "    var cssSelector = this.generateCSSSelector(element);" +
            "    var xpathSelector = this.generateXPathSelector(element);" +
            "    " +
            "    // Prefer CSS selector, fallback to XPath" +
            "    return {" +
            "      css: cssSelector," +
            "      xpath: xpathSelector," +
            "      recommended: cssSelector.length > 0 ? cssSelector : xpathSelector" +
            "    };" +
            "  }" +
            "};" +
            "" +
            "function handleLongPress(x, y) {" +
            "  var element = document.elementFromPoint(x, y);" +
            "  if (element) {" +
            "    var selectors = elementFinderBrowser.getBestSelector(element);" +
            "    Android.showSelectorDialog(JSON.stringify(selectors));" +
            "  }" +
            "}" +
            "" +
            "function recordClick(x, y) {" +
            "  var element = document.elementFromPoint(x, y);" +
            "  if (element) {" +
            "    var selectors = elementFinderBrowser.getBestSelector(element);" +
            "    Android.recordSelector(selectors.recommended);" +
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
        if (isRecordingMacro) {
            recordItem.setTitle("Stop Macro");
        } else {
            recordItem.setTitle("Record Macro");
        }
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
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
        WebSettings webSettings = webView.getSettings();
        String currentUserAgent = webSettings.getUserAgentString();
        
        if (currentUserAgent.contains("Mobile")) {
            // Switch to desktop mode
            String desktopUserAgent = currentUserAgent.replace("Mobile", "X11; Linux x86_64");
            webSettings.setUserAgentString(desktopUserAgent);
            Toast.makeText(this, "Desktop mode enabled", Toast.LENGTH_SHORT).show();
        } else {
            // Switch back to mobile mode
            webSettings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
            Toast.makeText(this, "Mobile mode enabled", Toast.LENGTH_SHORT).show();
        }
        
        webView.reload();
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
                        Toast.makeText(WebViewActivity.this, "Error parsing selectors", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        @JavascriptInterface
        public void recordSelector(String selector) {
            if (isRecordingMacro && !selector.isEmpty()) {
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
    }
    
    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, label + " copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}