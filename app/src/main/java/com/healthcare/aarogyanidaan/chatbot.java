package com.healthcare.aarogyanidaan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.healthcare.aarogyanidaan.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class chatbot extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int CAMERA_REQUEST_CODE = 102;
    private static final int GALLERY_REQUEST_CODE = 103;

    // Replace with your actual Gemini API key
    private static final String GEMINI_API_KEY = "AIzaSyAQCfBdSwupNXVDIc9Qobp1GaVhT1wb5UI";

    private RecyclerView chatRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton, backbutton;
    private ScrollView scrollView;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;

    // Gemini API Helper
    private GeminiApiHelper geminiHelper;
    private boolean useAI = true; // Toggle for AI vs local responses

    // Health knowledge base (fallback when API fails)
    private Map<String, List<String>> healthResponses;
    private List<String> generalGreetings;
    private List<String> unknownResponses;
    private List<String> symptomQuestions;
    private Map<String, List<String>> medicalConditions;
    private ImageButton camera, upload;

    private String currentPhotoPath;
    private ChatDatabaseHelper dbHelper;

    // Chatbot state variables
    private boolean inHealthConsultation = false;
    private String currentSymptom = null;
    private List<String> reportedSymptoms = new ArrayList<>();
    private int consultationStage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        checkPermissions();

        // Initialize UI components
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageEditText = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        backbutton = findViewById(R.id.backbutton);

        // Setup RecyclerView
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        camera = findViewById(R.id.camera);
        upload = findViewById(R.id.upload);

        // Initialize the database helper
        dbHelper = new ChatDatabaseHelper(this);

        // Initialize Gemini API Helper
        geminiHelper = new GeminiApiHelper(this, GEMINI_API_KEY);

        // Load previous messages
        loadChatHistory();

        camera.setOnClickListener(v -> openCamera());
        upload.setOnClickListener(v -> openGallery());

        // Initialize knowledge bases (for fallback)
        initializeKnowledgeBase();
        addDateMessage();

        // Setup send button click listener
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = messageEditText.getText().toString().trim();
                if (!message.isEmpty()) {
                    sendMessage(message);
                }
            }
        });

        ImageButton menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(this::showPopupMenu);

        backbutton.setOnClickListener(view -> {
            onBackPressed();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geminiHelper != null) {
            geminiHelper.shutdown();
        }
    }

    private void loadChatHistory() {
        chatMessages.clear();
        List<ChatMessage> messages = dbHelper.getAllMessages();

        String todayDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date());
        boolean todayDateExists = dbHelper.todayDateSeparatorExists(todayDate);

        if (messages.isEmpty() || !todayDateExists) {
            ChatMessage dateMessage = new ChatMessage("", todayDate, "");
            dateMessage.sentBy = ChatMessage.DATE_SEPARATOR;

            if (!todayDateExists) {
                dbHelper.addDateSeparator(dateMessage);
            }
        }

        chatMessages.addAll(messages);
        chatAdapter.notifyDataSetChanged();

        boolean onlyDateSeparator = true;
        for (ChatMessage msg : chatMessages) {
            if (msg.sentBy != ChatMessage.DATE_SEPARATOR) {
                onlyDateSeparator = false;
                break;
            }
        }

        if (onlyDateSeparator) {
            addBotMessage("Hello! I'm Aarogya Assist, your AI-powered health assistant. " +
                    "I was created by Nayan Pote to help you with health-related questions and wellness guidance. " +
                    "How can I help you today?");
        } else {
            chatRecyclerView.post(() -> chatRecyclerView.scrollToPosition(chatMessages.size() - 1));
        }
    }

    private void addUserMessage(String message) {
        ChatMessage chatMessage = new ChatMessage("user", message, getCurrentTime());
        chatMessages.add(chatMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
        dbHelper.addMessage(chatMessage);
    }

    private void addBotMessage(String message) {
        sendButton.setEnabled(false);

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ChatMessage chatMessage = new ChatMessage("bot", message, getCurrentTime());
                chatMessages.add(chatMessage);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                scrollToBottom();
                sendButton.setEnabled(true);
                dbHelper.addMessage(chatMessage);
            }
        }, 500);
    }

    private void addDateMessage() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateString = dateFormat.format(new Date());

        ChatMessage dateMessage = new ChatMessage("", dateString, "");
        dateMessage.sentBy = ChatMessage.DATE_SEPARATOR;

        chatMessages.add(dateMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        dbHelper.addDateSeparator(dateMessage);
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.healthcare.aarogyanidaan.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_REQUEST_CODE) {
                sendMediaMessage(currentPhotoPath, "📸 Photo");
            } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
                Uri selectedImage = data.getData();
                String imagePath = getPathFromUri(selectedImage);
                if (imagePath != null) {
                    sendMediaMessage(imagePath, "🖼️ Image");
                }
            }
        }
    }

    private String getPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return null;
    }

    private void sendMediaMessage(String mediaPath, String caption) {
        ChatMessage chatMessage = new ChatMessage("user", caption, getCurrentTime(), mediaPath);
        chatMessages.add(chatMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollToBottom();
        dbHelper.addMessage(chatMessage);

        String response = "I can see you've shared an image. While I can't analyze images directly yet, " +
                "feel free to describe what's in the image or ask me any health-related questions about it!";

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ChatMessage botMessage = new ChatMessage("bot", response, getCurrentTime());
                chatMessages.add(botMessage);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                scrollToBottom();
                dbHelper.addMessage(botMessage);
            }
        }, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.top_app_bar, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_clear_chat) {
                showClearChatConfirmation();
                return true;
            } else if (id == R.id.menu_settings) {
                openSettings();
                return true;
            } else if (id == R.id.menu_help) {
                showHelpDialog();
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void openSettings() {
        // Create dialog to toggle AI mode
        new AlertDialog.Builder(this)
                .setTitle("Chatbot Settings")
                .setMessage("Choose response mode:")
                .setPositiveButton("AI Mode (Gemini)", (dialog, which) -> {
                    useAI = true;
                    Toast.makeText(this, "AI Mode Enabled", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Local Mode", (dialog, which) -> {
                    useAI = false;
                    Toast.makeText(this, "Local Mode Enabled", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Aarogya Assist - AI Health Assistant")
                .setIcon(R.drawable.chatbot)
                .setMessage("I'm an AI-powered health assistant that can help with:\n\n" +
                        "• 🤖 Intelligent health conversations\n\n" +
                        "• 💊 General health information\n\n" +
                        "• 🏃 Exercise and fitness guidance\n\n" +
                        "• 🥗 Nutrition and diet advice\n\n" +
                        "• 😴 Sleep and stress management\n\n" +
                        "• 🩺 Symptom assessment (general guidance only)\n\n" +
                        "Note: I provide general information and am not a substitute for professional medical advice.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showClearChatConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat History")
                .setMessage("Are you sure you want to clear all chat history? This cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    dbHelper.clearAllMessages();
                    chatMessages.clear();
                    chatAdapter.notifyDataSetChanged();

                    // Clear Gemini conversation history
                    if (geminiHelper != null) {
                        geminiHelper.clearHistory();
                    }

                    addDateMessage();
                    addBotMessage("Hello! I'm Aarogya Assist, your AI-powered health assistant. " +
                            "How can I help you today?");

                    Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void initializeKnowledgeBase() {
        // Initialize health responses (fallback)
        healthResponses = new HashMap<>();

        generalGreetings = new ArrayList<>();
        generalGreetings.add("Hello! How can I assist you with your health today?");
        generalGreetings.add("Hi there! I'm Aarogya Assist. What health questions do you have?");
        generalGreetings.add("Greetings! I'm here to help with any health concerns or questions.");
        generalGreetings.add("Welcome! How can I help you stay healthy today?");

        unknownResponses = new ArrayList<>();
        unknownResponses.add("I'm not sure I understand. Could you rephrase that?");
        unknownResponses.add("I'm still learning. Can you ask that in a different way?");
        unknownResponses.add("I don't have information on that yet. Can I help with something else?");
        unknownResponses.add("I'm not programmed to understand that query. Could you try another question?");

        List<String> exerciseResponses = new ArrayList<>();
        exerciseResponses.add("Regular exercise is crucial for maintaining good health. Aim for at least 150 minutes of moderate activity per week.");
        exerciseResponses.add("Exercise benefits include improved cardiovascular health, better mood, and reduced risk of chronic diseases.");
        exerciseResponses.add("Good exercises for beginners include walking, swimming, and cycling. Always start slow and gradually increase intensity.");
        healthResponses.put("exercise", exerciseResponses);

        List<String> nutritionResponses = new ArrayList<>();
        nutritionResponses.add("A balanced diet should include fruits, vegetables, whole grains, lean proteins, and healthy fats.");
        nutritionResponses.add("Try to limit processed foods, excessive sugar, and salt in your diet for better health outcomes.");
        nutritionResponses.add("Staying hydrated is important. Aim to drink about 8 glasses of water daily, but needs vary by individual.");
        healthResponses.put("nutrition", nutritionResponses);

        List<String> sleepResponses = new ArrayList<>();
        sleepResponses.add("Adults should aim for 7-9 hours of quality sleep per night for optimal health.");
        sleepResponses.add("Poor sleep can impact immune function, metabolism, and mental health.");
        sleepResponses.add("Improving sleep hygiene includes maintaining a regular sleep schedule and creating a restful environment.");
        healthResponses.put("sleep", sleepResponses);

        List<String> stressResponses = new ArrayList<>();
        stressResponses.add("Chronic stress can negatively impact both physical and mental health.");
        stressResponses.add("Stress management techniques include meditation, deep breathing, physical activity, and maintaining social connections.");
        stressResponses.add("If stress is severely impacting your daily life, consider speaking with a healthcare professional.");
        healthResponses.put("stress", stressResponses);

        symptomQuestions = new ArrayList<>();
        symptomQuestions.add("How long have you been experiencing these symptoms?");
        symptomQuestions.add("On a scale of 1-10, how would you rate any pain associated with this?");
        symptomQuestions.add("Have you noticed any triggers that worsen your symptoms?");
        symptomQuestions.add("Are you currently taking any medications?");
        symptomQuestions.add("Have you made any recent lifestyle changes?");

        medicalConditions = new HashMap<>();

        List<String> feverInfo = new ArrayList<>();
        feverInfo.add("Fever is usually a sign that your body is fighting an infection. Rest, stay hydrated, and take appropriate fever reducers if needed.");
        feverInfo.add("See a doctor if your fever is very high (above 103°F/39.4°C), lasts more than three days, or is accompanied by severe symptoms.");
        medicalConditions.put("fever", feverInfo);

        List<String> headacheInfo = new ArrayList<>();
        headacheInfo.add("Headaches can be caused by stress, dehydration, lack of sleep, or underlying medical conditions.");
        headacheInfo.add("Try rest, hydration, and over-the-counter pain relievers. See a doctor if headaches are severe or persistent.");
        medicalConditions.put("headache", headacheInfo);

        List<String> coldInfo = new ArrayList<>();
        coldInfo.add("Common colds are viral infections affecting the upper respiratory tract. Rest, stay hydrated, and manage symptoms with over-the-counter medications.");
        coldInfo.add("Most colds resolve within 7-10 days. See a doctor if symptoms are severe or persist longer.");
        medicalConditions.put("cold", coldInfo);

        List<String> diabetesInfo = new ArrayList<>();
        diabetesInfo.add("Diabetes is a condition affecting how your body processes blood sugar. It requires proper management including medication, diet, and exercise.");
        diabetesInfo.add("Regular check-ups and monitoring blood sugar levels are essential for managing diabetes effectively.");
        medicalConditions.put("diabetes", diabetesInfo);

        List<String> hypertensionInfo = new ArrayList<>();
        hypertensionInfo.add("Hypertension (high blood pressure) often has no symptoms but can lead to serious health problems if untreated.");
        hypertensionInfo.add("Management includes regular monitoring, medication if prescribed, reducing sodium intake, exercising regularly, and maintaining a healthy weight.");
        medicalConditions.put("hypertension", hypertensionInfo);
    }

    private void sendMessage(String message) {
        addUserMessage(message);
        messageEditText.setText("");

        // Check for emergency keywords first
        String lowerMessage = message.toLowerCase();
        if (isEmergency(lowerMessage)) {
            String emergencyResponse = "⚠️ This sounds like a medical emergency! Please call emergency services immediately:\n\n" +
                    "🚨 India: 102 (Ambulance) or 108 (Emergency)\n" +
                    "🚨 USA: 911\n" +
                    "🚨 UK: 999\n\n" +
                    "Don't wait for online advice in emergency situations. Get help NOW!";
            addBotMessage(emergencyResponse);
            return;
        }

        // Use AI if enabled, otherwise use local responses
        if (useAI) {
            // Show typing indicator
            sendButton.setEnabled(false);

            geminiHelper.generateResponse(message, new GeminiApiHelper.GeminiCallback() {
                @Override
                public void onSuccess(String response) {
                    runOnUiThread(() -> {
                        addBotMessage(response);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.e("Chatbot", "Gemini API Error: " + error);
                        // Fallback to local response
                        String fallbackResponse = processLocalMessage(message);
                        addBotMessage(fallbackResponse);
                        Toast.makeText(chatbot.this, "Using local responses (API unavailable)",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            // Use local knowledge base
            String response = processLocalMessage(message);
            addBotMessage(response);
        }
    }

    private boolean isEmergency(String message) {
        String[] emergencyKeywords = {
                "emergency", "severe pain", "heart attack", "stroke", "can't breathe",
                "chest pain", "suicide", "kill myself", "heavy bleeding", "overdose",
                "unconscious", "seizure", "choking", "severe bleeding"
        };

        for (String keyword : emergencyKeywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String processLocalMessage(String message) {
        String lowerCaseMessage = message.toLowerCase();

        // Check for information about Aarogya Assist
        if (lowerCaseMessage.contains("who are you") ||
                lowerCaseMessage.contains("about you") ||
                lowerCaseMessage.contains("made you") ||
                lowerCaseMessage.contains("created you")) {
            return "I'm Aarogya Assist, an AI-powered health assistant created by Nayan Pote. " +
                    "I use advanced AI to provide health information and wellness guidance. " +
                    "Remember, I provide general information only, not medical diagnosis or treatment.";
        }

        // Check for greetings
        if (lowerCaseMessage.contains("hi") ||
                lowerCaseMessage.contains("hello") ||
                lowerCaseMessage.contains("hey") ||
                lowerCaseMessage.contains("greetings")) {
            return getRandomResponse(generalGreetings);
        }

        // Check for gratitude
        if (lowerCaseMessage.contains("thank") ||
                lowerCaseMessage.contains("thanks") ||
                lowerCaseMessage.contains("appreciate")) {
            return "You're welcome! I'm happy to help with your health questions. Feel free to ask me anything!";
        }

        // Check for farewell
        if (lowerCaseMessage.contains("bye") ||
                lowerCaseMessage.contains("goodbye") ||
                lowerCaseMessage.contains("see you") ||
                lowerCaseMessage.contains("talk later")) {
            return "Take care and stay healthy! Feel free to chat again whenever you have health questions. Goodbye! 👋";
        }

        // Check for common health topics
        for (Map.Entry<String, List<String>> entry : healthResponses.entrySet()) {
            if (lowerCaseMessage.contains(entry.getKey())) {
                return getRandomResponse(entry.getValue());
            }
        }

        // Check for medical conditions
        for (Map.Entry<String, List<String>> entry : medicalConditions.entrySet()) {
            if (lowerCaseMessage.contains(entry.getKey())) {
                return getRandomResponse(entry.getValue()) +
                        "\n\n⚕️ Note: I provide general information only. Please consult a healthcare professional for personalized medical advice.";
            }
        }

        // General health advice
        if (lowerCaseMessage.contains("health tip") ||
                lowerCaseMessage.contains("healthy habit") ||
                lowerCaseMessage.contains("wellness") ||
                lowerCaseMessage.contains("stay healthy")) {
            List<String> healthTips = new ArrayList<>();
            healthTips.add("💪 Stay physically active - aim for at least 30 minutes of moderate exercise most days.");
            healthTips.add("🥗 Maintain a balanced diet rich in fruits, vegetables, whole grains, and lean proteins.");
            healthTips.add("💧 Stay hydrated by drinking plenty of water throughout the day.");
            healthTips.add("😴 Prioritize sleep - most adults need 7-9 hours of quality sleep each night.");
            healthTips.add("🧘 Manage stress through techniques like meditation, deep breathing, or enjoyable activities.");
            return getRandomResponse(healthTips);
        }

        // If nothing specific is detected
        return getRandomResponse(unknownResponses) +
                " I can help with topics like exercise, nutrition, sleep, stress management, and general health information.";
    }

    private String getRandomResponse(List<String> responses) {
        if (responses == null || responses.isEmpty()) {
            return "I don't have information on that yet.";
        }
        Random random = new Random();
        return responses.get(random.nextInt(responses.size()));
    }

    private void scrollToBottom() {
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    private String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date());
    }

    public static class ChatMessage {
        public static final int SENT_BY_USER = 0;
        public static final int SENT_BY_BOT = 1;
        public static final int DATE_SEPARATOR = 2;

        private String sender;
        private String message;
        private String time;
        private String mediaPath;
        private long timestamp;
        public int sentBy;

        public ChatMessage(String sender, String message, String time) {
            this.sender = sender;
            this.message = message;
            this.time = time;
            this.mediaPath = null;
            this.timestamp = System.currentTimeMillis();

            if ("user".equals(sender)) {
                this.sentBy = SENT_BY_USER;
            } else if ("bot".equals(sender)) {
                this.sentBy = SENT_BY_BOT;
            }
        }

        public ChatMessage(String sender, String message, String time, String mediaPath) {
            this.sender = sender;
            this.message = message;
            this.time = time;
            this.mediaPath = mediaPath;
            this.timestamp = System.currentTimeMillis();

            if ("user".equals(sender)) {
                this.sentBy = SENT_BY_USER;
            } else if ("bot".equals(sender)) {
                this.sentBy = SENT_BY_BOT;
            }
        }

        public ChatMessage(String message) {
            this.sender = "";
            this.message = message;
            this.time = "";
            this.mediaPath = null;
            this.timestamp = System.currentTimeMillis();
            this.sentBy = DATE_SEPARATOR;
        }

        public String getSender() {
            return sender;
        }

        public String getMessage() {
            return message;
        }

        public String getTime() {
            return time;
        }

        public String getMediaPath() {
            return mediaPath;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public boolean isDateSeparator() {
            return sentBy == DATE_SEPARATOR;
        }
    }
}