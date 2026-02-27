package com.healthcare.aarogyanidaan;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.healthcare.aarogyanidaan.databinding.ActivityPatienthealthdatadisplayBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PatienthealthdatadisplayActivity extends AppCompatActivity {

    private static final String TAG = "PatientActivity";

    private ActivityPatienthealthdatadisplayBinding binding;
    private BluetoothDataService bluetoothService;
    private boolean serviceBound = false;

    private String patientId;

    // ── Chart ─────────────────────────────────────────────────────────────────
    private LineChart heartrateChart;
    private final List<Entry> heartrateEntries = new ArrayList<>();
    private LineDataSet heartrateDataSet;
    private LineData lineData;
    private int xIndex = 0;

    // ── Permission launcher ───────────────────────────────────────────────────
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean g : result.values()) if (!g) { allGranted = false; break; }
                        if (allGranted) showScanBottomSheet();
                        else Toast.makeText(this,
                                "Bluetooth permission required.", Toast.LENGTH_LONG).show();
                    });

    // ── Service connection ────────────────────────────────────────────────────
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bluetoothService = ((BluetoothDataService.LocalBinder) service).getService();
            serviceBound = true;

            if (patientId != null && !patientId.isEmpty()) {
                bluetoothService.setPatientId(patientId);
                Log.d(TAG, "Service bound → patientId set: " + patientId);
            } else {
                Log.e(TAG, "patientId still null at service bind — will retry when resolved");
            }

            updateConnectionUI(bluetoothService.getConnectionState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
            serviceBound = false;
            updateConnectionUI(BluetoothDataService.STATE_DISCONNECTED);
        }
    };

    // ── Broadcast receiver ────────────────────────────────────────────────────
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) return;
            switch (intent.getAction()) {
                case BluetoothDataService.ACTION_CONNECTION_STATE_CHANGED:
                    int state = intent.getIntExtra(
                            BluetoothDataService.EXTRA_CONNECTION_STATE,
                            BluetoothDataService.STATE_DISCONNECTED);
                    updateConnectionUI(state);
                    break;

                case BluetoothDataService.ACTION_HEART_RATE_DATA:
                    int bpm = intent.getIntExtra(BluetoothDataService.EXTRA_HEART_RATE, 0);
                    onBpmReceived(bpm);
                    break;
            }
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPatienthealthdatadisplayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupChart();
        setupClickListeners();
        registerLocalReceiver();
        updateConnectionUI(BluetoothDataService.STATE_DISCONNECTED);

        // ── STEP 1: Try Intent first (if launched with patientId) ─────────────
        String intentPatientId = getIntent().getStringExtra("patientId");
        if (intentPatientId != null && !intentPatientId.isEmpty()) {
            Log.d(TAG, "patientId from Intent: " + intentPatientId);
            onPatientIdResolved(intentPatientId);
        } else {
            // ── STEP 2: Fallback — fetch from FirebaseAuth + DB ───────────────
            Log.d(TAG, "No patientId in Intent — fetching from Firebase Auth");
            fetchPatientIdFromFirebase();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, BluetoothDataService.class);
        if (patientId != null) serviceIntent.putExtra(BluetoothDataService.EXTRA_PATIENT_ID, patientId);
        startServiceCompat(serviceIntent);

        bindService(
                new Intent(this, BluetoothDataService.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (serviceBound && bluetoothService != null &&
                bluetoothService.getConnectionState() == BluetoothDataService.STATE_CONNECTED) {
            Toast.makeText(this,
                    "Monitoring continues in background.", Toast.LENGTH_SHORT).show();
        }
        super.onBackPressed();
    }

    // ── Resolve patientId ─────────────────────────────────────────────────────

    /**
     * Fetches patientId from Firebase using the current logged-in user's UID.
     *
     * Firebase structure:
     *   patient/{uid}/patient_id  ← OR uid itself IS the patientId
     *
     * Based on your structure: patient/{uid}/patient_id = uid
     * So we can just use FirebaseAuth.getInstance().getCurrentUser().getUid() directly.
     */
    private void fetchPatientIdFromFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "No logged-in Firebase user found!");
            Toast.makeText(this,
                    "Not logged in. Please log in again.",
                    Toast.LENGTH_LONG).show();
            // Optionally redirect to login:
            // startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String uid = currentUser.getUid();
        Log.d(TAG, "Firebase Auth UID: " + uid);

        // Based on your Firebase structure:
        // patient/{uid}/patient_id = uid  → so uid IS the patientId
        // But let's read it from DB to be safe and confirm
        DatabaseReference patientRef = FirebaseDatabase.getInstance()
                .getReference("patient")
                .child(uid);

        patientRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "No patient record found for UID: " + uid);
                    // UID itself is the patientId based on your structure
                    // patient_id field equals the UID in your Firebase
                    Log.d(TAG, "Using UID directly as patientId: " + uid);
                    onPatientIdResolved(uid);
                    return;
                }

                // Try reading patient_id field first
                String pid = snapshot.child("patient_id").getValue(String.class);

                if (pid != null && !pid.isEmpty()) {
                    Log.d(TAG, "patientId from DB field: " + pid);
                    onPatientIdResolved(pid);
                } else {
                    // Fallback: use UID directly (matches your Firebase structure)
                    Log.d(TAG, "patient_id field empty, using UID: " + uid);
                    onPatientIdResolved(uid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase read error: " + error.getMessage());
                // Still try with UID as fallback
                Log.d(TAG, "Fallback: using UID as patientId: " + uid);
                onPatientIdResolved(uid);
            }
        });
    }

    /**
     * Called once patientId is known — from Intent or Firebase lookup.
     * Sets up the service and starts everything.
     */
    private void onPatientIdResolved(String resolvedId) {
        patientId = resolvedId;
        Log.d(TAG, "✅ patientId resolved: " + patientId);

        // Push to service if already bound
        if (serviceBound && bluetoothService != null) {
            bluetoothService.setPatientId(patientId);
            Log.d(TAG, "Pushed patientId to already-bound service");
        }

        // Also start/update the service intent with the patientId
        Intent serviceIntent = new Intent(this, BluetoothDataService.class);
        serviceIntent.putExtra(BluetoothDataService.EXTRA_PATIENT_ID, patientId);
        startServiceCompat(serviceIntent);
    }

    // ── Click listeners ───────────────────────────────────────────────────────
    private void setupClickListeners() {
        binding.backButton.setOnClickListener(v -> onBackPressed());

        binding.connectBluetoothButton.setOnClickListener(v -> {
            if (!serviceBound || bluetoothService == null) {
                ensureServiceRunning();
                return;
            }
            int state = bluetoothService.getConnectionState();
            if (state == BluetoothDataService.STATE_CONNECTED
                    || state == BluetoothDataService.STATE_CONNECTING) {
                bluetoothService.disconnect();
            } else {
                if (isDevicePaired(BluetoothDataService.DEVICE_NAME)) {
                    triggerConnect();
                } else {
                    Toast.makeText(this,
                            "ESP32 not paired. Long-press to scan.",
                            Toast.LENGTH_LONG).show();
                    showScanBottomSheet();
                }
            }
        });

        binding.connectBluetoothButton.setOnLongClickListener(v -> {
            showScanBottomSheet();
            return true;
        });
    }

    private void triggerConnect() {
        Intent i = new Intent(this, BluetoothDataService.class);
        i.setAction(BluetoothDataService.ACTION_CONNECT);
        if (patientId != null) i.putExtra(BluetoothDataService.EXTRA_PATIENT_ID, patientId);
        startServiceCompat(i);
    }

    // ── Chart setup ───────────────────────────────────────────────────────────
    private void setupChart() {
        heartrateChart = binding.heartrateChart;
        heartrateChart.getDescription().setEnabled(false);
        heartrateChart.setTouchEnabled(true);
        heartrateChart.setDragEnabled(true);
        heartrateChart.setScaleEnabled(true);
        heartrateChart.setPinchZoom(true);
        heartrateChart.setDrawGridBackground(false);
        heartrateChart.setBackgroundColor(Color.TRANSPARENT);
        heartrateChart.getLegend().setEnabled(false);

        XAxis xAxis = heartrateChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);

        YAxis left = heartrateChart.getAxisLeft();
        left.setAxisMinimum(40f);
        left.setAxisMaximum(180f);
        left.setDrawGridLines(true);
        left.setGridColor(Color.parseColor("#22000000"));
        left.setTextColor(Color.DKGRAY);
        heartrateChart.getAxisRight().setEnabled(false);

        heartrateDataSet = new LineDataSet(heartrateEntries, "BPM");
        heartrateDataSet.setColor(Color.RED);
        heartrateDataSet.setCircleColor(Color.RED);
        heartrateDataSet.setLineWidth(2.5f);
        heartrateDataSet.setCircleRadius(3f);
        heartrateDataSet.setDrawCircleHole(false);
        heartrateDataSet.setValueTextSize(0f);
        heartrateDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        heartrateDataSet.setDrawFilled(true);
        heartrateDataSet.setFillColor(Color.RED);
        heartrateDataSet.setFillAlpha(30);

        lineData = new LineData(heartrateDataSet);
        heartrateChart.setData(lineData);
        heartrateChart.invalidate();
    }

    private void registerLocalReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDataService.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDataService.ACTION_HEART_RATE_DATA);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    // ── BPM received ──────────────────────────────────────────────────────────
    private void onBpmReceived(int bpm) {
        binding.heartrateValueText.setText(bpm > 0 ? String.valueOf(bpm) : "---");

        heartrateEntries.add(new Entry(xIndex++, bpm));
        if (heartrateEntries.size() > 60) heartrateEntries.remove(0);

        heartrateDataSet.notifyDataSetChanged();
        lineData.notifyDataChanged();
        heartrateChart.notifyDataSetChanged();
        heartrateChart.moveViewToX(xIndex);
        heartrateChart.invalidate();

        if (serviceBound && bluetoothService != null) {
            int max = bluetoothService.getHighestHeartRate();
            double avg = bluetoothService.getAverageBpm();
            binding.avgheartrate.setText(
                    String.format(Locale.getDefault(),
                            "Avg: %.0f BPM  |  Max: %d BPM", avg, max));
        }
    }

    private void clearChart() {
        heartrateEntries.clear();
        xIndex = 0;
        if (heartrateDataSet != null) heartrateDataSet.notifyDataSetChanged();
        if (lineData != null) lineData.notifyDataChanged();
        if (heartrateChart != null) {
            heartrateChart.notifyDataSetChanged();
            heartrateChart.invalidate();
        }
        binding.heartrateValueText.setText("---");
        binding.avgheartrate.setText("Avg: ---  |  Max: ---");
    }

    // ── Connection UI ─────────────────────────────────────────────────────────
    private void updateConnectionUI(int state) {
        switch (state) {
            case BluetoothDataService.STATE_CONNECTED:
                binding.bluetoothStatusText.setText("Connected ✓");
                binding.bluetoothStatusText.setTextColor(Color.parseColor("#4CAF50"));
                setButtonStyle("#F44336", "Disconnect");
                break;
            case BluetoothDataService.STATE_CONNECTING:
                binding.bluetoothStatusText.setText("Connecting…");
                binding.bluetoothStatusText.setTextColor(Color.parseColor("#FF9800"));
                setButtonStyle("#9E9E9E", "Cancel");
                break;
            case BluetoothDataService.STATE_CONNECTION_FAILED:
                binding.bluetoothStatusText.setText("Connection Failed");
                binding.bluetoothStatusText.setTextColor(Color.parseColor("#F44336"));
                setButtonStyle("#1565C0", "Retry");
                clearChart();
                break;
            default:
                binding.bluetoothStatusText.setText("Disconnected");
                binding.bluetoothStatusText.setTextColor(Color.parseColor("#9E9E9E"));
                setButtonStyle("#1565C0", "Connect");
                clearChart();
                break;
        }
    }

    private void setButtonStyle(String hexColor, String label) {
        binding.connectBluetoothButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor(hexColor)));
        binding.connectBluetoothButton.setText(label);
        binding.connectBluetoothButton.setEnabled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean isDevicePaired(String name) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) return false;
        for (BluetoothDevice d : adapter.getBondedDevices())
            if (name.equals(d.getName())) return true;
        return false;
    }

    private void ensureServiceRunning() {
        Intent i = new Intent(this, BluetoothDataService.class);
        if (patientId != null) i.putExtra(BluetoothDataService.EXTRA_PATIENT_ID, patientId);
        startServiceCompat(i);
        bindService(new Intent(this, BluetoothDataService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startServiceCompat(Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private void showScanBottomSheet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION});
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
                return;
            }
        }

        DeviceScanBottomSheet sheet = new DeviceScanBottomSheet();
        sheet.setOnDeviceSelectedListener(device -> {
            ensureServiceRunning();
            Intent i = new Intent(this, BluetoothDataService.class);
            i.setAction(BluetoothDataService.ACTION_CONNECT_TO_DEVICE);
            i.putExtra(BluetoothDataService.EXTRA_DEVICE_ADDRESS, device.getAddress());
            if (patientId != null)
                i.putExtra(BluetoothDataService.EXTRA_PATIENT_ID, patientId);
            startServiceCompat(i);
        });
        sheet.show(getSupportFragmentManager(), "DeviceScan");
    }
}