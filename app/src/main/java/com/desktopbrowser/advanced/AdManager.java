package com.desktopbrowser.advanced;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class AdManager {
    private static final String TAG = "AdManager";
    
    // Test Ad Unit IDs
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110";
    
    private static AdManager instance;
    private Context context;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private long lastInterstitialTime = 0;
    private static final long INTERSTITIAL_COOLDOWN = 15 * 60 * 1000; // 15 minutes
    
    // INTELLIGENT AD STATE MANAGEMENT
    private boolean isShowingInterstitial = false;
    private boolean isShowingRewarded = false;
    private static final long AD_CLICK_DEBOUNCE = 2000; // 2 seconds debounce
    private long lastAdClickTime = 0;
    
    // INTELLIGENT CALLBACK MANAGEMENT - PREVENT CALLBACK LEAKS
    private FullScreenContentCallback currentInterstitialCallback = null;
    private FullScreenContentCallback currentRewardedCallback = null;
    private String currentAdContext = ""; // Track where ad was triggered from
    
    private AdManager(Context context) {
        this.context = context.getApplicationContext();
        initializeAds();
    }
    
    public static AdManager getInstance(Context context) {
        if (instance == null) {
            instance = new AdManager(context);
        }
        return instance;
    }
    
    private void initializeAds() {
        MobileAds.initialize(context, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                Log.d(TAG, "🚀 AdMob initialized successfully with intelligent callback management");
                loadInterstitialAd();
                loadRewardedAd();
            }
        });
    }
    
    // Banner Ad Methods
    public AdView createBannerAd(Activity activity) {
        AdView adView = new AdView(activity);
        adView.setAdUnitId(BANNER_AD_UNIT_ID);
        adView.setAdSize(AdSize.BANNER);
        
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
        
        return adView;
    }
    
    public void addBannerAdToLayout(Activity activity, ViewGroup container, boolean isTop) {
        AdView bannerAd = createBannerAd(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        
        if (isTop) {
            container.addView(bannerAd, 0, params);
        } else {
            container.addView(bannerAd, params);
        }
    }
    
    // INTELLIGENT INTERSTITIAL AD LOADING WITH CALLBACK SETUP
    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(InterstitialAd ad) {
                    interstitialAd = ad;
                    Log.d(TAG, "✅ Interstitial ad loaded and ready");
                    
                    // INTELLIGENT APPROACH: Set up callback during LOAD phase, not SHOW phase
                    setupInterstitialCallback();
                }
                
                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    Log.e(TAG, "❌ Interstitial ad failed to load: " + loadAdError.getMessage());
                    interstitialAd = null;
                    currentInterstitialCallback = null;
                }
            });
    }
    
    // INTELLIGENT CALLBACK SETUP - PREVENTS LEAKS AND INTERFERENCE
    private void setupInterstitialCallback() {
        if (interstitialAd == null) return;
        
        // CRITICAL: Clear any existing callback to prevent interference
        if (currentInterstitialCallback != null) {
            Log.d(TAG, "🧹 Clearing existing interstitial callback to prevent leaks");
            interstitialAd.setFullScreenContentCallback(null);
        }
        
        // Create new callback instance
        currentInterstitialCallback = new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "🎯 Interstitial ad dismissed by user - Context: " + currentAdContext);
                handleInterstitialAdClosed();
            }
            
            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                Log.e(TAG, "❌ Interstitial ad failed to show: " + adError.getMessage() + " - Context: " + currentAdContext);
                handleInterstitialAdFailure();
            }
            
            @Override
            public void onAdShowedFullScreenContent() {
                Log.d(TAG, "✨ Interstitial ad showed successfully - Context: " + currentAdContext + " - Close button should be responsive");
            }
            
            @Override
            public void onAdClicked() {
                Log.d(TAG, "👆 Interstitial ad clicked - Context: " + currentAdContext);
            }
            
            @Override
            public void onAdImpression() {
                Log.d(TAG, "👁️ Interstitial ad impression recorded - Context: " + currentAdContext);
            }
        };
        
        // Set the new callback
        interstitialAd.setFullScreenContentCallback(currentInterstitialCallback);
        Log.d(TAG, "🔧 New interstitial callback set up successfully");
    }
    
    // INTELLIGENT UNIVERSAL INTERSTITIAL AD SHOW METHOD
    public void showInterstitialAd(Activity activity, String context, Runnable onAdClosed) {
        currentAdContext = context; // Track context for debugging
        Log.d(TAG, "🎬 Attempting to show interstitial ad - Context: " + context);
        
        // Prevent multiple ad operations and add debouncing
        long currentTime = System.currentTimeMillis();
        if (isShowingInterstitial || (currentTime - lastAdClickTime) < AD_CLICK_DEBOUNCE) {
            Log.d(TAG, "⏸️ Ad operation already in progress or too recent, skipping - Context: " + context);
            if (onAdClosed != null) {
                executeCallback(onAdClosed, "debounced");
            }
            return;
        }
        
        // Additional safety check for activity state
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "⚠️ Activity is not in valid state for showing ads - Context: " + context);
            if (onAdClosed != null) {
                executeCallback(onAdClosed, "invalid_activity");
            }
            return;
        }
        
        lastAdClickTime = currentTime;
        
        if (interstitialAd != null) {
            isShowingInterstitial = true;
            
            // Store callback for later execution
            currentOnAdClosedCallback = onAdClosed;
            
            try {
                interstitialAd.show(activity);
                lastInterstitialTime = System.currentTimeMillis();
                Log.d(TAG, "🎯 Interstitial ad.show() called successfully - Context: " + context);
            } catch (Exception e) {
                Log.e(TAG, "💥 Error showing interstitial ad - Context: " + context, e);
                handleInterstitialAdFailure();
            }
        } else {
            Log.d(TAG, "📭 Interstitial ad not ready, proceeding without ad - Context: " + context);
            if (onAdClosed != null) {
                executeCallback(onAdClosed, "no_ad");
            }
        }
    }
    
    // INTELLIGENT CALLBACK STORAGE
    private Runnable currentOnAdClosedCallback = null;
    
    // INTELLIGENT CLEANUP HANDLER
    private void handleInterstitialAdClosed() {
        Log.d(TAG, "🧹 Starting intelligent interstitial cleanup - Context: " + currentAdContext);
        
        // Use handler with optimal delay for complete ad dismissal
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.postDelayed(() -> {
            try {
                // Reset state flags
                isShowingInterstitial = false;
                
                // Clear callback reference to prevent leaks
                currentInterstitialCallback = null;
                interstitialAd = null;
                
                // Execute stored callback
                if (currentOnAdClosedCallback != null) {
                    executeCallback(currentOnAdClosedCallback, "success");
                    currentOnAdClosedCallback = null;
                }
                
                // Load next ad
                loadInterstitialAd();
                
                Log.d(TAG, "✅ Intelligent interstitial cleanup completed successfully - Context: " + currentAdContext);
                currentAdContext = "";
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error in intelligent interstitial cleanup", e);
                forceResetInterstitialState();
            }
        }, 1200); // Optimal delay for complete dismissal
    }
    
    // INTELLIGENT FAILURE HANDLER
    private void handleInterstitialAdFailure() {
        Log.d(TAG, "🔧 Handling interstitial ad failure - Context: " + currentAdContext);
        
        // Immediate cleanup for failures since no ad is showing
        isShowingInterstitial = false;
        currentInterstitialCallback = null;
        interstitialAd = null;
        
        // Execute callback
        if (currentOnAdClosedCallback != null) {
            executeCallback(currentOnAdClosedCallback, "failure");
            currentOnAdClosedCallback = null;
        }
        
        // Load new ad
        loadInterstitialAd();
        
        Log.d(TAG, "✅ Interstitial failure handled - Context: " + currentAdContext);
        currentAdContext = "";
    }
    
    // INTELLIGENT CALLBACK EXECUTION WITH ERROR HANDLING
    private void executeCallback(Runnable callback, String reason) {
        try {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    callback.run();
                    Log.d(TAG, "✅ Callback executed successfully - Reason: " + reason);
                } catch (Exception e) {
                    Log.e(TAG, "💥 Error executing callback - Reason: " + reason, e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "💥 Error posting callback to main thread - Reason: " + reason, e);
        }
    }
    
    // FORCE RESET FOR EMERGENCY SITUATIONS
    private void forceResetInterstitialState() {
        Log.w(TAG, "🚨 Force resetting interstitial state");
        isShowingInterstitial = false;
        currentInterstitialCallback = null;
        interstitialAd = null;
        currentOnAdClosedCallback = null;
        currentAdContext = "";
        
        // Load new ad after delay
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> loadInterstitialAd(), 2000);
    }
    
    // INTELLIGENT REWARDED AD METHODS
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest,
            new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(RewardedAd ad) {
                    rewardedAd = ad;
                    Log.d(TAG, "✅ Rewarded ad loaded successfully");
                }
                
                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    Log.e(TAG, "❌ Rewarded ad failed to load: " + loadAdError.getMessage());
                    rewardedAd = null;
                }
            });
    }
    
    public interface RewardedAdCallback {
        void onUserEarnedReward();
        void onAdFailedToShow();
        void onAdClosed(boolean earnedReward);
    }
    
    public void showRewardedAd(Activity activity, RewardedAdCallback callback) {
        // Prevent multiple rewarded ad operations
        if (isShowingRewarded) {
            Log.d(TAG, "⏸️ Rewarded ad already showing, skipping");
            try {
                callback.onAdFailedToShow();
            } catch (Exception e) {
                Log.e(TAG, "💥 Error in rewarded ad skip callback", e);
            }
            return;
        }
        
        if (rewardedAd != null) {
            isShowingRewarded = true;
            final boolean[] rewardEarned = {false};
            
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    Log.d(TAG, "🎯 Rewarded ad dismissed");
                    isShowingRewarded = false;
                    rewardedAd = null;
                    
                    // Use Handler to ensure UI thread execution
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            loadRewardedAd(); // Load next ad
                            callback.onAdClosed(rewardEarned[0]);
                        } catch (Exception e) {
                            Log.e(TAG, "💥 Error in reward dismiss callback", e);
                        }
                    });
                }
                
                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    Log.e(TAG, "❌ Rewarded ad failed to show: " + adError.getMessage());
                    isShowingRewarded = false;
                    rewardedAd = null;
                    
                    // Use Handler to ensure UI thread execution
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            callback.onAdFailedToShow();
                        } catch (Exception e) {
                            Log.e(TAG, "💥 Error in reward failure callback", e);
                        }
                    });
                }
                
                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "✨ Rewarded ad showed successfully");
                }
            });
            
            try {
                rewardedAd.show(activity, new OnUserEarnedRewardListener() {
                    @Override
                    public void onUserEarnedReward(RewardItem rewardItem) {
                        Log.d(TAG, "🎉 User earned reward: " + rewardItem.getType() + " - " + rewardItem.getAmount());
                        rewardEarned[0] = true;
                        
                        // Use Handler to ensure UI thread execution
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            try {
                                callback.onUserEarnedReward();
                            } catch (Exception e) {
                                Log.e(TAG, "💥 Error in reward earned callback", e);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "💥 Error showing rewarded ad", e);
                isShowingRewarded = false;
                callback.onAdFailedToShow();
            }
        } else {
            Log.d(TAG, "📭 Rewarded ad not ready");
            try {
                callback.onAdFailedToShow();
            } catch (Exception e) {
                Log.e(TAG, "💥 Error in rewarded not ready callback", e);
            }
        }
    }
    
    public boolean isRewardedAdReady() {
        return rewardedAd != null;
    }
    
    // INTELLIGENT CONVENIENCE METHODS WITH CONTEXT TRACKING
    public void showQuickAccessAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, "QuickAccess", onComplete);
    }
    
    public void showSearchAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, "Search", onComplete);
    }
    
    public void showRecentSessionAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, "RecentSession", onComplete);
    }
    
    public void showDownloadsAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, "Downloads", onComplete);
    }
    
    public void showBrowsingInterstitial(Activity activity) {
        if (canShowInterstitial()) {
            showInterstitialAd(activity, "BrowsingInterval", null);
        }
    }
    
    // ENHANCED FORCE CLEANUP FOR EMERGENCY SITUATIONS
    public void forceCleanupAds() {
        Log.w(TAG, "🚨 Force cleanup initiated - resetting all ad states");
        
        // Force cleanup any stuck ads with proper logging
        boolean wasShowingInterstitial = isShowingInterstitial;
        boolean wasShowingRewarded = isShowingRewarded;
        
        // Reset all states
        forceResetInterstitialState();
        isShowingRewarded = false;
        
        if (rewardedAd != null) {
            Log.d(TAG, "🧹 Cleaning up stuck rewarded ad");
            rewardedAd = null;
        }
        
        // Reset timing controls
        lastAdClickTime = 0;
        
        // Reload ads with delay to ensure proper cleanup
        android.os.Handler cleanupHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        cleanupHandler.postDelayed(() -> {
            loadInterstitialAd();
            loadRewardedAd();
            Log.d(TAG, "✅ Force cleanup completed - all ad states reset and new ads loaded");
        }, 2000); // Longer delay for force cleanup
        
        Log.w(TAG, String.format("🚨 Force cleanup stats - Was showing interstitial: %b, Was showing rewarded: %b", 
            wasShowingInterstitial, wasShowingRewarded));
    }
    
    // Emergency cleanup method for activities to call when interstitial ads get stuck
    public void emergencyCleanupInterstitial() {
        Log.w(TAG, "🚨 Emergency interstitial cleanup requested");
        forceResetInterstitialState();
    }
}
    
    // Rewarded Ad Methods
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest,
            new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(RewardedAd ad) {
                    rewardedAd = ad;
                    Log.d(TAG, "Rewarded ad loaded");
                }
                
                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: " + loadAdError.getMessage());
                    rewardedAd = null;
                }
            });
    }
    
    public interface RewardedAdCallback {
        void onUserEarnedReward();
        void onAdFailedToShow();
        void onAdClosed(boolean earnedReward);
    }
    
    public void showRewardedAd(Activity activity, RewardedAdCallback callback) {
        // Prevent multiple rewarded ad operations
        if (isShowingRewarded) {
            Log.d(TAG, "Rewarded ad already showing, skipping");
            try {
                callback.onAdFailedToShow();
            } catch (Exception e) {
                Log.e(TAG, "Error in rewarded ad skip callback", e);
            }
            return;
        }
        
        if (rewardedAd != null) {
            isShowingRewarded = true;
            final boolean[] rewardEarned = {false};
            
            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed");
                    isShowingRewarded = false;
                    rewardedAd = null;
                    
                    // Use Handler to ensure UI thread execution
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            loadRewardedAd(); // Load next ad
                            callback.onAdClosed(rewardEarned[0]);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in reward dismiss callback", e);
                        }
                    });
                }
                
                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    Log.e(TAG, "Rewarded ad failed to show: " + adError.getMessage());
                    isShowingRewarded = false;
                    rewardedAd = null;
                    
                    // Use Handler to ensure UI thread execution
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            callback.onAdFailedToShow();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in reward failure callback", e);
                        }
                    });
                }
                
                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad showed");
                }
            });
            
            try {
                rewardedAd.show(activity, new OnUserEarnedRewardListener() {
                    @Override
                    public void onUserEarnedReward(RewardItem rewardItem) {
                        Log.d(TAG, "User earned reward: " + rewardItem.getType() + " - " + rewardItem.getAmount());
                        rewardEarned[0] = true;
                        
                        // Use Handler to ensure UI thread execution
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            try {
                                callback.onUserEarnedReward();
                            } catch (Exception e) {
                                Log.e(TAG, "Error in reward earned callback", e);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error showing rewarded ad", e);
                isShowingRewarded = false;
                callback.onAdFailedToShow();
            }
        } else {
            Log.d(TAG, "Rewarded ad not ready");
            try {
                callback.onAdFailedToShow();
            } catch (Exception e) {
                Log.e(TAG, "Error in rewarded not ready callback", e);
            }
        }
    }
    
    public boolean isRewardedAdReady() {
        return rewardedAd != null;
    }
    
    // Convenience methods for common ad scenarios
    public void showQuickAccessAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, onComplete);
    }
    
    public void showSearchAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, onComplete);
    }
    
    public void showRecentSessionAd(Activity activity, Runnable onComplete) {
        showInterstitialAd(activity, onComplete);
    }
    
    public void forceCleanupAds() {
        Log.w(TAG, "Force cleanup initiated - resetting all ad states");
        
        // Force cleanup any stuck ads with proper logging
        boolean wasShowingInterstitial = isShowingInterstitial;
        boolean wasShowingRewarded = isShowingRewarded;
        
        isShowingInterstitial = false;
        isShowingRewarded = false;
        
        if (interstitialAd != null) {
            Log.d(TAG, "Cleaning up stuck interstitial ad");
            interstitialAd = null;
        }
        if (rewardedAd != null) {
            Log.d(TAG, "Cleaning up stuck rewarded ad");
            rewardedAd = null;
        }
        
        // Reset timing controls
        lastAdClickTime = 0;
        
        // Reload ads with delay to ensure proper cleanup
        android.os.Handler cleanupHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        cleanupHandler.postDelayed(() -> {
            loadInterstitialAd();
            loadRewardedAd();
            Log.d(TAG, "Force cleanup completed - all ad states reset and new ads loaded");
        }, 1500); // Longer delay for force cleanup
        
        Log.w(TAG, String.format("Force cleanup stats - Was showing interstitial: %b, Was showing rewarded: %b", 
            wasShowingInterstitial, wasShowingRewarded));
    }
    
    public void showBrowsingInterstitial(Activity activity) {
        if (canShowInterstitial()) {
            showInterstitialAd(activity, null);
        }
    }
    
    // Emergency cleanup method for activities to call when interstitial ads get stuck
    public void emergencyCleanupInterstitial() {
        Log.w(TAG, "Emergency interstitial cleanup requested");
        
        if (isShowingInterstitial) {
            isShowingInterstitial = false;
            Log.d(TAG, "Reset isShowingInterstitial flag");
        }
        
        if (interstitialAd != null) {
            try {
                interstitialAd = null;
                Log.d(TAG, "Nullified stuck interstitial ad");
            } catch (Exception e) {
                Log.e(TAG, "Error during emergency cleanup", e);
            }
        }
        
        // Load new ad after cleanup
        android.os.Handler emergencyHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        emergencyHandler.postDelayed(() -> {
            loadInterstitialAd();
            Log.d(TAG, "Emergency cleanup completed - new ad loaded");
        }, 2000);
    }
}