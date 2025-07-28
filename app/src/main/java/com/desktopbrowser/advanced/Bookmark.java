package com.desktopbrowser.advanced;

public class Bookmark {
    private long id;
    private String title;
    private String url;
    private long timestamp;
    private String favicon;
    
    public Bookmark() {}
    
    public Bookmark(String title, String url) {
        this.title = title;
        this.url = url;
        this.timestamp = System.currentTimeMillis();
    }
    
    public Bookmark(long id, String title, String url, long timestamp, String favicon) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.timestamp = timestamp;
        this.favicon = favicon;
    }
    
    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getFavicon() { return favicon; }
    public void setFavicon(String favicon) { this.favicon = favicon; }
}