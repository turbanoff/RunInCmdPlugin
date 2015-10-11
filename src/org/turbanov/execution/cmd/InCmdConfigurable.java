package org.turbanov.execution.cmd;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Andrey Turbanov
 */
public class InCmdConfigurable implements Configurable  {

    private final Project myProject;
    private OptionsPatchConfiguration myState;
    private JTextArea toAddVmOptions;
    private JTextArea toRemoveVmOptions;
    private JTextArea toAddProgramOptions;
    private JTextArea toRemoveProgramOptions;

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

        GridLayout layout = new GridLayout(4, 1, 6, 6);
        JPanel result = new JPanel(layout);

        toAddVmOptions = new JTextArea(myState.toAddVmOptions);
        toRemoveVmOptions = new JTextArea(myState.toRemoveVmOptions);
        toAddProgramOptions = new JTextArea(myState.toAddProgramOptions);
        toRemoveProgramOptions = new JTextArea(myState.toRemoveProgramOptions);

        result.add(LabeledComponent.create(toAddVmOptions, "To add VM options"));
        result.add(LabeledComponent.create(toRemoveVmOptions, "To remove VM options"));
        result.add(LabeledComponent.create(toAddProgramOptions, "To add program options"));
        result.add(LabeledComponent.create(toRemoveProgramOptions, "To remove program options"));

        return result;
    }

    @Override
    public boolean isModified() {
        return !toAddVmOptions.getText().equals(myState.toAddVmOptions)
            || !toRemoveVmOptions.getText().equals(myState.toRemoveVmOptions)
            || !toAddProgramOptions.getText().equals(myState.toAddProgramOptions)
            || !toRemoveProgramOptions.getText().equals(myState.toRemoveProgramOptions);
    }

    @Override
    public void apply() throws ConfigurationException {
        OptionsPatchConfiguration state = new OptionsPatchConfiguration();
        state.toAddVmOptions = toAddVmOptions.getText().trim();
        state.toRemoveVmOptions = toRemoveVmOptions.getText().trim();
        state.toAddProgramOptions = toAddProgramOptions.getText().trim();
        state.toRemoveProgramOptions = toRemoveProgramOptions.getText().trim();
        ServiceManager.getService(myProject, OptionsPatchConfiguration.class).loadState(state);
    }

    @Override
    public void reset() {
        toAddVmOptions.setText(myState.toAddVmOptions);
        toRemoveVmOptions.setText(myState.toRemoveVmOptions);
        toAddProgramOptions.setText(myState.toAddProgramOptions);
        toRemoveProgramOptions.setText(myState.toRemoveProgramOptions);
    }

    @Override
    public void disposeUIResources() {
    }
}
