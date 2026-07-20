package com.warfacegpt.replugin;

import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;
import com.warfacegpt.replugin.service.WarfaceAPIClient;
import com.warfacegpt.replugin.config.ConfigurationManager;
import com.warfacegpt.replugin.ui.WarfaceProvider;

import javax.swing.*;

/**
 * WarfaceGPT RE Plugin — AI-powered reverse engineering for Ghidra.
 * Connects to the WarfaceGPT gateway for vulnerability detection,
 * function analysis, and decompiler enhancement.
 */
@PluginInfo(
    status = PluginStatus.RELEASED,
    packageName = "WarfaceGPT",
    category = PluginCategoryNames.ANALYSIS,
    shortDescription = "WarfaceGPT Reverse Engineering",
    description = "AI-powered reverse engineering plugin — vulnerability detection, " +
                  "function analysis, and decompiler enhancement via WarfaceGPT. " +
                  "Connects to gateway.warfacegpt.army for LLM-powered analysis."
)
public class WarfaceREPlugin extends ProgramPlugin {

    private WarfaceProvider provider;
    private WarfaceAPIClient apiClient;
    private ConfigurationManager configManager;
    private boolean configurationChecked = false;

    public WarfaceREPlugin(PluginTool tool) {
        super(tool);
        apiClient = new WarfaceAPIClient();
        configManager = new ConfigurationManager();
    }

    @Override
    protected void init() {
        super.init();
        provider = new WarfaceProvider(this, getName());

        // Auto-configure with saved settings or prompt for API key
        checkAndShowConfigurationIfNeeded();
    }

    private void checkAndShowConfigurationIfNeeded() {
        if (!configurationChecked) {
            configurationChecked = true;

            if (!configManager.configurationFileExists() || !configManager.isConfigured()) {
                SwingUtilities.invokeLater(() -> {
                    int choice = JOptionPane.showConfirmDialog(
                        getTool().getActiveWindow(),
                        "WarfaceGPT RE Plugin is not configured yet.\n\n" +
                        "You need a WarfaceGPT API key to use this plugin.\n" +
                        "Get one at https://warfacegpt.army\n\n" +
                        "Would you like to configure it now?",
                        "WarfaceGPT Configuration Required",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    );

                    if (choice == JOptionPane.YES_OPTION && provider != null) {
                        provider.setVisible(true);
                        provider.showConfigurationTab();
                    }
                });
            } else {
                // Load saved configuration
                apiClient.setApiKey(configManager.getApiKey());
                apiClient.setModel(configManager.getModel());
                apiClient.setMaxTokens(configManager.getMaxTokens());
                apiClient.setTemperature(configManager.getTemperature());
                apiClient.setTimeoutSeconds(configManager.getTimeoutSeconds());

                Msg.info(this, "WarfaceGPT RE Plugin loaded. Model: " +
                    configManager.getModel());
            }
        }
    }

    @Override
    protected void programActivated(Program program) {
        if (provider != null) {
            provider.programActivated(program);
        }
        checkAndShowConfigurationIfNeeded();
    }

    @Override
    protected void programDeactivated(Program program) {
        if (provider != null) {
            provider.programDeactivated(program);
        }
    }

    @Override
    protected void dispose() {
        if (provider != null) {
            provider.dispose();
        }
        super.dispose();
    }

    public WarfaceAPIClient getAPIClient() {
        return apiClient;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}