package com.example.wifidirect;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver implements WifiP2pManager.ConnectionInfoListener {

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private MainActivity activity;
    private WifiP2pInfo info;

    private boolean startingActivity = false;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, MainActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @SuppressLint("MissingPermission")
    WifiP2pManager.PeerListListener myPeerListListener = peers -> {
        ArrayList<WifiP2pDevice> devices = new ArrayList<>();
        ArrayList<String> deviceNames = new ArrayList<>();
        if (peers.getDeviceList().isEmpty()) {
            activity.scanStateTextView.setText("scanning...");
        } else {
            activity.scanStateTextView.setText("found devices!");
        }
        for (WifiP2pDevice device : peers.getDeviceList()) {
            devices.add(device);
            deviceNames.add(device.deviceName);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, R.layout.row, deviceNames);
        activity.devicesListView.setAdapter(adapter);
        activity.devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            Log.e(MainActivity.TAG, "Clicked device");
            startingActivity = true;
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = devices.get((int) id).deviceAddress;
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Executors.newSingleThreadScheduledExecutor().submit(WiFiDirectBroadcastReceiver.this::startCameraActivityAfterInfo);
                }

                @Override
                public void onFailure(int reason) {
                }
            });
        });
    };

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                //Wifi direct mode is enabled
                activity.setIsWifiP2pEnabled(true);
            } else {
                activity.setIsWifiP2pEnabled(false);
                activity.resetData();
            }
            Log.d(MainActivity.TAG, "P2P state changed - " + state);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null) {
                manager.requestPeers(channel, myPeerListListener);
            }

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            if (manager == null) {
                return;
            }

            if (isNetworkAvailable(activity.getApplication(), intent)) {
                // we are connected with the other device, request connection
                // info to find group owner IP
//                manager.requestConnectionInfo(channel, WiFiDirectBroadcastReceiver.this);
                manager.requestConnectionInfo(channel, WiFiDirectBroadcastReceiver.this);
                if (!startingActivity)
                    Executors.newSingleThreadScheduledExecutor().submit(this::getInfo);
            } else {
                // It's a disconnect
                activity.resetData();
            }

        }  // Respond to this device's wifi state changing

    }

    private void deletePersistentGroups() {
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Delete any persistent group
                    for (int netid = 0; netid < 32; netid++) {
                        methods[i].invoke(manager, channel, netid, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Boolean isNetworkAvailable(Application application, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ConnectivityManager connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network nw = connectivityManager.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
            return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
        } else {
            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            return networkInfo.isConnected();
        }
    }

    private void runServer() {
        /*
         * Create a server socket and wait for client connections. This
         * call blocks until a connection is accepted from a client
         */
        Socket client=null;
        if (!startingActivity) {
            try {
                Log.e(MainActivity.TAG, "Server socket start in receiver");
                ServerSocket serverSocket = new ServerSocket(8888);
                client = serverSocket.accept();
                Log.e(MainActivity.TAG, "Server socket success in receiver");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(MainActivity.TAG, "Server error in receiver");
            }
        }

        File file = CameraUtils.getOutputMediaFile(CameraActivity.MEDIA_TYPE_IMAGE, activity);
        try {
            InputStream inputStream = Objects.requireNonNull(client).getInputStream();
            copyFile(inputStream,new FileOutputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runClient() {
        if (!startingActivity) {
            Socket socket = new Socket();
            try {
                Log.e(MainActivity.TAG, "Client socket start in receiver");
                socket.bind(null);
                socket.connect((new InetSocketAddress(info.groupOwnerAddress, 8888)));
                Log.e(MainActivity.TAG, "Client socket success in receiver");
            } catch (ConnectException exception) {
                Log.e(MainActivity.TAG, "No connection, retry in receiver");
                if (info.groupFormed)
                    Executors.newSingleThreadScheduledExecutor().schedule(this::runClient, 1, TimeUnit.SECONDS);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(MainActivity.TAG, "Client error");
            }

            File file = CameraUtils.getOutputMediaFile(CameraActivity.MEDIA_TYPE_IMAGE, activity);
            try {
                InputStream inputStream = Objects.requireNonNull(socket).getInputStream();
                copyFile(inputStream,new FileOutputStream(file));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "error in copyFile");
            return false;
        }
        return true;
    }

    private void startCameraActivityAfterInfo() {
        try {
            while (info == null) {
                Log.e(MainActivity.TAG, "wait for info for activity");
                TimeUnit.MILLISECONDS.sleep(300);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.e(MainActivity.TAG, "got info for activity");
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra("IP", info.groupOwnerAddress.getHostAddress() + " " + info.isGroupOwner);
//        intent.putExtra(INFO, info);
        activity.startActivity(intent);
    }

    //fixme odpalanie watka po wyjsciu z kamery
    private void getInfo() {
        try {
            while (info == null) {
                Log.e(MainActivity.TAG, "wait for info");
                TimeUnit.MILLISECONDS.sleep(300);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!startingActivity) {
            Log.e(MainActivity.TAG, "got info");
            if (info.isGroupOwner) {
                Executors.newSingleThreadScheduledExecutor().submit(this::runServer);
            } else {
                Executors.newSingleThreadScheduledExecutor().submit(this::runClient);
            }
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        this.info = info;
        Log.e(MainActivity.TAG, info.toString());
    }
}
