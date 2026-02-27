package com.healthcare.aarogyanidaan;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.healthcare.aarogyanidaan.databinding.ActivityAppRatingandfeedbacksBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppRatingandfeedbacks extends AppCompatActivity {

    // Firebase references
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String userId;
    private String userType; // "doctor", "patient", or "admin"
    private String userName;
    private String userIdentifier;

    // View binding
    private ActivityAppRatingandfeedbacksBinding binding;

    // Review adapter
    private ReviewAdapter reviewAdapter;
    private List<ReviewModel> reviewList = new ArrayList<>();

    // Rating statistics
    private float averageRating = 0;
    private int totalRatings = 0;
    private int[] ratingCounts = new int[5]; // 5,4,3,2,1 star counts

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAppRatingandfeedbacksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge-to-edge UI
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Toolbar
        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // RecyclerView setup
        binding.recyclerViewReviews.setLayoutManager(new LinearLayoutManager(this));
        reviewAdapter = new ReviewAdapter(this, reviewList, userType, userId, userName);
        binding.recyclerViewReviews.setAdapter(reviewAdapter);

        // Get user details and reviews
        getCurrentUserDetails();
        loadRatingsStatistics();
        loadReviews();

        // Submit Review
        binding.btnSubmitReview.setOnClickListener(v -> submitReview());
    }

    private void getCurrentUserDetails() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();

            // First check for admin
            mDatabase.child("Admin").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        userType = "admin";
                        userName = dataSnapshot.child("email").getValue(String.class);
                        userIdentifier = "Admin";

                        // Hide write review card for admins
                        binding.cardWriteReview.setVisibility(View.GONE);

                        // Show admin info if needed
                        binding.tvUserInfo.setText("Admin: " + userName);

                        // Update adapter for admin features
                        reviewAdapter = new ReviewAdapter(AppRatingandfeedbacks.this, reviewList, userType, userId, userName);
                        binding.recyclerViewReviews.setAdapter(reviewAdapter);
                        reviewAdapter.notifyDataSetChanged();

                        // Optional: show admin instructions
//                        addAdminInstructions();
                    } else {
                        checkIfDoctor();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(AppRatingandfeedbacks.this,
                            "Failed to load user details: " + databaseError.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "Please log in to view reviews", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

//    private void addAdminInstructions() {
//        // Inflates a TextView for instructions and inserts above RecyclerView
//        ViewGroup mainLayout = findMainLayout();
//        if (mainLayout != null) {
//            View tv = getLayoutInflater().inflate(R.layout.admin_review_instruction, mainLayout, false);
//            // If you don't want a dedicated XML, use direct construction:
//            /*
//            TextView tvAdminInstructions = new TextView(this);
//            tvAdminInstructions.setText("As an admin, you can reply to user reviews. Tap on a review to add or edit your reply.");
//            tvAdminInstructions.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
//            tvAdminInstructions.setPadding(16, 16, 16, 16);
//            */
//            // Insert above the recycler view:
//            int idx = mainLayout.indexOfChild(binding.recyclerViewReviews);
//            if (idx >= 0)
//                mainLayout.addView(tv, idx);
//            else
//                mainLayout.addView(tv);
//        }
//    }

    /**
     * Find main layout for insertion (LinearLayout inside NestedScrollView).
     */
    private ViewGroup findMainLayout() {
        // main is CoordinatorLayout, nestedScrollView is in it, then LinearLayout (the true "main layout")
        if (binding.nestedScrollView.getChildCount() > 0) {
            View v = binding.nestedScrollView.getChildAt(0);
            if (v instanceof ViewGroup)
                return (ViewGroup) v;
        }
        // fallback
        return null;
    }

    private void checkIfDoctor() {
        mDatabase.child("doctor").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    userType = "doctor";
                    userName = dataSnapshot.child("doctor_name").getValue(String.class);
                    userIdentifier = dataSnapshot.child("doctor_id").getValue(String.class);
                    binding.tvUserInfo.setText("Dr. " + userName + " (" + userIdentifier + ")");
                    checkPreviousReview();
                } else {
                    checkIfPatient();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    private void checkIfPatient() {
        mDatabase.child("patient").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    userType = "patient";
                    userName = dataSnapshot.child("patient_name").getValue(String.class);
                    userIdentifier = dataSnapshot.child("patient_id").getValue(String.class);
                    binding.tvUserInfo.setText(userName + " (" + userIdentifier + ")");
                    checkPreviousReview();
                } else {
                    Toast.makeText(AppRatingandfeedbacks.this,
                            "User profile not found",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(AppRatingandfeedbacks.this,
                        "Failed to load user details: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPreviousReview() {
        mDatabase.child("reviews").orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                ReviewModel review = snapshot.getValue(ReviewModel.class);
                                if (review != null) {
                                    binding.ratingBarUser.setRating(review.getRating());
                                    binding.etFeedback.setText(review.getReview());
                                    binding.btnSubmitReview.setText("Update Review");
                                    break;
                                }
                            }
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Toast.makeText(AppRatingandfeedbacks.this,
                                "Failed to check previous reviews: " + databaseError.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadRatingsStatistics() {
        mDatabase.child("reviews").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                totalRatings = 0;
                float ratingSum = 0;
                for (int i = 0; i < 5; i++) ratingCounts[i] = 0;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    ReviewModel review = snapshot.getValue(ReviewModel.class);
                    if (review != null) {
                        totalRatings++;
                        ratingSum += review.getRating();
                        int starIndex = 5 - Math.round(review.getRating());
                        if (starIndex >= 0 && starIndex < 5) ratingCounts[starIndex]++;
                    }
                }

                if (totalRatings > 0) {
                    averageRating = ratingSum / totalRatings;
                    binding.tvAverageRating.setText(String.format(Locale.getDefault(), "%.1f", averageRating));
                    binding.ratingBarAverage.setRating(averageRating);
                    binding.tvTotalRatings.setText(String.format(Locale.getDefault(), "%,d ratings", totalRatings));
                } else {
                    binding.tvAverageRating.setText("0.0");
                    binding.ratingBarAverage.setRating(0);
                    binding.tvTotalRatings.setText("0 ratings");
                }

                updateRatingDistribution();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(AppRatingandfeedbacks.this,
                        "Failed to load ratings: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateRatingDistribution() {
        // 5 stars at index 0, 1 star at index 4
        // progressBars and tvCounts array order must match this
        binding.progressBar5.setProgress(totalRatings > 0 ? (ratingCounts[0] * 100 / totalRatings) : 0);
        binding.progressBar4.setProgress(totalRatings > 0 ? (ratingCounts[1] * 100 / totalRatings) : 0);
        binding.progressBar3.setProgress(totalRatings > 0 ? (ratingCounts[2] * 100 / totalRatings) : 0);
        binding.progressBar2.setProgress(totalRatings > 0 ? (ratingCounts[3] * 100 / totalRatings) : 0);
        binding.progressBar1.setProgress(totalRatings > 0 ? (ratingCounts[4] * 100 / totalRatings) : 0);

        binding.tvCount5.setText(String.valueOf(ratingCounts[0]));
        binding.tvCount4.setText(String.valueOf(ratingCounts[1]));
        binding.tvCount3.setText(String.valueOf(ratingCounts[2]));
        binding.tvCount2.setText(String.valueOf(ratingCounts[3]));
        binding.tvCount1.setText(String.valueOf(ratingCounts[4]));
    }

    private void loadReviews() {
        mDatabase.child("reviews").orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                reviewList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    ReviewModel review = snapshot.getValue(ReviewModel.class);
                    if (review != null) reviewList.add(review);
                }
                // Newest first
                Collections.sort(reviewList, (r1, r2) ->
                        Long.compare(r2.getTimestamp(), r1.getTimestamp()));

                if ("admin".equals(userType)) {
                    reviewAdapter = new ReviewAdapter(AppRatingandfeedbacks.this, reviewList, userType, userId, userName);
                    binding.recyclerViewReviews.setAdapter(reviewAdapter);
                } else {
                    reviewAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(AppRatingandfeedbacks.this,
                        "Failed to load reviews: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitReview() {
        float rating = binding.ratingBarUser.getRating();
        String feedback = binding.etFeedback.getText().toString().trim();

        if (rating == 0) {
            Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSubmitReview.setEnabled(false);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        ReviewModel review = new ReviewModel();
        review.setUserId(userId);
        review.setUserName(userName);
        review.setUserType(userType);
        review.setUserIdentifier(userIdentifier);
        review.setRating(rating);
        review.setReview(feedback);
        review.setTimestamp(System.currentTimeMillis());
        review.setFormattedDate(timestamp);

        mDatabase.child("reviews").orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        String reviewKey;
                        boolean isUpdate = false;

                        if (dataSnapshot.exists()) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                reviewKey = snapshot.getKey();
                                isUpdate = true;
                                saveReviewToFirebase(reviewKey, review, isUpdate);
                                break;
                            }
                        } else {
                            reviewKey = mDatabase.child("reviews").push().getKey();
                            saveReviewToFirebase(reviewKey, review, isUpdate);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        binding.btnSubmitReview.setEnabled(true);
                        Toast.makeText(AppRatingandfeedbacks.this,
                                "Failed to submit review: " + databaseError.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveReviewToFirebase(String reviewKey, ReviewModel review, boolean isUpdate) {
        if (reviewKey != null) {
            mDatabase.child("reviews").child(reviewKey).setValue(review)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            binding.btnSubmitReview.setEnabled(true);

                            if (task.isSuccessful()) {
                                String message = isUpdate ? "Review updated successfully" : "Review submitted successfully";
                                Snackbar.make(binding.main, message, Snackbar.LENGTH_SHORT).show();

                                if (!isUpdate) {
                                    binding.ratingBarUser.setRating(0);
                                    binding.etFeedback.setText("");
                                }
                            } else {
                                Toast.makeText(AppRatingandfeedbacks.this,
                                        "Failed to submit review: " + task.getException().getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
