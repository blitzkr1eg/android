package ro.asalajan.biletmaster.services.biletmaster;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

import org.joda.time.LocalDateTime;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ro.asalajan.biletmaster.model.Event;
import ro.asalajan.biletmaster.model.Location;
import ro.asalajan.biletmaster.model.Venue;
import ro.asalajan.biletmaster.parser.BiletMasterParser;
import ro.asalajan.biletmaster.gateways.HttpGateway;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.FuncN;

public class BiletMasterServiceImpl implements BiletMasterService {

    private static final String ROOT = "http://biletmaster.ro";
    private static final String LOCATIONS_URL = ROOT + "/ron/AllPlaces/Minden_helyszin";

    private final BiletMasterParser parser;
    private final HttpGateway httpGateway;
    private Comparator<Event> eventDateComparator;

    public BiletMasterServiceImpl(BiletMasterParser parser, HttpGateway httpGateway) {
        this.parser = parser;
        this.httpGateway = httpGateway;
        this.eventDateComparator = new Comparator<Event>() {
            @Override
            public int compare(Event lhs, Event rhs) {
                LocalDateTime left = lhs.getDateTime().orNull();
                LocalDateTime right = rhs.getDateTime().orNull();

                if (left == null && right == null) {
                    return 0;
                }
                if (left != null && right == null) {
                    return -1;
                }
                if (left == null && right != null) {
                    return 1;
                }
                return left.compareTo(right);
            }
        };
    }

    public Observable<List<Location>> getLocations() {
        return httpGateway.downloadWebPage(LOCATIONS_URL)
                .map(parseLocations);
    }

    public Observable<List<Location>> getDistinctLocations(final List<String> distinctLocations) {
        return httpGateway
                .downloadWebPage(LOCATIONS_URL)
                .map(parseLocations)
                .map(locations -> Multimaps.index(locations, groupBy(distinctLocations)))
                .map((ImmutableListMultimap<String, Location> nameToLocations) -> {
                    List<Location> locations = new ArrayList<>();
                    for (String name : nameToLocations.keySet()) {
                        locations.add(merge(name, nameToLocations.get(name)));
                    }
                    return locations;
                });
    }

    @NonNull
    private Function<Location, String> groupBy(final List<String> locationNameKeys) {
        return location -> {
            for (String key : locationNameKeys) {
                if (location.getLocation().toUpperCase().contains(key.toUpperCase())) {
                    return key;
                }
            }
            return location.getLocation();
        };
    }

    private Location merge(String newName, ImmutableList<Location> locations) {
        List<Venue> venues = new ArrayList<>();
        for(Location loc : locations) {
            venues.addAll(loc.getVenues());
        }
        return new Location(newName, venues);
    }

    public Observable<List<Event>> getEventsForLocation(Location location) {
        List<Observable<List<Event>>> events = new ArrayList<>();
        for (Venue v : location.getVenues()) {
            events.add(
                    Observable.just(v)
                            .flatMap(venue -> getEventsForVenue(venue))

             );
        }
        Log.d("biletService", "!!!!!!!!!!!! query-ing for " + location.getLocation());
        return Observable.zip(events, new FuncN<List<Event>>() {
            @Override
            public List<Event> call(Object... args) {
                List<Event> allEvents = new ArrayList<Event>();
                for (Object o : args) {
                    List<Event> le = (List<Event>) o;
                    allEvents.addAll(le);
                }
                Collections.sort(allEvents, eventDateComparator);
                return allEvents;
            }
        });

//        return Observable.from(location.getVenues())
//                .flatMap(venue -> getEventsForVenue(venue))
//                .flatMapIterable(list -> list)
//                .toList();

//        return Observable.from(location.getVenues())
//                .concatMapEager(venue -> getEventsForVenue(venue))
//                .concatMapIterable(list -> list)
//                .toList();
    }

    public Observable<List<Event>> getEventsForVenue(Venue venue) {

        return httpGateway.downloadWebPage(ROOT + venue.getUrl())
                .map(data -> parser.parseEvents(data))
                .map(ev -> {
                    for (Event e : ev) {
                        e.setVenue(venue);
                    }
                    return ev;
                });
                //.onErrorResumeNext(Observable.empty());
    }

    private Func1<? super InputStream, List<Location>> parseLocations =
        new Func1<InputStream, List<Location>>() {
            @Override
            public List<Location> call(InputStream inputStream) {
                return parser.parseLocations(inputStream);
            }
        };
}
