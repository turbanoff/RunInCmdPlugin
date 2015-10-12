package org.turbanov.execution.cmd;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;

/**
 * @author Andrey Turbanov
 */
@State(name = "RunInCmdPluginSettings", storages = {@Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/RunInCmd.xml")})
public class OptionsPatchConfiguration implements PersistentStateComponent<OptionsPatchConfiguration> {

    public String toAddVmOptions = "";
    public String toRemoveVmOptions = "";
    public String toAddProgramOptions = "";
    public String toRemoveProgramOptions = "";
    public boolean isRunInsideTerminal = false;

    @NotNull
    @Override
    public OptionsPatchConfiguration getState() {
        return this;
    }

    @Override
    public void loadState(OptionsPatchConfiguration state) {
        toAddVmOptions = state.toAddVmOptions;
        toRemoveVmOptions = state.toRemoveVmOptions;
        toAddProgramOptions = state.toAddProgramOptions;
        toRemoveProgramOptions = state.toRemoveProgramOptions;
        isRunInsideTerminal = state.isRunInsideTerminal;
    }
}
