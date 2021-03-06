package com.aroundroidgroup.astrid.googleAccounts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aroundroidgroup.astrid.gpsServices.ContactsHelper;
import com.aroundroidgroup.astrid.gpsServices.GPSService;
import com.aroundroidgroup.locationTags.LocationService;
import com.aroundroidgroup.map.DPoint;
import com.aroundroidgroup.map.Geocoding;
import com.timsu.astrid.R;

public class ManageContactsActivity extends ListActivity{

    public static final String PEOPLE_BACK = "peopleBack"; //$NON-NLS-1$


    private static final int PICKCONTACT = 1;

    private static final int DIALOG_MAIL_METHOD = 0;
    private static final int DIALOG_ALREADY_SCANNED = 2;
    private static final int DIALOG_HURRAY = 3;
    private static final int DIALOG_HURRAY2 = 4;
    private static final int DIALOG_NOT_CONNECTED = 5;
    private static final int DIALOG_ALREADY_FOUND = 6;

    private AroundroidDbAdapter mDbHelper;

    private Long taskId;

    public final static String taskIDSTR = "taskID"; //$NON-NLS-1$
    public final static String peopleArraySTR = "peopleArrrr"; //$NON-NLS-1$

    private final PeopleRequestService prs = PeopleRequestService.getPeopleRequestService();

    private static boolean alreadyScannedSometime = false;

    public static synchronized boolean getAlreadyScannedSometime(Boolean bool){
        boolean res = alreadyScannedSometime;
        if (bool!=null){
            alreadyScannedSometime = bool;
        }
        return res;
    }

    private ContactsHelper conHel;

    private String[] originalPeople;

    private final Set<String> peopleHashSet =  Collections.synchronizedSet(new LinkedHashSet<String>());

    private final LocationService myLocationService = new LocationService();

    public void startContactActivity(long contactId){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactId));
        intent.setData(uri);
        this.startActivity(intent);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Get the item that was clicked
        Object o = this.getListAdapter().getItem(position);
        createViewContactDialog((FriendPropsWithContactId) o).show();
    }

    private void pickContact() {
        // Create an intent to "pick" a contact, as defined by the content provider URI
        Intent intent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
        startActivityForResult(intent, PICKCONTACT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // If the request went well (OK) and the request was PICK_CONTACT_REQUEST
        if (resultCode == Activity.RESULT_OK && requestCode == PICKCONTACT) {
            // Perform a query to the contact's content provider for the contact's name
            Cursor cursor = getContentResolver().query(data.getData(),
                    new String[] {Contacts._ID,Contacts.DISPLAY_NAME}, null, null, null);
            if (cursor.moveToFirst()) { // True if the cursor is not empty
                //TODO make better dont call getFriends
                Set<Entry<String,Long>> es = conHel.getFriends(cursor);
                cursor.moveToFirst();
                String[] mails = new String[es.size()];
                int i =0;
                for (Entry<String,Long> en : es){
                    mails[i++] = en.getKey();
                }
                int columnIndex = cursor.getColumnIndex(Contacts.DISPLAY_NAME);
                String name = cursor.getString(columnIndex);
                int idcol = cursor.getColumnIndex(Contacts._ID);
                long id = (cursor.getLong(idcol));
                // Do something with the selected contact's name...

                //if not names dont create this dialog
                if (mails.length>0){
                    createFriendMailsDialog(name,mails,id).show();
                }else{
                    Toast.makeText(ManageContactsActivity.this, "This contact has no email addresses!", Toast.LENGTH_LONG).show();
                }

            }
            cursor.close();
        }
    }

    private Dialog createFriendMailsDialog(String contactName, final String[] mails,final long contactId){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose associated mail - "+ contactName);
        builder.setSingleChoiceItems(mails, 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean ok = true;
                Cursor cur = mDbHelper.fetchByMail(mails[which]);
                if (cur==null){
                    ok = false;
                }else{
                    if (!cur.moveToFirst()){
                        //record not found
                        mDbHelper.createPeople(mails[which],contactId);
                    }
                    else {
                        Long currentcontactId = cur.getLong(cur.getColumnIndex(AroundroidDbAdapter.KEY_CONTACTID));
                        if (currentcontactId == -2){
                            //record found with no contact id.
                            //UPDATE A NEW CONTACT ID
                            Long oldRecordId = cur.getLong(cur.getColumnIndex((AroundroidDbAdapter.KEY_ROWID)));
                            mDbHelper.updatePeople(oldRecordId, contactId);
                        }
                        else if  (currentcontactId != contactId){
                            ok = false;
                        }
                    }
                    cur.close();
                }
                if (ok){
                    dialog.dismiss();
                    new ScanOneFriendTask().execute(new String[]{mails[which]});
                }
                else{
                    Toast.makeText(ManageContactsActivity.this, "This mail address is already associated with another contact or other error!", Toast.LENGTH_LONG).show();
                }

            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        }).setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }

    private Dialog createViewContactDialog(final FriendPropsWithContactId fpwci) {
        final Resources r = getResources();
        final String contactDisplayName = (fpwci.getContactId()>=0?conHel.oneDisplayName(fpwci.getContactId())
                :null);

        StringBuilder sb = new StringBuilder();

        sb.append(r.getString(R.string.AD_ace_mail)+" ").append(fpwci.getMail()); //$NON-NLS-1$

        if (contactDisplayName!=null){
            sb.append("\n"+r.getString(R.string.AD_ace_name)+" ").append(contactDisplayName); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (fpwci.isValid()){
            String address = null;
            try {
                address = Geocoding.reverseGeocoding(new DPoint(fpwci.getDlat(), fpwci.getDlon()));
                sb.append("\nAddress: "+address);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (address==null){
                sb.append("\nLatitude: ").append(fpwci.getLat()).append("\nLongitude: " + fpwci.getLat());
            }
        }else{
            sb.append("\n"+r.getString(R.string.AD_ace_unregistered)); //$NON-NLS-1$
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(this);


        builder.setMessage(sb.toString())
        .setTitle(R.string.AD_ace_title)
        .setNeutralButton(R.string.AD_ace_contact_card, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if (contactDisplayName!=null){
                    startContactActivity(fpwci.getContactId());
                }
                else{
                    Toast.makeText(ManageContactsActivity.this, R.string.AD_no_contact_info, Toast.LENGTH_LONG).show();
                }
            }
        });
        AlertDialog alert = builder.create();
        return alert;

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDbHelper.close();
        GPSService.lockDeletes(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.people_menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.peoplelocation_menu_mail:
            if (!prs.isConnected()){
                //if not connected prompt connection
                showDialog(DIALOG_NOT_CONNECTED);
            }else{
                showDialog(DIALOG_MAIL_METHOD);
            }
            break;
        case R.id.peopleLocation_menu_contacts:
            if (!prs.isConnected()){
                //if not connected prompt connection
                showDialog(DIALOG_NOT_CONNECTED);
            }
            else{
                pickContact();
            }
            //TODO add another button
            /*
            else if (!getAlreadyScannedSometime(null)){
                new ScanContactsTask().execute(new Void[0]);
            } else {
                showDialog(DIALOG_ALREADY_SCANNED);
            }
             */
            break;
        case R.id.peoplelocation_menu_login:
            Intent intent = new Intent(ManageContactsActivity.this, PeopleLocationPreferneces.class);
            startActivity(intent);
            break;
        }


        return super.onOptionsItemSelected(item);
    }

    private Dialog createNotConnectedDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.AD_DLG_connecting_text)
        .setTitle(R.string.AD_DLG_connecting_title)
        .setPositiveButton(R.string.AD_DLG_yes_recommanded, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //directs to connection screen here
                Intent intent = new Intent(ManageContactsActivity.this, PeopleLocationPreferneces.class);
                startActivity(intent);
            }
        })
        .setNegativeButton(R.string.DLG_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        return alert;

    }

    private Dialog createNoFriendsDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.AD_DLG_friendless_title);
        builder.setMessage(R.string.AD_DLG_friendless_text);
        builder.setNeutralButton(R.string.AD_DLG_ok,  new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //do nothing
            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }


    private Dialog createHurrayDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.AD_DLG_hurray);
        builder.setMessage(R.string.AD_registered_added);
        builder.setNeutralButton(R.string.AD_DLG_ok,  new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //do nothing
            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }

    private Dialog createHurray2Dialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.AD_DLG_hurray);
        builder.setMessage(R.string.AD_unregistered_added);
        builder.setNeutralButton(R.string.AD_DLG_ok,  new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //do nothing
            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }

    private Dialog createAlreadyAddedDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.AD_DLG_friend_already_exist_title);
        builder.setMessage(R.string.AD_DLG_friend_already_exist_text);
        builder.setNeutralButton(R.string.AD_DLG_ok,  new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                //do nothing
            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }

    private Dialog createContactsDialog(){
        final Resources r = getResources();
        Cursor cur = mDbHelper.fetchAllPeopleWContactRegistered();
        //TODO deal with cur error
        int numberOfFriends = cur.getCount();

        //if no friends create ok dialog. if has friends create list dialog.
        if (numberOfFriends<=0){
            cur.close();
            ////GPSService.lockDeletes(false);
            return createNoFriendsDialog();
        }
        final ArrayList<String> mailList = new ArrayList<String>(numberOfFriends);
        ArrayList<String> displayMailList = new ArrayList<String>(numberOfFriends);
        final boolean[] tickArray = new boolean[(numberOfFriends)];
        if (cur.moveToFirst()){
            int i =0;
            do{
                String mail = cur.getString(cur.getColumnIndex(AroundroidDbAdapter.KEY_MAIL));
                mailList.add(mail);
                if (peopleHashSet.contains(mail)){
                    tickArray[i] = true;
                }
                else{
                    tickArray[i] = false;
                }
                i++;
                Long contactID = cur.getLong(cur.getColumnIndex(AroundroidDbAdapter.KEY_CONTACTID));
                String displayName = conHel.oneDisplayName(contactID);
                if (displayName==null){
                    displayName = r.getString(R.string.AD_no_contact_info);
                }
                displayMailList.add(displayName + "\n(" + mail + ")"); //$NON-NLS-1$ //$NON-NLS-2$
            }while (cur.moveToNext());
        }
        cur.close();


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.AD_pick_contacts);
        builder.setMultiChoiceItems(displayMailList.toArray(new String[0]), tickArray, new DialogInterface.OnMultiChoiceClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                tickArray[which] = isChecked;

            }
        }).setPositiveButton(R.string.AD_DLG_add_all, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (int i =0 ; i<tickArray.length ; i++){
                    if (tickArray[i]){
                        peopleHashSet.add(mailList.get(i));
                    }
                    else{
                        peopleHashSet.remove(mailList.get(i));
                    }
                }
                //GPSService.lockDeletes(false);
                fillData();

            }
        }).setNegativeButton(R.string.AD_DLG_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        }).setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                //GPSService.lockDeletes(false);

            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }

    private boolean friendInList(String friendMail){
        return peopleHashSet.contains(friendMail);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
        case DIALOG_HURRAY:
            dialog = createHurrayDialog();
            break;
        case DIALOG_MAIL_METHOD:
            dialog = createAddDialog();
            break;
        case DIALOG_NOT_CONNECTED:
            dialog = createNotConnectedDialog();
            break;
        case DIALOG_ALREADY_FOUND:
            dialog = createAlreadyAddedDialog();
            break;
        case DIALOG_HURRAY2:
            dialog = createHurray2Dialog();
            break;
        case DIALOG_ALREADY_SCANNED:
            dialog = createAlreadyScannedDialog();
            break;
        default:
            dialog = null;
        }
        return dialog;
    }

    private Dialog createAlreadyScannedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.AD_DLG_rescan_text)
        .setTitle(R.string.AD_DLG_rescan_title)
        .setPositiveButton(R.string.AD_DLG_yes_recommanded, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                createContactsDialog().show();
            }
        })
        .setNegativeButton(R.string.DLG_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                new ScanContactsTask().execute(new Void[0]);
            }
        });
        AlertDialog alert = builder.create();
        return alert;
    }

    private Dialog createAddAnywayDialog(final String friendMail) {
        final Resources r = getResources();
        final Dialog loginDialog = new Dialog(this);
        loginDialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        loginDialog.setTitle(R.string.AD_DLG_not_registered_title);

        LayoutInflater li = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogView = li.inflate(R.layout.add_anyway_dialog, null);
        loginDialog.setContentView(dialogView);

        TextView tv = (TextView) dialogView.findViewById(R.id.mail_not_using);
        tv.setText(r.getString(R.string.AD_invitation_suggestion_before_mail)+friendMail+r.getString(R.string.AD_invitation_suggestion_after_mail));

        Button addButton = (Button) dialogView.findViewById(R.id.add_anyway_button);
        Button cancelButton = (Button) dialogView
        .findViewById(R.id.cancel_anyway_button);
        final CheckBox cb = (CheckBox) dialogView
        .findViewById(R.id.mail_checkbox);

        cb.setChecked(true);

        addButton.setOnClickListener(new OnClickListener() {
            // @Override
            public void onClick(View v) {
                if (!friendInList(friendMail)){
                    peopleHashSet.add(friendMail);
                    fillData();
                    if (cb.isChecked()){
                        // send mail
                        new InviteFriendTask().execute(new String[]{friendMail});
                    }
                }
                loginDialog.dismiss();
                showDialog(DIALOG_HURRAY2);
            }
        });

        cancelButton.setOnClickListener(new OnClickListener() {
            // @Override
            public void onClick(View v) {
                loginDialog.cancel();
            }
        });

        return loginDialog;
    }

    private class InviteFriendTask extends AsyncTask<String, Void, Boolean> {


        @Override
        protected void onPostExecute(Boolean result) {
            if (result){
                Toast.makeText(getApplicationContext(), R.string.AD_invitation_sent, Toast.LENGTH_LONG).show();
            }
            else{
                Toast.makeText(getApplicationContext(), R.string.AD_invitation_not_sent, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected Boolean doInBackground(String... params) {

            boolean res = prs.inviteFriend(params[0]);

            return res;
        }
    }

    private Dialog createAddDialog() {

        final Dialog loginDialog = new Dialog(this);
        loginDialog.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        loginDialog.setTitle(R.string.AD_DLG_add_title);

        LayoutInflater li = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogView = li.inflate(R.layout.add_friend_dialog, null);
        loginDialog.setContentView(dialogView);

        Button addButton = (Button) dialogView.findViewById(R.id.add_button);
        Button cancelButton = (Button) dialogView
        .findViewById(R.id.cancel_button);
        final EditText et_email = (EditText) dialogView.findViewById(R.id.uname_id);

        addButton.setOnClickListener(new OnClickListener() {
            // @Override
            public void onClick(View v) {
                String friendMail = et_email.getText().toString();
                if (friendMail.compareTo("")==0){ //$NON-NLS-1$
                    Toast.makeText(getBaseContext(), R.string.AD_valid_email,
                            Toast.LENGTH_LONG).show();
                }else if (!friendMail.matches(AroundRoidAppConstants.emailRegularExpression)){
                    Toast.makeText(getBaseContext(), R.string.AD_invalid_email,
                            Toast.LENGTH_LONG).show();
                }else if (friendInList(friendMail)){
                    showDialog(DIALOG_ALREADY_FOUND);
                }else{
                    loginDialog.dismiss();
                    new ScanOneFriendTask().execute(new String[]{friendMail});
                }
            }
        });

        cancelButton.setOnClickListener(new OnClickListener() {
            // @Override
            public void onClick(View v) {
                loginDialog.cancel();
            }
        });

        return loginDialog;


    }

    private void fillData(){
        //sync data before
        LinkedHashSet<String> copySet = copySetSafe();
        if (taskId!=null){
            myLocationService.syncLocationsByPeople(taskId,copySet);
        }
        // Create a list of FriendPropsWithContactId, that will be put to our ListActivity.
        // Refreshing the list
        ArrayAdapter<FriendPropsWithContactId> adapter = new FriendAdapter(this,conHel,
                getProps(copySet));
        setListAdapter(adapter);
    }

    private Dialog createDeleteDialog(final String friendMail) {
        final Resources r = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(r.getString(R.string.AD_DLG_delete_text_before_mail) + " "+ //$NON-NLS-1$
                friendMail +" " + r.getString(R.string.AD_DLG_delete_text_after_mail)) //$NON-NLS-1$
                .setTitle(R.string.AD_DLG_delete_title)
                .setPositiveButton(R.string.DLG_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        peopleHashSet.remove(friendMail);
                        fillData();
                    }
                })
                .setNegativeButton(R.string.DLG_no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        return alert;

    }

    /*
    private void saveNewPeople(){
        myLocationService.syncLocationsByPeople(taskID,peopleHashSet);
    }
     */

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        GPSService.lockDeletes(true);
        setContentView(R.layout.friend_list_layout);
        conHel = new ContactsHelper(getContentResolver());
        mDbHelper = new AroundroidDbAdapter(this);
        mDbHelper.open();

        Bundle extras = getIntent().getExtras();
        String[] peopleWeGot = null;
        if (extras != null) {
            peopleWeGot = extras.getStringArray(peopleArraySTR);
            taskId = extras.getLong(taskIDSTR);
        }
        else{
            taskId = null;
        }


        if (taskId!=null){
            originalPeople = myLocationService.getLocationsByPeopleAsArray(taskId);
        }
        else if (peopleWeGot!=null){
            originalPeople = peopleWeGot;
        }
        else{
            originalPeople = new String[0];
        }

        for (String s : originalPeople)
            peopleHashSet.add(s);

        fillData();
        ListView list = getListView();
        list.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                FriendProps mailChosen = (FriendProps)parent.getAdapter().getItem(position);
                createDeleteDialog(mailChosen.getMail()).show();
                // Return true to consume the click event. In this case the
                // onListItemClick listener is not called anymore.
                return true;
            }
        });



    }

    private final Handler mHan = new Handler();
    final int mDelayMillis = 10 * 1000;
    private final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            fillData();
            mHan.postDelayed(this, mDelayMillis);
        }
    };
    private void setUITimer(){
        mHan.removeCallbacks(mUpdateTimeTask);
        mHan.postDelayed(mUpdateTimeTask, mDelayMillis);

    }

    private List<FriendPropsWithContactId> getProps(Set<String> peopleHashSet2) {
        //TODO deal with error
        List<FriendPropsWithContactId> list = new ArrayList<FriendPropsWithContactId>();
        for (String mail : peopleHashSet2){
            list.add(get(mail));
        }
        return list;
    }

    private FriendPropsWithContactId get(String s) {
        Cursor cur = mDbHelper.fetchByMail(s);
        if (!cur.moveToFirst()){
            cur.close();
            long rowId = mDbHelper.createPeople(s);
            //TODO assming create succesful
            cur = mDbHelper.fetchPeople(rowId);
        }
        //friendPROPSWITHCONTACTID CRITICAL PART

        FriendProps fp = AroundroidDbAdapter.userToFP(cur);
        FriendPropsWithContactId fpwci = new FriendPropsWithContactId(cur.getLong(cur.getColumnIndex(AroundroidDbAdapter.KEY_CONTACTID)), fp);
        cur.close();
        return fpwci;


    }

    /*
    private final Handler mListFillHandler = new Handler();
    private synchronized void fillListSmart(){
        new Thread() {
            @Override
            public void run() {
                mListFillHandler.post(new Runnable() {
                    public void run() {
                        fillData();
                        //GPSService.lockDeletes(false);
                    }
                });
            }
        }.start();
    }
     */

    private class ScanContactsTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPostExecute(Boolean result) {
            if (isFinishing()){
                //GPSService.lockDeletes(false);
                return;
            }
            _progressDialog.dismiss();
            if (result){
                createContactsDialog().show();
            }
            else{
                //TODO dont know what to do here
            }


        }

        private ProgressDialog _progressDialog;

        @Override
        protected void onPreExecute(){
            final Resources r = getResources();
            _progressDialog = ProgressDialog.show(
                    ManageContactsActivity.this,
                    r.getString(R.string.AD_contacts_scan_title),
                    r.getString(R.string.AD_contacts_scan_text),
                    true,
                    true,
                    new DialogInterface.OnCancelListener(){
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            ScanContactsTask.this.cancel(true);
                        }
                    }
            );
        }

        //assuming mail in lower case
        @Override
        protected Boolean doInBackground(Void... params) {
            Set<Entry<String, Long>> friendSet = conHel.friendsWithMail();
            if (isCancelled()){
                return false;
            }

            //GPSService.lockDeletes(true);
            ArrayList<String> al = new ArrayList<String>();
            for(Entry<String, Long> en : friendSet){
                Cursor cur = mDbHelper.fetchByMail(en.getKey());
                if (cur==null){
                    break;
                }
                if (!cur.moveToFirst()){
                    //record not found
                    mDbHelper.createPeople(en.getKey(),en.getValue());
                }
                else {
                    Long contactId = cur.getLong(cur.getColumnIndex(AroundroidDbAdapter.KEY_CONTACTID));
                    if (contactId == -2){
                        //record found with no contact id.
                        //UPDATE A NEW CONTACT ID
                        Long oldRecordId = cur.getLong(cur.getColumnIndex((AroundroidDbAdapter.KEY_ROWID)));
                        mDbHelper.updatePeople(oldRecordId, en.getValue());
                    }
                }
                al.add(en.getKey());
                cur.close();
            }
            if (isCancelled()){
                //GPSService.lockDeletes(false);
                return false;
            }
            //TODO limit update people location to few people
            if (al.size()<=0){
                //GPSService.lockDeletes(false);
                return true;
            }
            List <FriendProps> lfp = prs.updatePeopleLocations(al.toArray(new String[0]), null, mDbHelper);
            if (lfp == null){
                //GPSService.lockDeletes(false);
                return false;
            }
            else{
                getAlreadyScannedSometime(true);
                return true;
            }
        }


    }


    private class ScanOneFriendTask extends AsyncTask<String, Void, Boolean> {

        private boolean error = false;

        private String friend;

        private ProgressDialog _progressDialog;

        @Override
        protected void onPreExecute(){
            final Resources r = getResources();
            _progressDialog = ProgressDialog.show(
                    ManageContactsActivity.this,
                    r.getString(R.string.AD_checking_friend_title),
                    r.getString(R.string.AD_checking_friend_text),
                    true,
                    true,
                    new DialogInterface.OnCancelListener(){
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            ScanOneFriendTask.this.cancel(true);
                        }
                    }
            );
        }

        @Override
        protected void onPostExecute(Boolean result) {
            //TODO check this:
            if (isFinishing()){
                return;
            }
            _progressDialog.dismiss();
            if (!error){

                if (result){
                    fillData();
                    //friend is using Aroundroid
                    showDialog(DIALOG_HURRAY);
                }
                else{
                    //friend is not using Aroundroid
                    createAddAnywayDialog(friend).show();
                }
            }
            Toast.makeText(ManageContactsActivity.this, "an error occurd. try again later", Toast.LENGTH_LONG); //$NON-NLS-1$
        }

        //assuming mail in lower case
        @Override
        protected Boolean doInBackground(String... params) {
            //GPSService.lockDeletes(true);
            boolean returnVal = false;
            friend = params[0];
            Cursor cur  = mDbHelper.fetchByMail(friend);
            if (cur ==null){
                //GPSService.lockDeletes(false);
                return false;
            }

            if (!cur.moveToFirst()){
                cur.close();
                long rowId = mDbHelper.createPeople(friend);
                if (rowId==-1){
                    error = true;
                    return false;
                }
            }else{
                cur.close();
            }


            List<FriendProps> lfp =  prs.updatePeopleLocations(params,null,mDbHelper);
            if (lfp==null){
                error = true;
                //GPSService.lockDeletes(false);
                return false;
            }



            FriendProps fp = lfp.get(0);
            if (fp!=null){
                if (fp.isRegistered()){
                    returnVal = true;
                    //TODO check this:
                    if (!isFinishing()){
                        if (!friendInList(fp.getMail())){
                            //TODO add this check to the onResult function too.
                            peopleHashSet.add(fp.getMail());;
                        }
                    }
                }
            }else{
                error = true;
            }


            cur.close();
            //GPSService.lockDeletes(false);
            return returnVal;
        }
    }


    private LinkedHashSet<String> copySetSafe(){
        LinkedHashSet <String> retSet =  new LinkedHashSet<String>();
        synchronized (peopleHashSet) {
            for (String s : peopleHashSet){
                retSet.add(s);
            }
        }
        return retSet;
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(PEOPLE_BACK,copySetSafe());
        setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHan.removeCallbacks(mUpdateTimeTask);
        fillData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUITimer();
        fillData();
    }

}
