package de.example.bluejlike.workbench

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ActivateObjectWorkbenchAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Object Workbench") ?: return
        toolWindow.activate(null)
        toolWindow.show(null)
    }
}
