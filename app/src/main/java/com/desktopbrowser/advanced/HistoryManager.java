package com.desktopbrowser.advanced;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager extends SQLiteOpenHelper {
    
    private static final String DATABASE_NAME = "history.db";
    private static final int DATABASE_VERSION = 1;
    
    private static final String TABLE_HISTORY = "history";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_URL = "url";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_VISIT_COUNT = "visit_count";
    
    private static HistoryManager instance;
    
    private HistoryManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    public static synchronized HistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryManager(context.getApplicationContext());
        }
        return instance;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_HISTORY + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TITLE + " TEXT NOT NULL,"
                + COLUMN_URL + " TEXT NOT NULL,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_VISIT_COUNT + " INTEGER DEFAULT 1"
                + ")";
        db.execSQL(createTable);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }
    
    public void addHistoryItem(String title, String url) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Check if URL already exists
        String checkQuery = "SELECT " + COLUMN_ID + ", " + COLUMN_VISIT_COUNT + 
                           " FROM " + TABLE_HISTORY + " WHERE " + COLUMN_URL + " = ?";
        Cursor cursor = db.rawQuery(checkQuery, new String[]{url});
        
        if (cursor.moveToFirst()) {
            // Update existing entry
            long id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID));
            int visitCount = cursor.getInt(cursor.getColumnIndex(COLUMN_VISIT_COUNT)) + 1;
            
            ContentValues values = new ContentValues();
            values.put(COLUMN_TITLE, title);
            values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
            values.put(COLUMN_VISIT_COUNT, visitCount);
            
            db.update(TABLE_HISTORY, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        } else {
            // Insert new entry
            ContentValues values = new ContentValues();
            values.put(COLUMN_TITLE, title);
            values.put(COLUMN_URL, url);
            values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
            values.put(COLUMN_VISIT_COUNT, 1);
            
            db.insert(TABLE_HISTORY, null, values);
        }
        
        cursor.close();
        db.close();
    }
    
    public List<HistoryItem> getAllHistory() {
        List<HistoryItem> history = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_HISTORY + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        if (cursor.moveToFirst()) {
            do {
                HistoryItem item = new HistoryItem();
                item.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
                item.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
                item.setUrl(cursor.getString(cursor.getColumnIndex(COLUMN_URL)));
                item.setTimestamp(cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP)));
                item.setVisitCount(cursor.getInt(cursor.getColumnIndex(COLUMN_VISIT_COUNT)));
                
                history.add(item);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return history;
    }
    
    public void clearHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
        db.close();
    }
    
    public void removeHistoryItem(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }
    
    public static class HistoryItem {
        private long id;
        private String title;
        private String url;
        private long timestamp;
        private int visitCount;
        
        // Getters and setters
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public int getVisitCount() { return visitCount; }
        public void setVisitCount(int visitCount) { this.visitCount = visitCount; }
    }
}