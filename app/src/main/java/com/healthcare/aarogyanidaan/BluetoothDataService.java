package com.healthcare.aarogyanidaan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BluetoothDataService extends Service {

    private static final String TAG      = "BluetoothDataService";
    private static final UUID   SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public  static final String DEVICE_NAME = "ESP32_HeartRate";

    private static final String CHANNEL_ID = "BT_HR_CHANNEL";
    private static final int    NOTIF_ID   = 1;

    // ── States ────────────────────────────────────────────────────────────────
    public static final int STATE_DISCONNECTED      = 0;
    public static final int STATE_CONNECTING        = 1;
    public static final int STATE_CONNECTED         = 2;
    public static final int STATE_CONNECTION_FAILED = 3;

    // ── Intent actions ────────────────────────────────────────────────────────
    public static final String ACTION_CONNECT                  = "ACTION_CONNECT";
    public static final String ACTION_CONNECT_TO_DEVICE        = "ACTION_CONNECT_TO_DEVICE";
    public static final String ACTION_DISCONNECT               = "ACTION_DISCONNECT";
    public static final String ACTION_CONNECTION_STATE_CHANGED = "ACTION_CONNECTION_STATE_CHANGED";
    public static final String ACTION_HEART_RATE_DATA          = "ACTION_HEART_RATE_DATA";

    // ── Extras ────────────────────────────────────────────────────────────────
    public static final String EXTRA_CONNECTION_STATE = "EXTRA_CONNECTION_STATE";
    public static final String EXTRA_HEART_RATE       = "EXTRA_HEART_RATE";
    public static final String EXTRA_TIMESTAMP        = "EXTRA_TIMESTAMP";
    public static final String EXTRA_DEVICE_ADDRESS   = "EXTRA_DEVICE_ADDRESS";
    public static final String EXTRA_PATIENT_ID       = "EXTRA_PATIENT_ID";

    // ── Runtime ───────────────────────────────────────────────────────────────
    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket  bluetoothSocket;
    private ConnectThread    connectThread;
    private ReadThread       readThread;

    private int  connectionState = STATE_DISCONNECTED;
    private int  currentBpm      = 0;
    private int  highestBpm      = 0;
    private int  readingCount    = 0;
    private long totalBpm        = 0;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private DatabaseReference firebaseRef = null;
    private String            patientId   = null;

    // Buffer BPM readings that arrived before patientId was set
    private final List<Integer> pendingBpmQueue = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    public class LocalBinder extends Binder {
        public BluetoothDataService getService() { return BluetoothDataService.this; }
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public int    getConnectionState()  { return connectionState; }
    public int    getCurrentHeartRate() { return currentBpm; }
    public int    getHighestHeartRate() { return highestBpm; }
    public int    getReadingCount()     { return readingCount; }
    public double getAverageBpm()       { return readingCount > 0 ? (double) totalBpm / readingCount : 0; }
    public String getPatientId()        { return patientId; }

    // ── Called by Activity to set Firebase target ─────────────────────────────
    /**
     * MUST be called before (or just after) connecting.
     * Firebase path: patient_health_data/{patientId}/
     */
    public void setPatientId(String id) {
        if (id == null || id.isEmpty()) {
            Log.e(TAG, "setPatientId called with null/empty id — ignoring");
            return;
        }
        this.patientId = id;
        firebaseRef = FirebaseDatabase.getInstance()
                .getReference("patient_health_data")
                .child(id);

        Log.d(TAG, "✅ Firebase ref set → patient_health_data/" + id);

        // Flush any BPM readings that arrived before patientId was set
        if (!pendingBpmQueue.isEmpty()) {
            Log.d(TAG, "Flushing " + pendingBpmQueue.size() + " buffered BPM reading(s)");
            for (int bpm : pendingBpmQueue) {
                pushBpmToFirebase(bpm, System.currentTimeMillis());
            }
            pendingBpmQueue.clear();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        createNotificationChannel();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("Heart Rate Monitor running…"));

        if (intent == null) return START_STICKY;

        // Always grab patientId from the intent if present
        String pid = intent.getStringExtra(EXTRA_PATIENT_ID);
        if (pid != null && !pid.isEmpty()) {
            setPatientId(pid);   // sets firebaseRef immediately
        }

        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            connectToDeviceByName(DEVICE_NAME);
        } else if (ACTION_CONNECT_TO_DEVICE.equals(action)) {
            String address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
            if (address != null) connectToDeviceByAddress(address);
        } else if (ACTION_DISCONNECT.equals(action)) {
            disconnect();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        Log.d(TAG, "Service destroyed");
    }

    // ── Connection ────────────────────────────────────────────────────────────
    public void connectToDeviceByName(String name) {
        if (!isBluetoothReady()) return;
        BluetoothDevice target = null;
        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d : paired) {
            if (name.equals(d.getName())) { target = d; break; }
        }
        if (target == null) {
            Log.e(TAG, "Device '" + name + "' not in paired list");
            broadcastState(STATE_CONNECTION_FAILED);
            return;
        }
        startConnectThread(target);
    }

    public void connectToDeviceByAddress(String address) {
        if (!isBluetoothReady()) return;
        try {
            startConnectThread(bluetoothAdapter.getRemoteDevice(address));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Bad BT address: " + address);
            broadcastState(STATE_CONNECTION_FAILED);
        }
    }

    private boolean isBluetoothReady() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            broadcastState(STATE_CONNECTION_FAILED);
            return false;
        }
        return true;
    }

    private void startConnectThread(BluetoothDevice device) {
        broadcastState(STATE_CONNECTING);
        stopThreads();
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    public void disconnect() {
        stopThreads();
        if (bluetoothSocket != null) {
            try { bluetoothSocket.close(); } catch (IOException ignored) {}
            bluetoothSocket = null;
        }
        broadcastState(STATE_DISCONNECTED);
    }

    private void stopThreads() {
        if (connectThread != null) { connectThread.cancel(); connectThread = null; }
        if (readThread    != null) { readThread.cancel();    readThread    = null; }
    }

    // ── Connect Thread ────────────────────────────────────────────────────────
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final String          deviceName;

        ConnectThread(BluetoothDevice device) {
            deviceName = device.getName() != null ? device.getName() : device.getAddress();
            BluetoothSocket tmp = null;
            try { tmp = device.createRfcommSocketToServiceRecord(SPP_UUID); }
            catch (IOException e) { Log.e(TAG, "Socket create failed", e); }
            socket = tmp;
        }

        @Override
        public void run() {
            if (socket == null) { broadcastState(STATE_CONNECTION_FAILED); return; }
            bluetoothAdapter.cancelDiscovery();
            try {
                socket.connect();
                bluetoothSocket = socket;
                broadcastState(STATE_CONNECTED);
                updateNotification("Connected · " + deviceName);
                Log.d(TAG, "BT connected to " + deviceName);
                readThread = new ReadThread(socket);
                readThread.start();
            } catch (IOException e) {
                Log.e(TAG, "Connect failed: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}
                broadcastState(STATE_CONNECTION_FAILED);
            }
        }

        void cancel() { try { socket.close(); } catch (IOException ignored) {} }
    }

    // ── Read Thread ───────────────────────────────────────────────────────────
    private class ReadThread extends Thread {
        private final BluetoothSocket socket;
        private volatile boolean running = true;

        ReadThread(BluetoothSocket s) { socket = s; }

        @Override
        public void run() {
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    Log.d(TAG, "ESP32 RX → " + trimmed);
                    parseLine(trimmed);
                }
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Read error: " + e.getMessage());
                    broadcastState(STATE_CONNECTION_FAILED);
                }
            }
        }

        void cancel() {
            running = false;
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Parse ESP32 line ──────────────────────────────────────────────────────
    /**
     * ESP32 must send lines in the format:   BPM:72
     *
     * 1. Updates local stats
     * 2. Broadcasts to PatienthealthdatadisplayActivity (live chart)
     * 3. Pushes to Firebase → doctor sees it in real time
     */
    private void parseLine(String line) {
        if (!line.startsWith("BPM:")) {
            Log.w(TAG, "Unknown format, skipping: " + line);
            return;
        }
        try {
            int bpm = Integer.parseInt(line.substring(4).trim());
            currentBpm = bpm;

            if (bpm > 0) {
                readingCount++;
                totalBpm += bpm;
                if (bpm > highestBpm) highestBpm = bpm;
            }

            long ts = System.currentTimeMillis();

            // ── STEP 1: broadcast locally to patient's Activity ───────────────
            Intent intent = new Intent(ACTION_HEART_RATE_DATA);
            intent.putExtra(EXTRA_HEART_RATE, bpm);
            intent.putExtra(EXTRA_TIMESTAMP,  ts);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            Log.d(TAG, "Broadcast BPM=" + bpm + " to patient Activity");

            // ── STEP 2: push to Firebase so doctor sees it ────────────────────
            if (firebaseRef != null) {
                pushBpmToFirebase(bpm, ts);
            } else {
                // patientId not set yet — buffer it
                Log.w(TAG, "firebaseRef is null — buffering BPM=" + bpm);
                pendingBpmQueue.add(bpm);
            }

            updateNotification("Heart Rate: " + bpm + " BPM");

        } catch (NumberFormatException e) {
            Log.e(TAG, "Cannot parse BPM from: '" + line + "'");
        }
    }

    // ── Firebase write ────────────────────────────────────────────────────────
    /**
     * Writes to:
     *   patient_health_data/{patientId}/heartRate            ← live value (String)
     *   patient_health_data/{patientId}/heartRateLastUpdate  ← server timestamp
     *   patient_health_data/{patientId}/heartRateHistory/ts  ← history log
     *
     * chathealthdata.java listens to patient_health_data/{patientId}
     * and updates the doctor's UI the moment this write completes (~1s).
     */
    private void pushBpmToFirebase(int bpm, long ts) {
        if (firebaseRef == null) {
            Log.e(TAG, "pushBpmToFirebase: firebaseRef still null, aborting");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("heartRate",           String.valueOf(bpm));
        updates.put("heartRateLastUpdate", ServerValue.TIMESTAMP);

        firebaseRef.updateChildren(updates)
                .addOnSuccessListener(v ->
                        Log.d(TAG, "✅ Firebase updated: heartRate=" + bpm))
                .addOnFailureListener(e ->
                        Log.e(TAG, "❌ Firebase write FAILED: " + e.getMessage()));

        // History (optional, for graphs later)
        firebaseRef.child("heartRateHistory")
                .child(String.valueOf(ts))
                .setValue(bpm);

        Log.d(TAG, "Firebase push → patient_health_data/" + patientId + "/heartRate=" + bpm);
    }

    // ── State broadcast ───────────────────────────────────────────────────────
    private void broadcastState(int state) {
        connectionState = state;
        Intent i = new Intent(ACTION_CONNECTION_STATE_CHANGED);
        i.putExtra(EXTRA_CONNECTION_STATE, state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Heart Rate Monitor", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, PatienthealthdatadisplayActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Heart Rate Monitor")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIF_ID, buildNotification(text));
    }
}