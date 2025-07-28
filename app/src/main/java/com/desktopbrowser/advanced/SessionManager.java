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
        
        // Debug logging
        android.util.Log.d("SessionManager", "Premium check - Current: " + currentTime + ", Expiry: " + expiryTime + ", Active: " + isActive);
        
        return isActive;
    }
    
    public void grantPremiumAccess(long durationMillis) {
        long currentTime = System.currentTimeMillis();
        long expiryTime = currentTime + durationMillis;
        setPremiumExpiry(expiryTime);
        
        // Debug logging
        android.util.Log.d("SessionManager", "Premium granted - Duration: " + durationMillis + "ms (" + (durationMillis/1000/60) + " minutes)");
        android.util.Log.d("SessionManager", "Premium expires at: " + new java.util.Date(expiryTime));
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
        
        long minutes = remainingMs / (1000 * 60);
        long seconds = (remainingMs % (1000 * 60)) / 1000;
        
        return String.format("%02d:%02d remaining", minutes, seconds);
    }
    
    // Helper method to create tab session from WebView
    public TabSession createTabSession(WebView webView, String url, String title) {
        Bundle webViewState = new Bundle();
        webView.saveState(webViewState);
        return new TabSession(url, title, webViewState);
    }
    
    // Helper method to restore WebView from tab session
    public void restoreWebView(WebView webView, TabSession tabSession) {
        if (tabSession.webViewState != null) {
            webView.restoreState(tabSession.webViewState);
        } else if (tabSession.url != null) {
            webView.loadUrl(tabSession.url);
        }
    }
}