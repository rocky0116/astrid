package com.aroundroidgroup.map;

import java.io.IOException;
import java.util.Map;

import org.json.JSONException;

import android.database.Cursor;

import com.todoroo.andlib.service.ContextManager;

public class mapFunctions {

    public static final int SNIPPET_ADDRESS = 0;
    public static final int SNIPPET_EMAIL = 1;
    public static final int TITLE_BUSINESS_NAME = 2;
    public static final int TITLE_SPECIFIC = 3;
    public static final int TITLE_TASK_NAME = 4;

    public static final int ALL_GOOD = 0;
    public static final int PARTIAL_GOOD = 1;
    public static final int ALL_BAD = -1;

    public static int[] addTagsToMap(AdjustedMap map, int id, String[] locationTypes, DPoint center, double radius, long taskID) {
        if (locationTypes.length == 0)
            return new int[0];
//        DPoint deviceLocation = map.getDeviceLocation();
        LocationsDbAdapter locDB = new LocationsDbAdapter(ContextManager.getContext());
        locDB.open();
        if (center == null)
            return new int[locationTypes.length];

        int i = 0;
        int[] feedback = new int[locationTypes.length];
        for (String type : locationTypes) {
//            if (map.isContains(id, type)) {
//                feedback[i] = 1;
//                continue;
//            }
            Map<String, DPoint> kindLocations = null;
            try {
                Cursor c = locDB.fetchByTypeComplex(type, center.toString(), (new Double(radius)).toString());
                if (c == null) {
                    kindLocations = Misc.googlePlacesQuery(type, center, radius);
                    if (!kindLocations.isEmpty()) {
                        feedback[i] = 1;
                        locDB.createType(type, center.toString(), radius, kindLocations);
                    }
                    else {
                        feedback[i] = 0;
                        continue;
                    }
                    /* running on all the tags (bank, post-office, ATM, etc...) */
                    for (Map.Entry<String, DPoint> p : kindLocations.entrySet()) {
                        String savedAddr = locDB.fetchByCoordinateAsString(p.getValue().toString());
                        if (savedAddr == null) {
                            savedAddr = Geocoding.reverseGeocoding(p.getValue());
                            if (savedAddr == null)
                                savedAddr = LocationsDbAdapter.DATABASE_ADDRESS_GEOCODE_FAILURE;
                            locDB.createTranslate(p.getValue().toString(), savedAddr);
                        }
                        map.addItemToOverlay(Misc.degToGeo(p.getValue()), p.getKey(),
                                type, ((savedAddr.equals(LocationsDbAdapter.DATABASE_ADDRESS_GEOCODE_FAILURE))?p.getValue().toString():savedAddr),
                                id, taskID, type);
                    }
                }
                else {
                    /* having a record of this type in DB */
                    while (!c.isAfterLast()) {
                        String coord = c.getString(c.getColumnIndex(LocationsDbAdapter.KEY_COORDINATES));
                        String savedAddr = locDB.fetchByCoordinateAsString(coord);
                        if (savedAddr == null) {
                            savedAddr = Geocoding.reverseGeocoding(new DPoint(coord));
                            if (savedAddr == null)
                                savedAddr = LocationsDbAdapter.DATABASE_ADDRESS_GEOCODE_FAILURE;
                            locDB.createTranslate(coord, savedAddr);
                        }
                        map.addItemToOverlay(Misc.degToGeo(new DPoint(coord)),
                                c.getString(c.getColumnIndex(LocationsDbAdapter.KEY_BUSINESS_NAME)),
                                type,
                                savedAddr,
                                id,
                                taskID,
                                type);
                        c.moveToNext();
                    }
                    feedback[i] = 1;
                    c.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            i++;
        }
        locDB.close();
        return feedback;
    }

    public static void addLocationSetToMap(AdjustedMap map, int id, DPoint[] locations, String title, long taskID) {
        LocationsDbAdapter locDB = new LocationsDbAdapter(ContextManager.getContext());
        locDB.open();
        for (DPoint d : locations) {
            String savedAddr = locDB.fetchByCoordinateAsString(d.toString());
            if (savedAddr == null) {
                try {
                    savedAddr = Geocoding.reverseGeocoding(d);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if (savedAddr == null)
                    locDB.createTranslate(d.toString(), LocationsDbAdapter.DATABASE_COORDINATE_GEOCODE_FAILURE);
                else locDB.createTranslate(d.toString(), savedAddr);
            }
            map.addItemToOverlay(Misc.degToGeo(d), title, d.toString(), savedAddr, id, taskID, null);
        }

    }

    public static void addPeopleToMap(AdjustedMap map, int id, String[] people, DPoint[] locations, long taskID) {
        for (int i = 0 ; i < people.length ; i++)
            if (!(locations[i] == null || locations[i].isNaN()) && people[i] != null)
                map.addItemToOverlay(Misc.degToGeo(locations[i]), people[i], people[i], people[i], id, taskID, null);
    }

    public static int degreeOfSuccess(int[] feedback) {
        int sum = 0;
        for (int i : feedback)
            sum += i;
        if (sum > 0 && sum == feedback.length && feedback.length > 0)
            return ALL_GOOD;
        else if (sum == 0 && feedback.length > 0)
            return ALL_BAD;
        return PARTIAL_GOOD;
    }

}
