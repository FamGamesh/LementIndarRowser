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
    
    public DownloadItem() {
        // Default constructor for Gson
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
    }
    
    @Override
    public String toString() {
        return "DownloadItem{" +
                "filename='" + filename + '\'' +
                ", fileType='" + fileType + '\'' +
                ", fileSize=" + fileSize +
                ", downloadTime=" + downloadTime +
                '}';
    }
}