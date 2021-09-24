package ru.polyach.openweather;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.icu.text.Collator;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import ru.polyach.openweather.Database.TownBaseHelper;
import ru.polyach.openweather.Database.TownDbSchema;
import ru.polyach.openweather.Model.Coord;
import ru.polyach.openweather.Model.Town;

public class FindSettlementActivity extends AppCompatActivity {

    private static final String TAG = "OpenWeather";
    private static final String BUNDLE_KEY_CURRENT_COUNTRY = "openweather_current_country";
    private static final String BUNDLE_KEY_CURRENT_TOWN = "openweather_current_town";

    private Set<String> allCountries;               // Set со списком всех стран, встречающихся в базе, считывается из SharedPreferences для быстроты
    private String currCountry;                     // Текущая страна, для которой из базы создается список
    private String currTown = null;                 // Текущий город, достается из Intent (или Bundle при повороте утройства)

    private Spinner countriesSpinner;               // Spinner со списком стран
    private TextView townsNumber;
    private TextView townsNumberChosen;
    private RecyclerView townsView;                 // Список городов, отображает только города текущей страны
    private ProgressBar progressBar;                // ProgressBar, выводится при длительном процессе выборки городов из базы
    private TownsAdapter townsAdapter;              // Адаптер для RecyclerView
    private int position;                           // Позиция элемента в листе при его выборе

    private Menu mainMenu;                          // Меню, нужно чтобы активировать единственный пункт после формирования листа городов

    private java.util.List<Town> fullTownList;      // Полный список городов текущей страны
    private BorNode borRoot;                        // Струтура данных бор для быстрой выборки городов по префиксу
    private Collator collator;                      // Объект, используемый для сортировки строк в зависимости от языка страны

    private Handler handler;                        // Handler для передачи сообщений из параллельного потока

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_settlement);

        handler = new Handler();

        countriesSpinner = findViewById(R.id.spinner_countries);
        townsNumber = findViewById(R.id.number_of_towns);
        townsNumberChosen = findViewById(R.id.number_of_chosen_towns);
        townsView = findViewById(R.id.recycler_view_towns);
        townsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        townsView.setHasFixedSize(true);
        progressBar = findViewById(R.id.progressbar_town);

        if(savedInstanceState == null) {
            Intent intent = getIntent();
            currCountry = intent.getStringExtra(MainActivity.INTENT_CURRENT_COUNTRY);               // Страна, с которой работала MainActivity
            if(intent.hasExtra(MainActivity.INTENT_CURRENT_TOWN))
                currTown = intent.getStringExtra(MainActivity.INTENT_CURRENT_TOWN);                 // Последний город, для которого были сделаны запросы погоды
        }
        else {
            currCountry = savedInstanceState.getString(BUNDLE_KEY_CURRENT_COUNTRY);
            currTown = savedInstanceState.getString(BUNDLE_KEY_CURRENT_TOWN);
        }

        Log.d(TAG, "FindSettlementActivity: onCreate() currCountry = " + currCountry + " currTown = " + currTown);

        SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE); // Список всех стран достаем из SharedPreferences
        if(sharedPreferences.contains(MainActivity.APP_PREFERENCES_ALL_COUNTRIES))                                      // (это должно быть быстрее, чем из базы)
            allCountries = sharedPreferences.getStringSet(MainActivity.APP_PREFERENCES_ALL_COUNTRIES, new TreeSet<>());

        String[] countries = new String[allCountries.size()];       // Переводим список стран в массив для помещения в адаптер
        allCountries.toArray(countries);
        Arrays.sort(countries);

        ArrayAdapter<String> countriesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, countries);

        countriesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {                           // Слушатель для события выбора страны
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currCountry = countries[position];

                if (mainMenu != null)                                                   // Сворачиваем поле ввода префикса
                    mainMenu.findItem(R.id.search_menu_item).collapseActionView();

                if(view != null) {                                                      // Это нужно, чтобы список городов не создавался 2 раза (два раза приходит это сообщение,
                    Thread thread = new Thread(() -> createTownsList(currCountry));     // один раз view == null)
                    thread.start();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        countriesSpinner.setAdapter(countriesAdapter);
        position = countriesAdapter.getPosition(currCountry);
        countriesSpinner.setSelection(position);
    }

    private void createTownsList(String country) {

        long townListCreatingTime = System.currentTimeMillis();

        SQLiteDatabase database = new TownBaseHelper(this).getReadableDatabase();
        Cursor cursor = database.query(                                                             // Запрос к базе данных
                TownDbSchema.TownTable.NAME,                                                        // Координаты нужны, чтобы отличать одноименные города
                new String[] {TownDbSchema.TownTable.Cols.NAME, TownDbSchema.TownTable.Cols.ID, TownDbSchema.TownTable.Cols.LAT, TownDbSchema.TownTable.Cols.LON},
                TownDbSchema.TownTable.Cols.COUNTRY + " = ?",
                new String[] {country},
                null,
                null,
                null,
                null);

        Log.d(TAG, "Settlements in " + country + " = " + cursor.getCount() + " cursor loading time = " + (System.currentTimeMillis() - townListCreatingTime));

        handler.post(() -> {                                                                        // Выводим в активность информацию о количестве найденных городов
            if(cursor.getCount() > 6000)                                                            // Включаем progressbar при этом условии
                progressBar.setVisibility(View.VISIBLE);
            townsNumber.setText(String.format("%d", cursor.getCount()));
            townsNumberChosen.setText(String.format("%d", cursor.getCount()));
        });

        java.util.List<Town> townList = new ArrayList<>();

        cursor.moveToFirst();
        for (int i = 0; i < cursor.getCount(); i++)                                                 // Заполняем List списком городов
        {
            String name = cursor.getString(cursor.getColumnIndex(TownDbSchema.TownTable.Cols.NAME));
            String id = cursor.getString(cursor.getColumnIndex(TownDbSchema.TownTable.Cols.ID));
            double lat = cursor.getDouble(cursor.getColumnIndex(TownDbSchema.TownTable.Cols.LAT));
            double lon = cursor.getDouble(cursor.getColumnIndex(TownDbSchema.TownTable.Cols.LON));

            townList.add(new Town(id, name, currCountry, "", new Coord(lat, lon)));
            cursor.moveToNext();
        }
        cursor.close();
        database.close();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {                 // Если версия андроида >= 7
            collator = Collator.getInstance(new Locale(currCountry, currCountry));
            Collections.sort(townList, (o1, o2) -> collator.compare(o1.getName(), o2.getName()));   // сортируем города с учетом правил языка текущей страны,
        }
        else
            Collections.sort(townList, (o1, o2) -> o1.getName().compareTo(o2.getName()));           // если нет, то просто сортируем как придется.

        townsAdapter = new TownsAdapter(this, townList);                                    // Адаптер для RecyclerView списка городов
        fullTownList = townList;

        position = -1;
        if (currTown != null)                                                                       // Ищем позицию текущего города в списке
            for(int i = 0; i < townList.size(); i++) {
                if (townList.get(i).getName().startsWith(currTown)) {
                    position = i;
                    break;
                }
            }

        handler.post(() ->{                                                                         // Делаем в основном потоке:
            mainMenu.findItem(R.id.search_menu_item).setVisible(true);                              // Активируем пункт меню для поиска по префиксу
            townsView.setAdapter(townsAdapter);                                                     // Установка адаптера
            townsView.scrollToPosition(position);                                                   // Пролистываем RecyclerView до текущего города
            progressBar.setVisibility(View.INVISIBLE);                                              // Убираем прогрессбар
        });

        Log.d(TAG, "Town full list creating time: " + (System.currentTimeMillis() - townListCreatingTime));

        long borTime = System.currentTimeMillis();
        borRoot = new BorNode();                        // Сразу делаем бор из списка городов для быстрого отображения части городов по префиксу названия
        for(Town town : townList)
            borRoot.addTown(town, 0);
        Log.d(TAG, "Bor creating time: " + (System.currentTimeMillis() - borTime) + " ms");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {               // Сохраняем только текущую страну и город
        super.onSaveInstanceState(outState);
        outState.putString(BUNDLE_KEY_CURRENT_COUNTRY, currCountry);
        outState.putString(BUNDLE_KEY_CURRENT_TOWN, currTown);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search_menu, menu);
        mainMenu = menu;
        mainMenu.findItem(R.id.search_menu_item).setVisible(false);     // Пункт меню делаем невидимым до получения списка городов

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search_menu_item:
                handleSearch(item);         // Обработчик нажатия на пункт меню
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class BorNode{                              // Элемент струтуры данных "бор" - дерева с ветвями из букв, составляющих префиксы городов, лежащих ниже

        private Town town = null;               // Ссылка на элемент из townList, если в этой вершине префикс из букв всех ребер от вершины равен названию города
        private Map<Short, BorNode> nodes;      // Map для хранения пар <символ, ребро>

        BorNode() {
            nodes = new TreeMap<>();
        }

        public Town getTown() {
            return town;
        }

        public void setTown(Town town) {
            this.town = town;
        }

        void addTown(Town t, int pos) {  // Рекурсивная функция помещения города в бор
            if (pos == t.getName().length()) {
                this.town = t;                  // Если префикс равен названию города, помещаем ссылку а него в переменную town
                return;
            }
            if (pos < t.getName().length()) {   // Если нет, то спускаемся по ребру (или создаем его) на одну позицию вниз
                short val = (short) Character.toLowerCase(t.getName().charAt(pos));
                if (!nodes.containsKey(val)) {
                    nodes.put(val, new BorNode());
                }
                nodes.get(val).addTown(t, pos + 1);
            }
        }

        java.util.List<Town> getList(String pref) {                      // Возвращает список городов, начинающихся с pref

            java.util.List<Town> list = new ArrayList<>();
            BorNode firstNode = this;

            for(int i = 0; i < pref.length(); i++)                              // Сначала ищем корневую вершину для префикса
            {
                char lower = Character.toLowerCase(pref.charAt(i));             // Переводим в строчные
                short letter = (short)lower;
                firstNode = firstNode.nodes.get(letter);
                if(firstNode == null)
                    break;
            }
            if (firstNode != null)                                              // Далее вызов рекурсивной процедуры помещения всех дочерних городов в лист
                firstNode.addAllChildren(pref, list);

            return list;
        }

        private void addAllChildren(String pref, java.util.List<Town> list) {   // Рекурсивная процедура помещения всех дочерних городов в лист

            if (town != null)
                list.add(town);

            Set<Short> children = nodes.keySet();
            for(short n : children)
            {
                String currPref = pref + String.format("%c", (char)n);
                nodes.get(n).addAllChildren(currPref, list);
            }
        }
    }

    private void handleSearch(MenuItem item) {          // Управление вводом строки поиска и переход к SearchActivity с передачей этой строки

        SearchView searchView = (SearchView) item.getActionView();      // Получаем SearchView из Action View
        final EditText editText = searchView.findViewById(R.id.search_src_text);

        item.expandActionView();
        searchView.setQuery("", false);

        if (editText != null)
        {
            editText.addTextChangedListener(new TextWatcher() {     // Слушатель изменений в строке запроса
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {       // Изменение строки поиска - делаем новую выборку из бора
                                                                                                    // или снова ставим полный список городов при пустой строке
                    String prefix = editText.getText().toString();
                    if(!prefix.isEmpty())
                    {
                        long listTime = System.currentTimeMillis();
                        java.util.List<Town> townsChosen = borRoot.getList(prefix);
                        Log.d(TAG, "Town list creating time: " + (System.currentTimeMillis() - listTime) + " ms");

                        townsNumberChosen.setText("" + townsChosen.size());
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) { // Сортировка с учетом правил языка текущей строки - для версий Andoid >= 7
                            collator = Collator.getInstance(new Locale(currCountry, currCountry));
                            Collections.sort(townsChosen, (o1, o2) -> collator.compare(o1.getName(), o2.getName()));
                        }
                        else
                            Collections.sort(townsChosen, (o1, o2) -> o1.getName().compareTo(o2.getName()));
                        townsAdapter = new TownsAdapter(FindSettlementActivity.this, townsChosen);
                    }
                    else
                    {
                        townsNumberChosen.setText("" + fullTownList.size());
                        townsAdapter = new TownsAdapter(FindSettlementActivity.this, fullTownList);
                    }
                    townsView.setAdapter(townsAdapter);
                    //Log.d(TAG, "handleSearch(): " + editText.getText().toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }
}

//            if(size < name.length())
//            {
//                longest = name;
//                size = longest.length();
//            }
//            if (northernmost < lat) {
//                northernmost = lat;
//                northTown = townName;
//            }
//            if (southernmost > lat) {
//                southernmost = lat;
//                southTown = townName;
//            }
//            if (easternmost < lon) {
//                easternmost = lon;
//                eastTown = townName;
//            }
//            if (westernmost > lon) {
//                westernmost = lon;
//                westTown = townName;
//            }
