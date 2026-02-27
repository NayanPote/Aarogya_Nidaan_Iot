package com.healthcare.aarogyanidaan;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.healthcare.aarogyanidaan.databinding.ActivityDoctorDetailsBinding;
import com.healthcare.aarogyanidaan.databinding.BookAppointmentBinding;

import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public class DoctorDetailsActivity extends AppCompatActivity {

    private ActivityDoctorDetailsBinding binding;
    private FirebaseDatabase database;
    private FirebaseAuth auth;
    private String doctorId;

    private DatePickerDialog datePickerDialog;
    private TimePickerDialog timePickerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDoctorDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        database = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();

        // Get doctor ID from intent
        doctorId = getIntent().getStringExtra("doctor_id");
        if (doctorId == null) {
            Toast.makeText(this, "Error: Doctor information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up button listeners
        binding.bookappointment.setOnClickListener(v -> showBookingDialog());
        binding.requestButton.setOnClickListener(v -> sendChatRequest());
        binding.doctorDetailId.setOnClickListener(view -> copyToClipboard(binding.doctorDetailId.getText().toString()));

        fetchDoctorDetails();
        setupBackButton();
    }

    private void sendChatRequest() {
        String patientId = auth.getCurrentUser().getUid();
        DatabaseReference requestsRef = database.getReference("chat_requests");

        requestsRef.orderByChild("patientId").equalTo(patientId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot requestSnapshot : snapshot.getChildren()) {
                                ChatRequest existingRequest = requestSnapshot.getValue(ChatRequest.class);
                                if (existingRequest != null && existingRequest.getDoctorId().equals(doctorId)) {
                                    showToast("Chat request already sent to this doctor");
                                    return;
                                }
                            }
                        }
                        createNewChatRequest(patientId, requestsRef);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showToast("Error checking existing requests: " + error.getMessage());
                    }
                });
    }

    private void createNewChatRequest(String patientId, DatabaseReference requestsRef) {
        database.getReference("patient").child(patientId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Users patient = snapshot.getValue(Users.class);
                        if (patient != null) {
                            String requestId = UUID.randomUUID().toString();
                            ChatRequest chatRequest = new ChatRequest(
                                    requestId,
                                    patientId,
                                    doctorId,
                                    patient.getPatient_name(),
                                    System.currentTimeMillis(),
                                    "pending"
                            );
                            requestsRef.child(requestId).setValue(chatRequest)
                                    .addOnSuccessListener(aVoid -> showToast("Chat request sent successfully"))
                                    .addOnFailureListener(e -> showToast("Failed to send chat request: " + e.getMessage()));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showToast("Error fetching patient details: " + error.getMessage());
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String patientId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Doctor ID", patientId);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Doctor ID copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void showBookingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        BookAppointmentBinding dialogBinding = BookAppointmentBinding.inflate(getLayoutInflater());
        builder.setView(dialogBinding.getRoot());

        AlertDialog dialog = builder.create();

        dialogBinding.selectDateBtn.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            datePickerDialog = new DatePickerDialog(this,
                    (view, year1, monthOfYear, dayOfMonth) -> {
                        String selectedDate = dayOfMonth + "/" + (monthOfYear + 1) + "/" + year1;
                        dialogBinding.dateText.setText(selectedDate);
                    }, year, month, day);

            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        dialogBinding.selectTimeBtn.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            timePickerDialog = new TimePickerDialog(this,
                    (view, hourOfDay, minute1) -> {
                        String selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1);
                        dialogBinding.timeText.setText(selectedTime);
                    }, hour, minute, false);
            timePickerDialog.show();
        });

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Book", (dialog1, which) -> {
            String date = dialogBinding.dateText.getText().toString();
            String time = dialogBinding.timeText.getText().toString();

            if (!date.isEmpty() && !time.isEmpty()) {
                bookAppointment(date, time);
            } else {
                Toast.makeText(this, "Please select date and time", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (dialog1, which) -> dialog1.dismiss());
        dialog.show();
    }

    private void bookAppointment(String date, String time) {
        String patientId = auth.getCurrentUser().getUid();
        String appointmentId = database.getReference().child("appointments").push().getKey();

        Appointment appointment = new Appointment(
                appointmentId,
                patientId,
                doctorId,
                date,
                time,
                "pending"
        );

        DatabaseReference appointmentsRef = database.getReference("appointments");
        appointmentsRef.child(appointmentId).setValue(appointment)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Appointment booked successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to book appointment", Toast.LENGTH_SHORT).show()
                );
    }

    private void fetchDoctorDetails() {
        showLoading(true);
        DatabaseReference doctorRef = database.getReference("doctor").child(doctorId);

        doctorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                showLoading(false);
                Users doctor = snapshot.getValue(Users.class);
                if (doctor != null) {
                    updateUI(doctor);
                } else {
                    Toast.makeText(DoctorDetailsActivity.this,
                            "Error: Doctor data not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(DoctorDetailsActivity.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void updateUI(Users doctor) {
        binding.doctorDetailName.setText(doctor.getDoctor_name());
        binding.doctorDetailSpecialization.setText(doctor.getDoctor_specialization());
        binding.doctorDetailEmail.setText(doctor.getDoctor_email());
        binding.doctorDetailPhone.setText(doctor.getDoctor_contactno());
        binding.doctorDetailGender.setText(doctor.getDoctor_gender());
        binding.doctorDetailCity.setText(doctor.getDoctor_city());
        binding.doctorDetailId.setText(doctor.getDoctor_id());
        binding.doctorDetailAvatar.setImageResource(R.drawable.doctor3);
    }

    private void showLoading(boolean show) {
        binding.doctorDetailProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupBackButton() {
        binding.doctorDetailBack.setOnClickListener(v -> finish());
    }
}
