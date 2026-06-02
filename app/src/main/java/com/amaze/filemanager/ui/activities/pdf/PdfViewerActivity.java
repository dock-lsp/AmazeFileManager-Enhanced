/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.activities.pdf;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.ui.theme.AppTheme;
import com.amaze.filemanager.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Viewer Activity - Provides PDF viewing capabilities with zoom, scroll and navigation.
 * Uses Android's native PdfRenderer for efficient PDF rendering.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PdfViewerActivity extends ThemedActivity {

    public static final String EXTRA_FILE_PATH = "pdf_file_path";
    public static final String EXTRA_URI = "pdf_uri";

    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor fileDescriptor;
    private ImageView pdfImageView;
    private SeekBar pageSeekBar;
    private TextView pageIndicator;
    private TextView tvPdfInfo;
    private ScrollView scrollView;
    private LinearLayout pageContainer;
    
    private int currentPageIndex = 0;
    private int pageCount = 0;
    private String filePath;
    private Uri fileUri;
    private float currentZoom = 1.0f;
    private static final float ZOOM_STEP = 0.25f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 3.0f;

    private List<Bitmap> pageBitmaps = new ArrayList<>();
    private boolean isDualPageMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        setupToolbar();
        initViews();
        handleIntent();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.pdf_viewer);
        }
    }

    private void initViews() {
        pdfImageView = findViewById(R.id.pdf_image_view);
        pageSeekBar = findViewById(R.id.page_seekbar);
        pageIndicator = findViewById(R.id.page_indicator);
        tvPdfInfo = findViewById(R.id.tv_pdf_info);
        scrollView = findViewById(R.id.scroll_view);
        pageContainer = findViewById(R.id.page_container);

        pageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    showPage(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Double tap to zoom
        pdfImageView.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            private static final long DOUBLE_CLICK_TIME_DELTA = 300;

            @Override
            public void onClick(View v) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                    // Double click detected - reset zoom
                    currentZoom = 1.0f;
                    showPage(currentPageIndex);
                }
                lastClickTime = clickTime;
            }
        });
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FILE_PATH)) {
            filePath = intent.getStringExtra(EXTRA_FILE_PATH);
            loadPdfFile();
        } else if (intent.hasExtra(EXTRA_URI)) {
            fileUri = Uri.parse(intent.getStringExtra(EXTRA_URI));
            loadPdfFromUri();
        } else if (intent.getData() != null) {
            fileUri = intent.getData();
            loadPdfFromUri();
        }
    }

    private void loadPdfFile() {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new LoadPdfTask().execute(filePath);
    }

    private void loadPdfFromUri() {
        if (fileUri == null) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new LoadPdfFromUriTask().execute(fileUri);
    }

    @SuppressLint("StaticFieldLeak")
    private class LoadPdfTask extends AsyncTask<String, Void, Boolean> {
        private ProgressDialog progressDialog;
        private String errorMessage;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfViewerActivity.this);
            progressDialog.setMessage(getString(R.string.loading));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                File file = new File(params[0]);
                if (!file.exists()) {
                    errorMessage = "File not found";
                    return false;
                }

                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);
                pageCount = pdfRenderer.getPageCount();
                return true;
            } catch (IOException e) {
                errorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                setupPdfViewer();
            } else {
                Toast.makeText(PdfViewerActivity.this, 
                    getString(R.string.error) + ": " + errorMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class LoadPdfFromUriTask extends AsyncTask<Uri, Void, Boolean> {
        private ProgressDialog progressDialog;
        private String errorMessage;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfViewerActivity.this);
            progressDialog.setMessage(getString(R.string.loading));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            try {
                fileDescriptor = getContentResolver().openFileDescriptor(params[0], "r");
                if (fileDescriptor == null) {
                    errorMessage = "Cannot open file";
                    return false;
                }
                pdfRenderer = new PdfRenderer(fileDescriptor);
                pageCount = pdfRenderer.getPageCount();
                return true;
            } catch (IOException e) {
                errorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                setupPdfViewer();
            } else {
                Toast.makeText(PdfViewerActivity.this, 
                    getString(R.string.error) + ": " + errorMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void setupPdfViewer() {
        pageSeekBar.setMax(pageCount - 1);
        updatePageIndicator();
        
        String info = getString(R.string.pdf_info_format, pageCount, 
            Utils.formatFileSize(new File(filePath != null ? filePath : "").length()));
        tvPdfInfo.setText(info);

        // Load first page
        showPage(0);
    }

    private void showPage(int index) {
        if (pdfRenderer == null || index < 0 || index >= pageCount) {
            return;
        }

        // Close current page
        if (currentPage != null) {
            currentPage.close();
        }

        currentPageIndex = index;
        currentPage = pdfRenderer.openPage(index);

        // Create bitmap with zoom applied
        int width = (int) (currentPage.getWidth() * currentZoom);
        int height = (int) (currentPage.getHeight() * currentZoom);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Render page
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        pdfImageView.setImageBitmap(bitmap);
        updatePageIndicator();
        pageSeekBar.setProgress(index);
    }

    private void updatePageIndicator() {
        pageIndicator.setText(getString(R.string.pdf_page_indicator, currentPageIndex + 1, pageCount));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pdf_viewer_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_zoom_in) {
            zoomIn();
            return true;
        } else if (id == R.id.action_zoom_out) {
            zoomOut();
            return true;
        } else if (id == R.id.action_previous_page) {
            previousPage();
            return true;
        } else if (id == R.id.action_next_page) {
            nextPage();
            return true;
        } else if (id == R.id.action_first_page) {
            showPage(0);
            return true;
        } else if (id == R.id.action_last_page) {
            showPage(pageCount - 1);
            return true;
        } else if (id == R.id.action_share) {
            sharePdf();
            return true;
        } else if (id == R.id.action_edit) {
            openEditor();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void zoomIn() {
        if (currentZoom < MAX_ZOOM) {
            currentZoom += ZOOM_STEP;
            showPage(currentPageIndex);
        }
    }

    private void zoomOut() {
        if (currentZoom > MIN_ZOOM) {
            currentZoom -= ZOOM_STEP;
            showPage(currentPageIndex);
        }
    }

    private void previousPage() {
        if (currentPageIndex > 0) {
            showPage(currentPageIndex - 1);
        }
    }

    private void nextPage() {
        if (currentPageIndex < pageCount - 1) {
            showPage(currentPageIndex + 1);
        }
    }

    private void sharePdf() {
        if (filePath == null && fileUri == null) return;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        
        if (fileUri != null) {
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        } else {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this, getPackageName(), file);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        }
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
    }

    private void openEditor() {
        Intent intent = new Intent(this, PdfEditorActivity.class);
        if (filePath != null) {
            intent.putExtra(PdfEditorActivity.EXTRA_FILE_PATH, filePath);
        } else if (fileUri != null) {
            intent.putExtra(PdfEditorActivity.EXTRA_URI, fileUri.toString());
        }
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        if (pdfRenderer != null) {
            pdfRenderer.close();
            pdfRenderer = null;
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (IOException e) {
                // Ignore
            }
            fileDescriptor = null;
        }
        
        // Clear bitmaps
        for (Bitmap bitmap : pageBitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        pageBitmaps.clear();
    }
}
