package com.healthcare.aarogyanidaan;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.healthcare.aarogyanidaan.databinding.ActivityPatientdashboardBinding;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class patientdashboard extends AppCompatActivity {

    private static final String SHARED_PREFS = "sharedPrefs";
    private FirebaseAuth auth;
    private FirebaseDatabase mDatabase;
    private GestureManager gestureManager;
    private List<Article> articlesList = new ArrayList<>();
    private com.healthcare.aarogyanidaan.ArticleAdapter articleAdapter;
    private ActivityPatientdashboardBinding binding;

    private DrawerLayout drawerLayout;
    private AlertDialog logoutDialog;
    private HealthNewsManager healthNewsManager;

    private DatabaseReference firebaseDatabase;
    private String currentPatientId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize view binding
        binding = ActivityPatientdashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup articles RecyclerView
        setupArticlesRecyclerView();

        currentPatientId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firebaseDatabase = FirebaseDatabase.getInstance().getReference("patient_health_data");

        // Initialize health news manager
        healthNewsManager = new HealthNewsManager(this);

        binding.articlesRecyclerView.setAdapter(articleAdapter);

        loadRssFeed();
        loadHealthDataFromFirebase();

        // Initialize FirebaseAuth
        auth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance();

        // Setup components
        setupChatNotificationBadge();
        setupNavigationView();
        loadAppointments();
        setupClickListeners();
        // Setup immersive notch handling with theme colors
        setupImmersiveNotchHandling();
        drawerLayout = binding.drawerLayout;

        gestureManager = new GestureManager(this);

        // Attach to the root view
        View rootView = findViewById(android.R.id.content);
        gestureManager.attachToView(rootView);
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
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(patientdashboard.this,
                        "Failed to load health data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateHealthDataUI(String temperature, String heartRate,
                                    String bloodPressure, String oxygenSaturation) {
        if (binding == null) {
            Log.e("patientdashboard", "Binding is null in updateHealthDataUI");
            return;
        }

        if (temperature != null) {
            binding.temperatureValueText.setText(temperature + " °C");
        } else {
            binding.temperatureValueText.setText("-- °C"); // Default value
        }

        if (heartRate != null) {
            binding.heartRateValueText.setText(heartRate + " bpm");
        } else {
            binding.heartRateValueText.setText("-- bpm");
        }

        if (bloodPressure != null) {
            binding.bloodPressureValueText.setText(bloodPressure + " mmHg");
        } else {
            binding.bloodPressureValueText.setText("-- mmHg");
        }

        if (oxygenSaturation != null) {
            binding.oxygenSaturationValueText.setText(oxygenSaturation + " %");
        } else {
            binding.oxygenSaturationValueText.setText("-- %");
        }
    }


    private void setupClickListeners() {
        binding.relatedarticles.setOnClickListener(v -> {
            if (articlesList.size() > 0) {
                // Show all articles in a separate activity
                Intent intent = new Intent(patientdashboard.this, AllArticlesActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(patientdashboard.this, "No Articles available...", Toast.LENGTH_SHORT).show();
            }
        });

        binding.relatedarticles.setOnClickListener(v -> {
            Intent intent = new Intent(patientdashboard.this, AllArticlesActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(patientdashboard.this, PatientNotificationActivity.class);
            startActivity(intent);
        });

        // Navigation drawer toggle
        binding.patnavtoggle.setOnClickListener(v -> openDrawer());

        // Health data button
        binding.patienthealthdata.setOnClickListener(v ->
                startActivity(new Intent(patientdashboard.this, PatienthealthdatadisplayActivity.class))
        );

        // Chatbot button
        binding.chatbot.setOnClickListener(v ->
                startActivity(new Intent(patientdashboard.this, chatbot.class))
        );

        // Doctor button
        binding.Doctor.setOnClickListener(v ->
                startActivity(new Intent(patientdashboard.this, Doctorslist.class))
        );

        // Nearby hospital button
        binding.hospital.setOnClickListener(v -> openGoogleMapsSearch("nearby hospitals"));

        // Nearby pharmacy button
        binding.pharmacy.setOnClickListener(v -> openGoogleMapsSearch("nearby pharmacy"));

        // Nearby ambulance button
        binding.ambulance.setOnClickListener(v -> openGoogleMapsSearch("nearby ambulance"));

        // Chat button
        binding.navChat.setOnClickListener(v ->
                startActivity(new Intent(patientdashboard.this, patientchat.class))
        );

    }

    private void setupArticlesRecyclerView() {
        if (binding != null && binding.articlesRecyclerView != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(
                    this, LinearLayoutManager.HORIZONTAL, false);
            binding.articlesRecyclerView.setLayoutManager(layoutManager);

            articleAdapter = new ArticleAdapter(this, articlesList, true);
            binding.articlesRecyclerView.setAdapter(articleAdapter);

            // Ensure RecyclerView is visible from start
            binding.articlesRecyclerView.setVisibility(View.VISIBLE);
        }

        // Load articles after setting up RecyclerView
        loadHealthArticles();
    }

    private void loadHealthArticles() {
        // Ensure healthNewsManager is initialized
        if (healthNewsManager == null) {
            healthNewsManager = new HealthNewsManager(this);
        }

        // Show progress bar only if articles list is empty
        if (binding != null && (articlesList == null || articlesList.isEmpty())) {
            if (binding.articlesProgressBar != null) {
                binding.articlesProgressBar.setVisibility(View.VISIBLE);
            }
            if (binding.emptyarticletext != null) {
                binding.emptyarticletext.setVisibility(View.GONE);
            }
            if (binding.articlesRecyclerView != null) {
                binding.articlesRecyclerView.setVisibility(View.VISIBLE);
            }
        }

        if (healthNewsManager != null) {
            // Load 5 articles for the dashboard
            healthNewsManager.loadHealthArticles(binding.articlesProgressBar,
                    new HealthNewsManager.NewsLoadCallback() {
                        @Override
                        public void onArticlesLoaded(List<Article> articles) {
                            if (binding == null || isFinishing()) return;

                            // Always hide progress bar first
                            if (binding.articlesProgressBar != null) {
                                binding.articlesProgressBar.setVisibility(View.GONE);
                            }

                            if (articles != null && !articles.isEmpty()) {
                                // Update articles list
                                articlesList.clear();
                                articlesList.addAll(articles);
                                if (articleAdapter != null) {
                                    articleAdapter.notifyDataSetChanged();
                                }

                                // Show articles RecyclerView and hide empty text
                                if (binding.articlesRecyclerView != null) {
                                    binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                                }
                                if (binding.emptyarticletext != null) {
                                    binding.emptyarticletext.setVisibility(View.GONE);
                                }
                            } else {
                                // Show empty message and keep RecyclerView visible but empty
                                if (binding.emptyarticletext != null) {
                                    binding.emptyarticletext.setVisibility(View.VISIBLE);
                                }
                                if (binding.articlesRecyclerView != null) {
                                    binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                                }
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (binding == null || isFinishing()) return;

                            // Hide progress bar
                            if (binding.articlesProgressBar != null) {
                                binding.articlesProgressBar.setVisibility(View.GONE);
                            }

                            // Show empty text but keep RecyclerView visible
                            if (binding.emptyarticletext != null) {
                                binding.emptyarticletext.setVisibility(View.VISIBLE);
                            }
                            if (binding.articlesRecyclerView != null) {
                                binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                            }

                            // Show a toast with the error message
                            Toast.makeText(patientdashboard.this,
                                    "Health articles: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }, 5);
        } else {
            // Handle null healthNewsManager
            if (binding != null) {
                if (binding.articlesProgressBar != null) {
                    binding.articlesProgressBar.setVisibility(View.GONE);
                }
                if (binding.emptyarticletext != null) {
                    binding.emptyarticletext.setVisibility(View.VISIBLE);
                }
                if (binding.articlesRecyclerView != null) {
                    binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                }
            }
            Log.e("PatientDashboard", "HealthNewsManager is null");
        }
    }

    private void loadRssFeed() {
        // Show progress bar only if articles list is empty
        if (binding != null && (articlesList == null || articlesList.isEmpty())) {
            if (binding.articlesProgressBar != null) {
                binding.articlesProgressBar.setVisibility(View.VISIBLE);
            }
            if (binding.emptyarticletext != null) {
                binding.emptyarticletext.setVisibility(View.GONE);
            }
            if (binding.articlesRecyclerView != null) {
                binding.articlesRecyclerView.setVisibility(View.VISIBLE);
            }
        }

        new FetchRssTask(this).execute(
                "https://www.health.harvard.edu/blog/feed",
                "https://www.medicalnewstoday.com/newsfeeds/rss/medical_news_today.xml",
                "https://rss.medicalnewstoday.com/fitness.xml"
        );
    }

    private static class FetchRssTask extends AsyncTask<String, Void, List<Article>> {
        private final WeakReference<patientdashboard> activityReference;

        FetchRssTask(patientdashboard activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected List<Article> doInBackground(String... urls) {
            List<Article> result = new ArrayList<>();

            for (String urlString : urls) {
                InputStream stream = null;
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestMethod("GET");
                    conn.setDoInput(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        stream = conn.getInputStream();
                        RssParser parser = new RssParser();

                        List<Article> articles = parser.parse(stream, urlString);

                        int count = 0;
                        for (Article article : articles) {
                            result.add(article);
                            count++;
                            if (count >= 5) break;
                        }
                    }

                } catch (IOException | XmlPullParserException e) {
                    Log.e("FetchRssTask", "Error fetching RSS: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException e) {
                            Log.e("FetchRssTask", "Error closing stream: " + e.getMessage());
                        }
                    }
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<Article> articles) {
            patientdashboard activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return;

            // Always hide progress bar when task completes
            if (activity.binding != null && activity.binding.articlesProgressBar != null) {
                activity.binding.articlesProgressBar.setVisibility(View.GONE);
            }

            if (articles != null && !articles.isEmpty()) {
                // Update articles list
                activity.articlesList.clear();
                activity.articlesList.addAll(articles);
                if (activity.articleAdapter != null) {
                    activity.articleAdapter.notifyDataSetChanged();
                }

                // Ensure RecyclerView is visible and hide empty text
                if (activity.binding != null) {
                    if (activity.binding.articlesRecyclerView != null) {
                        activity.binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                    }
                    if (activity.binding.emptyarticletext != null) {
                        activity.binding.emptyarticletext.setVisibility(View.GONE);
                    }
                }
            } else {
                // Show empty message but keep RecyclerView visible
                if (activity.binding != null) {
                    if (activity.binding.articlesRecyclerView != null) {
                        activity.binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                    }
                    if (activity.binding.emptyarticletext != null) {
                        activity.binding.emptyarticletext.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            patientdashboard activity = activityReference.get();
            if (activity != null && activity.binding != null) {
                // Hide progress bar if task is cancelled
                if (activity.binding.articlesProgressBar != null) {
                    activity.binding.articlesProgressBar.setVisibility(View.GONE);
                }

                // Keep RecyclerView visible and show empty message
                if (activity.binding.articlesRecyclerView != null) {
                    activity.binding.articlesRecyclerView.setVisibility(View.VISIBLE);
                }
                if (activity.binding.emptyarticletext != null) {
                    activity.binding.emptyarticletext.setVisibility(View.VISIBLE);
                }
            }
        }
    }


    // Override onResume to ensure articles are loaded
    @Override
    protected void onResume() {
        super.onResume();

        // Only reload if articles list is empty to avoid unnecessary API calls
        if (articlesList == null || articlesList.isEmpty()) {
            loadHealthArticles();
            loadRssFeed();
        } else {
            // Ensure RecyclerView is visible if we have articles
            if (binding != null && binding.articlesRecyclerView != null) {
                binding.articlesRecyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void setupNavigationView() {
        NavigationView navigationView = findViewById(R.id.navigation_view);
        View headerView = navigationView.getHeaderView(0);


        // Header views
        TextView headerName = headerView.findViewById(R.id.patientnavname);
        TextView headerEmail = headerView.findViewById(R.id.patientnavemail);
        TextView headerId = headerView.findViewById(R.id.patientnavid);
        MaterialButton headerImage = headerView.findViewById(R.id.pat_edit_profile);
        ImageButton headerback = headerView.findViewById(R.id.back);

        headerback.setOnClickListener(view -> {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        });

        headerImage.setOnClickListener(v ->
                startActivity(new Intent(patientdashboard.this, patientprofile.class))
        );

        // Get current user ID
        String currentUserId = auth.getCurrentUser().getUid();

        // Fetch and display user data
        DatabaseReference databaseReference = FirebaseDatabase.getInstance()
                .getReference("patient/" + currentUserId);

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String name = dataSnapshot.child("patient_name").getValue(String.class);
                    String email = dataSnapshot.child("patient_email").getValue(String.class);
                    String id = dataSnapshot.child("patient_id").getValue(String.class);
                    String profilePictureUrl = dataSnapshot.child("profilePicture").getValue(String.class);

                    headerName.setText(name != null ? name : "Patient Name");
                    headerEmail.setText(email != null ? email : "patient@example.com");
                    headerId.setText(id != null ? id : "Patient Id");

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(patientdashboard.this, "Failed to load user data: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Handle navigation menu item selection
        // Handle navigation menu item selection
        binding.navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.patientlogout) {
                showLogoutDialog();
                return true;
            } else if (id == R.id.patientTac) {
                startActivity(new Intent(patientdashboard.this, patienttermsandcondition.class));
                return true;
            } else if (id == R.id.patientshare) {
                shareApp(); // Call the new share method
                return true;
            } else if (id == R.id.patientaboutus) {
                showinformationDialog();
                return true;
            } else if (id == R.id.patientprofile) {
                startActivity(new Intent(patientdashboard.this, patientprofile.class));
                return true;
            } else if (id == R.id.patientrate) {
                startActivity(new Intent(patientdashboard.this, AppRatingandfeedbacks.class));
                return true;
            }
            return false;
        });

    }

    private void shareApp() {
        try {
            ApplicationInfo app = getApplicationContext().getApplicationInfo();
            String filePath = app.sourceDir;
            File originalApk = new File(filePath);

            if (originalApk.exists()) {
                // Create a copy in external files directory
                File externalDir = new File(getExternalFilesDir(null), "shared_apk");
                if (!externalDir.exists()) {
                    externalDir.mkdirs();
                }

                // Create APK file with proper name
                File copiedApk = new File(externalDir, "AarogyaNidaan_v" + getVersionName() + ".apk");

                // Copy the APK file
                if (copyApkFile(originalApk, copiedApk)) {
                    // Use FileProvider to share
                    Uri apkUri = FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", copiedApk);

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/vnd.android.package-archive");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, " Aarogya Nidaan");
                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                            "🎵 Aarogya Nidaan\n\n" +
                                    "📱 Install the attached APK file\n" +
                                    "⚠️ You may need to enable 'Install from Unknown Sources' in your device settings\n\n" +
                                    "Developed with ❤ Passion by Nayan Pote\n" +
                                    "File: " + copiedApk.getName());

                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    startActivity(Intent.createChooser(shareIntent, "Share Aarogya Nidaan APK"));
                    showCustomToast("Sharing Aarogya Nidaan APK file...");
                } else {
                    showCustomToast("Failed to prepare APK file for sharing");
                }
            } else {
                showCustomToast("APK file not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showCustomToast("Unable to share app file: " + e.getMessage());
        }
    }

    private boolean copyApkFile(File source, File destination) {
        try {
            // Delete existing file if it exists
            if (destination.exists()) {
                destination.delete();
            }

            FileInputStream inStream = new FileInputStream(source);
            FileOutputStream outStream = new FileOutputStream(destination);

            byte[] buffer = new byte[8192]; // 8KB buffer for better performance
            int bytesRead;

            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }

            inStream.close();
            outStream.close();

            // Verify the copy was successful
            return destination.exists() && destination.length() > 0;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void showinformationDialog() {
        SpannableString message = new SpannableString(
                "Aarogya Nidaan is committed to providing accessible healthcare solutions.\n\n" +
                        "👨‍💻 Developer: Nayan Pote\n\n" +
                        "📧 Email: nayan.pote65@gmail.com\n\n" +
                        "📞 Phone: +918767378045\n\n" +
                        "We connect patients with doctors, enable real-time health monitoring, and ensure secure medical record management."
        );

        new AlertDialog.Builder(this)
                .setTitle("About Us")
                .setMessage(message)
                .setPositiveButton("Contact Us", (dialog, which) -> contactUs())
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void contactUs() {
        new AlertDialog.Builder(this)
                .setTitle("Contact Us")
                .setMessage("For any queries, feel free to reach out to us via Email or WhatsApp.")
                .setPositiveButton("Email", (dialog, which) -> openEmail())
                .setNegativeButton("WhatsApp", (dialog, which) -> openWhatsApp())
                .setNeutralButton("Close", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void openEmail() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"nayan.pote65@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Support Inquiry");
        intent.putExtra(Intent.EXTRA_TEXT, "Hello, I need assistance with...");

        try {
            startActivity(Intent.createChooser(intent, "Send Email"));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No email app installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCall() {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:+919960664278")); // Replace with actual phone number
        startActivity(callIntent);
    }

    private void openWhatsApp() {
        String phoneNumber = "+918767378045"; // Replace with your WhatsApp support number
        String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=Hello, I need assistance.";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAppointments() {
        String currentUserId = auth.getCurrentUser().getUid();
        AppointmentManager.loadAppointmentsForUser(currentUserId, "patient", binding.patientAppointmentsRecyclerView, this);
    }

    private void openDrawer() {
        if (binding.drawerLayout != null) {
            binding.drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void openGoogleMapsSearch(String query) {
        String url = "https://www.google.com/maps/search/" + Uri.encode(query);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void showLogoutDialog() {
        if (isFinishing() || isDestroyed()) {
            return; // Prevent showing dialog if activity is closing
        }

        logoutDialog = new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> logout())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .create();

        logoutDialog.show();
    }

    private void logout() {
        // Get notification service instance before logout
        LocalNotificationService notificationService = LocalNotificationService.getInstance(this);
        if (notificationService != null) {
            // Explicitly cleanup notification service
            notificationService.cleanup();
        }

        // Clear "Remember Me" preference
        SharedPreferences.Editor editor = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE).edit();
        editor.clear(); // Clear all preferences under SHARED_PREFS
        editor.apply();

        // Also clear ChatPrefs to ensure notification service doesn't restart
        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit().clear().apply();

        // Cancel all notifications
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        //  Clear chat history using ChatDatabaseHelper
        ChatDatabaseHelper chatDatabaseHelper = new ChatDatabaseHelper(this);
        chatDatabaseHelper.clearAllMessages(); //  Clear all chat history

        // Stop the foreground service (if running)
        Intent serviceIntent = new Intent(this, NotificationForegroundService.class);
        stopService(serviceIntent);

        // Firebase logout (if auth is initialized)
        if (auth != null) {
            auth.signOut();
        }

        // Redirect to login page
        Intent intent = new Intent(patientdashboard.this, loginpage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Finish current activity to prevent going back
    }



    private void setupChatNotificationBadge() {
        FirebaseUser currentUser = auth.getCurrentUser();

        // Ensure binding and patchatBadge are not null
        if (currentUser == null || binding == null || binding.patchatBadge == null) return;

        DatabaseReference conversationsRef = mDatabase.getReference("conversations");
        Query query = conversationsRef.orderByChild("patientId").equalTo(currentUser.getUid());

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int totalUnreadCount = 0;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    // Check if unreadCountForUser matches current user ID
                    String unreadCountForUser = snapshot.child("unreadCountForUser").getValue(String.class);
                    if (currentUser.getUid().equals(unreadCountForUser)) {
                        Long unreadCount = snapshot.child("unreadCount").getValue(Long.class);
                        if (unreadCount != null) {
                            totalUnreadCount += unreadCount;
                        }
                    }
                }

                // Update badge visibility and text only if binding is still valid
                if (binding != null && binding.patchatBadge != null) {
                    if (totalUnreadCount > 0) {
                        binding.patchatBadge.setVisibility(View.VISIBLE);
                        binding.patchatBadge.setText(String.valueOf(totalUnreadCount));
                    } else {
                        binding.patchatBadge.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("PatientDashboard", "Error loading unread counts", error.toException());
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (logoutDialog != null && logoutDialog.isShowing()) {
            logoutDialog.dismiss(); // Dismiss safely
            logoutDialog = null; // Clear reference to avoid memory leak
        }
        // Clean up binding references when the activity is destroyed
        binding = null;
        super.onDestroy(); // Call once at the end
    }
}