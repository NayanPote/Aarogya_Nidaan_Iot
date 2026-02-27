package com.healthcare.aarogyanidaan;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.healthcare.aarogyanidaan.databinding.ActivityLoginpageBinding;

public class loginpage extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseDatabase database;
    private ActivityLoginpageBinding binding;
    private android.app.ProgressDialog progressDialog;
    private static final String SHARED_PREFS = "sharedPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize View Binding
        binding = ActivityLoginpageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup immersive notch handling with theme colors
        setupImmersiveNotchHandling();

        initializeUI();
        animateSplashEffects();
        setupClickListeners();
    }

    private void setupImmersiveNotchHandling() {
        // Enable edge-to-edge display
        EdgeToEdge.enable(this);

        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Modern approach
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);

            // Make system bars match your theme
            window.getInsetsController().setSystemBarsAppearance(0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0+ - Legacy approach
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

            // Set transparent status bar to blend with your gradient
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);

            // Make content extend behind system bars
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

        // Handle window insets to properly position content around notch
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            // Calculate proper padding considering both system bars and display cutout
            int topPadding = Math.max(systemBars.top, displayCutout.top);
            int leftPadding = Math.max(systemBars.left, displayCutout.left);
            int rightPadding = Math.max(systemBars.right, displayCutout.right);
            int bottomPadding = Math.max(systemBars.bottom, displayCutout.bottom);

            // Apply padding to main container
            v.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);

            return insets;
        });

        // Start animations after layout
        binding.getRoot().post(() -> {
            startBackgroundAnimations();
        });
    }

    private void startBackgroundAnimations() {
        // Start the background ripple animation
        if (binding.rippleEffect != null) {
            AnimationDrawable rippleDrawable = (AnimationDrawable) binding.rippleEffect.getBackground();
            if (rippleDrawable != null) {
                rippleDrawable.start();
            }
        }

        // Start the logo ripple animation
        if (binding.logoRippleEffect != null) {
            AnimationDrawable logoRippleDrawable = (AnimationDrawable) binding.logoRippleEffect.getBackground();
            if (logoRippleDrawable != null) {
                logoRippleDrawable.start();
            }
        }
    }

    private void initializeUI() {
        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Logging in...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        binding.loginbutton.setOnClickListener(v -> {
            String email = binding.emailInput.getText().toString().trim();
            String password = binding.passwordInput.getText().toString().trim();

            if (validateInput(email, password)) {
                progressDialog.show();
                loginUser(email, password);
            }
        });

        binding.newuser.setOnClickListener(v -> {
            Toast.makeText(getApplicationContext(), "Select Register option", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(loginpage.this, registeroptions.class));
        });
    }

    private void animateSplashEffects() {
        if (binding.logoContainer == null) return;

        // Animate logo with splash-like entrance
        binding.logoContainer.setAlpha(0f);
        binding.logoContainer.setTranslationY(50f);
        binding.logoContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .withEndAction(() -> {
                    // After entrance animation, start the floating animation
                    animateFloatingLogo();
                })
                .start();
    }

    private void animateFloatingLogo() {
        if (binding.logoContainer == null) return;

        // Make sure logo is visible if this is called again
        binding.logoContainer.setAlpha(1f);

        // Create a continuous up-down floating animation
        ObjectAnimator floatingAnimator = ObjectAnimator.ofFloat(
                binding.logoContainer,
                "translationY",
                0f, -15f, 0f, 15f, 0f);

        // Set animation properties
        floatingAnimator.setDuration(4000); // 4 seconds for a complete cycle
        floatingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        floatingAnimator.setInterpolator(new LinearInterpolator());

        // Start with fade in if needed (first time only)
        if (binding.logoContainer.getAlpha() == 0f) {
            // Initial fade in
            ObjectAnimator fadeInAnimator = ObjectAnimator.ofFloat(binding.logoContainer, "alpha", 0f, 1f);
            fadeInAnimator.setDuration(1000);

            AnimatorSet initialAnimation = new AnimatorSet();
            initialAnimation.playSequentially(fadeInAnimator, floatingAnimator);
            initialAnimation.start();
        } else {
            // Just start the floating animation
            floatingAnimator.start();
        }
    }

    private boolean validateInput(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(getApplicationContext(), "Please enter the email", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(getApplicationContext(), "Please enter the password", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!email.contains("@") || !email.contains(".") || !email.endsWith("com")) {
            Toast.makeText(getApplicationContext(), "Invalid Email", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 8) {
            Toast.makeText(getApplicationContext(), "Password should be at least 8 characters", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!password.matches(".*[A-Z].*")) {
            Toast.makeText(getApplicationContext(), "Password should contain at least one uppercase letter", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!password.matches(".*[a-z].*")) {
            Toast.makeText(getApplicationContext(), "Password should contain at least one lowercase letter", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!password.matches(".*[!@#$%^&*()].*")) {
            Toast.makeText(getApplicationContext(), "Password should contain at least one special character", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!password.matches(".*[0-9].*")) {
            Toast.makeText(getApplicationContext(), "Password should contain at least one number", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void loginUser(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Save "Remember Me" preference
                            SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE).edit();
                            editor.putBoolean("isLoggedIn", binding.rememberme.isChecked());

                            // Save user data for notification service
                            SharedPreferences.Editor chatPrefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit();
                            chatPrefs.putBoolean("userLoggedIn", binding.rememberme.isChecked());
                            chatPrefs.putString("currentUserId", auth.getCurrentUser().getUid());

                            // Save additional flag for admin status if needed
                            chatPrefs.apply();
                            editor.apply();

                            String userId = auth.getCurrentUser().getUid();
                            checkUserTypeAndRedirect(userId);

                            // Make sure notification service is initialized
                            LocalNotificationService.getInstance(loginpage.this).checkAuthStatusAndReInitialize();
                        } else {
                            progressDialog.dismiss();
                            Toast.makeText(loginpage.this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkUserTypeAndRedirect(String userId) {
        // First, check if the user is an admin
        database.getReference("Admin").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            progressDialog.dismiss();
                            // User is an admin, redirect to admin dashboard
                            startActivity(new Intent(loginpage.this, Admin.class));
                            finish();
                        } else {
                            // If not admin, check if patient
                            checkInPatientBranch(userId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkInPatientBranch(String userId) {
        database.getReference("patient").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            progressDialog.dismiss();
                            startActivity(new Intent(loginpage.this, patientdashboard.class));
                            finish();
                        } else {
                            // If not patient, check if doctor
                            checkInDoctorsBranch(userId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkInDoctorsBranch(String userId) {
        database.getReference("doctor").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressDialog.dismiss();
                        if (snapshot.exists()) {
                            startActivity(new Intent(loginpage.this, doctordashboard.class));
                            finish();
                        } else {
                            Toast.makeText(getApplicationContext(), "User not found in database", Toast.LENGTH_SHORT).show();
                            auth.signOut();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up binding to prevent memory leaks
        if (binding != null) {
            binding = null;
        }
    }
}
