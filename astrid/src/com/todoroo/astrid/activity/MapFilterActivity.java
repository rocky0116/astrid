package com.todoroo.astrid.activity;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.aroundroidgroup.astrid.googleAccounts.AroundroidDbAdapter;
import com.aroundroidgroup.astrid.googleAccounts.FriendProps;
import com.aroundroidgroup.locationTags.LocationService;
import com.aroundroidgroup.map.AdjustedMap;
import com.aroundroidgroup.map.AdjustedOverlayItem;
import com.aroundroidgroup.map.DPoint;
import com.aroundroidgroup.map.Focaccia;
import com.aroundroidgroup.map.Misc;
import com.aroundroidgroup.map.MyEventClassListener;
import com.aroundroidgroup.map.mapFunctions;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;
import com.timsu.astrid.R;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.Query;
import com.todoroo.astrid.core.SortHelper;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.data.TaskApiDao.TaskCriteria;
import com.todoroo.astrid.service.TaskService;

public class MapFilterActivity extends MapActivity {

    private double mRadius;
    private AdjustedMap mMapView;
    private Button mViewAll;
    private Set<String> mTypes;
    private AroundroidDbAdapter mPeopleDB;
    private List<String> mNullPeople;
    private Map<String, DPoint> mPeople;
    private final LocationService mLocationService = new LocationService();


    /* identifiers for the overlays in the mapView */
    private static final int SPECIFIC_OVERLAY = 1;
    private static final int TYPE_OVERLAY = 2;
    private static final int PEOPLE_OVERLAY = 3;


    private static final int MENU_SPECIFIC_GROUP = 1;
    private static final int MENU_KIND_GROUP = 65536;
    private static final int MENU_PEOPLE_GROUP = 1048576;
    private static final int MENU_TAPPED_GROUP = 16777216;

    /* overlays' names */
    private static final String OVERLAY_TYPE_NAME = "Type Location"; //$NON-NLS-1$
    private static final String OVERLAY_SPECIFIC_NAME = "Specific Location"; //$NON-NLS-1$
    private static final String OVERLAY_PEOPLE_NAME = "People Location"; //$NON-NLS-1$

    private int mLocationNumber;

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPeopleDB.close();
    }

    private class DeviceLocation extends AbstractAction {

        public DeviceLocation() {
            super(R.drawable.ic_menu_mylocation);
        }

        @Override
        public void performAction(View view) {
            Resources r = getResources();
            DPoint deviceLocation = mMapView.getDeviceLocation();
            if (deviceLocation != null)
                mMapView.getController().setCenter(Misc.degToGeo(deviceLocation));
            else {
                AlertDialog dialog = new AlertDialog.Builder(MapFilterActivity.this).create();
                dialog.setIcon(android.R.drawable.ic_dialog_alert);
                dialog.setTitle(R.string.AD_map_alert_dialog_title);
                dialog.setMessage("Device location is not available.");
                dialog.setButton(DialogInterface.BUTTON_POSITIVE, r.getString(R.string.AD_DLG_ok),
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dg, int which) {
                        return;
                    }
                });
                dialog.show();
            }
            return;
        }

    }
    /* @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@    */
    /* @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@    */


    private class ViewAll extends AbstractAction {

        public ViewAll() {
            super(R.drawable.ic_menu_list);
        }

        @Override
        public void performAction(View view) {
            if (!hasPlaces()) {
                AlertDialog dialog = new AlertDialog.Builder(MapFilterActivity.this).create();
                dialog.setIcon(android.R.drawable.ic_dialog_alert);
                dialog.setTitle("Information"); //$NON-NLS-1$
                dialog.setMessage("No locations for this task."); //$NON-NLS-1$
                dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", //$NON-NLS-1$
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dg, int which) {
                        return;
                    }
                });
                dialog.show();
            }
            else mViewAll.showContextMenu();
            return;
        }

    }

    public boolean hasPlaces() {
        return mMapView.hasPlaces() || mTypes.size() > 0 ;
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.setHeaderTitle("All Locations"); //$NON-NLS-1$
        int len = mMapView.getOverlaySize(SPECIFIC_OVERLAY);
        DPoint[] specCoords = mMapView.getAllByIDAsCoords(SPECIFIC_OVERLAY);
        String[] specAddrs = mMapView.getAllByIDAsAddress(SPECIFIC_OVERLAY);
        for (int i = 0 ; i < len ; i++) {
            if (specAddrs[i] != null)
                menu.add(MENU_SPECIFIC_GROUP, MENU_SPECIFIC_GROUP + i, Menu.NONE, specAddrs[i]);
            else menu.add(MENU_SPECIFIC_GROUP, MENU_SPECIFIC_GROUP + i, Menu.NONE, specCoords[i].toString());
        }
        int i = 0;
        for (String type : mTypes)
            menu.add(MENU_KIND_GROUP, MENU_KIND_GROUP + i++, Menu.NONE, type);
        for (Entry<String, DPoint> element : mPeople.entrySet()) {
            List<AdjustedOverlayItem> contactMail = mMapView.selectItemFromOverlayByExtrasAsAjustedItem(PEOPLE_OVERLAY, element.getKey());
            if (!contactMail.isEmpty()) {
                menu.add(MENU_PEOPLE_GROUP, contactMail.get(0).getUniqueID(), Menu.NONE, contactMail.get(0).getSnippet());
            }
        }
        i = 0;
        for (i = 0 ; i < mNullPeople.size() ; i++)
            menu.add(MENU_PEOPLE_GROUP, -1, Menu.NONE, mNullPeople.get(i));
        for (i = 0 ; i < mMapView.getTappedPointsCount() ; i++) {
            String addr = mMapView.getTappedItem(i).getAddress();
            if (addr == null)
                addr = Misc.geoToDeg(mMapView.getTappedItem(i).getPoint()).toString();
            menu.add(MENU_TAPPED_GROUP, MENU_TAPPED_GROUP + i, Menu.NONE, addr);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Intent intent = new Intent(this, Focaccia.class);

        intent.putExtra(Focaccia.READ_ONLY, Focaccia.READ_ONLY);
        switch (item.getGroupId()) {
        case MENU_SPECIFIC_GROUP:
            AdjustedOverlayItem specItem = mMapView.getOverlay(SPECIFIC_OVERLAY).getItem(item.getItemId() - MENU_SPECIFIC_GROUP);
            if (specItem.getTaskID() > 0) {

                /* getting task's title by its ID */
                TaskService taskService = new TaskService();
                TodorooCursor<Task> cursor = taskService.query(Query.select(Task.ID, Task.TITLE).where(Criterion.and(TaskCriteria.isActive(),Criterion.and(TaskCriteria.byId(specItem.getTaskID()),
                        TaskCriteria.isVisible()))).
                        orderBy(SortHelper.defaultTaskOrder()).limit(100));
                try {

                    Task task = new Task();
                    for (int i = 0; i < cursor.getCount(); i++) {
                        cursor.moveToNext();
                        task.readFromCursor(cursor);
                        intent.putExtra(Focaccia.TASK_NAME, cursor.getString(cursor.getColumnIndex(Task.TITLE.toString())));
                        break;
                    }
                } finally {
                    cursor.close();
                }
            }

            mMapView.getController().setCenter(specItem.getPoint());

            intent.putExtra(Focaccia.CMENU_EXTRAS, item.getItemId() - MENU_SPECIFIC_GROUP + ""); //$NON-NLS-1$
            if (mMapView.hasConfig(SPECIFIC_OVERLAY, Focaccia.SHOW_TITLE))
                intent.putExtra(Focaccia.SHOW_TITLE, specItem.getTitle().toString());
            if (mMapView.hasConfig(SPECIFIC_OVERLAY, Focaccia.SHOW_ADDRESS))
                intent.putExtra(Focaccia.SHOW_ADDRESS, (specItem.getAddress() == null) ? Misc.geoToDeg(specItem.getPoint()).toString() : specItem.getAddress());

            startActivity(intent);
            return true;
        case MENU_KIND_GROUP:
            /* getting task's title by its ID */
            TaskService taskService = new TaskService();
            TodorooCursor<Task> cursor = taskService.query(Query.select(Task.ID, Task.TITLE).where(Criterion.and(TaskCriteria.isActive(),
                    TaskCriteria.isVisible())).
                    orderBy(SortHelper.defaultTaskOrder()).limit(100));
            List<Long> hasThisType = new ArrayList<Long>();
            try {

                Task task = new Task();

                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToNext();
                    task.readFromCursor(cursor);

                    LocationService locationService = new LocationService();
                    String[] types = locationService.getLocationsByTypeAsArray(task.getId());
                    if (types != null) {
                        for (String s : types)
                            if (s.equals(item.getTitle())) {
                                hasThisType.add(task.getId());
                            }
                    }
                    break;
                }
                if (hasThisType.size() == 1) {
                    intent.putExtra(Focaccia.TASK_NAME, cursor.getString(cursor.getColumnIndex(Task.TITLE.toString())));
                }
                else {
                    intent.putExtra(Focaccia.TASK_NAME, "Multiple Tasks"); //$NON-NLS-1$
                }
            } finally {
                cursor.close();
            }

            GeoPoint closestType = mMapView.getPointWithMinimalDistanceFromDeviceLocation(TYPE_OVERLAY, item.getTitle().toString());
            if (closestType != null)
                mMapView.getController().setCenter(closestType);

            if (mMapView.hasConfig(TYPE_OVERLAY, Focaccia.SHOW_AMOUNT_BY_EXTRAS)) {
                int bla = mMapView.getItemsByExtrasCount(TYPE_OVERLAY, item.getTitle().toString());
                intent.putExtra(Focaccia.SHOW_AMOUNT_BY_EXTRAS, bla + ""); //$NON-NLS-1$
            }
            if (mMapView.hasConfig(TYPE_OVERLAY, Focaccia.SHOW_NAME))
                intent.putExtra(Focaccia.SHOW_NAME, OVERLAY_TYPE_NAME);
            if (mMapView.hasConfig(TYPE_OVERLAY, Focaccia.SHOW_TITLE))
                intent.putExtra(Focaccia.SHOW_TITLE, item.getTitle().toString());

            startActivity(intent);
            return true;
        case MENU_PEOPLE_GROUP:
            if (item.getItemId() == -1) {
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_NAME))
                    intent.putExtra(Focaccia.SHOW_NAME, OVERLAY_PEOPLE_NAME);
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_TITLE))
                    intent.putExtra(Focaccia.SHOW_TITLE, item.getTitle());
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_ADDRESS))
                    intent.putExtra(Focaccia.SHOW_ADDRESS, Focaccia.NO_ADDRESS_WARNING);
                startActivity(intent);
                return true;
            }
            AdjustedOverlayItem peopleItem = mMapView.getOverlay(PEOPLE_OVERLAY).getItem(item.getItemId());
            if (peopleItem.getTaskID() > 0) {

                /* getting task's title by its ID */

                cursor = (new TaskService()).query(Query.select(Task.ID, Task.TITLE).where(Criterion.and(TaskCriteria.isActive(),Criterion.and(TaskCriteria.byId(peopleItem.getTaskID()),
                        TaskCriteria.isVisible()))).
                        orderBy(SortHelper.defaultTaskOrder()).limit(100));
                try {

                    Task task = new Task();
                    for (int i = 0; i < cursor.getCount(); i++) {
                        cursor.moveToNext();
                        task.readFromCursor(cursor);
                        intent.putExtra(Focaccia.TASK_NAME, cursor.getString(cursor.getColumnIndex(Task.TITLE.toString())));
                        break;
                    }
                } finally {
                    cursor.close();
                }
            }
            DPoint da = mPeople.get(item.getTitle());
            if (da != null && !da.isNaN()) {
                mMapView.getController().setCenter(Misc.degToGeo(da));
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_NAME))
                    intent.putExtra(Focaccia.SHOW_NAME, OVERLAY_PEOPLE_NAME);
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_TITLE))
                    intent.putExtra(Focaccia.SHOW_TITLE, peopleItem.getTitle());
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_SNIPPET))
                    intent.putExtra(Focaccia.SHOW_SNIPPET, peopleItem.getSnippet());
                if (mMapView.hasConfig(PEOPLE_OVERLAY, Focaccia.SHOW_ADDRESS))
                    intent.putExtra(Focaccia.SHOW_ADDRESS, peopleItem.getAddress());

                startActivity(intent);
                return true;
            }
        default: return super.onContextItemSelected(item);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_filter_activity);

        mMapView = (AdjustedMap) findViewById(R.id.mapview);
        mMapView.makeUneditable();

        mPeopleDB = new AroundroidDbAdapter(this);
        mPeopleDB.open();

        mRadius = 100;

        mViewAll = (Button)findViewById(R.id.forcontextmenuonlybutton2);
        mViewAll.setVisibility(View.GONE);
        registerForContextMenu(mViewAll);


        DPoint deviceLocation = mMapView.getDeviceLocation();
        if (deviceLocation != null)
            mMapView.getController().setCenter(Misc.degToGeo(deviceLocation));

        /* enable zoom option */
        mMapView.setBuiltInZoomControls(true);

        mMapView.getController().setZoom(13);

        mMapView.createOverlay(true, SPECIFIC_OVERLAY, this.getResources().getDrawable(R.drawable.icon_specific), new String[] {
            Focaccia.SHOW_TITLE, Focaccia.SHOW_ADDRESS
        }, OVERLAY_SPECIFIC_NAME);
        mMapView.createOverlay(false, TYPE_OVERLAY, this.getResources().getDrawable(R.drawable.icon_type), new String[] {
            Focaccia.SHOW_NAME, Focaccia.SHOW_AMOUNT_BY_EXTRAS, Focaccia.SHOW_TITLE, Focaccia.SHOW_ADDRESS
        }, OVERLAY_TYPE_NAME);
        mMapView.createOverlay(true, PEOPLE_OVERLAY, this.getResources().getDrawable(R.drawable.icon_people), new String[] {
            Focaccia.SHOW_NAME, Focaccia.SHOW_ADDRESS, Focaccia.SHOW_SNIPPET
        }, OVERLAY_PEOPLE_NAME);

        final List<String> allList = new ArrayList<String>();

        TodorooCursor<Task> cursor = (new TaskService()).query(Query.select(Task.ID).where(Criterion.and(TaskCriteria.isActive(),
                TaskCriteria.isVisible())).
                orderBy(SortHelper.defaultTaskOrder()).limit(100));
        try {

            Task task = new Task();
            for (int k = 0; k < cursor.getCount(); k++) {
                cursor.moveToNext();
                task.readFromCursor(cursor);
                String[] types = mLocationService.getLocationsByTypeAsArray(task.getId());
                for (String t : types)
                    allList.add(t);
            }
        }
        finally {
            cursor.close();
        }
        mTypes = new LinkedHashSet<String>();
        cursor = (new TaskService()).query(Query.select(Task.ID).where(Criterion.and(TaskCriteria.isActive(),
                TaskCriteria.isVisible())).
                orderBy(SortHelper.defaultTaskOrder()).limit(100));
        try {

            Task task = new Task();
            for (int k = 0; k < cursor.getCount(); k++) {
                cursor.moveToNext();
                task.readFromCursor(cursor);
                Long taskID = task.getId();

                /* adding the locations by SPECIFIC */
                String[] specificLocations = mLocationService.getLocationsBySpecificAsArray(taskID);

                if (specificLocations != null) {
                    mLocationNumber += specificLocations.length;
                    /* converting from location written as string to DPoint */
                    DPoint[] points = new DPoint[specificLocations.length];
                    for (int i = 0 ; i < specificLocations.length ; i++)
                        points[i] = new DPoint(specificLocations[i]);
                    mapFunctions.addLocationSetToMap(mMapView, SPECIFIC_OVERLAY, points, "Specific Location", taskID); //$NON-NLS-1$
                }
                /* adding the locations by KIND */
                String[] tags = mLocationService.getLocationsByTypeAsArray(taskID);

                if (tags != null) {
                    for (String s : tags) {
                        mTypes.add(s);
                        int counter = 0;
                        for (String str : allList)
                            counter += s.equals(str) ? 1 : 0;
                        if (counter > 1)
                            mapFunctions.addTagsToMap(mMapView, TYPE_OVERLAY, new String[] { s }, mRadius, mapFunctions.MULTIPLE_TASKS_ID);
                        else mapFunctions.addTagsToMap(mMapView, TYPE_OVERLAY, new String[] { s }, mRadius, taskID);
                    }
                }
                mPeople = new HashMap<String, DPoint>();
                mNullPeople = new ArrayList<String>();

                /* Adding people that are related to the task */
                String[] people = mLocationService.getLocationsByPeopleAsArray(taskID);
                if (people != null) {
                    DPoint[] coords = new DPoint[people.length];
                    for (int i = 0 ; i < people.length ; i++) {
                        Cursor c = mPeopleDB.fetchByMail(people[i]);
                        if (c == null || !c.moveToFirst()) {
                            coords[i] = null;
                            mNullPeople.add(people[i]);
                            if (c != null)
                                c.close();
                            continue;
                        }
                        FriendProps fp = AroundroidDbAdapter.userToFP(c);
                        if (fp != null) {
                            Double lat = c.getDouble(c.getColumnIndex(AroundroidDbAdapter.KEY_LAT));
                            Double lon = c.getDouble(c.getColumnIndex(AroundroidDbAdapter.KEY_LON));
                            coords[i] = new DPoint(lat, lon);
                            mPeople.put(people[i], coords[i]);
                        }
                        else {
                            coords[i] = null;
                            mNullPeople.add(people[i]);
                        }
                        c.close();

                    }

                    mapFunctions.addPeopleToMap(mMapView, PEOPLE_OVERLAY, people, coords, taskID);
                }

            }
        } finally {
            cursor.close();
        }

        final ActionBar actionBar = (ActionBar) findViewById(R.id.actionbar);
        actionBar.setTitle("Places Nearby"); //$NON-NLS-1$

        actionBar.addAction(new ViewAll());
        actionBar.addAction(new DeviceLocation());

        mMapView.addEventListener(new MyEventClassListener() {

            @Override
            public void handleMyEventClassEvent(EventObject e) {
                TodorooCursor<Task> c = (new TaskService()).query(Query.select(Task.ID).where(Criterion.and(TaskCriteria.isActive(),
                        TaskCriteria.isVisible())).
                        orderBy(SortHelper.defaultTaskOrder()).limit(100));
                try {

                    Task task = new Task();
                    for (int k = 0; k < c.getCount(); k++) {

                        c.moveToNext();
                        task.readFromCursor(c);
                        String[] tags = mLocationService.getLocationsByTypeAsArray(task.getId());
                        for (String s : tags) {
                            int counter = 0;
                            for (String str : allList)
                                counter += s.equals(str) ? 1 : 0;
                            if (counter > 1)
                                mapFunctions.addTagsToMap(mMapView, TYPE_OVERLAY, new String[] { s }, mRadius, mapFunctions.MULTIPLE_TASKS_ID);
                            else mapFunctions.addTagsToMap(mMapView, TYPE_OVERLAY, new String[] { s }, mRadius, task.getId());
                        }

                    }

                } finally {
                    c.close();
                }

            }

        });

        mMapView.setZoomByAllLocations();
    }

    private final Handler mHan = new Handler();
    final int mDelayMillis = 10 * 1000;
    private final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            /* my code */
            mMapView.clearOverlay(PEOPLE_OVERLAY);
            mMapView.invalidate();
            TodorooCursor<Task> c = (new TaskService()).query(Query.select(Task.ID).where(Criterion.and(TaskCriteria.isActive(),
                    TaskCriteria.isVisible())).
                    orderBy(SortHelper.defaultTaskOrder()).limit(100));
            try {

                Task task = new Task();
                for (int k = 0; k < c.getCount(); k++) {
                    c.moveToNext();
                    String[] existedPeople = mLocationService.getLocationsByPeopleAsArray(task.getId());
                    if (existedPeople != null) {
                        for (String person : existedPeople) {
                            Cursor cur = mPeopleDB.fetchByMail(person);
                            if (cur == null) {
                                continue;
                            }
                            if (!cur.moveToFirst()) {
                                cur.close();
                                continue;
                            }
                            FriendProps fp = AroundroidDbAdapter.userToFP(cur);
                            if (fp != null) {
                                if (fp.isValid()) {
                                    GeoPoint gp = Misc.degToGeo(new DPoint(fp.getDlat(), fp.getDlon()));
                                    String savedAddr = mapFunctions.getSavedAddressAndUpdate(gp.getLatitudeE6(), gp.getLongitudeE6());
                                    mMapView.addItemToOverlay(gp, OVERLAY_PEOPLE_NAME, person, savedAddr, PEOPLE_OVERLAY, task.getId(), person);
                                }
                            }
                            cur.close();
                        }
                    }
                }
            } finally {
                c.close();
            }

            mMapView.updateDeviceLocation();

            mHan.postDelayed(this, mDelayMillis);
        }
    };

    private void setUITimer(){
        mHan.removeCallbacks(mUpdateTimeTask);
        mHan.postDelayed(mUpdateTimeTask, mDelayMillis);

    }

    @Override
    protected void onPause() {
        mPeopleDB.close();
        mHan.removeCallbacks(mUpdateTimeTask);
        super.onPause();
    }

    @Override
    protected void onResume() {
        mPeopleDB.open();
        setUITimer();
        super.onResume();
    }

}


