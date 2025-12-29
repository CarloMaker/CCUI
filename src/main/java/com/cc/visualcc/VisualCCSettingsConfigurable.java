package com.cc.visualcc;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Settings configuration UI for VisualCC plugin.
 * Accessible via Settings → Tools → VisualCC
 */
@Service(Service.Level.PROJECT)
public final class VisualCCSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JTextField cliPathField;
    private JButton browseButton;
    private JCheckBox includeTimestampsCheckBox;
    private JCheckBox autoStartCheckBox;
    private JComboBox<String> themeCombo;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "VisualCC";
    }

    @Override
    public @Nullable JComponent createComponent() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel titleLabel = new JLabel("<html><h2>VisualCC Settings</h2></html>");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        mainPanel.add(titleLabel, gbc);

        gbc.gridwidth = 1;
        int row = 1;

        // Description
        JLabel descLabel = new JLabel("<html><i>Claude Code CLI integration settings</i></html>");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        mainPanel.add(descLabel, gbc);
        row++;

        // Empty row
        gbc.gridy = row;
        mainPanel.add(Box.createVerticalStrut(10), gbc);
        row++;

        // CLI Path
        JLabel cliPathLabel = new JLabel("Claude CLI Path:");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        mainPanel.add(cliPathLabel, gbc);

        cliPathField = new JTextField(30);
        cliPathField.setToolTipText("Path to claude executable (leave empty to auto-detect)");
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        mainPanel.add(cliPathField, gbc);

        browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseCliPath());
        gbc.gridx = 2;
        gbc.weightx = 0;
        mainPanel.add(browseButton, gbc);
        row++;

        // CLI Path help text
        JLabel cliPathHelp = new JLabel("<html><small style='color:gray;'>Leave empty to auto-detect from PATH</small></html>");
        gbc.gridx = 1;
        gbc.gridy = row;
        mainPanel.add(cliPathHelp, gbc);
        row++;

        // Empty row
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        mainPanel.add(Box.createVerticalStrut(10), gbc);
        row++;

        // Theme
        JLabel themeLabel = new JLabel("Theme:");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        mainPanel.add(themeLabel, gbc);

        themeCombo = new JComboBox<>(new String[]{"Modern", "Dark", "Light"});
        gbc.gridx = 1;
        mainPanel.add(themeCombo, gbc);
        row++;

        // Include Timestamps
        includeTimestampsCheckBox = new JCheckBox("Include Timestamps in messages");
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        mainPanel.add(includeTimestampsCheckBox, gbc);
        row++;

        // Auto Start CLI
        autoStartCheckBox = new JCheckBox("Auto-start CLI when tool window opens");
        autoStartCheckBox.setToolTipText("Automatically start Claude CLI when opening VisualCC panel");
        gbc.gridx = 0;
        gbc.gridy = row;
        mainPanel.add(autoStartCheckBox, gbc);
        row++;

        // Info text
        JLabel infoLabel = new JLabel("<html><i style='color:gray;'>Changes apply after restarting the IDE or opening a new VisualCC panel.</i></html>");
        gbc.gridx = 0;
        gbc.gridy = row;
        mainPanel.add(infoLabel, gbc);

        // Load current settings
        reset();

        return mainPanel;
    }

    private void browseCliPath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Claude CLI Executable");

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            fileChooser.setSelectedFile(new File("C:\\Program Files"));
        }

        int result = fileChooser.showOpenDialog(mainPanel);
        if (result == JFileChooser.APPROVE_OPTION) {
            cliPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    @Override
    public boolean isModified() {
        VisualCCConfig config = VisualCCConfig.getInstance();
        return !cliPathField.getText().equals(config.getCliPath()) ||
                !themeCombo.getSelectedItem().equals(config.getTheme()) ||
                includeTimestampsCheckBox.isSelected() != config.includeTimestamps() ||
                autoStartCheckBox.isSelected() != config.isAutoStart();
    }

    @Override
    public void apply() throws ConfigurationException {
        VisualCCConfig config = VisualCCConfig.getInstance();
        config.setCliPath(cliPathField.getText());
        config.setTheme(((String) themeCombo.getSelectedItem()).toUpperCase());
        config.setIncludeTimestamps(includeTimestampsCheckBox.isSelected());
        config.setAutoStart(autoStartCheckBox.isSelected());
    }

    @Override
    public void reset() {
        VisualCCConfig config = VisualCCConfig.getInstance();
        cliPathField.setText(config.getCliPath());
        themeCombo.setSelectedItem(config.getTheme());
        includeTimestampsCheckBox.setSelected(config.includeTimestamps());
        autoStartCheckBox.setSelected(config.isAutoStart());
    }
}
