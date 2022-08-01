package org.turbanov.execution.cmd;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author Andrey Turbanov
 */
public class InCmdRunner extends GenericProgramRunner<RunnerSettings> {

    private static final Logger LOG = Logger.getInstance(InCmdRunner.class);
    private Boolean terminalPluginEnabled;

    @NotNull
    @Override
    public String getRunnerId() {
        return "InCmdRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return executorId.equals(InCmdExecutor.executorId) &&
                (profile instanceof ApplicationConfiguration || profile instanceof JavaTestConfigurationBase);
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
        JavaCommandLineState state = (JavaCommandLineState) runProfileState;
        JavaParameters javaParameters = state.getJavaParameters();
        javaParameters.setUseDynamicClasspath(false);
        LOG.info("Old command line. JDK path: " + javaParameters.getJdkPath() +
                " VM options: " + javaParameters.getVMParametersList() +
                " Parameters: " + javaParameters.getProgramParametersList()
        );

        OptionsPatchConfiguration options = ServiceManager.getService(environment.getProject(), OptionsPatchConfiguration.class);
        patchParameterList(javaParameters.getVMParametersList(), options.toAddVmOptions, options.toRemoveVmOptions, options.startPort);
        patchParameterList(javaParameters.getProgramParametersList(), options.toAddProgramOptions, options.toRemoveProgramOptions, options.startPort);

        String workingDirectory = state.getJavaParameters().getWorkingDirectory();

        PathsList classPath = javaParameters.getClassPath();
        String classPathPathsString = classPath.getPathsString();
        clear(classPath);

        GeneralCommandLine generalCommandLine = javaParameters.toCommandLine();
        String original = generalCommandLine.getCommandLineString();
        String newCommandLine;
        if (SystemInfo.isWindows) {
            newCommandLine = original
                    .replace("^", "^^") //replace ^ first
                    .replace("&", "^&")
                    .replace("<", "^<")
                    .replace(">", "^>")
                    .replace("(", "^(")
                    .replace(")", "^)")
                    .replace("@", "^@")
                    .replace("|", "^|");
        } else {
            newCommandLine = original;
        }

        if (options.isRunInsideTerminal && isTerminalPluginEnabled()) {
            try {
                String[] command = createCommand(false, newCommandLine, null, null);
                TerminalRunner.runInIdeaTerminal(environment.getProject(), command, classPathPathsString, workingDirectory);
            } catch (Throwable e) {
                showWarning("Unable to run in internal IDEA Terminal due to '" + e.getMessage() + "'<br>Run in external cmd instead", environment);
                runInExternalCmd(classPathPathsString, generalCommandLine, workingDirectory, newCommandLine);
            }
        } else {
            if (!isTerminalPluginEnabled()) {
                showWarning("Terminal plugin disabled<br>Run in external cmd instead", environment);
            }
            runInExternalCmd(classPathPathsString, generalCommandLine, workingDirectory, newCommandLine);
        }
        return null;
    }

    private static void runInExternalCmd(String classPathPathsString, GeneralCommandLine generalCommandLine,
                                         String workingDirectory, String commandLine) throws ProcessNotCreatedException {
        Process process;
        try {
            String[] command = createCommand(true, commandLine, workingDirectory, classPathPathsString);
            ProcessBuilder builder = new ProcessBuilder().command(command);
            builder.directory(new File(workingDirectory));
            builder.environment().put("CLASSPATH", classPathPathsString);
            LOG.info(builder.command().toString());
            process = builder.start();
        } catch (IOException e) {
            LOG.info(e);
            throw new ProcessNotCreatedException(e.getMessage(), e, generalCommandLine);
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CapturingProcessHandler processHandler = new CapturingProcessHandler(process, Charset.forName("cp866"), commandLine);
            ProcessOutput output = processHandler.runProcess();
            LOG.debug("Process output: " + output.getStdout());
            String processErrors = output.getStderr();
            if (!processErrors.isEmpty()) {
                LOG.info("Process error: " + processErrors);
            }
        });
    }

    private static String[] createCommand(boolean external, String commandLine, String workingDirectory,
                                          String classPathPathsString) throws IOException {
        if (SystemInfo.isWindows) {
            return external ?
                    new String[]{"cmd.exe", "/C", "\"start cmd.exe /K \"" + commandLine + "\"\""} :
                    new String[]{"cmd.exe", "/K", commandLine};
        } if (SystemInfo.isMac) {
            String shell = System.getenv("SHELL");
            LOG.info("Shell used " + shell);
            if (external) {
                Path path = Files.createTempFile("launch", ".sh", PosixFilePermissions.asFileAttribute(
                        new HashSet<>(Arrays.asList(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE))));
                String launchScript = String.format("cd %s\nexport CLASSPATH=%s\n%s", workingDirectory, classPathPathsString, commandLine);
                Files.write(path, launchScript.getBytes(), StandardOpenOption.APPEND);
                return new String[]{"open", "-a", "Terminal", path.toString()};
            }
            return new String[]{shell, "-c", commandLine};
        } else {
            throw new UnsupportedOperationException("");
        }
    }

    private void showWarning(@NotNull String htmlContent, @NotNull ExecutionEnvironment environment) {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(environment.getProject());
        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(htmlContent, MessageType.WARNING, null)
                .setHideOnClickOutside(true)
                .createBalloon()
                .show(RelativePoint.getCenterOf(statusBar.getComponent()), Balloon.Position.above);
    }

    private static void clear(PathsList classPath) {
        List<String> pathList = classPath.getPathList();
        for (String path : pathList) {
            classPath.remove(path);
        }
    }

    private static void patchParameterList(ParametersList parametersList, String toAdd, String toRemove, Integer startPort) {
        if (!toAdd.isEmpty()) {
            if (startPort != null && toAdd.contains("$freePort")) {
                Integer freePort = findFreePort(startPort);
                if (freePort != null) {
                    toAdd = toAdd.replace("$freePort", freePort.toString());
                }
            }
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

    //Adapted from org.jetbrains.jps.incremental.java.JavaBuilder.findFreePort()
    private static Integer findFreePort(int startFrom) {
        for (int i = 0; i < 100; i++) {
            try {
                int tryPort = startFrom + i;
                if (tryPort < 0) {
                    return null;
                }
                try (ServerSocket ignored = new ServerSocket(tryPort)) {
                    // calling close() immediately after opening socket may result that socket is not closed
                    Thread.sleep(1);
                }
                return tryPort;
            } catch (IOException | InterruptedException ignored) {
            }
        }
        return null;
    }
}
