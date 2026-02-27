package com.healthcare.aarogyanidaan;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.healthcare.aarogyanidaan.databinding.ActivitySplashpageBinding;

public class splashpage extends AppCompatActivity {

    private static final String SHARED_PREFS = "sharedPrefs";
    private FirebaseAuth auth;
    private FirebaseDatabase database;
    private ActivitySplashpageBinding binding;
    private Animation topAnim, bottomAnim;
    private ValueAnimator progressAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Initialize View Binding
        binding = ActivitySplashpageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeUI();
        setupAnimations();
        checkAuthState();
    }

    private void initializeUI() {
        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        // Null check for binding
        if (binding == null) return;

        // Progress indicator animation with null checks
        if (binding.progressIndicator != null) {
            binding.progressIndicator.setAlpha(0f);
            binding.progressIndicator.setProgress(0);
            binding.progressIndicator.animate()
                    .alpha(1f)
                    .setStartDelay(900)
                    .setDuration(600)
                    .start();

            // Animate progress with null checks in callback
            progressAnimation = ValueAnimator.ofInt(0, 100);
            progressAnimation.setDuration(2000);
            progressAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            progressAnimation.addUpdateListener(animation -> {
                // Add null checks to prevent crash
                if (binding != null && binding.progressIndicator != null && !isFinishing()) {
                    binding.progressIndicator.setProgress((int) animation.getAnimatedValue());
                }
            });
            progressAnimation.setStartDelay(1000);
            progressAnimation.start();
        }

        // Start Lottie animation with null check
        if (binding.lottieAnimation != null) {
            binding.lottieAnimation.playAnimation();
        }
    }

    private void setupAnimations() {
        if (binding == null) return;

        topAnim = AnimationUtils.loadAnimation(this, R.anim.top_animation);
        bottomAnim = AnimationUtils.loadAnimation(this, R.anim.bottom_animation);

        if (binding.copyright != null) {
            binding.copyright.setAnimation(bottomAnim);
        }

        if (binding.main != null) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }
    }

    private void checkAuthState() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);

        new Handler().postDelayed(() -> {
            // Check if activity is still valid
            if (isFinishing() || isDestroyed()) return;

            if (isLoggedIn && auth.getCurrentUser() != null) {
                String userId = auth.getCurrentUser().getUid();
                checkUserTypeAndRedirect(userId);
            } else {
                navigateToLogin();
            }
        }, 2000); // Show splash screen for 2 seconds
    }

    private void checkUserTypeAndRedirect(String userId) {
        if (isFinishing() || isDestroyed()) return;

        // First check if user is an admin
        database.getReference("Admin").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;

                        if (snapshot.exists()) {
                            navigateToActivity(Admin.class);
                        } else {
                            // If not admin, check if patient
                            checkInPatientBranch(userId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;

                        Toast.makeText(getApplicationContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        redirectToLogin();
                    }
                });
    }

    private void checkInPatientBranch(String userId) {
        if (isFinishing() || isDestroyed()) return;

        database.getReference("patient").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;

                        if (snapshot.exists()) {
                            navigateToActivity(patientdashboard.class);
                        } else {
                            checkInDoctorsBranch(userId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;

                        Toast.makeText(getApplicationContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        redirectToLogin();
                    }
                });
    }

    private void checkInDoctorsBranch(String userId) {
        if (isFinishing() || isDestroyed()) return;

        database.getReference("doctor").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;

                        if (snapshot.exists()) {
                            navigateToActivity(doctordashboard.class);
                        } else {
                            Toast.makeText(getApplicationContext(), "User not found in database", Toast.LENGTH_SHORT).show();
                            auth.signOut();
                            redirectToLogin();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;

                        Toast.makeText(getApplicationContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        redirectToLogin();
                    }
                });
    }

    private void navigateToActivity(Class<?> activityClass) {
        if (isFinishing() || isDestroyed()) return;

        startActivity(new Intent(splashpage.this, activityClass));
        finish();
    }

    private void navigateToLogin() {
        if (isFinishing() || isDestroyed()) return;

        startActivity(new Intent(splashpage.this, loginpage.class));
        finish();
    }

    private void redirectToLogin() {
        SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("isLoggedIn", false);
        editor.apply();

        navigateToLogin();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cancel progress animation to prevent callback execution
        if (progressAnimation != null) {
            progressAnimation.cancel();
            progressAnimation = null;
        }

        // Clean up binding to prevent memory leaks
        binding = null;
    }
}
