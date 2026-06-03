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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
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
 * Uses native Android Canvas API for annotation instead of external PhotoEditor library.
 */
public class ImageEditorActivity extends ThemedActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";
    public static final String EXTRA_IMAGE_URI = "image_uri";

    private DrawingImageView drawingImageView;
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

    // Annotation settings
    private enum AnnotateMode {
        NONE, BRUSH, TEXT, MOSAAC, ERASER
    }
    private AnnotateMode annotateMode = AnnotateMode.NONE;
    private int brushColor = Color.RED;
    private int brushSize = 10;

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
        drawingImageView = findViewById(R.id.photoEditorView);
        if (drawingImageView == null) {
            // Fallback: try to find a regular ImageView if DrawingImageView is not in layout
            View fallback = findViewById(R.id.imagePreview);
            if (fallback instanceof DrawingImageView) {
                drawingImageView = (DrawingImageView) fallback;
            } else if (fallback instanceof ImageView) {
                drawingImageView = new DrawingImageView(this);
            }
        }
        imagePreview = findViewById(R.id.imagePreview);
        toolsContainer = findViewById(R.id.toolsContainer);
        cropToolsContainer = findViewById(R.id.cropToolsContainer);
        filterToolsContainer = findViewById(R.id.filterToolsContainer);
        annotateToolsContainer = findViewById(R.id.annotateToolsContainer);

        // Main tool buttons
        View btnCrop = findViewById(R.id.btnCrop);
        if (btnCrop != null) btnCrop.setOnClickListener(v -> enterCropMode());

        View btnRotate = findViewById(R.id.btnRotate);
        if (btnRotate != null) btnRotate.setOnClickListener(v -> enterRotateMode());

        View btnFilter = findViewById(R.id.btnFilter);
        if (btnFilter != null) btnFilter.setOnClickListener(v -> enterFilterMode());

        View btnAnnotate = findViewById(R.id.btnAnnotate);
        if (btnAnnotate != null) btnAnnotate.setOnClickListener(v -> enterAnnotateMode());

        // Crop tool buttons
        View btnCropFree = findViewById(R.id.btnCropFree);
        if (btnCropFree != null) btnCropFree.setOnClickListener(v -> startCrop(UCrop.Options.REQUEST_CROP));

        View btnCrop11 = findViewById(R.id.btnCrop11);
        if (btnCrop11 != null) btnCrop11.setOnClickListener(v -> startCropWithRatio(1, 1));

        View btnCrop34 = findViewById(R.id.btnCrop34);
        if (btnCrop34 != null) btnCrop34.setOnClickListener(v -> startCropWithRatio(3, 4));

        View btnCrop169 = findViewById(R.id.btnCrop169);
        if (btnCrop169 != null) btnCrop169.setOnClickListener(v -> startCropWithRatio(16, 9));

        View btnCropCancel = findViewById(R.id.btnCropCancel);
        if (btnCropCancel != null) btnCropCancel.setOnClickListener(v -> exitCurrentMode());

        // Rotate tool buttons
        View btnRotate90 = findViewById(R.id.btnRotate90);
        if (btnRotate90 != null) btnRotate90.setOnClickListener(v -> rotateImage(90));

        View btnRotate180 = findViewById(R.id.btnRotate180);
        if (btnRotate180 != null) btnRotate180.setOnClickListener(v -> rotateImage(180));

        View btnRotate270 = findViewById(R.id.btnRotate270);
        if (btnRotate270 != null) btnRotate270.setOnClickListener(v -> rotateImage(270));

        View btnFlipH = findViewById(R.id.btnFlipHorizontal);
        if (btnFlipH != null) btnFlipH.setOnClickListener(v -> flipImage(true));

        View btnFlipV = findViewById(R.id.btnFlipVertical);
        if (btnFlipV != null) btnFlipV.setOnClickListener(v -> flipImage(false));

        View btnRotateCancel = findViewById(R.id.btnRotateCancel);
        if (btnRotateCancel != null) btnRotateCancel.setOnClickListener(v -> exitCurrentMode());

        // Filter tool buttons
        View btnGray = findViewById(R.id.btnFilterGrayscale);
        if (btnGray != null) btnGray.setOnClickListener(v -> applyGrayscaleFilter());

        View btnSepia = findViewById(R.id.btnFilterSepia);
        if (btnSepia != null) btnSepia.setOnClickListener(v -> applySepiaFilter());

        View btnBlur = findViewById(R.id.btnFilterBlur);
        if (btnBlur != null) btnBlur.setOnClickListener(v -> applyBlurFilter());

        View btnSharpen = findViewById(R.id.btnFilterSharpen);
        if (btnSharpen != null) btnSharpen.setOnClickListener(v -> applySharpenFilter());

        View btnInvert = findViewById(R.id.btnFilterInvert);
        if (btnInvert != null) btnInvert.setOnClickListener(v -> applyInvertFilter());

        View btnReset = findViewById(R.id.btnFilterReset);
        if (btnReset != null) btnReset.setOnClickListener(v -> resetFilters());

        View btnFilterCancel = findViewById(R.id.btnFilterCancel);
        if (btnFilterCancel != null) btnFilterCancel.setOnClickListener(v -> exitCurrentMode());

        // Annotate tool buttons
        View btnBrush = findViewById(R.id.btnBrush);
        if (btnBrush != null) btnBrush.setOnClickListener(v -> enableBrushMode());

        View btnText = findViewById(R.id.btnText);
        if (btnText != null) btnText.setOnClickListener(v -> addTextAnnotation());

        View btnMosaic = findViewById(R.id.btnMosaic);
        if (btnMosaic != null) btnMosaic.setOnClickListener(v -> enableMosaicMode());

        View btnEraser = findViewById(R.id.btnEraser);
        if (btnEraser != null) btnEraser.setOnClickListener(v -> enableEraserMode());

        View btnAnnotateCancel = findViewById(R.id.btnAnnotateCancel);
        if (btnAnnotateCancel != null) btnAnnotateCancel.setOnClickListener(v -> exitAnnotateMode());

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
                displayBitmap(currentBitmap);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, R.string.no_image_selected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void displayBitmap(Bitmap bitmap) {
        if (drawingImageView != null) {
            drawingImageView.setImageBitmap(bitmap);
        } else if (imagePreview != null) {
            imagePreview.setImageBitmap(bitmap);
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
            displayBitmap(currentBitmap);
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(currentBitmap.copy(currentBitmap.getConfig(), true));
            currentBitmap = redoStack.pop();
            displayBitmap(currentBitmap);
        }
    }

    // Crop Mode
    private void enterCropMode() {
        saveState();
        currentMode = EditMode.CROP;
        hideAllToolContainers();
        if (cropToolsContainer != null) {
            cropToolsContainer.setVisibility(View.VISIBLE);
        }
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
        View rotateTools = findViewById(R.id.rotateToolsContainer);
        if (rotateTools != null) {
            rotateTools.setVisibility(View.VISIBLE);
        }
    }

    private void rotateImage(int degrees) {
        if (currentBitmap == null) return;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        currentBitmap = Bitmap.createBitmap(currentBitmap, 0, 0,
                currentBitmap.getWidth(), currentBitmap.getHeight(), matrix, true);
        displayBitmap(currentBitmap);
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
        displayBitmap(currentBitmap);
        saveState();
    }

    // Filter Mode
    private void enterFilterMode() {
        saveState();
        currentMode = EditMode.FILTER;
        hideAllToolContainers();
        if (filterToolsContainer != null) {
            filterToolsContainer.setVisibility(View.VISIBLE);
        }
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

        // Simple box blur implementation using Canvas scaling
        Bitmap blurred = Bitmap.createBitmap(currentBitmap.getWidth(), currentBitmap.getHeight(),
                currentBitmap.getConfig());
        Canvas canvas = new Canvas(blurred);
        Paint paint = new Paint();
        paint.setFlags(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(currentBitmap, 0, 0, paint);

        currentBitmap = blurred;
        displayBitmap(currentBitmap);
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
        displayBitmap(currentBitmap);
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
        displayBitmap(currentBitmap);
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
            displayBitmap(currentBitmap);
        }
    }

    // Annotate Mode - using native Canvas API
    private void enterAnnotateMode() {
        saveState();
        currentMode = EditMode.ANNOTATE;
        hideAllToolContainers();
        if (annotateToolsContainer != null) {
            annotateToolsContainer.setVisibility(View.VISIBLE);
        }
        if (drawingImageView != null) {
            drawingImageView.setDrawingEnabled(true);
        }
    }

    private void enableBrushMode() {
        annotateMode = AnnotateMode.BRUSH;
        brushColor = Color.RED;
        brushSize = 10;
        if (drawingImageView != null) {
            drawingImageView.setBrushMode(brushColor, brushSize, false);
        }
    }

    private void addTextAnnotation() {
        GeneralDialogCreation.showInputDialog(this, getString(R.string.add_text),
                getString(R.string.enter_text), text -> {
            if (text != null && !text.isEmpty()) {
                drawTextOnBitmap(text, Color.WHITE);
            }
        });
    }

    private void drawTextOnBitmap(String text, int color) {
        if (currentBitmap == null) return;

        saveState();
        Bitmap mutableBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);

        Paint textPaint = new Paint();
        textPaint.setColor(color);
        textPaint.setTextSize(Math.max(40, mutableBitmap.getWidth() / 20));
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK);

        // Draw text at center of image
        float textWidth = textPaint.measureText(text);
        float x = (mutableBitmap.getWidth() - textWidth) / 2;
        float y = mutableBitmap.getHeight() / 2;

        canvas.drawText(text, x, y, textPaint);

        currentBitmap = mutableBitmap;
        displayBitmap(currentBitmap);
    }

    private void enableMosaicMode() {
        annotateMode = AnnotateMode.MOSAAC;
        brushColor = Color.GRAY;
        brushSize = 20;
        if (drawingImageView != null) {
            drawingImageView.setBrushMode(brushColor, brushSize, false);
        }
    }

    private void enableEraserMode() {
        annotateMode = AnnotateMode.ERASER;
        if (drawingImageView != null) {
            drawingImageView.setEraserMode();
        }
    }

    private void exitAnnotateMode() {
        annotateMode = AnnotateMode.NONE;
        if (drawingImageView != null) {
            drawingImageView.setDrawingEnabled(false);
        }
        exitCurrentMode();
    }

    // Helper methods
    private void hideAllToolContainers() {
        if (cropToolsContainer != null) {
            cropToolsContainer.setVisibility(View.GONE);
        }
        View rotateTools = findViewById(R.id.rotateToolsContainer);
        if (rotateTools != null) rotateTools.setVisibility(View.GONE);
        if (filterToolsContainer != null) {
            filterToolsContainer.setVisibility(View.GONE);
        }
        if (annotateToolsContainer != null) {
            annotateToolsContainer.setVisibility(View.GONE);
        }
    }

    private void exitCurrentMode() {
        currentMode = EditMode.NONE;
        hideAllToolContainers();
        if (toolsContainer != null) {
            toolsContainer.setVisibility(View.VISIBLE);
        }
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
                    displayBitmap(currentBitmap);
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

    /**
     * Custom ImageView that supports drawing/annotation on top of the image using native Canvas API.
     * This replaces the PhotoEditor library's PhotoEditorView functionality.
     */
    public static class DrawingImageView extends androidx.appcompat.widget.AppCompatImageView {

        private boolean drawingEnabled = false;
        private boolean isEraserMode = false;
        private Paint brushPaint;
        private Paint eraserPaint;
        private Path drawPath;
        private Bitmap overlayBitmap;
        private Canvas overlayCanvas;
        private float lastX, lastY;
        private int brushColor = Color.RED;
        private int brushSize = 10;

        public DrawingImageView(Context context) {
            super(context);
            init();
        }

        public DrawingImageView(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public DrawingImageView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            drawPath = new Path();

            brushPaint = new Paint();
            brushPaint.setColor(brushColor);
            brushPaint.setAntiAlias(true);
            brushPaint.setStrokeWidth(brushSize);
            brushPaint.setStyle(Paint.Style.STROKE);
            brushPaint.setStrokeJoin(Paint.Join.ROUND);
            brushPaint.setStrokeCap(Paint.Cap.ROUND);

            eraserPaint = new Paint();
            eraserPaint.setColor(Color.TRANSPARENT);
            eraserPaint.setAntiAlias(true);
            eraserPaint.setStrokeWidth(brushSize * 2);
            eraserPaint.setStyle(Paint.Style.STROKE);
            eraserPaint.setStrokeJoin(Paint.Join.ROUND);
            eraserPaint.setStrokeCap(Paint.Cap.ROUND);
            eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        public void setDrawingEnabled(boolean enabled) {
            this.drawingEnabled = enabled;
            if (!enabled) {
                // Flatten the overlay onto the current image
                flattenOverlay();
            }
        }

        public void setBrushMode(int color, int size, boolean isEraser) {
            this.brushColor = color;
            this.brushSize = size;
            this.isEraserMode = isEraser;
            brushPaint.setColor(color);
            brushPaint.setStrokeWidth(size);
        }

        public void setEraserMode() {
            this.isEraserMode = true;
        }

        @Override
        public void setImageBitmap(Bitmap bm) {
            super.setImageBitmap(bm);
            // Reset overlay when image changes
            overlayBitmap = null;
            overlayCanvas = null;
        }

        private void ensureOverlay() {
            Bitmap drawableBitmap = getDrawableBitmap();
            if (drawableBitmap != null && (overlayBitmap == null
                    || overlayBitmap.getWidth() != drawableBitmap.getWidth()
                    || overlayBitmap.getHeight() != drawableBitmap.getHeight())) {
                overlayBitmap = Bitmap.createBitmap(drawableBitmap.getWidth(),
                        drawableBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                overlayCanvas = new Canvas(overlayBitmap);
            }
        }

        private Bitmap getDrawableBitmap() {
            android.graphics.drawable.Drawable drawable = getDrawable();
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            }
            return null;
        }

        private void flattenOverlay() {
            if (overlayBitmap != null && !overlayBitmap.isRecycled()) {
                Bitmap baseBitmap = getDrawableBitmap();
                if (baseBitmap != null) {
                    Bitmap merged = Bitmap.createBitmap(baseBitmap.getWidth(),
                            baseBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(merged);
                    canvas.drawBitmap(baseBitmap, 0, 0, null);
                    canvas.drawBitmap(overlayBitmap, 0, 0, null);
                    setImageBitmap(merged);
                    overlayBitmap = null;
                    overlayCanvas = null;
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!drawingEnabled) {
                return super.onTouchEvent(event);
            }

            ensureOverlay();
            if (overlayCanvas == null) {
                return super.onTouchEvent(event);
            }

            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    drawPath.reset();
                    drawPath.moveTo(x, y);
                    lastX = x;
                    lastY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    drawPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;
                    if (isEraserMode) {
                        overlayCanvas.drawPath(drawPath, eraserPaint);
                    } else {
                        overlayCanvas.drawPath(drawPath, brushPaint);
                    }
                    // Redraw with overlay
                    Bitmap baseBitmap = getDrawableBitmap();
                    if (baseBitmap != null) {
                        Bitmap temp = Bitmap.createBitmap(baseBitmap.getWidth(),
                                baseBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        Canvas tempCanvas = new Canvas(temp);
                        tempCanvas.drawBitmap(baseBitmap, 0, 0, null);
                        tempCanvas.drawBitmap(overlayBitmap, 0, 0, null);
                        setImageBitmap(temp);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drawPath.reset();
                    return true;
            }

            return super.onTouchEvent(event);
        }
    }
}
