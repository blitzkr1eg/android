package ro.asalajan.biletmaster.services;

import com.google.common.base.Optional;

import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

import ro.asalajan.biletmaster.model.Event;
import ro.asalajan.biletmaster.model.Location;
import ro.asalajan.biletmaster.model.Venue;
import rx.Observable;

public class CachedBiletMasterService implements BiletMasterService  {

    private BiletMasterService service;

    private DataCache cache;


    public CachedBiletMasterService(BiletMasterService service, DataCache cache) {
        this.service = service;
        this.cache = cache;
    }

    @Override
    public Observable<List<Location>> getLocations() {
        return service.getLocations();
    }

    @Override
    public Observable<List<Location>> getDistinctLocations(List<String> distinctLocations) {
        return service.getDistinctLocations(distinctLocations);
    }

    @Override
    public Observable<List<Event>> getEventsForLocation(Location location) {
        return Observable.defer(() -> {
            List<SerializableEvent> cachedEvents = (List<SerializableEvent>) cache.get(location.hashCode());
            if (cachedEvents != null) {
                return Observable.just(fromSerial(cachedEvents));
            }
            return service.getEventsForLocation(location)
                    .doOnNext(events -> cache.put(location.hashCode(), toSerial(events)));
        });
    }

    @Override
    public Observable<List<Event>> getEventsForVenue(Venue venue) {
        return service.getEventsForVenue(venue);
    }

    public class SerializableEvent {
        private String name;
        private String artist;
        private String room;
        private String datetime;

        public SerializableEvent(String name, String artist, String room, String datetime) {
            this.name = name;
            this.artist = artist;
            this.room = room;
            this.datetime = datetime;
        }

        public String getName() {
            return name;
        }

        public String getArtist() {
            return artist;
        }

        public String getRoom() {
            return room;
        }

        public String getDatetime() {
            return datetime;
        }
    }

    private List<SerializableEvent> toSerial(List<Event> events) {
        List<SerializableEvent> serializableEvents = new ArrayList<>();
        for (Event e: events) {
            serializableEvents.add(fromEvent(e));
        }
        return serializableEvents;
    }

    private List<Event> fromSerial(List<SerializableEvent> serializableEvents) {
        List<Event> events = new ArrayList<>();
        for (SerializableEvent e: serializableEvents) {
            events.add(fromSerialized(e));
        }
        return events;
    }

    private SerializableEvent fromEvent(Event e) {
        LocalDateTime dateTime = e.getDateTime().orNull();
        return new SerializableEvent(e.getName(), e.getArtist(), e.getRoom(), dateTime == null ? null : dateTime.toString());
    }

    private Event fromSerialized(SerializableEvent e) {
        LocalDateTime dateTime = null;
        if (e.getDatetime() != null) {
            dateTime = LocalDateTime.parse(e.getDatetime());
        }
        return new Event(e.getName(), e.getArtist(), e.getRoom(), Optional.fromNullable(dateTime), false, null);
    }

}