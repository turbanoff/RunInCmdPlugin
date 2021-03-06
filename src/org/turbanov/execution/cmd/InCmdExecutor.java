package org.turbanov.execution.cmd;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import com.intellij.execution.Executor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;

/**
 * @author Andrey Turbanov
 */
public class InCmdExecutor extends Executor {
    public static final Icon cmdExecutorIcon = IconLoader.getIcon("/cmd.png");
    public static final String executorId = "RunInCmdExecutor";

    @NotNull
    @Override
    public String getToolWindowId() {
        return ToolWindowId.DEBUG;
    }

    @NotNull
    @Override
    public Icon getToolWindowIcon() {
        return cmdExecutorIcon;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return cmdExecutorIcon;
    }

    @Override
    public Icon getDisabledIcon() {
        return cmdExecutorIcon;
    }

    @Override
    public String getDescription() {
        return "Run program in cmd.exe instead of internal console";
    }

    @NotNull
    @Override
    public String getActionName() {
        return "Run in cmd";
    }

    @NotNull
    @Override
    public String getId() {
        return executorId;
    }

    @NotNull
    @Override
    public String getStartActionText() {
        return "Run in cmd";
    }

    @Override
    public String getContextActionId() {
        return "RunInCmd";
    }

    @Override
    public String getHelpId() {
        return null;
    }
}
