package ru.polyach.openweather;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.util.Log;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import ru.polyach.openweather.Database.TownBaseHelper;
import ru.polyach.openweather.Database.TownDbSchema;
import ru.polyach.openweather.Model.Town;

class FillingDatabase {     // Объект-одиночка для наполнения базы данных городов; используется один раз сразу после загрузки архива со списком городов

    private static FillingDatabase fillingDatabase = null;
    private static Context context;
    private static boolean isInProgress = false;
    private static final String TAG = "OpenWeather";

    private static class DbFillingTask extends AsyncTask<String, Void, Integer>{  // AsyncTask, выполняющий всю работу

        @Override
        protected Integer doInBackground(String... strings) {
            long time1 = System.currentTimeMillis();
            isInProgress = true;

            SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE).edit();
            editor.putString(MainActivity.APP_PREFERENCES_DATABASE, MainActivity.APP_PREFERENCES_DB_CREATING);
            editor.apply();

            SQLiteDatabase database = new TownBaseHelper(context).getWritableDatabase();
            database.delete(TownDbSchema.TownTable.NAME, null, null);          // Удаляем все что было в базе на случай, если предыдущая попытка была неудачной

            Log.d(TAG, "ungzip() started");

            StringBuilder stringBuilder = new StringBuilder();
            int count = 0;                                                                  // Число прочитанных городов
            Set<String> allCountries = new TreeSet<>();                                     // Set для множества стран

            ObjectMapper mapper = new ObjectMapper();                                       // Объект Jackson для разбора Json

            String sql = "INSERT INTO "                                                     // Запрос на вставку данных в базу для компиляции
                    + TownDbSchema.TownTable.NAME
                    + " ( "
                    + TownDbSchema.TownTable.Cols.ID + ", "
                    + TownDbSchema.TownTable.Cols.NAME + ", "
                    + TownDbSchema.TownTable.Cols.COUNTRY + ", "
                    + TownDbSchema.TownTable.Cols.LAT + ", "
                    + TownDbSchema.TownTable.Cols.LON
                    + " ) VALUES ( ?, ?, ?, ?, ? );";
            SQLiteStatement statement = database.compileStatement(sql);                     // Компиляция запроса

            char[] buf = new char[1024*100];                                                // Буфер для чтения из потока архивного файла
            java.util.List<Town> townsBuffer;                                               // Лист городов, прочитанных порциями из буфера

            File gzipFile = new File(strings[0]);
            TypeReference<List<Town>> typeReference = new TypeReference<List<Town>>() {};

            try (FileInputStream fis = new FileInputStream(gzipFile);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 GZIPInputStream gzis = new GZIPInputStream(bis);
                 BufferedReader in = new BufferedReader(new InputStreamReader(gzis))
            ){
                while (in.read(buf) != -1)                                                  // Считываем порцию данных в буфер
                {
                    stringBuilder.append(buf);
                    while (stringBuilder.length() > 0 && stringBuilder.charAt(0) != '{')    // Ищем первую скобку '{'
                        stringBuilder.deleteCharAt(0);
                    int end = Utilities.findLastBrace(stringBuilder);                       // Ищем последнюю скобку '}'
                    if (end == -1)
                        break;
                    String tmp = stringBuilder.substring(0, end + 1);

                    townsBuffer = mapper.readValue("[" + tmp + "]", typeReference); // Парсинг Json

                    database.beginTransaction();                                            // Открытие транзакции
                    for (Town town : townsBuffer) {                                         // Загрузка в базу
                        statement.bindString(1, town.getId());
                        statement.bindString(2, town.getName());
                        statement.bindString(3, town.getCountry());
                        statement.bindString(4, town.getCoord().getLat().toString());
                        statement.bindString(5, town.getCoord().getLon().toString());
                        statement.execute();
                        allCountries.add(town.getCountry());
                    }
                    database.setTransactionSuccessful();                                    // Закрытие транзакции
                    database.endTransaction();

                    count += townsBuffer.size();                                            // Число прочитанных городов

//                    Log.d(TAG, "Записей занесно в базу: " + count + " из " + allCountries.size() + " стран " + Thread.currentThread() + " interrupted: " + Thread.currentThread().isInterrupted());
//                    Cursor c = database.query(TownDbSchema.TownTable.NAME, new String[] {"_id"}, null, null, null, null, null, null);
//                    Log.d(TAG, "Всего записей в базе: " + c.getCount());

                    stringBuilder.delete(0, end);
                }
                Log.d(TAG, "ungzip() finished: " + count + " towns found (" + allCountries.size() + " countries)  time = " + (System.currentTimeMillis() - time1) / 1000 + " s");

                editor.putStringSet(MainActivity.APP_PREFERENCES_ALL_COUNTRIES, allCountries);
                editor.apply();
            }
            catch (Exception e) {                       // Ошибка парсинга файла - скорее всего изменился формат файла, требуется переделка класса Town
                e.printStackTrace();
                Log.d(TAG, "ungzip() failed");
                count = -1;
                isInProgress = false;
            }
            finally {
                database.close();
            }
                                // count > 0 - нормальное окончание парсинга, база заполнена, в SharedPreferences занесена отметка об этом и список стран;
            return count;       // count == -1 - ошибка парсинга файла, а т.к. он гарантированно загрузился правильно, его не нужно перезагружать (скорее всего изменился формат);
        }

        @Override
        protected void onPostExecute(Integer townsRead) {   // Обработка окончания наполнения базы

            isInProgress = false;

            if (townsRead >= 0) {                           // Отправляем сообщение в тот же ресивер после успешного выполнения или выполнения с ошибкой, требующего перезагрузку файла (count = 0)
                Intent dbIntent = new Intent(FileDownloadService.FILE_DOWNLOAD_SERVICE);
                dbIntent.putExtra(FileDownloadService.FILE_DOWNLOADED, "database");
                dbIntent.putExtra(FileDownloadService.FILE_DOWNLOAD_RESULT, townsRead > 0);
                dbIntent.putExtra(FileDownloadService.FILE_DOWNLOAD_TOWNS_NUMBER, townsRead);
                context.sendBroadcast(dbIntent);
                                                            // Заносим в SharedPreferences отметку о завершении заполнения базы
                SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE).edit();
                editor.putString(MainActivity.APP_PREFERENCES_DATABASE, MainActivity.APP_PREFERENCES_DB_DONE); // Отметка в SharedPreferences об успешном наполнении базы
                editor.apply();

                Log.d(TAG, "FillingDatabase(): onPostExecute()");
                fillingDatabase = null;
            }
        }
    }

    private FillingDatabase() {

        // Log.d(TAG, "FillingDatabase(): конструктор");
    }

    public static void start(Context cnt, String fileName) {  // Запуск наполнения базы, если оно еще не было начато или не закончено до конца

        if (fillingDatabase == null) {
            context = cnt;
            fillingDatabase = new FillingDatabase();
            }

        if(!isInProgress) {
            isInProgress = true;
            new DbFillingTask().execute(fileName);
            Log.d(TAG, "FillingDatabase(): started");
        }
        else
        {
            Log.d(TAG, "FillingDatabase(): skipped");
        }
    }
}


/*
catch (UnrecognizedPropertyException e) {   // Ошибка парсинга файла - скорее всего изменился формат файла, требуется переделка класса Town
                e.printStackTrace();
                Log.d(TAG, "ungzip() failed: Unrecognized property exception");
                count = -1;                             // Устанавливаем счетчик городов равным -1 чтобы файл не грузился циклически
            }
            catch (JsonParseException e)
            {
                e.printStackTrace();
                Log.d(TAG, "ungzip() failed: JsonParseException");
                count = -1;                             // Устанавливаем счетчик городов равным -1 чтобы файл не грузился циклически
            }
            catch (JsonMappingException e)
            {
                e.printStackTrace();
                Log.d(TAG, "ungzip() failed: JsonMappingException");
                count = -1;                             // Устанавливаем счетчик городов равным -1 чтобы файл не грузился циклически
            }
            catch (IOException e) {                     // Ошибка чтения файла - скорее всего он не полностью закачался
                e.printStackTrace();
                Log.d(TAG, "ungzip() failed: Error reading settlement file");
                count = 0;                              // Устанавливаем счетчик городов равным 0 для перезагрузки файла с сервиса
            }
 */
