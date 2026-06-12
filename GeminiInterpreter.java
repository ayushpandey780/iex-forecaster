import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class GeminiInterpreter {
    
    private static final Logger LOGGER = Logger.getLogger(GeminiInterpreter.class.getName());
    // Note: Verify your endpoint. gemini-1.5-flash is the current stable v1beta model.
    private static final String API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";
    private static final int MAX_RETRIES = 4;
    private static final int BASE_DELAY_MS = 2000;

    public static void main(String[] args) {
        if (args.length == 0 || args[0].trim().isEmpty()) {
            failGracefully("No prediction data provided by the dashboard.");
            return;
        }

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.severe("GEMINI_API_KEY environment variable is missing or empty.");
            failGracefully("System configuration error. Intelligence service unavailable.");
            return;
        }

        String predictions = args[0];
        String prompt = "You are an expert energy market analyst. Explain the following 15-minute block price predictions (in Rs/MWh) to a non-technical manager in 2 concise, professional sentences. Highlight any major trends: " + predictions;

        String jsonPayload = buildJsonPayload(prompt);
        if (jsonPayload == null) return;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_ENDPOINT + apiKey))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        executeWithRetry(client, request);
    }

    private static String buildJsonPayload(String promptText) {
        try {
            JsonObject textNode = new JsonObject();
            textNode.addProperty("text", promptText);

            JsonArray partsArray = new JsonArray();
            partsArray.add(textNode);

            JsonObject contentNode = new JsonObject();
            contentNode.add("parts", partsArray);

            JsonArray contentsArray = new JsonArray();
            contentsArray.add(contentNode);

            JsonObject root = new JsonObject();
            root.add("contents", contentsArray);

            return root.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to construct JSON payload", e);
            failGracefully("Internal data formatting error.");
            return null;
        }
    }

    private static void executeWithRetry(HttpClient client, HttpRequest request) {
        int currentDelay = BASE_DELAY_MS;
        Random random = new Random();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    parseAndPrintResponse(response.body());
                    return;
                }

                if (statusCode == 429 || statusCode == 503) {
                    LOGGER.warning(String.format("API returned %d. Attempt %d of %d.", statusCode, attempt, MAX_RETRIES));
                    
                    // Respect Retry-After header if provided
                    Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                    if (retryAfter.isPresent()) {
                        currentDelay = Integer.parseInt(retryAfter.get()) * 1000;
                    }

                    if (attempt < MAX_RETRIES) {
                        // Add up to 20% jitter to prevent thundering herd
                        int jitter = random.nextInt((int) (currentDelay * 0.2)); 
                        Thread.sleep(currentDelay + jitter);
                        currentDelay *= 2; // Exponential backoff
                        continue;
                    }
                } else {
                    handleTerminalError(statusCode, response.body());
                    return;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.severe("Thread interrupted during backoff.");
                failGracefully("Analysis interrupted.");
                return;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Network/IO Exception during API call", e);
                if (attempt == MAX_RETRIES) {
                    failGracefully("Failed to connect to the intelligence service after multiple attempts.");
                    return;
                }
            }
        }
        failGracefully("The AI analysis service is currently experiencing heavy load. Please try again later.");
    }

    private static void parseAndPrintResponse(String responseBody) {
        try {
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            
            // Navigate safely through the Gemini JSON structure
            String text = jsonResponse.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
                    
            // Print clean output for Streamlit to capture
            System.out.println(text.trim());
            
        } catch (JsonSyntaxException | NullPointerException | IndexOutOfBoundsException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse API response: " + responseBody, e);
            failGracefully("Received an unexpected response format from the intelligence service.");
        }
    }

    private static void handleTerminalError(int statusCode, String body) {
        LOGGER.severe("Terminal API Error. Status: " + statusCode + ", Body: " + body);
        switch (statusCode) {
            case 400: failGracefully("Invalid request format sent to the analysis server."); break;
            case 401:
            case 403: failGracefully("Authentication failed. Please verify API credentials."); break;
            case 404: failGracefully("The specified AI model endpoint was not found."); break;
            case 500: failGracefully("The AI analysis server encountered an internal error."); break;
            default:  failGracefully("An unexpected error occurred during analysis."); break;
        }
    }

    private static void failGracefully(String userMessage) {
        // Output clean message to standard out for the UI
        System.out.println(userMessage);
    }
}
