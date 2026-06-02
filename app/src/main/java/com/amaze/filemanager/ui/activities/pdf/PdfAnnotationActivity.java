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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Annotation Activity - Provides PDF annotation capabilities including
 * highlighting, underlining, and adding text notes.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PdfAnnotationActivity extends ThemedActivity {

    public static final String EXTRA_FILE_PATH = "pdf_file_path";
    public static final String EXTRA_URI = "pdf_uri";

    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor fileDescriptor;
    private AnnotationView annotationView;
    private SeekBar pageSeekBar;
    private TextView pageIndicator;

    private int currentPageIndex = 0;
    private int pageCount = 0;
    private String filePath;
    private Uri fileUri;

    // Annotation state
    private AnnotationType currentAnnotationType = AnnotationType.HIGHLIGHT;
    private int currentColor = Color.YELLOW;
    private float strokeWidth = 5f;
    private List<Annotation> annotations = new ArrayList<>();

    // Available annotation types
    public enum AnnotationType {
        HIGHLIGHT, UNDERLINE, STRIKE_THROUGH, NOTE, FREEHAND, ERASER
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_annotation);

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
            actionBar.setTitle(R.string.pdf_annotate);
        }
    }

    private void initViews() {
        annotationView = findViewById(R.id.annotation_view);
        pageSeekBar = findViewById(R.id.page_seekbar);
        pageIndicator = findViewById(R.id.page_indicator);

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

        // Setup annotation view
        annotationView.setOnAnnotationListener(new AnnotationView.OnAnnotationListener() {
            @Override
            public void onAnnotationCreated(Annotation annotation) {
                annotations.add(annotation);
            }

            @Override
            public void onAnnotationDeleted(Annotation annotation) {
                annotations.remove(annotation);
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
        private Bitmap firstPageBitmap;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfAnnotationActivity.this);
            progressDialog.setMessage(getString(R.string.loading));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                File file = new File(params[0]);
                if (!file.exists()) return false;

                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(fileDescriptor);
                pageCount = pdfRenderer.getPageCount();

                // Render first page
                currentPage = pdfRenderer.openPage(0);
                firstPageBitmap = Bitmap.createBitmap(
                    currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
                currentPage.render(firstPageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                currentPage.close();

                return true;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                setupPdfViewer(firstPageBitmap);
            } else {
                Toast.makeText(PdfAnnotationActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class LoadPdfFromUriTask extends AsyncTask<Uri, Void, Boolean> {
        private ProgressDialog progressDialog;
        private Bitmap firstPageBitmap;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfAnnotationActivity.this);
            progressDialog.setMessage(getString(R.string.loading));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            try {
                fileDescriptor = getContentResolver().openFileDescriptor(params[0], "r");
                if (fileDescriptor == null) return false;

                pdfRenderer = new PdfRenderer(fileDescriptor);
                pageCount = pdfRenderer.getPageCount();

                // Render first page
                currentPage = pdfRenderer.openPage(0);
                firstPageBitmap = Bitmap.createBitmap(
                    currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
                currentPage.render(firstPageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                currentPage.close();

                return true;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                setupPdfViewer(firstPageBitmap);
            } else {
                Toast.makeText(PdfAnnotationActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void setupPdfViewer(Bitmap firstPage) {
        pageSeekBar.setMax(pageCount - 1);
        updatePageIndicator();
        annotationView.setBaseBitmap(firstPage);
        showPage(0);
    }

    private void showPage(int index) {
        if (pdfRenderer == null || index < 0 || index >= pageCount) {
            return;
        }

        // Save current annotations for the page
        saveCurrentPageAnnotations();

        // Close current page
        if (currentPage != null) {
            currentPage.close();
        }

        currentPageIndex = index;

        // Load new page
        new RenderPageTask().execute(index);
        updatePageIndicator();
        pageSeekBar.setProgress(index);
    }

    @SuppressLint("StaticFieldLeak")
    private class RenderPageTask extends AsyncTask<Integer, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Integer... params) {
            try {
                currentPage = pdfRenderer.openPage(params[0]);
                Bitmap bitmap = Bitmap.createBitmap(
                    currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
                currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                currentPage.close();
                return bitmap;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                annotationView.setBaseBitmap(bitmap);
                loadAnnotationsForPage(currentPageIndex);
            }
        }
    }

    private void saveCurrentPageAnnotations() {
        // Save annotations for current page
        // This would be implemented with proper storage mechanism
    }

    private void loadAnnotationsForPage(int pageIndex) {
        // Load annotations for the given page
        // This would be implemented with proper storage mechanism
        annotationView.clearAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.getPageIndex() == pageIndex) {
                annotationView.addAnnotation(annotation);
            }
        }
    }

    private void updatePageIndicator() {
        pageIndicator.setText(getString(R.string.pdf_page_indicator, currentPageIndex + 1, pageCount));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pdf_annotation_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_highlight) {
            setAnnotationType(AnnotationType.HIGHLIGHT);
            return true;
        } else if (id == R.id.action_underline) {
            setAnnotationType(AnnotationType.UNDERLINE);
            return true;
        } else if (id == R.id.action_strike_through) {
            setAnnotationType(AnnotationType.STRIKE_THROUGH);
            return true;
        } else if (id == R.id.action_note) {
            setAnnotationType(AnnotationType.NOTE);
            return true;
        } else if (id == R.id.action_freehand) {
            setAnnotationType(AnnotationType.FREEHAND);
            return true;
        } else if (id == R.id.action_eraser) {
            setAnnotationType(AnnotationType.ERASER);
            return true;
        } else if (id == R.id.action_color_red) {
            setColor(Color.RED);
            return true;
        } else if (id == R.id.action_color_yellow) {
            setColor(Color.YELLOW);
            return true;
        } else if (id == R.id.action_color_green) {
            setColor(Color.GREEN);
            return true;
        } else if (id == R.id.action_color_blue) {
            setColor(Color.BLUE);
            return true;
        } else if (id == R.id.action_undo) {
            annotationView.undo();
            return true;
        } else if (id == R.id.action_save) {
            saveAnnotatedPdf();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void setAnnotationType(AnnotationType type) {
        currentAnnotationType = type;
        annotationView.setAnnotationType(type);
        Toast.makeText(this, getString(R.string.annotation_type_set, type.name()), Toast.LENGTH_SHORT).show();
    }

    private void setColor(int color) {
        currentColor = color;
        annotationView.setColor(color);
    }

    private void saveAnnotatedPdf() {
        new SaveAnnotatedPdfTask().execute();
    }

    @SuppressLint("StaticFieldLeak")
    private class SaveAnnotatedPdfTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progressDialog;
        private String outputPath;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfAnnotationActivity.this);
            progressDialog.setMessage(getString(R.string.saving));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // Save annotated PDF
                // This is a simplified implementation
                // In production, you would use a PDF library like iText or PDFBox
                outputPath = filePath != null ? 
                    filePath.replace(".pdf", "_annotated.pdf") : 
                    getExternalFilesDir(null) + "/annotated_" + System.currentTimeMillis() + ".pdf";
                
                // Save current page bitmap with annotations
                Bitmap annotatedBitmap = annotationView.getAnnotatedBitmap();
                if (annotatedBitmap != null) {
                    FileOutputStream fos = new FileOutputStream(outputPath);
                    annotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                }
                
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(PdfAnnotationActivity.this, 
                    getString(R.string.saved_to, outputPath), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PdfAnnotationActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
            }
        }
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
    }

    /**
     * Annotation model class
     */
    public static class Annotation {
        private int pageIndex;
        private AnnotationType type;
        private int color;
        private float startX, startY, endX, endY;
        private String text;
        private Path freehandPath;

        public Annotation(int pageIndex, AnnotationType type, int color) {
            this.pageIndex = pageIndex;
            this.type = type;
            this.color = color;
        }

        // Getters and setters
        public int getPageIndex() { return pageIndex; }
        public AnnotationType getType() { return type; }
        public int getColor() { return color; }
        public float getStartX() { return startX; }
        public void setStartX(float startX) { this.startX = startX; }
        public float getStartY() { return startY; }
        public void setStartY(float startY) { this.startY = startY; }
        public float getEndX() { return endX; }
        public void setEndX(float endX) { this.endX = endX; }
        public float getEndY() { return endY; }
        public void setEndY(float endY) { this.endY = endY; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Path getFreehandPath() { return freehandPath; }
        public void setFreehandPath(Path freehandPath) { this.freehandPath = freehandPath; }
    }
}
