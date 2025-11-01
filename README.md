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
chmod +x firefly-importer
```

## Authentication

The CLI supports authentication with Firefly III using Personal Access Tokens.

### Getting Your API Token

1. Log in to your Firefly III instance
2. Go to Options → Profile → OAuth
3. Click "Create New Token"
4. Give it a name and click "Create"
5. Copy the generated token

### Using the CLI

#### Test Authentication

```bash
# Test with command-line arguments
./firefly-importer test-auth --url https://your-firefly-instance.com --token your-api-token

# Save configuration for future use
./firefly-importer test-auth --url https://your-firefly-instance.com --token your-api-token --save-config

# Use saved configuration
./firefly-importer test-auth
```

#### Configuration File

The CLI can save your credentials to `~/.firefly-importer/config` for convenience:

```properties
firefly.url=https://your-firefly-instance.com
firefly.token=your-api-token
```

You can also specify a custom config location:

```bash
./firefly-importer test-auth --config /path/to/custom/config
```

## Commands

### `test-auth`

Test authentication with your Firefly III instance.

**Options:**

- `-u, --url <URL>` - Firefly III instance URL
- `-t, --token <TOKEN>` - Firefly III API token (Personal Access Token)
- `-c, --config <PATH>` - Path to configuration file (default: `~/.firefly-importer/config`)
- `--save-config` - Save the provided URL and token to configuration file

**Example:**

```bash
./firefly-importer test-auth --url https://firefly.example.com --token eyJ0eXAiOiJKV1QiLCJhbGc... --save-config
```

## API Documentation

This CLI uses the [Firefly III API](https://api-docs.firefly-iii.org/).

## Development

The script uses:

- **JBang** - Script-based Java execution
- **Picocli** - Command-line argument parsing
- **OkHttp** - HTTP client for API calls
- **Gson** - JSON parsing

## License

See LICENSE file for details.
