# Firefly III Importer CLI

A JBang-based CLI tool for importing data into Firefly III instances.

## Prerequisites

- Java 11 or higher
- [JBang](https://www.jbang.dev/) installed

## Installation

```bash
# Install JBang if you haven't already
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Make the script executable
chmod +x firefly-importer.java
```

## Authentication

The CLI requires authentication with Firefly III using Personal Access Tokens. You must provide the URL and token via command-line options for each command.

### Getting Your API Token

1. Log in to your Firefly III instance
2. Go to Options → Profile → OAuth
3. Click "Create New Token"
4. Give it a name and click "Create"
5. Copy the generated token

## Commands

### `test-auth`

Test authentication with your Firefly III instance.

**Options:**

- `-u, --url <URL>` - Firefly III instance URL (required)
- `-t, --token <TOKEN>` - Firefly III API token (Personal Access Token) (required)

**Example:**

```bash
./firefly-importer.java test-auth --url https://firefly.example.com --token eyJ0eXAiOiJKV1QiLCJhbGc...
```

### `import-piraeus-data`

Import transaction data from Piraeus Bank (Greece) unified transactions export into Firefly III.

**Options:**

- `-u, --url <URL>` - Firefly III instance URL (required)
- `-t, --token <TOKEN>` - Firefly III API token (Personal Access Token) (required)
- `--dry-run` - Parse the file but do not import into Firefly III
- `<data-file>` - Path to the Piraeus unified transactions TSV file (required)

**Expected File Format:**

The import expects a tab-separated values (TSV) file exported from Piraeus e-banking with the following Greek header columns:
- Κατηγορία (Category)
- Περιγραφή Συναλλαγής (Transaction Description)
- Ημερομηνία Καταχώρησης (Posting Date)
- Αριθμός Προϊόντος (Product Number)
- Ποσό (Amount)

**Account Matching:**

The importer matches Piraeus product numbers to Firefly III accounts by checking:
1. Account number field
2. IBAN field
3. Notes field (if product number appears in notes)

**Transaction Handling:**

- Positive amounts are imported as deposits
- Negative amounts are imported as withdrawals
- "Ανακατανομή" (Redistribution) category entries are merged into transfers between accounts
- Credit card payments marked with "(ΠΛΗΡΩΜΗ - ΕΥΧΑΡΙΣΤΟΥΜΕ)" are handled as transfers

**Example:**

```bash
# Dry run to preview transactions
./firefly-importer.java import-piraeus-data --url https://firefly.example.com --token YOUR_TOKEN --dry-run piraeus-unified-example.txt

# Actually import the transactions
./firefly-importer.java import-piraeus-data --url https://firefly.example.com --token YOUR_TOKEN piraeus-unified-example.txt
```

## API Documentation

This CLI uses the [Firefly III API](https://api-docs.firefly-iii.org/).

## Development

The script uses:

- **JBang** - Script-based Java execution
- **Picocli** - Command-line argument parsing
- **Java HttpClient** - HTTP client for API calls
- **Jakarta JSON-B (Yasson)** - JSON parsing and binding

## License

See LICENSE file for details.
