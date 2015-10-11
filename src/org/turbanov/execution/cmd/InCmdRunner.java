package org.turbanov.execution.cmd;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfiguration.JavaApplicationCommandLineState;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;

/**
 * @author Andrey Turbanov
 */
public class InCmdRunner<Settings extends RunnerSettings> extends GenericProgramRunner<Settings> {

    private static final Logger LOG = Logger.getInstance(InCmdRunner.class);

    @NotNull
    @Override
    public String getRunnerId() {
        return "InCmdRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return executorId.equals(InCmdExecutor.executorId) && profile instanceof ApplicationConfiguration;
    }

    @Nullable
    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState runProfileState, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        FileDocumentManager.getInstance().saveAllDocuments();
        JavaApplicationCommandLineState state = (JavaApplicationCommandLineState) runProfileState;
        JavaParameters javaParameters = state.getJavaParameters();

        GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters, environment.getProject(), true);
        LOG.info("Old command line: " + commandLine);

        OptionsPatchConfiguration options = ServiceManager.getService(environment.getProject(), OptionsPatchConfiguration.class);
        patchParameterList(javaParameters.getVMParametersList(), options.toAddVmOptions, options.toRemoveVmOptions);
        patchParameterList(javaParameters.getProgramParametersList(), options.toAddProgramOptions, options.toRemoveProgramOptions);

        String workingDirectory = state.getJavaParameters().getWorkingDirectory();

        commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters, environment.getProject(), true);
        Process start;
        try {
            ProcessBuilder builder = new ProcessBuilder().command("cmd.exe", "/C", "\"cd /D \"" + workingDirectory + "\" && start cmd.exe /K \"" + commandLine.getCommandLineString() + "\"\"");
            LOG.info("" + builder.command());
            start = builder.start();
        } catch (IOException e) {
            LOG.info(e);
            throw new ProcessNotCreatedException(e.getMessage(), e, commandLine);
        }

        CapturingProcessHandler processHandler = new CapturingProcessHandler(start, Charset.forName("cp866"));
        ProcessOutput output = processHandler.runProcess();
        LOG.debug("Process output: " + output.getStdout());
        LOG.debug("Process error: " + output.getStderr());

        return null;
    }

    private void patchParameterList(ParametersList parametersList, String toAdd, String toRemove) {
        if (!toAdd.isEmpty()) {
            String[] toAddParams = ParametersList.parse(toAdd);
            for (String toAddParam : toAddParams) {
                if (!parametersList.hasParameter(toAddParam)) {
                    parametersList.add(toAddParam);
                }
            }
        }

        if (!toRemove.isEmpty()) {
            HashSet<String> toRemoveParams = ContainerUtil.newHashSet(ParametersList.parse(toRemove));
            String[] oldParameters = parametersList.getArray();
            parametersList.clearAll();
            for (String oldParameter : oldParameters) {
                if (!toRemoveParams.contains(oldParameter)) {
                    parametersList.add(oldParameter);
                }
            }
        }
    }
}
