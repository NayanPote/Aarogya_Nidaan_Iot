package com.healthcare.aarogyanidaan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {

    private List<Appointment> appointments;
    private Context context;
    private FirebaseDatabase database;

    public AppointmentAdapter(List<Appointment> appointments, Context context) {
        this.appointments = appointments;
        this.context = context;
        this.database = FirebaseDatabase.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_appointment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Appointment appointment = appointments.get(position);

        if (appointment == null) return;

        // Load doctor and patient details
        loadUserDetails(appointment.getDoctorId(), "doctor", holder.doctorNameText);
        loadUserDetails(appointment.getPatientId(), "patient", holder.patientNameText);
        holder.patientId.setText(String.format("ID: %s", appointment.getPatientId()));

        // Set appointment date and time
        holder.dateText.setText(String.format("%s", appointment.getDate()));

        // Convert to 12-hour format with AM/PM
        String timeIn12HourFormat = convertTo12HourFormat(appointment.getTime());
        holder.TimeText.setText(timeIn12HourFormat);

        // Set status text
        holder.statusText.setText(String.format("%s", appointment.getStatus()));

        // Update countdown
        updateCountdown(holder.countdownText, appointment.getDate(), appointment.getTime());

        // Cancel button logic
        holder.cancelButton.setOnClickListener(v -> showCancelDialog(appointment));
    }

    // 12-hour format
    private String convertTo12HourFormat(String time) {
        try {
            SimpleDateFormat sdf24 = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat sdf12 = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Date date = sdf24.parse(time);
            if (date != null) {
                return sdf12.format(date);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return time; // Return original if parsing fails
    }

    @Override
    public int getItemCount() {
        return appointments != null ? appointments.size() : 0;
    }

    public void updateAppointments(List<Appointment> newAppointments) {
        this.appointments = newAppointments;
        notifyDataSetChanged();
    }

    private void loadUserDetails(String userId, String userType, TextView textView) {
        DatabaseReference userRef = database.getReference(userType).child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.exists() ?
                        snapshot.child(userType.equals("doctor") ? "doctor_name" : "patient_name").getValue(String.class) :
                        "Unknown " + userType;
                textView.setText(name != null ? name : "Unknown " + userType);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                textView.setText("Unknown " + userType);
            }
        });
    }

    private void updateCountdown(TextView textView, String date, String time) {
        try {
            String[] dateParts = date.split("/");
            String[] timeParts = time.split(":");

            Calendar appointmentCal = Calendar.getInstance();
            appointmentCal.set(
                    Integer.parseInt(dateParts[2]),
                    Integer.parseInt(dateParts[1]) - 1,
                    Integer.parseInt(dateParts[0]),
                    Integer.parseInt(timeParts[0]),
                    Integer.parseInt(timeParts[1])
            );

            long timeUntilAppointment = appointmentCal.getTimeInMillis() - System.currentTimeMillis();

            if (timeUntilAppointment > 0) {
                long days = timeUntilAppointment / (24 * 60 * 60 * 1000);
                long hours = (timeUntilAppointment % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);

                if (days > 0) {
                    textView.setText(days + " days " + hours + " hrs remaining");
                } else {
                    textView.setText(hours + " hrs remaining");
                }
            } else {
                textView.setText("Time expired");
            }
        } catch (Exception e) {
            textView.setText("Invalid date or time");
            e.printStackTrace();
        }
    }

    private void showCancelDialog(Appointment appointment) {
        new AlertDialog.Builder(context)
                .setTitle("Cancel Appointment")
                .setMessage("Are you sure you want to cancel this appointment?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    DatabaseReference appointmentRef = database.getReference("appointments")
                            .child(appointment.getId());
                    appointmentRef.removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(context,
                                    "Appointment cancelled successfully", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(context,
                                    "Failed to cancel appointment", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", null)
                .show();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView doctorNameText;
        TextView patientNameText;
        TextView dateText;
        TextView TimeText;
        TextView statusText;
        TextView countdownText;
        TextView patientId;
        Button cancelButton;

        ViewHolder(View itemView) {
            super(itemView);
            doctorNameText = itemView.findViewById(R.id.doctorNameText);
            patientNameText = itemView.findViewById(R.id.patientNameText);
            dateText = itemView.findViewById(R.id.dateText);
            TimeText = itemView.findViewById(R.id.TimeText);
            statusText = itemView.findViewById(R.id.statusText);
            countdownText = itemView.findViewById(R.id.countdownText);
            cancelButton = itemView.findViewById(R.id.cancelButton);
            patientId = itemView.findViewById(R.id.patientid);
        }
    }
}