package com.healthcare.aarogyanidaan;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class patienthealthdata extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final String PREFS_NAME = "HealthDataPrefs";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Constants for chart data
    private static final int MAX_DATA_POINTS = 24; // 24 hours of data
    private static final int CHART_ANIMATION_DURATION = 1000; // 1 second animation

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private ExecutorService executorService;
    private Handler mainHandler;

    private ImageButton backpatchat;

    private TextView bluetoothStatusText;
    private TextView temperatureValueText, heartRateValueText,
            bloodPressureValueText, oxygenSaturationValueText;

    private DatabaseReference firebaseDatabase;
    private String currentPatientId;
    private SharedPreferences sharedPreferences;

    private List<BluetoothDevice> pairedDevicesList;

    private GestureManager gestureManager;

    private Button addMetricButton;
    private View additionalMetricsContainer;
    private LineChart heartRateChart, bloodPressureChart, oxygenSaturationChart, temperatureChart;

    // Store today's data for each metric
    private Map<String, List<Entry>> heartRateData = new HashMap<>();
    private Map<String, List<Entry>> systolicData = new HashMap<>();
    private Map<String, List<Entry>> diastolicData = new HashMap<>();
    private Map<String, List<Entry>> oxygenData = new HashMap<>();
    private Map<String, List<Entry>> temperatureData = new HashMap<>();

    // Store timestamp of last data reset
    private long lastResetTimestamp;

    // Metric normal ranges
    private final float[] HEART_RATE_RANGE = {60f, 100f};
    private final float[] BLOOD_PRESSURE_SYS_RANGE = {90f, 120f};
    private final float[] BLOOD_PRESSURE_DIA_RANGE = {60f, 80f};
    private final float[] OXYGEN_RANGE = {95f, 100f};
    private final float[] TEMPERATURE_RANGE = {36.1f, 37.2f};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patienthealthdata);

        backpatchat = findViewById(R.id.backpatdata);
        backpatchat.setOnClickListener(v -> onBackPressed());

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());

        currentPatientId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firebaseDatabase = FirebaseDatabase.getInstance().getReference("patient_health_data");

        // Initialize data maps with current date key
        String today = getCurrentDateKey();
        heartRateData.put(today, new ArrayList<>());
        systolicData.put(today, new ArrayList<>());
        diastolicData.put(today, new ArrayList<>());
        oxygenData.put(today, new ArrayList<>());
        temperatureData.put(today, new ArrayList<>());

        // Set last reset time to now
        lastResetTimestamp = System.currentTimeMillis();

        initializeComponents();
        initializeCharts();
        loadHealthDataFromFirebase();
        initializeBluetooth();

        gestureManager = new GestureManager(this);
        gestureManager.attachToView(findViewById(android.R.id.content));

        // Start checking for day change
        startDayChangeChecker();
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        checkBluetoothPermissions();
    }

    private void initializeComponents() {
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText);
        temperatureValueText = findViewById(R.id.temperatureValueText);
        heartRateValueText = findViewById(R.id.heartRateValueText);
        bloodPressureValueText = findViewById(R.id.bloodPressureValueText);
        oxygenSaturationValueText = findViewById(R.id.oxygenSaturationValueText);

        heartRateChart = findViewById(R.id.heartRateChart);
        bloodPressureChart = findViewById(R.id.bloodPressureChart);
        oxygenSaturationChart = findViewById(R.id.oxygenSaturationChart);
        temperatureChart = findViewById(R.id.temperatureChart);

        additionalMetricsContainer = findViewById(R.id.additionalMetricsContainer);
        addMetricButton = findViewById(R.id.addMetricButton);

        Button connectBluetoothButton = findViewById(R.id.connectBluetoothButton);
        connectBluetoothButton.setOnClickListener(v -> showBluetoothDeviceSelectionDialog());

        Button manualInputButton = findViewById(R.id.manualInputButton);
        manualInputButton.setOnClickListener(v -> showManualInputDialog());

        executorService = Executors.newSingleThreadExecutor();

        addMetricButton.setOnClickListener(v -> {
            if (additionalMetricsContainer.getVisibility() == View.VISIBLE) {
                additionalMetricsContainer.setVisibility(View.GONE);
                addMetricButton.setText("Add Metrics");
            } else {
                additionalMetricsContainer.setVisibility(View.VISIBLE);
                addMetricButton.setText("Hide Metrics");
            }
        });
    }

    private void initializeCharts() {
        setupChart(heartRateChart, new ArrayList<>(), "Heart Rate", Color.WHITE);
        setupChart(bloodPressureChart, new ArrayList<>(), "Blood Pressure", Color.WHITE);
        setupChart(oxygenSaturationChart, new ArrayList<>(), "Oxygen", Color.WHITE);
        setupChart(temperatureChart, new ArrayList<>(), "Temperature", Color.WHITE);
    }

    private void setupChart(LineChart chart, List<Entry> entries, String label, int color) {
        // Chart styling
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setExtraOffsets(10, 10, 10, 10);

        // Customize X axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatHourForAxis((int) value);
            }
        });

        // Customize Y axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.argb(50, 255, 255, 255));
        leftAxis.setAxisLineColor(Color.WHITE);

        // Disable right Y axis
        chart.getAxisRight().setEnabled(false);

        // Customize legend
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setTextColor(Color.WHITE);
        legend.setForm(Legend.LegendForm.LINE);

        // Create empty data set if no entries
        if (entries.isEmpty()) {
            // Add placeholder invisible data to initialize chart
            entries.add(new Entry(0, 0));
            LineDataSet dataSet = createDataSet(entries, label, color);
            dataSet.setVisible(false);

            LineData data = new LineData(dataSet);
            data.setValueTextColor(Color.WHITE);
            data.setValueTextSize(9f);
            chart.setData(data);
            chart.invalidate();
        } else {
            // Use real data
            updateChartData(chart, entries, label, color);
        }
    }

    private String formatHourForAxis(int hour) {
        return String.format(Locale.US, "%02d:00", hour);
    }

    private LineDataSet createDataSet(List<Entry> entries, String label, int color) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawFilled(true);
        dataSet.setFormLineWidth(1f);
        dataSet.setFormSize(15.f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        // Set fill gradient
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // Fill gradient: solid color to transparent
            dataSet.setFillDrawable(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {ColorUtils.setAlphaComponent(color, 180),
                            ColorUtils.setAlphaComponent(color, 10)}));
        } else {
            dataSet.setFillColor(ColorUtils.setAlphaComponent(color, 100));
        }

        return dataSet;
    }

    private void updateChartData(LineChart chart, List<Entry> entries, String label, int color) {
        LineDataSet dataSet;

        if (chart.getData() != null && chart.getData().getDataSetCount() > 0) {
            dataSet = (LineDataSet) chart.getData().getDataSetByIndex(0);
            dataSet.setValues(entries);
            dataSet.setLabel(label);
            dataSet.notifyDataSetChanged();
            chart.getData().notifyDataChanged();
        } else {
            dataSet = createDataSet(entries, label, color);
            LineData data = new LineData(dataSet);
            data.setValueTextColor(Color.WHITE);
            data.setValueTextSize(9f);
            chart.setData(data);
        }

        // Set visible range
        if (!entries.isEmpty()) {
            // If we have data, make sure Y axis shows appropriate range
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;
            for (Entry entry : entries) {
                if (entry.getY() < min) min = entry.getY();
                if (entry.getY() > max) max = entry.getY();
            }

            // Add some padding to min and max
            float padding = (max - min) * 0.2f;
            chart.getAxisLeft().setAxisMinimum(Math.max(0, min - padding));
            chart.getAxisLeft().setAxisMaximum(max + padding);

            // Set X axis range
            chart.getXAxis().setAxisMinimum(0);
            chart.getXAxis().setAxisMaximum(23); // 24 hours (0-23)
        }

        chart.animateX(CHART_ANIMATION_DURATION);
        chart.invalidate();
    }

    private void loadHealthDataFromFirebase() {
        firebaseDatabase.child(currentPatientId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String temperature = snapshot.child("temperature").getValue(String.class);
                    String heartRate = snapshot.child("heartRate").getValue(String.class);
                    String bloodPressure = snapshot.child("bloodPressure").getValue(String.class);
                    String oxygenSaturation = snapshot.child("oxygenSaturation").getValue(String.class);

                    updateHealthDataUI(
                            temperature != null ? temperature : "",
                            heartRate != null ? heartRate : "",
                            bloodPressure != null ? bloodPressure : "",
                            oxygenSaturation != null ? oxygenSaturation : ""
                    );

                    // Check for historical data
                    DataSnapshot historySnapshot = snapshot.child("history");
                    if (historySnapshot.exists()) {
                        processHistoricalData(historySnapshot);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(patienthealthdata.this,
                        "Failed to load health data", Toast.LENGTH_SHORT).show();
            }
        });

        // Listen for real-time updates
        firebaseDatabase.child(currentPatientId).child("realtime")
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        processRealtimeData(snapshot);
                    }

                    @Override
                    public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        processRealtimeData(snapshot);
                    }

                    @Override
                    public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                        // Not needed for this implementation
                    }

                    @Override
                    public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        // Not needed for this implementation
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(patienthealthdata.this,
                                "Real-time data update failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void processHistoricalData(DataSnapshot historySnapshot) {
        // Process historical data for charts
        for (DataSnapshot dateSnapshot : historySnapshot.getChildren()) {
            String dateKey = dateSnapshot.getKey();

            if (!heartRateData.containsKey(dateKey)) {
                heartRateData.put(dateKey, new ArrayList<>());
                systolicData.put(dateKey, new ArrayList<>());
                diastolicData.put(dateKey, new ArrayList<>());
                oxygenData.put(dateKey, new ArrayList<>());
                temperatureData.put(dateKey, new ArrayList<>());
            }

            for (DataSnapshot timeSnapshot : dateSnapshot.getChildren()) {
                try {
                    String timeKey = timeSnapshot.getKey();
                    int hourOfDay = Integer.parseInt(timeKey.split(":")[0]);

                    // Extract values if they exist
                    if (timeSnapshot.hasChild("heartRate")) {
                        float hr = Float.parseFloat(timeSnapshot.child("heartRate").getValue(String.class));
                        heartRateData.get(dateKey).add(new Entry(hourOfDay, hr));
                    }

                    if (timeSnapshot.hasChild("bloodPressure")) {
                        String[] bpParts = timeSnapshot.child("bloodPressure").getValue(String.class).split("/");
                        if (bpParts.length == 2) {
                            float systolic = Float.parseFloat(bpParts[0]);
                            float diastolic = Float.parseFloat(bpParts[1]);
                            systolicData.get(dateKey).add(new Entry(hourOfDay, systolic));
                            diastolicData.get(dateKey).add(new Entry(hourOfDay, diastolic));
                        }
                    }

                    if (timeSnapshot.hasChild("oxygenSaturation")) {
                        float oxygen = Float.parseFloat(timeSnapshot.child("oxygenSaturation").getValue(String.class));
                        oxygenData.get(dateKey).add(new Entry(hourOfDay, oxygen));
                    }

                    if (timeSnapshot.hasChild("temperature")) {
                        float temp = Float.parseFloat(timeSnapshot.child("temperature").getValue(String.class));
                        temperatureData.get(dateKey).add(new Entry(hourOfDay, temp));
                    }
                } catch (NumberFormatException e) {
                    Log.e("HealthData", "Error parsing historical data: " + e.getMessage());
                }
            }
        }

        // Update charts with today's data
        String today = getCurrentDateKey();
        updateAllCharts(today);
    }

    private void processRealtimeData(DataSnapshot dataSnapshot) {
        String today = getCurrentDateKey();
        Calendar calendar = Calendar.getInstance();
        int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);

        try {
            String metricType = dataSnapshot.getKey();
            String value = dataSnapshot.getValue(String.class);

            switch (metricType) {
                case "heartRate":
                    float hr = Float.parseFloat(value);
                    updateMetric(heartRateData, today, hourOfDay, hr);
                    heartRateValueText.setText(value);
                    updateHeartRateChart(today);
                    break;

                case "bloodPressure":
                    String[] bpParts = value.split("/");
                    if (bpParts.length == 2) {
                        float systolic = Float.parseFloat(bpParts[0]);
                        float diastolic = Float.parseFloat(bpParts[1]);
                        updateMetric(systolicData, today, hourOfDay, systolic);
                        updateMetric(diastolicData, today, hourOfDay, diastolic);
                        bloodPressureValueText.setText(value);
                        updateBloodPressureChart(today);
                    }
                    break;

                case "oxygenSaturation":
                    float oxygen = Float.parseFloat(value);
                    updateMetric(oxygenData, today, hourOfDay, oxygen);
                    oxygenSaturationValueText.setText(value);
                    updateOxygenSaturationChart(today);
                    break;

                case "temperature":
                    float temp = Float.parseFloat(value);
                    updateMetric(temperatureData, today, hourOfDay, temp);
                    temperatureValueText.setText(value);
                    updateTemperatureChart(today);
                    break;
            }

        } catch (NumberFormatException e) {
            Log.e("HealthData", "Error parsing realtime data: " + e.getMessage());
        }
    }

    private void updateMetric(Map<String, List<Entry>> dataMap, String dateKey, int hourOfDay, float value) {
        if (!dataMap.containsKey(dateKey)) {
            dataMap.put(dateKey, new ArrayList<>());
        }

        // Check if we already have a value for this hour
        List<Entry> dayData = dataMap.get(dateKey);
        boolean hourExists = false;

        for (int i = 0; i < dayData.size(); i++) {
            Entry entry = dayData.get(i);
            if ((int)entry.getX() == hourOfDay) {
                // Replace existing value
                dayData.set(i, new Entry(hourOfDay, value));
                hourExists = true;
                break;
            }
        }

        // If no entry for this hour, add it
        if (!hourExists) {
            dayData.add(new Entry(hourOfDay, value));
        }

        // Sort by hour
        Collections.sort(dayData, (e1, e2) -> Float.compare(e1.getX(), e2.getX()));
    }

    private void updateAllCharts(String dateKey) {
        updateHeartRateChart(dateKey);
        updateBloodPressureChart(dateKey);
        updateOxygenSaturationChart(dateKey);
        updateTemperatureChart(dateKey);
    }

    private void updateHeartRateChart(String dateKey) {
        if (heartRateData.containsKey(dateKey)) {
            List<Entry> entries = new ArrayList<>(heartRateData.get(dateKey));
            updateChartData(heartRateChart, entries, "Heart Rate", Color.rgb(209, 122, 234));

            // Update stats text below chart
            TextView averageText = findViewById(R.id.heartRateChart).getRootView()
                    .findViewWithTag("heart_rate_average");
            TextView rangeText = findViewById(R.id.heartRateChart).getRootView()
                    .findViewWithTag("heart_rate_range");

            if (averageText != null && rangeText != null) {
                float avg = calculateAverage(entries);
                averageText.setText(String.format(Locale.US, "Average: %.1f bpm", avg));
                rangeText.setText(String.format(Locale.US, "Range: %.0f-%.0f bpm",
                        HEART_RATE_RANGE[0], HEART_RATE_RANGE[1]));
            }
        }
    }

    private void updateBloodPressureChart(String dateKey) {
        if (systolicData.containsKey(dateKey) && diastolicData.containsKey(dateKey)) {
            LineData lineData = new LineData();

            // Add systolic data
            List<Entry> systolicEntries = new ArrayList<>(systolicData.get(dateKey));
            if (!systolicEntries.isEmpty()) {
                LineDataSet systolicSet = createDataSet(systolicEntries, "Systolic", Color.rgb(230, 132, 132));
                lineData.addDataSet(systolicSet);
            }

            // Add diastolic data
            List<Entry> diastolicEntries = new ArrayList<>(diastolicData.get(dateKey));
            if (!diastolicEntries.isEmpty()) {
                LineDataSet diastolicSet = createDataSet(diastolicEntries, "Diastolic", Color.rgb(132, 230, 132));
                lineData.addDataSet(diastolicSet);
            }

            if (lineData.getDataSetCount() > 0) {
                bloodPressureChart.setData(lineData);

                // Adjust Y axis
                float sysMin = calculateMin(systolicEntries);
                float sysMax = calculateMax(systolicEntries);
                float diaMin = calculateMin(diastolicEntries);
                float diaMax = calculateMax(diastolicEntries);

                float min = Math.min(sysMin, diaMin);
                float max = Math.max(sysMax, diaMax);
                float padding = (max - min) * 0.2f;

                bloodPressureChart.getAxisLeft().setAxisMinimum(Math.max(0, min - padding));
                bloodPressureChart.getAxisLeft().setAxisMaximum(max + padding);

                bloodPressureChart.animateX(CHART_ANIMATION_DURATION);
                bloodPressureChart.invalidate();

                // Update stats text below chart
                TextView systolicText = findViewById(R.id.bloodPressureChart).getRootView()
                        .findViewWithTag("blood_pressure_systolic");
                TextView diastolicText = findViewById(R.id.bloodPressureChart).getRootView()
                        .findViewWithTag("blood_pressure_diastolic");

                if (systolicText != null && diastolicText != null) {
                    float sysAvg = calculateAverage(systolicEntries);
                    float diaAvg = calculateAverage(diastolicEntries);
                    systolicText.setText(String.format(Locale.US, "Systolic: %.0f-%.0f",
                            BLOOD_PRESSURE_SYS_RANGE[0], BLOOD_PRESSURE_SYS_RANGE[1]));
                    diastolicText.setText(String.format(Locale.US, "Diastolic: %.0f-%.0f",
                            BLOOD_PRESSURE_DIA_RANGE[0], BLOOD_PRESSURE_DIA_RANGE[1]));
                }
            }
        }
    }

    private void updateOxygenSaturationChart(String dateKey) {
        if (oxygenData.containsKey(dateKey)) {
            List<Entry> entries = new ArrayList<>(oxygenData.get(dateKey));
            updateChartData(oxygenSaturationChart, entries, "Oxygen", Color.rgb(116, 185, 255));

            // Update stats text below chart
            TextView averageText = findViewById(R.id.oxygenSaturationChart).getRootView()
                    .findViewWithTag("oxygen_average");
            TextView rangeText = findViewById(R.id.oxygenSaturationChart).getRootView()
                    .findViewWithTag("oxygen_range");

            if (averageText != null && rangeText != null) {
                float avg = calculateAverage(entries);
                averageText.setText(String.format(Locale.US, "Average: %.1f%%", avg));
                rangeText.setText(String.format(Locale.US, "Range: %.0f-%.0f%%",
                        OXYGEN_RANGE[0], OXYGEN_RANGE[1]));
            }
        }
    }

    private void updateTemperatureChart(String dateKey) {
        if (temperatureData.containsKey(dateKey)) {
            List<Entry> entries = new ArrayList<>(temperatureData.get(dateKey));
            updateChartData(temperatureChart, entries, "Temperature", Color.rgb(255, 165, 0));

            // Update stats text below chart
            TextView averageText = findViewById(R.id.temperatureChart).getRootView()
                    .findViewWithTag("temperature_average");
            TextView rangeText = findViewById(R.id.temperatureChart).getRootView()
                    .findViewWithTag("temperature_range");

            if (averageText != null && rangeText != null) {
                float avg = calculateAverage(entries);
                averageText.setText(String.format(Locale.US, "Average: %.1f°C", avg));
                rangeText.setText(String.format(Locale.US, "Range: %.1f-%.1f°C",
                        TEMPERATURE_RANGE[0], TEMPERATURE_RANGE[1]));
            }
        }
    }

    private float calculateAverage(List<Entry> entries) {
        if (entries.isEmpty()) {
            return 0f;
        }

        float sum = 0f;
        for (Entry entry : entries) {
            sum += entry.getY();
        }
        return sum / entries.size();
    }

    private float calculateMin(List<Entry> entries) {
        if (entries.isEmpty()) {
            return 0f;
        }

        float min = Float.MAX_VALUE;
        for (Entry entry : entries) {
            if (entry.getY() < min) {
                min = entry.getY();
            }
        }
        return min;
    }

    private float calculateMax(List<Entry> entries) {
        if (entries.isEmpty()) {
            return 0f;
        }

        float max = Float.MIN_VALUE;
        for (Entry entry : entries) {
            if (entry.getY() > max) {
                max = entry.getY();
            }
        }
        return max;
    }

    private void updateHealthDataUI(String temperature, String heartRate,
                                    String bloodPressure, String oxygenSaturation) {
        temperatureValueText.setText(temperature);
        heartRateValueText.setText(heartRate);
        bloodPressureValueText.setText(bloodPressure);
        oxygenSaturationValueText.setText(oxygenSaturation);

        // Add data to charts if values are present
        Calendar calendar = Calendar.getInstance();
        int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
        String today = getCurrentDateKey();

        try {
            if (!heartRate.isEmpty()) {
                float hr = Float.parseFloat(heartRate);
                updateMetric(heartRateData, today, hourOfDay, hr);
            }

            if (!bloodPressure.isEmpty()) {
                String[] bpParts = bloodPressure.split("/");
                if (bpParts.length == 2) {
                    float systolic = Float.parseFloat(bpParts[0]);
                    float diastolic = Float.parseFloat(bpParts[1]);
                    updateMetric(systolicData, today, hourOfDay, systolic);
                    updateMetric(diastolicData, today, hourOfDay, diastolic);
                }
            }

            if (!oxygenSaturation.isEmpty()) {
                float oxygen = Float.parseFloat(oxygenSaturation);
                updateMetric(oxygenData, today, hourOfDay, oxygen);
            }

            if (!temperature.isEmpty()) {
                float temp = Float.parseFloat(temperature);
                updateMetric(temperatureData, today, hourOfDay, temp);
            }

            // Update all charts
            updateAllCharts(today);

        } catch (NumberFormatException e) {
            Log.e("HealthData", "Error updating UI with health data: " + e.getMessage());
        }
    }

    private String getCurrentDateKey() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return dateFormat.format(new Date());
    }

    private void startDayChangeChecker() {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable dayChecker = new Runnable() {
            @Override
            public void run() {
                checkForDayChange();
                // Check every minute
                handler.postDelayed(this, 60000);
            }
        };
        handler.post(dayChecker);
    }

    private void checkForDayChange() {
    Calendar now = Calendar.getInstance();
    Calendar lastReset = Calendar.getInstance();
    lastReset.setTimeInMillis(lastResetTimestamp);

    // Check if day has changed since last reset
    if (now.get(Calendar.DAY_OF_YEAR) != lastReset.get(Calendar.DAY_OF_YEAR) ||
            now.get(Calendar.YEAR) != lastReset.get(Calendar.YEAR)) {

        // It's a new day, reset data
        String today = getCurrentDateKey();
        heartRateData.put(today, new ArrayList<>());
        systolicData.put(today, new ArrayList<>());
        diastolicData.put(today, new ArrayList<>());
        oxygenData.put(today, new ArrayList<>());
        temperatureData.put(today, new ArrayList<>());

        // Initialize empty charts
        initializeCharts();

        // Update last reset time
        lastResetTimestamp = System.currentTimeMillis();

        // Save yesterday's data to Firebase
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String yesterdayKey = dateFormat.format(yesterday.getTime());

        // Only save data if we have it for yesterday
        if (heartRateData.containsKey(yesterdayKey)) {
            saveYesterdayDataToFirebase(yesterdayKey);
        }
    }
}

private void saveYesterdayDataToFirebase(String dateKey) {
    DatabaseReference historyRef = firebaseDatabase.child(currentPatientId).child("history").child(dateKey);

    // Combine all metrics into a structured format
    Map<String, Map<String, String>> dailyData = new HashMap<>();

    // Process heart rate data
    List<Entry> heartRateEntries = heartRateData.get(dateKey);
    if (heartRateEntries != null) {
        for (Entry entry : heartRateEntries) {
            int hour = (int) entry.getX();
            String timeKey = String.format(Locale.US, "%02d:00", hour);

            if (!dailyData.containsKey(timeKey)) {
                dailyData.put(timeKey, new HashMap<>());
            }

            dailyData.get(timeKey).put("heartRate", String.valueOf(entry.getY()));
        }
    }

    // Process blood pressure data
    List<Entry> systolicEntries = systolicData.get(dateKey);
    List<Entry> diastolicEntries = diastolicData.get(dateKey);

    if (systolicEntries != null && diastolicEntries != null) {
        // Create a map to match systolic with diastolic values by hour
        Map<Integer, Float> systolicByHour = new HashMap<>();
        Map<Integer, Float> diastolicByHour = new HashMap<>();

        for (Entry entry : systolicEntries) {
            systolicByHour.put((int) entry.getX(), entry.getY());
        }

        for (Entry entry : diastolicEntries) {
            diastolicByHour.put((int) entry.getX(), entry.getY());
        }

        // Find all hours that have both systolic and diastolic values
        Set<Integer> hoursWithBP = new HashSet<>(systolicByHour.keySet());
        hoursWithBP.retainAll(diastolicByHour.keySet());

        for (Integer hour : hoursWithBP) {
            String timeKey = String.format(Locale.US, "%02d:00", hour);

            if (!dailyData.containsKey(timeKey)) {
                dailyData.put(timeKey, new HashMap<>());
            }

            String bpValue = String.format(Locale.US, "%.0f/%.0f",
                    systolicByHour.get(hour),
                    diastolicByHour.get(hour));
            dailyData.get(timeKey).put("bloodPressure", bpValue);
        }
    }

    // Process oxygen data
    List<Entry> oxygenEntries = oxygenData.get(dateKey);
    if (oxygenEntries != null) {
        for (Entry entry : oxygenEntries) {
            int hour = (int) entry.getX();
            String timeKey = String.format(Locale.US, "%02d:00", hour);

            if (!dailyData.containsKey(timeKey)) {
                dailyData.put(timeKey, new HashMap<>());
            }

            dailyData.get(timeKey).put("oxygenSaturation", String.valueOf(entry.getY()));
        }
    }

    // Process temperature data
    List<Entry> temperatureEntries = temperatureData.get(dateKey);
    if (temperatureEntries != null) {
        for (Entry entry : temperatureEntries) {
            int hour = (int) entry.getX();
            String timeKey = String.format(Locale.US, "%02d:00", hour);

            if (!dailyData.containsKey(timeKey)) {
                dailyData.put(timeKey, new HashMap<>());
            }

            dailyData.get(timeKey).put("temperature", String.valueOf(entry.getY()));
        }
    }

    // Calculate daily averages
    Map<String, String> dailyAverages = calculateDailyAverages(dateKey);

    // Save data to Firebase
    if (!dailyData.isEmpty()) {
        // Save hourly data
        historyRef.setValue(dailyData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("HealthData", "Successfully saved yesterday's data");

                    // Save daily averages after hourly data is saved
                    if (!dailyAverages.isEmpty()) {
                        firebaseDatabase.child(currentPatientId)
                                .child("daily_averages")
                                .child(dateKey)
                                .setValue(dailyAverages)
                                .addOnSuccessListener(aVoid1 ->
                                        Log.d("HealthData", "Saved daily averages"))
                                .addOnFailureListener(e ->
                                        Log.e("HealthData", "Failed to save daily averages: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("HealthData", "Failed to save yesterday's data: " + e.getMessage()));
    }
}

private Map<String, String> calculateDailyAverages(String dateKey) {
    Map<String, String> averages = new HashMap<>();

    // Heart rate average
    List<Entry> heartRateEntries = heartRateData.get(dateKey);
    if (heartRateEntries != null && !heartRateEntries.isEmpty()) {
        float avg = calculateAverage(heartRateEntries);
        averages.put("heartRate", String.format(Locale.US, "%.1f", avg));
    }

    // Blood pressure average
    List<Entry> systolicEntries = systolicData.get(dateKey);
    List<Entry> diastolicEntries = diastolicData.get(dateKey);
    if (systolicEntries != null && !systolicEntries.isEmpty() &&
            diastolicEntries != null && !diastolicEntries.isEmpty()) {
        float sysAvg = calculateAverage(systolicEntries);
        float diaAvg = calculateAverage(diastolicEntries);
        averages.put("bloodPressure", String.format(Locale.US, "%.0f/%.0f", sysAvg, diaAvg));
    }

    // Oxygen saturation average
    List<Entry> oxygenEntries = oxygenData.get(dateKey);
    if (oxygenEntries != null && !oxygenEntries.isEmpty()) {
        float avg = calculateAverage(oxygenEntries);
        averages.put("oxygenSaturation", String.format(Locale.US, "%.1f", avg));
    }

    // Temperature average
    List<Entry> temperatureEntries = temperatureData.get(dateKey);
    if (temperatureEntries != null && !temperatureEntries.isEmpty()) {
        float avg = calculateAverage(temperatureEntries);
        averages.put("temperature", String.format(Locale.US, "%.1f", avg));
    }

    return averages;
}

private void checkBluetoothPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN},
                REQUEST_BLUETOOTH_PERMISSIONS);
    } else {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            pairedDevicesList = new ArrayList<>(bluetoothAdapter.getBondedDevices());
            updateBluetoothStatus("Ready to connect");
        }
    }
}

private void updateBluetoothStatus(String status) {
    mainHandler.post(() -> bluetoothStatusText.setText(status));
}

private void showBluetoothDeviceSelectionDialog() {
    if (bluetoothAdapter == null) {
        Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        return;
    }

    if (!bluetoothAdapter.isEnabled()) {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        return;
    }

    pairedDevicesList = new ArrayList<>(bluetoothAdapter.getBondedDevices());

    if (pairedDevicesList.isEmpty()) {
        Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
        return;
    }

    final String[] deviceNames = new String[pairedDevicesList.size()];
    for (int i = 0; i < pairedDevicesList.size(); i++) {
        deviceNames[i] = pairedDevicesList.get(i).getName();
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Select Health Monitor Device");
    builder.setItems(deviceNames, (dialog, which) -> {
        connectToDevice(pairedDevicesList.get(which));
    });
    builder.setCancelable(true);
    builder.show();
}

private void connectToDevice(BluetoothDevice device) {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
    }
    updateBluetoothStatus("Connecting to " + device.getName() + "...");

    executorService.execute(() -> {
        try {
            // Close any existing connection
            if (bluetoothSocket != null) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    Log.e("HealthData", "Error closing previous socket", e);
                }
            }

            // Create a socket connection to the device
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();

            updateBluetoothStatus("Connected to " + device.getName());

            // Start reading data
            readHealthData();

        } catch (IOException e) {
            Log.e("HealthData", "Connection failed", e);
            updateBluetoothStatus("Connection failed: " + e.getMessage());

            // Cleanup on failure
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException closeException) {
                Log.e("HealthData", "Error closing socket", closeException);
            }
        }
    });
}

private void readHealthData() {
    if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
        updateBluetoothStatus("Not connected");
        return;
    }

    executorService.execute(() -> {
        byte[] buffer = new byte[1024];
        int bytes;

        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read from the InputStream
                bytes = inputStream.read(buffer);
                String data = new String(buffer, 0, bytes);

                // Process the data
                processBluetoothData(data);

            } catch (IOException e) {
                Log.e("HealthData", "Error reading data", e);
                updateBluetoothStatus("Connection lost");
                break;
            }
        }
    });
}

private void processBluetoothData(String data) {
    // Example format: HR:72;BP:120/80;OS:98;TEMP:36.8
    // Split the data by semicolons
    String[] metrics = data.split(";");

    String temperature = "";
    String heartRate = "";
    String bloodPressure = "";
    String oxygenSaturation = "";

    for (String metric : metrics) {
        String[] parts = metric.split(":");
        if (parts.length == 2) {
            String key = parts[0].trim();
            String value = parts[1].trim();

            switch (key) {
                case "HR":
                    heartRate = value;
                    break;
                case "BP":
                    bloodPressure = value;
                    break;
                case "OS":
                    oxygenSaturation = value;
                    break;
                case "TEMP":
                    temperature = value;
                    break;
            }
        }
    }

    // Update Firebase with new data
    final String finalTemperature = temperature;
    final String finalHeartRate = heartRate;
    final String finalBloodPressure = bloodPressure;
    final String finalOxygenSaturation = oxygenSaturation;

    mainHandler.post(() -> {
        DatabaseReference patientRef = firebaseDatabase.child(currentPatientId);

        if (!finalTemperature.isEmpty()) {
            patientRef.child("temperature").setValue(finalTemperature);
            patientRef.child("realtime").child("temperature").setValue(finalTemperature);
        }

        if (!finalHeartRate.isEmpty()) {
            patientRef.child("heartRate").setValue(finalHeartRate);
            patientRef.child("realtime").child("heartRate").setValue(finalHeartRate);
        }

        if (!finalBloodPressure.isEmpty()) {
            patientRef.child("bloodPressure").setValue(finalBloodPressure);
            patientRef.child("realtime").child("bloodPressure").setValue(finalBloodPressure);
        }

        if (!finalOxygenSaturation.isEmpty()) {
            patientRef.child("oxygenSaturation").setValue(finalOxygenSaturation);
            patientRef.child("realtime").child("oxygenSaturation").setValue(finalOxygenSaturation);
        }
    });
}

private void showManualInputDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    LayoutInflater inflater = getLayoutInflater();
    View dialogView = inflater.inflate(R.layout.dialog_manual_input, null);
    builder.setView(dialogView);

    EditText temperatureInput = dialogView.findViewById(R.id.temperatureInput);
    EditText heartRateInput = dialogView.findViewById(R.id.heartRateInput);
    EditText systolicInput = dialogView.findViewById(R.id.systolicInput);
    EditText diastolicInput = dialogView.findViewById(R.id.diastolicInput);
    EditText oxygenInput = dialogView.findViewById(R.id.oxygenInput);

    builder.setTitle("Manual Health Data Input")
            .setPositiveButton("Save", (dialog, id) -> {
                // Validate and save data
                saveManualInputData(
                        temperatureInput.getText().toString().trim(),
                        heartRateInput.getText().toString().trim(),
                        systolicInput.getText().toString().trim(),
                        diastolicInput.getText().toString().trim(),
                        oxygenInput.getText().toString().trim()
                );
            })
            .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());

    AlertDialog dialog = builder.create();
    dialog.show();
}

private void saveManualInputData(String temperature, String heartRate,
                                 String systolic, String diastolic, String oxygen) {
    // Validate input values
    boolean hasValidData = false;
    String bloodPressure = "";

    // Process blood pressure if both systolic and diastolic are provided
    if (!systolic.isEmpty() && !diastolic.isEmpty()) {
        try {
            float sys = Float.parseFloat(systolic);
            float dia = Float.parseFloat(diastolic);

            if (sys > 0 && dia > 0) {
                bloodPressure = systolic + "/" + diastolic;
                hasValidData = true;
            }
        } catch (NumberFormatException e) {
            // Invalid input, ignore
        }
    }

    // Validate temperature
    boolean validTemperature = false;
    if (!temperature.isEmpty()) {
        try {
            float temp = Float.parseFloat(temperature);
            if (temp >= 34 && temp <= 42) {  // Valid human temperature range
                validTemperature = true;
                hasValidData = true;
            } else {
                Toast.makeText(this, "Temperature out of valid range", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid temperature format", Toast.LENGTH_SHORT).show();
        }
    }

    // Validate heart rate
    boolean validHeartRate = false;
    if (!heartRate.isEmpty()) {
        try {
            float hr = Float.parseFloat(heartRate);
            if (hr > 0 && hr < 250) {  // Valid human heart rate range
                validHeartRate = true;
                hasValidData = true;
            } else {
                Toast.makeText(this, "Heart rate out of valid range", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid heart rate format", Toast.LENGTH_SHORT).show();
        }
    }

    // Validate oxygen
    boolean validOxygen = false;
    if (!oxygen.isEmpty()) {
        try {
            float ox = Float.parseFloat(oxygen);
            if (ox > 0 && ox <= 100) {  // Valid oxygen saturation range
                validOxygen = true;
                hasValidData = true;
            } else {
                Toast.makeText(this, "Oxygen saturation out of valid range", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid oxygen saturation format", Toast.LENGTH_SHORT).show();
        }
    }

    // Only proceed if we have at least one valid data point
    if (hasValidData) {
        DatabaseReference patientRef = firebaseDatabase.child(currentPatientId);

        // Update main values
        if (validTemperature) {
            patientRef.child("temperature").setValue(temperature);
            patientRef.child("realtime").child("temperature").setValue(temperature);
        }

        if (validHeartRate) {
            patientRef.child("heartRate").setValue(heartRate);
            patientRef.child("realtime").child("heartRate").setValue(heartRate);
        }

        if (!bloodPressure.isEmpty()) {
            patientRef.child("bloodPressure").setValue(bloodPressure);
            patientRef.child("realtime").child("bloodPressure").setValue(bloodPressure);
        }

        if (validOxygen) {
            patientRef.child("oxygenSaturation").setValue(oxygen);
            patientRef.child("realtime").child("oxygenSaturation").setValue(oxygen);
        }

        Toast.makeText(this, "Health data updated", Toast.LENGTH_SHORT).show();
    } else {
        Toast.makeText(this, "No valid data provided", Toast.LENGTH_SHORT).show();
    }
}

@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == REQUEST_ENABLE_BT) {
        if (resultCode == RESULT_OK) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            pairedDevicesList = new ArrayList<>(bluetoothAdapter.getBondedDevices());
            updateBluetoothStatus("Ready to connect");
        } else {
            updateBluetoothStatus("Bluetooth not enabled");
            Toast.makeText(this, "Bluetooth must be enabled to connect to devices",
                    Toast.LENGTH_SHORT).show();
        }
    }
}

@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                       @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                pairedDevicesList = new ArrayList<>(bluetoothAdapter.getBondedDevices());
                updateBluetoothStatus("Ready to connect");
            }
        } else {
            updateBluetoothStatus("Bluetooth permissions required");
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
        }
    }
}

@Override
protected void onDestroy() {
    super.onDestroy();

    // Close the Bluetooth connection
    if (bluetoothSocket != null) {
        try {
            bluetoothSocket.close();
        } catch (IOException e) {
            Log.e("HealthData", "Error closing socket", e);
        }
    }

    // Shutdown the executor service
    if (executorService != null) {
        executorService.shutdown();
    }
}

// Inner class for gesture management (swipe gestures)
private class GestureManager extends GestureDetector.SimpleOnGestureListener {
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    private GestureDetector gestureDetector;
    private Context context;

    public GestureManager(Context context) {
        this.context = context;
        this.gestureDetector = new GestureDetector(context, this);
    }

    public void attachToView(View view) {
        view.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2,
                           float velocityX, float velocityY) {
        try {
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                // Left or right swipe
                if (Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Right swipe - go back
                        onBackPressed();
                    }
                }
            }

        } catch (Exception exception) {
            Log.e("GestureManager", "Error in onFling", exception);
        }

        return false;
    }
}
}

//    private void loadHealthDataFromFirebase() {
//        firebaseDatabase.child(currentPatientId).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (snapshot.exists()) {
//                    String temperature = snapshot.child("temperature").getValue(String.class);
//                    String heartRate = snapshot.child("heartRate").getValue(String.class);
//                    String bloodPressure = snapshot.child("bloodPressure").getValue(String.class);
//                    String oxygenSaturation = snapshot.child("oxygenSaturation").getValue(String.class);
//
//                    updateHealthDataUI(
//                            temperature != null ? temperature : "",
//                            heartRate != null ? heartRate : "",
//                            bloodPressure != null ? bloodPressure : "",
//                            oxygenSaturation != null ? oxygenSaturation : ""
//                    );
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Toast.makeText(patienthealthdata.this,
//                        "Failed to load health data", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//
//
//    private void receiveBluetoothData() {
//        try {
//            byte[] buffer = new byte[1024];
//            while (!Thread.currentThread().isInterrupted()) {
//                int bytes = inputStream.read(buffer);
//                String receivedData = new String(buffer, 0, bytes);
//
//                mainHandler.post(() -> parseHealthData(receivedData));
//                Thread.sleep(1000);
//            }
//        } catch (IOException | InterruptedException e) {
//            mainHandler.post(this::handleBluetoothDisconnection);
//        }
//    }
//
//    private void parseHealthData(String data) {
//        String[] healthMetrics = data.split(",");
//        if (healthMetrics.length == 4) {
//            if (validateHealthData(healthMetrics[0], healthMetrics[1],
//                    healthMetrics[2], healthMetrics[3])) {
//                saveHealthData(
//                        healthMetrics[0],
//                        healthMetrics[1],
//                        healthMetrics[2],
//                        healthMetrics[3]
//                );
//            }
//        }
//    }
//
//    private boolean validateHealthData(String healthMetric, String healthMetric1, String healthMetric2, String healthMetric3) {
//        return true;
//    }
//
//    private void handleBluetoothDisconnection() {
//        bluetoothStatusText.setText("Disconnected");
//        bluetoothStatusText.setTextColor(
//                ContextCompat.getColor(this, android.R.color.holo_red_dark)
//        );
//        showBluetoothConnectionFailureDialog();
//    }
//
//    private void showBluetoothConnectionFailureDialog() {
//        new AlertDialog.Builder(this)
//                .setTitle("Bluetooth Connection Failed")
//                .setMessage("Unable to connect to Bluetooth device. Would you like to retry or input data manually?")
//                .setPositiveButton("Retry", (dialog, which) -> showBluetoothDeviceSelectionDialog())
//                .setNeutralButton("Manual Input", (dialog, which) -> showManualInputDialog())
//                .setNegativeButton("Cancel", null)
//                .show();
//    }
//
//    private void updateHealthDataUI(String temperature, String heartRate,
//                                    String bloodPressure, String oxygenSaturation) {
//        temperatureValueText.setText(temperature);
//        heartRateValueText.setText(heartRate);
//        bloodPressureValueText.setText(bloodPressure);
//        oxygenSaturationValueText.setText(oxygenSaturation);
//    }
//
//    private void showManualInputDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        LayoutInflater inflater = getLayoutInflater();
//        View dialogView = inflater.inflate(R.layout.activity_manual_input, null);
//
//        EditText temperatureInput = dialogView.findViewById(R.id.temperatureInput);
//        EditText heartRateInput = dialogView.findViewById(R.id.heartRateInput);
//        EditText bloodPressureInput = dialogView.findViewById(R.id.bloodPressureInput);
//        EditText oxygenSaturationInput = dialogView.findViewById(R.id.oxygenSaturationInput);
//
//        builder.setTitle("Manual Health Data Input")
//                .setView(dialogView)
//                .setPositiveButton("Save", (dialog, which) -> {
//                    String temperature = temperatureInput.getText().toString().trim();
//                    String heartRate = heartRateInput.getText().toString().trim();
//                    String bloodPressure = bloodPressureInput.getText().toString().trim();
//                    String oxygenSaturation = oxygenSaturationInput.getText().toString().trim();
//
//                    // Updated validation to handle partial data input
//                    if (validatePartialHealthData(temperature, heartRate, bloodPressure, oxygenSaturation)) {
//                        saveHealthData(temperature, heartRate, bloodPressure, oxygenSaturation);
//                    } else {
//                        Toast.makeText(this, "Please enter valid health data", Toast.LENGTH_SHORT).show();
//                    }
//                })
//                .setNegativeButton("Cancel", null)
//                .create()
//                .show();
//    }
//
//    private boolean validatePartialHealthData(String temperature, String heartRate,
//                                              String bloodPressure, String oxygenSaturation) {
//        // If any field is empty, it's allowed
//        if (temperature.isEmpty() && heartRate.isEmpty() &&
//                bloodPressure.isEmpty() && oxygenSaturation.isEmpty()) {
//            return false;
//        }
//
//        // Validate non-empty fields
//        try {
//            if (!temperature.isEmpty()) {
//                double temp = Double.parseDouble(temperature);
//                if (temp < 35.0 || temp > 42.0) {
//                    Toast.makeText(this, "Temperature must be between 35.0°C and 42.0°C", Toast.LENGTH_SHORT).show();
//                    return false;
//                }
//            }
//
//            if (!heartRate.isEmpty()) {
//                int hr = Integer.parseInt(heartRate);
//                if (hr < 40 || hr > 180) {
//                    Toast.makeText(this, "Heart rate must be between 40 and 180 bpm", Toast.LENGTH_SHORT).show();
//                    return false;
//                }
//            }
//
//            if (!bloodPressure.isEmpty() && !validateBloodPressure(bloodPressure)) {
//                Toast.makeText(this, "Blood pressure must be in the format 'systolic/diastolic'", Toast.LENGTH_SHORT).show();
//                return false;
//            }
//
//            if (!oxygenSaturation.isEmpty()) {
//                int oxygen = Integer.parseInt(oxygenSaturation);
//                if (oxygen < 80 || oxygen > 100) {
//                    Toast.makeText(this, "Oxygen saturation must be between 80% and 100%", Toast.LENGTH_SHORT).show();
//                    return false;
//                }
//            }
//
//            return true;
//        } catch (NumberFormatException e) {
//            return false;
//        }
//    }
//
//    private boolean validateBloodPressure(String bloodPressure) {
//        // Check format (e.g., "120/80")
//        if (!bloodPressure.matches("\\d{2,3}/\\d{2,3}")) {
//            return false;
//        }
//
//        // Split systolic and diastolic
//        String[] parts = bloodPressure.split("/");
//        int systolic = Integer.parseInt(parts[0]);
//        int diastolic = Integer.parseInt(parts[1]);
//
//        // Validate ranges (safe bounds)
//        return systolic >= 80 && systolic <= 200 &&
//                diastolic >= 50 && diastolic <= 130;
//    }
//
//    private void saveHealthData(String temperature, String heartRate,
//                                String bloodPressure, String oxygenSaturation) {
//        // Preserve existing data if new input is empty
//        DatabaseReference patientRef = firebaseDatabase.child(currentPatientId);
//
//        if (!temperature.isEmpty())
//            patientRef.child("temperature").setValue(temperature);
//
//        if (!heartRate.isEmpty())
//            patientRef.child("heartRate").setValue(heartRate);
//
//        if (!bloodPressure.isEmpty())
//            patientRef.child("bloodPressure").setValue(bloodPressure);
//
//        if (!oxygenSaturation.isEmpty())
//            patientRef.child("oxygenSaturation").setValue(oxygenSaturation);
//
//        // Refresh the displayed data
//        loadHealthDataFromFirebase();
//    }
//
//    private void checkBluetoothPermissions() {
//        List<String> permissionsNeeded = new ArrayList<>();
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
//                != PackageManager.PERMISSION_GRANTED) {
//            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
//        }
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
//                != PackageManager.PERMISSION_GRANTED) {
//            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
//        }
//
//        if (!permissionsNeeded.isEmpty()) {
//            ActivityCompat.requestPermissions(this,
//                    permissionsNeeded.toArray(new String[0]),
//                    REQUEST_BLUETOOTH_PERMISSIONS);
//            return;
//        }
//
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
//            boolean allGranted = true;
//            for (int result : grantResults) {
//                if (result != PackageManager.PERMISSION_GRANTED) {
//                    allGranted = false;
//                    break;
//                }
//            }
//
//            if (allGranted) {
//                // Permissions granted, enable Bluetooth if not already enabled
//                if (!bluetoothAdapter.isEnabled()) {
//                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                        // TODO: Consider calling
//                        //    ActivityCompat#requestPermissions
//                        // here to request the missing permissions, and then overriding
//                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                        //                                          int[] grantResults)
//                        // to handle the case where the user grants the permission. See the documentation
//                        // for ActivityCompat#requestPermissions for more details.
//                        return;
//                    }
//                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//                }
//            } else {
//                // Permissions denied
//                new AlertDialog.Builder(this)
//                        .setTitle("Bluetooth Connection Failed")
//                        .setMessage("Unable to connect to Bluetooth device. Would you like to retry or input data manually?")
//                        .setPositiveButton("Retry", (dialog, which) -> showBluetoothDeviceSelectionDialog())
//                        .setNeutralButton("Manual Input", (dialog, which) -> showManualInputDialog())
//                        .setNegativeButton("Cancel", null)
//                        .show();
//            }
//        }
//    }
//
//    private void showBluetoothDeviceSelectionDialog() {
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//            return;
//        }
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
//                != PackageManager.PERMISSION_GRANTED) {
//            checkBluetoothPermissions();
//            return;
//        }
//
//        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
//        if (pairedDevices.isEmpty()) {
//            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        pairedDevicesList = new ArrayList<>(pairedDevices);
//        String[] deviceNames = pairedDevicesList.stream()
//                .map(BluetoothDevice::getName)
//                .toArray(String[]::new);
//
//        new AlertDialog.Builder(this)
//                .setTitle("Select Bluetooth Device")
//                .setItems(deviceNames, (dialog, which) -> connectToBluetoothDevice(pairedDevicesList.get(which)))
//                .setNegativeButton("Cancel", null)
//                .create()
//                .show();
//    }
//
//    private void connectToBluetoothDevice(BluetoothDevice device) {
//        executorService.execute(() -> {
//            try {
//                if (bluetoothSocket != null) {
//                    bluetoothSocket.close();
//                }
//
//                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
//                        != PackageManager.PERMISSION_GRANTED) {
//                    ActivityCompat.requestPermissions(this,
//                            new String[]{Manifest.permission.BLUETOOTH_CONNECT},
//                            REQUEST_BLUETOOTH_PERMISSIONS); // Use your defined request code here
//                    return;
//                }
//                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
//                if (pairedDevices.isEmpty()) {
//                    Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
//                    showBluetoothConnectionFailureDialog();
//                    return;
//                }
//                if (!bluetoothAdapter.isEnabled()) {
//                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//                    return;
//                }
//
//                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
//                bluetoothSocket.connect();
//
//                inputStream = bluetoothSocket.getInputStream();
//
//                mainHandler.post(() -> {
//                    bluetoothStatusText.setText("Connected to " + device.getName());
//                    bluetoothStatusText.setTextColor(
//                            ContextCompat.getColor(this, android.R.color.holo_green_dark)
//                    );
//                });
//
//                receiveBluetoothData();
//            } catch (IOException e) {
//                mainHandler.post(() -> {
//                    bluetoothStatusText.setText("Connection Failed");
//                    bluetoothStatusText.setTextColor(
//                            ContextCompat.getColor(this, android.R.color.holo_red_dark)
//                    );
//                    showBluetoothConnectionFailureDialog();
//                });
//            }
//        });
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == REQUEST_ENABLE_BT) {
//            if (resultCode == RESULT_OK) {
//                // Bluetooth is now enabled, you can proceed with device connection
//                showBluetoothDeviceSelectionDialog();
//            } else {
//                // User declined to enable Bluetooth
//                Toast.makeText(this,
//                        "Bluetooth must be enabled to connect devices",
//                        Toast.LENGTH_LONG).show();
//            }
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // Close Bluetooth socket and input stream
//        try {
//            if (inputStream != null) {
//                inputStream.close();
//            }
//            if (bluetoothSocket != null) {
//                bluetoothSocket.close();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        // Shutdown executor service
//        if (executorService != null) {
//            executorService.shutdownNow();
//        }
//    }
//}