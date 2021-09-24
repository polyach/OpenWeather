package ru.polyach.openweather.Database;

public class TownDbSchema {
    public static final class TownTable {
        public static final String NAME = "towns";
        public static final class Cols {
            public static final String ID = "id";
            public static final String NAME = "name";
            public static final String COUNTRY = "country";
            public static final String LAT = "lat";
            public static final String LON = "lon";
        }
    }
}