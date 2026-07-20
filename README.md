# WarfaceGPT RE Plugin

AI-powered reverse engineering plugin for Ghidra — vulnerability detection, function analysis, and decompiler enhancement via WarfaceGPT.

## Features

- **Vulnerability Detection** — Analyze decompiled C pseudocode for security bugs (buffer overflows, use-after-free, format string bugs, integer overflow, TOCTOU, type confusion, info leaks)
- **Function Rename/Retype** — Get better names for obfuscated functions and variables
- **Exploit Chain Analysis** — Analyze how vulnerabilities can be exploited and chained
- **Smart Contract Audit** — Detect reentrancy, access control issues, flash loan attacks, and unchecked external calls in EVM bytecode
- **Batch Analysis** — Headless mode for analyzing entire binaries at once
- **7 Warface Models** — From fast cheap analysis (warface-scout) to heavyweight research (warface-supreme)

## Models

| Alias | Model | Best For | Speed | Cost |
|---|---|---|---|---|
| warface-scout | DeepSeek V4 Flash | Fast rename/retype | ⚡⚡⚡ | $ |
| warface-core | DeepSeek V4 Pro | Vulnerability detection | ⚡⚡ | $$ |
| warface-elite | DeepSeek V4 Flash | Quick analysis | ⚡⚡⚡ | $ |
| warface-titan | Hermes 4 405B | Deep binary analysis | ⚡ | $$$$ |
| warface-vision | Gemini 3.1 Pro | Multimodal analysis | ⚡ | $$$ |
| warface-supreme | Claude Fable 5 | Heavyweight research | 🐢 | $$$$ |
| warface-arsenal | DeepSeek V4 Pro | Exploit development | ⚡⚡ | $$ |

## Installation

### Prerequisites
- Ghidra 11.0+ (with JDK 17+)
- A WarfaceGPT API key (get one at https://warfacegpt.army)
- Maven 3.6+

### Build from Source

```bash
# Set GHIDRA_INSTALL_DIR to your Ghidra installation
export GHIDRA_INSTALL_DIR=/path/to/ghidra

# Clone and build
cd warface-re-plugin
mvn clean package

# The extension ZIP will be at:
# target/WarfaceGPT-RE-Plugin-1.0.0.zip
```

### Install in Ghidra

1. Build the extension ZIP (or download from releases)
2. In Ghidra: **File → Install Extensions...**
3. Click the **+** button and select the ZIP file
4. Restart Ghidra
5. The "WarfaceGPT RE" plugin will appear in **Window → WarfaceGPT RE**

### First Run

1. Open the WarfaceGPT RE panel: **Window → WarfaceGPT RE**
2. Click the **Configuration** tab
3. Enter your WarfaceGPT API key
4. Select a model (warface-core is recommended for general use)
5. Click **Test Connection** to verify
6. Click **Save Configuration**

## Usage

### Interactive Analysis (in Ghidra)

1. Open a binary in Ghidra
2. Select a function in the decompiler view
3. Open the WarfaceGPT RE panel: **Window → WarfaceGPT RE**
4. Choose an **Analysis Mode**:
   - `VULNERABILITY_DETECTION` — Find security bugs (uses warface-core)
   - `RENAME_RETYPE` — Get better names for functions/variables (uses warface-scout)
   - `EXPLOIT_ANALYSIS` — Analyze exploit chains (uses warface-arsenal)
   - `CONTRACT_AUDIT` — Audit EVM smart contracts (uses warface-core)
   - `GENERAL` — General analysis
5. The decompiled code is auto-filled from the current function
6. Click **🔍 Analyze**

### Batch Analysis (Headless)

```bash
# Analyze all functions for vulnerabilities
analyzeHeadless /path/to/project MyProject \
  -import target_binary \
  -postScript WarfaceBatchAnalysis.java \
  -scriptPath /path/to/warface-re-plugin/data \
  --mode vuln --model warface-core

# With environment variables
export WARFACE_API_KEY="wf-your-key-here"
export WARFACE_MODE="contract"     # vuln|rename|exploit|contract
export WARFACE_MODEL="warface-core"
export WARFACE_MAX_FUNCS="100"
export WARFACE_OUTPUT="analysis_results.txt"

analyzeHeadless /path/to/project MyProject \
  -import contract_bytecode \
  -postScript WarfaceBatchAnalysis.java \
  -scriptPath /path/to/warface-re-plugin/data
```

### Smart Contract Audit (EVM)

```bash
# Import contract bytecode into Ghidra, then:
export WARFACE_API_KEY="wf-your-key-here"
export WARFACE_MODE="contract"
export WARFACE_MODEL="warface-core"

analyzeHeadless /path/to/project MyProject \
  -import contract.bin \
  -postScript WarfaceBatchAnalysis.java \
  -scriptPath /path/to/warface-re-plugin/data
```

## API

The plugin connects to WarfaceGPT's OpenAI-compatible API:

- **Endpoint:** `https://gateway.warfacegpt.army/v1/chat/completions`
- **Authentication:** Bearer token (your WarfaceGPT API key)
- **Models:** warface-scout, warface-core, warface-elite, warface-titan, warface-vision, warface-supreme, warface-arsenal

You can also use the API directly with any OpenAI-compatible client:

```python
import openai

client = openai.OpenAI(
    api_key="wf-your-key-here",
    base_url="https://gateway.warfacegpt.army/v1"
)

response = client.chat.completions.create(
    model="warface-core",
    messages=[
        {"role": "system", "content": "You are a reverse engineering expert..."},
        {"role": "user", "content": "Analyze this decompiled function for vulnerabilities..."}
    ]
)
print(response.choices[0].message.content)
```

## Configuration

Config is stored in `~/.warfacegpt-re/config.properties`:

| Setting | Default | Description |
|---|---|---|
| api.key.encrypted | (none) | Your WarfaceGPT API key (XOR encrypted at rest) |
| api.model | warface-core | Default model alias |
| api.max.tokens | 8000 | Max tokens per request |
| api.temperature | 0.1 | Temperature (0.1 = deterministic, good for RE) |
| api.timeout.seconds | 120 | Request timeout |

## License

MIT License — see [LICENSE](LICENSE) for details.

## Links

- **WarfaceGPT:** https://warfacegpt.army
- **API Docs:** https://gateway.warfacegpt.army/v1/models
- **Issues:** https://github.com/WarfaceProd/warface-re-plugin/issues