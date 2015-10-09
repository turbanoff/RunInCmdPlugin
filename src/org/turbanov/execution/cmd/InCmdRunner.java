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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

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
        ParametersList vmParametersList = javaParameters.getVMParametersList();

        //patchVmParameters(vmParametersList);


        String workingDirectory = state.getJavaParameters().getWorkingDirectory();

        GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters, environment.getProject(), true);
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
        LOG.debug("Process output\n" + output.getStdout());
        LOG.debug("Process error\n" + output.getStderr());

        return null;
    }

    private void patchVmParameters(ParametersList vmParametersList) {
        String toRemove = "-Djline.terminal=jline.UnsupportedTerminal";
        String toAdd = "-javaagent:libs/spring-instrument-4.1.7.RELEASE.jar -Dspring.profiles.active=\"server-default,testprofile\"";

        List<String> parameters = vmParametersList.getParameters();

        String[] toRemoveParams = ParametersList.parse(toRemove);
        String[] toAddParams = ParametersList.parse(toAdd);
        for (String toRemoveParam : toRemoveParams) {
            int i = parameters.indexOf(toRemoveParam);
            if (i != -1) {
                parameters.set(i, "");
            }
        }

        for (String toAddParam : toAddParams) {
            if (!vmParametersList.hasParameter(toAddParam)) {
                vmParametersList.add(toAddParam);
            }
        }
    }
}
