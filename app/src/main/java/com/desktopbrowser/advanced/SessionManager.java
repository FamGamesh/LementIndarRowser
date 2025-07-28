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

public class SessionManager {
    private static final String PREFS_NAME = "RealDesktopBrowserSession";
    private static final String KEY_LAST_SESSION = "last_session";
    private static final String KEY_RECENT_SESSION = "recent_session";
    private static final String KEY_CURRENT_TAB_INDEX = "current_tab_index";
    private static final String KEY_PREMIUM_EXPIRY = "premium_expiry";
    
    private static SessionManager instance;
    private SharedPreferences prefs;
    private Gson gson;
    
    private SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // Session data structure
    public static class TabSession {
        public String url;
        public String title;
        public Bundle webViewState;
        public long timestamp;
        
        public TabSession(String url, String title, Bundle webViewState) {
            this.url = url;
            this.title = title;
            this.webViewState = webViewState;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static class BrowserSession {
        public List<TabSession> tabs;
        public int currentTabIndex;
        public long timestamp;
        
        public BrowserSession() {
            this.tabs = new ArrayList<>();
            this.currentTabIndex = 0;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    // Save current session as "recent session"
    public void saveRecentSession(BrowserSession session) {
        String sessionJson = gson.toJson(session);
        prefs.edit().putString(KEY_RECENT_SESSION, sessionJson).apply();
    }
    
    // Save current session as "last session" (for app close recovery)
    public void saveLastSession(BrowserSession session) {
        String sessionJson = gson.toJson(session);
        prefs.edit().putString(KEY_LAST_SESSION, sessionJson).apply();
    }
    
    // Get recent session (visible when home button pressed)
    public BrowserSession getRecentSession() {
        String sessionJson = prefs.getString(KEY_RECENT_SESSION, null);
        if (sessionJson != null) {
            Type type = new TypeToken<BrowserSession>(){}.getType();
            return gson.fromJson(sessionJson, type);
        }
        return null;
    }
    
    // Get last session (for app recovery)
    public BrowserSession getLastSession() {
        String sessionJson = prefs.getString(KEY_LAST_SESSION, null);
        if (sessionJson != null) {
            Type type = new TypeToken<BrowserSession>(){}.getType();
            return gson.fromJson(sessionJson, type);
        }
        return null;
    }
    
    // Check if recent session exists
    public boolean hasRecentSession() {
        return prefs.getString(KEY_RECENT_SESSION, null) != null;
    }
    
    // Check if last session exists
    public boolean hasLastSession() {
        return prefs.getString(KEY_LAST_SESSION, null) != null;
    }
    
    // Clear recent session (called when app starts fresh)
    public void clearRecentSession() {
        prefs.edit().remove(KEY_RECENT_SESSION).apply();
    }
    
    // Clear last session (called after successful recovery)
    public void clearLastSession() {
        prefs.edit().remove(KEY_LAST_SESSION).apply();
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
    
    // Helper method to create tab session from WebView with enhanced state capture
    public TabSession createTabSession(WebView webView, String url, String title) {
        Bundle webViewState = new Bundle();
        try {
            // Save comprehensive WebView state
            webView.saveState(webViewState);
            android.util.Log.d("SessionManager", "Created tab session for URL: " + url + " with state bundle size: " + webViewState.size());
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error saving WebView state", e);
        }
        return new TabSession(url, title, webViewState);
    }
    
    // Helper method to restore WebView from tab session with enhanced restoration
    public void restoreWebView(WebView webView, TabSession tabSession) {
        try {
            if (tabSession.webViewState != null && !tabSession.webViewState.isEmpty()) {
                android.util.Log.d("SessionManager", "Restoring WebView state for: " + tabSession.url);
                webView.restoreState(tabSession.webViewState);
            } else if (tabSession.url != null && !tabSession.url.isEmpty()) {
                android.util.Log.d("SessionManager", "Loading URL directly (no saved state): " + tabSession.url);
                webView.loadUrl(tabSession.url);
            } else {
                android.util.Log.w("SessionManager", "No URL or state to restore, loading Google");
                webView.loadUrl("https://www.google.com");
            }
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Error restoring WebView", e);
            // Fallback to loading URL directly
            if (tabSession.url != null && !tabSession.url.isEmpty()) {
                webView.loadUrl(tabSession.url);
            } else {
                webView.loadUrl("https://www.google.com");
            }
        }
    }
}