package com.desktopbrowser.advanced;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder> {
    
    private static final String TAG = "DownloadsAdapter";
    private List<DownloadItem> downloads;
    private Context context;
    
    public DownloadsAdapter(List<DownloadItem> downloads, Context context) {
        this.downloads = downloads;
        this.context = context;
    }
    
    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new DownloadViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        DownloadItem item = downloads.get(position);
        
        // Set file info
        holder.fileIcon.setText(item.fileIcon);
        holder.fileName.setText(item.filename);
        holder.fileDescription.setText(item.fileDescription);
        holder.fileSize.setText(DownloadManager.formatFileSize(item.fileSize));
        holder.downloadTime.setText(DownloadManager.formatDownloadTime(item.downloadTime));
        
        // FIXED: Enhanced click listener to open file with better error handling
        holder.itemView.setOnClickListener(v -> {
            try {
                File file = new File(item.filepath);
                if (file.exists()) {
                    Log.d(TAG, "📂 Opening file: " + item.filename + " at: " + item.filepath);
                    
                    openFileWithCompatibleApp(file, item);
                    
                } else {
                    Log.w(TAG, "📄 File not found: " + item.filepath);
                    Toast.makeText(context, "File not found: " + item.filename + "\n\nFile may have been moved or deleted.", Toast.LENGTH_LONG).show();
                    
                    // Remove from list since file doesn't exist
                    downloads.remove(position);
                    notifyItemRemoved(position);
                    
                    // Update download manager
                    DownloadManager.getInstance(context).removeDownload(item.filepath);
                }
            } catch (Exception e) {
                Log.e(TAG, "💥 Error opening file: " + item.filename, e);
                Toast.makeText(context, "Cannot open file: " + item.filename + "\nError: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        // Long click for additional options
        holder.itemView.setOnLongClickListener(v -> {
            showFileOptions(item, position);
            return true;
        });
    }
    
    // FIXED: Enhanced file opening with better app detection
    private void openFileWithCompatibleApp(File file, DownloadItem item) {
        try {
            // Create intent to open file
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            // Use FileProvider to get URI for the file with better error handling
            Uri uri;
            try {
                uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
                );
                Log.d(TAG, "✅ FileProvider URI created: " + uri.toString());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "❌ FileProvider failed, trying content URI", e);
                // Fallback to direct file URI (less secure but more compatible)
                uri = Uri.fromFile(file);
            }
            
            // Get MIME type with improved detection
            String mimeType = getMimeTypeWithFallback(item.filename);
            Log.d(TAG, "🔍 Detected MIME type: " + mimeType + " for file: " + item.filename);
            
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Try to find compatible apps
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                try {
                    context.startActivity(Intent.createChooser(intent, "Open " + item.filename));
                    Log.d(TAG, "✅ File opened successfully: " + item.filename);
                } catch (android.content.ActivityNotFoundException e) {
                    Log.w(TAG, "⚠️ No app found to open file: " + item.filename, e);
                    // Try with generic MIME type
                    intent.setDataAndType(uri, "*/*");
                    try {
                        context.startActivity(Intent.createChooser(intent, "Open " + item.filename));
                        Log.d(TAG, "✅ File opened with generic MIME type: " + item.filename);
                    } catch (Exception fallbackException) {
                        showNoAppFoundDialog(item, mimeType);
                    }
                }
            } else {
                showNoAppFoundDialog(item, mimeType);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error opening file: " + item.filename, e);
            Toast.makeText(context, "Cannot open file: " + item.filename + "\nError: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showNoAppFoundDialog(DownloadItem item, String mimeType) {
        String message = "No app found to open this file type: " + mimeType + 
                        "\n\nYou may need to install an app that can handle " + 
                        item.fileDescription.toLowerCase() + " files.";
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Cannot Open File")
               .setMessage(message)
               .setPositiveButton("Try with File Manager", (dialog, which) -> {
                   openWithFileManager(item);
               })
               .setNegativeButton("OK", null)
               .show();
    }
    
    private void openWithFileManager(DownloadItem item) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(new File(item.filepath).getParent());
            intent.setDataAndType(uri, "resource/folder");
            
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "No file manager app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open file location", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public int getItemCount() {
        return downloads.size();
    }
    
    private void showFileOptions(DownloadItem item, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle(item.fileIcon + " " + item.filename);
        
        String[] options = {"Open", "Share", "Delete", "Show in Folder"};
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Open
                    // Trigger the same action as normal click
                    try {
                        File file = new File(item.filepath);
                        if (file.exists()) {
                            openFileWithCompatibleApp(file, item);
                        }
                    } catch (Exception e) {
                        Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show();
                    }
                    break;
                    
                case 1: // Share
                    try {
                        File file = new File(item.filepath);
                        if (file.exists()) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.getPackageName() + ".fileprovider",
                                file
                            );
                            shareIntent.setType(getMimeTypeWithFallback(item.filename));
                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(Intent.createChooser(shareIntent, "Share " + item.filename));
                        }
                    } catch (Exception e) {
                        Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show();
                    }
                    break;
                    
                case 2: // Delete
                    deleteFile(item, position);
                    break;
                    
                case 3: // Show in folder
                    openWithFileManager(item);
                    break;
            }
        });
        
        builder.show();
    }
    
    private void deleteFile(DownloadItem item, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Delete File");
        builder.setMessage("Are you sure you want to delete \"" + item.filename + "\"?");
        
        builder.setPositiveButton("Delete", (dialog, which) -> {
            try {
                File file = new File(item.filepath);
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "🗑️ File deleted: " + item.filename);
                    Toast.makeText(context, "File deleted: " + item.filename, Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "⚠️ Could not delete file: " + item.filename);
                    Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show();
                }
                
                // Remove from list and update UI
                downloads.remove(position);
                notifyItemRemoved(position);
                
                // Update download manager
                DownloadManager.getInstance(context).removeDownload(item.filepath);
                
            } catch (Exception e) {
                Log.e(TAG, "💥 Error deleting file", e);
                Toast.makeText(context, "Error deleting file", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    // FIXED: Improved MIME type detection with better fallbacks
    private String getMimeTypeWithFallback(String filename) {
        String extension = "";
        if (filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        
        // First try Android's built-in MIME type map
        android.webkit.MimeTypeMap mime = android.webkit.MimeTypeMap.getSingleton();
        String mimeType = mime.getMimeTypeFromExtension(extension);
        
        // If that fails, use our enhanced custom MIME type detection
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = getEnhancedCustomMimeType(extension);
        }
        
        Log.d(TAG, "🔍 File extension: " + extension + " -> MIME type: " + mimeType);
        return mimeType != null ? mimeType : "*/*";
    }
    
    // Enhanced MIME type detection with more file types
    private String getEnhancedCustomMimeType(String extension) {
        switch (extension.toLowerCase()) {
            // Images
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "svg":
                return "image/svg+xml";
            case "tiff":
            case "tif":
                return "image/tiff";
                
            // Videos
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mkv":
                return "video/x-matroska";
            case "mov":
                return "video/quicktime";
            case "wmv":
                return "video/x-ms-wmv";
            case "webm":
                return "video/webm";
            case "flv":
                return "video/x-flv";
            case "m4v":
                return "video/x-m4v";
                
            // Audio
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "aac":
                return "audio/aac";
            case "ogg":
                return "audio/ogg";
            case "m4a":
                return "audio/mp4";
            case "wma":
                return "audio/x-ms-wma";
                
            // Documents
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "text/javascript";
            case "json":
                return "application/json";
            case "xml":
                return "text/xml";
                
            // Archives
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            case "tar":
                return "application/x-tar";
            case "gz":
                return "application/gzip";
            case "bz2":
                return "application/x-bzip2";
                
            // Applications
            case "apk":
                return "application/vnd.android.package-archive";
            case "exe":
                return "application/x-msdownload";
            case "deb":
                return "application/vnd.debian.binary-package";
            case "rpm":
                return "application/x-rpm";
                
            // E-books
            case "epub":
                return "application/epub+zip";
            case "mobi":
                return "application/x-mobipocket-ebook";
                
            default:
                return "application/octet-stream";
        }
    }
    
    static class DownloadViewHolder extends RecyclerView.ViewHolder {
        TextView fileIcon, fileName, fileDescription, fileSize, downloadTime;
        
        public DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileDescription = itemView.findViewById(R.id.file_description);
            fileSize = itemView.findViewById(R.id.file_size);
            downloadTime = itemView.findViewById(R.id.download_time);
        }
    }
}