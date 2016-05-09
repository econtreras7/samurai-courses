package com.ninja.cse.samuaricourses;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.http.NextServiceFilterCallback;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilter;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterRequest;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Created by dgist on 4/26/2016.
 */

public class NotificationActivity extends AppCompatActivity {

    private MobileServiceClient mClient;
    private MobileServiceTable<courses> mCoursesTable;
    private ProgressBar mProgressBar;


    AutoCompleteTextView Department, Classes;
    private ArrayAdapter<String> departmentAdapter, classesAdapter;
    ArrayList<String> classeslist = new ArrayList<String>();
    Button add,notify;
    ArrayList<courses> coursesToPass = new ArrayList<courses>();
    static int selection=0;
    ArrayList<courses> selectedCourses = new ArrayList<courses>();

    private PendingIntent pendingIntent;
    private AlarmManager manager;
    boolean alarmOn;


    DBHelper db;
    //removed from layout: <android:layout_alignEnd="@+id/btnTrack" under add button

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification);
        db = new DBHelper(this);

        pendingIntent = PendingIntent.getBroadcast(NotificationActivity.this,0,new Intent(NotificationActivity.this, AlarmReceiver.class), PendingIntent.FLAG_NO_CREATE);

        alarmOn = (pendingIntent != null);

        final Button btnAdd = (Button)findViewById(R.id.btnAdd);
        final Button btnNotify = (Button)findViewById(R.id.btnNotify);

        classesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, classeslist);
        Classes = (AutoCompleteTextView) findViewById(R.id.number);
        Classes.setThreshold(1);
        Classes.setAdapter(classesAdapter);
        Classes.setHint("Class");

        departmentAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.Department_array));
        Department = (AutoCompleteTextView) findViewById(R.id.department);
        Department.setAdapter(departmentAdapter);
        Department.setThreshold(1);
        Department.setHint("Department");

        if(alarmOn){
            btnNotify.setText("STOP");
        }
        manager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);


        Department.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedCourses.clear();
                ArrayAdapter<String> departmentTagAdapter = new ArrayAdapter<String>(NotificationActivity.this, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.Department_tag_array));
                ArrayAdapter<String> departmentTempAdapter = new ArrayAdapter<String>(NotificationActivity.this, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.Department_array));

                String chosen = parent.getItemAtPosition(position).toString();
                int newpos = departmentTempAdapter.getPosition(chosen);
                chosen = departmentTagAdapter.getItem(newpos);

                selectedCourses.addAll(db.courseSearchByDepartment(chosen));

                classesAdapter.clear();
                for (courses each : selectedCourses) {
                    classesAdapter.add(each.getNumber().substring(each.getNumber().indexOf('-')+1,each.getNumber().length()).replaceFirst("^0+(?!$)", ""));
                    classesAdapter.notifyDataSetChanged();
                    //Log.d("ON LIST",classesAdapter.getItem(classesAdapter.getCount()-1));
                }
            }
        });

        final ListView listview = (ListView) findViewById(R.id.listViewNotification);
        final ArrayList<String> listToTrackCourses = new ArrayList<String>();
        final ArrayAdapter<String> listadapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.custom_listitems, listToTrackCourses);
        listview.setAdapter(listadapter);
        //updates the list to whats in database
        listadapter.addAll(db.retrieveNotificationCourses());
        listadapter.notifyDataSetChanged();
        AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
                if (!listview.isItemChecked(position)) {
                    String temp = listadapter.getItem(position);
                    db.deleteNotificationCourses(temp);
                    listadapter.remove(temp);
                    listadapter.notifyDataSetChanged();
                }
            }
        };
        listview.setOnItemClickListener(itemClickListener);

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (listToTrackCourses.size() == 3) {
                    Toast.makeText(NotificationActivity.this, "Tracking maxed at 3 courses!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (Department.getText().toString().equals("") || Classes.getText().toString().equals("")) {
                    Toast.makeText(NotificationActivity.this, " Select valid courses", Toast.LENGTH_SHORT).show();
                    return;
                }

                int numFromNumber = Integer.parseInt(Classes.getText().toString().substring(0,Classes.getText().toString().indexOf('-')));
                String restOfString = Classes.getText().toString().substring(Classes.getText().toString().indexOf('-'), Classes.getText().toString().length());
                listadapter.add(Department.getText().toString() + "-" + String.format("%03d",numFromNumber) + restOfString);
                listview.setItemChecked(listadapter.getCount() - 1, true);
                listadapter.notifyDataSetChanged();


                classeslist.clear();
                Department.setText("");
                Classes.setText("");
                InputMethodManager imm = (InputMethodManager) getSystemService(NinjaActivity.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        });

        btnNotify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(alarmOn){
                    cancelAlarm(v);
                    btnNotify.setText("GET NOTIFIED");
                    return;
                }

                Intent intent = new Intent(NotificationActivity.this, AlarmReceiver.class);
                Bundle bundle = new Bundle();

                if (listToTrackCourses.size() == 0)
                    return;
                else {

                    for(int j =0;j<listToTrackCourses.size();j++) {
                        //Log.d("NOTIFY_TEST", listToTrackCourses.get(j));
                        coursesToPass.add(db.courseSearchByNumber(listToTrackCourses.get(j)));
                        Log.d("ARRCRN",coursesToPass.get(j).getCrn() + "");
                        db.deleteNotificationCourses(coursesToPass.get(j).getNumber());
                        if(!db.insertCourse(coursesToPass.get(j).getCrn(), coursesToPass.get(j).getNumber())){
                            Log.d("INSERT", "insert failed");
                            return;
                        }
                    }
                    bundle.putParcelableArrayList("CLASSES", coursesToPass);
                    intent.putExtras(bundle);
                    // Retrieve a PendingIntent that will perform a broadcast with the intent and its extras above
                    pendingIntent = PendingIntent.getBroadcast(NotificationActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                    btnNotify.setText("STOP");
                    startAlarm(v);
                }
            }
        });
    }

    public void startAlarm(View view) {
        //manager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        int interval = 15000;

        manager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, pendingIntent);
        Toast.makeText(NotificationActivity.this, "You will get notified!", Toast.LENGTH_LONG).show();
    }


    public void cancelAlarm(View view) {
        if (alarmOn) {
            manager.cancel(pendingIntent);
            Toast.makeText(this, "Notifications Canceled", Toast.LENGTH_LONG).show();
            manager = null;
        }
    }

    /**
     * This method performs an Asynchronous task that adds a list of course number to the
     * classes TextView.
     *
     * @param selectedDepartment String from the first dropdown list of textView ex:CSE
     */
    private void getActiveEnrl(final String selectedDepartment)throws ExecutionException, InterruptedException{

        AsyncTask<Void,Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try{
                    Log.d("get Query", "Just before getting query");
                    final courses course = availableClasses(selectedDepartment);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //put logic for checking enrollment here.

                        }
                    });

                }catch(final Exception e){
                    e.printStackTrace();
                }
                return null;
            }

        };
        task.execute();
    }

    /**
     * Runs LINQ to database and retrieves course that the user selected.
     *
     * @param item string passed from the getActiveEnrl method and added to the LINQ query
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private courses availableClasses(String item) throws ExecutionException, InterruptedException{
        courses entity =  mCoursesTable.where().field("crn").eq(item).execute().get().get(0);
        return entity;
    }



    private class ProgressFilter implements ServiceFilter {

        /**
         * method that handles the html response from the client.
         *
         * Plan to implement the progress bar for longer wait times.
         *
         * @param request
         * @param nextServiceFilterCallback
         * @return
         */
        @Override
        public ListenableFuture<ServiceFilterResponse> handleRequest(ServiceFilterRequest request, NextServiceFilterCallback nextServiceFilterCallback) {

            final SettableFuture<ServiceFilterResponse> resultFuture = SettableFuture.create();


            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.VISIBLE);
                }
            });

            ListenableFuture<ServiceFilterResponse> future = nextServiceFilterCallback.onNext(request);

            Futures.addCallback(future, new FutureCallback<ServiceFilterResponse>() {
                @Override
                public void onFailure(Throwable e) {
                    resultFuture.setException(e);
                }

                @Override
                public void onSuccess(ServiceFilterResponse response) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.GONE);
                        }
                    });

                    resultFuture.set(response);
                }
            });

            return resultFuture;
        }
    }


}
