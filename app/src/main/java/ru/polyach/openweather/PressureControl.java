package ru.polyach.openweather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class PressureControl extends View {

    private final float PRESSURE_NORMAL = 1013.25f;
    private final float PRESSURE_HIGH = PRESSURE_NORMAL + 22;
    private final float PRESSURE_LOW = PRESSURE_NORMAL - 22;

    private float pressure = PRESSURE_NORMAL;
    private Paint drawPaintFill;
    private Paint drawPaintLine;
    private Paint textPaint;
    private float density;
    //private float scaleDensity;
    private Rect textRect = new Rect();
    private Path path = new Path();

    public PressureControl(Context context, AttributeSet attrs) {
        super(context, attrs);

        density = context.getResources().getDisplayMetrics().density;
        //scaleDensity = context.getResources().getDisplayMetrics().scaledDensity;

        drawPaintFill = new Paint();
        drawPaintFill.setStyle(Paint.Style.FILL);

        drawPaintLine = new Paint();
        drawPaintLine.setStrokeCap(Paint.Cap.ROUND);
        drawPaintLine.setStyle(Paint.Style.STROKE);
        drawPaintLine.setStrokeWidth(2);
        drawPaintLine.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setTextSize(density*10);
        textPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float barWidth = 12*density;
        float barHeight = height - 4*density;

        float alpha;
        float pressureHeight;
        if(pressure > PRESSURE_NORMAL) {
            alpha = (pressure - PRESSURE_NORMAL) / (PRESSURE_HIGH - PRESSURE_NORMAL);
            if (alpha > 1f)
                alpha = 1f;
            pressureHeight = (barHeight / 2 +  alpha* barHeight / 2);
        }
        else {
            alpha = (pressure - PRESSURE_NORMAL) / (PRESSURE_NORMAL - PRESSURE_LOW);
            if (alpha < -0.85f)
                alpha = -0.85f;
            pressureHeight = (barHeight / 2 + alpha * barHeight / 2);
        }

//        int red = 0x00 + (int)(Math.abs(alpha)*(0xff - 0x00));
//        int green = 0x00 + (int)(Math.abs(alpha)*(0x00 - 0x00));
//        int blue = 0xff + (int)(Math.abs(alpha)*(0x00 - 0xff));

//        int red = 0x88 + (int)(Math.abs(alpha)*(0xff - 0x88));
//        int green = 0xff + (int)(Math.abs(alpha)*(0x00 - 0xff));
//        int blue = 0x88 + (int)(Math.abs(alpha)*(0x00 - 0x88));

        int color = Utilities.getWeatherColor(Math.abs(alpha));

        drawPaintFill.setColor(color);
        canvas.drawRect(width - barWidth, height - pressureHeight, width, height, drawPaintFill);   // Закрашенная часть столбика

        drawPaintLine.setColor(0xFF000000);
        canvas.drawRect(width - barWidth, height - barHeight, width, height, drawPaintLine);        // Контур столбика давления

        if(alpha>0) {
            drawPaintLine.setColor(0xFFFFFFFF);
        }
        canvas.drawLine(width - barWidth, height - barHeight/2, width, height - barHeight/2, drawPaintLine);    // Отметка нормального давления

        path.reset();
        path.moveTo(width - barWidth - density, height - pressureHeight);
        path.lineTo(width - barWidth - density - 6*density, height - pressureHeight - 4*density);
        path.lineTo(width - barWidth - density - 6*density, height - pressureHeight + 4*density);
        path.close();
        canvas.drawPath(path, drawPaintFill);

        String pressureLevel = getContext().getString(R.string.normal);
        if(alpha > 0.5) {
            pressureLevel = getContext().getString(R.string.high);
        }
        if(alpha < -0.5) {
            pressureLevel = getContext().getString(R.string.low);
        }

        textPaint.setColor(color);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.getTextBounds(pressureLevel, 0, pressureLevel.length(), textRect);
        canvas.drawText(pressureLevel, width - barWidth - density - 8*density - textPaint.measureText(pressureLevel), height - pressureHeight + 4*density, textPaint);
        textPaint.setColor(0xFF888888);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(1);
        canvas.drawText(pressureLevel, width - barWidth - density - 8*density - textPaint.measureText(pressureLevel), height - pressureHeight + 4*density, textPaint);

    }

    public void setPressure(double pressure)
    {
        this.pressure = (float)pressure;
        this.invalidate();
    }
}
