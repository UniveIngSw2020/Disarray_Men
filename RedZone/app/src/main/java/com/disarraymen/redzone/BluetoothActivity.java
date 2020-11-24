package com.disarraymen.redzone;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static android.os.AsyncTask.execute;
import static android.provider.Settings.Global.DEVICE_NAME;


public class BluetoothActivity extends MapsActivity  implements BeaconConsumer {

    Button btn4;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    ListView listView;
    private ArrayList<String> mDeviceList = new ArrayList<String>();
    private BeaconManager beaconManager;
    protected double distance = 0;


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceToAdd = device.getName() + "\n" + device.getAddress() + "\n" + device.getBondState() + "\n";
                if(device != null && device.getName() != null && !mDeviceList.contains(deviceToAdd)) {
                    mDeviceList.add(deviceToAdd);
                }
                Log.i("BT", device.getName() + "\n" + device.getAddress());
                listView.setAdapter(new ArrayAdapter<String>(context,
                        android.R.layout.simple_list_item_1, mDeviceList));
            }
        }
    };

    NotificationCompat.Builder distanze = new NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_alert)
            //.setLargeIcon(bitmap)
            .setContentTitle("Red Zone!")
            .setContentText("Sei troppo vicino a un'altro soggetto!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(NotificationUtils.pendingIntent)
            .setAutoCancel(true)//Vibration
            .setColor(Color.RED)
            .setOnlyAlertOnce(true)
            .setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000 })
            //.setSound(Uri.parse("uri://notification.mp3"))
            .setLights(Color.RED, 3000, 3000);

    NotificationManager notificationManager;

    @SuppressLint("HardwareIds")
    private String getBluetoothMac(final Context context) {

        String result = null;
        if (context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Hardware ID are restricted in Android 6+
                // https://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id
                // Getting bluetooth mac via reflection for devices with Android 6+
                result = android.provider.Settings.Secure.getString(context.getContentResolver(),
                        "bluetooth_address");
            } else {
                BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
                result = bta != null ? bta.getAddress() : "";
            }
        }
        toastMe(result);
        return result;
    }

    @SuppressLint("ResourceType")
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

        btn4 = (Button) findViewById(R.id.btn5);
        btn4.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(final View v) {
                setBtn4();
            }
        });
        btn4.setClickable(true);
        btn4.setVisibility(View.VISIBLE);

        listView = (ListView) findViewById(R.id.listView2);

        checkBluetoothDevices();

        ensureDiscoverable();

        beaconManager.bind(this);

        @SuppressLint("HardwareIds") Beacon beacon = new Beacon.Builder()
                .setId1("2f234454-cf6d-4a0f-adf2-f4911ba9ffa6")
                .setId2("1")
                .setId3("2")
                .setManufacturer(0x0118) // Radius Networks.  Change this for other beacon layouts
                .setTxPower(-59)
                .setDataFields(Arrays.asList(0l)) // Remove this for beacon layouts without d: fields
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


    public void setBtn4() {

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            toastMe("Device does not support Bluetooth");
        } else if (!mBluetoothAdapter.isEnabled()) {
            // Bluetooth is not enabled :)
            flipBT();
            toastMe("Bluetooth is not enabled :)");
        } else {
            // Bluetooth is enabled
            checkBluetoothDevices();
            toastMe("Bluetooth is enabled ");
        }
    }

    public void checkBluetoothDevices() {
        mDeviceList.clear();
        listView.clearChoices();
        mBluetoothAdapter.startDiscovery();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);
    }

    BluetoothDevice bluetoothDevice;
    private boolean findDevice(String address) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                toastMe(device.getAddress()+"-----"+address);
                if (device.getAddress().equals(address)) {
                    //bluetoothDevice = device;
                    return true;
                }
            }
        }
        return false;
    }

    private void flipBT() {
        //if (mBluetoothAdapter.isEnabled()) {
            //mBluetoothAdapter.disable();
        //} else {
            //mBluetoothAdapter.enable();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 98);
        //}
    }


    private boolean isBTPermissionAccess() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED);
    }


    private void toastMe(String str){
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    private void ensureDiscoverable() {
        //toastMe("discoverable");
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


    NotificationUtils mNotificationUtils;
    ArrayList<Beacon> beaconsNotified = new ArrayList<>();

    protected static final String TAG = "RangingActivity";
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    Beacon beacon = beacons.iterator().next();
                    distance = beacon.getDistance();
                    //toastMe("The first beacon I see is about "+distance+" meters away.");
                    if (!beaconsNotified.contains(beacon) && distance < 1 && !findDevice(beacon.getBluetoothAddress())){
                        beaconsNotified.add(beacon);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Notification.Builder nb = mNotificationUtils.getAndroidChannelNotification("Red Zone!", "Sei troppo vicino a un'altro soggetto!");
                            mNotificationUtils.getManager().notify(1, nb.build());
                        } else {
                            notificationManager.notify(1, distanze.build());
                        }
                        //toastMe(String.valueOf(distance));
                    } else if (beaconsNotified.contains(beacon) && distance > 1) {
                        beaconsNotified.remove(beacon);
                    }
                    //Log.i(TAG, "The first beacon I see is about "+beacons.iterator().next().getDistance()+" meters away.");
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {    }
    }
}