package ru.polyach.openweather;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class Utilities1Test {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void findLastBraceTest() {
        String str1 = "{}";
        String str2 = "{\"id\": 707860,\"name\": \"Hurzuf\",\"country\": \"UA\",\"coord\": {\"lon\": 34.283333,\"lat\": 44.549999}},{\"id\": 519188,\"name\": \"Novinki\",\"country\": \"RU\",\"coord\": {\"lon\": 37.666668,\"lat\": 55.683334}}";
        String str3 = "";

        assertEquals(str1.lastIndexOf('}'), Utilities.findLastBrace(new StringBuilder(str1)));
        assertEquals(str2.lastIndexOf('}'), Utilities.findLastBrace(new StringBuilder(str2)));
        assertEquals(-1, Utilities.findLastBrace(new StringBuilder(str3)));
    }
}