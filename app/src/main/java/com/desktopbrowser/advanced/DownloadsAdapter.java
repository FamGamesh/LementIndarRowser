package com.desktopbrowser.advanced;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;
import android.os.Handler;
import android.os.Looper;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder> {
    
    private static final String TAG = "DownloadsAdapter";
    
    private List<DownloadItem> downloadItems;
    private Context context;
    private Handler uiHandler;
    
    public DownloadsAdapter(List<DownloadItem> downloadItems, Context context) {
        this.downloadItems = downloadItems;
        this.context = context;
        this.uiHandler = new Handler(Looper.getMainLooper());
    }
    
    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_download, parent, false);
        return new DownloadViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        DownloadItem item = downloadItems.get(position);
        
        try {
            // Set file icon
            holder.fileIcon.setText(item.fileIcon != null ? item.fileIcon : "📄");
            
            // Set filename
            holder.fileName.setText(item.filename != null ? item.filename : "Unknown File");
            
            // Set file description
            holder.fileDescription.setText(item.fileDescription != null ? item.fileDescription : "File");
            
            // ENHANCED: Show live progress or file size
            if (item.isActive && item.downloadProgress > 0) {
                // Show progress bar and live progress info
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBar.setProgress(item.downloadProgress);
                holder.progressText.setVisibility(View.VISIBLE);
                holder.progressText.setText(item.getProgressText());
                
                // Show live download info
                holder.fileSize.setText(item.getDownloadInfoSummary());
                holder.downloadTime.setText(item.getStatusWithIcon());
                
                // Set progress bar color based on status
                if (item.downloadProgress == 100) {
                    holder.progressBar.getProgressDrawable().setColorFilter(
                        android.graphics.Color.parseColor("#4CAF50"), 
                        android.graphics.PorterDuff.Mode.SRC_IN);
                } else {
                    holder.progressBar.getProgressDrawable().setColorFilter(
                        android.graphics.Color.parseColor("#2196F3"), 
                        android.graphics.PorterDuff.Mode.SRC_IN);
                }
                
                Log.d(TAG, "📊 Showing live progress for: " + item.filename + " - " + item.downloadProgress + "%");
                
            } else {
                // Hide progress bar for completed/inactive downloads
                holder.progressBar.setVisibility(View.GONE);
                holder.progressText.setVisibility(View.GONE);
                
                // Show normal file info
                holder.fileSize.setText(DownloadManager.formatFileSize(item.fileSize));
                holder.downloadTime.setText(DownloadManager.formatDownloadTime(item.downloadTime));
            }
            
            // ENHANCED: Set different styling based on download status
            if (item.isCompleted()) {
                // Completed download
                holder.fileName.setTextColor(android.graphics.Color.parseColor("#2E7D32"));
                holder.statusIndicator.setText("✅");
                holder.statusIndicator.setVisibility(View.VISIBLE);
            } else if (item.isDownloading()) {
                // Active download
                holder.fileName.setTextColor(android.graphics.Color.parseColor("#1976D2"));
                holder.statusIndicator.setText("📥");
                holder.statusIndicator.setVisibility(View.VISIBLE);
            } else if (item.isFailed()) {
                // Failed download
                holder.fileName.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                holder.statusIndicator.setText("❌");
                holder.statusIndicator.setVisibility(View.VISIBLE);
            } else {
                // Default
                holder.fileName.setTextColor(android.graphics.Color.parseColor("#212121"));
                holder.statusIndicator.setVisibility(View.GONE);
            }
            
            // Set click listener
            holder.itemView.setOnClickListener(v -> openFile(item));
            
            // Set long click listener for options
            holder.itemView.setOnLongClickListener(v -> {
                showFileOptions(item, position);
                return true;
            });
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error binding download item", e);
            
            // Fallback values
            holder.fileIcon.setText("📄");
            holder.fileName.setText("Error loading file");
            holder.fileDescription.setText("Unknown");
            holder.fileSize.setText("0 B");
            holder.downloadTime.setText("Unknown");
            holder.progressBar.setVisibility(View.GONE);
            holder.progressText.setVisibility(View.GONE);
        }
    }
    
    @Override
    public int getItemCount() {
        return downloadItems != null ? downloadItems.size() : 0;
    }
    
    // ENHANCED: Open file with appropriate app
    private void openFile(DownloadItem item) {
        try {
            File file = new File(item.filepath);
            
            if (!file.exists()) {
                Toast.makeText(context, "❌ File not found: " + item.filename, Toast.LENGTH_SHORT).show();
                Log.w(TAG, "File not found: " + item.filepath);
                return;
            }
            
            // If download is still active, show status instead of opening
            if (item.isActive && item.downloadProgress < 100) {
                String status = "📥 Download in progress: " + item.downloadProgress + "%";
                if (item.downloadSpeed != null && !item.downloadSpeed.equals("0 KB/s")) {
                    status += " at " + item.downloadSpeed;
                }
                Toast.makeText(context, status, Toast.LENGTH_LONG).show();
                return;
            }
            
            // Create file URI using FileProvider
            Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
            );
            
            // Determine MIME type
            String mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(item.filepath));
            
            if (mimeType == null) {
                mimeType = "*/*";
            }
            
            // Create intent to open file
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            try {
                context.startActivity(Intent.createChooser(intent, "Open " + item.filename));
                Log.d(TAG, "✅ Opening file: " + item.filename);
            } catch (Exception e) {
                Toast.makeText(context, "❌ No app found to open this file type", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "No app found to open file: " + mimeType, e);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "💥 Error opening file", e);
            Toast.makeText(context, "❌ Error opening file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // ENHANCED: Show file options menu
    private void showFileOptions(DownloadItem item, int position) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("📄 " + item.filename);
        
        java.util.List<String> options = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        
        // If download is active, show different options
        if (item.isActive && item.downloadProgress < 100) {
            options.add("📊 View Progress Details");
            actions.add(() -> showProgressDetails(item));
            
            options.add("⏸️ Cancel Download");
            actions.add(() -> cancelDownload(item, position));
        } else {
            // Normal file options
            options.add("📂 Open File");
            actions.add(() -> openFile(item));
            
            options.add("📤 Share File");
            actions.add(() -> shareFile(item));
            
            options.add("ℹ️ File Details");
            actions.add(() -> showFileDetails(item));
            
            options.add("🗑️ Delete File");
            actions.add(() -> deleteFile(item, position));
        }
        
        String[] optionsArray = options.toArray(new String[0]);
        
        builder.setItems(optionsArray, (dialog, which) -> {
            try {
                actions.get(which).run();
            } catch (Exception e) {
                Log.e(TAG, "Error executing file option", e);
                Toast.makeText(context, "Action failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    // ENHANCED: Show detailed progress information
    private void showProgressDetails(DownloadItem item) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("📊 Download Progress");
        
        StringBuilder details = new StringBuilder();
        details.append("📄 File: ").append(item.filename).append("\n\n");
        details.append("📈 Progress: ").append(item.downloadProgress).append("%\n");
        details.append("📊 Status: ").append(item.downloadStatus).append("\n");
        details.append("⚡ Speed: ").append(item.downloadSpeed).append("\n");
        details.append("⏱️ Time Remaining: ").append(item.estimatedTimeRemaining).append("\n\n");
        
        if (item.totalBytes > 0) {
            details.append("📥 Downloaded: ").append(DownloadManager.formatFileSize(item.bytesDownloaded)).append("\n");
            details.append("📦 Total Size: ").append(DownloadManager.formatFileSize(item.totalBytes)).append("\n");
        }
        
        details.append("🔗 URL: ").append(item.url);
        
        builder.setMessage(details.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    // Cancel active download
    private void cancelDownload(DownloadItem item, int position) {
        new android.app.AlertDialog.Builder(context)
            .setTitle("⏸️ Cancel Download")
            .setMessage("Cancel download of " + item.filename + "?")
            .setPositiveButton("Cancel Download", (dialog, which) -> {
                try {
                    // Cancel download in Android DownloadManager
                    android.app.DownloadManager downloadManager = 
                        (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    
                    if (item.downloadId != null && !item.downloadId.isEmpty()) {
                        downloadManager.remove(Long.parseLong(item.downloadId));
                    }
                    
                    // Remove from our list
                    downloadItems.remove(position);
                    notifyItemRemoved(position);
                    
                    Toast.makeText(context, "⏸️ Download cancelled: " + item.filename, Toast.LENGTH_SHORT).show();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error cancelling download", e);
                    Toast.makeText(context, "Error cancelling download", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Keep Downloading", null)
            .show();
    }
    
    // Share file
    private void shareFile(DownloadItem item) {
        try {
            File file = new File(item.filepath);
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("*/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Shared from Real Desktop Browser");
            shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Share " + item.filename));
            
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
            Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Show file details
    private void showFileDetails(DownloadItem item) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("ℹ️ File Details");
        
        StringBuilder details = new StringBuilder();
        details.append("📄 Name: ").append(item.filename).append("\n");
        details.append("📁 Type: ").append(item.fileDescription).append("\n");
        details.append("📊 Size: ").append(DownloadManager.formatFileSize(item.fileSize)).append("\n");
        details.append("📅 Downloaded: ").append(DownloadManager.formatDownloadTime(item.downloadTime)).append("\n");
        details.append("📍 Location: ").append(item.filepath).append("\n");
        details.append("🔗 Source: ").append(item.url);
        
        builder.setMessage(details.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    // Delete file
    private void deleteFile(DownloadItem item, int position) {
        new android.app.AlertDialog.Builder(context)
            .setTitle("🗑️ Delete File")
            .setMessage("Delete " + item.filename + " permanently?")
            .setPositiveButton("Delete", (dialog, which) -> {
                try {
                    File file = new File(item.filepath);
                    if (file.exists() && file.delete()) {
                        // Remove from download manager
                        DownloadManager.getInstance(context).removeDownload(item.filepath);
                        
                        // Remove from adapter
                        downloadItems.remove(position);
                        notifyItemRemoved(position);
                        
                        Toast.makeText(context, "🗑️ File deleted: " + item.filename, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "❌ Failed to delete file", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting file", e);
                    Toast.makeText(context, "Error deleting file", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // ENHANCED: Update adapter data with live progress
    public void updateDownloads(List<DownloadItem> newDownloads) {
        this.downloadItems.clear();
        this.downloadItems.addAll(newDownloads);
        
        uiHandler.post(() -> {
            notifyDataSetChanged();
        });
        
        Log.d(TAG, "📊 Downloads list updated with " + newDownloads.size() + " items");
    }
    
    // ENHANCED: Update specific download progress
    public void updateDownloadProgress(String downloadId, int progress) {
        for (int i = 0; i < downloadItems.size(); i++) {
            DownloadItem item = downloadItems.get(i);
            if (downloadId.equals(item.downloadId)) {
                final int position = i;
                uiHandler.post(() -> {
                    notifyItemChanged(position);
                });
                break;
            }
        }
    }
    
    static class DownloadViewHolder extends RecyclerView.ViewHolder {
        TextView fileIcon;
        TextView fileName;
        TextView fileDescription;
        TextView fileSize;
        TextView downloadTime;
        ImageView actionArrow;
        
        // ENHANCED: Progress tracking views
        ProgressBar progressBar;
        TextView progressText;
        TextView statusIndicator;
        
        public DownloadViewHolder(@NonNull View itemView) {
            super(itemView);
            
            fileIcon = itemView.findViewById(R.id.file_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileDescription = itemView.findViewById(R.id.file_description);
            fileSize = itemView.findViewById(R.id.file_size);
            downloadTime = itemView.findViewById(R.id.download_time);
            actionArrow = itemView.findViewById(R.id.action_arrow);
            
            // ENHANCED: Initialize progress views
            progressBar = itemView.findViewById(R.id.download_progress);
            progressText = itemView.findViewById(R.id.progress_text);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
        }
    }
}