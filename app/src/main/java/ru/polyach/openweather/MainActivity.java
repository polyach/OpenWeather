package ru.polyach.openweather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import ru.polyach.openweather.Model.Rain;
import ru.polyach.openweather.Model.Snow;
import ru.polyach.openweather.Model.Weather1;
import ru.polyach.openweather.Model.Weather5;


public class MainActivity extends AppCompatActivity
{
    public static final String TAG = "OpenWeather";

    private static final String URI_WEATHER1 = "http://api.openweathermap.org/data/2.5/weather";    // http://api.openweathermap.org/data/2.5/weather?lat=55.632390&lon=37.523040&APPID=29c0f8d67faaa78d8b6a03247ce7bae9&units=metric&lang=ru
    private static final String URI_WEATHER5 = "http://api.openweathermap.org/data/2.5/forecast";   // http://api.openweathermap.org/data/2.5/forecast?lat=55.632498&lon=37.523183&APPID=29c0f8d67faaa78d8b6a03247ce7bae9&units=metric&lang=ru

    private static final long MIN_RENEW_INTERVAL = 60*1000;         // Интервал, по истечении которого инициируются запросы в сервис после действий пользователя (например при повороте) (в миллисекундах)
    private static final long MAX_RELOAD_INTERVAL = 10*60*1000;     // Интервал, по истечению которого инициируются запросы в сервис в активности без действий пользователя (в миллисекундах)

    private Weather1 weather1;                                      // Объект, получаемый из запроса погоды в точке на текущий момент
    private Weather5 weather5;                                      // Объект, получаемый из запроса прогноза погоды в точке на пять дней
    private final String w1 = "weather1";                           // Название Json-файла (в Cache-папке) с погодой на текущий момент
    private final String w5 = "weather5";                           // Название Json-файла (в Cache-папке) с прогнозом на пять дней
    private final String cityList = "city.list.json.gz";            // Список городов с ID и координатами, поддерживаемый сервисом
//    private final String cityList = "city.list.min.json.gz";
//    private final String cityList = "weather_14.json.gz";

    private boolean isByLocation = true;                            // Режим работы: погода в текущей точке / погода в указанной точке
    private double currentLat = 0;                                  // Последние полученные координаты в режиме автолокации (isByLocation = true) для совершения запросов;
    private double currentLon = 0;                                  // сохраняются в SharedPreferences
    private String currentCountry =                                 // Двухбуквенное обозначение текущей страны
            Locale.getDefault().getCountry();
    private String currentTownId;                                   // ID города в ручном режиме позиционирования (isByLocation = false), используется для запросов в этом режиме
    private String currentTown;                                     // Название текущего города (в любом режиме)

    public static final String APP_PREFERENCES =                  "openweather_params";              // Ключ для SharedPreferences
    private static final String APP_PREFERENCES_TIME_WEATHER =    "openweather_weather_time";        // Ключ для времени последнего получения данных о погоде из сервиса
    private static final String APP_PREFERENCES_TIME_FORCAST =    "openweather_forcast_time";        // Ключ для времени последнего получения данных о прогнозе из сервиса
    private static final String APP_PREFERENCES_PENDING_FILES =   "openweather_pending_files";       // Ключ для сета из имен незагруженных файлов
    public static final String APP_PREFERENCES_ALL_COUNTRIES =    "openweather_all_countries";       // Ключ для списка всех стран в базе данных
    private static final String APP_PREFERENCES_LOCATION_FLAG =   "openweather_location_flag";       // Ключ для переменной переключения режима автолокация / указание места вручную
    private static final String APP_PREFERENCES_CURRENT_TOWN_ID = "openweather_current_town_id";     // Ключ для currentTownId
    private static final String APP_PREFERENCES_LATITUDE =        "openweather_latitude";            // Ключи для сохранения координат
    private static final String APP_PREFERENCES_LONGITUDE =       "openweather_longitude";
    public static final String APP_PREFERENCES_DATABASE =         "openweather_database";            // Ключи для сохранения статуса создания базы данных
    public static final String APP_PREFERENCES_DB_CREATING =      "openweather_database_db_creating";// Значение параметра на этапе создания базы данных
    public static final String APP_PREFERENCES_DB_DONE =          "openweather_database_db_done";    // Значение параметра на этапе после создания базы данных
    private Set<String> pendingFiles = new HashSet<>();                                              // Set с именами файлов в очереди на загрузку (незагруженных по причине отсутствия интернет-соединения)

    private static final int INTENT_FIND_SETTLEMENT = 1000;                                          // Ключ для вызова FindSettlementActivity
    public static final String INTENT_CURRENT_COUNTRY = "openweather_current_country";               // Ключ для передачи текущей страны в FindSettlementActivity
    public static final String INTENT_CURRENT_TOWN = "openweather_current_town";                     // Ключ для передачи текущего города в FindSettlementActivity
    public static final String INTENT_CURRENT_TOWN_ID = "openweather_current_town_id";               // Ключ для передачи выбранного id города из FindSettlementActivity

    private FusedLocationProviderClient mClient;                    // Объекты для определения местоположения через сервис Google Play
    private SettingsClient mSettingsClient;
    private LocationCallback mLocationCallback;
    private LocationRequest locationRequest;
    private int locationPriority = POWER_ACCURACY;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_CHECK_SETTINGS = 101;
    private static final int POWER_ACCURACY = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
    private static final int HIGH_ACCURACY = LocationRequest.PRIORITY_HIGH_ACCURACY;

    private ObjectMapper mapper;                                    // Объект Jackson для парсинга Json-файлов

    private ImageView imageView;                                    // Поля представления
    private TextView descriptionText;
    private TextView precipitationText;

    private TextView cityText;
    private TextView temperatureText;
    private TextView temperatureTime;
    private ImageButton replayButton;

    private TextView pressureText;
    private TextView pressureTitle;
    private TextView pressureUnits;

    private TextView humidityText;
    private TextView humidityTitle;

    private TextView cloudsText;
    private TextView cloudsTitle;

    private TextView windText;
    private TextView windTitle;
    private WindDirectionControl windDirection;
    private DayControl dayControl;

    private RecyclerView weatherRecyclerView;
    private LinearLayoutManager weatherLayoutManager;
    private WeatherAdapter weatherAdapter;

    private FileDownloadingReceiver fileDownloadingReceiver;        // Внутренний BroadcastReceiver для приема сообщений о завершении закачки файлов (всех: картинок, Json-файлов и архива городов)

    private NetworkMonitor networkMonitor;                          // BroadcastReceiver для получения апдейтов о состоянии Интернет-соединения
    private boolean isInternetAvailable = false;                    // Состояние доступности интернета, обновляется NetworkMonitorом

    private Handler mainHandler;
    private Menu mainMenu;

    private class FileDownloadingReceiver extends BroadcastReceiver {   // Ресивер для приемки сообщений о завершении загрузки файлов и создании базы данных городов

        @Override
        public void onReceive(Context context, Intent intent) {

            Log.d (TAG, "FileDownloadingReceiver: onReceive - " + intent.getStringExtra(FileDownloadService.FILE_DOWNLOADED) + " - " + intent.getBooleanExtra(FileDownloadService.FILE_DOWNLOAD_RESULT, false));

            if (intent.hasExtra(FileDownloadService.FILE_DOWNLOADED)) {
                String fileName = intent.getStringExtra(FileDownloadService.FILE_DOWNLOADED);
                boolean result = intent.getBooleanExtra(FileDownloadService.FILE_DOWNLOAD_RESULT, false);

                if(fileName.equals("database"))
                {
                    if (result)
                    {
                        Toast.makeText(context, getString(R.string.database_created) + ": " + intent.getIntExtra(FileDownloadService.FILE_DOWNLOAD_TOWNS_NUMBER, 0) + " " +
                                getString(R.string.settlements_found1), Toast.LENGTH_LONG).show();
                        if (mainMenu != null)
                            mainMenu.findItem(R.id.find_item).setVisible(true);
                    }
                    else
                    {
                        //Toast.makeText(context, R.string.error_creating_database, Toast.LENGTH_LONG).show();
                    }
                    countingIdlingResourceChange(false);
                    return;
                }

                if (fileName.contains(cityList)) {      // Архивный файл со списком городов

                    File file = new File(fileName);

                    if (!result) {                      // Если загрузка завершилась неудачно (скорее, из-за нестабильного интернета), удаляем его, ставим снова в очередь и выходим
                        if (file.exists())
                            file.delete();
                        downloadFile(cityList);
                    } else {                            // При удачном завершении скачивания, переименовываем его и запускаем разбор в объекте-одиночке, содержащем AsyncTask разбора
                        File newFile = new File(fileName + "_");    // Для гарантии, что он правильно скачался с сервиса
                        file.renameTo(newFile);
                        Toast.makeText(context, R.string.start_creating_database, Toast.LENGTH_LONG).show();
                        countingIdlingResourceChange(true);
                        FillingDatabase.start(getApplicationContext(), newFile.toString());
                    }
                    countingIdlingResourceChange(false);
                    return;
                }

                if (fileName.contains(w1)) {
                    if (!result) {                              // Если загрузка завершилась неудачно, ставим файл снова в очередь и выходим
                        downloadFile(w1);
                    } else {                                            // Если файл получен, запоминаем время получения для отображения в представлении
                        SharedPreferences.Editor editor = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit();
                        editor.putLong(APP_PREFERENCES_TIME_WEATHER, System.currentTimeMillis());
                        editor.apply();
                        File file = new File(fileName);                 // и переименовываем его, чтобы использовать в дальнейших процедурах, иначе он будет удален при неудачном скачивании
                        File file_ = new File(fileName + "_");
                        file.renameTo(file_);
                        readWeather();
                        redrawWeather();
                    }
                    countingIdlingResourceChange(false);
                    return;
                }

                if (fileName.contains(w5)) {
                    if (!result) {                              // Если загрузка завершилась неудачно, ставим файл снова в очередь и выходим
                        downloadFile(w5);
                    } else {                                            // Если файл получен, запоминаем время получения
                        SharedPreferences.Editor editor = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit();
                        editor.putLong(APP_PREFERENCES_TIME_FORCAST, System.currentTimeMillis());
                        editor.apply();
                        File file = new File(fileName);                 // Переименовываем файл, чтобы использовать в дальнейшем, иначе он будет удален при очередном неудачном скачивании
                        File file_ = new File(fileName + "_");
                        file.renameTo(file_);
                        readForcast();
                        redrawForcast();
                    }
                    countingIdlingResourceChange(false);
                    return;
                }

                if(weather1 != null && fileName.contains(weather1.getWeather().get(0).getIcon()))
                {
                    if(!result) {
                        downloadFile(weather1.getWeather().get(0).getIcon());
                        return;
                    }
                    Bitmap bm = BitmapFactory.decodeFile(fileName);     // Если файл получен, отображаем его в представлении
                    imageView.setImageBitmap(bm);
                }

                if (weatherAdapter != null)                             // Или делаем перерисовку RecyclerView, если он оттуда
                    weatherAdapter.notifyDataSetChanged();
                countingIdlingResourceChange(false);

            }
        }
    }

    private class NetworkMonitor extends BroadcastReceiver {         // Ресивер для приемки сообщений об изменении доступности интернета

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo info = manager.getActiveNetworkInfo();
            if(info != null && info.isConnected())
            {
                switch (info.getType())
                {
                    case ConnectivityManager.TYPE_WIFI:
                        Log.d(TAG, "NetworkMonitor: TYPE_WIFI");
                        break;
                    case ConnectivityManager.TYPE_MOBILE:
                        Log.d(TAG, "NetworkMonitor: TYPE_MOBILE");
                        break;
                    default:
                        Log.d(TAG, "NetworkMonitor: " + info.getType());
                        break;
                }
                isInternetAvailable = true;
                reloadAllPendingFiles();
            }
            else
            {
                isInternetAvailable = false;
                Toast.makeText(context, R.string.no_internet_connection, Toast.LENGTH_LONG).show();
                Log.d(TAG, "NetworkMonitor: No network!");
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler();

        mapper = new ObjectMapper();                                // Объект Jackson для парсинга Json-файлов

        // Контролы представления
        imageView = findViewById(R.id.image);
        descriptionText = findViewById(R.id.weather_description);
        precipitationText = findViewById(R.id.precipitation_level);

        cityText = findViewById(R.id.city);
        temperatureText = findViewById(R.id.temperature);
        temperatureTime = findViewById(R.id.temperature_time);
        replayButton = findViewById(R.id.replay_button);
        replayButton.setOnClickListener(v -> {
            downloadFile(w1);
            downloadFile(w5);});

        pressureText = findViewById(R.id.pressure);
        pressureTitle = findViewById(R.id.pressure_title);
        pressureUnits = findViewById(R.id.pressure_units);

        humidityText = findViewById(R.id.humidity);
        humidityTitle = findViewById(R.id.humidity_title);

        cloudsText = findViewById(R.id.clouds);
        cloudsTitle = findViewById(R.id.clouds_title);

        windText = findViewById(R.id.wind);
        windTitle = findViewById(R.id.wind_title);
        windDirection = findViewById(R.id.wind_direction);

        dayControl = findViewById(R.id.day_control);

        weatherRecyclerView = findViewById(R.id.recycler_view);
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
            weatherLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        else
            weatherLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        weatherRecyclerView.setLayoutManager(weatherLayoutManager);

        SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);  // Считываем сохраненные параметры из SharedPreferences
        if (sharedPreferences.contains(APP_PREFERENCES_LOCATION_FLAG))
            isByLocation = sharedPreferences.getBoolean(APP_PREFERENCES_LOCATION_FLAG, true);

        if(isByLocation)        // Последним был режим автолокации
        {                       // Считываем последние записанные координаты
            if(sharedPreferences.contains(APP_PREFERENCES_LATITUDE))
                currentLat = sharedPreferences.getFloat(APP_PREFERENCES_LATITUDE, 0);
            if(sharedPreferences.contains(APP_PREFERENCES_LONGITUDE))
                currentLon = sharedPreferences.getFloat(APP_PREFERENCES_LONGITUDE, 0);
        }
        else                    // Последним был режим ручной локации
        {                       // Считываем ID последнего города для запроса новых данных
            if(sharedPreferences.contains(APP_PREFERENCES_CURRENT_TOWN_ID))
                currentTownId = sharedPreferences.getString(APP_PREFERENCES_CURRENT_TOWN_ID, null);
        }

        File fileWeather1 = new File(getCacheDir(), w1 + "_");            // Последний загруженный Json-файл с текущей погодой
        if(!fileWeather1.exists()) {                                            // Если файла нет, его нужно загрузить из сервиса,
             locationPriority = HIGH_ACCURACY;                                  // предварительно получив координаты по ускоренной схеме
        }
        else {
            readWeather();
            redrawWeather();
        }

        File fileWeather5 = new File(getCacheDir(), w5 + "_");            // Последний загруженный Json-файл с прогнозом на 5 дней
        if(!fileWeather5.exists()) {                                            // Если файла нет, его нужно загрузить из сервиса
             locationPriority = HIGH_ACCURACY;                                  // предварительно получив координаты по ускоренной схеме
        }
        else {
            readForcast();
            redrawForcast();
        }

        // Проверка доступности GooglePlayServices
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int errorCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (errorCode != ConnectionResult.SUCCESS) {                                                // Если недоступен, то вывод Toast и в дальнейшем
            //Toast.makeText(this, R.string.GPS_is_not_available, Toast.LENGTH_LONG).show();  // выбор местоположения только вручную
        }
        else            // Если доступен, то строим клиента FusedLocationProviderClient, запрос координат - в методе findPosition() (после получения разрешения на определение локации)
        {
            mClient = LocationServices.getFusedLocationProviderClient(this);
            if (mClient == null)
                Toast.makeText(this, R.string.location_service_unavailable, Toast.LENGTH_LONG).show();
            mSettingsClient = LocationServices.getSettingsClient(this);
            mLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);

                    Log.d(TAG, "onLocationChanged() priority = " + locationRequest.getPriority());

                    currentLat = locationResult.getLastLocation().getLatitude();
                    currentLon = locationResult.getLastLocation().getLongitude();
                    SharedPreferences.Editor editor = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE).edit();
                    editor.putFloat(APP_PREFERENCES_LATITUDE, (float)currentLat);
                    editor.putFloat(APP_PREFERENCES_LONGITUDE, (float)currentLon);
                    editor.apply();

                    downloadFile(w1);     // Здесь вызовы сервиса OpenWeatherMap
                    downloadFile(w5);     // после получения координат

                    if(locationPriority == HIGH_ACCURACY)           // Переключение на обычный приоритет для экономии заряда
                    {
                        locationPriority = POWER_ACCURACY;
                        locationRequest.setPriority(locationPriority);
                        mClient.removeLocationUpdates(mLocationCallback)
                                .addOnSuccessListener(command -> mClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper()));
                    }
                }
            };

            locationRequest = new LocationRequest();
            locationRequest.setPriority(locationPriority);
            locationRequest.setInterval(60000);
            locationRequest.setFastestInterval(30000);
            locationRequest.setSmallestDisplacement(1000);
        }

        fileDownloadingReceiver = new FileDownloadingReceiver();            // Ресивер, принимающий сообщения о закачке файлов (отправлюяются из FileDownloadService после успешного скачивания файла)
        networkMonitor = new NetworkMonitor();                              // Ресивер, получающий сообщения при изменении доступности интернет-сети

        File townListFile = new File(getCacheDir(), cityList + "_");  // Список городов, поддерживаемый сервисом - скачиваются при первом запуске и загружаются в базу
        if(!townListFile.exists()) {                                        // Если список еще не загружен, запускаем загрузку в IntentServicе
            downloadFile(cityList);
        }
        else                                                                // Если файл скачан, а база не заполнена до конца, обнуляем базу и запускаем ее заполнение
        {
            String dbStatus = "";
            if(sharedPreferences.contains(APP_PREFERENCES_DATABASE))
                dbStatus = sharedPreferences.getString(APP_PREFERENCES_DATABASE, "");
            if(!dbStatus.equals(APP_PREFERENCES_DB_DONE))
            {
                countingIdlingResourceChange(true);
                FillingDatabase.start(getApplicationContext(), townListFile.toString());
            }
        }
    }

    public void downloadFile(String filename) {                     // Метод для загрузки файла из сервиса OpenWeatherMap (заружает файл через вызов IntentService при наличии интернета
                                                                    // или ставит в очередь на загрузку путем помещения имени файла в Set, если интернета нет).
        SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        if(sharedPreferences.contains(APP_PREFERENCES_PENDING_FILES))
            pendingFiles = sharedPreferences.getStringSet(APP_PREFERENCES_PENDING_FILES, new HashSet<>());

        if(isInternetAvailable) {           // Интернет есть - делаем запросы на загрузку файлов
            countingIdlingResourceChange(true);
            String cacheDir = getCacheDir().toString() + "/";
            switch (filename) {
                case cityList:
                    FileDownloadService.startActionDownloadFile(this,
                            "http://bulk.openweathermap.org/sample/" + cityList,
                            cacheDir + filename);
                    break;

                case w1:
                    String weather1Uri = makeWeather1Uri();
                    FileDownloadService.startActionDownloadFile(this, weather1Uri, cacheDir + filename);
                    break;

                case w5:
                    String weather5Uri = makeWeather5Uri();
                    FileDownloadService.startActionDownloadFile(this, weather5Uri, cacheDir + filename);
                    break;

                default:
                    FileDownloadService.startActionDownloadFile(this,
                            //"http://openweathermap.org/img/w/" + filename + ".png",
                            "http://openweathermap.org/img/wn/" + filename + "@2x.png",
                            cacheDir + filename);
            }
            if (pendingFiles.contains(filename)) {
                pendingFiles.remove(filename);
                Log.d(TAG, "pendingFiles: removed " + filename);
            }
        }
        else                                // Интернета нет - сохраняем имена файлов в SharedPreferences для загрузки после восстановления интернет-соединения
        {
            pendingFiles.add(filename);
            Log.d(TAG, "pendingFiles: added " + filename);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();             // Сохраняем Set с именами файлов в SharedPreferences для загрузки после поворота
        editor.putStringSet(APP_PREFERENCES_PENDING_FILES, pendingFiles);       // или повторного запуска приложения
        editor.commit();
    }

    private void readWeather() {                                                // Считывает файл погоды из json-файла или делает его запрос в сервис, если файл не обнаружен
        File fileWeather1 = new File(getCacheDir(), w1 + "_");
        if(!fileWeather1.exists())
        {
            downloadFile(w1);
            return;
        }
        long lastTimeWeather = 0;
        try {
            weather1 = mapper.readValue(fileWeather1, Weather1.class);
            lastTimeWeather = getFromSharedPrefs(APP_PREFERENCES_TIME_WEATHER);
            weather1.setTime(lastTimeWeather);
            currentCountry = weather1.getSys().getCountry();
            currentTown = weather1.getName();
        } catch (IOException e) {
            Toast.makeText(this, R.string.reading_weather_error, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        if((System.currentTimeMillis() - lastTimeWeather) > MIN_RENEW_INTERVAL) // Перезапрос данных у сервиса по таймауту.
            downloadFile(w1);
    }

    private void readForcast() {                                            // Считывает файл прогноза погоды из json-файла или делает его запрос в сервис, если файл не обнаружен

        File fileWeather5 = new File(getCacheDir(), w5 + "_");
        if(!fileWeather5.exists())
        {
            downloadFile(w5);
            return;
        }
        long lastTimeForcast = 0;
        try {
            weather5 = mapper.readValue(fileWeather5, Weather5.class);
            lastTimeForcast = getFromSharedPrefs(APP_PREFERENCES_TIME_FORCAST);
            weather5.setTime(lastTimeForcast);
        } catch (IOException e) {
            Toast.makeText(this, R.string.reading_forecast_error, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        if((System.currentTimeMillis() - lastTimeForcast) > MIN_RENEW_INTERVAL)
            downloadFile(w5);
    }

    private long getFromSharedPrefs(String preferencesKey) {                    // Считывает время из SharedPreferences по ключу
        long lastTime = 0;
        SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        if (sharedPreferences.contains(preferencesKey))
            lastTime = sharedPreferences.getLong(preferencesKey, 0);
        return lastTime;
    }

    private String makeWeather1Uri() {      // Формирует запрос в сервис для получения данных о погоде в зависимости от текущего режима: либо по координатам, либо по id
        if (isByLocation)   // http://api.openweathermap.org/data/2.5/weather?lat=55.632390&lon=37.523040&APPID=29c0f8d67faaa78d8b6a03247ce7bae9&units=metric&lang=ru
            return URI_WEATHER1 + "?lat=" + currentLat + "&lon=" + currentLon + "&APPID=" + getString(R.string.appid) + "&units=metric&lang=" + Locale.getDefault().getCountry();
        else
            return URI_WEATHER1 + "?id=" + currentTownId + "&APPID=" + getString(R.string.appid) + "&units=metric&lang=" + Locale.getDefault().getCountry();
    }

    private String makeWeather5Uri() {      // Формирует запрос в сервис для получения данных о прогнозе погоде в зависимости от текущего режима: либо по координатам, либо по id
        if (isByLocation)   // http://api.openweathermap.org/data/2.5/forecast?lat=55.632498&lon=37.523183&APPID=29c0f8d67faaa78d8b6a03247ce7bae9&units=metric&lang=ru
            return URI_WEATHER5 + "?lat=" + currentLat + "&lon=" + currentLon + "&APPID=" + getString(R.string.appid) + "&units=metric&lang=" + Locale.getDefault().getCountry();
        else
            return URI_WEATHER5 + "?id=" + currentTownId + "&APPID=" + getString(R.string.appid) + "&units=metric&lang=" + Locale.getDefault().getCountry();
    }

    private void reloadAllPendingFiles() {  // Загружает все отложенные из-за отсутсвия интернета файлы
        Log.d(TAG, "reloadAllPendingFiles()...");
        String[] files = new String[pendingFiles.size()];
        pendingFiles.toArray(files);
        for(String s : files)
        {
            Log.d(TAG, "                          " + s);
            downloadFile(s);
        }
    }

    private void redrawWeather() {          // Перевыводит данные о погоде в лейауте активности
        Log.d(TAG, "Weather view is redrawing...");

        if(weather1 == null)
        {
            Log.d(TAG, "Weather1 = null");
            return;
        }

        cityText.setText(weather1.getName());
        temperatureText.setText(String.format("%.1f", weather1.getMain().getTemp()).replace('-', '−'));
        temperatureTime.setText((System.currentTimeMillis() - weather1.getTime())/60000 + " " + getString(R.string.x_min_ago));
        String[] description = weather1.getWeather().get(0).getDescription().toUpperCase().split(" ");
        StringBuilder stringBuilder = new StringBuilder(description[0]);
        int pos = stringBuilder.length();
        for(int i = 1; i < description.length; i++)                             // Построение описания погоды, если оно состоит из
        {                                                                       // нескольких слов (чтобы не занимало много строк)
            if(pos + 1 + description[i].length() > 10) {
                stringBuilder.append('\n').append(description[i]);
                pos = description[i].length();
            }
            else {
                stringBuilder.append(' ').append(description[i]);
                pos += 1+ description[i].length();
            }
        }
        descriptionText.setText(stringBuilder.toString());

        File file = new File(getCacheDir(), weather1.getWeather().get(0).getIcon());    // Файл с иконкой погодных условий

        if(file.exists()) {                                                         // Если файл существует, выводим в ImageView
            Bitmap bm = BitmapFactory.decodeFile(file.toString());
            imageView.setImageBitmap(bm);
        }
        else                                                                        // Если нет, скачиваем в IntentService и после выводим
        {
            downloadFile(weather1.getWeather().get(0).getIcon());
        }

        pressureTitle.setText(R.string.pressure);
        pressureText.setText(String.format("%.0f", weather1.getMain().getPressure()));
        pressureUnits.setText(R.string.pressure_units);

        humidityTitle.setText(R.string.humidity);
        humidityText.setText(weather1.getMain().getHumidity().toString());

        cloudsTitle.setText(R.string.clouds);
        cloudsText.setText(weather1.getClouds().getAll().toString());

        windTitle.setText(R.string.wind);
        windText.setText(String.format("%d", weather1.getWind().getSpeed()));
        windDirection.setDirection(weather1.getWind().getDeg());

        dayControl.setSunriseSunset(weather1.getSys().getSunrise(), weather1.getSys().getSunset());

        Log.d(TAG, "Files: ");
        for (String ss : getCacheDir().list())
        {
            Log.d(TAG, "        " + ss);
        }
    }

    private void redrawForcast() {          // Перевыводит данные о прогнозе погоды в лейауте активности и RecyclerView
        Log.d(TAG, "Weather forcast is redrawing...");

        if(weather5 == null)
        {
            Log.d(TAG, "Weather5 = null");
            return;
        }

        Snow snow = weather5.getList().get(0).getSnow();
        Rain rain = weather5.getList().get(0).getRain();
        Double snowLevel = null;
        if (snow != null)
            snowLevel = snow.get3h();
        Double rainLevel = null;
        if (rain != null)
            rainLevel = rain.get3h();
        double level = 0;
        if (snowLevel != null)
            level += snowLevel;
        if (rainLevel != null)
            level += rainLevel;
        String formatString;
        if(level > 10 || level == 0)
            formatString = "%.0f %s";
        else
            formatString = "%.1f %s";
        if (level != 0) {
            precipitationText.setTextSize(12);
            precipitationText.setText(String.format(formatString, level, getString(R.string.precipitation_for_3_hours)));
        }
        else
            precipitationText.setTextSize(0);

        weatherAdapter = new WeatherAdapter(this, weather5.getList(), getCacheDir().toString());
        weatherRecyclerView.setAdapter(weatherAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(mainMenu != null) {      // Если база сформирована активируем меню
            SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE);
            String dbString = sharedPreferences.getString(MainActivity.APP_PREFERENCES_DATABASE, "");
            if(dbString.equals(MainActivity.APP_PREFERENCES_DB_DONE))
                mainMenu.findItem(R.id.find_item).setVisible(true);
        }

        registerReceiver(fileDownloadingReceiver, new IntentFilter(FileDownloadService.FILE_DOWNLOAD_SERVICE));     // Приемник обратной связи от сервиса загрузки файлов
        registerReceiver(networkMonitor, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));                // Отслеживание состояния сети

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)     // Проверка разрешений определения местоположения для версий андроида > 6
        {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
                Log.d(TAG, "Position Request sent()");
                findPosition();
            }
            else
            {
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
        else
        {
            findPosition();
        }

        mainHandler.postDelayed(new Runnable() {        // Повторяющаяся каждую минуту процедура перерисовки представления или перезапрос погоды по истечении MAX_RELOAD_INTERVAL
            @Override public void run() {

                redrawWeather();                                    // Перерисовка представления каждую минуту

                if(System.currentTimeMillis() - getFromSharedPrefs(APP_PREFERENCES_TIME_WEATHER) > MAX_RELOAD_INTERVAL ||       // Или перезапрос данных по истечении времени
                        System.currentTimeMillis() - getFromSharedPrefs(APP_PREFERENCES_TIME_FORCAST) > MAX_RELOAD_INTERVAL)
                {
                    downloadFile(w1);
                    downloadFile(w5);
                }

                mainHandler.postDelayed(this, 60 * 1000);  // Повторять каждые 60 секунд
                                }},
                65*1000);
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(fileDownloadingReceiver);                        // Убрать контроль за закачкой файлов
        unregisterReceiver(networkMonitor);                                 // Убрать контроль за состоянием сети

        Log.d(TAG, "FusedLocationProviderClient disconnecting...");
        if (mClient != null)
            mClient.removeLocationUpdates(mLocationCallback)                // Убрать слежение за местоположением
                    .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.d(TAG, "disconnecting done");
                        }
                    });

        mainHandler.removeCallbacksAndMessages(null);               // Убираем все повторяющиеся задачи из очереди
    }

    @SuppressLint("MissingPermission")
    private void findPosition() {                       // Метод для отправки запросов в Fused Location Provider для определения местоположения

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        LocationSettingsRequest mLocationSettingsRequest = builder.build();

        Log.d(TAG, "FusedLocationProviderClient connecting...");

        if (mClient != null)
            mClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper())       // Постановка клиента на отслеживание местоположения
                    .addOnSuccessListener(command -> Log.d(TAG, "connection done"))               // с установленными параметрами locationRequest
                    .addOnFailureListener(command -> {
                        Toast.makeText(this, R.string.can_not_find_location, Toast.LENGTH_LONG).show();
                        Log.d(TAG, "connection failed");
                    });
        else
            Toast.makeText(this, R.string.location_service_unavailable, Toast.LENGTH_LONG).show();

//        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)                           // Гугловский код с проверкой установок - не рабочий (например, в авиарежиме срабатывает onFailure()
//                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {   // и при выключении авиарежима нужно снова выполнять как-то mClient.requestLocationUpdates.
//                    @Override
//                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
//                        Log.d(TAG, "connecting done");
//                        mClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper());
//                    }
//                })
//                .addOnFailureListener(this, new OnFailureListener() {
//                    @Override
//                    public void onFailure(@NonNull Exception e) {
//                        int statusCode = ((ApiException) e).getStatusCode();
//                        Log.d(TAG, "connecting failed");
//                        switch (statusCode) {
//                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
//                                Log.d(TAG, "Location settings are not satisfied. Attempting to upgrade location settings ");
//                                try {
//                                    // Show the dialog by calling startResolutionForResult(), and check the
//                                    // result in onActivityResult().
//                                    ResolvableApiException rae = (ResolvableApiException) e;
//                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
//                                } catch (IntentSender.SendIntentException sie) {
//                                    Log.d(TAG, "PendingIntent unable to execute request.");
//                                }
//                                break;
//                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
//                                String errorMessage = "Location settings are inadequate, and cannot be fixed here. Fix in Settings.";
//                                //mClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper());
//                                Log.d(TAG, errorMessage);
//                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
//                        }
//                    }
//                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mainMenu = menu;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.location_item).setVisible(!isByLocation);    // Если в режиме автолокации, исключаем пункт меню переключения на автолокацию

        String dbStatus = "";                                           // Определение наличия базы данных; если ее еще нет - скрываем значок поиска в меню
        SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        if (sharedPreferences.contains(APP_PREFERENCES_DATABASE))
            dbStatus = sharedPreferences.getString(APP_PREFERENCES_DATABASE, "");
        if (!dbStatus.equals(APP_PREFERENCES_DB_DONE))
            menu.findItem(R.id.find_item).setVisible(false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case R.id.find_item:        // Запускаем активность поиска локации вручную
                Intent intent = new Intent(this, FindSettlementActivity.class);
                intent.putExtra(INTENT_CURRENT_COUNTRY, currentCountry);
                intent.putExtra(INTENT_CURRENT_TOWN, currentTown);
                startActivityForResult(intent, INTENT_FIND_SETTLEMENT);
                return true;

            case R.id.location_item:    // Переключаемся на режим автолокации
                isByLocation = true;
                SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
                currentLat = sharedPreferences.getFloat(APP_PREFERENCES_LATITUDE, 0);       // Читаем последние полученные координаты из SharedPreferences
                currentLon = sharedPreferences.getFloat(APP_PREFERENCES_LONGITUDE, 0);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(APP_PREFERENCES_LOCATION_FLAG, isByLocation);                     // Записываем текущий режим в SharedPreferences: isByLocation = true
                editor.apply();
                downloadFile(w1);                                                                   // Перезапрашиваем погоду и прогноз на 5 дней
                downloadFile(w5);
                mainMenu.findItem(R.id.location_item).setVisible(false);                            // И скрываем этот пункт меню
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(requestCode == LOCATION_PERMISSION_REQUEST_CODE){
            if (grantResults.length <= 0) {
                Log.d(TAG, "User interaction was cancelled.");
            }
            else  if (grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
            } else {
                findPosition();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case INTENT_FIND_SETTLEMENT:            // Возврат из активности выбора города
                if (resultCode == RESULT_OK)
                {
                    Log.d(TAG, "Answer from FindSettlementActivity received: Ok");
                    currentCountry = data.getStringExtra(INTENT_CURRENT_COUNTRY);
                    currentTownId = data.getStringExtra(INTENT_CURRENT_TOWN_ID);
                    currentTown = data.getStringExtra(INTENT_CURRENT_TOWN);
                    isByLocation = false;

                    SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean(APP_PREFERENCES_LOCATION_FLAG, isByLocation);                 // Записываем текущий режим - указание локации вручную
                    editor.putString(APP_PREFERENCES_CURRENT_TOWN_ID, currentTownId);
                    editor.apply();
                    downloadFile(w1);
                    downloadFile(w5);
                    if(mainMenu != null)
                        mainMenu.findItem(R.id.location_item).setVisible(true);
                }
                if (requestCode == RESULT_CANCELED)
                    Log.d(TAG, "Answer from FindSettlementActivity received: canceled");       // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                break;

            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.d(TAG, "User chose not to make required location settings changes.");
                        break;
                }
                break;
        }
    }

    private CountingIdlingResource countingIdlingResource;          // Объект-семафор для ожидания выполнения тестов при выполнении длительных операций типа загрузки файлов
    // или заполнения базы данных городов
    public CountingIdlingResource getCountingIdlingResource() {
        return countingIdlingResource;
    }
    public void setCountingIdlingResource(CountingIdlingResource countingIdlingResource) {
        this.countingIdlingResource = countingIdlingResource;
    }

    private static int countingIdlingResourceCount;

    private void countingIdlingResourceChange(boolean increase) {       // Метод для обработки CountingIdlingResource (увеличение или уменьшение счетчика семафора)

        if(countingIdlingResource == null) {
            if(increase)
                countingIdlingResourceCount++;
            else {
                countingIdlingResourceCount--;
                if(countingIdlingResourceCount < 0)
                    countingIdlingResourceCount = 0;
            }
            Log.d(TAG, "countingIdlingResource = null increase = " + increase + " countingIdlingResourceCount = " + countingIdlingResourceCount);

            return;
        }

        while (countingIdlingResourceCount > 0)
        {
            countingIdlingResource.increment();
            countingIdlingResourceCount--;
            Log.d(TAG, "countingIdlingResource != null, countingIdlingResourceCount decreasing, countingIdlingResourceCount = " + countingIdlingResourceCount + " isIdleNow() = " + countingIdlingResource.isIdleNow());
        }

        if(increase) {
            countingIdlingResource.increment();
            Log.d(TAG, "countingIdlingResource.increment()");
        }
        else {
            if(!countingIdlingResource.isIdleNow()) {
                countingIdlingResource.decrement();
                Log.d(TAG, "countingIdlingResource.decrement  isIdleNow() = " + countingIdlingResource.isIdleNow());
            }
            else
            {
                Log.d(TAG, "Try to decrement countingIdlingResource in Idle state");
            }
        }
    }

}
