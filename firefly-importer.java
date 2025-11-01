///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.7
//DEPS org.eclipse:yasson:3.0.4
//DEPS org.glassfish:jakarta.json:2.0.1

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;

@Command()
class ReusableOptions
{
    @Option(names = {"-u", "--url"}, 
            required = true,
            description = "Firefly III instance URL")
    String fireflyUrl;

    @Option(names = {"-t", "--token"}, 
            required = true,
            description = "Firefly III API token (Personal Access Token)")
    String apiToken;
}

@Command(name = "firefly-importer.java",
         mixinStandardHelpOptions = true, 
         version = "firefly-importer 0.1.0",
         description = "CLI tool for importing data into Firefly III",
         subcommands = {TestAuth.class})
class FireflyImporter implements Callable<Integer> {

    @Override
    public Integer call() {
        // Default action - show help
        new CommandLine(this).usage(System.out);
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new FireflyImporter()).execute(args);
        System.exit(exitCode);
    }

}

@Command(name = "test-auth", description = "Test authentication with Firefly III API")
class TestAuth extends ReusableOptions implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Testing authentication with Firefly III instance at: " + fireflyUrl);
        
        try {
            if (authenticate()) {
                System.out.println("✓ Authentication successful!");
                return 0;
            } else {
                System.err.println("✗ Authentication failed!");
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error during authentication: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private boolean authenticate() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // Ensure URL doesn't end with slash
        String baseUrl = fireflyUrl.endsWith("/") ? fireflyUrl.substring(0, fireflyUrl.length() - 1) : fireflyUrl;

        // Test authentication by calling the /api/v1/about endpoint
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/about"))
                .header("Authorization", "Bearer " + apiToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String responseBody = response.body();

        if (status == 200 && responseBody != null && !responseBody.isEmpty()) {
            Jsonb jsonb = JsonbBuilder.create();
            try {
                AboutResponse about = jsonb.fromJson(responseBody, AboutResponse.class);
                if (about != null && about.data != null) {
                    AboutData data = about.data;
                    System.out.println("\nFirefly III Instance Information:");
                    if (data.version != null) System.out.println("  Version: " + data.version);
                    if (data.api_version != null) System.out.println("  API Version: " + data.api_version);
                    if (data.php_version != null) System.out.println("  PHP Version: " + data.php_version);
                    if (data.os != null) System.out.println("  OS: " + data.os);
                }
                return true;
            } catch (Exception e) {
                System.err.println("Failed to parse JSON response: " + e.getMessage());
                return false;
            }
        } else {
            System.err.println("HTTP Error: " + status);
            if (responseBody != null && !responseBody.isEmpty()) {
                System.err.println("Response: " + responseBody);
            }
            return false;
        }
    }

    // JSON-B mapping classes for the /api/v1/about response
    public static class AboutResponse {
        public AboutData data;
    }

    public static class AboutData {
        public String version;
        public String api_version;
        public String php_version;
        public String os;
    }
}
