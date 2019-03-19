package ru.polyach.openweather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;


public class WindDirectionControl extends View {

    private Paint drawPaint;
    private Integer direction = null;

    public WindDirectionControl(Context context, AttributeSet attrs) {
        super(context, attrs);

        drawPaint = new Paint();
        drawPaint.setColor(0xFF888888);                                                     // Цвет краски
        drawPaint.setStrokeWidth(2*context.getResources().getDisplayMetrics().density);     // Ширина мазка
        drawPaint.setStrokeCap(Paint.Cap.ROUND);                                            // Вид кисти
        drawPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(direction == null)
            return;

        int canvasHeight = getHeight();
        int canvasWidth = getWidth();

        canvas.rotate((int)direction.doubleValue(), canvasWidth/2, canvasHeight/2);
        canvas.drawLine(canvasWidth/2, 0, canvasWidth/2, canvasHeight, drawPaint);
        canvas.drawLine(canvasWidth/2, canvasHeight, canvasWidth/4, canvasHeight-canvasHeight/3, drawPaint);
        canvas.drawLine(canvasWidth/2, canvasHeight, 3*canvasWidth/4, canvasHeight-canvasHeight/3, drawPaint);
    }

    public void setDirection(Integer drc)
    {
        direction = drc;
        this.invalidate();
    }
}
