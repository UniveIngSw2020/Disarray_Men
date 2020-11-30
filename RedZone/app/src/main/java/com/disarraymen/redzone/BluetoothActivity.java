package com.disarraymen.redzone;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;


public class BluetoothActivity extends MapsActivity  implements BeaconConsumer {

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private ArrayList<String> mDeviceList;
    private BeaconManager beaconManager;
    protected double distance = 0;
    NotificationUtils mNotificationUtils;
    ArrayList<Beacon> beaconsNotified = new ArrayList<>();
    protected static final String TAG = "RangingActivity";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device != null && device.getName() != null) {
                    String deviceToAdd = device.getName() + "\n" + device.getAddress() + "\n" + device.getBondState() + "\n";
                    if(!mDeviceList.contains(deviceToAdd)) {
                        mDeviceList.add(deviceToAdd);
                    }
                }
            }
        }
    };

    NotificationCompat.Builder distanze = new NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_alert)
            .setContentTitle("Red Zone!")
            .setContentText("Sei troppo vicino a un'altro soggetto!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(NotificationUtils.pendingIntent)
            .setAutoCancel(true)
            .setColor(Color.RED)
            .setOnlyAlertOnce(true)
            .setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000 })
            //.setSound(Uri.parse("uri://notification.mp3"))
            .setLights(Color.RED, 3000, 3000);

    NotificationManager notificationManager;

    public BluetoothActivity() {
        mDeviceList = new ArrayList<>();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationUtils.pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationUtils = new NotificationUtils(this);
        }


        beaconManager = BeaconManager.getInstanceForApplication(this);
        checkBluetoothDevices();

        ensureDiscoverable();

        beaconManager.bind(this);

        @SuppressLint("HardwareIds") Beacon beacon = new Beacon.Builder()
                .setId1("2f234454-cf6d-4a0f-adf2-f4911ba9ffa6")
                .setId2("1")
                .setId3("2")
                .setManufacturer(0x0118) // Radius Networks.  Change this for other beacon layouts
                .setTxPower(-59)
                .setDataFields(Collections.singletonList(0L)) // Remove this for beacon layouts without d: fields
                .build();
        // Change the layout below for other beacon types
        BeaconParser beaconParser = new BeaconParser()
                .setBeaconLayout("m:2-3=beac,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25");
        BeaconTransmitter beaconTransmitter = new BeaconTransmitter(getApplicationContext(), beaconParser);
        beaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Advertisement start failed with code: "+errorCode);
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "Advertisement start succeeded.");
            }
        });
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        beaconManager.unbind(this);
        super.onDestroy();
    }

    public void checkBluetoothDevices() {
        mDeviceList.clear();
        mBluetoothAdapter.startDiscovery();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
    }

    private boolean findDevice(String address) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                toastMe(device.getAddress()+"-----"+address);
                if (device.getAddress().equals(address)) {
                    return true;
                }
            }
        }
        return false;
    }


    private void toastMe(String str){
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
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
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier((beacons, region) -> {
            if (beacons.size() > 0) {
                Beacon beacon = beacons.iterator().next();
                distance = beacon.getDistance();
                if (!beaconsNotified.contains(beacon) && distance < 1 && !findDevice(beacon.getBluetoothAddress())){
                    beaconsNotified.add(beacon);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Notification.Builder nb = mNotificationUtils.getAndroidChannelNotification("Red Zone!", "Sei troppo vicino a un'altro soggetto!");
                        mNotificationUtils.getManager().notify(1, nb.build());
                    } else {
                        notificationManager.notify(1, distanze.build());
                    }
                } else if (beaconsNotified.contains(beacon) && distance > 1) {
                    beaconsNotified.remove(beacon);
                }
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

}