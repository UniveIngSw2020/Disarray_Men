package com.disarraymen.redzone;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
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
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

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
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static com.disarraymen.redzone.UserData.userChecked;
import static com.disarraymen.redzone.UserData.userId;
import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    // ------------------------------------------ Objects ------------------------------------------

    // Layout
    Button accept_button, decline_button;
    ImageButton blt;
    ImageButton gps;
    ImageButton exit;
    TextView text1;
    ImageView image1, image2;
    Animation show, hide;
    FrameLayout frameLayout;

    // Mappa
    private GoogleMap mMap;
    private Marker myMarker;
    private LatLng myLatLng;
    private LatLng myOldLatLng;
    private double currentlatitude, currentlongitude;
    HashMap<String, Circle> marks = new HashMap<>();

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
    private boolean recheckUser = true;
    private boolean btnGPSClick = false;
    private static boolean exit_app = false;
    private final int raggioCerchio = 1000;
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
            if(!marks.containsKey(snapshot.getKey()) && !Objects.equals(snapshot.getKey(), userId)){
                String[] latlong = Objects.requireNonNull(snapshot.child("data").getValue()).toString().split(",");
                LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                CircleOptions circle = new CircleOptions()
                        .center(pos)
                        .radius(raggioCerchio)
                        .fillColor(0x44ff0000)
                        .strokeColor(0xffff0000)
                        .strokeWidth(0);
                if(mMap != null){
                    Circle localMark = mMap.addCircle(circle);
                    localMark.setTag(snapshot.getKey());
                    marks.put(snapshot.getKey(), localMark);
                }
            }
            assembraCerchi();
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            if(marks.containsKey(snapshot.getKey()) && !Objects.equals(snapshot.getKey(), userId)) {
                String[] latlong = Objects.requireNonNull(snapshot.child("data").getValue()).toString().split(",");
                LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                if(metersFromLatLng(Objects.requireNonNull(marks.get(snapshot.getKey())).getCenter(), pos) > 1) {
                    Objects.requireNonNull(marks.get(snapshot.getKey())).setCenter(pos);
                }
            }
            assembraCerchi();
        }
        @Override
        public void onChildRemoved(@NonNull DataSnapshot snapshot) {
            if(Objects.equals(snapshot.getKey(), userId) && !MapsActivity.exit_app) {
                Map<String,Object> update = new HashMap<>();
                update.put("data", myLatLng.latitude + "," + myLatLng.longitude);
                update.put("timestamp", System.currentTimeMillis());
                myRef.child(userId).updateChildren(update);
            }
            if(marks.containsKey(snapshot.getKey()) && !Objects.equals(snapshot.getKey(), userId)) {
                Objects.requireNonNull(marks.get(snapshot.getKey())).setVisible(false);
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
            if(myOldLatLng == null || metersFromLatLng(myLatLng, myOldLatLng) > 5) {
                myOldLatLng = myLatLng;
                onLocationChanged(locationResult.getLastLocation());
                myMarker.setPosition(myLatLng);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(myLatLng));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 12.0f));
                while(marks.entrySet().iterator().hasNext()){
                    Circle v = marks.entrySet().iterator().next().getValue();
                    if(metersFromLatLng(v.getCenter(), myMarker.getPosition()) < 100){

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Notification.Builder nb = mNotificationUtils
                                        .getAndroidChannelNotification("Red Zone!", "Sei appena entrato in un assembramento!")
                                        .setAutoCancel(false)
                                        .setOnlyAlertOnce(true);
                                mNotificationUtils.getManager().notify(101, nb.build());
                            } else {
                                notificationManager.notify(101, assembramento.build());
                            }
                            return;
                    }
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
        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MapsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        frameLayout = findViewById(R.id.privacyLayout);



        TabHost tabs = findViewById(R.id.tabhost);
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
            WebView terms = findViewById(R.id.webview);
            terms.loadUrl("http://testesercitazioni.altervista.org/Disarray_Men/RedZone/Docs/terms___coditions.html");
            accept_button = findViewById(R.id.accept_button);
            decline_button = findViewById(R.id.decline_button);
            accept_button.setOnClickListener(v -> acceptBtn());
            decline_button.setOnClickListener(v -> declineBtn());
        } else {
            start();
        }
    }

    protected void close(){
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory( Intent.CATEGORY_HOME );
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
        finish();
        System.exit(0);
    }

    @Override
    protected void onDestroy() {
        exit_app = true;
        myRef.child(userId).removeEventListener(eventListenerUpdate);
        myRef.removeEventListener(updateOthersLocationsListener);
        myRef.removeEventListener(checkUsernameListener);
        DatabaseReference.CompletionListener completionListener = (error, ref) -> close();
        myRef.child(userId).removeValue(completionListener);
        super.onDestroy();
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("No", (dialog, id) -> dialog.cancel());
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public void start(){
        frameLayout.setVisibility(View.INVISIBLE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationUtils.pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        final LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );





        blt = findViewById(R.id.blt);
        gps = findViewById(R.id.gps);
        exit = findViewById(R.id.exit);

        text1 = findViewById(R.id.tabText);
        image1 = findViewById(R.id.imageView);
        image2 = findViewById((R.id.imageView2));

        gps.setOnClickListener(v -> setBtnGPS());
        blt.setOnClickListener(v -> flipBT());
        exit.setOnClickListener(v -> exit());

        show = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_in_down);
        hide = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_out_up);

        text1.startAnimation(hide);
        text1.setVisibility(View.GONE);
        image1.startAnimation(hide);
        image1.setVisibility(View.GONE);
        image2.startAnimation(hide);
        image2.setVisibility(View.GONE);

        database = FirebaseDatabase.getInstance();
        myRef = database.getReference().child("userLocations");
        createUsername();

        if (isPermissionAccess()) {
            getCurrentLocation();

            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            assert mapFragment != null;
            mapFragment.getMapAsync(this);
        }
        if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            buildAlertMessageNoGps();
        }
        Intent intent = new Intent(this, BluetoothActivity.class);
        startActivity(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationUtils = new NotificationUtils(this);
        }

        if (!isPermissionAccess()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 99);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 99);
            }
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

    /*
    public void createLastKnownLocation(){
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        @SuppressLint("MissingPermission") Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        @SuppressLint("MissingPermission") Location netLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (gpsLocation != null) {
            currentlatitude = gpsLocation.getLatitude();
            currentlongitude = gpsLocation.getLongitude();
        } else if (netLocation != null) {
            currentlatitude = netLocation.getLatitude();
            currentlongitude = netLocation.getLongitude();
        }
        myLatLng = new LatLng(currentlatitude, currentlongitude);
        myMarker = mMap.addMarker(new MarkerOptions()
                .position(myLatLng)
                .title("Tu sei qui"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(myLatLng));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 12.0f));
    }*/

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
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        getCurrentLocation();
        startLocationUpdates();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
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
        return Math.abs(d * 1000); // meters
    }

    public void assembraCerchi() {
        Iterator<Circle> it1 = marks.values().iterator();
        int assembraMiniFound = 0;
        String circleKey = null;
        while (it1.hasNext()) {
            Circle circle1 = it1.next();
            for (Circle circle2 : marks.values()) {
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
                Objects.requireNonNull(marks.get(circleKey)).setVisible(true);
            } else if(assembraMiniFound > 0){
                Objects.requireNonNull(marks.get(circleKey)).setVisible(true);
                Objects.requireNonNull(marks.get(circleKey)).setRadius(raggioCerchio*assembraMiniFound);
                assembraMiniFound = 0;
            }
        }
    }

    private void flipBT() {
        if (BluetoothActivity.mBluetoothAdapter.isEnabled()) {
            BluetoothActivity.mBluetoothAdapter.disable();
            blt.setBackgroundColor(Color.parseColor("#E91E1E"));
        } else {
            ensureDiscoverable();
            BluetoothActivity.mBluetoothAdapter.enable();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 98);
        }
    }

    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600);
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            sleep(36000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        ensureDiscoverable();
                    }
                }.start();
            } else{
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            }
            startActivity(discoverableIntent);
        }
        new Thread() {
            @Override
            public void run() {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                changeColorButtonBluetooth();
            }
        }.start();
    }
    static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    public void changeColorButtonBluetooth(){
        if (mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE && mBluetoothAdapter.isEnabled()) {
            blt.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            blt.setBackgroundColor(Color.parseColor("#E91E1E"));
            new Thread() {
                @Override
                public void run() {
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    changeColorButtonBluetooth();
                    ensureDiscoverable();
                }
            }.start();
        }
    }

    public void acceptBtn() {
        SharedPreferences.Editor editor = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("privacy", true);
        editor.apply();
        start();
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 99);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 99);
        }

    }

    public void declineBtn() {
        exit();
    }

    public void exit(){
        onDestroy();
    }

    public void setBtnGPS() {
        if (!btnGPSClick) {
            text1.startAnimation(show);
            image1.startAnimation(show);
            image2.startAnimation(show);
            text1.setVisibility(View.VISIBLE);
            image1.setVisibility(View.VISIBLE);
            image2.setVisibility(View.VISIBLE);
            getFusedLocationProviderClient(this).removeLocationUpdates(mLocationCallback);
            btnGPSClick = true;
            gps.setBackgroundColor(Color.parseColor("#E91E1E"));
        } else {
            text1.startAnimation(hide);
            image1.startAnimation(hide);
            image2.startAnimation(hide);
            text1.setVisibility(View.GONE);
            image1.setVisibility(View.GONE);
            image2.setVisibility(View.GONE);
            btnGPSClick = false;
            gps.setBackgroundColor(Color.parseColor("#4CAF50"));
        }
    }

}
