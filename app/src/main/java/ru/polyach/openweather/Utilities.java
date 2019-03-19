package ru.polyach.openweather;

import android.graphics.Color;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class Utilities {

    private static final String TAG = "OpenWeather";

    public static int getWeatherColor(float alpha)      // Расчет цвета в соответствии с уровнем погодной опасности 0 <= alpha <= 1: зеленый/желтый/оранжевый/красный
    {
        float beta;
        int red1, red2;
        int green1, green2;
        int blue1, blue2;

        if (alpha <= 0.3333333333)                          // Диапазон зеленый (0x00FF00) - желтый (0xFFFF00)
        {
            beta = alpha*3;
            red1 = 0x00;
            red2 = 0xFF;
            green1 = 0xFF;
            green2 = 0xFF;
            blue1 = 0x00;
            blue2 = 0x00;
        }
        else if (alpha <= 0.6666666666)                     // Диапазон желтый (0xFFFF00) - оранжевый (0xFF8C00)
        {
            beta = (alpha - 0.3333333333f)*3;
            red1 = 0xFF;
            red2 = 0xFF;
            green1 = 0xFF;
            green2 = 0x8C;
            blue1 = 0x00;
            blue2 = 0x00;
        }
        else                                                // Диапазон оранжевый (0xFF8C00) - красный (0xFF0000)
        {
            beta = (alpha - 0.6666666666f)*3;
            red1 = 0xFF;
            red2 = 0xFF;
            green1 = 0x8C;
            green2 = 0x00;
            blue1 = 0x00;
            blue2 = 0x00;
        }

        int red = (int)(red1 + beta*(red2 - red1));
        int green = (int)(green1 + beta*(green2 - green1));
        int blue = (int)(blue1 + beta*(blue2 - blue1));
        return Color.rgb(red, green, blue);
    }

    public static boolean downloadFile(String url, String filename)  {            // Метод для скачивания файлов

        Log.d(TAG, "Starting download " + url + " Thread = " + Thread.currentThread());

        boolean result = false;

        File output = new File(filename);
        if (output.exists())
            output.delete();

        try
        {
            URL netURL = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) netURL.openConnection();

            try (InputStream input = connection.getInputStream();
                 FileOutputStream fos = new FileOutputStream(output.getPath())) {
                int read;
                long bytesRead = 0;
                byte[] bytes = new byte[1024];

                while ((read = input.read(bytes, 0, bytes.length)) != -1) {
                    fos.write(bytes, 0, read);
                    bytesRead += read;
                }
                result = true;
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        catch (IOException e) {
            if(output.exists()) {
                output.delete();
            }
            e.printStackTrace();
        }
        return result;
    }

//    public static int ungzip(Context context, File gzipFile)                      // Разбор архивного файла с городами и запись их в базу
//    {
//        long time1 = System.currentTimeMillis();
//
//        SharedPreferences.Editor editor = context.getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE).edit();
//        editor.putString(MainActivity.APP_PREFERENCES_DATABASE, MainActivity.APP_PREFERENCES_DB_CREATING);
//        editor.apply();
//
//        SQLiteDatabase database = new TownBaseHelper(context).getWritableDatabase();
//        database.delete(TownTable.NAME, null, null);          // Удаляем все что было в базе
//
//        Log.d(TAG, "ungzip() started");
//
//        StringBuilder stringBuilder = new StringBuilder();
//        int count = 0;
//        Set<String> allCountries = new TreeSet<>();
//
//        ObjectMapper mapper = new ObjectMapper();                                   // Объект Jackson для разбора Json
//
//        String sql = "INSERT INTO "                                                 // Запрос на вставку данных в базу для компиляции
//                + TownTable.NAME
//                + " ( "
//                + TownTable.Cols.ID + ", "
//                + TownTable.Cols.NAME + ", "
//                + TownTable.Cols.COUNTRY + ", "
//                + TownTable.Cols.LAT + ", "
//                + TownTable.Cols.LON
//                + " ) VALUES ( ?, ?, ?, ?, ? );";
//
//        SQLiteStatement statement = database.compileStatement(sql);                 // Компиляция запроса
//
//        char[] buf = new char[1024*1024];                                           // Буфер для чтения из потока архивного файла
//        java.util.List<Town> towns;                                                 // Лист городов, прочитанных порциями из буфера
//
////        long allFindTime = 0;
////        long allParsingTime = 0;
////        long allDBTime = 0;
//
//        try (   FileInputStream fis = new FileInputStream(gzipFile);
//                BufferedInputStream bis = new BufferedInputStream(fis);
//                GZIPInputStream gzis = new GZIPInputStream(bis);
//                BufferedReader in = new BufferedReader(new InputStreamReader(gzis));
//             ){
//            while (in.read(buf) != -1)                          // Считываем порцию данных в буфер
//            {
//                stringBuilder.append(buf);
//                while (stringBuilder.charAt(0) != '{')          // Ищем первую скобку '{'
//                    stringBuilder.deleteCharAt(0);
//
////                long time2 = System.currentTimeMillis();
//                int end = findLastBrace(stringBuilder);         // Ищем последнюю скобку '}'
////                allFindTime += (System.currentTimeMillis() - time2);
//
//                String tmp = stringBuilder.substring(0, end + 1);
////                long time3 = System.currentTimeMillis();
//                towns = mapper.readValue("[" + tmp + "]", new TypeReference<java.util.List<Town>>() {});    // Парсинг Json
//
////                allParsingTime += (System.currentTimeMillis() - time3);
//
////                long time4 = System.currentTimeMillis();
//
//                database.beginTransaction();                        // Загрузка в базу
//                for (Town town : towns) {
//                    statement.bindString(1, town.getId());
//                    statement.bindString(2, town.getName());
//                    statement.bindString(3, town.getCountry());
//                    statement.bindString(4, town.getCoord().getLat().toString());
//                    statement.bindString(5, town.getCoord().getLon().toString());
//                    statement.execute();
//                    allCountries.add(town.getCountry());
//                    if (Thread.currentThread().isInterrupted())     // Отслеживание прерывания потока из MainActivity
//                    {
//                        Log.d(TAG, Thread.currentThread() + " is interrupted, closed");
//                        break;
//                    }
//                }
//
//                database.setTransactionSuccessful();                // Закрытие транзакции, если все идет нормально
//                database.endTransaction();
//
//                if (Thread.currentThread().isInterrupted())         // Если активность была закрыта в процессе создания базы и вызван метод onDestroy() очищаем базу
//                {                                                   // и выходим с ошибкой (count = 0)
//                    Log.d(TAG, Thread.currentThread() + " is interrupted, closed");
//                    database.delete(TownTable.NAME, null, null);
//                    count = 0;                                      // Сбрасываем счетчик городов для перезаливки базы в новой активности
//                    break;
//                }
//
//                //                allDBTime += (System.currentTimeMillis() - time4);
//
//                count += towns.size();                          // Число прочитанных городов
//                Log.d(TAG, "Записей занесно в базу: " + count + " из " + allCountries.size() + " стран " + Thread.currentThread() + " interrupted: " + Thread.currentThread().isInterrupted());
//
//                Cursor c = database.query(TownTable.NAME, new String[] {"_id"}, null, null, null, null, null, null);
//                Log.d(TAG, "Всего записей в базе: " + c.getCount());
//
//                stringBuilder.delete(0, end);
//            }
////            Log.d(TAG, "allFindTime: " + allFindTime / 1000 + " s");
////            Log.d(TAG, "allParsingTime: " + allParsingTime / 1000 + " s");
////            Log.d(TAG, "allDBTime: " + allDBTime / 1000 + " s");
//            Log.d(TAG, "ungzip() finished: " + count + " towns found (" + allCountries.size() + " countries)  time = " + (System.currentTimeMillis() - time1) / 1000 + " s");
//
//            if (!Thread.currentThread().isInterrupted()) {
//                editor.putStringSet(MainActivity.APP_PREFERENCES_ALL_COUNTRIES, allCountries);
//                editor.putString(MainActivity.APP_PREFERENCES_DATABASE, MainActivity.APP_PREFERENCES_DB_DONE);
//                editor.apply();
//            }
//        }
//        catch (UnrecognizedPropertyException e) {   // Ошибка парсинга файла - скорее всего изменился формат файла, требуется переделка класса Town
//            e.printStackTrace();
//            Log.d(TAG, "ungzip() failed: Unrecognized property exception");
//            count = -2;                             // Устанавливаем счетчик городов равным -2 чтобы файл не грузился циклически
//        }
//        catch (IOException e) {                     // Ошибка чтения файла - скорее всего он не полностью закачался
//            e.printStackTrace();
//            Log.d(TAG, "ungzip() failed: Error reading settlement file");
//            count = -1;                             // Устанавливаем счетчик городов равным -1 для перезагрузки файла с сервиса
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//            Log.d(TAG, "ungzip() failed");
//        }
//        finally {
//            database.close();
//        }
//                            // count > 0 - нормальное окончание парсинга, база заполнена, в SharedPreferences занесена отметка об этом и список стран;
//        return count;       // count == 0 - поток прерван поворотом устройства, делать ничего не нужно, в новой активности разбор возобновится;
//    }                       // count == -1 - ошибка чтения/парсинга файла, его нужно загрузить заново;

    public static int findLastBrace(StringBuilder stringBuilder) {

        int end = -1;
        int balance = 1;
        for (int i = 1; i < stringBuilder.length(); i++)
        {
            char c = stringBuilder.charAt(i);
            if (c == '{')
                balance++;
            if (c == '}')
            {
                balance--;
                if (balance == 0)
                    end = i;
            }
        }
        return end;
    }
}
