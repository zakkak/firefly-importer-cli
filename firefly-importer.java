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
import java.util.Map;
import java.util.HashMap;
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
         subcommands = {PiraeusImporter.class, TestAuth.class})
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

class Utils {
    static HttpResponse<String> get(String baseUrl, String path, String token) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        // Ensure URL doesn't end with slash
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        url += path.startsWith("/") ? path : "/" + path;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
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
        HttpResponse<String> response = Utils.get(fireflyUrl, "/api/v1/about", apiToken);
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

@Command(name = "import-piraeus-data", description = "Import piraeus unified data into Firefly III (Not yet implemented)")
class PiraeusImporter extends ReusableOptions implements Callable<Integer> {

    private static final int PIRAEUS_HEADER_COLUMNS = 5;
    // Cache productNumber -> accountId (null means not found)
    private final Map<String, String> accountCache = new HashMap<>();

    @Parameters(paramLabel = "<data-file>", description = "Path to the data file to import")
    String dataFile;

    /**
     * Import data into Firefly III from a tab separated values file generated
     * through Piraeus e-banking (unified transactions view).
     *
     * The file is expected to have some info in the first lines. Then a header
     * line followed by data lines. And finally an ending line with some
     * additional info.
     * 
     * The expected header columns (in Greek) are:
     * Κατηγορία	Περιγραφή Συναλλαγής (είδος)	Ημερομηνία Καταχώρησης	Αριθμός Προϊόντος	Ποσό	
     * 
     * @return Exit code
     */
    @Override
    public Integer call() {
        System.out.println("Importing data from: " + dataFile);
                
        try {
            java.nio.file.Path filePath = java.nio.file.Path.of(dataFile);
            if (!java.nio.file.Files.exists(filePath)) {
                System.err.println("✗ File not found: " + dataFile);
                return 1;
            }
            
            java.util.List<String> lines = java.nio.file.Files.readAllLines(filePath);
            if (lines.isEmpty()) {
                System.err.println("✗ File is empty");
                return 1;
            }
            
            // Skip initial info lines until we find the header
            int headerIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("Κατηγορία") && lines.get(i).contains("Περιγραφή Συναλλαγής")) {
                    headerIndex = i;
                    break;
                }
            }
            
            if (headerIndex == -1) {
                System.err.println("✗ Could not find expected header line");
                return 1;
            }
            
            String headerLine = lines.get(headerIndex);
            String[] headers = headerLine.split("\t");
            if (headers.length != PIRAEUS_HEADER_COLUMNS) {
                System.err.println("✗ Header line does not contain expected number of columns");
                System.err.println("✗ Found " + headers.length + " columns, expected " + PIRAEUS_HEADER_COLUMNS);
                return 1;
            }
            System.out.println("Found header with " + headers.length + " columns");
            
            // Process data lines (skip header and info lines)
            int dataCount = 0;
            String[] lastFields = null;
            for (int i = headerIndex + 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                
                String[] fields = line.split("\t");
                if (fields.length != PIRAEUS_HEADER_COLUMNS) {
                    // Likely the ending info line
                    break;
                }

                String category = fields[0];
                String description = fields[1];
                String postingDate = fields[2];
                // Drop the friendly name inside the parentheses and remove all spaces from the product number
                String productNumber = fields[3].replaceAll("\\s*\\(.*?\\)", "").replaceAll("\\s+", "");
                String amount = fields[4];

                // Use productNumber to find the corresponding account in Firefly III
                try {
                    String accountId = findAccountIdForProduct(productNumber);
                    if (accountId != null) {
                        System.out.println("Found account for product " + productNumber + ": " + accountId);
                    } else {
                        System.out.println("No account found for product " + productNumber);
                    }

                    if ("Ανακατανομή".equals(category)) {
                        if (lastFields == null) {
                            lastFields = fields;
                            continue;
                        }
                        // TODO Merge the two transactions in a transfer
                        lastFields = null;
                    }
                    if (lastFields != null) {
                        if ("Επαγγελματικά".equals(category)) {
                            // TODO Merge the two transactions in a transfer
                            continue;
                        }
                        // TODO warn user about unmatched redistribution
                        System.out.println("Warning: Unmatched redistribution entry: " + lastFields[0] + " " + lastFields[1] + " " + lastFields[3] + " " + lastFields[4]);
                    }
                    // TODO handle normal transaction entry
                } catch (Exception e) {
                    System.err.println("Error looking up account for product " + productNumber + ": " + e.getMessage());
                }
                
                dataCount++;
            }
            
            System.out.println("\n✓ Successfully parsed " + dataCount + " data rows");
            System.out.println("Note: Import to Firefly III not yet implemented");
            return 0;
            
        } catch (IOException e) {
            System.err.println("✗ Error reading file: " + e.getMessage());
            return 1;
        }
    }


    private String findAccountIdForProduct(String productNumber) throws IOException, InterruptedException {
        if (productNumber == null || productNumber.isBlank()) {
            return null;
        }

        // Check cache first (containsKey used to allow caching "not found" as null)
        if (accountCache.containsKey(productNumber)) {
            return accountCache.get(productNumber);
        }

        HttpResponse<String> response = Utils.get(fireflyUrl, "/api/v1/accounts", apiToken);

        int status = response.statusCode();
        String body = response.body();

        if (status == 200 && body != null && !body.isEmpty()) {
            Jsonb jsonb = JsonbBuilder.create();
            try {
                AccountsResponse accounts = jsonb.fromJson(body, AccountsResponse.class);
                if (accounts != null && accounts.data != null) {
                    for (AccountItem item : accounts.data) {
                        if (item == null || item.attributes == null) {
                            continue;
                        }
                        AccountAttributes a = item.attributes;
                        if (matchesProduct(a, productNumber)) {
                            accountCache.put(productNumber, item.id);
                            return item.id;
                        }
                    }
                }
            } catch (Exception e) {
                // parsing failed; fall through to return null
                System.err.println("Warning: could not parse accounts response: " + e.getMessage());
            }
        }

        // Cache negative result to avoid repeated lookups
        accountCache.put(productNumber, null);
        return null;
    }

    private boolean matchesProduct(AccountAttributes a, String productNumber) {
        if (a == null) {
            return false;
        }
        if (productNumber.equals(a.account_number)) {
            return true;
        }
        if (productNumber.equals(a.iban)) {
            return true;
        }
        if (a.notes != null && a.notes.contains(productNumber)) {
            return true;
        }
        return false;
    }

    // JSON-B mapping classes for accounts endpoint
    public static class AccountsResponse {
        public java.util.List<AccountItem> data;
    }

    public static class AccountItem {
        public String id;
        public String type;
        public AccountAttributes attributes;
    }

    public static class AccountAttributes {
        public String name;
        public String number;
        public String account_number;
        public String product_number;
        public String iban;
        public String notes;
    }

}