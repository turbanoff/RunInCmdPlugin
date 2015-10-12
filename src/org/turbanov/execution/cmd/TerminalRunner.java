package org.turbanov.execution.cmd;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalView;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @author Andrey Turbanov
 */
public class TerminalRunner {
    public static void runInIdeaTerminal(Project project, final String[] command, final String classPath, final String workingDirectory) {
        TerminalView terminalView = TerminalView.getInstance(project);
        terminalView.createNewSession(project, new LocalTerminalDirectRunner(project) {
            @Override
            protected PtyProcess createProcess(@Nullable String directory) throws ExecutionException {
                Map<String, String> envs = new HashMap<String, String>(System.getenv());
                envs.put("CLASSPATH", classPath);
                try {
                    return PtyProcess.exec(command, envs, workingDirectory);
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }
        });
    }
}
