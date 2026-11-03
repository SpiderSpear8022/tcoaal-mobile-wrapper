package com.example.rpgmakermobileshell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

public final class VirtualControlsView extends View {
    public interface Listener {
        void onControlsChanged(int controlMask);

        void onLayoutChanged(Layout layout);
    }

    public static final class Layout {
        private static final float[] DEFAULT_BUTTON_X = {0.86f, 0.94f, 0.86f, 0.94f, 0.94f};
        private static final float[] DEFAULT_BUTTON_Y = {0.50f, 0.50f, 0.68f, 0.68f, 0.86f};

        public float dpadX = 0.12f;
        public float dpadY = 0.78f;
        public float scale = 1.0f;
        public float opacity = 0.62f;
        public final float[] buttonX = DEFAULT_BUTTON_X.clone();
        public final float[] buttonY = DEFAULT_BUTTON_Y.clone();

        public void reset() {
            dpadX = 0.12f;
            dpadY = 0.78f;
            scale = 1.0f;
            opacity = 0.62f;
            System.arraycopy(DEFAULT_BUTTON_X, 0, buttonX, 0, buttonX.length);
            System.arraycopy(DEFAULT_BUTTON_Y, 0, buttonY, 0, buttonY.length);
        }
    }

    private static final int LEFT = 1;
    private static final int RIGHT = 1 << 1;
    private static final int UP = 1 << 2;
    private static final int DOWN = 1 << 3;
    private static final int OK = 1 << 4;
    private static final int CANCEL = 1 << 5;
    private static final int DASH = 1 << 6;
    private static final int PAGE_UP = 1 << 7;
    private static final int PAGE_DOWN = 1 << 8;
    private static final int DPAD = -1;

    private final Listener listener;
    private final Layout layout;
    private final boolean editMode;
    private final SparseArray<Integer> pointerControls = new SparseArray<>();
    private final SparseArray<Integer> layoutPointers = new SparseArray<>();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float buttonRadius;
    private float dpadRadius;
    private final float margin;
    private int dpadPointer = -1;
    private int dpadMask;
    private int controlMask;
    private boolean capturedTouch;
    private float dragOffsetX;
    private float dragOffsetY;

    public VirtualControlsView(Context context, Listener listener, Layout layout, boolean editMode) {
        super(context);
        this.listener = listener;
        this.layout = layout;
        this.editMode = editMode;
        margin = dp(24);
        updateScale();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextSize(dp(14));
        setClickable(true);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawDpad(canvas);
        drawButton(canvas, 0, "Q", PAGE_UP);
        drawButton(canvas, 1, "W", PAGE_DOWN);
        drawButton(canvas, 2, "RUN", DASH);
        drawButton(canvas, 3, "BACK", CANCEL);
        drawButton(canvas, 4, "OK", OK);
    }

    private void drawDpad(Canvas canvas) {
        float centerX = dpadCenterX();
        float centerY = dpadCenterY();
        float arm = dpadRadius * 0.42f;
        float extent = dpadRadius;
        drawDpadArm(canvas, centerX - arm, centerY - extent,
                centerX + arm, centerY - arm * 0.55f, UP);
        drawDpadArm(canvas, centerX - arm, centerY + arm * 0.55f,
                centerX + arm, centerY + extent, DOWN);
        drawDpadArm(canvas, centerX - extent, centerY - arm,
                centerX - arm * 0.55f, centerY + arm, LEFT);
        drawDpadArm(canvas, centerX + arm * 0.55f, centerY - arm,
                centerX + extent, centerY + arm, RIGHT);

        fillPaint.setColor(withOpacity(155, Color.rgb(24, 30, 43)));
        canvas.drawRoundRect(centerX - arm, centerY - arm,
                centerX + arm, centerY + arm, dp(6), dp(6), fillPaint);
        outlinePaint.setColor(withOpacity(205, Color.rgb(235, 242, 255)));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(2));
        canvas.drawRoundRect(centerX - arm, centerY - arm,
                centerX + arm, centerY + arm, dp(6), dp(6), outlinePaint);
        outlinePaint.setStyle(Paint.Style.FILL);

        textPaint.setTextSize(dp(17));
        drawArrow(canvas, centerX, centerY - dpadRadius * 0.69f, UP);
        drawArrow(canvas, centerX, centerY + dpadRadius * 0.69f, DOWN);
        drawArrow(canvas, centerX - dpadRadius * 0.69f, centerY, LEFT);
        drawArrow(canvas, centerX + dpadRadius * 0.69f, centerY, RIGHT);
        if (editMode) {
            textPaint.setTextSize(dp(9));
            float textY = centerY - (textPaint.ascent() + textPaint.descent()) / 2;
            canvas.drawText("DRAG", centerX, textY, textPaint);
        }
    }

    private void drawDpadArm(Canvas canvas, float left, float top, float right, float bottom,
                             int direction) {
        boolean pressed = (controlMask & direction) != 0;
        int color = pressed ? Color.rgb(38, 150, 233) : Color.rgb(24, 30, 43);
        fillPaint.setColor(withOpacity(pressed ? 218 : 145, color));
        canvas.drawRoundRect(left, top, right, bottom, dp(7), dp(7), fillPaint);
        outlinePaint.setColor(withOpacity(205, Color.rgb(235, 242, 255)));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(2));
        canvas.drawRoundRect(left, top, right, bottom, dp(7), dp(7), outlinePaint);
        outlinePaint.setStyle(Paint.Style.FILL);
    }

    private void drawArrow(Canvas canvas, float centerX, float centerY, int direction) {
        Path arrow = new Path();
        float halfWidth = dp(7);
        float halfHeight = dp(5);
        if (direction == UP) {
            arrow.moveTo(centerX, centerY - halfHeight);
            arrow.lineTo(centerX - halfWidth, centerY + halfHeight);
            arrow.lineTo(centerX + halfWidth, centerY + halfHeight);
        } else if (direction == DOWN) {
            arrow.moveTo(centerX, centerY + halfHeight);
            arrow.lineTo(centerX - halfWidth, centerY - halfHeight);
            arrow.lineTo(centerX + halfWidth, centerY - halfHeight);
        } else if (direction == LEFT) {
            arrow.moveTo(centerX - halfHeight, centerY);
            arrow.lineTo(centerX + halfHeight, centerY - halfWidth);
            arrow.lineTo(centerX + halfHeight, centerY + halfWidth);
        } else {
            arrow.moveTo(centerX + halfHeight, centerY);
            arrow.lineTo(centerX - halfHeight, centerY - halfWidth);
            arrow.lineTo(centerX - halfHeight, centerY + halfWidth);
        }
        arrow.close();
        fillPaint.setColor(withOpacity(220, Color.WHITE));
        canvas.drawPath(arrow, fillPaint);
    }

    private void drawButton(Canvas canvas, int index, String label, int mask) {
        float centerX = buttonCenterX(index);
        float centerY = buttonCenterY(index);
        boolean pressed = (controlMask & mask) != 0;
        fillPaint.setColor(withOpacity(pressed ? 218 : 128,
                pressed ? Color.rgb(38, 150, 233) : Color.rgb(24, 30, 43)));
        canvas.drawCircle(centerX, centerY, buttonRadius, fillPaint);
        outlinePaint.setColor(withOpacity(205, Color.rgb(235, 242, 255)));
        outlinePaint.setStrokeWidth(dp(2));
        canvas.drawCircle(centerX, centerY, buttonRadius, outlinePaint);
        textPaint.setTextSize(dp(label.length() > 4 ? 11 : 14));
        float textY = centerY - (textPaint.ascent() + textPaint.descent()) / 2;
        canvas.drawText(label, centerX, textY, textPaint);
        if (editMode) {
            textPaint.setTextSize(dp(10));
            canvas.drawText("DRAG", centerX, centerY + buttonRadius * 0.62f, textPaint);
        }
    }

    private int withOpacity(int alpha, int color) {
        int adjustedAlpha = Math.round(alpha * layout.opacity);
        return Color.argb(adjustedAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editMode) {
            return handleLayoutTouch(event);
        }
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                int control = controlAt(event.getX(pointerIndex), event.getY(pointerIndex));
                if (control == 0) {
                    return capturedTouch;
                }
                if (control == DPAD && dpadPointer >= 0) {
                    return capturedTouch;
                }
                capturedTouch = true;
                pointerControls.put(pointerId, control);
                if (control == DPAD) {
                    dpadPointer = pointerId;
                    updateDpad(event.getX(pointerIndex), event.getY(pointerIndex));
                }
                updateControls();
                return true;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int movingPointerId = event.getPointerId(i);
                    Integer movingControl = pointerControls.get(movingPointerId);
                    if (movingControl != null && movingControl == DPAD) {
                        updateDpad(event.getX(i), event.getY(i));
                    }
                }
                updateControls();
                return capturedTouch;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (action == MotionEvent.ACTION_CANCEL) {
                    pointerControls.clear();
                    dpadPointer = -1;
                    dpadMask = 0;
                    capturedTouch = false;
                } else {
                    Integer releasedControl = pointerControls.get(pointerId);
                    if (releasedControl != null && releasedControl == DPAD) {
                        dpadPointer = -1;
                        dpadMask = 0;
                    }
                    pointerControls.remove(pointerId);
                    if (action == MotionEvent.ACTION_UP) {
                        capturedTouch = false;
                    }
                }
                updateControls();
                return true;

            default:
                return capturedTouch;
        }
    }

    private int controlAt(float x, float y) {
        float dx = Math.abs(x - dpadCenterX());
        float dy = Math.abs(y - dpadCenterY());
        float armHalfWidth = dpadRadius * 0.42f;
        if ((dx <= armHalfWidth && dy <= dpadRadius)
                || (dy <= armHalfWidth && dx <= dpadRadius)) {
            return DPAD;
        }
        int[] buttonMasks = {PAGE_UP, PAGE_DOWN, DASH, CANCEL, OK};
        for (int i = 0; i < buttonMasks.length; i++) {
            float hitRadius = buttonRadius * 1.12f;
            if (distanceSquared(x, y, buttonCenterX(i), buttonCenterY(i))
                    <= hitRadius * hitRadius) {
                return buttonMasks[i];
            }
        }
        return 0;
    }

    private boolean handleLayoutTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (layoutPointers.size() > 0) {
                    return true;
                }
                int item = layoutItemAt(event.getX(pointerIndex), event.getY(pointerIndex));
                if (item == 0) {
                    return false;
                }
                layoutPointers.put(pointerId, item);
                dragOffsetX = item == DPAD
                        ? dpadCenterX() - event.getX(pointerIndex)
                        : buttonCenterX(item - 1) - event.getX(pointerIndex);
                dragOffsetY = item == DPAD
                        ? dpadCenterY() - event.getY(pointerIndex)
                        : buttonCenterY(item - 1) - event.getY(pointerIndex);
                return true;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int movingItem = layoutPointers.get(event.getPointerId(i), 0);
                    if (movingItem != 0) {
                        moveLayoutItem(movingItem, event.getX(i) + dragOffsetX,
                                event.getY(i) + dragOffsetY);
                    }
                }
                invalidate();
                return layoutPointers.size() > 0;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                layoutPointers.remove(pointerId);
                if (layoutPointers.size() == 0) {
                    listener.onLayoutChanged(layout);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                layoutPointers.clear();
                listener.onLayoutChanged(layout);
                return true;

            default:
                return layoutPointers.size() > 0;
        }
    }

    private int layoutItemAt(float x, float y) {
        float hitRadius = Math.max(dpadRadius, buttonRadius) * 1.4f;
        if (distanceSquared(x, y, dpadCenterX(), dpadCenterY())
                <= hitRadius * hitRadius) {
            return DPAD;
        }
        for (int i = 0; i < 5; i++) {
            if (distanceSquared(x, y, buttonCenterX(i), buttonCenterY(i))
                    <= hitRadius * hitRadius) {
                return i + 1;
            }
        }
        return 0;
    }

    private void moveLayoutItem(int item, float x, float y) {
        if (item == DPAD) {
            layout.dpadX = clampFraction(x / Math.max(1, getWidth()));
            layout.dpadY = clampFraction(y / Math.max(1, getHeight()));
        } else {
            int index = item - 1;
            layout.buttonX[index] = clampFraction(x / Math.max(1, getWidth()));
            layout.buttonY[index] = clampFraction(y / Math.max(1, getHeight()));
        }
    }

    private float clampFraction(float value) {
        return Math.max(0.04f, Math.min(0.96f, value));
    }

    private void updateDpad(float x, float y) {
        float dx = x - dpadCenterX();
        float dy = y - dpadCenterY();
        dpadMask = 0;
        if (Math.max(Math.abs(dx), Math.abs(dy)) > dpadRadius * 0.22f) {
            if (Math.abs(dx) > Math.abs(dy)) {
                dpadMask = dx < 0 ? LEFT : RIGHT;
            } else {
                dpadMask = dy < 0 ? UP : DOWN;
            }
        }
        invalidate();
    }

    private void updateControls() {
        int newMask = dpadMask;
        for (int i = 0; i < pointerControls.size(); i++) {
            int pointerId = pointerControls.keyAt(i);
            int control = pointerControls.valueAt(i);
            if (pointerId != dpadPointer) {
                newMask |= control;
            }
        }
        if (newMask != controlMask) {
            controlMask = newMask;
            listener.onControlsChanged(controlMask);
        }
        invalidate();
    }

    private float dpadCenterX() {
        return getWidth() * layout.dpadX;
    }

    private float dpadCenterY() {
        return getHeight() * layout.dpadY;
    }

    private float buttonCenterX(int index) {
        return getWidth() * layout.buttonX[index];
    }

    private float buttonCenterY(int index) {
        return getHeight() * layout.buttonY[index];
    }

    public void setScale(float scale) {
        layout.scale = Math.max(0.65f, Math.min(1.45f, scale));
        updateScale();
        invalidate();
    }

    public void setOpacity(float opacity) {
        layout.opacity = Math.max(0.25f, Math.min(1.0f, opacity));
        invalidate();
    }

    public void resetLayout() {
        layout.reset();
        updateScale();
        invalidate();
        listener.onLayoutChanged(layout);
    }

    private void updateScale() {
        layout.scale = Math.max(0.65f, Math.min(1.45f, layout.scale));
        layout.opacity = Math.max(0.25f, Math.min(1.0f, layout.opacity));
        buttonRadius = dp(32) * layout.scale;
        dpadRadius = dp(66) * layout.scale;
    }

    private float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
