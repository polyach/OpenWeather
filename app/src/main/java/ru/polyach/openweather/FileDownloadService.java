package ru.polyach.openweather;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileDownloadService extends IntentService {    // Сервис, обеспечивающий загрузку всех типов файлов: картинок, Json-файлов, архивов и пр.

    private static final String TAG = "OpenWeather";

    public static final String FILE_DOWNLOAD_SERVICE = "ru.polyach.openweather.FileDownloadingReceiver";
    public static final String FILE_DOWNLOADED = "ru.polyach.openweather.FileDownloadingReceiver.done";
    public static final String FILE_DOWNLOAD_RESULT = "ru.polyach.openweather.FileDownloadingReceiver.result";
    public static final String FILE_DOWNLOAD_TOWNS_NUMBER = "ru.polyach.openweather.FileDownloadingReceiver.number";

    private static final String ACTION_DOWNLOAD_FILE = "ru.polyach.openweather.action.download.file";

    private static final String URI_PARAM1 = "ru.polyach.openweather.extra.PARAM1";
    private static final String FILE_PARAM2 = "ru.polyach.openweather.extra.PARAM2";

    private ExecutorService executorService;

    public FileDownloadService() {

        super("FileDownloadService");
        executorService = Executors.newFixedThreadPool(2);  // ExecutorService для параллельного скачивания 2-х файлов, один из которых может быть большой
    }

    public static void startActionDownloadFile(Context context, String iconUri, String fileName) {

        Intent intent = new Intent(context, FileDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD_FILE);
        intent.putExtra(URI_PARAM1, iconUri);
        intent.putExtra(FILE_PARAM2, fileName);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_DOWNLOAD_FILE.equals(action)) {
                final String param1 = intent.getStringExtra(URI_PARAM1);
                final String param2 = intent.getStringExtra(FILE_PARAM2);
                handleActionDownloadFile(param1, param2);
            }
        }
    }

    private void handleActionDownloadFile(String url, String filename) {

        executorService.execute(()-> {      // Параллельное скачивание до 2-х файлов - на случай, если один большой и загружается долго
            boolean result = Utilities.downloadFile(url, filename);
            Intent intent = new Intent(FILE_DOWNLOAD_SERVICE);
            intent.putExtra(FILE_DOWNLOADED, filename);
            intent.putExtra(FILE_DOWNLOAD_RESULT, result);
            sendBroadcast(intent);
        });
    }
}
