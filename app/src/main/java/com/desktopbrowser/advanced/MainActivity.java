package com.desktopbrowser.advanced;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
            e.printStackTrace();
        }
    }
    
    private void initializeViews() {
        urlEditText = findViewById(R.id.url_edit_text);
        browseButton = findViewById(R.id.browse_button);
        quickAccessGrid = findViewById(R.id.quick_access_grid);
        mainContainer = findViewById(R.id.main_container);
        
        if (urlEditText == null || browseButton == null) {
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
        
        // Navigation buttons
        findViewById(R.id.btn_history).setOnClickListener(v -> openHistory());
        findViewById(R.id.btn_bookmarks).setOnClickListener(v -> openBookmarks());
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
    }
    
    private void setupQuickAccess() {
        String[][] quickSites = {
            {"Google", "https://www.google.com", "google"},
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
            case "google":
                button.setBackgroundResource(R.drawable.quick_access_background);
                button.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_google, 0, 0);
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
        
        // Add click listener to button as well for better responsiveness
        button.setOnClickListener(v -> {
            // Show interstitial ad before opening quick access sites
            try {
                adManager.showQuickAccessAd(MainActivity.this, () -> {
                    if (url != null && !url.isEmpty()) {
                        openUrl(url);
                    } else {
                        Toast.makeText(MainActivity.this, "URL not available", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                // Fallback: open URL directly if ad fails
                if (url != null && !url.isEmpty()) {
                    openUrl(url);
                } else {
                    Toast.makeText(MainActivity.this, "URL not available", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        card.addView(button);
        
        card.setOnClickListener(v -> {
            // Show interstitial ad before opening quick access sites
            try {
                adManager.showQuickAccessAd(MainActivity.this, () -> {
                    if (url != null && !url.isEmpty()) {
                        openUrl(url);
                    } else {
                        Toast.makeText(MainActivity.this, "URL not available", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                // Fallback: open URL directly if ad fails
                if (url != null && !url.isEmpty()) {
                    openUrl(url);
                } else {
                    Toast.makeText(MainActivity.this, "URL not available", Toast.LENGTH_SHORT).show();
                }
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
            
            // Check if premium is still active
            if (sessionManager.isPremiumActive()) {
                // User has active premium, browse directly
                String remaining = sessionManager.getRemainingPremiumTimeFormatted();
                Toast.makeText(this, "✨ Premium Active: " + remaining, Toast.LENGTH_SHORT).show();
                openUrl(url);
            } else {
                // Show interstitial ad first, then show mandatory rewarded ad
                adManager.showSearchAd(this, () -> {
                    showMandatoryRewardedAd("Watch this ad to unlock premium desktop browsing experience for 1 hour!", 
                        () -> openUrl(url));
                });
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Error opening browser: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void showMandatoryRewardedAd(String message, Runnable onSuccess) {
        showPremiumRewardedAdDialog(message, onSuccess);
    }
    
    private String processInput(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        } else if (input.contains(".") && !input.contains(" ") && input.length() > 3) {
            return "https://" + input;
        } else {
            return "https://www.google.com/search?q=" + android.net.Uri.encode(input);
        }
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
    
    private void openHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }
    
    private void openBookmarks() {
        Intent intent = new Intent(this, BookmarksActivity.class);
        startActivity(intent);
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
        // Check if session exists BEFORE showing ads
        if (!sessionManager.hasLastSession()) {
            showSessionNotFoundDialog("No Previous Session Found", 
                "You haven't browsed any websites yet. Start browsing to create sessions that can be restored later!");
            return;
        }
        
        if (sessionManager.isPremiumActive()) {
            // User has premium access, restore session directly
            restoreLastSession();
        } else {
            // Show premium message and rewarded ad
            showPremiumRewardedAdDialog("This premium feature allows you to restore your last browsing session. Watch this ad to unlock premium features for 1 hour!", 
                this::restoreLastSession);
        }
    }
    
    private void handleRecentSession() {
        // Check if recent session exists BEFORE showing ads
        if (!sessionManager.hasRecentSession()) {
            showSessionNotFoundDialog("No Recent Session Found", 
                "You need to browse some websites first, then press the Home button to create a recent session. Come back here after that!");
            return;
        }
        
        // Show interstitial ad before opening recent session
        try {
            adManager.showRecentSessionAd(this, this::restoreRecentSession);
        } catch (Exception e) {
            // Fallback if ad fails
            restoreRecentSession();
        }
    }
    
    private void restoreLastSession() {
        SessionManager.BrowserSession lastSession = sessionManager.getLastSession();
        if (lastSession != null) {
            Intent intent = new Intent(this, BrowserActivity.class);
            intent.putExtra("restore_session", true);
            intent.putExtra("session_type", "last");
            startActivity(intent);
            sessionManager.clearLastSession(); // Clear after successful restoration
        } else {
            Toast.makeText(this, "No previous session found", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void restoreRecentSession() {
        SessionManager.BrowserSession recentSession = sessionManager.getRecentSession();
        if (recentSession != null) {
            Intent intent = new Intent(this, BrowserActivity.class);
            intent.putExtra("restore_session", true);
            intent.putExtra("session_type", "recent");
            startActivity(intent);
        } else {
            Toast.makeText(this, "No recent session found", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
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
    }
}