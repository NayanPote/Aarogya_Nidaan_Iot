package com.healthcare.aarogyanidaan;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class doctordashboard extends AppCompatActivity {
    private static final String SHARED_PREFS = "sharedPrefs";
    private DrawerLayout doctorDrawerLayout;
    private ImageView docChat, docnoti;
    private FirebaseAuth auth;
    private DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
    private TextView chatBadge;
    private AlertDialog logoutDialog;

    private MaterialButton docNavToggle;

    private FloatingActionButton navchatbot;

    private List<Article> articlesList = new ArrayList<>();
    private ArticleAdapter articleAdapter;
    private RecyclerView articlesRecyclerView;
    private TextView emptyArticleText;
    private ProgressBar articlesProgressBar;
    private HealthNewsManager healthNewsManager;


    private RecyclerView appointmentsRecyclerView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctordashboard);

        // Initialize views
        doctorDrawerLayout = findViewById(R.id.doctor_drawer_layout);
        docNavToggle = findViewById(R.id.docnavtoggle);
        docChat = findViewById(R.id.docchat);
        appointmentsRecyclerView = findViewById(R.id.doctorAppointmentsRecyclerView);
        auth = FirebaseAuth.getInstance();
        chatBadge = findViewById(R.id.chatBadge);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        navchatbot = findViewById(R.id.navchatbot);

        setupNavigationView();
        setupChatNotificationBadge();

        // Articles setup
        articlesRecyclerView = findViewById(R.id.articlesRecyclerView);
        emptyArticleText = findViewById(R.id.emptyarticletext);
        articlesProgressBar = findViewById(R.id.articlesProgressBar);
        healthNewsManager = new HealthNewsManager(this);

// Setup articles RecyclerView
        setupArticlesRecyclerView();

// Load articles data
        loadHealthArticles();
        loadRssFeed();

// Add click listener for "Related Articles" button
        findViewById(R.id.relatedarticles).setOnClickListener(v -> {
            Intent intent = new Intent(doctordashboard.this, AllArticlesActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        navchatbot.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), chatbot.class);
            startActivity(intent);
        });

        // Get the current user's ID
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (currentUserId != null) {
            // Load appointments for the current user
            AppointmentManager.loadAppointmentsForUser(
                    currentUserId,
                    "doctor",
                    appointmentsRecyclerView,
                    doctordashboard.this // Explicitly specify the activity context
            );

        } else {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Handle chat button click
        docChat.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), doctorchat.class);
            startActivity(intent);
        });

        // Handle navigation toggle button click
        docNavToggle.setOnClickListener(v -> openDrawer());
    }
    private void setupArticlesRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false);
        articlesRecyclerView.setLayoutManager(layoutManager);

        articleAdapter = new ArticleAdapter(this, articlesList, true);
        articlesRecyclerView.setAdapter(articleAdapter);
    }

    private void loadHealthArticles() {
        // Ensure healthNewsManager is initialized
        if (healthNewsManager == null) {
            healthNewsManager = new HealthNewsManager(this);
        }

        if (articlesProgressBar != null) {
            articlesProgressBar.setVisibility(View.VISIBLE);
        }

        if (healthNewsManager != null) {
            // Load 5 articles for the dashboard
            healthNewsManager.loadHealthArticles(articlesProgressBar,
                    new HealthNewsManager.NewsLoadCallback() {
                        @Override
                        public void onArticlesLoaded(List<Article> articles) {
                            if (isFinishing()) return;

                            if (articlesProgressBar != null) {
                                articlesProgressBar.setVisibility(View.GONE);
                            }

                            if (articles != null && !articles.isEmpty()) {
                                articlesList.clear();
                                articlesList.addAll(articles);
                                articleAdapter.notifyDataSetChanged();

                                // Show articles section
                                if (emptyArticleText != null) {
                                    emptyArticleText.setVisibility(View.GONE);
                                }
                            } else {
                                // Hide articles section if no articles
                                if (emptyArticleText != null) {
                                    emptyArticleText.setVisibility(View.VISIBLE);
                                }
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (isFinishing()) return;

                            if (articlesProgressBar != null) {
                                articlesProgressBar.setVisibility(View.GONE);
                            }

                            // Hide articles section on error
                            if (emptyArticleText != null) {
                                emptyArticleText.setVisibility(View.VISIBLE);
                            }

                            // Show a toast with the error message
                            Toast.makeText(doctordashboard.this,
                                    "Health articles: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }, 5);
        } else {
            Log.e("DoctorDashboard", "HealthNewsManager is null");
        }
    }

    private void loadRssFeed() {
        if (articlesProgressBar != null) {
            articlesProgressBar.setVisibility(View.VISIBLE);
        }
        new FetchRssTask(this).execute(
                "https://www.health.harvard.edu/blog/feed",
                "https://www.medicalnewstoday.com/newsfeeds/rss/medical_news_today.xml",
                "https://rss.medicalnewstoday.com/fitness.xml"
        );
    }

    private static class FetchRssTask extends AsyncTask<String, Void, List<Article>> {
        private final WeakReference<doctordashboard> activityReference;

        FetchRssTask(doctordashboard activity) {
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
                    conn.connect();

                    stream = conn.getInputStream();
                    RssParser parser = new RssParser();

                    List<Article> articles = parser.parse(stream, urlString);

                    int count = 0;
                    for (Article article : articles) {
                        result.add(article);
                        count++;
                        if (count >= 5) break;
                    }

                } catch (IOException | XmlPullParserException e) {
                    e.printStackTrace();
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<Article> articles) {
            doctordashboard activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return;

            if (activity.articlesProgressBar != null) {
                activity.articlesProgressBar.setVisibility(View.GONE);
            }

            if (articles != null && !articles.isEmpty()) {
                activity.articlesList.clear();
                activity.articlesList.addAll(articles);
                activity.articleAdapter.notifyDataSetChanged();
            }
//            else {
//                Toast.makeText(activity, "Failed to load articles", Toast.LENGTH_SHORT).show();
//            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (articlesList.isEmpty()) {
            loadHealthArticles();
        }
    }

    private void setupNavigationView() {
        NavigationView navigationView = findViewById(R.id.navigation_view);
        View headerView = navigationView.getHeaderView(0);

        // Header views
        TextView headerName = headerView.findViewById(R.id.doctornavname);
        TextView headerEmail = headerView.findViewById(R.id.doctornavemail);
        TextView headerId = headerView.findViewById(R.id.doctornavid);


        // Get current user ID
        String currentUserId = auth.getCurrentUser().getUid();

        // Fetch and display user data
        DatabaseReference databaseReference = FirebaseDatabase.getInstance()
                .getReference("doctor/" + currentUserId);

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String name = dataSnapshot.child("doctor_name").getValue(String.class);
                    String email = dataSnapshot.child("doctor_email").getValue(String.class);
                    String id = dataSnapshot.child("doctor_id").getValue(String.class);
                    String profilePictureUrl = dataSnapshot.child("profilePicture").getValue(String.class);

                    headerName.setText(name != null ? name : "doctor Name");
                    headerEmail.setText(email != null ? email : "doctor@example.com");
                    headerId.setText(id != null ? id : "doctor Id");

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(doctordashboard.this, "Failed to load user data: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Handle navigation menu item selection
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.doctorlogout) {
                showLogoutDialog();
                return true;
            } else if (id == R.id.doctorTac) {
                startActivity(new Intent(doctordashboard.this, doctortermsandcondition.class));
                return true;
            } else if (id == R.id.doctorshare) {
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
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "AarogyaNidaan");
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
            }else if (id == R.id.doctoraboutus) {
                showinformationDialog();
                return true;
            }else if (id == R.id.doctorrate) {
                startActivity(new Intent(doctordashboard.this, AppRatingandfeedbacks.class));
                return true;
            }
            return false;
        });
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
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

        // 🚨 Clear chat history using ChatDatabaseHelper
        ChatDatabaseHelper chatDatabaseHelper = new ChatDatabaseHelper(this);
        chatDatabaseHelper.clearAllMessages(); // ✅ Clear all chat history

        // Stop the foreground service (if running)
        Intent serviceIntent = new Intent(this, NotificationForegroundService.class);
        stopService(serviceIntent);

        // Firebase logout (if auth is initialized)
        if (auth != null) {
            auth.signOut();
        }

        // Redirect to login page
        Intent intent = new Intent(doctordashboard.this, loginpage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Finish current activity to prevent going back
    }



    // Method to open the drawer
    private void openDrawer() {
        if (doctorDrawerLayout != null) {
            doctorDrawerLayout.openDrawer(GravityCompat.START); // Open the drawer from the left
        }
    }

    // Method to handle the chat notification badge
    private void setupChatNotificationBadge() {
        String currentUserId = auth.getCurrentUser().getUid();
        DatabaseReference conversationsRef = mDatabase.child("conversations");
        Query query = conversationsRef.orderByChild("doctorId").equalTo(currentUserId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int totalUnreadCount = 0;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Conversation conversation = snapshot.getValue(Conversation.class);
                    if (conversation != null) {
                        // Only count if unread messages are for the doctor
                        String unreadCountForUser = snapshot.child("unreadCountForUser")
                                .getValue(String.class);
                        if (currentUserId.equals(unreadCountForUser)) {
                            totalUnreadCount += conversation.getUnreadCount();
                        }
                    }
                }

                // Update badge in UI
                chatBadge.setVisibility(totalUnreadCount > 0 ? View.VISIBLE : View.GONE);
                chatBadge.setText(String.valueOf(totalUnreadCount));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("DoctorDashboard", "Error loading unread counts", error.toException());
            }
        });
    }

    // Method to handle back press for closing the drawer
    @Override
    public void onBackPressed() {
        if (doctorDrawerLayout != null && doctorDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            doctorDrawerLayout.closeDrawer(GravityCompat.START); // Close the drawer if open
        } else {
            super.onBackPressed(); // Perform default back press behavior
        }
    }

    @Override
    protected void onDestroy() {
        if (logoutDialog != null && logoutDialog.isShowing()) {
            logoutDialog.dismiss();
        }
        super.onDestroy();
    }
}
