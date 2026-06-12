import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class GeminiInterpreter {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        
        if (args.length == 0) {
            System.err.println("Error: Missing parameters.");
            return;
        }
        
        String predictions = args[0];
        String prompt = "";

        // Check if the user asked a specific question (XAI Mode)
        if (args.length > 1 && !args[1].trim().isEmpty()) {
            String userQuestion = args[1];
            
            // Injecting the absolute truth of your project architecture
            String systemContext = "System Context: This is the 'IEX Renewable Energy Forecaster'. " +
                                   "Architecture: It uses an XGBoost Regressor (not LSTMs or Random Forest) optimized with the 'hist' tree method. " +
                                   "Data: Trained on 131,000 rows of IEX market data and weather APIs, engineered into 46 features including cyclical sine/cosine time encodings. " +
                                   "Compute: Trained locally on an Apple M4 chip. " +
                                   "Metrics: Achieved an R2 score of 0.9890 and MAE of ~139 Rs/MWh on the test set.";

            prompt = String.format(
                "You are an Explainable AI (XAI) assistant answering questions about a specific machine learning model. " +
                "%s " +
                "The model just predicted these 15-minute block prices (Rs/MWh): %s. " +
                "The user asked: '%s'. " +
                "Answer their question clearly, accurately, and professionally in 2-4 sentences based strictly on the System Context provided.",
                systemContext, predictions, userQuestion
            );
        } else {
            // Default Summary Mode
            prompt = "You are an expert energy market analyst. Explain the following 15-minute block price predictions (in Rs/MWh) to a non-technical manager in 2 concise, professional sentences. Highlight any major trends: " + predictions;
        }

        // Build the strict JSON payload using Gson
        JsonObject textObj = new JsonObject();
        textObj.addProperty("text", prompt);

        JsonArray partsArr = new JsonArray();
        partsArr.add(textObj);

        JsonObject contentObj = new JsonObject();
        contentObj.add("parts", partsArr);

        JsonArray contentsArr = new JsonArray();
        contentsArr.add(contentObj);

        JsonObject root = new JsonObject();
        root.add("contents", contentsArr);

        String jsonPayload = root.toString();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        int maxRetries = 3;
        int delayMs = 2000;
        HttpResponse<String> response = null;

        for (int i = 0; i < maxRetries; i++) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 503) {
                break;
            }
            if (i < maxRetries - 1) {
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }
        
        if (response == null || response.statusCode() != 200) {
            System.err.println("API Error. Status Code: " + (response != null ? response.statusCode() : "Unknown"));
            return;
        }

        try {
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
            String cleanText = jsonResponse.getAsJsonArray("candidates")
                                          .get(0).getAsJsonObject()
                                          .getAsJsonObject("content")
                                          .getAsJsonArray("parts")
                                          .get(0).getAsJsonObject()
                                          .get("text").getAsString();
            
            System.out.println(cleanText.trim());
        } catch (JsonSyntaxException | NullPointerException | IndexOutOfBoundsException e) {
            System.err.println("JSON Parsing failed. Raw response: " + response.body());
        }
    }
}
