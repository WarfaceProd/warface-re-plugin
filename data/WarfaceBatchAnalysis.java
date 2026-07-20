// WarfaceGPT Batch Analysis Script for Ghidra Headless Analyzer
// Run: analyzeHeadless /path/to/project ProjectName -import binary.bin -postScript WarfaceBatchAnalysis.java
//
// Parameters (environment variables or script args):
//   WARFACE_API_KEY  - Required. Your WarfaceGPT API key
//   WARFACE_MODE     - vuln|rename|exploit|contract|general (default: vuln)
//   WARFACE_MODEL    - Warface model alias (default: auto-selected per mode)
//   WARFACE_MAX_FUNCS - Max functions to analyze (default: 50)
//   WARFACE_OUTPUT   - Output file path (default: warface_analysis_results.txt)

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WarfaceBatchAnalysis extends GhidraScript {

    private static final String API_URL = "https://gateway.warfacegpt.army/v1/chat/completions";

    private static final String VULN_SYSTEM = "You are a security researcher performing vulnerability detection on decompiled binary code. Analyze the following decompiled function for security vulnerabilities. Focus on: buffer overflows, use-after-free, double-free, format string bugs, integer overflow/underflow, TOCTOU, type confusion, uninitialized memory, information leaks. For each finding: VULN: [CRITICAL|HIGH|MEDIUM|LOW] description. If safe, say 'No vulnerabilities detected.'";

    private static final String RENAME_SYSTEM = "You are a reverse engineering expert analyzing obfuscated/decompiled code. Suggest better names for the function and its variables. Format: FUNCTION_NAME: name. RENAME: old -> new (type) - reason";

    private static final String EXPLOIT_SYSTEM = "You are an exploit developer analyzing vulnerable code. Provide: ATTACK SURFACE, EXPLOIT STRATEGY, PRIMITIVES, CHAIN, MITIGATION.";

    private static final String CONTRACT_SYSTEM = "You are a smart contract security auditor analyzing decompiled EVM bytecode. Check for: reentrancy, access control, integer overflow, flash loan attacks, unchecked external calls, front-running, storage collision. Format: VULN: [severity] description";

    @Override
    public void run() throws Exception {
        String apiKey = getEnvOrArg("WARFACE_API_KEY", "");
        String mode = getEnvOrArg("WARFACE_MODE", "vuln");
        String model = getEnvOrArg("WARFACE_MODEL", "");
        int maxFuncs = Integer.parseInt(getEnvOrArg("WARFACE_MAX_FUNCS", "50"));
        String outputPath = getEnvOrArg("WARFACE_OUTPUT", "warface_analysis_results.txt");

        if (apiKey.isEmpty()) {
            apiKey = System.getenv("WARFACE_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                println("ERROR: WARFACE_API_KEY not set. Get your key at https://warfacegpt.army");
                return;
            }
        }

        if (model.isEmpty()) {
            switch (mode) {
                case "vuln": model = "warface-core"; break;
                case "rename": model = "warface-scout"; break;
                case "exploit": model = "warface-arsenal"; break;
                case "contract": model = "warface-core"; break;
                default: model = "warface-core"; break;
            }
        }

        String systemPrompt;
        switch (mode) {
            case "vuln": systemPrompt = VULN_SYSTEM; break;
            case "rename": systemPrompt = RENAME_SYSTEM; break;
            case "exploit": systemPrompt = EXPLOIT_SYSTEM; break;
            case "contract": systemPrompt = CONTRACT_SYSTEM; break;
            default: systemPrompt = VULN_SYSTEM; break;
        }

        println("=== WarfaceGPT Batch Analysis ===");
        println("Mode: " + mode + " | Model: " + model + " | Max funcs: " + maxFuncs);

        DecompInterface decompiler = new DecompInterface();
        decompiler.openProgram(currentProgram);

        FunctionManager funcMgr = currentProgram.getFunctionManager();
        List<Function> functions = funcMgr.getFunctions(true).stream()
            .limit(maxFuncs)
            .collect(Collectors.toList());

        println("Analyzing " + functions.size() + " functions...");
        List<String> results = new ArrayList<>();

        for (Function func : functions) {
            String funcName = func.getName();
            String address = func.getEntryPoint().toString();

            DecompileResults decompResult = decompiler.decompileFunction(func, 30, monitor);
            if (decompResult.depiledFunction() == null) {
                println("SKIP: " + funcName + " @ " + address + " (decompilation failed)");
                continue;
            }

            String decompiledCode = decompResult.getDecompiledFunction().getC();
            if (decompiledCode == null || decompiledCode.trim().isEmpty()) continue;

            String prompt = systemPrompt + "\n\n--- FUNCTION: " + funcName + " @ " + address + " ---\n\n" + decompiledCode;

            try {
                String result = callWarfaceAPI(apiKey, model, prompt, systemPrompt);
                results.add("=== " + funcName + " @ " + address + " ===\n" + result);
                println("DONE: " + funcName + " @ " + address);
            } catch (Exception e) {
                results.add("=== " + funcName + " @ " + address + " ===\nERROR: " + e.getMessage());
                println("ERROR: " + funcName + " @ " + address + ": " + e.getMessage());
            }

            Thread.sleep(1000); // Rate limit
        }

        decompiler.dispose();

        String output = String.join("\n\n", results);
        Files.write(Paths.get(outputPath), output.getBytes(StandardCharsets.UTF_8));
        println("Results written to: " + outputPath);
        println("=== Analysis Complete ===");
    }

    private String callWarfaceAPI(String apiKey, String model, String prompt, String systemPrompt) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(120000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);

        String jsonBody = buildJsonRequest(model, systemPrompt, prompt);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        InputStream is = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) response.append(line);
        }

        if (responseCode != 200) {
            throw new Exception("API error " + responseCode + ": " + response.toString());
        }

        // Extract content from JSON response
        String resp = response.toString();
        int idx = resp.indexOf("\"content\":");
        if (idx == -1) throw new Exception("No content in response: " + resp.substring(0, Math.min(200, resp.length())));
        int q1 = resp.indexOf("\"", idx + 10) + 1;
        int q2 = resp.indexOf("\"", q1);
        if (q1 <= 0 || q2 <= q1) throw new Exception("Failed to parse content");
        return resp.substring(q1, q2).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String buildJsonRequest(String model, String systemPrompt, String userPrompt) {
        // Simple JSON builder (no external deps for headless script)
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userPrompt)).append("\"}");
        sb.append("],");
        sb.append("\"max_tokens\":8000,\"temperature\":0.1,\"stream\":false}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String getEnvOrArg(String key, String defaultVal) {
        String val = System.getenv(key);
        if (val != null && !val.isEmpty()) return val;
        String[] args = getScriptArgs();
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--" + key + "=")) {
                    return arg.substring(key.length() + 3);
                }
            }
        }
        return defaultVal;
    }
}