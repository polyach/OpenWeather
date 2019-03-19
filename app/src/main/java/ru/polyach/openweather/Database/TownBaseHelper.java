package ru.polyach.openweather.Database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;

import ru.polyach.openweather.Database.TownDbSchema.TownTable;

public class TownBaseHelper extends SQLiteOpenHelper {

    private static final int VERSION = 1;
    private static final String DATABASE_NAME = "townBase.db";

    public TownBaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("create table " + TownTable.NAME + "(" +
                " _id integer primary key autoincrement, " +
                TownTable.Cols.ID + ", " +
                TownTable.Cols.NAME + ", " +
                TownTable.Cols.COUNTRY + ", " +
                TownTable.Cols.LAT  + ", " +
                TownTable.Cols.LON +
                ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
