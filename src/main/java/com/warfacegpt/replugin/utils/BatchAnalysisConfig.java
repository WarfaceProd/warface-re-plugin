package com.warfacegpt.replugin.utils;

/**
 * Utility for headless batch analysis.
 * Can be run from Ghidra's headless analyzer or as a script.
 * 
 * Usage (headless Ghidra):
 *   analyzeHeadless /path/to/project ProjectName \
 *     -import binary.bin \
 *     -postScript WarfaceBatchAnalysis.java \
 *     -scriptPath /path/to/warface-re-plugin/data \
 *     --mode vuln --model warface-core --output results.json
 * 
 * Or via WarfaceAPIClient directly from Java:
 *   WarfaceAPIClient client = new WarfaceAPIClient();
 *   client.setApiKey("wf-xxx");
 *   client.setModel("warface-core");
 *   String result = client.sendRequest(decompiledCode);
 */
public class BatchAnalysisConfig {

    // Analysis modes
    public static final String MODE_VULN = "vuln";
    public static final String MODE_RENAME = "rename";
    public static final String MODE_EXPLOIT = "exploit";
    public static final String MODE_CONTRACT = "contract";
    public static final String MODE_GENERAL = "general";

    // Model recommendations per mode
    public static final String MODEL_FOR_VULN = "warface-core";
    public static final String MODEL_FOR_RENAME = "warface-scout";
    public static final String MODEL_FOR_EXPLOIT = "warface-arsenal";
    public static final String MODEL_FOR_CONTRACT = "warface-core";
    public static final String MODEL_FOR_GENERAL = "warface-core";

    /**
     * Get the recommended WarfaceGPT model for an analysis mode.
     */
    public static String recommendedModel(String mode) {
        switch (mode) {
            case MODE_VULN: return MODEL_FOR_VULN;
            case MODE_RENAME: return MODEL_FOR_RENAME;
            case MODE_EXPLOIT: return MODEL_FOR_EXPLOIT;
            case MODE_CONTRACT: return MODEL_FOR_CONTRACT;
            default: return MODEL_FOR_GENERAL;
        }
    }

    /**
     * Get the WarfaceGPT API URL.
     * Hardcoded — no configuration needed.
     */
    public static String getAPIUrl() {
        return "https://gateway.warfacegpt.army/v1/chat/completions";
    }
}