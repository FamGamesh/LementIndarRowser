package com.desktopbrowser.advanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final String PREFS_NAME = "RealDesktopBrowserDownloads";
    private static final String KEY_DOWNLOADS = "downloads_list";
    
    private static DownloadManager instance;
    private SharedPreferences prefs;
    private Gson gson;
    private Context context;
    
    private DownloadManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static DownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // INTELLIGENT FILE TYPE DETECTION
    public static class FileTypeInfo {
        public String category;
        public String icon;
        public String description;
        
        public FileTypeInfo(String category, String icon, String description) {
            this.category = category;
            this.icon = icon;
            this.description = description;
        }
    }
    
    public FileTypeInfo intelligentFileTypeDetection(String url, String filename) {
        try {
            // Extract file extension
            String extension = "";
            if (filename != null && filename.contains(".")) {
                extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            } else if (url != null && url.contains(".")) {
                String urlPath = url.split("\\?")[0]; // Remove query parameters
                if (urlPath.contains(".")) {
                    extension = urlPath.substring(urlPath.lastIndexOf(".") + 1).toLowerCase();
                }
            }
            
            Log.d(TAG, "🔍 Detecting file type for extension: " + extension);
            
            // Intelligent categorization
            switch (extension) {
                // Images
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                case "webp":
                case "svg":
                    return new FileTypeInfo("Image", "🖼️", "Image File");
                
                // Videos
                case "mp4":
                case "avi":
                case "mkv":
                case "mov":
                case "wmv":
                case "flv":
                case "webm":
                case "m4v":
                    return new FileTypeInfo("Video", "🎥", "Video File");
                
                // Audio
                case "mp3":
                case "wav":
                case "flac":
                case "aac":
                case "ogg":
                case "m4a":
                case "wma":
                    return new FileTypeInfo("Audio", "🎵", "Audio File");
                
                // Documents
                case "pdf":
                    return new FileTypeInfo("Document", "📄", "PDF Document");
                case "doc":
                case "docx":
                    return new FileTypeInfo("Document", "📝", "Word Document");
                case "xls":
                case "xlsx":
                    return new FileTypeInfo("Document", "📊", "Excel Spreadsheet");
                case "ppt":
                case "pptx":
                    return new FileTypeInfo("Document", "📊", "PowerPoint Presentation");
                case "txt":
                    return new FileTypeInfo("Document", "📄", "Text File");
                
                // Archives
                case "zip":
                case "rar":
                case "7z":
                case "tar":
                case "gz":
                    return new FileTypeInfo("Archive", "🗜️", "Archive File");
                
                // Applications
                case "apk":
                    return new FileTypeInfo("Application", "📱", "Android App");
                case "exe":
                    return new FileTypeInfo("Application", "⚙️", "Windows Application");
                
                // Web files
                case "html":
                case "htm":
                    return new FileTypeInfo("Web", "🌐", "Web Page");
                case "css":
                    return new FileTypeInfo("Web", "🎨", "Stylesheet");
                case "js":
                    return new FileTypeInfo("Web", "⚡", "JavaScript File");
                
                default:
                    // Try MIME type detection as fallback
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    if (mimeType != null) {
                        if (mimeType.startsWith("image/")) {
                            return new FileTypeInfo("Image", "🖼️", "Image File");
                        } else if (mimeType.startsWith("video/")) {
                            return new FileTypeInfo("Video", "🎥", "Video File");
                        } else if (mimeType.startsWith("audio/")) {
                            return new FileTypeInfo("Audio", "🎵", "Audio File");
                        } else if (mimeType.startsWith("text/")) {
                            return new FileTypeInfo("Document", "📄", "Text File");
                        }
                    }
                    
                    Log.d(TAG, "🤷 Unknown file type for extension: " + extension);
                    return new FileTypeInfo("Other", "📄", "File");
            }
        } catch (Exception e) {
            Log.e(TAG, "💥 Error detecting file type", e);
            return new FileTypeInfo("Other", "📄", "File");
        }
    }
    
    // ENHANCED: Add download with live progress tracking
    public void addDownloadWithProgress(String filename, String url, String downloadId) {
        try {
            Log.d(TAG, "🚀 Adding download with live progress tracking: " + filename + ", ID: " + downloadId);
            
            List<DownloadItem> downloads = getAllDownloads();
            
            // Create filepath in Downloads directory
            String downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String filepath = downloadsDir + File.separator + filename;
            
            // Get file info
            File file = new File(filepath);
            long fileSize = file.exists() ? file.length() : 0;
            FileTypeInfo typeInfo = intelligentFileTypeDetection(url, filename);
            
            // Create enhanced download item with progress tracking
            DownloadItem item = new DownloadItem();
            item.url = url;
            item.filename = filename;
            item.filepath = filepath;
            item.downloadId = downloadId;
            item.fileSize = fileSize;
            item.downloadTime = System.currentTimeMillis();
            item.fileType = typeInfo.category;
            item.fileIcon = typeInfo.icon;
            item.fileDescription = typeInfo.description;
            
            // ENHANCED: Initialize progress tracking fields
            item.downloadProgress = 0;
            item.downloadStatus = "Starting...";
            item.downloadSpeed = "0 KB/s";
            item.estimatedTimeRemaining = "Calculating...";
            item.bytesDownloaded = 0;
            item.totalBytes = 0;
            item.isActive = true;
            
            // Add to list (newest first)
            downloads.add(0, item);
            
            // Keep only last 100 downloads to avoid storage issues
            if (downloads.size() > 100) {
                downloads = downloads.subList(0, 100);
            }
            
            // Save to preferences
            String downloadsJson = gson.toJson(downloads);
            prefs.edit().putString(KEY_DOWNLOADS, downloadsJson).apply();
            
            Log.d(TAG, "✅ Enhanced download added successfully with progress tracking: " + filename + " (" + typeInfo.category + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error adding download with progress", e);
        }
    }
    
    // ENHANCED: Update download progress in real-time
    public void updateDownloadProgress(String downloadId, int progress, long bytesDownloaded, long totalBytes) {
        try {
            List<DownloadItem> downloads = getAllDownloads();
            boolean updated = false;
            
            for (DownloadItem item : downloads) {
                if (downloadId.equals(item.downloadId)) {
                    // Update progress information
                    item.downloadProgress = progress;
                    item.bytesDownloaded = bytesDownloaded;
                    item.totalBytes = totalBytes;
                    
                    // Calculate download speed (simplified)
                    long currentTime = System.currentTimeMillis();
                    long timeElapsed = currentTime - item.downloadTime;
                    if (timeElapsed > 0) {
                        long speedBytesPerSecond = (bytesDownloaded * 1000) / timeElapsed;
                        item.downloadSpeed = formatSpeed(speedBytesPerSecond);
                        
                        // Calculate estimated time remaining
                        if (speedBytesPerSecond > 0 && totalBytes > bytesDownloaded) {
                            long remainingBytes = totalBytes - bytesDownloaded;
                            long remainingSeconds = remainingBytes / speedBytesPerSecond;
                            item.estimatedTimeRemaining = formatTimeRemaining(remainingSeconds);
                        } else {
                            item.estimatedTimeRemaining = "Almost done...";
                        }
                    }
                    
                    // Update status
                    if (progress == 100) {
                        item.downloadStatus = "✅ Completed";
                        item.isActive = false;
                        item.fileSize = totalBytes; // Update final file size
                    } else {
                        item.downloadStatus = "📥 Downloading... " + progress + "%";
                        item.isActive = true;
                    }
                    
                    updated = true;
                    break;
                }
            }
            
            if (updated) {
                // Save updated progress
                String downloadsJson = gson.toJson(downloads);
                prefs.edit().putString(KEY_DOWNLOADS, downloadsJson).apply();
                
                Log.d(TAG, "📊 Download progress updated: " + downloadId + " - " + progress + "%");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error updating download progress", e);
        }
    }
    
    // Add download to tracking - Enhanced version
    public void addDownload(String filename, String url, String downloadId) {
        // Redirect to enhanced version with progress tracking
        addDownloadWithProgress(filename, url, downloadId);
    }
    
    // Get all downloads
    public List<DownloadItem> getAllDownloads() {
        try {
            String downloadsJson = prefs.getString(KEY_DOWNLOADS, null);
            if (downloadsJson != null) {
                Type type = new TypeToken<List<DownloadItem>>(){}.getType();
                List<DownloadItem> downloads = gson.fromJson(downloadsJson, type);
                
                // Filter out files that no longer exist and update file sizes
                List<DownloadItem> existingDownloads = new ArrayList<>();
                for (DownloadItem item : downloads) {
                    File file = new File(item.filepath);
                    if (file.exists()) {
                        // Update file size if it has changed
                        long currentSize = file.length();
                        if (item.fileSize != currentSize) {
                            item.fileSize = currentSize;
                        }
                        existingDownloads.add(item);
                    } else {
                        Log.d(TAG, "🗑️ Removing non-existent file from list: " + item.filename);
                    }
                }
                
                // Update list if files were removed or sizes changed
                if (existingDownloads.size() != downloads.size()) {
                    String updatedJson = gson.toJson(existingDownloads);
                    prefs.edit().putString(KEY_DOWNLOADS, updatedJson).apply();
                }
                
                return existingDownloads;
            }
        } catch (Exception e) {
            Log.e(TAG, "💥 Error getting downloads", e);
        }
        return new ArrayList<>();
    }
    
    // Get active downloads (currently downloading)
    public List<DownloadItem> getActiveDownloads() {
        List<DownloadItem> allDownloads = getAllDownloads();
        List<DownloadItem> activeDownloads = new ArrayList<>();
        
        for (DownloadItem item : allDownloads) {
            if (item.isActive) {
                activeDownloads.add(item);
            }
        }
        
        return activeDownloads;
    }
    
    // Get completed downloads
    public List<DownloadItem> getCompletedDownloads() {
        List<DownloadItem> allDownloads = getAllDownloads();
        List<DownloadItem> completedDownloads = new ArrayList<>();
        
        for (DownloadItem item : allDownloads) {
            if (!item.isActive) {
                completedDownloads.add(item);
            }
        }
        
        return completedDownloads;
    }
    
    // Get downloads by type
    public List<DownloadItem> getDownloadsByType(String type) {
        List<DownloadItem> allDownloads = getAllDownloads();
        List<DownloadItem> filteredDownloads = new ArrayList<>();
        
        for (DownloadItem item : allDownloads) {
            if (type.equals(item.fileType)) {
                filteredDownloads.add(item);
            }
        }
        
        return filteredDownloads;
    }
    
    // Remove download
    public void removeDownload(String filepath) {
        try {
            List<DownloadItem> downloads = getAllDownloads();
            downloads.removeIf(item -> filepath.equals(item.filepath));
            
            String downloadsJson = gson.toJson(downloads);
            prefs.edit().putString(KEY_DOWNLOADS, downloadsJson).apply();
            
            Log.d(TAG, "🗑️ Download removed from tracking");
        } catch (Exception e) {
            Log.e(TAG, "💥 Error removing download", e);
        }
    }
    
    // Clear all downloads
    public void clearAllDownloads() {
        prefs.edit().remove(KEY_DOWNLOADS).apply();
        Log.d(TAG, "🧹 All downloads cleared from tracking");
    }
    
    // ENHANCED: Format download speed
    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.1f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    // ENHANCED: Format estimated time remaining
    public static String formatTimeRemaining(long seconds) {
        if (seconds < 60) {
            return seconds + " sec";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " min";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    // Format file size
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    // Format download time
    public static String formatDownloadTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US);
        return sdf.format(new Date(timestamp));
    }
}