package com.todoroo.astrid.activity;

import java.io.IOException;
import java.util.List;

import org.json.JSONException;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.aroundroidgroup.map.AdjustedMap;
import com.aroundroidgroup.map.AdjustedOverlayItem;
import com.aroundroidgroup.map.AsyncAutoComplete;
import com.aroundroidgroup.map.DPoint;
import com.aroundroidgroup.map.Focaccia;
import com.aroundroidgroup.map.Geocoding;
import com.aroundroidgroup.map.Misc;
import com.google.android.maps.MapActivity;
import com.timsu.astrid.R;
import com.todoroo.andlib.service.ContextManager;

public class SpecificMapLocation extends MapActivity {


    public static final String SPECIFIC_POINTS = "SpecificMapLocation"; //$NON-NLS-1$
    public static final String SPECIFIC_POINTS_SECOND = "SpecificMapLocation2"; //$NON-NLS-1$
    public static final int FOCACCIA_RESULT_CODE = 1;
    public static final int FOCACCIA_RESULT_CODE_BACK_PRESSED = 2;

    private static final int MENU_SPECIFIC_GROUP = 1;
    private static final int MENU_KIND_GROUP = 65536;
    private static final int MENU_PEOPLE_GROUP = 1048576;

    private long taskID;
    private AdjustedMap mapView;

    private Thread previousThread = null;
    private static ArrayAdapter<String> adapter;

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    public static void updateSuggestions(List<String> lst) {
        adapter.clear();
        for (String s : lst)
            adapter.add(s);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      menu.setHeaderTitle("All Locations"); //$NON-NLS-1$
      String[] specificAsAddress = mapView.getTappedAddress();
      DPoint[] specificAsCoords = mapView.getTappedCoords();
      for (int i = 0 ; i < specificAsAddress.length ; i++)
          menu.add(MENU_SPECIFIC_GROUP, MENU_SPECIFIC_GROUP + i, Menu.NONE, specificAsCoords[i].toString());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      switch (item.getGroupId()) {
      case MENU_SPECIFIC_GROUP:
          mapView.getController().setCenter(Misc.degToGeo(new DPoint(item.getTitle().toString())));
          Intent intent = new Intent(ContextManager.getContext(), Focaccia.class);
          AdjustedOverlayItem mItem = mapView.getTappedItem(item.getItemId() - MENU_SPECIFIC_GROUP);
          String[] sentData = new String[6];
          sentData[0] = item.getItemId() - MENU_SPECIFIC_GROUP + ""; //$NON-NLS-1$
          sentData[1] = item.getTitle().toString();
          sentData[2] = mItem.getTitle();
          sentData[3] = mItem.getSnippet();
          sentData[4] = mItem.getAddress();
          sentData[5] = "1"; // can be removed //$NON-NLS-1$

          intent.putExtra(Focaccia.SOURCE_SPECIFICMAP, sentData);
          this.startActivityForResult(intent, 1);
          return true;
      case MENU_KIND_GROUP:
          return true;
      case MENU_PEOPLE_GROUP:
          mapView.getController().setCenter(Misc.degToGeo(new DPoint(item.getTitle().toString())));
          return true;
      default: return super.onContextItemSelected(item);
      }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.specific_map);

        mapView = (AdjustedMap) findViewById(R.id.mapview);

        /* allowing adding of location by tapping on the map */
        mapView.enableAddByTap();

        /* disabling the feature that shows the device location on the map */
        mapView.removeDeviceLocation();

        final AutoCompleteTextView textView = (AutoCompleteTextView) findViewById(R.id.specificAddress);
        adapter = new ArrayAdapter<String>(SpecificMapLocation.this, R.layout.search_result_list, new String[0]);
        textView.setAdapter(adapter);

        textView.setOnKeyListener(new View.OnKeyListener() {

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (previousThread != null) {
                    if (previousThread.isAlive())
                        previousThread.destroy();
                    previousThread = null;
                }
                previousThread = new Thread(new AsyncAutoComplete(textView.getText().toString()));
                previousThread.run();
                return false;
            }
        });





        /* getting the data from the calling activity. */
        /* this data contains the taskID as first object in the array and the points */
        /* that already added as specific locations as the other array elements. */
        Bundle bundle = getIntent().getExtras();
        String[] existedSpecificLocations = bundle.getStringArray(SPECIFIC_POINTS);
        taskID = Long.parseLong(existedSpecificLocations[0]);

        mapView.associateMapWithTask(taskID);
        for (int i = 1 ; i < existedSpecificLocations.length ; i++) {
            DPoint d = new DPoint(existedSpecificLocations[i]);
            if (d.isNaN())
                continue;
            String address = null;
            try {
                address = Geocoding.reverseGeocoding(d);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (address == null)
                address = new String("Specific Location"); //$NON-NLS-1$
            mapView.addTappedLocation(Misc.degToGeo(d), "Specific Location", address); //$NON-NLS-1$
        }

        Button viewAll = (Button)findViewById(R.id.viewAll);
        registerForContextMenu(viewAll);
        if (mapView.getTappedPointsCount() == 0) {
            viewAll.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Toast.makeText(SpecificMapLocation.this, "No locations for this task", Toast.LENGTH_LONG).show();
                }
            });
            viewAll.setOnLongClickListener(new View.OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(SpecificMapLocation.this, "No locations for this task", Toast.LENGTH_LONG).show();
                    return false;
                }
            });
        }


        /* getting the task by the taskID that has been extracted from the Intent */
//        TaskService taskService = new TaskService();
//        TodorooCursor<Task> cursor = taskService.query(Query.select(Task.TITLE).
//                where(MetadataCriteria.byTask(taskID)).
//                orderBy(SortHelper.defaultTaskOrder()).limit(100));
//        try {
//            Toast.makeText(this, cursor.getCount() + " results", Toast.LENGTH_LONG).show();
//            Task task = new Task();
//            cursor.moveToNext();
//            task.readFromCursor(cursor);
//        } finally {
//            cursor.close();
//        }

        /* setting a headline with the task's title */
//        TextView title = (TextView)findViewById(R.id.takeTitle);

        /* enable zoom option */
        mapView.setBuiltInZoomControls(true);
        mapView.getController().setZoom(13);

        if (myService.getLastUserLocation() != null){

            /* Centralizing the map to the last known location of the device */
            mapView.getController().setCenter(Misc.locToGeo(myService.getLastUserLocation()));

        }

        final EditText address = (EditText)findViewById(R.id.specificAddress);

        Button addressButton = (Button)findViewById(R.id.specificAddAddressButton);
        addressButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                DPoint d = null;
                try {
                    d = Geocoding.geocoding(address.getText().toString());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if (d != null) {
                    String address = null;
                    try {
                        address = Geocoding.reverseGeocoding(d);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (address == null)
                        address = new String("Specific Location"); //$NON-NLS-1$
                        mapView.addTappedLocation(Misc.degToGeo(d), "Specific Location", address); //$NON-NLS-1$
                        mapView.invalidate();
                }
                else Toast.makeText(SpecificMapLocation.this, "Address not found!", Toast.LENGTH_LONG).show(); //$NON-NLS-1$
            }
        });

        address.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
               address.setText(""); //$NON-NLS-1$
               address.setOnClickListener(null);
            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == FOCACCIA_RESULT_CODE) {
            Bundle bundle = data.getExtras();
            Toast.makeText(this, "before there were " + mapView.getAllPointsCount() + " points", Toast.LENGTH_LONG).show(); //$NON-NLS-1$ //$NON-NLS-2$
            mapView.removeTappedLocation(Integer.parseInt(bundle.getString(Focaccia.SOURCE_ADJUSTEDMAP)));
            mapView.invalidate();
            Toast.makeText(this, "after there are " + mapView.getAllPointsCount() + " points", Toast.LENGTH_LONG).show(); //$NON-NLS-1$ //$NON-NLS-2$
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (resultCode == FOCACCIA_RESULT_CODE_BACK_PRESSED) {
            Toast.makeText(this, "no deletion", Toast.LENGTH_LONG).show(); //$NON-NLS-1$
            String[] receivedData = data.getExtras().getStringArray(Focaccia.SOURCE_ADJUSTEDMAP);
            if (receivedData[1] != null)
                mapView.getOverlay(AdjustedMap.SPECIFIC_OVERLAY_UNIQUE_NAME).getItem(Integer.parseInt(receivedData[0])).setLocationAddress(receivedData[1]);
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    private void saveAndQuit() {
        /* WHAT IS SENT BACK: array which the first part of it contains the location and second part
         *                    of it contains the addresses (null if certain location has never been
         *                    reversed geocoded. location at position k in the array has its address
         *                    at position 2k in the array. */
        Intent intent = new Intent();
        int pointsCount = mapView.getAllPointsCount();
        String[] classOvelrays = new String[pointsCount * 2];

        DPoint[] allPoints = mapView.getAllPoints();
        for (int i = 0 ; i < pointsCount ; i++)
            classOvelrays[i] = allPoints[i].toString();
        String[] allAddresses = mapView.getAllAddresses();
        for (int i = 0 ; i < pointsCount ; i++)
            classOvelrays[pointsCount + i] = allAddresses[i];

        intent.putExtra(SPECIFIC_POINTS_SECOND, classOvelrays);
        setResult(TaskEditActivity.SPECIFIC_LOCATION_MAP_RESULT_CODE, intent);
        SpecificMapLocation.this.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Toast.makeText(this, "press back", Toast.LENGTH_LONG).show(); //$NON-NLS-1$
            saveAndQuit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
