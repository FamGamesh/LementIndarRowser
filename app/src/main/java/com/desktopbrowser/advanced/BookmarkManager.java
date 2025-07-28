package com.desktopbrowser.advanced;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager extends SQLiteOpenHelper {
    
    private static final String DATABASE_NAME = "bookmarks.db";
    private static final int DATABASE_VERSION = 1;
    
    private static final String TABLE_BOOKMARKS = "bookmarks";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_URL = "url";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_FAVICON = "favicon";
    
    private static BookmarkManager instance;
    
    private BookmarkManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    public static synchronized BookmarkManager getInstance(Context context) {
        if (instance == null) {
            instance = new BookmarkManager(context.getApplicationContext());
        }
        return instance;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_BOOKMARKS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TITLE + " TEXT NOT NULL,"
                + COLUMN_URL + " TEXT NOT NULL,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_FAVICON + " TEXT"
                + ")";
        db.execSQL(createTable);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        onCreate(db);
    }
    
    public long addBookmark(Bookmark bookmark) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_TITLE, bookmark.getTitle());
        values.put(COLUMN_URL, bookmark.getUrl());
        values.put(COLUMN_TIMESTAMP, bookmark.getTimestamp());
        values.put(COLUMN_FAVICON, bookmark.getFavicon());
        
        long id = db.insert(TABLE_BOOKMARKS, null, values);
        db.close();
        
        return id;
    }
    
    public List<Bookmark> getAllBookmarks() {
        List<Bookmark> bookmarks = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_BOOKMARKS + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        if (cursor.moveToFirst()) {
            do {
                Bookmark bookmark = new Bookmark();
                bookmark.setId(cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
                bookmark.setTitle(cursor.getString(cursor.getColumnIndex(COLUMN_TITLE)));
                bookmark.setUrl(cursor.getString(cursor.getColumnIndex(COLUMN_URL)));
                bookmark.setTimestamp(cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP)));
                bookmark.setFavicon(cursor.getString(cursor.getColumnIndex(COLUMN_FAVICON)));
                
                bookmarks.add(bookmark);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return bookmarks;
    }
    
    public boolean isBookmarked(String url) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT 1 FROM " + TABLE_BOOKMARKS + " WHERE " + COLUMN_URL + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{url});
        
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        
        return exists;
    }
    
    public void removeBookmark(String url) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_BOOKMARKS, COLUMN_URL + " = ?", new String[]{url});
        db.close();
    }
    
    public void removeBookmark(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_BOOKMARKS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }
}