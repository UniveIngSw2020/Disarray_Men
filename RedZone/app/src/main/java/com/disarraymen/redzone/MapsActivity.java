package com.disarraymen.redzone;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    public FirebaseDatabase database;
    public DatabaseReference myRef;
    private LocationManager locationManager;
    private double currentlatitude;
    private double currentlongitude;
    public String userId;
    private LatLng myLatLng;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);
        database = FirebaseDatabase.getInstance();
        myRef = database.getReference().child("userLocations");

        if (!isPermissionAccess()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 99);
        }
        getCurrentLocation();
        startLocationUpdates();
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getCurrentLocation();
        Toast.makeText(this, "latitude:" + currentlatitude + " longitude:" + currentlongitude, Toast.LENGTH_SHORT).show();

        LatLng locationMarker = new LatLng(currentlatitude, currentlongitude);
        mMap.addMarker(new MarkerOptions()
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
                updateMyLocationOnDatabase();
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
        getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        // do work here
                        onLocationChanged(locationResult.getLastLocation());
                        updateOthersLocation();
                    }
                },
                Looper.myLooper());
    }


    public void onLocationChanged(Location location) {
        // New location has now been determined
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        //Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        // You can now create a LatLng Object for use with maps
        myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        updateMyLocationOnDatabase();
    }

    HashMap<String, Circle> marks = new HashMap();

    public void updateOthersLocation() {
        // Read from the database
        myRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if(!marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId){
                    String[] latlong = snapshot.child("data").getValue().toString().split(",");
                    LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                    Circle localMark = mMap.addCircle(new CircleOptions()
                            .center(pos)
                            .radius(1000)
                            .fillColor(0x44ff0000)
                            .strokeColor(0xffff0000)
                            .strokeWidth(0));
                    marks.put(snapshot.getKey(), localMark);

                    assembraCerchi(localMark, snapshot.getKey());
                    if(assembraFound > 0){
                        localMark.setRadius(localMark.getRadius()*assembraFound);
                        assembraFound = 0;
                    }

                    toastMe("added: " + snapshot.getKey());
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if(marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId) {
                    String[] latlong = snapshot.child("data").getValue().toString().split(",");
                    LatLng pos = new LatLng(Double.parseDouble(latlong[0]), Double.parseDouble(latlong[1]));
                    marks.get(snapshot.getKey()).setCenter(pos);

                    assembraCerchi(marks.get(snapshot.getKey()), snapshot.getKey());
                    if(assembraFound > 0) {
                        marks.get(snapshot.getKey()).setRadius(marks.get(snapshot.getKey()).getRadius() * assembraFound);
                        assembraFound = 0;
                    }

                    toastMe("changed: " + snapshot.getKey());
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                if(marks.containsKey(snapshot.getKey()) && snapshot.getKey() != userId) {
                    marks.get(snapshot.getKey()).setVisible(false);
                    marks.remove(snapshot.getKey());
                    toastMe("removed: " + snapshot.getKey());
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                toastMe("moved");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                toastMe("cancelled");
            }
        });
    }

    int assembraFound = 0;
    ArrayList<Circle> found = new ArrayList<>();

    public void assembraCerchi(final Circle localMark, String val){
        Iterator<Circle> it = marks.values().iterator();
        while(it.hasNext()){
            Circle circle = it.next();
            if(circle.getCenter().longitude-localMark.getCenter().longitude < 0.00002 ||
                    circle.getCenter().latitude-localMark.getCenter().latitude < 0.00002){
                //circle.setVisible(false);
                assembraFound++;
            } else if (!circle.isVisible() && (circle.getCenter().longitude-localMark.getCenter().longitude > 0.00002 ||
                    circle.getCenter().latitude-localMark.getCenter().latitude > 0.00002)) {
                circle.setVisible(true);
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
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }


    public void updateMyLocationOnDatabase() {
        if(userId == null) {userId = generateRandomString();}
        DatabaseReference userNameRef = myRef.child(userId);
        ValueEventListener eventListener = new ValueEventListener() {
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


        userNameRef.addListenerForSingleValueEvent(eventListener);
        //Toast.makeText(this, myLatLng.toString(), Toast.LENGTH_SHORT).show();
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
