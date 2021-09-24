package ru.polyach.openweather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class DayControl extends View {

    private Paint drawPaint;
    private Paint textPaint;
    private RectF rect = new RectF();
    private Rect textRect = new Rect();
    private int sunrise = 0;
    private int sunset = 0;
    private String strSunrise = "XX:XX";
    private String strSunset = "XX:XX";
    private float sunHeight;
    private float density;
    private float scaleDensity;
    private Bitmap bm;

    private Drawable sun;

    public DayControl(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        density = context.getResources().getDisplayMetrics().density;
        scaleDensity = context.getResources().getDisplayMetrics().scaledDensity;

        drawPaint = new Paint();
        drawPaint.setColor(0xFFAAAAAA);             // Цвет краски
        drawPaint.setStrokeWidth(2*density);        // Ширина мазка
        drawPaint.setStrokeCap(Paint.Cap.ROUND);    // Вид кисти
        drawPaint.setStyle(Paint.Style.STROKE);     // Без заливки
        drawPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setTextSize(14*scaleDensity);
        textPaint.setTextLocale(Locale.getDefault());
        textPaint.setAntiAlias(true);


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            sun = context.getDrawable(R.drawable.ic_wb_sunny_black_24dp);
            sunHeight = sun.getIntrinsicHeight();
        }
        else
        {
            bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.sun);
            sunHeight = bm.getHeight();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float canvasHeight = getHeight();
        float canvasWidth = getWidth();

        textPaint.setColor(0xFF666666);
        textPaint.getTextBounds(strSunrise, 0, strSunrise.length(), textRect);
        canvas.drawText(strSunrise, 0, canvasHeight, textPaint);
        textPaint.getTextBounds(strSunset, 0, strSunset.length(), textRect);
        canvas.drawText(strSunset, canvasWidth - textPaint.measureText(strSunset), canvasHeight, textPaint);

        float strSunriseWidth = textPaint.measureText(strSunrise);
        float strSunsetWidth = textPaint.measureText(strSunset);

        float w = (canvasWidth - strSunriseWidth/2 - strSunsetWidth/2)/2;
        float h = canvasHeight - sunHeight/2 - textRect.height() - 5*density;
        float r = (w*w + h*h)/2/h;
        double alpha = Math.acos((r-h)/r)/Math.PI*180;

        rect.left = canvasWidth/2 - r;
        rect.right = canvasWidth/2 + r;
        rect.top = sunHeight/2;
        rect.bottom = sunHeight/2 + 2*r;

        canvas.drawArc(rect, 270f - (float)alpha, 2*(float)alpha, false, drawPaint);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            sun.setBounds((int)(canvasWidth / 2 - sun.getIntrinsicWidth() / 2), 0, (int)(canvasWidth / 2 + sun.getIntrinsicWidth() / 2), sun.getIntrinsicHeight());
            sun.draw(canvas);
        }
        else {
            canvas.drawBitmap(bm, (canvasWidth - bm.getWidth()) / 2, 0, drawPaint);
        }

        String durationTitle = getContext().getString(R.string.day_duration);
        textPaint.getTextBounds(durationTitle, 0, durationTitle.length(), textRect);
        canvas.drawText(durationTitle, canvasWidth/2 - textPaint.measureText(durationTitle)/2,
                canvasHeight - textRect.height() - 15*density, textPaint);

        int duration = sunset - sunrise;
        String strDuration = String.format("%02d:%02d", duration/3600, (duration%3600)/60);
        textPaint.setColor(0xFF333333);
        textPaint.setTextSize(20*scaleDensity);
        canvas.drawText(strDuration, canvasWidth/2 - textPaint.measureText(strDuration)/2, canvasHeight, textPaint);
        textPaint.setTextSize(14*scaleDensity);

//        Path path = new Path();
//        path.addCircle(canvasWidth/2, canvasHeight/2, 50, Path.Direction.CCW);
//        canvas.drawPath(path, drawPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int desiredWidth = (int)(250*density);
        int desiredHeight = (int)(135*density);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(desiredWidth, widthSize);
        } else {
            //Be whatever you want
            width = desiredWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(desiredHeight, heightSize);
        } else {
            //Be whatever you want
            height = desiredHeight;
        }

        //MUST CALL THIS
        setMeasuredDimension(width, height);

//        int height = (int)(getMeasuredHeight()/density);
//        Log.d("OpenWeather", "heightMeasureSpec = " + heightMeasureSpec + " height = " + height + " width = " + (int)(getMeasuredWidth()/density));
//        if (height < 100)
//            height = 100;
//        if (height > 150)
//            height = 150;
        //setMeasuredDimension(getMeasuredWidth(), (int)(height*density));
    }

    public void setSunriseSunset(int sunrise, int sunset)
    {
        this.sunrise = sunrise;
        this.sunset = sunset;
        strSunrise = getTimeString(sunrise);
        strSunset = getTimeString(sunset);
        this.invalidate();
    }

    private String getTimeString(int seconds)
    {
        Time time = new Time(((long)1000)*seconds);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(time);
    }
}