package com.healthcare.aarogyanidaan;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.healthcare.aarogyanidaan.databinding.ActivityPatientchatBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class patientchat extends AppCompatActivity {
    private ActivityPatientchatBinding binding;
    private DoctorConversationAdapter doctorConversationAdapter;
    private List<Conversation> conversations;
    private List<Conversation> filteredConversations;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ValueEventListener conversationsListener;
    private String currentUserId;
    private int totalConversationsToProcess = 0;
    private int processedConversations = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPatientchatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize lists
        conversations = new ArrayList<>();
        filteredConversations = new ArrayList<>();

        setupViews();
        setupRecyclerView();
        setupSearch();
        setupSwipeRefresh();
        loadConversations();
        // Setup immersive notch handling with theme colors
        setupImmersiveNotchHandling();
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

    private void setupViews() {
        binding.backbutton.setOnClickListener(v -> {
            onBackPressed();
            finish();
        });
    }

    private void setupRecyclerView() {
        binding.conversationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        doctorConversationAdapter = new DoctorConversationAdapter(
                filteredConversations,
                conversation -> {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("conversationId", conversation.getConversationId());
                    intent.putExtra("otherPersonName", conversation.getDoctorName());
                    intent.putExtra("otherPersonId", conversation.getDoctorId());
                    intent.putExtra("doctorSpecialization", conversation.getDoctorSpecialization());
                    startActivity(intent);
                },
                currentUserId
        );

        binding.conversationsRecyclerView.setAdapter(doctorConversationAdapter);
    }

    private void setupSearch() {
        binding.patsearchBar.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterConversations(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterConversations(newText);
                return true;
            }
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );

        binding.swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshConversations();
            }
        });
    }

    private void refreshConversations() {
        // Clear current data
        conversations.clear();
        filteredConversations.clear();
        doctorConversationAdapter.notifyDataSetChanged();

        // Reload conversations
        loadConversations();
    }

    private void filterConversations(String query) {
        filteredConversations.clear();

        if (TextUtils.isEmpty(query)) {
            filteredConversations.addAll(conversations);
        } else {
            String lowercaseQuery = query.toLowerCase();
            for (Conversation conversation : conversations) {
                if (conversation.getDoctorName().toLowerCase().contains(lowercaseQuery) ||
                        conversation.getDoctorSpecialization().toLowerCase().contains(lowercaseQuery) ||
                        conversation.getDoctorId().toLowerCase().contains(lowercaseQuery)) {
                    filteredConversations.add(conversation);
                }
            }
        }

        updateEmptyStateVisibility();
        doctorConversationAdapter.notifyDataSetChanged();
    }

    private void loadConversations() {
        showLoading(true);

        DatabaseReference conversationsRef = mDatabase.child("conversations");
        Query conversationsQuery = conversationsRef.orderByChild("patientId").equalTo(currentUserId);

        conversationsListener = conversationsQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                conversations.clear();
                totalConversationsToProcess = (int) snapshot.getChildrenCount();
                processedConversations = 0;

                if (totalConversationsToProcess == 0) {
                    showLoading(false);
                    // Stop swipe refresh
                    binding.swipeRefreshLayout.setRefreshing(false);
                    updateEmptyStateVisibility();
                    return;
                }

                for (DataSnapshot convSnapshot : snapshot.getChildren()) {
                    Conversation conversation = convSnapshot.getValue(Conversation.class);
                    if (conversation != null) {
                        countUnreadMessages(conversation);
                    } else {
                        processedConversations++;
                        checkIfAllProcessed();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                // Stop swipe refresh on error
                binding.swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(patientchat.this,
                        "Error loading conversations: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void countUnreadMessages(Conversation conversation) {
        DatabaseReference messagesRef = mDatabase.child("messages");
        Query unreadMessagesQuery = messagesRef.orderByChild("conversationId")
                .equalTo(conversation.getConversationId());

        unreadMessagesQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int unreadCount = 0;

                for (DataSnapshot messageSnapshot : dataSnapshot.getChildren()) {
                    Message message = messageSnapshot.getValue(Message.class);
                    if (message != null &&
                            !message.getSenderId().equals(currentUserId) &&
                            !message.isRead()) {
                        unreadCount++;
                    }
                }

                conversation.setUnreadCount(unreadCount);
                conversations.add(conversation);

                processedConversations++;
                checkIfAllProcessed();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                processedConversations++;
                Log.e("patientchat", "Failed to count unread messages", databaseError.toException());
                checkIfAllProcessed();
            }
        });
    }

    private void checkIfAllProcessed() {
        if (processedConversations >= totalConversationsToProcess) {
            sortAndUpdateUI();
        }
    }

    private void sortAndUpdateUI() {
        showLoading(false);
        // Stop swipe refresh
        binding.swipeRefreshLayout.setRefreshing(false);

        Collections.sort(conversations, (c1, c2) ->
                Long.compare(c2.getLastMessageTimestamp(), c1.getLastMessageTimestamp()));

        filteredConversations.clear();
        filteredConversations.addAll(conversations);

        updateEmptyStateVisibility();
        doctorConversationAdapter.notifyDataSetChanged();
    }

    private void updateEmptyStateVisibility() {
        if (filteredConversations.isEmpty()) {
            binding.emptyStateView.setVisibility(View.VISIBLE);
            binding.conversationsRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyStateView.setVisibility(View.GONE);
            binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.conversationsRecyclerView.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (conversationsListener != null) {
            mDatabase.child("conversations").removeEventListener(conversationsListener);
        }
    }
}