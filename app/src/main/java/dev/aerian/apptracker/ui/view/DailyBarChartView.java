package dev.aerian.apptracker.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.DailyTotal;

/** Lightweight rounded bar chart for daily foreground duration. */
public class DailyBarChartView extends View {

    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("M/d", Locale.getDefault());

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<DailyTotal> data = new ArrayList<>();
    private final float density;
    private final float labelHeight;
    private final float topPadding;

    public DailyBarChartView(Context context) {
        this(context, null);
    }

    public DailyBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        labelHeight = 18f * density;
        topPadding = 8f * density;

        barPaint.setColor(ContextCompat.getColor(context, R.color.primary));
        trackPaint.setColor(ContextCompat.getColor(context, R.color.track));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.on_surface_variant));
        labelPaint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        linePaint.setColor(ContextCompat.getColor(context, R.color.outline_variant));
        linePaint.setStrokeWidth(1f * density);
    }

    public void setData(List<DailyTotal> totals) {
        data.clear();
        if (totals != null) {
            data.addAll(totals);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (190f * density);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data.isEmpty()) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("—", getWidth() / 2f, getHeight() / 2f, labelPaint);
            return;
        }

        float left = 2f * density;
        float right = getWidth() - 2f * density;
        float bottom = getHeight() - labelHeight;
        float top = topPadding;
        float chartHeight = Math.max(1f, bottom - top);
        float availableWidth = Math.max(1f, right - left);

        long max = 0L;
        for (DailyTotal item : data) {
            max = Math.max(max, item.durationMs);
        }
        if (max <= 0L) {
            max = 1L;
        }

        canvas.drawLine(left, bottom, right, bottom, linePaint);

        int count = data.size();
        float slot = availableWidth / count;
        float barWidth = Math.max(2f * density, slot * 0.56f);
        float radius = Math.min(barWidth / 2f, 4f * density);

        int labelEvery = count <= 10 ? 1 : Math.max(1, count / 6);
        for (int i = 0; i < count; i++) {
            DailyTotal item = data.get(i);
            float centerX = left + slot * (i + 0.5f);
            float barLeft = centerX - barWidth / 2f;
            float barRight = centerX + barWidth / 2f;
            float ratio = (float) item.durationMs / max;

            RectF track = new RectF(barLeft, top, barRight, bottom);
            canvas.drawRoundRect(track, radius, radius, trackPaint);
            if (ratio > 0f) {
                float barTop = bottom - chartHeight * ratio;
                RectF bar = new RectF(barLeft, barTop, barRight, bottom);
                canvas.drawRoundRect(bar, radius, radius, barPaint);
            }

            if (i % labelEvery == 0 || i == count - 1) {
                String label = DAY_FORMAT.format(LocalDate.ofEpochDay(item.epochDay));
                canvas.drawText(label, centerX, getHeight() - 5f * density, labelPaint);
            }
        }
    }
}
