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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Custom view for PDF annotation rendering and interaction.
 */
public class AnnotationView extends View {

    private Bitmap baseBitmap;
    private Bitmap annotationBitmap;
    private Canvas annotationCanvas;
    private Paint paint;
    private Paint highlightPaint;
    private Paint eraserPaint;
    
    private PdfAnnotationActivity.AnnotationType currentType = 
        PdfAnnotationActivity.AnnotationType.HIGHLIGHT;
    private int currentColor = Color.YELLOW;
    private float strokeWidth = 5f;
    
    private Path currentPath;
    private float startX, startY;
    private boolean isDrawing = false;
    
    private List<PdfAnnotationActivity.Annotation> annotations = new ArrayList<>();
    private Stack<PdfAnnotationActivity.Annotation> undoStack = new Stack<>();
    
    private OnAnnotationListener annotationListener;

    public interface OnAnnotationListener {
        void onAnnotationCreated(PdfAnnotationActivity.Annotation annotation);
        void onAnnotationDeleted(PdfAnnotationActivity.Annotation annotation);
    }

    public AnnotationView(Context context) {
        super(context);
        init();
    }

    public AnnotationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnnotationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(currentColor);

        highlightPaint = new Paint();
        highlightPaint.setAntiAlias(true);
        highlightPaint.setAlpha(128);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(currentColor);

        eraserPaint = new Paint();
        eraserPaint.setAntiAlias(true);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        eraserPaint.setStrokeWidth(20);
        eraserPaint.setStyle(Paint.Style.STROKE);
        eraserPaint.setStrokeJoin(Paint.Join.ROUND);
        eraserPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBaseBitmap(Bitmap bitmap) {
        this.baseBitmap = bitmap;
        if (bitmap != null) {
            annotationBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            annotationCanvas = new Canvas(annotationBitmap);
        }
        invalidate();
    }

    public void setAnnotationType(PdfAnnotationActivity.AnnotationType type) {
        this.currentType = type;
    }

    public void setColor(int color) {
        this.currentColor = color;
        paint.setColor(color);
        highlightPaint.setColor(color);
    }

    public void setOnAnnotationListener(OnAnnotationListener listener) {
        this.annotationListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (baseBitmap != null) {
            // Draw base PDF page
            canvas.drawBitmap(baseBitmap, 0, 0, null);
            
            // Draw annotations
            if (annotationBitmap != null) {
                canvas.drawBitmap(annotationBitmap, 0, 0, null);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = x;
                startY = y;
                isDrawing = true;
                
                if (currentType == PdfAnnotationActivity.AnnotationType.FREEHAND ||
                    currentType == PdfAnnotationActivity.AnnotationType.ERASER) {
                    currentPath = new Path();
                    currentPath.moveTo(x, y);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isDrawing) {
                    if (currentType == PdfAnnotationActivity.AnnotationType.FREEHAND) {
                        currentPath.lineTo(x, y);
                        annotationCanvas.drawPath(currentPath, paint);
                    } else if (currentType == PdfAnnotationActivity.AnnotationType.ERASER) {
                        currentPath.lineTo(x, y);
                        annotationCanvas.drawPath(currentPath, eraserPaint);
                    }
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isDrawing) {
                    float endX = x;
                    float endY = y;
                    
                    drawAnnotation(startX, startY, endX, endY);
                    isDrawing = false;
                    currentPath = null;
                }
                break;
        }

        return true;
    }

    private void drawAnnotation(float startX, float startY, float endX, float endY) {
        PdfAnnotationActivity.Annotation annotation = new PdfAnnotationActivity.Annotation(
            0, currentType, currentColor);
        annotation.setStartX(startX);
        annotation.setStartY(startY);
        annotation.setEndX(endX);
        annotation.setEndY(endY);

        switch (currentType) {
            case HIGHLIGHT:
                drawHighlight(startX, startY, endX, endY);
                break;
            case UNDERLINE:
                drawUnderline(startX, startY, endX, endY);
                break;
            case STRIKE_THROUGH:
                drawStrikeThrough(startX, startY, endX, endY);
                break;
            case NOTE:
                drawNote(startX, startY);
                break;
            case FREEHAND:
                if (currentPath != null) {
                    annotation.setFreehandPath(currentPath);
                }
                break;
        }

        annotations.add(annotation);
        undoStack.push(annotation);
        
        if (annotationListener != null) {
            annotationListener.onAnnotationCreated(annotation);
        }
        
        invalidate();
    }

    private void drawHighlight(float startX, float startY, float endX, float endY) {
        float left = Math.min(startX, endX);
        float top = Math.min(startY, endY);
        float right = Math.max(startX, endX);
        float bottom = Math.max(startY, endY);
        
        RectF rect = new RectF(left, top, right, bottom);
        annotationCanvas.drawRect(rect, highlightPaint);
    }

    private void drawUnderline(float startX, float startY, float endX, float endY) {
        float y = Math.max(startY, endY);
        paint.setStrokeWidth(3f);
        annotationCanvas.drawLine(startX, y, endX, y, paint);
        paint.setStrokeWidth(strokeWidth);
    }

    private void drawStrikeThrough(float startX, float startY, float endX, float endY) {
        float y = (startY + endY) / 2;
        paint.setStrokeWidth(3f);
        annotationCanvas.drawLine(startX, y, endX, y, paint);
        paint.setStrokeWidth(strokeWidth);
    }

    private void drawNote(float x, float y) {
        Paint notePaint = new Paint();
        notePaint.setColor(Color.YELLOW);
        notePaint.setStyle(Paint.Style.FILL);
        
        float size = 40;
        RectF rect = new RectF(x - size/2, y - size/2, x + size/2, y + size/2);
        annotationCanvas.drawRect(rect, notePaint);
        
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        annotationCanvas.drawRect(rect, borderPaint);
    }

    public void addAnnotation(PdfAnnotationActivity.Annotation annotation) {
        annotations.add(annotation);
        // Redraw the annotation
        if (annotation.getType() == PdfAnnotationActivity.AnnotationType.HIGHLIGHT) {
            drawHighlight(annotation.getStartX(), annotation.getStartY(), 
                annotation.getEndX(), annotation.getEndY());
        } else if (annotation.getType() == PdfAnnotationActivity.AnnotationType.UNDERLINE) {
            drawUnderline(annotation.getStartX(), annotation.getStartY(), 
                annotation.getEndX(), annotation.getEndY());
        } else if (annotation.getType() == PdfAnnotationActivity.AnnotationType.STRIKE_THROUGH) {
            drawStrikeThrough(annotation.getStartX(), annotation.getStartY(), 
                annotation.getEndX(), annotation.getEndY());
        }
        invalidate();
    }

    public void clearAnnotations() {
        annotations.clear();
        if (annotationBitmap != null && annotationCanvas != null) {
            annotationCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        invalidate();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            PdfAnnotationActivity.Annotation annotation = undoStack.pop();
            annotations.remove(annotation);
            
            // Clear and redraw remaining annotations
            if (annotationBitmap != null && annotationCanvas != null) {
                annotationCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                for (PdfAnnotationActivity.Annotation a : annotations) {
                    redrawAnnotation(a);
                }
            }
            
            if (annotationListener != null) {
                annotationListener.onAnnotationDeleted(annotation);
            }
            invalidate();
        }
    }

    private void redrawAnnotation(PdfAnnotationActivity.Annotation annotation) {
        switch (annotation.getType()) {
            case HIGHLIGHT:
                drawHighlight(annotation.getStartX(), annotation.getStartY(), 
                    annotation.getEndX(), annotation.getEndY());
                break;
            case UNDERLINE:
                drawUnderline(annotation.getStartX(), annotation.getStartY(), 
                    annotation.getEndX(), annotation.getEndY());
                break;
            case STRIKE_THROUGH:
                drawStrikeThrough(annotation.getStartX(), annotation.getStartY(), 
                    annotation.getEndX(), annotation.getEndY());
                break;
            case NOTE:
                drawNote(annotation.getStartX(), annotation.getStartY());
                break;
        }
    }

    public Bitmap getAnnotatedBitmap() {
        if (baseBitmap == null) return null;
        
        Bitmap result = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), 
            Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(baseBitmap, 0, 0, null);
        if (annotationBitmap != null) {
            canvas.drawBitmap(annotationBitmap, 0, 0, null);
        }
        return result;
    }
}
