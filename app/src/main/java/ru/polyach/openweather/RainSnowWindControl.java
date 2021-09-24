package ru.polyach.openweather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import ru.polyach.openweather.Model.Rain;
import ru.polyach.openweather.Model.Snow;
import ru.polyach.openweather.Model.Wind;

@SuppressWarnings("unused")
public class RainSnowWindControl extends View {

    private final float RAIN_MAX = 25f;     // Уровень выпадения дождя за 3 часа (в мм), примерно соответствующий "красному уровню погодной опасности"
    private final float SNOW_MAX = 2.5f;    // Уровень выпадения снега за 3 часа (в мм талого снега), также примерно соответствующий "красному уровню погодной опасности"
    private final float WIND_MAX = 22f;     // Примерная скорость ветра в м/с для красного уровня опасности

    private Context context;

    private Rain rain;
    private Snow snow;
    private Wind wind;
    private boolean isWind = false;

    private Paint drawPaintFill;
    private Paint drawPaintLine;
    private Paint textPaint;
    private float density;
    //private float scaleDensity;
    private Rect textRect = new Rect();
    private Path path = new Path();

    public RainSnowWindControl(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        this.context = context;

        density = context.getResources().getDisplayMetrics().density;
        //scaleDensity = context.getResources().getDisplayMetrics().scaledDensity;

        drawPaintFill = new Paint();
        drawPaintFill.setStyle(Paint.Style.FILL);

        drawPaintLine = new Paint();
        drawPaintLine.setStrokeCap(Paint.Cap.ROUND);
        drawPaintLine.setStyle(Paint.Style.STROKE);
        drawPaintLine.setStrokeWidth(density);
        drawPaintLine.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(0xFF000000);
        textPaint.setTextSize(density*10);
        textPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // float width = getWidth();
        float height = getHeight();
        float barWidth = 12*density;
        float barHeight = height - 4*density;

        float alpha;                            // Уровень погодной опасности от осадков или ветра: от 0 (зеленый) до 1.0 (красный)
        String levelString;

        if(!isWind) {                               // В режиме отображения осадков
            float levelRain = 0;
            if (rain != null) {
                if (rain.get3h() != null)
                    levelRain = rain.get3h().floatValue();
            }
            float levelSnow = 0;
            if (snow != null) {
                if (snow.get3h() != null)
                    levelSnow = snow.get3h().floatValue();
            }

            float level = levelRain + levelSnow;
            alpha = levelRain / RAIN_MAX + levelSnow/SNOW_MAX;

            if (level < 9.95)
                levelString = String.format("%.1f %s", level, context.getString(R.string.rain_snow_units));
            else
                levelString = String.format("%.0f %s", level, context.getString(R.string.rain_snow_units));
        }
        else                                        // В режиме отображения ветра
        {
            int windSpeed = 0;
            if (wind != null)
                if(wind.getSpeed() != null)
                    windSpeed = wind.getSpeed();

            alpha = windSpeed / WIND_MAX;
            levelString = String.format("%d %s", windSpeed, context.getString(R.string.wind_units));
        }

        if (alpha > 1f)
            alpha = 1f;

        int color = Utilities.getWeatherColor(alpha);

        drawPaintFill.setColor(color);
        canvas.drawRect(0, height - barHeight* alpha, barWidth, height, drawPaintFill);   // Закрашенная часть столбика

        drawPaintLine.setColor(0xFF000000);
        canvas.drawRect(0, height - barHeight, barWidth, height, drawPaintLine);                // Контур столбика давления

        path.reset();                                                                                     // Указатель уровня
        path.moveTo(barWidth + density, height - barHeight* alpha);
        path.lineTo(barWidth + density + 6*density, height - barHeight* alpha - 4*density);
        path.lineTo(barWidth + density + 6*density, height - barHeight* alpha + 4*density);
        path.close();
        canvas.drawPath(path, drawPaintFill);

        textPaint.setColor(color);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.getTextBounds(levelString, 0, levelString.length(), textRect);
        float stringHeightPos = height - barHeight* alpha + 4*density;
        if(stringHeightPos > height)
            stringHeightPos = height;
        canvas.drawText(levelString, barWidth + 4*density + 6*density, stringHeightPos, textPaint);
        textPaint.setColor(0xFF888888);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(1);
        canvas.drawText(levelString, barWidth + 4*density + 6*density, stringHeightPos, textPaint);
    }

    public void setRainSnow(Rain rain, Snow snow)
    {
        this.rain = rain;
        this.snow = snow;
        isWind = false;
        this.invalidate();
    }

    public void setWind(Wind wind) {
        this.wind = wind;
        isWind = true;
    }
}
