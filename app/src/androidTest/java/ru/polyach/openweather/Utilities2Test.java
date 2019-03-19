package ru.polyach.openweather;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.polyach.openweather.Model.Main;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Utilities2Test {

    Context context;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        context = null;
    }

    @Test
    public void getWeatherColor() {
        assertEquals(0xFF00FF00, Utilities.getWeatherColor(0));                                             // Зеленый
        assertEquals(0xFFFF0000, Utilities.getWeatherColor(1));                                             // Красный
        assertEquals(0xFF000000 + 0x00FF0000 + (0xFF + 0x8C)/2*0x100, Utilities.getWeatherColor(0.5f ));    // Посередине между желтым (0xFFFF00) и оранжевым (0xFF8C00)
    }

    @Test
    public void downloadFileTest() {
        String fileName = "01d.png";
        String fullFilename = context.getCacheDir().getAbsolutePath().toString() + "/" + fileName;
        boolean result = Utilities.downloadFile("http://openweathermap.org/img/w/" + fileName, fullFilename);
        assertEquals(true, result);
    }
}