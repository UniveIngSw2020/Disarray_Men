package com.disarraymen.redzone;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
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
import android.service.notification.StatusBarNotification;
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

    // ------------------------------------------ Objects ------------------------------------------

    // Layout
    Button accept_button, decline_button, blt, gps, exit;
    TextView text1;
    ImageView image1, image2;
    Animation show, show2, show3, hide, hide2, hide3;
    FrameLayout frameLayout;

    // Mappa
    private GoogleMap mMap;
    private Marker myMarker;
    private LatLng myLatLng;
    private LatLng myOldLatLng;
    private double currentlatitude, currentlongitude;
    private LocationManager locationManager;
    HashMap<String, Circle> marks = new HashMap();

    // Database
    public FirebaseDatabase database;
    public DatabaseReference myRef;

    // Notifiche
    NotificationManager notificationManager;
    NotificationUtils mNotificationUtils;
    public Intent intent = new Intent();
    PendingIntent pendingIntent;
    public static final String MY_PREFS_NAME = "com.disarraymen.redzone";

    // Altro
    Looper mapsLooper = Looper.myLooper();
    private boolean recheckUser = true, btnGPSClick = false, onDestroyVar = false;
    private int raggioCerchio = 1000;
    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static SecureRandom rnd = new SecureRandom();

    // ----------------------------------------- Listener ------------------------------------------

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
            }
            assembraCerchi();
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            if(marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId) {
                String[] latlong = snapshot.child("data").getValue().toString().split(",");
                LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                if(metersFromLatLng(marks.get(snapshot.getKey()).getCenter(), pos) > 1) {
                    marks.get(snapshot.getKey()).setCenter(pos);
                }
            }
            assembraCerchi();
        }
        @Override
        public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            if(!onDestroyVar && snapshot.getKey().equals(userId)) {
                Map<String,Object> update = new HashMap<>();
                update.put("data", myLatLng.latitude + "," + myLatLng.longitude);
                update.put("timestamp", System.currentTimeMillis());
                myRef.child(userId).updateChildren(update);
            }
            if(marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId) {
                marks.get(snapshot.getKey()).setVisible(false);
                marks.remove(snapshot.getKey());
                assembraCerchi();
            }
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { //toastMe("moved");
            }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
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
        }
    };

    // ----------------------------------------- Callback ------------------------------------------

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            getCurrentLocation();
            if(myOldLatLng == null || metersFromLatLng(myLatLng, myOldLatLng) > 1) {
                StatusBarNotification[] notifications = new StatusBarNotification[0];
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    notifications = notificationManager.getActiveNotifications();
                }
                myOldLatLng = myLatLng;
                onLocationChanged(locationResult.getLastLocation());
                myMarker.setPosition(myLatLng);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(myLatLng));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 12.0f));
                for (StatusBarNotification notification : notifications) {
                    if (notification.getId() == 101) {
                        return;
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder nb = mNotificationUtils.getAndroidChannelNotification("Red Zone!", "Sei appena entrato in un assembramento!");
                    mNotificationUtils.getManager().notify(101, nb.build());
                } else {
                    notificationManager.notify(101, assembramento.build());
                }
            }
        }
    };

    // ----------------------------------------- Notifica ------------------------------------------

    NotificationCompat.Builder assembramento = new NotificationCompat.Builder(this)
            .setSmallIcon(R.mipmap.ic_alert)
            .setContentTitle("Red Zone!")
            .setContentText("Sei appena entrato in un assembramento!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)      //Priorita'
            .setAutoCancel(true)
            .setColor(Color.RED)                                //Colore
            .setOnlyAlertOnce(true)
            .setVibrate(new long[] { 1000, 1000, 1000, 1000 })  //Vibrazione
            //.setSound(Uri.parse("uri://notification.mp3"))    //Suono
            .setLights(Color.RED, 3000, 3000);      //Led


    // ------------------------------------ Funzioni Principali ------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);
        Intent intent = new Intent(this, BluetoothActivity.class);
        startActivity(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationUtils = new NotificationUtils(this);
        }
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
        spec.setIndicator("Settings");
        tabs.addTab(spec);

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

    @Override
    protected void onDestroy() {
        myRef.removeEventListener(checkUsernameListener);
        myRef.removeEventListener(updateOthersLocationsListener);
        myRef.removeEventListener(eventListenerUpdate);
        DatabaseReference.CompletionListener completionListener = new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                MapsActivity.super.onDestroy();
            }
        };
        myRef.child(userId).removeValue(completionListener);
    }

    public void start(){
        frameLayout.setVisibility(View.INVISIBLE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationUtils.pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        blt = (Button) findViewById(R.id.blt);
        gps = (Button) findViewById(R.id.gps);
        exit = (Button) findViewById(R.id.exit);

        text1 = (TextView) findViewById(R.id.tabText);
        image1 = (ImageView) findViewById(R.id.imageView);
        image2 = (ImageView) findViewById((R.id.imageView2));

        gps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                setBtnGPS();
            }
        });

        blt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                flipBT();
            }
        });

        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                exit();
            }
        });

        show = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_up);
        show2 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_up2);
        hide = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_down);
        hide2 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_down2);
        show3 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_down);
        hide3 = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_up);

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

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getCurrentLocation();
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
                } else if (netLocation != null) {
                    currentlatitude = netLocation.getLatitude();
                    currentlongitude = netLocation.getLongitude();
                }
                myLatLng = new LatLng(currentlatitude, currentlongitude);
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
        updateOthersLocation();
    }

    public void onLocationChanged(Location location) {
        // New location has now been determined
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        updateMyLocationOnDatabase();
    }

    public void updateOthersLocation() {
        // Read from the database
        myRef.addChildEventListener(updateOthersLocationsListener);
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
    }

    // -------------------------------------- Altre Funzioni ---------------------------------------

    private void toastMe(String str){
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
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

    private String generateRandomString(){
        int len = 7;
        StringBuilder sb = new StringBuilder( len );
        for( int i = 0; i < len; i++ )
            sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
        return sb.toString();
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

    public void assembraCerchi() {
        Iterator<Circle> it1 = marks.values().iterator();
        int assembraMiniFound = 0;
        String circleKey = null;
        boolean notified = false;
        while (it1.hasNext()) {
            Circle circle1 = it1.next();
            Iterator<Circle> it2 = marks.values().iterator();
            while(it2.hasNext()){
                Circle circle2 = it2.next();
                if (circle1 != circle2 &&
                        circle1.getCenter().longitude - circle2.getCenter().longitude < 0.00002 ||
                        circle1.getCenter().latitude - circle2.getCenter().latitude < 0.00002
                                && circle2.getTag() != userId) {
                    assembraMiniFound++;
                    circle2.setVisible(false);
                    circleKey = (String) circle1.getTag();
                } else if (circleKey == null) {
                    circleKey = (String) circle1.getTag();
                }
            }
            if(assembraMiniFound == 0){
                marks.get(circleKey).setVisible(true);
            } else if(assembraMiniFound > 0){
                marks.get(circleKey).setVisible(true);
                marks.get(circleKey).setRadius(raggioCerchio*assembraMiniFound);
                assembraMiniFound = 0;
            }
        }
    }

    private void flipBT() {
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            BluetoothAdapter.getDefaultAdapter().disable();
        } else {
            BluetoothAdapter.getDefaultAdapter().enable();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 98);
        }
    }

    public void acceptBtn() {
        SharedPreferences.Editor editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("privacy", true);
        editor.apply();
        start();

    }

    public void declineBtn() {
        exit();
    }

    public void exit(){
        onDestroy();
        finish();
        System.exit(0);
    }

    public void setBtnGPS() {
        if (!isPermissionAccess()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 99);
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

}
