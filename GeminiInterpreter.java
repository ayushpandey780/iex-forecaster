import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GeminiInterpreter {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        
        if (args.length == 0) {
            System.out.println("Error: No price data provided.");
            return;
        }
        
        String predictions = args[0]; 
        String prompt = "You are an expert energy market analyst. Explain the following 15-minute block price predictions (in Rs/MWh) to a non-technical manager in 2 concise, professional sentences. Highlight any major trends: " + predictions;
        String jsonPayload = String.format("{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}", prompt);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        int maxRetries = 3;
        int delayMs = 2000; // Start with a 2-second delay
        HttpResponse<String> response = null;

        for (int i = 0; i < maxRetries; i++) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // If success (200) or client error (400), don't retry. 
            // If server error (503), wait and retry.
            if (response.statusCode() != 503) {
                break;
            }
            
            if (i < maxRetries - 1) {
                Thread.sleep(delayMs);
                delayMs *= 2; // Double the wait time for the next try (Exponential Backoff)
            }
        }
        
        try {
            String body = response.body();
            String explanation = body.split("\"text\": \"")[1].split("\"")[0];
            System.out.println(explanation.replace("\\n", " "));
        } catch (Exception e) {
            System.out.println("The AI market analysis server is currently crowded. Please try clicking the button again in a few moments.");
        }
    }
}
