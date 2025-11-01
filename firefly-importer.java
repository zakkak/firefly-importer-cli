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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @Option(names = {"--dry-run"}, description = "Parse the file but do not import into Firefly III")
    boolean dryRun = false;

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
            String lastProductNumber = null;
            List<Transaction> transactions = new ArrayList<>(lines.size() - headerIndex - 1);
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
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(
                        postingDate, java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"));
                    postingDate = d.toString(); // ISO 8601 (yyyy-MM-dd)
                } catch (Exception e) {
                    // Fallback: keep original if parsing fails
                }
                // Drop the friendly name inside the parentheses and remove all spaces from the product number
                String productNumber = fields[3].replaceAll("\\s*\\(.*?\\)", "").replaceAll("\\s+", "");
                String amount = fields[4].split(" ")[0].replace(".", "").replace(",", "."); // Remove currency if present, remove dots and replace comma with dot

                // Use productNumber to find the corresponding account in Firefly III
                try {
                    String accountId = findAccountIdForProduct(productNumber);
                    if (accountId == null) {
                        System.out.println("No account found for product " + productNumber);
                        // TODO handle case where no account is found
                        continue;
                    }

                    dataCount++;

                    if (description.contains("(ΠΛΗΡΩΜΗ - ΕΥΧΑΡΙΣΤΟΥΜΕ)")) {
                        // handle as transfer
                        String sourceID;
                        if (lastProductNumber == null) {
                            Transaction last = transactions.removeLast();
                            sourceID = last.sourceID;
                        } else {
                            sourceID = findAccountIdForProduct(lastProductNumber);
                        }
                        transactions.add(new Transaction(
                            "transfer",
                            postingDate,
                            amount,
                            description,
                            sourceID,
                            accountId,
                            "Ανακατανομή"
                        ));
                        lastProductNumber = null;
                        continue;
                    } else if ("Ανακατανομή".equals(category)) {
                        if (lastProductNumber == null) {
                            lastProductNumber = productNumber;
                            continue;
                        }
                        // Merge the two transactions in a transfer
                        transactions.add(new Transaction(
                            "transfer",
                            postingDate,
                            amount.substring(1), // remove negative sign
                            description,
                            accountId,
                            findAccountIdForProduct(lastProductNumber),
                            "Ανακατανομή"
                        ));
                        lastProductNumber = null;
                        continue;
                    }
                    if (lastProductNumber != null) {
                        if ("Επαγγελματικά".equals(category)) {
                            // Merge the two transactions in a transfer
                            transactions.add(new Transaction(
                                "transfer",
                                postingDate,
                                amount.substring(1), // remove negative sign
                                description,
                                accountId,
                                findAccountIdForProduct(lastProductNumber),
                                "Ανακατανομή"
                            ));
                            lastProductNumber = null;
                            continue;
                        }
                        System.out.println("Warning: Unmatched redistribution entry:");
                        System.out.println("Last  " + lastProductNumber);
                        System.out.println("Current  " + fields[0] + " " + fields[1] + " " + fields[3] + " " + fields[4]);
                        lastProductNumber = null;
                        // fall through to normal transaction handling for current transaction
                    }
                    // Handle normal transaction entry
                    double amountNumber = Double.parseDouble(amount);
                    transactions.add(new Transaction(
                        amountNumber < 0 ? "withdrawal" : "deposit",
                        postingDate,
                        amountNumber < 0 ? amount.substring(1) : amount,
                        description,
                        amountNumber < 0 ? accountId : null,
                        amountNumber < 0 ? null : accountId,
                        category
                    ));
                } catch (Exception e) {
                    System.err.println("Error looking up account for product " + productNumber + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            System.out.println("\n✓ Successfully parsed " + dataCount + " data rows");
            System.out.println("✓ Prepared " + transactions.size() + " transactions for import");
            transactions.forEach(t -> {
                System.out.println(t.type + "\t| " + t.date + "\t| " + t.amount + "\t| " + t.description + "\t| " + t.sourceID + " -> " + t.destinationID);
            });
            System.out.println("Note: Import to Firefly III not yet implemented");
            return 0;
            
        } catch (IOException e) {
            System.err.println("✗ Error reading file: " + e.getMessage());
            return 1;
        }
    }

    private class Transaction {
        String type;
        String date;
        String amount;
        String description;
        String sourceID;
        String destinationID;
        String category;

        Transaction(String type, String date, String amount, String description, String sourceID, String destinationID, String category) {
            if (sourceID == destinationID) {
                System.out.println("Source ID: " + sourceID + " Destination ID: " + destinationID);
                System.out.println("Description: " + description);
                System.out.println("Category: " + category);
                System.out.println("Amount: " + amount);
                System.out.println("Transaction Type: " + type);
                throw new IllegalArgumentException("sourceID and destinationID cannot be the same");
            }
            this.type = type;
            this.date = date;
            this.amount = amount;
            this.description = description;
            this.sourceID = sourceID;
            this.category = category;
            this.destinationID = destinationID;
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
        public AccountAttributes attributes;
    }

    public static class AccountAttributes {
        public String name;
        public String account_number;
        public String iban;
        public String notes;
    }

}