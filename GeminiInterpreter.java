import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GeminiInterpreter {
    public static void main(String[] args) throws Exception {
        // Ensure you set this environment variable in Streamlit Cloud settings
        String apiKey = System.getenv("GEMINI_API_KEY");
        
        if (args.length == 0) {
            System.out.println("Error: No price data provided.");
            return;
        }
        
        String predictions = args[0]; 
        String prompt = "You are an expert energy market analyst. Explain the following 15-minute block price predictions (in Rs/MWh) to a non-technical manager in 2 concise, professional sentences. Highlight any major trends: " + predictions;
        
        // Construct the JSON payload
        String jsonPayload = String.format("{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}", prompt);

        // Build and send the HTTP Request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Extract the generated text from the JSON response and print to standard output
        try {
            String body = response.body();
            String explanation = body.split("\"text\": \"")[1].split("\"")[0];
            System.out.println(explanation.replace("\\n", " "));
        } catch (Exception e) {
            System.out.println("Analysis temporarily unavailable. Raw output: " + response.body());
        }
    }
}
