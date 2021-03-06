package ro.asalajan.biletmaster.presenter;

import android.support.annotation.NonNull;
import android.view.DragEvent;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ro.asalajan.biletmaster.model.Event;
import ro.asalajan.biletmaster.model.Location;
import ro.asalajan.biletmaster.model.Venue;
import ro.asalajan.biletmaster.presenters.Environment;
import ro.asalajan.biletmaster.presenters.EventsPresenter;
import ro.asalajan.biletmaster.services.biletmaster.BiletMasterHelper;
import ro.asalajan.biletmaster.services.biletmaster.BiletMasterService;
import ro.asalajan.biletmaster.services.biletmaster.BiletMasterServiceImpl;
import ro.asalajan.biletmaster.view.EventsView;
import ro.asalajan.biletmaster.view.NoInternetView;
import rx.Observable;
import rx.Scheduler;
import rx.android.plugins.RxAndroidPlugins;
import rx.android.plugins.RxAndroidSchedulersHook;
import rx.functions.Action0;
import rx.plugins.RxJavaPlugins;
import rx.plugins.RxJavaSchedulersHook;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.schedulers.TrampolineScheduler;

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestEventsPresenter {

    private EventsPresenter presenter;

    private BiletMasterService service;
    private Location location;
    private Location location2;

    private static TestScheduler mainThread = new TestScheduler();
    private static TestScheduler ioThread = new TestScheduler();
    private static TestScheduler computationThread = new TestScheduler();
    private static TestScheduler newThread = new TestScheduler();

    static {
        RxJavaPlugins.getInstance().registerSchedulersHook(new RxJavaSchedulersHook() {
            @Override
            public Scheduler getIOScheduler() {
                return ioThread;
            }

            @Override
            public Scheduler getComputationScheduler() {
                return computationThread;
            }

            @Override
            public Scheduler getNewThreadScheduler() {
                return newThread;
            }



        });

        RxAndroidPlugins.getInstance().registerSchedulersHook(new RxAndroidSchedulersHook() {
            @Override
            public Scheduler getMainThreadScheduler() {
                return mainThread;
            }


        });
    }

    private List<List<Location>> viewLocations;
    private List<List<Event>> viewEvents;

    Environment env;
    private boolean showedOffline, hiddenOffline, showedError;
    private Location selectedLocation;

//        @After
//    public void tearDown() {
//        RxAndroidPlugins.getInstance().reset();
//    }


    @Before
    public void setup() {
        service = mock(BiletMasterService.class);
        location = new Location("location1", newArrayList(
                new Venue("venue1", "venue1Url"))
        );
        location2 = new Location("location2", newArrayList(
                new Venue("venue2", "venue2Url"))
        );

        viewLocations = new ArrayList<>();
        viewEvents = new ArrayList<>();

        env = mock(Environment.class);
        showedOffline = false;
        hiddenOffline = false;
        showedError = false;
        when(env.isOnline()).thenReturn(Observable.just(Boolean.TRUE));
    }

    @Test
    public void getLocations() {
        when(service.getDistinctLocations(eq(BiletMasterHelper.DISTINCT_LOCATIONS)))
                .thenReturn(Observable.just(expectedLocations()));


        presenter = new EventsPresenter(env, service, BiletMasterHelper.DISTINCT_LOCATIONS);
        EventsView view = getEventsView();
        presenter.setView(view);

        mainThread.advanceTimeBy(1, TimeUnit.SECONDS);
        Assert.assertEquals(expectedLocations(), viewLocations.get(0));
        noMsgShowed();

    }

    private void noMsgShowed() {
        Assert.assertFalse("Error showed", showedError);
        Assert.assertFalse("Offline showed", showedOffline);
    }

    @Test
    public void getEventsForSelectedLocation() {
        when(service.getDistinctLocations(eq(BiletMasterHelper.DISTINCT_LOCATIONS)))
                .thenReturn(Observable.just(expectedLocations()));

        when(service.getEventsForLocation(eq(location)))
                .thenReturn(Observable.just(expectedEvents()));

        when(service.getEventsForLocation(eq(location2)))
                .thenReturn(Observable.just(expectedEvents2()));

        presenter = new EventsPresenter(env, service, BiletMasterHelper.DISTINCT_LOCATIONS);
        EventsView view = getEventsView();

        presenter.setView(view);

        mainThread.advanceTimeBy(1, TimeUnit.SECONDS);
//        mainThread.advanceTimeBy(1, TimeUnit.SECONDS);
//        mainThread.advanceTimeBy(1, TimeUnit.SECONDS);

        Assert.assertEquals(expectedEvents(), viewEvents.get(0));
        Assert.assertEquals(expectedEvents2(), viewEvents.get(1));

        noMsgShowed();
    }


    @Test
    public void givenNoInternetWhenGetLocationsShowOffline() {

        when(service.getDistinctLocations(eq(BiletMasterHelper.DISTINCT_LOCATIONS)))
                .thenReturn(Observable.create(subscriber -> {
                    subscriber.onError(new IOException("offline test error"));
                    subscriber.onNext(expectedLocations());
                }), Observable.just(expectedLocations()));

        when(env.isOnline()).thenReturn(Observable.just(Boolean.FALSE));

        presenter = new EventsPresenter(env, service, BiletMasterHelper.DISTINCT_LOCATIONS);
        EventsView view = getEventsView();

        presenter.setView(view);

        mainThread.advanceTimeBy(5, TimeUnit.SECONDS);

        Assert.assertTrue("Offline msg not showed", showedOffline);
        Assert.assertTrue("Offline msg not hidden", hiddenOffline);

//        Assert.assertEquals(expectedLocations(), viewLocations.get(0));
    }


    @Test
    public void givenNoInternetWhenGetEventsShowOffline() {

        when(service.getDistinctLocations(eq(BiletMasterHelper.DISTINCT_LOCATIONS))).thenReturn(Observable.just(newArrayList(location, location2)));

        when(service.getEventsForLocation(eq(location))).thenReturn(Observable.error(new IOException("offline test error")));

        when(env.isOnline()).thenReturn(Observable.just(Boolean.FALSE));

        presenter = new EventsPresenter(env, service, BiletMasterHelper.DISTINCT_LOCATIONS);
        EventsView view = getEventsView();

        presenter.setView(view);

        mainThread.advanceTimeBy(1, TimeUnit.SECONDS);

        Assert.assertTrue("Offline msg not showed", showedOffline);
        Assert.assertTrue("Offline msg not hidden", hiddenOffline);

       // Assert.assertEquals(Collections.emptyList(), viewEvents.get(0));
    }

    @Test
    public void givenParseErrorWhenGetEventsShowError() {

        when(service.getDistinctLocations(eq(BiletMasterHelper.DISTINCT_LOCATIONS))).thenReturn(Observable.just(newArrayList(location, location2)));

        when(service.getEventsForLocation(eq(location))).thenReturn(Observable.error(new IOException("offline")));

        when(env.isOnline()).thenReturn(Observable.just(Boolean.TRUE));

        presenter = new EventsPresenter(env, service, BiletMasterHelper.DISTINCT_LOCATIONS);
        EventsView view = getEventsView();

        presenter.setView(view);

        mainThread.advanceTimeBy(1, TimeUnit.SECONDS);

        Assert.assertEquals(Collections.emptyList(), viewEvents.get(0));

        Assert.assertTrue("Error msg not showed", showedError);
    }

    //TODO on online-offline state change, refresh view
    @Test
    public void givenOfflineWhenOnlineRefreshView() {
    }

    @Test
    public void givenOnlineWhenOnlineRefreshView() {

    }

    private List<Event> expectedEvents() {
        return newArrayList(
                new Event("event1", "artist1", null, null, false, null),
                new Event("event2", "artist2", null, null, false, null)
        );
    }

    private List<Event> expectedEvents2() {
        return newArrayList(
                new Event("event3", "artist3", null, null, false, null),
                new Event("event4", "artist4", null, null, false, null)
        );
    }

    @NonNull
    private List<Location> expectedLocations() {

        return newArrayList(
                location, location2
        );
    }

    @NonNull
    private EventsView getEventsView() {
        return new EventsView() {
            @Override
            public void setLocations(List<Location> locations) {
                TestEventsPresenter.this.viewLocations.add(locations);
            }

            @Override
            public void setEvents(List<Event> events) {
                TestEventsPresenter.this.viewEvents.add(events);
            }

            @Override
            public Observable<Location> getSelectedLocation() {
                return Observable.just(location, location2);
            }

            @Override
            public void showOffline() {
                showedOffline = true;
            }

            @Override
            public void hideOffline() {
                hiddenOffline = true;
            }

            @Override
            public void showError() {
                showedError = true;
            }

            @Override
            public NoInternetView getNoInternetView() {
                return new NoInternetView() {
                    @Override
                    public Observable<Object> retries() {
                        return Observable.just(new Object());
                    }

                    @Override
                    public void onViewCreate() {

                    }

                    @Override
                    public void onBackground() {

                    }

                    @Override
                    public void onForeground() {

                    }

                    @Override
                    public void onViewDestroy() {

                    }
                };
            }

            @Override
            public void onViewCreate() {

            }

            @Override
            public void onBackground() {

            }

            @Override
            public void onForeground() {

            }

            @Override
            public void onViewDestroy() {

            }
        };
    }
}
