package com.disarraymen.redzone;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private double currentlatitude;
    private double currentlongitude;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);

        if (!isPermisionAccess()) {
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 99);
        }
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
        if (isPermisionAccess()) {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                @SuppressLint("MissingPermission") Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                @SuppressLint("MissingPermission") Location netLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (gpsLocation != null) {
                    currentlatitude = gpsLocation.getLatitude();
                    currentlongitude = gpsLocation.getLongitude();
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER , 1000, 0, locationListenerGPS);
                } else if (netLocation != null) {
                    currentlatitude = netLocation.getLatitude();
                    currentlongitude = netLocation.getLongitude();
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER , 1000, 0, locationListenerGPS);

                }
            }
        }

    }

    private boolean isPermisionAccess() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }


    LocationListener locationListenerGPS = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String msg = "Latitude: " + latitude + ", Longitude: " + longitude;
            // Write a message to the database
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference myRef = database.getReference("usersLocations");
            // Read from the database
            myRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    // This method is called once with the initial value and again
                    // whenever data at this location is updated.
                    String value = dataSnapshot.getValue(String.class);
                    //Log.d(TAG, "Value is: " + value);
                    Toast.makeText(getBaseContext(), value, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Failed to read value
                    //Log.w(TAG, "Failed to read value.", error.toException());
                }
            });
            //Write
            myRef.setValue(msg);
            //Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
        }
    };
}
