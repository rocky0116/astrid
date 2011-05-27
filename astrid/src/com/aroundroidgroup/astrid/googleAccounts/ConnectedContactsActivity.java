/*
z * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aroundroidgroup.astrid.googleAccounts;

import java.util.Collections;
import java.util.List;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.aroundroidgroup.astrid.gpsServices.ContactsHelper;
import com.aroundroidgroup.astrid.gpsServices.ContactsHelper.idNameMail;
import com.timsu.astrid.R;

public class ConnectedContactsActivity extends ListActivity {
    public static final int SCAN_ID = Menu.FIRST;

    private AroundroidDbAdapter mDbHelper;
    private final PeopleRequestService prs = PeopleRequestService.getPeopleRequestService();
    private ContactsHelper conHel;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contactsf_list);
        mDbHelper = new AroundroidDbAdapter(this);
        mDbHelper.open();
        conHel = new ContactsHelper(getContentResolver());
        fillData();
        Toast.makeText(getApplicationContext(), "Hit scan button from menu to scan for friend in the contact list!", Toast.LENGTH_SHORT);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        //TODO externalize strings
        menu.add(0, SCAN_ID, 0, "Scan now!"); //$NON-NLS-1$
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case SCAN_ID:
            if (prs.isConnected()){
                scanContacts();
            }
            else{
                Toast.makeText(getApplicationContext(), "Not connected to the people location service!", Toast.LENGTH_SHORT);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    //assuming prs is connected
    private void scanContacts() {
        List<idNameMail> chINM = conHel.friendsWithGoogle();
        String[] peopleArr = new String[chINM.size()];
        int i =0;
        for (idNameMail idnm : chINM){
            peopleArr[i++] = idnm.mail;
        }
        //TODO - sychronized to be parralled with the GPSService
        //TODO - send and update only the ones that does not aleady exists
        //the list is sorted!
        List<FriendProps> lfp =  prs.getPeopleLocations(peopleArr, null);
        if (lfp!=null && lfp.size()>0){
            FriendProps exampleProps = new FriendProps();
            for (idNameMail idnm : chINM){
                exampleProps.setMail(idnm.mail);
                int index = Collections.binarySearch(lfp, exampleProps, FriendProps.getMailComparator());
                if (index>=0){
                    FriendProps findMe = lfp.get(index);
                    Cursor curMail = mDbHelper.fetchByMail(idnm.mail);
                    if (curMail!=null && curMail.moveToFirst()){
                        long l = curMail.getLong(0);
                        curMail.close();
                        mDbHelper.updatePeople(l, findMe.getDlat(), findMe.getDlon(), findMe.getTimestamp(), Long.parseLong(idnm.id));
                        curMail.close();
                    }
                    else{
                        //TODO change to one function
                        long rowId = mDbHelper.createPeople(idnm.mail, Long.parseLong(idnm.id));
                        mDbHelper.updatePeople(rowId, findMe.getDlat(), findMe.getDlon(), findMe.getTimestamp());
                    }

                }
            }
            fillData();
        }
        else{
            Toast.makeText(getApplicationContext(), "Scan faild, cannot connect to service", Toast.LENGTH_LONG);
        }



    }

    private void fillData() {
        /*
        Cursor cur = mDbHelper.fetchAllPeopleWContact();
        while(cur.moveToNext()){
            int index = cur.getColumnIndex(mDbHelper.KEY_CONTACTID);
            int rowID = cur.getInt(index);
            List<idNameMail> idNm = conHel.oneFriendWithGoogle(rowID);
            if (idNm!=null&&idNm.size()>0){

            }
        }
        */
        /*
        // Get all of the notes from the database and create the item list
        Cursor c = mDbHelper.fetchAllPeople(); //fetch all people with contact assosiated
        startManagingCursor(c);

        String[] from = new String[] { AroundroidDbAdapter.KEY_ROWID };
        int[] to = new int[] { R.id.text1 };

        // Now create an array adapter and set it to display using our row
        SimpleCursorAdapter notes =
            new SimpleCursorAdapter(this, R.layout.contactsf_row, c, from, to);
        setListAdapter(notes);
         */
        Toast.makeText(getApplicationContext(), "Scan was a Marvelous success!", Toast.LENGTH_LONG);

    }
}
