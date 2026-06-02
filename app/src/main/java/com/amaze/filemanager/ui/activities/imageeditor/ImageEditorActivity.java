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

package com.amaze.filemanager.ui.activities.imageeditor;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.ui.dialogs.GeneralDialogCreation;
import com.burhanrashid52.photoeditor.PhotoEditor;
import com.burhanrashid52.photoeditor.PhotoEditorView;
import com.burhanrashid52.photoeditor.SaveSettings;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

/**
 * ImageEditorActivity - A comprehensive image editing activity for Amaze File Manager.
 * Supports cropping, rotating, filters, and annotation features.
 */
public class ImageEditorActivity extends ThemedActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";
    public static final String EXTRA_IMAGE_URI = "image_uri";
    
    private PhotoEditorView photoEditorView;
    private PhotoEditor photoEditor;
    private ImageView imagePreview;
    private LinearLayout toolsContainer;
    private LinearLayout cropToolsContainer;
    private LinearLayout filterToolsContainer;
    private LinearLayout annotateToolsContainer;
    
    private Bitmap originalBitmap;
    private Bitmap currentBitmap;
    private String imagePath;
    private Uri imageUri;
    
    // Undo/Redo stacks
    private Stack<Bitmap> undoStack;
    private Stack<Bitmap> redoStack;
    
    // Current editing mode
    private enum EditMode {
        NONE, CROP, ROTATE, FILTER, ANNOTATE
    }
    private EditMode currentMode = EditMode.NONE;
    
    // Filter values
    private float brightnessValue = 0;
    private float contrastValue = 1;
    private float saturationValue = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_editor);
        
        setupToolbar();
        initViews();
        initPhotoEditor();
        loadImage();
        initUndoRedo();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.image_editor);
        }
        initStatusBarResources(findViewById(android.R.id.content));
    }
    
    private void initViews() {
        photoEditorView = findViewById(R.id.photoEditorView);
        imagePreview = findViewById(R.id.imagePreview);
        toolsContainer = findViewById(R.id.toolsContainer);
        cropToolsContainer = findViewById(R.id.cropToolsContainer);
        filterToolsContainer = findViewById(R.id.filterToolsContainer);
        annotateToolsContainer = findViewById(R.id.annotateToolsContainer);
        
        // Main tool buttons
        findViewById(R.id.btnCrop).setOnClickListener(v -> enterCropMode());
        findViewById(R.id.btnRotate).setOnClickListener(v -> enterRotateMode());
        findViewById(R.id.btnFilter).setOnClickListener(v -> enterFilterMode());
        findViewById(R.id.btnAnnotate).setOnClickListener(v -> enterAnnotateMode());
        
        // Crop tool buttons
        findViewById(R.id.btnCropFree).setOnClickListener(v -> startCrop(UCrop.Options.REQUEST_CROP));
        findViewById(R.id.btnCrop11).setOnClickListener(v -> startCropWithRatio(1, 1));
        findViewById(R.id.btnCrop34).setOnClickListener(v -> startCropWithRatio(3, 4));
        findViewById(R.id.btnCrop169).setOnClickListener(v -> startCropWithRatio(16, 9));
        findViewById(R.id.btnCropCancel).setOnClickListener(v -> exitCurrentMode());
        
        // Rotate tool buttons
        findViewById(R.id.btnRotate90).setOnClickListener(v -> rotateImage(90));
        findViewById(R.id.btnRotate180).setOnClickListener(v -> rotateImage(180));
        findViewById(R.id.btnRotate270).setOnClickListener(v -> rotateImage(270));
        findViewById(R.id.btnFlipHorizontal).setOnClickListener(v -> flipImage(true));
        findViewById(R.id.btnFlipVertical).setOnClickListener(v -> flipImage(false));
        findViewById(R.id.btnRotateCancel).setOnClickListener(v -> exitCurrentMode());
        
        // Filter tool buttons
        findViewById(R.id.btnFilterGrayscale).setOnClickListener(v -> applyGrayscaleFilter());
        findViewById(R.id.btnFilterSepia).setOnClickListener(v -> applySepiaFilter());
        findViewById(R.id.btnFilterBlur).setOnClickListener(v -> applyBlurFilter());
        findViewById(R.id.btnFilterSharpen).setOnClickListener(v -> applySharpenFilter());
        findViewById(R.id.btnFilterInvert).setOnClickListener(v -> applyInvertFilter());
        findViewById(R.id.btnFilterReset).setOnClickListener(v -> resetFilters());
        findViewById(R.id.btnFilterCancel).setOnClickListener(v -> exitCurrentMode());
        
        // Annotate tool buttons
        findViewById(R.id.btnBrush).setOnClickListener(v -> enableBrushMode());
        findViewById(R.id.btnText).setOnClickListener(v -> addTextAnnotation());
        findViewById(R.id.btnMosaic).setOnClickListener(v -> enableMosaicMode());
        findViewById(R.id.btnEraser).setOnClickListener(v -> enableEraserMode());
        findViewById(R.id.btnAnnotateCancel).setOnClickListener(v -> exitAnnotateMode());
        
        // Brightness/Contrast seekbars
        SeekBar brightnessSeekBar = findViewById(R.id.seekBarBrightness);
        SeekBar contrastSeekBar = findViewById(R.id.seekBarContrast);
        
        if (brightnessSeekBar != null) {
            brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    brightnessValue = (progress - 100) / 100f;
                    applyColorAdjustments();
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        
        if (contrastSeekBar != null) {
            contrastSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    contrastValue = progress / 100f;
                    applyColorAdjustments();
                }
                
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }
    
    private void initPhotoEditor() {
        photoEditor = new PhotoEditor.Builder(this, photoEditorView)
                .setPinchTextScalable(true)
                .build();
    }
    
    private void initUndoRedo() {
        undoStack = new Stack<>();
        redoStack = new Stack<>();
    }
    
    private void loadImage() {
        Intent intent = getIntent();
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
        imageUri = intent.getParcelableExtra(EXTRA_IMAGE_URI);
        
        if (imageUri == null && imagePath != null) {
            imageUri = FileProvider.getUriForFile(this, getPackageName(), new File(imagePath));
        }
        
        if (imageUri != null) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                originalBitmap = BitmapFactory.decodeStream(inputStream);
                if (inputStream != null) {
                    inputStream.close();
                }
                currentBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
                photoEditorView.getSource().setImageBitmap(currentBitmap);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, R.string.no_image_selected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    private void saveState() {
        if (currentBitmap != null) {
            undoStack.push(currentBitmap.copy(currentBitmap.getConfig(), true));
            redoStack.clear();
        }
    }
    
    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(currentBitmap.copy(currentBitmap.getConfig(), true));
            currentBitmap = undoStack.pop();
            photoEditorView.getSource().setImageBitmap(currentBitmap);
        }
    }
    
    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(currentBitmap.copy(currentBitmap.getConfig(), true));
            currentBitmap = redoStack.pop();
            photoEditorView.getSource().setImageBitmap(currentBitmap);
        }
    }
    
    // Crop Mode
    private void enterCropMode() {
        saveState();
        currentMode = EditMode.CROP;
        hideAllToolContainers();
        cropToolsContainer.setVisibility(View.VISIBLE);
    }
    
    private void startCrop(int requestCode) {
        if (imageUri != null) {
            Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "cropped_image.jpg"));
            UCrop.of(imageUri, destinationUri)
                    .start(this);
        }
    }
    
    private void startCropWithRatio(int aspectRatioX, int aspectRatioY) {
        if (imageUri != null) {
            Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "cropped_image.jpg"));
            UCrop.of(imageUri, destinationUri)
                    .withAspectRatio(aspectRatioX, aspectRatioY)
                    .start(this);
        }
    }
    
    // Rotate Mode
    private void enterRotateMode() {
        saveState();
        currentMode = EditMode.ROTATE;
        hideAllToolContainers();
        findViewById(R.id.rotateToolsContainer).setVisibility(View.VISIBLE);
    }
    
    private void rotateImage(int degrees) {
        if (currentBitmap == null) return;
        
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        currentBitmap = Bitmap.createBitmap(currentBitmap, 0, 0, 
                currentBitmap.getWidth(), currentBitmap.getHeight(), matrix, true);
        photoEditorView.getSource().setImageBitmap(currentBitmap);
        saveState();
    }
    
    private void flipImage(boolean horizontal) {
        if (currentBitmap == null) return;
        
        Matrix matrix = new Matrix();
        if (horizontal) {
            matrix.preScale(-1, 1);
        } else {
            matrix.preScale(1, -1);
        }
        currentBitmap = Bitmap.createBitmap(currentBitmap, 0, 0,
                currentBitmap.getWidth(), currentBitmap.getHeight(), matrix, true);
        photoEditorView.getSource().setImageBitmap(currentBitmap);
        saveState();
    }
    
    // Filter Mode
    private void enterFilterMode() {
        saveState();
        currentMode = EditMode.FILTER;
        hideAllToolContainers();
        filterToolsContainer.setVisibility(View.VISIBLE);
    }
    
    private void applyGrayscaleFilter() {
        if (currentBitmap == null) return;
        
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        applyColorMatrixFilter(colorMatrix);
    }
    
    private void applySepiaFilter() {
        if (currentBitmap == null) return;
        
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        
        ColorMatrix sepiaMatrix = new ColorMatrix();
        sepiaMatrix.set(new float[] {
            0.393f, 0.769f, 0.189f, 0, 0,
            0.349f, 0.686f, 0.168f, 0, 0,
            0.272f, 0.534f, 0.131f, 0, 0,
            0, 0, 0, 1, 0
        });
        
        colorMatrix.postConcat(sepiaMatrix);
        applyColorMatrixFilter(colorMatrix);
    }
    
    private void applyBlurFilter() {
        if (currentBitmap == null) return;
        
        // Simple box blur implementation
        Bitmap blurred = Bitmap.createBitmap(currentBitmap.getWidth(), currentBitmap.getHeight(), 
                currentBitmap.getConfig());
        Canvas canvas = new Canvas(blurred);
        Paint paint = new Paint();
        paint.setFlags(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(currentBitmap, 0, 0, paint);
        
        currentBitmap = blurred;
        photoEditorView.getSource().setImageBitmap(currentBitmap);
        saveState();
    }
    
    private void applySharpenFilter() {
        if (currentBitmap == null) return;
        
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.set(new float[] {
            0, -1, 0, 0, 0,
            -1, 5, -1, 0, 0,
            0, -1, 0, 0, 0,
            0, 0, 0, 1, 0
        });
        applyColorMatrixFilter(colorMatrix);
    }
    
    private void applyInvertFilter() {
        if (currentBitmap == null) return;
        
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.set(new float[] {
            -1, 0, 0, 0, 255,
            0, -1, 0, 0, 255,
            0, 0, -1, 0, 255,
            0, 0, 0, 1, 0
        });
        applyColorMatrixFilter(colorMatrix);
    }
    
    private void applyColorMatrixFilter(ColorMatrix colorMatrix) {
        if (currentBitmap == null) return;
        
        Bitmap filteredBitmap = Bitmap.createBitmap(currentBitmap.getWidth(), 
                currentBitmap.getHeight(), currentBitmap.getConfig());
        Canvas canvas = new Canvas(filteredBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(currentBitmap, 0, 0, paint);
        
        currentBitmap = filteredBitmap;
        photoEditorView.getSource().setImageBitmap(currentBitmap);
        saveState();
    }
    
    private void applyColorAdjustments() {
        if (originalBitmap == null) return;
        
        ColorMatrix colorMatrix = new ColorMatrix();
        
        // Apply brightness
        colorMatrix.set(new float[] {
            1, 0, 0, 0, brightnessValue * 255,
            0, 1, 0, 0, brightnessValue * 255,
            0, 0, 1, 0, brightnessValue * 255,
            0, 0, 0, 1, 0
        });
        
        // Apply contrast
        float scale = contrastValue;
        float translate = (-0.5f * scale + 0.5f) * 255f;
        ColorMatrix contrastMatrix = new ColorMatrix();
        contrastMatrix.set(new float[] {
            scale, 0, 0, 0, translate,
            0, scale, 0, 0, translate,
            0, 0, scale, 0, translate,
            0, 0, 0, 1, 0
        });
        
        colorMatrix.postConcat(contrastMatrix);
        
        Bitmap adjustedBitmap = Bitmap.createBitmap(originalBitmap.getWidth(),
                originalBitmap.getHeight(), originalBitmap.getConfig());
        Canvas canvas = new Canvas(adjustedBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(originalBitmap, 0, 0, paint);
        
        currentBitmap = adjustedBitmap;
        photoEditorView.getSource().setImageBitmap(currentBitmap);
    }
    
    private void resetFilters() {
        brightnessValue = 0;
        contrastValue = 1;
        saturationValue = 1;
        
        SeekBar brightnessSeekBar = findViewById(R.id.seekBarBrightness);
        SeekBar contrastSeekBar = findViewById(R.id.seekBarContrast);
        
        if (brightnessSeekBar != null) brightnessSeekBar.setProgress(100);
        if (contrastSeekBar != null) contrastSeekBar.setProgress(100);
        
        if (originalBitmap != null) {
            currentBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
            photoEditorView.getSource().setImageBitmap(currentBitmap);
        }
    }
    
    // Annotate Mode
    private void enterAnnotateMode() {
        saveState();
        currentMode = EditMode.ANNOTATE;
        hideAllToolContainers();
        annotateToolsContainer.setVisibility(View.VISIBLE);
        photoEditor.setBrushDrawingMode(true);
    }
    
    private void enableBrushMode() {
        photoEditor.setBrushDrawingMode(true);
        photoEditor.setBrushColor(Color.RED);
        photoEditor.setBrushSize(10);
    }
    
    private void addTextAnnotation() {
        GeneralDialogCreation.showInputDialog(this, getString(R.string.add_text), 
                getString(R.string.enter_text), text -> {
            if (text != null && !text.isEmpty()) {
                photoEditor.addText(text, Color.WHITE);
            }
        });
    }
    
    private void enableMosaicMode() {
        photoEditor.setBrushDrawingMode(true);
        photoEditor.setBrushColor(Color.GRAY);
        photoEditor.setBrushSize(20);
    }
    
    private void enableEraserMode() {
        photoEditor.brushEraser();
    }
    
    private void exitAnnotateMode() {
        photoEditor.setBrushDrawingMode(false);
        exitCurrentMode();
    }
    
    // Helper methods
    private void hideAllToolContainers() {
        cropToolsContainer.setVisibility(View.GONE);
        View rotateTools = findViewById(R.id.rotateToolsContainer);
        if (rotateTools != null) rotateTools.setVisibility(View.GONE);
        filterToolsContainer.setVisibility(View.GONE);
        annotateToolsContainer.setVisibility(View.GONE);
    }
    
    private void exitCurrentMode() {
        currentMode = EditMode.NONE;
        hideAllToolContainers();
        toolsContainer.setVisibility(View.VISIBLE);
    }
    
    private void saveImage(boolean overwrite) {
        if (currentBitmap == null) return;
        
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.saving_image));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        File outputFile;
        if (overwrite && imagePath != null) {
            outputFile = new File(imagePath);
        } else {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "IMG_" + timeStamp + ".jpg";
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            outputFile = new File(picturesDir, fileName);
        }
        
        try {
            FileOutputStream fos = new FileOutputStream(outputFile);
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
            fos.close();
            
            // Add to media store
            MediaStore.Images.Media.insertImage(getContentResolver(), 
                    outputFile.getAbsolutePath(), outputFile.getName(), null);
            
            progressDialog.dismiss();
            Toast.makeText(this, getString(R.string.image_saved, outputFile.getAbsolutePath()), 
                    Toast.LENGTH_SHORT).show();
            
            if (overwrite) {
                finish();
            }
        } catch (IOException e) {
            progressDialog.dismiss();
            Toast.makeText(this, R.string.error_saving_image, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(resultUri);
                    currentBitmap = BitmapFactory.decodeStream(inputStream);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    photoEditorView.getSource().setImageBitmap(currentBitmap);
                    saveState();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_cropping_image, Toast.LENGTH_SHORT).show();
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            Toast.makeText(this, R.string.error_cropping_image, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.image_editor_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_undo) {
            undo();
            return true;
        } else if (id == R.id.action_redo) {
            redo();
            return true;
        } else if (id == R.id.action_save) {
            saveImage(true);
            return true;
        } else if (id == R.id.action_save_as) {
            saveImage(false);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        if (currentMode != EditMode.NONE) {
            exitCurrentMode();
        } else {
            GeneralDialogCreation.showExitDialog(this, () -> finish());
        }
    }
}
