package com.healthcare.aarogyanidaan;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiApiHelper {
    private static final String TAG = "GeminiApiHelper";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    private String apiKey;
    private ExecutorService executorService;
    private List<MessageHistory> conversationHistory;
    private static final int MAX_HISTORY = 10; // Keep last 10 messages for context

    public interface GeminiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    private static class MessageHistory {
        String role;
        String content;

        MessageHistory(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public GeminiApiHelper(Context context, String apiKey) {
        this.apiKey = apiKey;
        this.executorService = Executors.newSingleThreadExecutor();
        this.conversationHistory = new ArrayList<>();
    }

    public void generateResponse(String userMessage, GeminiCallback callback) {
        // Add user message to history
        addToHistory("user", userMessage);

        executorService.execute(() -> {
            try {
                String response = callGeminiApi(userMessage);

                if (response != null && !response.isEmpty()) {
                    // Add bot response to history
                    addToHistory("model", response);
                    callback.onSuccess(response);
                } else {
                    callback.onError("Empty response from API");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calling Gemini API", e);
                callback.onError(e.getMessage());
            }
        });
    }

    private String callGeminiApi(String message) throws Exception {
        URL url = new URL(GEMINI_API_URL + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        // Build the system instruction and conversation
        JSONObject requestBody = new JSONObject();

        // Add system instruction
        JSONObject systemInstruction = new JSONObject();
        systemInstruction.put("role", "user");
        JSONArray systemParts = new JSONArray();
        JSONObject systemPart = new JSONObject();
        systemPart.put("text", getSystemPrompt());
        systemParts.put(systemPart);
        systemInstruction.put("parts", systemParts);

        // Build contents with conversation history
        JSONArray contents = new JSONArray();

        // Add conversation history
        for (MessageHistory msg : conversationHistory) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", msg.role);
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", msg.content);
            parts.put(part);
            msgObj.put("parts", parts);
            contents.put(msgObj);
        }

        requestBody.put("system_instruction", systemInstruction);
        requestBody.put("contents", contents);

        // Add generation config
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 1024);
        requestBody.put("generationConfig", generationConfig);

        // Add safety settings
        JSONArray safetySettings = new JSONArray();
        String[] categories = {
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
        };

        for (String category : categories) {
            JSONObject setting = new JSONObject();
            setting.put("category", category);
            setting.put("threshold", "BLOCK_MEDIUM_AND_ABOVE");
            safetySettings.put(setting);
        }
        requestBody.put("safetySettings", safetySettings);

        // Send request
        OutputStream os = conn.getOutputStream();
        os.write(requestBody.toString().getBytes("UTF-8"));
        os.close();

        // Read response
        int responseCode = conn.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();

            // Parse the response
            JSONObject jsonResponse = new JSONObject(response.toString());

            if (jsonResponse.has("candidates")) {
                JSONArray candidates = jsonResponse.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    JSONObject content = candidate.getJSONObject("content");
                    JSONArray parts = content.getJSONArray("parts");
                    if (parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text");
                    }
                }
            }

            throw new Exception("No valid response in API result");
        } else {
            // Read error response
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                errorResponse.append(line);
            }
            br.close();

            Log.e(TAG, "API Error Response: " + errorResponse.toString());
            throw new Exception("API Error: " + responseCode + " - " + errorResponse.toString());
        }
    }

    private String getSystemPrompt() {
        return "You are Aarogya Assist, a friendly and knowledgeable health assistant created by Nayan Pote. " +
                "Your role is to:\n" +
                "1. Provide general health information and wellness tips\n" +
                "2. Answer questions about exercise, nutrition, sleep, stress management, and common health topics\n" +
                "3. Offer symptom assessment and general guidance (NOT medical diagnosis)\n" +
                "4. Engage in friendly, supportive conversations about health and wellness\n\n" +

                "IMPORTANT GUIDELINES:\n" +
                "- Always remind users that you provide general information only, not medical diagnosis or treatment\n" +
                "- For serious symptoms or emergencies, immediately advise users to seek professional medical help or call emergency services\n" +
                "- Be empathetic, supportive, and encouraging\n" +
                "- Keep responses concise but informative (2-4 sentences unless more detail is requested)\n" +
                "- Use simple, easy-to-understand language\n" +
                "- When discussing symptoms, ask relevant follow-up questions to better understand the situation\n" +
                "- Promote healthy lifestyle habits and preventive care\n" +
                "- If asked about medications, dosages, or specific treatments, always advise consulting a healthcare professional\n\n" +

                "EMERGENCY KEYWORDS: If the user mentions words like 'emergency', 'severe pain', 'heart attack', " +
                "'stroke', 'can't breathe', 'suicide', or similar critical situations, immediately advise them to " +
                "call emergency services (911/102/108) or go to the nearest emergency room.\n\n" +

                "Respond naturally and conversationally while maintaining professionalism and accuracy.";
    }

    private void addToHistory(String role, String content) {
        conversationHistory.add(new MessageHistory(role, content));

        // Keep only the last MAX_HISTORY messages to manage context length
        if (conversationHistory.size() > MAX_HISTORY * 2) { // *2 because we have user + model pairs
            conversationHistory.remove(0);
            conversationHistory.remove(0);
        }
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}