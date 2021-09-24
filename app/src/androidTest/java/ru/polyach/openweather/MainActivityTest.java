package ru.polyach.openweather;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.IdlingPolicies;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.action.ViewActions;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.support.test.rule.*;
import android.support.test.runner.AndroidJUnit4;
import android.view.Surface;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static android.support.test.espresso.contrib.RecyclerViewActions.scrollToPosition;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    CountingIdlingResource idlingResource;
    MainActivity activity;

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule(MainActivity.class);

    @Before
    public void setUp() throws Exception {
        IdlingPolicies.setIdlingResourceTimeout(180, TimeUnit.SECONDS);         // Изменяем таймаут IdlingResource на максимальное время заполнения базы данных
        IdlingPolicies.setMasterPolicyTimeout(180, TimeUnit.SECONDS);

        activity = activityRule.getActivity();
        idlingResource = new CountingIdlingResource("Data loading");        // Объект-семафор для блокировки выполнения тестов при длительных операциях в MainActivity
        activity.setCountingIdlingResource(idlingResource);                              // Передача объекта в активность

        int rotate = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotate) {
            case Surface.ROTATION_90:                                                    // Фиксируем текущую ориентацию на период теста
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case Surface.ROTATION_270:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
            default:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        //idlingResource = activity.getCountingIdlingResource();      // CountingIdlingResource - объект-семафор, устанавливаемый в MainActivity при выполнении длительных операций
        IdlingRegistry.getInstance().register(idlingResource);      // Регистрация CountingIdlingResource для ожидания окончания операции
    }

    @After
    public void tearDown() throws Exception {
        IdlingRegistry.getInstance().unregister(idlingResource);
        activity.setCountingIdlingResource(null);
    }

    @Test
    public void scrollForecast() throws Exception {
        for(int i = 0; i < 40; i++) {
            onView(withId(R.id.recycler_view)).perform(scrollToPosition(i));
            //Thread.sleep(10);
        }
    }

    @Test
    public void findSettlementActivityOpenAndCancel() throws Exception {
        onView(withId(R.id.find_item)).perform(click());
        Thread.sleep(1000);
        pressBack();
        Thread.sleep(1000);
    }

    @Test
    public void findSettlementActivityOpenChooseCountryCancel() throws Exception {
        onView(withId(R.id.find_item)).perform(click());
        Thread.sleep(2000);
        onData(equalTo("DE")).perform(click(), click());
        Thread.sleep(3000);
        onView(withId(R.id.recycler_view_towns)).perform(swipeUp());
        onView(withId(R.id.recycler_view_towns)).perform(swipeUp());
        onView(withId(R.id.recycler_view_towns)).perform(swipeUp());
        onView(withId(R.id.recycler_view_towns)).perform(swipeUp());
        onView(withId(R.id.recycler_view_towns)).perform(swipeUp());
        Thread.sleep(1000);
        pressBack();
        Thread.sleep(2000);
    }

    @Test
    public void chooseMagadan() throws Exception {
        String prefix = "magad";
        onView(withId(R.id.find_item)).perform(click());
        Thread.sleep(4000);
        onData(equalTo("RU")).perform(click(), click());
        Thread.sleep(2000);
        onView(withId(R.id.search_menu_item)).perform(click());
        //onView(withId(R.id.spinner_countries)).perform(actionOnItemAtPosition(100, click()));
        //onView(withId(R.id.recycler_view_towns)).perform(actionOnItemAtPosition(3736, click()));
        onView(withId(R.id.search_src_text)).perform(typeText(prefix), ViewActions.closeSoftKeyboard());
        Thread.sleep(500);
        onView(withId(R.id.recycler_view_towns)).perform(actionOnItemAtPosition(0, click()));
        onView(withId(R.id.city)).check(matches(withText(startsWith("Magad"))));
        Thread.sleep(3000);
        onView(withId(R.id.location_item)).perform(click());
        Thread.sleep(1000);
    }

    @Test
    public void countriesNumberTest() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Context appContext = InstrumentationRegistry.getTargetContext();
        SharedPreferences sharedPreferences = appContext.getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE);
        Set<String> countries = sharedPreferences.getStringSet(MainActivity.APP_PREFERENCES_ALL_COUNTRIES, null);

        assertEquals(247, countries.size());
    }

        /*
    private static Matcher<View> withAdaptedData(final Matcher<Object> dataMatcher) {
        return new TypeSafeMatcher<View>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("Adapter data ");
                dataMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                if (!(view instanceof AdapterView)) {
                    return false;
                }

                @SuppressWarnings("rawtypes")
                Adapter adapter = ((AdapterView) view).getAdapter();
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (dataMatcher.matches(adapter.getItem(i))) {
                        return true;
                    }
                }

                return false;
            }
        };
    }*/
}