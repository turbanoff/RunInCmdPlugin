package org.turbanov.execution.cmd;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalView;
import com.intellij.openapi.project.Project;
import com.pty4j.PtyProcess;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @author Andrey Turbanov
 */
public class TerminalRunner {
    public static void runInIdeaTerminal(@NotNull Project project, @NotNull String[] command, @NotNull String classPath, @NotNull String workingDirectory) {
        TerminalView terminalView = TerminalView.getInstance(project);
        LocalTerminalDirectRunner runner = new LocalTerminalDirectRunner(project) {
            @Override
            protected PtyProcess createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
                Map<String, String> envs = new HashMap<>(System.getenv());
                envs.put("CLASSPATH", classPath);
                try {
                    return PtyProcess.exec(command, envs, workingDirectory);
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }
        };
        terminalView.createNewSession(runner);
    }
}
