package ru.polyach.openweather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import ru.polyach.openweather.Model.List;

public class WeatherAdapter extends RecyclerView.Adapter<WeatherAdapter.RecyclerViewItemWeather> {

    private Context context;
    private java.util.List<List> list;
    private String cashDir;
    private SimpleDateFormat sdf = new SimpleDateFormat("E  HH:mm");
    private SimpleDateFormat sdfForLocalDate = new SimpleDateFormat("yyyy-MM-dd");

    class RecyclerViewItemWeather extends RecyclerView.ViewHolder {

        View itemView;
        ImageView image;
        TextView textTemperature;
        TextView textDayTime;
        PressureControl pressure;
        TextView rainTitle;
        RainSnowWindControl rainSnowWindControl;


        RecyclerViewItemWeather(View itemView) {
            super(itemView);
            this.itemView = itemView;
            image = itemView.findViewById(R.id.weather_icon);
            textTemperature = itemView.findViewById(R.id.temperature_forecast);
            textDayTime = itemView.findViewById(R.id.date_forecast);
            pressure = itemView.findViewById(R.id.pressureControl);
            rainTitle = itemView.findViewById(R.id.rain_title_forecast);
            rainSnowWindControl = itemView.findViewById(R.id.rain_snow_forecast);
        }
    }

    public WeatherAdapter(Context cnt, java.util.List<List> list, String dir) {
        context = cnt;
        this.list = list;
        cashDir = dir;
    }

    @NonNull
    @Override
    public RecyclerViewItemWeather onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_weather_forecast, parent, false);
        return new RecyclerViewItemWeather(view);
    }

    private Set<String> namesSet = new TreeSet<>();

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewItemWeather holder, int position) {
        String imageName = list.get(position).getWeather().get(0).getIcon();
        File file = new File(cashDir, imageName);

        if (file.exists()) {                                                            // Если файл существует, выводим в ImageView
            Bitmap bm = BitmapFactory.decodeFile(file.toString());
            holder.image.setImageBitmap(bm);
        } else                                                                          // Если нет, скачиваем в IntentService и выводим
        {
            holder.image.setImageBitmap(null);
            if(!namesSet.contains(imageName)) {
                ((MainActivity)context).downloadFile(list.get(position).getWeather().get(0).getIcon());
                namesSet.add(imageName);
            }
        }

        double temp = list.get(position).getMain().getTemp();
        if (Math.abs(temp) < 9.95) {
            holder.textTemperature.setText(String.format("%.1f", temp));
        } else {
            holder.textTemperature.setText(String.format("%.0f", temp));
        }

        Date date = new Date(((long) 1000) * list.get(position).getDt());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {  // Для более современных устройств деление дней на четные и нечетные исчисляем с 1970 г.
            String str = sdfForLocalDate.format(date);
            LocalDate localDate = LocalDate.parse(str);
            if (localDate.toEpochDay() % 2 == 1) {
                holder.itemView.setBackgroundColor(0xFFDDDDDD);
                holder.textDayTime.setTextColor(0xFFFFFFFF);
            }
            else {
                holder.itemView.setBackgroundColor(0xFFFFFFFF);
                holder.textDayTime.setTextColor(0xFF000000);
            }
        } else {                                                                    // Для древних устройств деление дней на четные и нечетные исчисляем с нового года (в этом случае
            {                                                                       // будет два подряд нечетных дня в невисокосный год - 31 декабря и 1 января)
                if (date.getDate() % 2 == 1) {
                    holder.itemView.setBackgroundColor(0xFFDDDDDD);
                    holder.textDayTime.setTextColor(0xFFFFFFFF);
                }
                else {
                    holder.itemView.setBackgroundColor(0xFFFFFFFF);
                    holder.textDayTime.setTextColor(0xFF000000);
                }
            }
        }
        holder.textDayTime.setText(sdf.format(((long) 1000) * list.get(position).getDt()).toUpperCase());

        holder.pressure.setPressure(list.get(position).getMain().getSeaLevel());

        String rainTitle;
//        if(list.get(position).getMain().getTemp() < 0)
//            rainTitle = context.getString(R.string.snow);

        Double rain = null;
        Double snow = null;
        if(list.get(position).getRain() != null) {
            rain = list.get(position).getRain().get3h();
//            rainTitle = context.getString(R.string.rain);
        }
        if(list.get(position).getSnow() != null) {
            snow = list.get(position).getSnow().get3h();
//            rainTitle = context.getString(R.string.snow);
        }
        if (rain == null)
            rain = 0.0;
        if (snow == null)
            snow = 0.0;
        if(rain + snow == 0) {                              // Если нет ни дождя, ни снега выводим скорость ветра
            rainTitle = context.getString(R.string.wind);
            holder.rainSnowWindControl.setWind(list.get(position).getWind());
        }
        else                                                // В противном случае - уровень осадков
        {
            rainTitle = rain > snow ? context.getString(R.string.rain) : context.getString(R.string.snow);
            holder.rainSnowWindControl.setRainSnow(list.get(position).getRain(), list.get(position).getSnow());
        }

        holder.rainTitle.setText(rainTitle);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
