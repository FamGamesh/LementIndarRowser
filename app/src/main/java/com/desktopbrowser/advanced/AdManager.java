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
    
    // Add flags to prevent multiple ad operations
    private boolean isShowingInterstitial = false;
    private boolean isShowingRewarded = false;
    private static final long AD_CLICK_DEBOUNCE = 2000; // 2 seconds debounce
    private long lastAdClickTime = 0;
    
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
                Log.d(TAG, "AdMob initialized successfully");
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
    
    // Interstitial Ad Methods
    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(InterstitialAd ad) {
                    interstitialAd = ad;
                    Log.d(TAG, "Interstitial ad loaded");
                }
                
                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: " + loadAdError.getMessage());
                    interstitialAd = null;
                }
            });
    }
    
    public void showInterstitialAd(Activity activity, Runnable onAdClosed) {
        // Prevent multiple ad operations and add debouncing
        long currentTime = System.currentTimeMillis();
        if (isShowingInterstitial || (currentTime - lastAdClickTime) < AD_CLICK_DEBOUNCE) {
            Log.d(TAG, "Ad operation already in progress or too recent, skipping");
            if (onAdClosed != null) {
                try {
                    onAdClosed.run();
                } catch (Exception e) {
                    Log.e(TAG, "Error in debounced callback", e);
                }
            }
            return;
        }
        
        lastAdClickTime = currentTime;
        
        if (interstitialAd != null) {
            isShowingInterstitial = true;
            
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed");
                    isShowingInterstitial = false;
                    interstitialAd = null;
                    
                    // Use Handler to ensure UI thread execution
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            loadInterstitialAd(); // Load next ad
                            if (onAdClosed != null) {
                                onAdClosed.run();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in interstitial dismiss callback", e);
                        }
                    });
                }
                
                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    Log.e(TAG, "Interstitial ad failed to show: " + adError.getMessage());
                    isShowingInterstitial = false;
                    interstitialAd = null;
                    
                    // Use Handler to ensure UI thread execution
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            if (onAdClosed != null) {
                                onAdClosed.run();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in interstitial failure callback", e);
                        }
                    });
                }
                
                @Override
                public void onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed");
                }
            });
            
            try {
                interstitialAd.show(activity);
                lastInterstitialTime = System.currentTimeMillis();
            } catch (Exception e) {
                Log.e(TAG, "Error showing interstitial ad", e);
                isShowingInterstitial = false;
                if (onAdClosed != null) {
                    onAdClosed.run();
                }
            }
        } else {
            Log.d(TAG, "Interstitial ad not ready, proceeding without ad");
            try {
                if (onAdClosed != null) {
                    onAdClosed.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in interstitial not ready callback", e);
            }
        }
    }
    
    // Check if enough time has passed for next interstitial ad (15 minutes rule)
    public boolean canShowInterstitial() {
        return System.currentTimeMillis() - lastInterstitialTime >= INTERSTITIAL_COOLDOWN;
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
    
    public void showBrowsingInterstitial(Activity activity) {
        if (canShowInterstitial()) {
            showInterstitialAd(activity, null);
        }
    }
}