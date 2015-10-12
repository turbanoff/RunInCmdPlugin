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
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;

/**
 * @author Andrey Turbanov
 */
public class InCmdRunner<Settings extends RunnerSettings> extends GenericProgramRunner<Settings> {

    private static final Logger LOG = Logger.getInstance(InCmdRunner.class);
    private Boolean terminalPluginEnabled;

    @NotNull
    @Override
    public String getRunnerId() {
        return "InCmdRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return executorId.equals(InCmdExecutor.executorId) && profile instanceof ApplicationConfiguration;
    }

    private boolean isTerminalPluginEnabled() {
        if (terminalPluginEnabled != null) return terminalPluginEnabled;
        IdeaPluginDescriptor terminalPlugin = PluginManager.getPlugin(PluginId.getId("org.jetbrains.plugins.terminal"));
        this.terminalPluginEnabled = terminalPlugin != null && terminalPlugin.isEnabled();
        return terminalPluginEnabled;
    }

    @Nullable
    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState runProfileState, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        FileDocumentManager.getInstance().saveAllDocuments();
        JavaApplicationCommandLineState state = (JavaApplicationCommandLineState) runProfileState;
        JavaParameters javaParameters = state.getJavaParameters();

        GeneralCommandLine oldCommandLine = CommandLineBuilder.createFromJavaParameters(javaParameters, environment.getProject(), false);
        LOG.info("Old command line: " + oldCommandLine);

        OptionsPatchConfiguration options = ServiceManager.getService(environment.getProject(), OptionsPatchConfiguration.class);
        patchParameterList(javaParameters.getVMParametersList(), options.toAddVmOptions, options.toRemoveVmOptions);
        patchParameterList(javaParameters.getProgramParametersList(), options.toAddProgramOptions, options.toRemoveProgramOptions);

        String workingDirectory = state.getJavaParameters().getWorkingDirectory();

        PathsList classPath = javaParameters.getClassPath();
        String classPathPathsString = classPath.getPathsString();
        clear(classPath);

        GeneralCommandLine generalCommandLine = CommandLineBuilder.createFromJavaParameters(javaParameters, environment.getProject(), false);
        String newCommandLine = generalCommandLine.getCommandLineString().replace("&", "^&");

        if (options.isRunInsideTerminal && isTerminalPluginEnabled()) {
            try {
                String[] command = {"cmd.exe /K \"" + newCommandLine + "\""};
                TerminalRunner.runInIdeaTerminal(environment.getProject(), command, classPathPathsString, workingDirectory);
            } catch (Throwable e) {
                StatusBar statusBar = WindowManager.getInstance().getStatusBar(environment.getProject());
                JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("Unable to run in internal IDEA Terminal due to '" + e.getMessage() + "'<br>Run in external cmd instead", MessageType.WARNING, null)
                        .setHideOnClickOutside(true)
                        .createBalloon()
                        .show(RelativePoint.getNorthEastOf(statusBar.getComponent()), Balloon.Position.atRight);
                runInExternalCmd(classPathPathsString, generalCommandLine, workingDirectory, newCommandLine);
            }
        } else {
            if (!isTerminalPluginEnabled()) {
                StatusBar statusBar = WindowManager.getInstance().getStatusBar(environment.getProject());
                JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("Terminal plugin disabled<br>Run in external cmd instead", MessageType.WARNING, null)
                        .setHideOnClickOutside(true)
                        .createBalloon()
                        .show(RelativePoint.getNorthEastOf(statusBar.getComponent()), Balloon.Position.atRight);
            }
            runInExternalCmd(classPathPathsString, generalCommandLine, workingDirectory, newCommandLine);
        }
        return null;
    }

    private void runInExternalCmd(String classPathPathsString, GeneralCommandLine generalCommandLine, String workingDirectory, String commandLine) throws ProcessNotCreatedException {
        String[] command = {"cmd.exe", "/C", "\"cd /D \"" + workingDirectory + "\" && start cmd.exe /K \"" + commandLine + "\"\""};
        Process start;
        try {
            ProcessBuilder builder = new ProcessBuilder().command(command);
            builder.environment().put("CLASSPATH", classPathPathsString);
            LOG.info("" + builder.command());
            start = builder.start();
        } catch (IOException e) {
            LOG.info(e);
            throw new ProcessNotCreatedException(e.getMessage(), e, generalCommandLine);
        }

        CapturingProcessHandler processHandler = new CapturingProcessHandler(start, Charset.forName("cp866"));
        ProcessOutput output = processHandler.runProcess();
        LOG.debug("Process output: " + output.getStdout());
        LOG.info("Process error: " + output.getStderr());
    }

    private void clear(PathsList classPath) {
        List<String> pathList = classPath.getPathList();
        for (String path : pathList) {
            classPath.remove(path);
        }
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
