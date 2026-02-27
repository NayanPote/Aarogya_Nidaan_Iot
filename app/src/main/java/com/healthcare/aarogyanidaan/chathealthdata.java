package com.healthcare.aarogyanidaan;

import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * DOCTOR'S DEVICE
 *
 * Real-time flow:
 *
 *  Patient phone (BluetoothDataService)
 *      └─► Firebase: patient_health_data/{patientId}/heartRate
 *                              │
 *                              │  (Firebase push, ~1 second)
 *                              ▼
 *                    chathealthdata.java  ◄── YOU ARE HERE
 *                    heartRateListener fires → updates UI instantly
 *
 * Steps inside this Activity:
 *  1. Read conversationId from Intent
 *  2. Fetch patientId from conversations/{conversationId}/patientId
 *  3. Attach ValueEventListener on patient_health_data/{patientId}
 *  4. Every time the patient's phone writes a new BPM → onDataChange fires here
 *  5. Update heartRateValueText, status badge, and last-update timestamp
 */
public class chathealthdata extends AppCompatActivity {

    private static final String TAG              = "ChatHealthData";
    private static final int    SPEECH_REQUEST   = 0;

    // ── Views ─────────────────────────────────────────────────────────────────
    private ImageButton backButton;
    private ImageButton clinicalNotessave;
    private ImageButton clinicalNotesedit;
    private ImageButton clinicalNotesmic;

    private EditText clinicalNotes;

    // Heart-rate card views
    private TextView heartRateValueText;   // big BPM number
    private TextView heartRateStatusText;  // "● LIVE" / "● Waiting…"
    private TextView heartRateLastUpdate;  // "Last update: HH:MM:SS"

    // Patient / report info
    private TextView patientIdText;
    private TextView patientNameText;
    private TextView patientage;
    private TextView doctorNameText;
    private TextView reportDate;
    private TextView reportTime;

    // ── State ─────────────────────────────────────────────────────────────────
    private String  conversationId;
    private String  patientId;
    private String  currentNotes  = "";
    private boolean isEditing     = false;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private DatabaseReference conversationRef;
    private DatabaseReference healthDataRef;

    private ValueEventListener conversationListener;
    private ValueEventListener heartRateListener;

    // ── Clock ─────────────────────────────────────────────────────────────────
    private Timer clockTimer;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chathealthdata);

        conversationId = getIntent().getStringExtra("conversationId");
        if (TextUtils.isEmpty(conversationId)) {
            Toast.makeText(this, "Error: conversationId missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupInitialState();
        setupButtonListeners();
        setupConversationListener();   // ← resolves patientId → attaches HR listener
        startClock();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeAllListeners();
        stopClock();
    }

    // ── View init ─────────────────────────────────────────────────────────────
    private void initViews() {
        backButton        = findViewById(R.id.backButton);
        clinicalNotes     = findViewById(R.id.clinicalNotes);
        clinicalNotessave = findViewById(R.id.clinicalNotessave);
        clinicalNotesedit = findViewById(R.id.clinicalNotesedit);
        clinicalNotesmic  = findViewById(R.id.clinicalNotesmic);

        heartRateValueText  = findViewById(R.id.heartRateValueText);
        heartRateStatusText = findViewById(R.id.heartRateStatusText);
        heartRateLastUpdate = findViewById(R.id.heartRateLastUpdate);

        patientIdText   = findViewById(R.id.PatientId);
        patientNameText = findViewById(R.id.patientNameText);
        patientage      = findViewById(R.id.patientage);
        doctorNameText  = findViewById(R.id.doctorName);
        reportDate      = findViewById(R.id.reportDate);
        reportTime      = findViewById(R.id.reportTime);
    }

    private void setupInitialState() {
        clinicalNotes.setEnabled(false);
        clinicalNotessave.setEnabled(false);
        clinicalNotesedit.setEnabled(true);
        clinicalNotesmic.setEnabled(true);
        showWaitingState();
    }

    // ── Button listeners ──────────────────────────────────────────────────────
    private void setupButtonListeners() {
        backButton.setOnClickListener(v -> onBackPressed());

        clinicalNotesedit.setOnClickListener(v -> setEditMode(true));

        clinicalNotessave.setOnClickListener(v -> {
            saveClinicalNotes(clinicalNotes.getText().toString().trim());
            setEditMode(false);
        });

        clinicalNotesmic.setOnClickListener(v -> startSpeechToText());
    }

    // ── STEP 1: resolve patientId from conversation ───────────────────────────
    private void setupConversationListener() {
        conversationRef = FirebaseDatabase.getInstance()
                .getReference("conversations")
                .child(conversationId);

        conversationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Conversation snapshot empty for id: " + conversationId);
                    return;
                }

                String newPatientId = snapshot.child("patientId").getValue(String.class);
                String doctorName   = snapshot.child("doctorName").getValue(String.class);
                String patientName  = snapshot.child("patientName").getValue(String.class);

                if (doctorName  != null) doctorNameText.setText(doctorName);
                if (patientName != null) patientNameText.setText(patientName);

                // ── STEP 2: attach heart-rate listener once we have patientId ─
                if (newPatientId != null && !newPatientId.equals(patientId)) {
                    patientId = newPatientId;
                    patientIdText.setText(patientId);
                    Log.d(TAG, "patientId resolved: " + patientId);

                    attachHeartRateListener(patientId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Conversation listener cancelled: " + error.getMessage());
                Toast.makeText(chathealthdata.this,
                        "Failed to load patient data", Toast.LENGTH_SHORT).show();
            }
        };

        conversationRef.addValueEventListener(conversationListener);
        Log.d(TAG, "Conversation listener attached for: " + conversationId);
    }

    // ── STEP 2: real-time heart-rate listener ─────────────────────────────────
    /**
     * Attaches to:  patient_health_data/{patientId}
     *
     * Firebase calls onDataChange():
     *  • Immediately with the current stored value (so the screen is never blank)
     *  • Again every time BluetoothDataService writes a new BPM on the patient's phone
     *
     * This is the core of the real-time sync — no polling, no refresh.
     */
    private void attachHeartRateListener(String pid) {
        // Remove stale listener if switching patients
        if (healthDataRef != null && heartRateListener != null) {
            healthDataRef.removeEventListener(heartRateListener);
            Log.d(TAG, "Old HR listener removed");
        }

        healthDataRef = FirebaseDatabase.getInstance()
                .getReference("patient_health_data")
                .child(pid);

        heartRateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No health data yet for patient: " + pid);
                    showWaitingState();
                    return;
                }

                // ── Read heartRate written by BluetoothDataService ────────────
                String hrValue = snapshot.child("heartRate").getValue(String.class);

                if (hrValue != null && !hrValue.isEmpty() && !hrValue.equals("0")) {
                    // ── Update the big BPM number ─────────────────────────────
                    heartRateValueText.setText(hrValue + " bpm");
                    heartRateValueText.setTextColor(Color.parseColor("#F44336")); // red = live

                    // ── Green LIVE badge ──────────────────────────────────────
                    if (heartRateStatusText != null) {
                        heartRateStatusText.setText("● LIVE");
                        heartRateStatusText.setTextColor(Color.parseColor("#4CAF50"));
                    }

                    // ── Timestamp of this update ──────────────────────────────
                    if (heartRateLastUpdate != null) {
                        String time = new SimpleDateFormat(
                                "hh:mm:ss a", Locale.getDefault()).format(new Date());
                        heartRateLastUpdate.setText("Last update: " + time);
                    }

                    Log.d(TAG, "✅ Doctor received real-time BPM: " + hrValue);

                } else {
                    showWaitingState();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "HR listener cancelled: " + error.getMessage());
                showWaitingState();
            }
        };

        // THIS LINE is what makes it real-time.
        // Firebase SDK keeps an open socket and calls onDataChange every time the
        // data at this path changes — no manual refresh required.
        healthDataRef.addValueEventListener(heartRateListener);

        Log.d(TAG, "✅ Real-time HR listener attached → patient_health_data/" + pid);
    }

    // ── Waiting / default state ───────────────────────────────────────────────
    private void showWaitingState() {
        if (heartRateValueText  != null) {
            heartRateValueText.setText("-- bpm");
            heartRateValueText.setTextColor(Color.GRAY);
        }
        if (heartRateStatusText != null) {
            heartRateStatusText.setText("● Waiting…");
            heartRateStatusText.setTextColor(Color.GRAY);
        }
        if (heartRateLastUpdate != null) {
            heartRateLastUpdate.setText("No data yet");
        }
    }

    // ── Clinical notes ────────────────────────────────────────────────────────
    private void setEditMode(boolean editing) {
        isEditing = editing;
        clinicalNotes.setEnabled(editing);
        clinicalNotessave.setEnabled(editing);
        clinicalNotesedit.setEnabled(!editing);
        clinicalNotesmic.setEnabled(editing);
        if (editing) clinicalNotes.requestFocus();
    }

    private void saveClinicalNotes(String notes) {
        if (patientId == null) {
            Toast.makeText(this, "Patient ID not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("patient_health_data")
                .child(patientId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("clinicalNotes",           notes);
        updates.put("clinicalNotesLastUpdate", ServerValue.TIMESTAMP);

        ref.updateChildren(updates)
                .addOnSuccessListener(v -> {
                    currentNotes = notes;
                    Toast.makeText(this, "Notes saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setEditMode(true);
                });
    }

    // ── Speech to text ────────────────────────────────────────────────────────
    private void startSpeechToText() {
        android.content.Intent intent =
                new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak clinical notes");
        try {
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spoken  = results.get(0);
                String current = clinicalNotes.getText().toString();
                clinicalNotes.setText(TextUtils.isEmpty(current)
                        ? spoken : current + "\n" + spoken);
                setEditMode(true);
            }
        }
    }

    // ── Clock ─────────────────────────────────────────────────────────────────
    private void startClock() {
        clockTimer = new Timer();
        clockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                runOnUiThread(() -> {
                    Date now = new Date();
                    if (reportDate != null)
                        reportDate.setText(
                                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now));
                    if (reportTime != null)
                        reportTime.setText(
                                new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(now));
                });
            }
        }, 0, 1000);
    }

    private void stopClock() {
        if (clockTimer != null) { clockTimer.cancel(); clockTimer.purge(); clockTimer = null; }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    private void removeAllListeners() {
        if (conversationRef != null && conversationListener != null)
            conversationRef.removeEventListener(conversationListener);
        if (healthDataRef != null && heartRateListener != null)
            healthDataRef.removeEventListener(heartRateListener);
    }
}