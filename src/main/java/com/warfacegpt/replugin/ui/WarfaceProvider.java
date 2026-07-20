package com.warfacegpt.replugin.ui;

import com.warfacegpt.replugin.WarfaceREPlugin;
import com.warfacegpt.replugin.service.WarfaceAPIClient;
import com.warfacegpt.replugin.service.WarfacePrompts;
import com.warfacegpt.replugin.config.ConfigurationManager;
import ghidra.app.services.CodeViewerService;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import ghidra.program.model.listing.Program;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.util.Msg;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main UI panel for the WarfaceGPT RE Plugin.
 * Provides analysis modes, model selection, and results display.
 */
public class WarfaceProvider extends JPanel {

    private final WarfaceREPlugin plugin;
    private final JTabbedPane tabbedPane;

    // Configuration tab components
    private final JComboBox<String> modelCombo;
    private final JPasswordField apiKeyField;
    private final JSpinner maxTokensSpinner;
    private final JSpinner temperatureSpinner;
    private final JSpinner timeoutSpinner;
    private final JLabel statusLabel;

    // Analysis tab components
    private final JComboBox<WarfacePrompts.AnalysisMode> analysisModeCombo;
    private final JTextArea decompiledCodeArea;
    private final JTextArea resultArea;
    private final JButton analyzeButton;
    private final JButton stopButton;
    private final JButton fetchModelsButton;

    // Decompiler
    private DecompInterface decompiler;
    private Program currentProgram;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean analysisRunning = false;

    public WarfaceProvider(WarfaceREPlugin plugin, String pluginName) {
        this.plugin = plugin;

        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // === Analysis Tab ===
        JPanel analysisPanel = new JPanel(new BorderLayout(5, 5));
        analysisPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Top: analysis mode selector
        JPanel modePanel = new JPanel(new BorderLayout(5, 0));
        modePanel.add(new JLabel("Analysis Mode:"), BorderLayout.WEST);
        analysisModeCombo = new JComboBox<>(WarfacePrompts.AnalysisMode.values());
        analysisModeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof WarfacePrompts.AnalysisMode) {
                    WarfacePrompts.AnalysisMode mode = (WarfacePrompts.AnalysisMode) value;
                    String tip = switch (mode) {
                        case VULNERABILITY_DETECTION -> "Detect security bugs (recommended: warface-core)";
                        case RENAME_RETYPE -> "Suggest better names/types (recommended: warface-scout)";
                        case EXPLOIT_ANALYSIS -> "Analyze exploit chains (recommended: warface-arsenal)";
                        case CONTRACT_AUDIT -> "Audit EVM smart contracts (recommended: warface-core)";
                        case GENERAL -> "General analysis (uses selected model)";
                    };
                    setToolTipText(tip);
                }
                return this;
            }
        });
        modePanel.add(analysisModeCombo, BorderLayout.CENTER);

        // Auto-select best model for the analysis mode
        analysisModeCombo.addActionListener(e -> autoSelectModel());

        // Current function info
        JLabel functionLabel = new JLabel("No function selected");
        functionLabel.setFont(functionLabel.getFont().deriveFont(Font.BOLD));
        modePanel.add(functionLabel, BorderLayout.SOUTH);

        analysisPanel.add(modePanel, BorderLayout.NORTH);

        // Middle: split pane with decompiled code and results
        decompiledCodeArea = new JTextArea();
        decompiledCodeArea.setEditable(true);
        decompiledCodeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        decompiledCodeArea.setLineWrap(false);
        JScrollPane decompScroll = new JScrollPane(decompiledCodeArea);
        decompScroll.setBorder(BorderFactory.createTitledBorder("Decompiled Code (auto-filled from Ghidra, or paste manually)"));

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Analysis Result"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, decompScroll, resultScroll);
        splitPane.setResizeWeight(0.4);
        analysisPanel.add(splitPane, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        analyzeButton = new JButton("🔍 Analyze");
        analyzeButton.setPreferredSize(new Dimension(150, 35));
        analyzeButton.setFont(analyzeButton.getFont().deriveFont(Font.BOLD, 13f));
        analyzeButton.addActionListener(e -> runAnalysis());
        buttonPanel.add(analyzeButton);

        stopButton = new JButton("⏹ Stop");
        stopButton.setPreferredSize(new Dimension(100, 35));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopAnalysis());
        buttonPanel.add(stopButton);

        JButton clearButton = new JButton("🗑 Clear");
        clearButton.setPreferredSize(new Dimension(100, 35));
        clearButton.addActionListener(e -> {
            decompiledCodeArea.setText("");
            resultArea.setText("");
        });
        buttonPanel.add(clearButton);

        analysisPanel.add(buttonPanel, BorderLayout.SOUTH);
        tabbedPane.addTab("Analysis", analysisPanel);

        // === Configuration Tab ===
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel headerLabel = new JLabel("<html><b>WarfaceGPT RE Plugin Configuration</b><br>" +
            "<i>Connects to gateway.warfacegpt.army — no custom URL needed</i></html>");
        add(configPanel, headerLabel, gbc);

        // API Key
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("WarfaceGPT API Key:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        apiKeyField = new JPasswordField(30);
        apiKeyField.setToolTipText("Get your API key at https://warfacegpt.army");
        configPanel.add(apiKeyField, gbc);

        // Model selection
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("Model:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JPanel modelPanel = new JPanel(new BorderLayout(5, 0));
        modelCombo = new JComboBox<>(WarfaceAPIClient.ALL_MODELS);
        modelCombo.setEditable(false);
        modelPanel.add(modelCombo, BorderLayout.CENTER);

        fetchModelsButton = new JButton("Fetch");
        fetchModelsButton.setPreferredSize(new Dimension(70, 25));
        fetchModelsButton.addActionListener(e -> fetchModels());
        modelPanel.add(fetchModelsButton, BorderLayout.EAST);
        configPanel.add(modelPanel, gbc);

        // Max Tokens
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("Max Tokens:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        maxTokensSpinner = new JSpinner(new SpinnerNumberModel(WarfaceAPIClient.DEFAULT_MAX_TOKENS, 100, 32000, 100));
        configPanel.add(maxTokensSpinner, gbc);

        // Temperature
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("Temperature:"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        temperatureSpinner = new JSpinner(new SpinnerNumberModel(WarfaceAPIClient.DEFAULT_TEMPERATURE, 0.0, 2.0, 0.1));
        configPanel.add(temperatureSpinner, gbc);

        // Timeout
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        configPanel.add(new JLabel("Timeout (seconds):"), gbc);

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(WarfaceAPIClient.DEFAULT_TIMEOUT_SECONDS, 10, 300, 10));
        configPanel.add(timeoutSpinner, gbc);

        // Model recommendations
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel recLabel = new JLabel("<html><b>Model recommendations:</b><br>" +
            "• warface-scout — Fast rename/retype (cheapest)<br>" +
            "• warface-core — Vulnerability detection & general analysis<br>" +
            "• warface-arsenal — Exploit chain analysis<br>" +
            "• warface-titan — Deep binary analysis (strongest reasoning)<br>" +
            "• warface-supreme — Heavyweight research (Claude)<br>" +
            "• warface-vision — Multimodal analysis (Gemini)<br>" +
            "• warface-elite — Fast analysis (same as scout)</html>");
        configPanel.add(recLabel, gbc);

        // Buttons
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        JPanel buttonPanel2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));

        JButton testButton = new JButton("Test Connection");
        testButton.setPreferredSize(new Dimension(150, 30));
        testButton.addActionListener(e -> testConnection());
        buttonPanel2.add(testButton);

        JButton saveButton = new JButton("Save Configuration");
        saveButton.setPreferredSize(new Dimension(150, 30));
        saveButton.addActionListener(e -> saveConfiguration());
        buttonPanel2.add(saveButton);

        configPanel.add(buttonPanel2, gbc);

        // Status
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        statusLabel = new JLabel("Not configured");
        statusLabel.setForeground(Color.RED);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        configPanel.add(statusLabel, gbc);

        // Spacer
        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        configPanel.add(new JPanel(), gbc);

        tabbedPane.addTab("Configuration", configPanel);

        // Load saved configuration
        loadConfiguration();
    }

    private void add(JPanel panel, JComponent comp, GridBagConstraints gbc) {
        panel.add(comp, gbc);
    }

    // === Auto-select best model for analysis mode ===

    private void autoSelectModel() {
        WarfacePrompts.AnalysisMode mode = (WarfacePrompts.AnalysisMode) analysisModeCombo.getSelectedItem();
        if (mode == null) return;
        String recommended = switch (mode) {
            case VULNERABILITY_DETECTION -> WarfaceAPIClient.RECOMMENDED_VULN;
            case RENAME_RETYPE -> WarfaceAPIClient.RECOMMENDED_RENAME;
            case EXPLOIT_ANALYSIS -> WarfaceAPIClient.RECOMMENDED_EXPLOIT;
            case CONTRACT_AUDIT -> WarfaceAPIClient.RECOMMENDED_VULN;
            case GENERAL -> WarfaceAPIClient.DEFAULT_MODEL;
        };
        modelCombo.setSelectedItem(recommended);
    }

    // === Run Analysis ===

    private void runAnalysis() {
        if (analysisRunning) return;

        WarfaceAPIClient client = plugin.getAPIClient();
        if (client.getApiKey() == null || client.getApiKey().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please configure your WarfaceGPT API key first.\nGet one at https://warfacegpt.army",
                "Not Configured", JOptionPane.ERROR_MESSAGE);
            showConfigurationTab();
            return;
        }

        String code = decompiledCodeArea.getText().trim();
        if (code.isEmpty()) {
            // Try to get decompiled code from current function
            String decompiled = decompileCurrentFunction();
            if (decompiled == null || decompiled.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "No decompiled code. Select a function in Ghidra or paste code manually.",
                    "No Code", JOptionPane.WARNING_MESSAGE);
                return;
            }
            decompiledCodeArea.setText(decompiled);
            code = decompiled;
        }

        WarfacePrompts.AnalysisMode mode = (WarfacePrompts.AnalysisMode) analysisModeCombo.getSelectedItem();
        String prompt = WarfacePrompts.buildFunctionPrompt("selected_function", code, mode);

        analyzeButton.setEnabled(false);
        analyzeButton.setText("Analyzing...");
        stopButton.setEnabled(true);
        resultArea.setText("Analyzing with " + client.getModel() + "...\n\n");
        analysisRunning = true;

        executor.submit(() -> {
            try {
                WarfaceAPIClient.StreamCallback callback = new WarfaceAPIClient.StreamCallback() {
                    private final StringBuilder sb = new StringBuilder();

                    @Override
                    public void onPartialResponse(String partialContent) {
                        sb.append(partialContent);
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(sb.toString());
                            resultArea.setCaretPosition(resultArea.getDocument().getLength());
                        });
                    }

                    @Override
                    public void onComplete(String fullContent) {
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(sb.toString());
                            analyzeButton.setEnabled(true);
                            analyzeButton.setText("🔍 Analyze");
                            stopButton.setEnabled(false);
                            analysisRunning = false;
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText("Error: " + error.getMessage());
                            analyzeButton.setEnabled(true);
                            analyzeButton.setText("🔍 Analyze");
                            stopButton.setEnabled(false);
                            analysisRunning = false;
                        });
                    }
                };

                String systemPrompt = switch (mode) {
                    case VULNERABILITY_DETECTION -> WarfacePrompts.VULN_DETECTION_PROMPT;
                    case RENAME_RETYPE -> WarfacePrompts.RENAME_PROMPT;
                    case EXPLOIT_ANALYSIS -> WarfacePrompts.EXPLOIT_PROMPT;
                    case CONTRACT_AUDIT -> WarfacePrompts.CONTRACT_AUDIT_PROMPT;
                    default -> WarfacePrompts.SYSTEM_PROMPT;
                };

                client.sendRequestWithSystemPrompt(code, systemPrompt, callback);

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    resultArea.setText("Error: " + e.getMessage());
                    analyzeButton.setEnabled(true);
                    analyzeButton.setText("🔍 Analyze");
                    stopButton.setEnabled(false);
                    analysisRunning = false;
                });
            }
        });
    }

    private void stopAnalysis() {
        analysisRunning = false;
        analyzeButton.setEnabled(true);
        analyzeButton.setText("🔍 Analyze");
        stopButton.setEnabled(false);
        resultArea.setText(resultArea.getText() + "\n\n--- Analysis stopped by user ---");
    }

    // === Decompile current function ===

    private String decompileCurrentFunction() {
        if (currentProgram == null) return null;

        try {
            if (decompiler == null || decompiler.getProgram() != currentProgram) {
                if (decompiler != null) decompiler.dispose();
                decompiler = new DecompInterface();
                decompiler.openProgram(currentProgram);
            }

            // Get the current function from the code viewer
            CodeViewerService codeViewer = plugin.getTool().getService(CodeViewerService.class);
            if (codeViewer == null) return null;

            FunctionManager funcMgr = currentProgram.getFunctionManager();
            // Try to get function at current address
            ghidra.program.model.address.Address currentAddr = codeViewer.getCurrentLocation().getAddress();
            if (currentAddr == null) return null;

            Function func = funcMgr.getFunctionContaining(currentAddr);
            if (func == null) return null;

            DecompileResults results = decompiler.decompileFunction(func, 60, null);
            if (results.depiledFunction() != null) {
                return results.getDecompiledFunction().getC();
            }
        } catch (Exception e) {
            Msg.warn(this, "Failed to decompile: " + e.getMessage());
        }
        return null;
    }

    // === Configuration ===

    private void loadConfiguration() {
        ConfigurationManager config = plugin.getConfigManager();
        apiKeyField.setText(config.getApiKey());
        modelCombo.setSelectedItem(config.getModel());
        maxTokensSpinner.setValue(config.getMaxTokens());
        temperatureSpinner.setValue(config.getTemperature());
        timeoutSpinner.setValue(config.getTimeoutSeconds());

        if (config.isConfigured()) {
            statusLabel.setText("Configuration loaded — " + config.getModel());
            statusLabel.setForeground(new Color(0, 128, 0));
            applyConfiguration();
        } else {
            statusLabel.setText("Not configured — enter your WarfaceGPT API key");
            statusLabel.setForeground(Color.RED);
        }
    }

    private void saveConfiguration() {
        String apiKey = new String(apiKeyField.getPassword()).trim();
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter your WarfaceGPT API key.\nGet one at https://warfacegpt.army",
                "API Key Required", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ConfigurationManager config = plugin.getConfigManager();
        config.setApiKey(apiKey);
        config.setModel((String) modelCombo.getSelectedItem());
        config.setMaxTokens((Integer) maxTokensSpinner.getValue());
        config.setTemperature((Double) temperatureSpinner.getValue());
        config.setTimeoutSeconds((Integer) timeoutSpinner.getValue());
        config.saveConfiguration();

        applyConfiguration();

        statusLabel.setText("Configuration saved — " + config.getModel());
        statusLabel.setForeground(new Color(0, 128, 0));

        JOptionPane.showMessageDialog(this,
            "Configuration saved!\nSaved to: " + config.getConfigurationPath(),
            "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    private void applyConfiguration() {
        ConfigurationManager config = plugin.getConfigManager();
        WarfaceAPIClient client = plugin.getAPIClient();
        client.setApiKey(config.getApiKey());
        client.setModel(config.getModel());
        client.setMaxTokens(config.getMaxTokens());
        client.setTemperature(config.getTemperature());
        client.setTimeoutSeconds(config.getTimeoutSeconds());
    }

    private void testConnection() {
        String apiKey = new String(apiKeyField.getPassword()).trim();
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter your WarfaceGPT API key first.",
                "API Key Required", JOptionPane.ERROR_MESSAGE);
            return;
        }

        WarfaceAPIClient client = plugin.getAPIClient();
        client.setApiKey(apiKey);
        client.setModel((String) modelCombo.getSelectedItem());

        statusLabel.setText("Testing connection...");
        statusLabel.setForeground(Color.ORANGE);

        executor.submit(() -> {
            try {
                String result = client.sendRequest("Hello, this is a test. Please respond with 'WarfaceGPT connection successful'.");
                SwingUtilities.invokeLater(() -> {
                    if (result.toLowerCase().contains("successful") || result.toLowerCase().contains("warface")) {
                        statusLabel.setText("✅ Connected to WarfaceGPT — " + client.getModel());
                        statusLabel.setForeground(new Color(0, 128, 0));
                        int choice = JOptionPane.showConfirmDialog(this,
                            "Connection successful!\n\nSave this configuration?",
                            "Success", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) {
                            saveConfiguration();
                        }
                    } else {
                        statusLabel.setText("Connected — unexpected response");
                        statusLabel.setForeground(Color.BLUE);
                        JOptionPane.showMessageDialog(this,
                            "Connected but unexpected response:\n" + result,
                            "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("❌ Connection failed");
                    statusLabel.setForeground(Color.RED);
                    JOptionPane.showMessageDialog(this,
                        "Connection failed:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void fetchModels() {
        String apiKey = new String(apiKeyField.getPassword()).trim();
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter your API key first.",
                "API Key Required", JOptionPane.ERROR_MESSAGE);
            return;
        }

        WarfaceAPIClient client = plugin.getAPIClient();
        client.setApiKey(apiKey);

        fetchModelsButton.setEnabled(false);
        fetchModelsButton.setText("Fetching...");

        executor.submit(() -> {
            try {
                java.util.List<String> models = client.fetchAvailableModels();
                SwingUtilities.invokeLater(() -> {
                    String currentModel = (String) modelCombo.getSelectedItem();
                    modelCombo.removeAllItems();
                    for (String model : models) {
                        modelCombo.addItem(model);
                    }
                    if (currentModel != null) {
                        modelCombo.setSelectedItem(currentModel);
                    } else if (!models.isEmpty()) {
                        modelCombo.setSelectedIndex(0);
                    }
                    statusLabel.setText("Fetched " + models.size() + " models from WarfaceGPT");
                    statusLabel.setForeground(Color.BLUE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Failed to fetch models:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    fetchModelsButton.setEnabled(true);
                    fetchModelsButton.setText("Fetch");
                });
            }
        });
    }

    // === Program lifecycle ===

    public void programActivated(Program program) {
        this.currentProgram = program;
        if (decompiler != null) {
            decompiler.dispose();
            decompiler = null;
        }
    }

    public void programDeactivated(Program program) {
        this.currentProgram = null;
        if (decompiler != null) {
            decompiler.dispose();
            decompiler = null;
        }
    }

    public void showConfigurationTab() {
        tabbedPane.setSelectedIndex(1); // Configuration tab
    }

    public void dispose() {
        if (decompiler != null) {
            decompiler.dispose();
        }
        executor.shutdownNow();
    }
}