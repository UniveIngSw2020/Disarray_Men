package com.disarraymen.redzone;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.disarraymen.redzone.UserData.userChecked;
import static com.disarraymen.redzone.UserData.userId;
import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    public FirebaseDatabase database;
    public DatabaseReference myRef;
    private LocationManager locationManager;
    private double currentlatitude;
    private double currentlongitude;
    private LatLng myLatLng;
    private boolean recheckUser = true;
    FloatingActionButton btn1, btn2, btn3, btnGPS;
    Button accept_button, decline_button;
    TextView text1;
    ImageView image1, image2;
    Animation show, show2, show3, hide, hide2, hide3;
    boolean btnGPSClick = false;

    public Intent intent = new Intent();

    Marker myMarker;




    ValueEventListener checkUsernameListener = new ValueEventListener() {

        @Override
        public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.hasChild(userId)) {
                userId = generateRandomString();
                recheckUser = true;
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
        }

    };

    int raggioCerchio = 1000;
    ChildEventListener updateOthersLocationsListener = new ChildEventListener() {
        @Override
        public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            if(!marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId){
                String[] latlong = snapshot.child("data").getValue().toString().split(",");
                LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                Circle localMark = mMap.addCircle(new CircleOptions()
                        .center(pos)
                        .radius(raggioCerchio)
                        .fillColor(0x44ff0000)
                        .strokeColor(0xffff0000)
                        .strokeWidth(0));
                localMark.setTag(snapshot.getKey());
                marks.put(snapshot.getKey(), localMark);

                assembraCerchio(localMark, snapshot.getKey());
                if(assembraFound > 1){
                    localMark.setRadius(raggioCerchio*assembraFound);
                    assembraFound = 1;
                }
                //toastMe("added: " + snapshot.getKey());
            }
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            if(marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId) {
                String[] latlong = snapshot.child("data").getValue().toString().split(",");
                LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                marks.get(snapshot.getKey()).setCenter(pos);

                assembraCerchio(marks.get(snapshot.getKey()), snapshot.getKey());
                if(assembraFound > 1) {
                    marks.get(snapshot.getKey()).setRadius(raggioCerchio * assembraFound);
                    assembraFound = 1;
                }
                //toastMe("changed: " + snapshot.getKey());
            }
        }

        @Override
        public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            if(marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId) {
                marks.get(snapshot.getKey()).setVisible(false);
                marks.remove(snapshot.getKey());
                assembraCerchi();
                //toastMe("removed: " + snapshot.getKey());
            }
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { //toastMe("moved");
            }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            //toastMe("cancelled");
        }
    };


    ValueEventListener eventListenerUpdate = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            if(!dataSnapshot.hasChild("data") || dataSnapshot.child(userId).getValue() == null) {
                Map<String,Object> update = new HashMap<>();
                update.put("data", myLatLng.latitude + "," + myLatLng.longitude);
                update.put("timestamp", System.currentTimeMillis());
                myRef.child(userId).updateChildren(update);
            } else {
                userId = generateRandomString();
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            //updateMyLocationOnDatabase();
            //userId = generateRandomString();
        }
    };

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            // do work here
            onLocationChanged(locationResult.getLastLocation());
            updateOthersLocation();
            getCurrentLocation();


            myMarker.setPosition(myLatLng);
            mMap.moveCamera(CameraUpdateFactory.newLatLng(myLatLng));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 12.0f));
        }
    };
    PendingIntent pendingIntent;

    NotificationCompat.Builder assembramento = new NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_alert)
            //.setLargeIcon(bitmap)
            .setContentTitle("Red Zone!")
            .setContentText("Sei appena entrato in un assembramento!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)//Vibration
            .setColor(Color.RED)
            .setOnlyAlertOnce(true)
            .setVibrate(new long[] { 1000, 1000, 1000, 1000 })
            //.setSound(Uri.parse("uri://notification.mp3"))
            .setLights(Color.RED, 3000, 3000);


    FrameLayout frameLayout;
    public static final String MY_PREFS_NAME = "com.disarraymen.redzone";

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);
        Intent intent = new Intent(this, BluetoothActivity.class);
        startActivity(intent);

        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MapsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        frameLayout = (FrameLayout) findViewById(R.id.privacyLayout);


        TabHost tabs = (TabHost) findViewById(R.id.tabhost);
        tabs.setup();
        TabHost.TabSpec spec = tabs.newTabSpec("tag1");
        spec.setContent(R.id.tab1);
        spec.setIndicator("Map");
        tabs.addTab(spec);
        spec = tabs.newTabSpec("tag2");
        spec.setContent(R.id.tab2);
        spec.setIndicator("Bluetooth");
        tabs.addTab(spec);
        /*
        spec = tabs.newTabSpec("tag3");
        spec.setContent(R.id.tab3);
        spec.setIndicator("Settings");
        tabs.addTab(spec);*/

        SharedPreferences prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        boolean privacy = prefs.getBoolean("privacy", false);


        if (!privacy){
            WebView terms = (WebView) findViewById(R.id.webview);
            terms.loadUrl("http://testesercitazioni.altervista.org/Disarray_Men/RedZone/Docs/terms___coditions.html");


            accept_button = (Button) findViewById(R.id.accept_button);
            decline_button = (Button) findViewById(R.id.decline_button);

            accept_button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(final View v) {
                    acceptBtn();
                }
            });
            decline_button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(final View v) {
                    declineBtn();
                }
            });
        } else {
            start();
        }
    }






    Looper mapsLooper = Looper.myLooper();

    @Override
    protected void onStop() {
        super.onStop();
        //toastMe("STOPPED");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        getCurrentLocation();
        startLocationUpdates();

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    NotificationManager notificationManager;

    public void start(){


        frameLayout.setVisibility(View.INVISIBLE);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);


        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationUtils.pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);


        //notificationManager.notify(notificationId, assembramento.build());

        btn1 = (FloatingActionButton) findViewById(R.id.btn4);
        btn2 = (FloatingActionButton) findViewById(R.id.btn2);
        btn3 = (FloatingActionButton) findViewById(R.id.btn3);

        text1 = (TextView) findViewById(R.id.tabText);
        image1 = (ImageView) findViewById(R.id.imageView);
        image2 = (ImageView) findViewById((R.id.imageView2));
        btnGPS = (FloatingActionButton) findViewById((R.id.btnGPS));


        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setBtn1();
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setBtn2();
            }
        });

        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setBtn3();
            }
        });

        btnGPS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setBtnGPS();
            }
        });

        show = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_up);
        show2 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_up2);
        hide = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_down);
        hide2 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_down2);
        show3 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_down);
        hide3 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_up);

        btn1.startAnimation(show);
        btn1.setClickable(true);
        btn1.setVisibility(View.VISIBLE);
        btn2.startAnimation(hide);
        btn2.setClickable(false);
        btn2.setVisibility(View.GONE);
        btn3.startAnimation(hide);
        btn3.setClickable(false);
        btn3.setVisibility(View.GONE);

        btnGPS.startAnimation(show);
        btnGPS.setClickable(true);
        btnGPS.setVisibility(View.VISIBLE);
        text1.startAnimation(hide3);
        text1.setVisibility(View.GONE);
        image1.startAnimation(hide3);
        image1.setVisibility(View.GONE);
        image2.startAnimation(hide3);
        image2.setVisibility(View.GONE);


        database = FirebaseDatabase.getInstance();
        myRef = database.getReference().child("userLocations");
        createUsername();

        if (!isPermissionAccess()) {
            setBtnGPS();
        } else {
            getCurrentLocation();
            startLocationUpdates();

            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        }
    }





    public void acceptBtn() {
        SharedPreferences.Editor editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("privacy", true);
        editor.apply();
        start();

    }
    public void declineBtn() {
        btn2.startAnimation(hide);
        btn2.setClickable(false);
        btn2.setVisibility(View.GONE);
        startActivity(new Intent(this, BluetoothActivity.class));
    }












    public void setBtn1() {
        //btn1.startAnimation(hide);
        //btn1.setClickable(false);
        //btn1.setVisibility(View.GONE);
        if(!btn2.isClickable()){
            btn2.startAnimation(show);
            btn2.setClickable(true);
            btn2.setVisibility(View.VISIBLE);
            btn3.startAnimation(show2);
            btn3.setClickable(true);
            btn3.setVisibility(View.VISIBLE);
        } else {
            btn2.startAnimation(hide);
            btn2.setClickable(false);
            btn2.setVisibility(View.GONE);
            btn3.startAnimation(hide2);
            btn3.setClickable(false);
            btn3.setVisibility(View.GONE);
        }
    }

    public void setBtn2() {
        btn2.startAnimation(hide);
        btn2.setClickable(false);
        btn2.setVisibility(View.GONE);
        startActivity(new Intent(this, BluetoothActivity.class));
    }

    public void setBtn3() {
        btn3.startAnimation(hide);
        btn3.setClickable(false);
        btn3.setVisibility(View.GONE);
    }

    public void setBtnGPS() {
        //btnGPS.startAnimation(hide);
        //btnGPS.setClickable(true);
        //btnGPS.setVisibility(View.GONE);
        if (!isPermissionAccess()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 99);

        } else {
            if (!btnGPSClick) {
                text1.startAnimation(show3);
                image1.startAnimation(show3);
                image2.startAnimation(show3);
                text1.setVisibility(View.VISIBLE);
                image1.setVisibility(View.VISIBLE);
                image2.setVisibility(View.VISIBLE);
                getFusedLocationProviderClient(this).removeLocationUpdates(mLocationCallback);
                btnGPSClick = true;
            } else {
                text1.startAnimation(hide3);
                image1.startAnimation(hide3);
                image2.startAnimation(hide3);
                text1.setVisibility(View.GONE);
                image1.setVisibility(View.GONE);
                image2.setVisibility(View.GONE);
                startLocationUpdates();
                btnGPSClick = false;
            }
        }
    }








    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getCurrentLocation();
        //Toast.makeText(this, "latitude:" + currentlatitude + " longitude:" + currentlongitude, Toast.LENGTH_SHORT).show();

        LatLng locationMarker = new LatLng(currentlatitude, currentlongitude);
        myMarker = mMap.addMarker(new MarkerOptions()
                .position(locationMarker)
                .title("Tu sei qui"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(locationMarker));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locationMarker, 12.0f));

    }




    @SuppressLint("MissingPermission")
    public void getCurrentLocation() {
        if (isPermissionAccess()) {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location netLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (gpsLocation != null) {
                    currentlatitude = gpsLocation.getLatitude();
                    currentlongitude = gpsLocation.getLongitude();
                    //myRef.setValue(currentlatitude + "," + currentlongitude);
                } else if (netLocation != null) {
                    currentlatitude = netLocation.getLatitude();
                    currentlongitude = netLocation.getLongitude();
                }
                myLatLng = new LatLng(currentlatitude, currentlongitude);
                //updateMyLocationOnDatabase();
            }
        }

    }


    private boolean isPermissionAccess() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }



    @SuppressLint("MissingPermission")
    protected void startLocationUpdates() {

        // Create the location request to start receiving updates
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(800);

        // Create LocationSettingsRequest object using location request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        // Check whether location settings are satisfied
        // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, mLocationCallback, mapsLooper);
    }


    public void onLocationChanged(Location location) {
        // New location has now been determined
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        ////Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        // You can now create a LatLng Object for use with maps
        myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        updateMyLocationOnDatabase();
    }

    HashMap<String, Circle> marks = new HashMap();

    public void updateOthersLocation() {
        // Read from the database
        myRef.addChildEventListener(updateOthersLocationsListener);
    }

    int assembraFound = 1;
    ArrayList<Circle> found = new ArrayList<>();

    public void assembraCerchio(final Circle localMark, String val){
        Iterator<Circle> it = marks.values().iterator();
        while(it.hasNext()){
            Circle circle = it.next();
            if(circle.getTag() != val) {
                if (circle.getCenter().longitude - localMark.getCenter().longitude < 0.00002 ||
                        circle.getCenter().latitude - localMark.getCenter().latitude < 0.00002) {
                    circle.setVisible(false);
                    assembraFound++;
                } else if (!circle.isVisible() && (circle.getCenter().longitude - localMark.getCenter().longitude > 0.00002 ||
                        circle.getCenter().latitude - localMark.getCenter().latitude > 0.00002)) {
                    circle.setVisible(true);
                }
            }
            if (metersFromLatLng(circle.getCenter(), myLatLng) < 2) {
                notificationManager.notify(0, assembramento.build());
            }
        }

    }

    /* vecchia versione (che richiede android Nougat (API 24)):
    marks.forEach(new BiConsumer<String, Circle>() {
        @Override
        public void accept(String s, Circle circle) {
            if(circle.isVisible() && (circle.getCenter().longitude-localMark.getCenter().longitude < 0.00002 ||
                    circle.getCenter().latitude-localMark.getCenter().latitude < 0.00002)){
                marks.get(s).setVisible(false);
                assembraFound++;
            } else if (!circle.isVisible() && (circle.getCenter().longitude-localMark.getCenter().longitude > 0.00002 ||
                    circle.getCenter().latitude-localMark.getCenter().latitude > 0.00002)) {
                Circle tempMark = marks.get(circle);
                marks.get(s).setVisible(true);
            }
        }
    });

     */


    private void toastMe(String str){
        //Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    public void createUsername(){
        if(userId == null) {userId = generateRandomString();}
        while(recheckUser && userChecked){
            recheckUser = false;
            myRef.addListenerForSingleValueEvent(checkUsernameListener);
        }
        userChecked = false;
    }


    public void updateMyLocationOnDatabase() {
        DatabaseReference userNameRef = myRef.child(userId);
        userNameRef.addListenerForSingleValueEvent(eventListenerUpdate);
        Toast.makeText(this, myLatLng.toString(), Toast.LENGTH_SHORT).show();


    }

    public void assembraCerchi() {
        Iterator<Circle> it1 = marks.values().iterator();
        int assembraMiniFound = 0;
        String circleKey = null;
        while (it1.hasNext()) {
            Circle circle1 = it1.next();
            Iterator<Circle> it2 = marks.values().iterator();
            while(it2.hasNext()){
                Circle circle2 = it2.next();
                if (circle1 != circle2 &&
                        circle1.getCenter().longitude - circle2.getCenter().longitude < 0.00002 ||
                        circle1.getCenter().latitude - circle2.getCenter().latitude < 0.00002) {
                    assembraMiniFound++;
                    circle2.setVisible(false);
                    circleKey = (String) circle1.getTag();
                }
            }
            if(assembraMiniFound > 0){
                marks.get(circleKey).setVisible(true);
                marks.get(circleKey).setRadius(raggioCerchio*assembraMiniFound);
                assembraMiniFound = 0;
            }
            if (metersFromLatLng(circle1.getCenter(), myLatLng) < 2) {
                notificationManager.notify(0, assembramento.build());
            }
        }
    }



    // LatLng to meters, explanation: https://en.wikipedia.org/wiki/Haversine_formula
    private double metersFromLatLng(LatLng p1, LatLng p2){
        double R = 6378.137; // Radius of earth in KM
        double dLat = p2.latitude * Math.PI / 180 - p1.latitude * Math.PI / 180;
        double dLon = p2.longitude * Math.PI / 180 - p1.longitude * Math.PI / 180;
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                        Math.cos(p1.latitude * Math.PI / 180) *
                        Math.cos(p2.latitude * Math.PI / 180) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double d = R * c;
        return d * 1000; // meters
    }

    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();

    private String generateRandomString(){
        int len = 7;
        StringBuilder sb = new StringBuilder( len );
        for( int i = 0; i < len; i++ )
            sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
        return sb.toString();
    }

    @SuppressLint("MissingPermission")
    public void getLastLocation() {
        // Get last known recent location using new Google Play Services SDK (v11+)
        FusedLocationProviderClient locationClient = getFusedLocationProviderClient(this);

        locationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // GPS location can be null if GPS is switched off
                        if (location != null) {
                            onLocationChanged(location);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("MapDemoActivity", "Error trying to get last GPS location");
                        e.printStackTrace();
                    }
                });
    }
}
