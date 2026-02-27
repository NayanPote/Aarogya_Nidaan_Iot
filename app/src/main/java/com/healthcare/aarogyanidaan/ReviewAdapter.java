package com.healthcare.aarogyanidaan;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.healthcare.aarogyanidaan.databinding.ItemReviewBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final Context context;
    private final List<ReviewModel> reviews;
    private final String currentUserType;
    private final String currentUserId;
    private final String currentUserName;
    private final DatabaseReference mDatabase;

    private final int[] avatarColors = new int[]{
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.Color.parseColor("#2196F3"),
            android.graphics.Color.parseColor("#FF9800"),
            android.graphics.Color.parseColor("#9C27B0"),
            android.graphics.Color.parseColor("#F44336"),
            android.graphics.Color.parseColor("#009688")
    };
    private final Random random = new Random();

    // Constructor for regular users
    public ReviewAdapter(Context context, List<ReviewModel> reviews) {
        this.context = context;
        this.reviews = reviews;
        this.currentUserType = "patient"; // Default to patient
        this.currentUserId = "";
        this.currentUserName = "";
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    // Constructor with user information for admin functionality
    public ReviewAdapter(Context context, List<ReviewModel> reviews, String userType, String userId, String userName) {
        this.context = context;
        this.reviews = reviews;
        this.currentUserType = userType;
        this.currentUserId = userId;
        this.currentUserName = userName;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use View Binding to inflate layout
        ItemReviewBinding binding = ItemReviewBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ReviewViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        ReviewModel review = reviews.get(position);
        ItemReviewBinding binding = holder.binding;

        // Safe data access with defaults
        String userName = review.getUserName() != null ? review.getUserName() : "Anonymous";
        String userType = review.getUserType() != null ? review.getUserType() : "";
        String userIdentifier = review.getUserIdentifier() != null ? review.getUserIdentifier() : "N/A";
        String reviewText = review.getReview() != null ? review.getReview() : "";

        // Set avatar image by userType
        ShapeableImageView avatarView = binding.imgUserAvatar;
        if ("doctor".equals(userType)) {
            avatarView.setImageResource(R.drawable.doctor3);
        } else {
            avatarView.setImageResource(R.drawable.patient);
        }

        // Background color for avatar based on userName hash
        avatarView.setBackgroundColor(
                avatarColors[Math.abs(userName.hashCode()) % avatarColors.length]
        );

        // Reviewer Info
        if ("doctor".equals(userType)) {
            binding.tvReviewerName.setText("Dr. " + userName);
        } else {
            binding.tvReviewerName.setText(userName);
        }
        binding.tvReviewerId.setText(userIdentifier);

        // Rating safely
        Float rating = review.getRating();
        binding.ratingBarReview.setRating(rating != null ? rating : 0f);

        // Format and set review date
        String formattedDate = review.getFormattedDate() != null ? formatDate(review.getFormattedDate()) : "Unknown Date";
        binding.tvReviewDate.setText(formattedDate);

        // Review content visibility
        if (!reviewText.isEmpty()) {
            binding.tvReviewContent.setText(reviewText);
            binding.tvReviewContent.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.tvReviewContent.setVisibility(android.view.View.GONE);
        }

        // Admin reply section
        if (review.getAdminReply() != null) {
            binding.layoutAdminReply.setVisibility(android.view.View.VISIBLE);
            binding.tvAdminReplyName.setText("Admin: " + review.getAdminReply().getAdminName());
            binding.tvAdminReplyDate.setText(formatDate(review.getAdminReply().getFormattedDate()));
            binding.tvAdminReplyContent.setText(review.getAdminReply().getReplyContent());
        } else {
            binding.layoutAdminReply.setVisibility(android.view.View.GONE);
        }

        // Add/Edit reply button only for admins
        if ("admin".equals(currentUserType)) {
            binding.btnAddReply.setVisibility(android.view.View.VISIBLE);
            binding.btnAddReply.setText(review.getAdminReply() != null ? "Edit Reply" : "Add Reply");
            binding.btnAddReply.setOnClickListener(v -> showReplyDialog(review));
        } else {
            binding.btnAddReply.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    // Format date helper
    private String formatDate(String timestampStr) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
            Date date = inputFormat.parse(timestampStr);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return timestampStr;
        }
    }

    private void showReplyDialog(ReviewModel review) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Admin Reply");

        // Use EditText for multi-line input
        final EditText input = new EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);

        // Pre-fill with existing reply content, if any
        if (review.getAdminReply() != null) {
            input.setText(review.getAdminReply().getReplyContent());
        }

        builder.setView(input);

        // Buttons
        builder.setPositiveButton("Submit", (dialog, which) -> {
            String replyContent = input.getText().toString().trim();
            if (!TextUtils.isEmpty(replyContent)) {
                submitReply(review, replyContent);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void submitReply(ReviewModel review, String replyContent) {
        ReviewReply reply = new ReviewReply();
        reply.setAdminId(currentUserId);
        reply.setAdminName(currentUserName);
        reply.setReplyContent(replyContent);
        reply.setTimestamp(System.currentTimeMillis());

        String formattedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        reply.setFormattedDate(formattedDate);

        // Find review by timestamp and update adminReply
        mDatabase.child("reviews").orderByChild("timestamp").equalTo(review.getTimestamp())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            String reviewKey = snapshot.getKey();
                            if (reviewKey != null) {
                                mDatabase.child("reviews").child(reviewKey).child("adminReply")
                                        .setValue(reply)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(context, "Reply submitted successfully", Toast.LENGTH_SHORT).show();
                                            // Update local review and refresh
                                            review.setAdminReply(reply);
                                            notifyDataSetChanged();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(context, "Failed to submit reply: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                break;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Toast.makeText(context, "Error: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ViewHolder with View Binding
    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        final ItemReviewBinding binding;

        public ReviewViewHolder(@NonNull ItemReviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
