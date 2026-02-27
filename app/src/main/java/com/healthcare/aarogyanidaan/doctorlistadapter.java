package com.healthcare.aarogyanidaan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.healthcare.aarogyanidaan.databinding.DoctorslistItemBinding;
import java.util.ArrayList;

public class doctorlistadapter extends RecyclerView.Adapter<doctorlistadapter.ViewHolder> {

    private final Context context;
    private final ArrayList<Users> doctorList;
    private OnDoctorClickListener clickListener;

    public interface OnDoctorClickListener {
        void onDoctorClick(Users doctor, int position);
    }

    public doctorlistadapter(Context context, ArrayList<Users> doctorList) {
        this.context = context;
        this.doctorList = doctorList;
    }

    public void setOnDoctorClickListener(OnDoctorClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DoctorslistItemBinding binding = DoctorslistItemBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Users doctor = doctorList.get(position);

        // Set text values
        holder.binding.doctorlistname.setText(doctor.getDoctor_name());
        holder.binding.doctorlistspe.setText(doctor.getDoctor_specialization());
        holder.binding.doclistcity.setText(doctor.getDoctor_city());

        // Set default avatar or load real image using an image loading library
        holder.binding.doctoravatar.setImageResource(R.drawable.doctor3);

        // Set rating if available in the doctor object
        if (doctor.getDoctor_rating() != null && !doctor.getDoctor_rating().isEmpty()) {
            try {
                float rating = Float.parseFloat(doctor.getDoctor_rating());
                holder.binding.doctorRating.setRating(rating);
                holder.binding.ratingText.setText(doctor.getDoctor_rating());
            } catch (NumberFormatException e) {
                holder.binding.doctorRating.setRating(4.0f); // Default rating
                holder.binding.ratingText.setText("4.0");
            }
        } else {
            holder.binding.doctorRating.setRating(4.0f); // Default rating
            holder.binding.ratingText.setText("4.0");
        }

        // Set review count if available
        if (doctor.getDoctor_reviews_count() != null && !doctor.getDoctor_reviews_count().isEmpty()) {
            holder.binding.reviewsCount.setText("(" + doctor.getDoctor_reviews_count() + " reviews)");
        } else {
            holder.binding.reviewsCount.setText("(236 reviews)"); // Default review count
        }

        // Handle book appointment button click
        holder.binding.btnBookAppointment.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onDoctorClick(doctor, position);
            }
        });

        // Handle entire item click
        holder.binding.getRoot().setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onDoctorClick(doctor, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return doctorList != null ? doctorList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final DoctorslistItemBinding binding;

        ViewHolder(@NonNull DoctorslistItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}