package com.desktopbrowser.advanced;

public class DownloadItem {
    public String url;
    public String filename;
    public String filepath;
    public String downloadId;  // Store download ID for tracking
    public long fileSize;
    public long downloadTime;
    public String fileType;
    public String fileIcon;
    public String fileDescription;
    
    // ENHANCED: Live progress tracking fields
    public int downloadProgress;      // Progress percentage (0-100)
    public String downloadStatus;     // Status text (e.g., "Downloading...", "Completed", "Failed")
    public String downloadSpeed;      // Download speed (e.g., "1.2 MB/s")
    public String estimatedTimeRemaining; // Time remaining (e.g., "2 min", "30 sec")
    public long bytesDownloaded;      // Bytes downloaded so far
    public long totalBytes;           // Total file size in bytes
    public boolean isActive;          // Whether download is currently active
    
    public DownloadItem() {
        // Default constructor for Gson
        this.downloadProgress = 0;
        this.downloadStatus = "Pending...";
        this.downloadSpeed = "0 KB/s";
        this.estimatedTimeRemaining = "Unknown";
        this.bytesDownloaded = 0;
        this.totalBytes = 0;
        this.isActive = false;
    }
    
    public DownloadItem(String url, String filename, String filepath, long fileSize, long downloadTime, String fileType, String fileIcon, String fileDescription) {
        this.url = url;
        this.filename = filename;
        this.filepath = filepath;
        this.fileSize = fileSize;
        this.downloadTime = downloadTime;
        this.fileType = fileType;
        this.fileIcon = fileIcon;
        this.fileDescription = fileDescription;
        
        // Initialize progress tracking fields
        this.downloadProgress = 0;
        this.downloadStatus = "Pending...";
        this.downloadSpeed = "0 KB/s";
        this.estimatedTimeRemaining = "Unknown";
        this.bytesDownloaded = 0;
        this.totalBytes = fileSize;
        this.isActive = false;
    }
    
    // ENHANCED: Constructor with progress tracking
    public DownloadItem(String url, String filename, String filepath, String downloadId, 
                       long fileSize, long downloadTime, String fileType, String fileIcon, 
                       String fileDescription, int progress, String status, boolean isActive) {
        this.url = url;
        this.filename = filename;
        this.filepath = filepath;
        this.downloadId = downloadId;
        this.fileSize = fileSize;
        this.downloadTime = downloadTime;
        this.fileType = fileType;
        this.fileIcon = fileIcon;
        this.fileDescription = fileDescription;
        this.downloadProgress = progress;
        this.downloadStatus = status;
        this.isActive = isActive;
        this.downloadSpeed = "0 KB/s";
        this.estimatedTimeRemaining = "Unknown";
        this.bytesDownloaded = 0;
        this.totalBytes = fileSize;
    }
    
    // ENHANCED: Get progress percentage as formatted string
    public String getProgressText() {
        if (downloadProgress == 100) {
            return "✅ Complete";
        } else if (downloadProgress == 0) {
            return "⏳ Starting...";
        } else {
            return downloadProgress + "%";
        }
    }
    
    // ENHANCED: Get status with icon
    public String getStatusWithIcon() {
        if (isActive) {
            return "📥 " + downloadStatus;
        } else if (downloadProgress == 100) {
            return "✅ Completed";
        } else {
            return "⏸️ Paused";
        }
    }
    
    // ENHANCED: Get download info summary
    public String getDownloadInfoSummary() {
        if (isActive && downloadProgress > 0) {
            return downloadProgress + "% • " + downloadSpeed + " • " + estimatedTimeRemaining + " left";
        } else if (downloadProgress == 100) {
            return "Downloaded • " + DownloadManager.formatFileSize(fileSize);
        } else {
            return DownloadManager.formatFileSize(fileSize) + " • " + fileType;
        }
    }
    
    // ENHANCED: Check if download is in progress
    public boolean isDownloading() {
        return isActive && downloadProgress > 0 && downloadProgress < 100;
    }
    
    // ENHANCED: Check if download is completed
    public boolean isCompleted() {
        return !isActive && downloadProgress == 100;
    }
    
    // ENHANCED: Check if download failed
    public boolean isFailed() {
        return !isActive && downloadProgress < 100 && downloadStatus != null && 
               (downloadStatus.toLowerCase().contains("failed") || downloadStatus.toLowerCase().contains("error"));
    }
    
    @Override
    public String toString() {
        return "DownloadItem{" +
                "filename='" + filename + '\'' +
                ", fileType='" + fileType + '\'' +
                ", fileSize=" + fileSize +
                ", downloadTime=" + downloadTime +
                ", progress=" + downloadProgress + "%" +
                ", status='" + downloadStatus + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}