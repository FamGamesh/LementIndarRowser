package com.desktopbrowser.advanced;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {
    
    private EditText urlEditText;
    private Button browseButton;
    private Button menuButton;
    private GridLayout quickAccessGrid;
    private Button recentSessionButton;
    private Button openLastSessionButton;
    private LinearLayout mainContainer;
    
    private SessionManager sessionManager;
    private AdManager adManager;
    private boolean isReturningFromBrowser = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        try {
            // Initialize managers
            sessionManager = SessionManager.getInstance(this);
            adManager = AdManager.getInstance(this);
            
            initializeViews();
            setupClickListeners();
            setupQuickAccess();
            setupSessionButtons();
            setupAds();
            
            // Check and show premium status on startup
            android.os.Handler startupHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            startupHandler.postDelayed(() -> {
                sessionManager.checkAndShowPremiumStatus(this);
            }, 1000); // Show after 1 second delay
            
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Log the initialization error
            ErrorLogger.logError(this, "INIT_ERROR", e);
            
            e.printStackTrace();
        }
    }
    
    private void initializeViews() {
        urlEditText = findViewById(R.id.url_edit_text);
        browseButton = findViewById(R.id.browse_button);
        menuButton = findViewById(R.id.btn_menu);
        quickAccessGrid = findViewById(R.id.quick_access_grid);
        mainContainer = findViewById(R.id.main_container);
        
        if (urlEditText == null || browseButton == null || menuButton == null) {
            throw new RuntimeException("Failed to find required views");
        }
        
        // Check if returning from browser (recent session button should be visible)
        isReturningFromBrowser = getIntent().getBooleanExtra("returning_from_browser", false);
    }
    
    private void setupClickListeners() {
        browseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleBrowse();
            }
        });
        
        // Handle enter key press in URL field
        urlEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                handleBrowse();
                return true;
            }
            return false;
        });
        
        // Menu button click listener - shows dropdown menu with navigation options
        menuButton.setOnClickListener(v -> showNavigationMenu());
    }
    
    private void setupQuickAccess() {
        String[][] quickSites = {
            {"Wikipedia", "https://www.wikipedia.org", "wikipedia"},
            {"Instagram", "https://www.instagram.com", "instagram"},
            {"Reddit", "https://www.reddit.com", "reddit"},
            {"Twitter", "https://www.x.com", "twitter"},
            {"Facebook", "https://www.facebook.com", "facebook"},
            {"YouTube", "https://www.youtube.com", "youtube"}
        };
        
        for (String[] site : quickSites) {
            addQuickAccessSite(site[0], site[1], site[2]);
        }
    }
    
    private void addQuickAccessSite(String name, String url, String platform) {
        // Make URL final so it can be used in click listeners
        final String finalUrl = url;
        
        CardView card = new CardView(this);
        card.setCardElevation(12);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        card.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            card.setForeground(getDrawable(android.R.drawable.list_selector_background));
        } else {
            card.setForeground(getResources().getDrawable(android.R.drawable.list_selector_background));
        }
        
        // Create button with platform-specific background and icon
        Button button = new Button(this);
        button.setText(name);
        button.setTextColor(android.graphics.Color.WHITE);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setPadding(20, 20, 20, 20);
        
        // Set platform-specific background and icon
        switch (platform) {
            case "wikipedia":
                button.setBackgroundResource(R.drawable.quick_access_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_wikipedia, 0, 0);
                break;
            case "instagram":
                button.setBackgroundResource(R.drawable.instagram_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_instagram, 0, 0);
                break;
            case "reddit":
                button.setBackgroundResource(R.drawable.reddit_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_reddit_original, 0, 0);
                break;
            case "twitter":
                button.setBackgroundResource(R.drawable.twitter_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_twitter, 0, 0);
                break;
            case "facebook":
                button.setBackgroundResource(R.drawable.facebook_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_facebook, 0, 0);
                break;
            case "youtube":
                button.setBackgroundResource(R.drawable.youtube_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_youtube, 0, 0);
                break;
            default:
                button.setBackgroundResource(R.drawable.quick_access_background);
                break;
        }
        
        // Set drawable padding for better icon positioning
        button.setCompoundDrawablePadding(12);
        
        // Add click listener to button - Opens in minimal UI mode for quick access compliance
        button.setOnClickListener(v -> {
            android.util.Log.d("MainActivity", "🎯 Quick access button clicked: " + name + " -> " + finalUrl);
            // Open with minimal UI mode for quick access sites
            if (finalUrl != null && !finalUrl.isEmpty()) {
                android.util.Log.d("MainActivity", "✅ Opening Quick Access URL with minimal UI: " + finalUrl);
                openUrl(finalUrl, true); // true = quick access mode (minimal UI)
            } else {
                Toast.makeText(MainActivity.this, "URL not available", Toast.LENGTH_SHORT).show();
            }
        });
        
        card.addView(button);
        
        // Add click listener to card - Opens in minimal UI mode for quick access compliance
        card.setOnClickListener(v -> {
            android.util.Log.d("MainActivity", "🎯 Quick access card clicked: " + name + " -> " + finalUrl);
            // Open with minimal UI mode for quick access sites
            if (finalUrl != null && !finalUrl.isEmpty()) {
                android.util.Log.d("MainActivity", "✅ Opening Quick Access URL with minimal UI: " + finalUrl);
                openUrl(finalUrl, true); // true = quick access mode (minimal UI)
            } else {
                Toast.makeText(MainActivity.this, "URL not available", Toast.LENGTH_SHORT).show();
            }
        });
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(12, 12, 12, 12);
        
        card.setLayoutParams(params);
        quickAccessGrid.addView(card);
    }
    
    private void handleBrowse() {
        try {
            String input = urlEditText.getText().toString().trim();
            
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter a URL or search term", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String url = processInput(input);
            android.util.Log.d("MainActivity", "Search button pressed - URL: " + url);
            
            // Safety checks for managers
            if (sessionManager == null) {
                android.util.Log.e("MainActivity", "SessionManager is null, reinitializing...");
                sessionManager = SessionManager.getInstance(this);
            }
            
            if (adManager == null) {
                android.util.Log.e("MainActivity", "AdManager is null, reinitializing...");
                adManager = AdManager.getInstance(this);
            }
            
            // Simplified flow - just open URL directly for now to test functionality
            android.util.Log.d("MainActivity", "Opening URL directly: " + url);
            openUrl(url);
            
            /* Original ad flow - commenting out temporarily to test basic functionality
            // Check if premium is still active
            if (sessionManager.isPremiumActive()) {
                // User has active premium - show interstitial ad then browse directly
                String remaining = sessionManager.getRemainingPremiumTimeFormatted();
                Toast.makeText(this, "✨ Premium Active: " + remaining, Toast.LENGTH_SHORT).show();
                
                // Show interstitial ad for premium users too (each search gets an interstitial)
                adManager.showSearchAd(this, () -> {
                    android.util.Log.d("MainActivity", "Premium user - opening URL directly after interstitial: " + url);
                    openUrl(url);
                });
            } else {
                // User doesn't have premium - show interstitial ad then mandatory rewarded ad
                android.util.Log.d("MainActivity", "Non-premium user - showing interstitial then rewarded ad flow");
                adManager.showSearchAd(this, () -> {
                    // After interstitial ad, show mandatory rewarded ad for premium unlock
                    showMandatoryRewardedAd("Watch this ad to unlock premium desktop browsing experience for 1 hour!", 
                        () -> {
                            android.util.Log.d("MainActivity", "Non-premium user - opening URL after rewarded ad: " + url);
                            openUrl(url);
                        });
                });
            }
            */
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in handleBrowse", e);
            
            // Log the error to file
            ErrorLogger.logError(this, "BROWSE_ERROR", e);
            
            // Fallback - try to open URL directly without ads
            try {
                String input = urlEditText.getText().toString().trim();
                if (!input.isEmpty()) {
                    String url = processInput(input);
                    openUrl(url);
                }
            } catch (Exception fallbackException) {
                ErrorLogger.logError(this, "BROWSE_FALLBACK_ERROR", fallbackException);
                Toast.makeText(this, "Error opening browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void showMandatoryRewardedAd(String message, Runnable onSuccess) {
        showPremiumRewardedAdDialog(message, onSuccess);
    }
    
    private String processInput(String input) {
        try {
            if (input == null || input.trim().isEmpty()) {
                return "https://www.google.com";
            }
            
            input = input.trim();
            
            if (input.startsWith("http://") || input.startsWith("https://")) {
                return input;
            } else if (input.contains(".") && !input.contains(" ") && input.length() > 3) {
                return "https://" + input;
            } else {
                return "https://www.google.com/search?q=" + Uri.encode(input);
            }
        } catch (Exception e) {
            // Fallback to google search if encoding fails
            return "https://www.google.com/search?q=" + input.replace(" ", "+");
        }
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("is_quick_access_mode", false); // Normal mode for search
        startActivity(intent);
    }
    
    private void openUrl(String url, boolean isQuickAccess) {
        Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("is_quick_access_mode", isQuickAccess);
        startActivity(intent);
    }
    
    private void showNavigationMenu() {
        // Create dropdown menu with navigation options using custom theme for white text
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(this, R.style.CustomPopupMenuStyle);
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(contextThemeWrapper, menuButton);
        popupMenu.getMenuInflater().inflate(R.menu.main_navigation_menu, popupMenu.getMenu());
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_back) {
                // Navigate back in browser if possible
                Toast.makeText(this, "Open browser first to navigate", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_forward) {
                // Navigate forward in browser if possible
                Toast.makeText(this, "Open browser first to navigate", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_refresh) {
                // Refresh current page in browser if possible
                Toast.makeText(this, "Open browser first to refresh", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_home) {
                // Already on home screen
                Toast.makeText(this, "You're already on the home screen", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.menu_history) {
                openHistory();
                return true;
            } else if (id == R.id.menu_bookmarks) {
                openBookmarks();
                return true;
            } else if (id == R.id.menu_downloads) {
                openDownloads();
                return true;
            } else if (id == R.id.menu_settings) {
                openSettings();
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }
    
    private void openHistory() {
        // Show interstitial ad before opening history
        adManager.showInterstitialAd(this, "History", () -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });
    }
    
    private void openBookmarks() {
        // Show interstitial ad before opening bookmarks
        adManager.showInterstitialAd(this, "Bookmarks", () -> {
            Intent intent = new Intent(this, BookmarksActivity.class);
            startActivity(intent);
        });
    }
    
    private void openDownloads() {
        try {
            android.util.Log.d("MainActivity", "🎯 Downloads section clicked - attempting to open");
            
            // First try to open downloads directly to check if activity exists
            Intent intent = new Intent(this, DownloadsActivity.class);
            
            // Verify the activity exists
            if (intent.resolveActivity(getPackageManager()) != null) {
                android.util.Log.d("MainActivity", "📱 DownloadsActivity found, showing interstitial ad first");
                
                // Show interstitial ad before opening downloads section
                adManager.showDownloadsAd(this, () -> {
                    android.util.Log.d("MainActivity", "✅ Interstitial ad completed - opening downloads");
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error starting DownloadsActivity after ad", e);
                        Toast.makeText(this, "Error opening Downloads: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                android.util.Log.e("MainActivity", "❌ DownloadsActivity not found in manifest");
                Toast.makeText(this, "Downloads feature not available", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "💥 Error opening downloads section", e);
            Toast.makeText(this, "Error accessing Downloads: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void openSettings() {
        // Show interstitial ad before opening settings
        adManager.showInterstitialAd(this, "Settings", () -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }
    
    private void setupSessionButtons() {
        // Show "Open Last Session" button if app was closed and session exists
        if (!isReturningFromBrowser && sessionManager.hasLastSession()) {
            showOpenLastSessionButton();
        }
        
        // Show "Recent Session" button if returning from browser and recent session exists
        if (isReturningFromBrowser && sessionManager.hasRecentSession()) {
            showRecentSessionButton();
        }
    }
    
    private void setupAds() {
        // Add native ads to top and bottom of main container
        adManager.addBannerAdToLayout(this, mainContainer, true);  // Top ad
        adManager.addBannerAdToLayout(this, mainContainer, false); // Bottom ad
    }
    
    private void showOpenLastSessionButton() {
        openLastSessionButton = new Button(this);
        openLastSessionButton.setText("Open Last Session");
        openLastSessionButton.setBackgroundResource(R.drawable.golden_box_background);
        openLastSessionButton.setTextColor(getResources().getColor(android.R.color.black));
        openLastSessionButton.setTypeface(openLastSessionButton.getTypeface(), android.graphics.Typeface.BOLD);
        openLastSessionButton.setPadding(24, 16, 24, 16);
        openLastSessionButton.setElevation(8);
        
        // Add to layout
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(24, 16, 24, 16);
        
        openLastSessionButton.setOnClickListener(v -> handleOpenLastSession());
        
        // Insert after URL input section
        mainContainer.addView(openLastSessionButton, 2, params);
    }
    
    private void showRecentSessionButton() {
        recentSessionButton = new Button(this);
        recentSessionButton.setText("Recent Session");
        recentSessionButton.setBackgroundResource(R.drawable.button_background);
        recentSessionButton.setTextColor(getResources().getColor(android.R.color.white));
        recentSessionButton.setTypeface(recentSessionButton.getTypeface(), android.graphics.Typeface.BOLD);
        recentSessionButton.setPadding(24, 16, 24, 16);
        recentSessionButton.setElevation(6);
        
        // Add to layout
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(24, 16, 24, 16);
        
        recentSessionButton.setOnClickListener(v -> handleRecentSession());
        
        // Insert after navigation buttons
        mainContainer.addView(recentSessionButton, 4, params);
    }
    
    private void handleOpenLastSession() {
        android.util.Log.d("MainActivity", "Open Last Session clicked");
        
        // Check if session exists BEFORE showing ads
        if (!sessionManager.hasLastSession()) {
            android.util.Log.d("MainActivity", "No last session found");
            showSessionNotFoundDialog("No Previous Session Found", 
                "You haven't browsed any websites yet. Start browsing to create sessions that can be restored later!");
            return;
        }
        
        // ALWAYS show rewarded ad for "Open Last Session" - not bound by premium timer
        android.util.Log.d("MainActivity", "Showing rewarded ad for Open Last Session");
        showPremiumRewardedAdDialog("This premium feature allows you to restore your last browsing session with all tabs and data. Watch this ad to access your saved session!", 
            () -> {
                android.util.Log.d("MainActivity", "Rewarded ad completed - restoring last session");
                restoreLastSession();
            });
    }
    
    private void handleRecentSession() {
        android.util.Log.d("MainActivity", "Recent Session clicked");
        
        // Check if recent session exists BEFORE showing ads
        if (!sessionManager.hasRecentSession()) {
            android.util.Log.d("MainActivity", "No recent session found");
            showSessionNotFoundDialog("No Recent Session Found", 
                "You need to browse some websites first, then press the Home button to create a recent session. Come back here after that!");
            return;
        }
        
        // ALWAYS show interstitial ad before opening recent session (regardless of premium status)
        android.util.Log.d("MainActivity", "🎯 Showing interstitial ad for Recent Session");
        adManager.showRecentSessionAd(this, () -> {
            android.util.Log.d("MainActivity", "✅ Interstitial ad completed - restoring recent session");
            restoreRecentSession();
        });
    }
    
    private void restoreLastSession() {
        android.util.Log.d("MainActivity", "Attempting to restore last session");
        SessionManager.BrowserSession lastSession = sessionManager.getLastSession();
        if (lastSession != null && !lastSession.tabs.isEmpty()) {
            android.util.Log.d("MainActivity", "Last session found with " + lastSession.tabs.size() + " tabs");
            Intent intent = new Intent(this, BrowserActivity.class);
            intent.putExtra("restore_session", true);
            intent.putExtra("session_type", "last");
            startActivity(intent);
            sessionManager.clearLastSession(); // Clear after successful restoration
        } else {
            android.util.Log.e("MainActivity", "No last session data available");
            Toast.makeText(this, "No previous session found", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void restoreRecentSession() {
        android.util.Log.d("MainActivity", "Attempting to restore recent session");
        SessionManager.BrowserSession recentSession = sessionManager.getRecentSession();
        if (recentSession != null && !recentSession.tabs.isEmpty()) {
            android.util.Log.d("MainActivity", "Recent session found with " + recentSession.tabs.size() + " tabs");
            Intent intent = new Intent(this, BrowserActivity.class);
            intent.putExtra("restore_session", true);
            intent.putExtra("session_type", "recent");
            startActivity(intent);
        } else {
            android.util.Log.e("MainActivity", "No recent session data available");
            Toast.makeText(this, "No recent session found", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showPremiumRewardedAdDialog(String message, Runnable onSuccess) {
        // Create custom dialog with attractive design
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Inflate custom layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_premium_reward, null);
        builder.setView(dialogView);
        
        // Set custom message if provided
        TextView messageView = dialogView.findViewById(R.id.premium_message);
        if (message != null && !message.isEmpty()) {
            messageView.setText(message);
        }
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        
        // Set transparent background for custom styling
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Handle buttons
        Button watchAdButton = dialogView.findViewById(R.id.btn_watch_ad);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        
        watchAdButton.setOnClickListener(v -> {
            dialog.dismiss();
            adManager.showRewardedAd(this, new AdManager.RewardedAdCallback() {
                @Override
                public void onUserEarnedReward() {
                    // Grant 1 hour premium access
                    sessionManager.grantPremiumAccess(60 * 60 * 1000); // 1 hour
                    
                    // Show success toast with remaining time
                    String remainingTime = sessionManager.getRemainingPremiumTimeFormatted();
                    Toast.makeText(MainActivity.this, "🎉 Premium unlocked! " + remainingTime + " 🚀", Toast.LENGTH_LONG).show();
                }
                
                @Override
                public void onAdFailedToShow() {
                    Toast.makeText(MainActivity.this, "❌ Ad failed to load. Please try again later.", Toast.LENGTH_LONG).show();
                }
                
                @Override
                public void onAdClosed(boolean earnedReward) {
                    if (earnedReward) {
                        onSuccess.run();
                    }
                }
            });
        });
        
        // Only show cancel for non-mandatory scenarios
        if (onSuccess != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        } else {
            cancelButton.setVisibility(android.view.View.GONE);
        }
        
        dialog.show();
    }
    
    private void showSessionNotFoundDialog(String title, String message) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Inflate custom layout
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_premium_reward, null);
        builder.setView(dialogView);
        
        // Customize for session not found
        TextView messageView = dialogView.findViewById(R.id.premium_message);
        messageView.setText(message);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setCancelable(true);
        
        // Set transparent background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Handle buttons - hide watch ad button, show only OK
        Button watchAdButton = dialogView.findViewById(R.id.btn_watch_ad);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
        
        watchAdButton.setVisibility(android.view.View.GONE);
        cancelButton.setText("Got It!");
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Clear the returning flag when resuming
        if (isReturningFromBrowser) {
            getIntent().putExtra("returning_from_browser", false);
            isReturningFromBrowser = false;
        }
        
        // Emergency cleanup for any stuck ads (safety measure)
        if (adManager != null) {
            // Small delay to let the activity fully resume
            android.os.Handler resumeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            resumeHandler.postDelayed(() -> {
                try {
                    adManager.emergencyCleanupInterstitial();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }, 500);
        }
    }
}