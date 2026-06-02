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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Editor Activity - Provides PDF editing capabilities including merge, split, 
 * convert to images, and annotation features.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PdfEditorActivity extends ThemedActivity {

    public static final String EXTRA_FILE_PATH = "pdf_file_path";
    public static final String EXTRA_URI = "pdf_uri";

    private String filePath;
    private Uri fileUri;
    private TextView tvFileInfo;
    private ListView lvOperations;
    private PdfOperationAdapter operationAdapter;
    private List<PdfOperation> operations;

    // PDF processing utilities
    private PdfProcessor pdfProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_editor);

        setupToolbar();
        initViews();
        handleIntent();
        
        pdfProcessor = new PdfProcessor(this);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.pdf_editor_title);
        }
    }

    private void initViews() {
        tvFileInfo = findViewById(R.id.tv_file_info);
        lvOperations = findViewById(R.id.lv_operations);

        operations = new ArrayList<>();
        operations.add(new PdfOperation(PdfOperationType.MERGE, getString(R.string.pdf_merge), 
            getString(R.string.pdf_merge_desc), R.drawable.ic_baseline_content_copy_24));
        operations.add(new PdfOperation(PdfOperationType.SPLIT, getString(R.string.pdf_split), 
            getString(R.string.pdf_split_desc), R.drawable.ic_baseline_vertical_split_white_24));
        operations.add(new PdfOperation(PdfOperationType.TO_IMAGES, getString(R.string.pdf_to_images), 
            getString(R.string.pdf_to_images_desc), R.drawable.ic_photo_library_white_24dp));
        operations.add(new PdfOperation(PdfOperationType.ANNOTATE, getString(R.string.pdf_annotate), 
            getString(R.string.pdf_annotate_desc), R.drawable.ic_baseline_brush_white_24));
        operations.add(new PdfOperation(PdfOperationType.COMPRESS, getString(R.string.pdf_compress), 
            getString(R.string.pdf_compress_desc), R.drawable.ic_zip_box_white));
        operations.add(new PdfOperation(PdfOperationType.EXTRACT_TEXT, getString(R.string.pdf_extract_text), 
            getString(R.string.pdf_extract_text_desc), R.drawable.ic_library_books_white_24dp));

        operationAdapter = new PdfOperationAdapter(this, operations);
        lvOperations.setAdapter(operationAdapter);

        lvOperations.setOnItemClickListener((parent, view, position, id) -> {
            PdfOperation operation = operations.get(position);
            handleOperation(operation.getType());
        });
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FILE_PATH)) {
            filePath = intent.getStringExtra(EXTRA_FILE_PATH);
            updateFileInfo();
        } else if (intent.hasExtra(EXTRA_URI)) {
            fileUri = Uri.parse(intent.getStringExtra(EXTRA_URI));
            updateFileInfo();
        }
    }

    private void updateFileInfo() {
        if (filePath != null) {
            File file = new File(filePath);
            String info = getString(R.string.pdf_file_info, file.getName(), 
                Utils.formatFileSize(file.length()));
            tvFileInfo.setText(info);
        } else if (fileUri != null) {
            tvFileInfo.setText(fileUri.toString());
        }
    }

    private void handleOperation(PdfOperationType type) {
        switch (type) {
            case MERGE:
                showMergeDialog();
                break;
            case SPLIT:
                showSplitDialog();
                break;
            case TO_IMAGES:
                showConvertToImagesDialog();
                break;
            case ANNOTATE:
                openAnnotationEditor();
                break;
            case COMPRESS:
                showCompressDialog();
                break;
            case EXTRACT_TEXT:
                extractText();
                break;
        }
    }

    private void showMergeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pdf_merge);
        
        View view = getLayoutInflater().inflate(R.layout.dialog_pdf_merge, null);
        ListView lvSelectedFiles = view.findViewById(R.id.lv_selected_files);
        EditText etOutputName = view.findViewById(R.id.et_output_name);
        
        List<String> selectedFiles = new ArrayList<>();
        if (filePath != null) {
            selectedFiles.add(filePath);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1, selectedFiles);
        lvSelectedFiles.setAdapter(adapter);
        
        builder.setView(view);
        builder.setPositiveButton(R.string.merge, (dialog, which) -> {
            if (selectedFiles.size() < 2) {
                Toast.makeText(this, R.string.pdf_merge_min_files, Toast.LENGTH_SHORT).show();
                return;
            }
            String outputName = etOutputName.getText().toString();
            if (outputName.isEmpty()) {
                outputName = "merged_" + System.currentTimeMillis() + ".pdf";
            }
            if (!outputName.endsWith(".pdf")) {
                outputName += ".pdf";
            }
            performMerge(selectedFiles, outputName);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showSplitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pdf_split);
        
        View view = getLayoutInflater().inflate(R.layout.dialog_pdf_split, null);
        EditText etPageRange = view.findViewById(R.id.et_page_range);
        EditText etOutputPrefix = view.findViewById(R.id.et_output_prefix);
        Spinner spinnerSplitMode = view.findViewById(R.id.spinner_split_mode);
        
        ArrayAdapter<CharSequence> splitModeAdapter = ArrayAdapter.createFromResource(this,
            R.array.pdf_split_modes, android.R.layout.simple_spinner_item);
        splitModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSplitMode.setAdapter(splitModeAdapter);
        
        builder.setView(view);
        builder.setPositiveButton(R.string.split, (dialog, which) -> {
            String pageRange = etPageRange.getText().toString();
            String outputPrefix = etOutputPrefix.getText().toString();
            if (outputPrefix.isEmpty()) {
                outputPrefix = "split_";
            }
            int splitMode = spinnerSplitMode.getSelectedItemPosition();
            performSplit(pageRange, outputPrefix, splitMode);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showConvertToImagesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pdf_to_images);
        
        View view = getLayoutInflater().inflate(R.layout.dialog_pdf_to_images, null);
        EditText etPageRange = view.findViewById(R.id.et_page_range);
        Spinner spinnerFormat = view.findViewById(R.id.spinner_format);
        Spinner spinnerQuality = view.findViewById(R.id.spinner_quality);
        
        ArrayAdapter<CharSequence> formatAdapter = ArrayAdapter.createFromResource(this,
            R.array.image_formats, android.R.layout.simple_spinner_item);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFormat.setAdapter(formatAdapter);
        
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(this,
            R.array.image_qualities, android.R.layout.simple_spinner_item);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuality.setAdapter(qualityAdapter);
        
        builder.setView(view);
        builder.setPositiveButton(R.string.convert, (dialog, which) -> {
            String pageRange = etPageRange.getText().toString();
            String format = spinnerFormat.getSelectedItem().toString().toLowerCase();
            int quality = spinnerQuality.getSelectedItemPosition();
            performConvertToImages(pageRange, format, quality);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void openAnnotationEditor() {
        Intent intent = new Intent(this, PdfAnnotationActivity.class);
        if (filePath != null) {
            intent.putExtra(PdfAnnotationActivity.EXTRA_FILE_PATH, filePath);
        } else if (fileUri != null) {
            intent.putExtra(PdfAnnotationActivity.EXTRA_URI, fileUri.toString());
        }
        startActivity(intent);
    }

    private void showCompressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pdf_compress);
        
        View view = getLayoutInflater().inflate(R.layout.dialog_pdf_compress, null);
        Spinner spinnerQuality = view.findViewById(R.id.spinner_quality);
        EditText etOutputName = view.findViewById(R.id.et_output_name);
        
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(this,
            R.array.compression_qualities, android.R.layout.simple_spinner_item);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuality.setAdapter(qualityAdapter);
        
        builder.setView(view);
        builder.setPositiveButton(R.string.compress, (dialog, which) -> {
            int quality = spinnerQuality.getSelectedItemPosition();
            String outputName = etOutputName.getText().toString();
            if (outputName.isEmpty()) {
                outputName = "compressed_" + System.currentTimeMillis() + ".pdf";
            }
            if (!outputName.endsWith(".pdf")) {
                outputName += ".pdf";
            }
            performCompress(quality, outputName);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void extractText() {
        if (filePath == null) {
            Toast.makeText(this, R.string.pdf_no_file, Toast.LENGTH_SHORT).show();
            return;
        }
        new ExtractTextTask().execute(filePath);
    }

    @SuppressLint("StaticFieldLeak")
    private class ExtractTextTask extends AsyncTask<String, Void, String> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfEditorActivity.this);
            progressDialog.setMessage(getString(R.string.extracting));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            return pdfProcessor.extractText(params[0]);
        }

        @Override
        protected void onPostExecute(String text) {
            progressDialog.dismiss();
            showExtractedText(text);
        }
    }

    private void showExtractedText(String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pdf_extracted_text);
        
        View view = getLayoutInflater().inflate(R.layout.dialog_extracted_text, null);
        EditText etText = view.findViewById(R.id.et_extracted_text);
        etText.setText(text);
        
        builder.setView(view);
        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            // Save text to file
            saveTextToFile(etText.getText().toString());
        });
        builder.setNegativeButton(R.string.close, null);
        builder.show();
    }

    private void saveTextToFile(String text) {
        String outputName = "extracted_" + System.currentTimeMillis() + ".txt";
        new SaveTextTask().execute(text, outputName);
    }

    @SuppressLint("StaticFieldLeak")
    private class SaveTextTask extends AsyncTask<String, Void, Boolean> {
        private ProgressDialog progressDialog;
        private String outputPath;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfEditorActivity.this);
            progressDialog.setMessage(getString(R.string.saving));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            outputPath = pdfProcessor.saveText(params[0], params[1]);
            return outputPath != null;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(PdfEditorActivity.this, 
                    getString(R.string.saved_to, outputPath), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PdfEditorActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performMerge(List<String> files, String outputName) {
        new MergePdfTask().execute(files.toArray(new String[0]), outputName);
    }

    @SuppressLint("StaticFieldLeak")
    private class MergePdfTask extends AsyncTask<Object, Void, Boolean> {
        private ProgressDialog progressDialog;
        private String outputPath;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfEditorActivity.this);
            progressDialog.setMessage(getString(R.string.pdf_merging));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            String[] files = (String[]) params[0];
            String outputName = (String) params[1];
            outputPath = pdfProcessor.mergePdfs(files, outputName);
            return outputPath != null;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(PdfEditorActivity.this, 
                    getString(R.string.pdf_merge_success, outputPath), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PdfEditorActivity.this, R.string.pdf_merge_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performSplit(String pageRange, String outputPrefix, int splitMode) {
        if (filePath == null) {
            Toast.makeText(this, R.string.pdf_no_file, Toast.LENGTH_SHORT).show();
            return;
        }
        new SplitPdfTask().execute(filePath, pageRange, outputPrefix, splitMode);
    }

    @SuppressLint("StaticFieldLeak")
    private class SplitPdfTask extends AsyncTask<Object, Void, Boolean> {
        private ProgressDialog progressDialog;
        private List<String> outputPaths;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfEditorActivity.this);
            progressDialog.setMessage(getString(R.string.pdf_splitting));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            String filePath = (String) params[0];
            String pageRange = (String) params[1];
            String outputPrefix = (String) params[2];
            int splitMode = (int) params[3];
            outputPaths = pdfProcessor.splitPdf(filePath, pageRange, outputPrefix, splitMode);
            return outputPaths != null && !outputPaths.isEmpty();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(PdfEditorActivity.this, 
                    getString(R.string.pdf_split_success, outputPaths.size()), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PdfEditorActivity.this, R.string.pdf_split_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performConvertToImages(String pageRange, String format, int quality) {
        if (filePath == null) {
            Toast.makeText(this, R.string.pdf_no_file, Toast.LENGTH_SHORT).show();
            return;
        }
        new ConvertToImagesTask().execute(filePath, pageRange, format, quality);
    }

    @SuppressLint("StaticFieldLeak")
    private class ConvertToImagesTask extends AsyncTask<Object, Void, Boolean> {
        private ProgressDialog progressDialog;
        private List<String> outputPaths;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfEditorActivity.this);
            progressDialog.setMessage(getString(R.string.converting));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            String filePath = (String) params[0];
            String pageRange = (String) params[1];
            String format = (String) params[2];
            int quality = (int) params[3];
            outputPaths = pdfProcessor.convertToImages(filePath, pageRange, format, quality);
            return outputPaths != null && !outputPaths.isEmpty();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(PdfEditorActivity.this, 
                    getString(R.string.pdf_convert_success, outputPaths.size()), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PdfEditorActivity.this, R.string.pdf_convert_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performCompress(int quality, String outputName) {
        if (filePath == null) {
            Toast.makeText(this, R.string.pdf_no_file, Toast.LENGTH_SHORT).show();
            return;
        }
        new CompressPdfTask().execute(filePath, quality, outputName);
    }

    @SuppressLint("StaticFieldLeak")
    private class CompressPdfTask extends AsyncTask<Object, Void, Boolean> {
        private ProgressDialog progressDialog;
        private String outputPath;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(PdfEditorActivity.this);
            progressDialog.setMessage(getString(R.string.compressing));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            String filePath = (String) params[0];
            int quality = (int) params[1];
            String outputName = (String) params[2];
            outputPath = pdfProcessor.compressPdf(filePath, quality, outputName);
            return outputPath != null;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressDialog.dismiss();
            if (success) {
                Toast.makeText(PdfEditorActivity.this, 
                    getString(R.string.pdf_compress_success, outputPath), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PdfEditorActivity.this, R.string.pdf_compress_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pdf_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_view) {
            openViewer();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void openViewer() {
        Intent intent = new Intent(this, PdfViewerActivity.class);
        if (filePath != null) {
            intent.putExtra(PdfViewerActivity.EXTRA_FILE_PATH, filePath);
        } else if (fileUri != null) {
            intent.putExtra(PdfViewerActivity.EXTRA_URI, fileUri.toString());
        }
        startActivity(intent);
    }

    /**
     * Enum for PDF operation types
     */
    public enum PdfOperationType {
        MERGE, SPLIT, TO_IMAGES, ANNOTATE, COMPRESS, EXTRACT_TEXT
    }

    /**
     * Model class for PDF operations
     */
    public static class PdfOperation {
        private PdfOperationType type;
        private String title;
        private String description;
        private int iconResId;

        public PdfOperation(PdfOperationType type, String title, String description, int iconResId) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.iconResId = iconResId;
        }

        public PdfOperationType getType() { return type; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getIconResId() { return iconResId; }
    }
}
