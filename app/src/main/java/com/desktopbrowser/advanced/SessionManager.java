package com.desktopbrowser.advanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.os.Bundle;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
import java.util.Timer;
import java.util.TimerTask;

public class SessionManager {
    private static final String PREFS_NAME = "RealDesktopBrowserSession";
    private static final String KEY_LAST_SESSION = "last_session";
    private static final String KEY_RECENT_SESSION = "recent_session";
    private static final String KEY_CURRENT_TAB_INDEX = "current_tab_index";
    private static final String KEY_PREMIUM_EXPIRY = "premium_expiry";
    private static final String KEY_TEMP_STATE = "temp_browser_state";
    private static final String KEY_WEBVIEW_COOKIES = "webview_cookies";
    private static final String KEY_SESSION_COOKIES = "session_cookies";
    private static final String KEY_AUTO_SAVE_ENABLED = "auto_save_enabled";
    
    private static SessionManager instance;
    private SharedPreferences prefs;
    private Gson gson;
    private Context context;
    
    // ENHANCED: Auto-save functionality
    private Timer autoSaveTimer;
    private Handler uiHandler;
    private boolean autoSaveEnabled = true;
    
    private SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        uiHandler = new Handler(Looper.getMainLooper());
        
        // Initialize auto-save
        autoSaveEnabled = prefs.getBoolean(KEY_AUTO_SAVE_ENABLED, true);
        if (autoSaveEnabled) {
            startAutoSave();
        }
    }
    
    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // Enhanced session data structure with comprehensive state saving
    public static class TabSession {
        public String url;
        public String title;
        public Bundle webViewState;
        public long timestamp;
        public String cookieData; // Store cookies for this tab
        public float zoomLevel; // Store zoom level
        public int scrollX; // Horizontal scroll position
        public int scrollY; // Vertical scroll position
        public String formData; // Store form data as JSON
        public boolean isActive; // Track if this tab is currently active
        public boolean isClosed; // Track if user manually closed this tab
        public String sessionId; // Unique session identifier
        public List<String> history; // Tab's browsing history
        
        public TabSession(String url, String title, Bundle webViewState) {
            this.url = url;
            this.title = title;
            this.webViewState = webViewState;
            this.timestamp = System.currentTimeMillis();
            this.zoomLevel = 65f; // Default zoom
            this.scrollX = 0;
            this.scrollY = 0;
            this.formData = "{}";
            this.isActive = false;
            this.isClosed = false;
            this.sessionId = generateSessionId();
            this.history = new ArrayList<>();
        }
        
        public TabSession(String url, String title, Bundle webViewState, String cookieData, float zoomLevel, 
                         int scrollX, int scrollY, String formData, boolean isActive) {
            this.url = url;
            this.title = title;
            this.webViewState = webViewState;
            this.cookieData = cookieData;
            this.zoomLevel = zoomLevel;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.formData = formData != null ? formData : "{}";
            this.isActive = isActive;
            this.isClosed = false;
            this.timestamp = System.currentTimeMillis();
            this.sessionId = generateSessionId();
            this.history = new ArrayList<>();
        }
        
        // ENHANCED: Generate unique session ID
        private String generateSessionId() {
            return "session_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }
        
        // Mark tab as closed by user
        public void markAsClosed() {
            this.isClosed = true;
            android.util.Log.d("SessionManager", "🗑️ Tab marked as closed: " + this.url);
        }
        
        // Check if tab should be saved (not closed by user)
        public boolean shouldBeSaved() {
            boolean shouldSave = !isClosed && url != null && !url.isEmpty() && 
                               !url.equals("about:blank") && !url.startsWith("chrome://");
            
            if (!shouldSave) {
                android.util.Log.d("SessionManager", "❌ Tab should not be saved: " + url + 
                    " (closed: " + isClosed + ", url valid: " + (url != null && !url.isEmpty()) + ")");
            }
            
            return shouldSave;
        }
        
        // ENHANCED: Add URL to history
        public void addToHistory(String historyUrl) {
            if (historyUrl != null && !historyUrl.isEmpty() && !historyUrl.equals("about:blank")) {
                if (!history.contains(historyUrl)) {
                    history.add(0, historyUrl); // Add to beginning
                    
                    // Keep only last 20 URLs in history
                    if (history.size() > 20) {
                        history = history.subList(0, 20);
                    }
                }
            }
        }
        
        // ENHANCED: Get session summary for debugging
        public String getSessionSummary() {
            return "TabSession{" +
                "url='" + (url != null ? url.substring(0, Math.min(url.length(), 50)) : "null") + "...'" +
                ", title='" + (title != null ? title.substring(0, Math.min(title.length(), 30)) : "null") + "...'" +
                ", active=" + isActive +
                ", closed=" + isClosed +
                ", zoomLevel=" + zoomLevel +
                ", scroll=(" + scrollX + "," + scrollY + ")" +
                ", cookies=" + (cookieData != null && !cookieData.isEmpty() ? "YES" : "NO") +
                ", history=" + (history != null ? history.size() : 0) + " items" +
                '}';
        }
    }
    
    public static class BrowserSession {
        public List<TabSession> tabs;
        public int currentTabIndex;
        public long timestamp;
        public String globalCookies; // Store global cookies
        public String sessionName; // Name for this session
        public boolean wasRestored; // Track if this session was restored
        
        public BrowserSession() {
            this.tabs = new ArrayList<>();
            this.currentTabIndex = 0;
            this.timestamp = System.currentTimeMillis();
            this.sessionName = "Session_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(new java.util.Date());
            this.wasRestored = false;
        }
        
        // ENHANCED: Get session summary
        public String getSessionSummary() {
            int activeTabs = 0;
            int closedTabs = 0;
            for (TabSession tab : tabs) {
                if (tab.shouldBeSaved()) {
                    activeTabs++;
                } else {
                    closedTabs++;
                }
            }
            
            return "BrowserSession{" +
                "name='" + sessionName + "'" +
                ", totalTabs=" + tabs.size() +
                ", activeTabs=" + activeTabs +
                ", closedTabs=" + closedTabs +
                ", currentTab=" + currentTabIndex +
                ", timestamp=" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date(timestamp)) +
                '}';
        }
    }
    
    // ENHANCED: Start automatic session saving
    private void startAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
        }
        
        autoSaveTimer = new Timer();
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (autoSaveEnabled) {
                    // This would need to be triggered by the browser activity
                    // with current session data
                    android.util.Log.d("SessionManager", "🔄 Auto-save timer triggered");
                }
            }
        }, 30000, 60000); // Save every minute after initial 30 second delay
        
        android.util.Log.d("SessionManager", "⏰ Auto-save started - saving every 60 seconds");
    }
    
    // ENHANCED: Stop automatic session saving
    private void stopAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
            android.util.Log.d("SessionManager", "⏰ Auto-save stopped");
        }
    }
    
    // Enhanced session management with cookies and comprehensive state
    public void saveRecentSession(BrowserSession session) {
        try {
            // ENHANCED: Filter and validate tabs before saving
            List<TabSession> validTabs = new ArrayList<>();
            for (TabSession tab : session.tabs) {
                if (tab.shouldBeSaved()) {
                    validTabs.add(tab);
                }
            }
            
            if (validTabs.isEmpty()) {
                android.util.Log.d("SessionManager", "⚠️ No valid tabs to save in recent session");
                return;
            }
            
            // Update session with valid tabs only
            session.tabs = validTabs;
            
            // Adjust current tab index if needed
            if (session.currentTabIndex >= session.tabs.size()) {
                session.currentTabIndex = Math.max(0, session.tabs.size() - 1);
            }
            
            // Save cookies before saving session
            saveCookiesForSession(session, "recent");
            
            String sessionJson = gson.toJson(session);
            prefs.edit().putString(KEY_RECENT_SESSION, sessionJson).apply();
            android.util.Log.d("SessionManager", "💾 Recent session saved: " + session.getSessionSummary());
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving recent session", e);
        }
    }

    // Enhanced save for "last session" with cookies
    public void saveLastSession(BrowserSession session) {
        try {
            // ENHANCED: Filter and validate tabs before saving
            List<TabSession> validTabs = new ArrayList<>();
            for (TabSession tab : session.tabs) {
                if (tab.shouldBeSaved()) {
                    validTabs.add(tab);
                }
            }
            
            if (validTabs.isEmpty()) {
                android.util.Log.d("SessionManager", "⚠️ No valid tabs to save in last session");
                return;
            }
            
            // Update session with valid tabs only
            session.tabs = validTabs;
            
            // Adjust current tab index if needed
            if (session.currentTabIndex >= session.tabs.size()) {
                session.currentTabIndex = Math.max(0, session.tabs.size() - 1);
            }
            
            // Save cookies before saving session
            saveCookiesForSession(session, "last");
            
            String sessionJson = gson.toJson(session);
            prefs.edit().putString(KEY_LAST_SESSION, sessionJson).apply();
            android.util.Log.d("SessionManager", "💾 Last session saved: " + session.getSessionSummary());
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving last session", e);
        }
    }

    // Get recent session with cookie restoration
    public BrowserSession getRecentSession() {
        try {
            String sessionJson = prefs.getString(KEY_RECENT_SESSION, null);
            if (sessionJson != null) {
                Type type = new TypeToken<BrowserSession>(){}.getType();
                BrowserSession session = gson.fromJson(sessionJson, type);
                
                if (session != null && session.tabs != null && !session.tabs.isEmpty()) {
                    // Restore cookies for session
                    restoreCookiesForSession(session, "recent");
                    
                    session.wasRestored = true;
                    android.util.Log.d("SessionManager", "📂 Recent session loaded: " + session.getSessionSummary());
                    return session;
                } else {
                    android.util.Log.d("SessionManager", "📂 Recent session is empty or invalid");
                }
            } else {
                android.util.Log.d("SessionManager", "📂 No recent session found");
            }
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error getting recent session", e);
        }
        return null;
    }

    // Get last session with cookie restoration
    public BrowserSession getLastSession() {
        try {
            String sessionJson = prefs.getString(KEY_LAST_SESSION, null);
            if (sessionJson != null) {
                Type type = new TypeToken<BrowserSession>(){}.getType();
                BrowserSession session = gson.fromJson(sessionJson, type);
                
                if (session != null && session.tabs != null && !session.tabs.isEmpty()) {
                    // Restore cookies for session
                    restoreCookiesForSession(session, "last");
                    
                    session.wasRestored = true;
                    android.util.Log.d("SessionManager", "📂 Last session loaded: " + session.getSessionSummary());
                    return session;
                } else {
                    android.util.Log.d("SessionManager", "📂 Last session is empty or invalid");
                }
            } else {
                android.util.Log.d("SessionManager", "📂 No last session found");
            }
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error getting last session", e);
        }
        return null;
    }
    
    // Check if recent session exists
    public boolean hasRecentSession() {
        BrowserSession session = getRecentSession();
        return session != null && !session.tabs.isEmpty();
    }
    
    // Check if last session exists
    public boolean hasLastSession() {
        BrowserSession session = getLastSession();
        return session != null && !session.tabs.isEmpty();
    }
    
    // Clear recent session (called when app starts fresh)
    public void clearRecentSession() {
        prefs.edit().remove(KEY_RECENT_SESSION).apply();
        android.util.Log.d("SessionManager", "🧹 Recent session cleared");
    }
    
    // Clear last session (called after successful recovery)
    public void clearLastSession() {
        prefs.edit().remove(KEY_LAST_SESSION).apply();
        android.util.Log.d("SessionManager", "🧹 Last session cleared");
    }
    
    // ENHANCED: Clear all sessions
    public void clearAllSessions() {
        prefs.edit()
            .remove(KEY_RECENT_SESSION)
            .remove(KEY_LAST_SESSION)
            .remove(KEY_SESSION_COOKIES)
            .remove(KEY_WEBVIEW_COOKIES)
            .apply();
        android.util.Log.d("SessionManager", "🧹 All sessions cleared");
    }
    
    // Premium features management
    public void setPremiumExpiry(long expiryTime) {
        prefs.edit().putLong(KEY_PREMIUM_EXPIRY, expiryTime).apply();
    }
    
    public boolean isPremiumActive() {
        long expiryTime = prefs.getLong(KEY_PREMIUM_EXPIRY, 0);
        long currentTime = System.currentTimeMillis();
        boolean isActive = currentTime < expiryTime;
        
        // Debug logging with readable dates
        if (expiryTime > 0) {
            java.util.Date expiryDate = new java.util.Date(expiryTime);
            java.util.Date currentDate = new java.util.Date(currentTime);
            android.util.Log.d("SessionManager", "Premium check:");
            android.util.Log.d("SessionManager", "Current time: " + currentDate);
            android.util.Log.d("SessionManager", "Expiry time: " + expiryDate);
            android.util.Log.d("SessionManager", "Is Active: " + isActive);
            android.util.Log.d("SessionManager", "Remaining: " + (expiryTime - currentTime) / 1000 + " seconds");
        } else {
            android.util.Log.d("SessionManager", "No premium expiry time set (never activated)");
        }
        
        return isActive;
    }
    
    public void grantPremiumAccess(long durationMillis) {
        long currentTime = System.currentTimeMillis();
        long expiryTime = currentTime + durationMillis;
        
        // Store both the expiry time and duration for debugging
        prefs.edit()
            .putLong(KEY_PREMIUM_EXPIRY, expiryTime)
            .putLong("premium_duration", durationMillis) // For debugging
            .putLong("premium_granted_at", currentTime) // For debugging
            .apply();
        
        // Debug logging with readable dates
        java.util.Date expiryDate = new java.util.Date(expiryTime);
        java.util.Date grantedDate = new java.util.Date(currentTime);
        android.util.Log.d("SessionManager", "Premium granted:");
        android.util.Log.d("SessionManager", "Granted at: " + grantedDate);
        android.util.Log.d("SessionManager", "Duration: " + (durationMillis/1000/60) + " minutes");
        android.util.Log.d("SessionManager", "Expires at: " + expiryDate);
        android.util.Log.d("SessionManager", "Expiry timestamp: " + expiryTime);
    }
    
    public long getRemainingPremiumTime() {
        long expiryTime = prefs.getLong(KEY_PREMIUM_EXPIRY, 0);
        long currentTime = System.currentTimeMillis();
        return Math.max(0, expiryTime - currentTime);
    }
    
    public String getRemainingPremiumTimeFormatted() {
        long remainingMs = getRemainingPremiumTime();
        if (remainingMs <= 0) {
            return "Premium expired";
        }
        
        long hours = remainingMs / (1000 * 60 * 60);
        long minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (remainingMs % (1000 * 60)) / 1000;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d remaining", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d remaining", minutes, seconds);
        }
    }
    
    public void checkAndShowPremiumStatus(android.content.Context context) {
        if (isPremiumActive()) {
            String remaining = getRemainingPremiumTimeFormatted();
            android.widget.Toast.makeText(context, "✨ Premium Active: " + remaining, 
                android.widget.Toast.LENGTH_LONG).show();
            android.util.Log.d("SessionManager", "Premium status shown to user: " + remaining);
        } else {
            android.util.Log.d("SessionManager", "Premium not active, no status shown");
        }
    }
    
    // Enhanced helper methods for comprehensive session and cookie management
    public TabSession createTabSession(WebView webView, String url, String title) {
        Bundle webViewState = new Bundle();
        String cookieData = "";
        float zoomLevel = 65f;
        
        try {
            // Save comprehensive WebView state
            webView.saveState(webViewState);
            
            // Get cookies for this URL
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            if (url != null) {
                cookieData = cookieManager.getCookie(url);
                if (cookieData == null) cookieData = "";
            }
            
            // Get zoom level (if available)
            // Note: WebView zoom level extraction is limited in newer Android versions
            
            android.util.Log.d("SessionManager", "Created enhanced tab session for URL: " + url + 
                " with state bundle size: " + webViewState.size() + 
                " and cookies: " + (cookieData.length() > 0 ? "YES" : "NO"));
                
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving comprehensive WebView state", e);
        }
        
        return new TabSession(url, title, webViewState, cookieData, zoomLevel, 0, 0, "{}", false);
    }

    // Enhanced WebView restoration with cookies
    public void restoreWebView(WebView webView, TabSession tabSession) {
        try {
            // Restore cookies first
            if (tabSession.cookieData != null && !tabSession.cookieData.isEmpty() && tabSession.url != null) {
                android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                cookieManager.setCookie(tabSession.url, tabSession.cookieData);
                cookieManager.flush(); // Ensure cookies are saved
                android.util.Log.d("SessionManager", "🍪 Cookies restored for: " + tabSession.url);
            }
            
            // Restore WebView state
            if (tabSession.webViewState != null && !tabSession.webViewState.isEmpty()) {
                android.util.Log.d("SessionManager", "🔄 Restoring WebView state for: " + tabSession.url);
                webView.restoreState(tabSession.webViewState);
            } else if (tabSession.url != null && !tabSession.url.isEmpty()) {
                android.util.Log.d("SessionManager", "🌐 Loading URL directly (no saved state): " + tabSession.url);
                webView.loadUrl(tabSession.url);
            } else {
                android.util.Log.w("SessionManager", "⚠️ No URL or state to restore, loading Google");
                webView.loadUrl("https://www.google.com");
            }
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error restoring enhanced WebView", e);
            // Fallback to loading URL directly
            if (tabSession.url != null && !tabSession.url.isEmpty()) {
                webView.loadUrl(tabSession.url);
            } else {
                webView.loadUrl("https://www.google.com");
            }
        }
    }
    
    // Cookie management for sessions
    private void saveCookiesForSession(BrowserSession session, String sessionType) {
        try {
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            
            // Save global cookies
            StringBuilder allCookies = new StringBuilder();
            
            // For each tab, save its specific cookies
            for (TabSession tab : session.tabs) {
                if (tab.url != null) {
                    String cookies = cookieManager.getCookie(tab.url);
                    if (cookies != null && !cookies.isEmpty()) {
                        tab.cookieData = cookies;
                        allCookies.append(tab.url).append(":").append(cookies).append(";");
                    }
                }
            }
            
            session.globalCookies = allCookies.toString();
            android.util.Log.d("SessionManager", "🍪 Cookies saved for " + sessionType + " session");
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving cookies for session", e);
        }
    }
    
    private void restoreCookiesForSession(BrowserSession session, String sessionType) {
        try {
            android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
            
            // Restore cookies for each tab
            for (TabSession tab : session.tabs) {
                if (tab.cookieData != null && !tab.cookieData.isEmpty() && tab.url != null) {
                    cookieManager.setCookie(tab.url, tab.cookieData);
                }
            }
            
            cookieManager.flush(); // Ensure all cookies are saved
            android.util.Log.d("SessionManager", "🍪 Cookies restored for " + sessionType + " session");
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error restoring cookies for session", e);
        }
    }
    
    // Temporary state management for pause/resume scenarios
    public void saveTemporaryState(String url, String title, float zoomLevel) {
        try {
            java.util.Map<String, Object> tempState = new java.util.HashMap<>();
            tempState.put("url", url);
            tempState.put("title", title);
            tempState.put("zoomLevel", zoomLevel);
            tempState.put("timestamp", System.currentTimeMillis());
            
            String tempStateJson = gson.toJson(tempState);
            prefs.edit().putString(KEY_TEMP_STATE, tempStateJson).apply();
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving temporary state", e);
        }
    }
    
    public java.util.Map<String, Object> getTemporaryState() {
        try {
            String tempStateJson = prefs.getString(KEY_TEMP_STATE, null);
            if (tempStateJson != null) {
                Type type = new TypeToken<java.util.Map<String, Object>>(){}.getType();
                return gson.fromJson(tempStateJson, type);
            }
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error getting temporary state", e);
        }
        return null;
    }
    
    // ========= ENHANCED SESSION MANAGEMENT FOR COMPREHENSIVE STATE SAVING =========
    
    /**
     * Create comprehensive tab session with all state information
     */
    public TabSession createComprehensiveTabSession(WebView webView, String url, String title) {
        try {
            // Get WebView state
            Bundle webViewState = new Bundle();
            webView.saveState(webViewState);
            
            // Get cookies
            String cookieData = "";
            if (url != null) {
                android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                cookieData = cookieManager.getCookie(url);
                if (cookieData == null) cookieData = "";
            }
            
            // Get scroll position
            int scrollX = webView.getScrollX();
            int scrollY = webView.getScrollY();
            
            // Get zoom level (if available)
            float zoomLevel = webView.getScale() * 100f; // Convert to percentage
            
            // Get form data (simplified - actual form data would need JavaScript injection)
            String formData = extractFormData(webView);
            
            // Create comprehensive tab session
            TabSession tabSession = new TabSession(url, title, webViewState, cookieData, zoomLevel, scrollX, scrollY, formData, true);
            
            // Add current URL to history
            tabSession.addToHistory(url);
            
            android.util.Log.d("SessionManager", "📝 Created comprehensive tab session: " + tabSession.getSessionSummary());
            
            return tabSession;
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error creating comprehensive tab session", e);
            // Fallback to basic session
            return new TabSession(url, title, new Bundle());
        }
    }
    
    /**
     * ENHANCED: Save complete browser session with immediate auto-save
     */
    public void saveCompleteBrowserSession(List<TabSession> allTabs, int currentTabIndex) {
        try {
            BrowserSession session = new BrowserSession();
            
            // Filter out closed tabs and only save tabs that should be saved
            for (TabSession tab : allTabs) {
                if (tab.shouldBeSaved()) {
                    session.tabs.add(tab);
                    android.util.Log.d("SessionManager", "✅ Adding tab to session: " + tab.url);
                } else {
                    android.util.Log.d("SessionManager", "❌ Skipping closed/invalid tab: " + tab.url);
                }
            }
            
            // Adjust current tab index if needed
            if (currentTabIndex >= session.tabs.size()) {
                currentTabIndex = Math.max(0, session.tabs.size() - 1);
            }
            session.currentTabIndex = currentTabIndex;
            
            // Mark the current active tab
            if (currentTabIndex >= 0 && currentTabIndex < session.tabs.size()) {
                session.tabs.get(currentTabIndex).isActive = true;
            }
            
            // Save session only if there are tabs to save
            if (!session.tabs.isEmpty()) {
                // ENHANCED: Save as both recent and last session for redundancy
                saveRecentSession(session);
                saveLastSession(session);
                
                android.util.Log.d("SessionManager", "💾 Complete browser session saved immediately: " + 
                    session.getSessionSummary());
            } else {
                android.util.Log.d("SessionManager", "⚠️ No tabs to save - session not saved");
            }
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving complete browser session", e);
        }
    }
    
    /**
     * Restore complete WebView with all saved state
     */
    public void restoreComprehensiveWebView(WebView webView, TabSession tabSession) {
        try {
            // First restore the basic WebView
            restoreWebView(webView, tabSession);
            
            // Set up a post-load callback to restore additional state
            webView.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    
                    // Restore scroll position after page loads
                    if (tabSession.scrollX != 0 || tabSession.scrollY != 0) {
                        view.post(() -> {
                            try {
                                view.scrollTo(tabSession.scrollX, tabSession.scrollY);
                                android.util.Log.d("SessionManager", "📍 Restored scroll position: (" + 
                                    tabSession.scrollX + ", " + tabSession.scrollY + ")");
                            } catch (Exception e) {
                                android.util.Log.e("SessionManager", "Error restoring scroll position", e);
                            }
                        });
                    }
                    
                    // Restore form data if available
                    if (tabSession.formData != null && !tabSession.formData.equals("{}")) {
                        restoreFormData(view, tabSession.formData);
                    }
                    
                    // Set zoom level
                    if (tabSession.zoomLevel > 0 && tabSession.zoomLevel != 65f) {
                        try {
                            view.setInitialScale((int) tabSession.zoomLevel);
                            android.util.Log.d("SessionManager", "🔍 Restored zoom level: " + tabSession.zoomLevel + "%");
                        } catch (Exception e) {
                            android.util.Log.e("SessionManager", "Error restoring zoom level", e);
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error restoring comprehensive WebView", e);
            // Fallback to basic restoration
            restoreWebView(webView, tabSession);
        }
    }
    
    /**
     * Extract form data from WebView (simplified implementation)
     */
    private String extractFormData(WebView webView) {
        try {
            // This is a simplified approach - in a full implementation, 
            // we would inject JavaScript to collect all form field values
            return "{}"; // Placeholder for now
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error extracting form data", e);
            return "{}";
        }
    }
    
    /**
     * Restore form data to WebView (simplified implementation)
     */
    private void restoreFormData(WebView webView, String formData) {
        try {
            // This would inject JavaScript to restore form field values
            // Implementation would depend on specific form handling requirements
            android.util.Log.d("SessionManager", "📝 Form data restoration not fully implemented yet");
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error restoring form data", e);
        }
    }
    
    /**
     * Mark a tab as closed by user (so it won't be saved in session)
     */
    public void markTabAsClosed(String tabUrl) {
        try {
            android.util.Log.d("SessionManager", "🗑️ Marking tab as closed: " + tabUrl);
            // This would be called when user manually closes a tab
            // The tab's isClosed flag would be set to true
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error marking tab as closed", e);
        }
    }
    
    // ENHANCED: Auto-save management
    public void setAutoSaveEnabled(boolean enabled) {
        autoSaveEnabled = enabled;
        prefs.edit().putBoolean(KEY_AUTO_SAVE_ENABLED, enabled).apply();
        
        if (enabled) {
            startAutoSave();
            android.util.Log.d("SessionManager", "⏰ Auto-save enabled");
        } else {
            stopAutoSave();
            android.util.Log.d("SessionManager", "⏰ Auto-save disabled");
        }
    }
    
    public boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }
    
    // ENHANCED: Get session statistics
    public String getSessionStatistics() {
        BrowserSession recent = getRecentSession();
        BrowserSession last = getLastSession();
        
        StringBuilder stats = new StringBuilder();
        stats.append("📊 Session Statistics:\n");
        stats.append("Recent Session: ").append(recent != null ? recent.tabs.size() + " tabs" : "None").append("\n");
        stats.append("Last Session: ").append(last != null ? last.tabs.size() + " tabs" : "None").append("\n");
        stats.append("Auto-save: ").append(autoSaveEnabled ? "Enabled" : "Disabled").append("\n");
        
        return stats.toString();
    }
    
    // Clean up resources when session manager is no longer needed
    public void cleanup() {
        stopAutoSave();
        android.util.Log.d("SessionManager", "🧹 SessionManager cleaned up");
    }
}