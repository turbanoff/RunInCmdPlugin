package org.turbanov.execution.cmd;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;

import java.awt.BorderLayout;
import java.util.Objects;

/**
 * @author Andrey Turbanov
 */
public class InCmdConfigurable implements Configurable {

    private final Project myProject;
    private OptionsPatchConfiguration myState;
    private JTextArea toAddVmOptions;
    private JTextArea toRemoveVmOptions;
    private JTextArea toAddProgramOptions;
    private JTextArea toRemoveProgramOptions;
    private JCheckBox runInTerminal;
    private SpinnerNumberModel freePortSpinnerModel;
    private JCheckBox findFreePort;

    public InCmdConfigurable(Project project) {
        myProject = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "RunInCmd plugin";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        myState = ServiceManager.getService(myProject, OptionsPatchConfiguration.class);

        toAddVmOptions = new JTextArea(myState.toAddVmOptions);
        toRemoveVmOptions = new JTextArea(myState.toRemoveVmOptions);
        toAddProgramOptions = new JTextArea(myState.toAddProgramOptions);
        toRemoveProgramOptions = new JTextArea(myState.toRemoveProgramOptions);

        Box mainBox = Box.createVerticalBox();

        mainBox.add(LabeledComponent.create(toAddVmOptions, "To add VM options"));
        mainBox.add(LabeledComponent.create(toRemoveVmOptions, "To remove VM options"));
        mainBox.add(LabeledComponent.create(toAddProgramOptions, "To add program options"));
        mainBox.add(LabeledComponent.create(toRemoveProgramOptions, "To remove program options"));

        runInTerminal = new JCheckBox("Run inside IDEA Terminal", myState.isRunInsideTerminal);

        Box bottomBox = Box.createHorizontalBox();
        freePortSpinnerModel = new SpinnerNumberModel(60000, 1024, 65535, 1);
        JSpinner freePortSpinner = new JSpinner(freePortSpinnerModel);
        findFreePort = new JCheckBox("Try to find free port");
        findFreePort.setToolTipText("At start plugin will try to find free port\n" +
                "This port will be available as $freePort macros in program/VM options");
        bottomBox.add(findFreePort);
        bottomBox.add(LabeledComponent.create(freePortSpinner, "Start value to check"));

        JPanel result = new JPanel(new BorderLayout());
        result.add(runInTerminal, BorderLayout.PAGE_START);
        result.add(mainBox, BorderLayout.CENTER);
        result.add(bottomBox, BorderLayout.PAGE_END);
        return result;
    }

    @Override
    public boolean isModified() {
        boolean simpleModified = !toAddVmOptions.getText().equals(myState.toAddVmOptions)
                || !toRemoveVmOptions.getText().equals(myState.toRemoveVmOptions)
                || !toAddProgramOptions.getText().equals(myState.toAddProgramOptions)
                || !toRemoveProgramOptions.getText().equals(myState.toRemoveProgramOptions)
                || runInTerminal.isSelected() != myState.isRunInsideTerminal;
        if (simpleModified) return true;
        Integer currentValue = findFreePort.isSelected() ? freePortSpinnerModel.getNumber().intValue() : null;
        return !Objects.equals(currentValue, myState.startPort);
    }

    @Override
    public void apply() throws ConfigurationException {
        OptionsPatchConfiguration state = new OptionsPatchConfiguration();
        state.toAddVmOptions = toAddVmOptions.getText().trim();
        state.toRemoveVmOptions = toRemoveVmOptions.getText().trim();
        state.toAddProgramOptions = toAddProgramOptions.getText().trim();
        state.toRemoveProgramOptions = toRemoveProgramOptions.getText().trim();
        state.isRunInsideTerminal = runInTerminal.isSelected();
        state.startPort = findFreePort.isSelected() ? freePortSpinnerModel.getNumber().intValue() : null;
        ServiceManager.getService(myProject, OptionsPatchConfiguration.class).loadState(state);
    }

    @Override
    public void reset() {
        toAddVmOptions.setText(myState.toAddVmOptions);
        toRemoveVmOptions.setText(myState.toRemoveVmOptions);
        toAddProgramOptions.setText(myState.toAddProgramOptions);
        toRemoveProgramOptions.setText(myState.toRemoveProgramOptions);
        runInTerminal.setSelected(myState.isRunInsideTerminal);
        if (myState.startPort == null) {
            findFreePort.setSelected(false);
        } else {
            findFreePort.setSelected(true);
            freePortSpinnerModel.setValue(myState.startPort);
        }
    }

    @Override
    public void disposeUIResources() {
    }
}
