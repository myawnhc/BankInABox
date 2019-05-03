package com.theyawns.domain.payments;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** A 'fake' location class to be used for some fraud detection rules.  The class encapsulates the
 *  idea that some cities are close to others without the need to create a full-blown GIS subsystem.
 */
public class Location implements Serializable {

    private static LinkedHashMap<String,Location> allLocations = new LinkedHashMap<>();

    private double latitude; // NOT USED
    private double longitude; // NOT USED
    private String city;
    private List<String> closeCities;

    private static Random random = new Random(33);

    private Location(String city) {
        this.city = city;
    }

    public static Location getLocation(String name) {
        return allLocations.get(name);
    }

    public static Location getRandom() {
        int index = random.nextInt(allLocations.size() - 1);
        Location l = allLocations.values().toArray(new Location[allLocations.size()])[index];
        return l;
    }


    public Location setCloseCities(List<String> close) {
        this.closeCities = close;
        return this;
    }

    public Location getCloseCity() {
        return allLocations.get(closeCities.get(random.nextInt(3)));
    }

    public boolean isNear(Location other)  {
        return city.equals(other.city) || closeCities.contains(other.city);
    }

    static {
        allLocations.put("Atlanta", new Location("Atlanta")
                .setCloseCities(Arrays.asList("Memphis", "Orlando", "Nashville" )));
        allLocations.put("Memphis", new Location("Memphis")
                .setCloseCities(Arrays.asList("Atlanta", "Orlando", "Nashville")));
        allLocations.put("Orlando", new Location("Orlando")
                .setCloseCities(Arrays.asList("Atlanta", "Memphis", "Nashville")));
        allLocations.put("Nashville", new Location("Nashville")
                .setCloseCities(Arrays.asList("Atlanta", "Memphis", "Orlando")));

        allLocations.put("London", new Location("London")
                .setCloseCities(Arrays.asList("Edinburgh", "Dublin", "Paris" )));
        allLocations.put("Edinburgh", new Location("Edinburgh")
                .setCloseCities(Arrays.asList("London", "Dublin", "Paris")));
        allLocations.put("Dublin", new Location("Dublin")
                .setCloseCities(Arrays.asList("London", "Edinburgh", "Paris")));
        allLocations.put("Paris", new Location("Paris")
                .setCloseCities(Arrays.asList("London", "Edinburgh", "Dublin")));

        allLocations.put("Istanbul", new Location("Istanbul")
                .setCloseCities(Arrays.asList("Athens", "Sofia", "Ankara" )));
        allLocations.put("Athens", new Location("Athens")
                .setCloseCities(Arrays.asList("Istanbul", "Sofia", "Ankara")));
        allLocations.put("Sofia", new Location("Sofia")
                .setCloseCities(Arrays.asList("Istanbul", "Athens", "Ankara")));
        allLocations.put("Ankara", new Location("Ankara")
                .setCloseCities(Arrays.asList("Istanbul", "Athens", "Sofia")));

        allLocations.put("San Francisco", new Location("San Francisco")
                .setCloseCities(Arrays.asList("Las Vegas", "Los Angeles", "Seattle" )));
        allLocations.put("Las Vegas", new Location("Las Vegas")
                .setCloseCities(Arrays.asList("San Francisco", "Los Angeles", "Seattle")));
        allLocations.put("Los Angeles", new Location("Los Angeles")
                .setCloseCities(Arrays.asList("San Francisco", "Las Vegas", "Seattle")));
        allLocations.put("Seattle", new Location("Seattle")
                .setCloseCities(Arrays.asList("San Francisco", "Las Vegas", "Los Angeles")));

    }
}
