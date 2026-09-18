package de.example.bluejlike.workbench

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.JBColor
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

class ObjectWorkbenchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ObjectWorkbenchPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}

private class ObjectWorkbenchPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val pageBackground = JBColor(Color(0xEAF2FC), Color(0x2B3646))
    private val benchBackground = JBColor(Color(0xDCEAFE), Color(0x31455E))
    private val cardBackground = JBColor(Color(0xFFD7D7), Color(0x6A2F2F))
    private val cardBorder = JBColor(Color(0xB23A3A), Color(0xD27A7A))
    private val accent = JBColor(Color(0x1C5EA8), Color(0x8AB8FF))
    private val accentSoft = JBColor(Color(0x2D76C9), Color(0x5E8FD0))
    private val badgeBackground = JBColor(Color(0xD7E8FF), Color(0x445D7D))
    private val badgeText = JBColor(Color(0x184E86), Color(0xDDEBFF))
    private val resultPanelBackground = JBColor(Color(0xFFF7D7), Color(0x4C4422))
    private val resultPanelBorder = JBColor(Color(0xC7A24A), Color(0xE4C16A))

    private val classField = JBTextField()
    private val constructorCombo = JComboBox<ConstructorWrapper>()
    private val instancesCanvas = JPanel(FlowLayout(FlowLayout.LEFT, 12, 12))
    private val logArea = JTextArea(8, 40)

    private val instances = mutableListOf<WorkbenchInstance>()
    private var selectedInstance: WorkbenchInstance? = null
    private var helper: RemoteJvmHelper? = null

    init {
        background = pageBackground
        border = JBUI.Borders.empty(8)
        logArea.isEditable = false
        logArea.lineWrap = true
        logArea.wrapStyleWord = true
        logArea.background = JBColor(Color(0xF4F8FF), Color(0x2A3443))
        logArea.foreground = JBColor.foreground()
        logArea.font = Font("DejaVu Sans Mono", Font.PLAIN, 12)

        classField.font = Font("SansSerif", Font.PLAIN, 13)
        constructorCombo.font = Font("SansSerif", Font.PLAIN, 13)
        val createButton = JButton("Instanz erstellen")
        val browseButton = JButton("Klasse auswaehlen")

        styleActionButton(createButton)
        styleActionButton(browseButton)

        browseButton.addActionListener { chooseClassFromProject() }
        classField.addActionListener { refreshConstructors() }
        createButton.addActionListener { createInstance() }

        val top = FormBuilder.createFormBuilder()
            .addComponent(
                JLabel("Object Workbench").apply {
                    foreground = accent
                    font = Font("SansSerif", Font.BOLD, 16)
                    border = JBUI.Borders.emptyBottom(4)
                }
            )
            .addLabeledComponent("Vollqualifizierte Klasse", classField)
            .addComponent(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    add(browseButton)
                    add(createButton)
                }
            )
            .addLabeledComponent("Konstruktor", constructorCombo)
            .panel
            .apply {
                isOpaque = false
            }

        instancesCanvas.border = BorderFactory.createTitledBorder("Objekt-Workbench")
        instancesCanvas.background = benchBackground
        (instancesCanvas.border as? TitledBorder)?.titleColor = accent
        (instancesCanvas.border as? TitledBorder)?.titleFont = Font("SansSerif", Font.BOLD, 12)

        val center = JBScrollPane(instancesCanvas)
        center.border = JBUI.Borders.emptyTop(8)
        center.background = benchBackground

        val bottom = JBScrollPane(logArea)
        bottom.border = BorderFactory.createTitledBorder("Ausgabe")
        (bottom.border as? TitledBorder)?.titleColor = accentSoft
        (bottom.border as? TitledBorder)?.titleFont = Font("SansSerif", Font.BOLD, 12)

        add(top, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        add(bottom, BorderLayout.SOUTH)
    }

    private fun chooseClassFromProject() {
        val chooser = com.intellij.ide.util.TreeClassChooserFactory.getInstance(project)
            .createProjectScopeChooser("Klasse waehlen")
        chooser.showDialog()
        val psiClass = chooser.selected
        if (psiClass != null) {
            classField.text = psiClass.qualifiedName ?: ""
            refreshConstructors()
        }
    }

    private fun refreshConstructors() {
        val fqcn = classField.text.trim()
        if (fqcn.isEmpty()) {
            Messages.showWarningDialog(project, "Bitte eine Klasse eingeben.", "Object Workbench")
            return
        }

        runCatching {
            val wrappers = helper().listConstructors(fqcn)
            val namesBySignature = constructorNamesFromSource(fqcn)
            val enriched = wrappers.map { wrapper ->
                val signature = wrapper.parameterTypes.joinToString(";")
                val sourceNames = namesBySignature[signature]
                if (sourceNames.isNullOrEmpty()) {
                    wrapper
                } else {
                    wrapper.copy(parameterNames = sourceNames)
                }
            }
            constructorCombo.model = DefaultComboBoxModel(enriched.toTypedArray())
            appendLog("${enriched.size} Konstruktor(en) geladen fuer $fqcn")
        }.onFailure {
            appendThrowable("Konstruktoren konnten nicht geladen werden", it)
        }
    }

    private fun constructorNamesFromSource(fqcn: String): Map<String, List<String>> {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(fqcn, GlobalSearchScope.projectScope(project))
            ?: return emptyMap()

        return psiClass.constructors.associate { ctor ->
            val typeSignature = ctor.parameterList.parameters
                .joinToString(";") { it.type.canonicalText }
            val names = ctor.parameterList.parameters.map { it.name ?: "" }
            typeSignature to names
        }
    }

    private fun createInstance() {
        val wrapper = constructorCombo.selectedItem as? ConstructorWrapper
        if (wrapper == null) {
            Messages.showWarningDialog(project, "Bitte zuerst einen Konstruktor laden und waehlen.", "Object Workbench")
            return
        }

        val dialog = ParameterDialog(
            project,
            wrapper.parameterTypes,
            wrapper.parameterNames,
            instances,
            helper()
        )
        if (!dialog.showAndGet()) {
            return
        }

        runCatching {
            val instance = helper().createInstance(
                className = wrapper.declaringClassName,
                constructorIndex = wrapper.index,
                argumentTokens = dialog.argumentTokens()
            )
            instances += instance
            selectedInstance = instance
            refreshInstancesCanvas()
            appendLog("Instanz #${instance.id} erstellt: ${instance.className}")
        }.onFailure {
            appendThrowable("Instanz konnte nicht erstellt werden", it)
        }
    }

    private fun refreshInstancesCanvas() {
        instancesCanvas.removeAll()
        instances.forEach { instance ->
            instancesCanvas.add(createInstanceCard(instance))
        }
        instancesCanvas.revalidate()
        instancesCanvas.repaint()
    }

    private fun createInstanceCard(instance: WorkbenchInstance): JComponent {
        return RoundedCardPanel(cardBackground, cardBorder).apply {
            border = JBUI.Borders.empty(8)
            preferredSize = JBUI.size(320, 132)

            val packageName = instance.className.substringBeforeLast('.', "<default>")
            val badges = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(createBadge("#${instance.id}"))
                add(createBadge(instance.classSimpleName))
            }

            val title = JLabel("Objekt #${instance.id}").apply {
                foreground = accent
                font = Font("SansSerif", Font.BOLD, 13)
            }
            val details = JLabel("${instance.classSimpleName} (${instance.className})").apply {
                font = Font("SansSerif", Font.PLAIN, 12)
            }
            val packageLabel = JLabel("Paket: $packageName").apply {
                foreground = JBColor.GRAY
                font = Font("SansSerif", Font.PLAIN, 11)
            }
            val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(JButton("Auswaehlen").apply {
                    styleActionButton(this)
                    addActionListener {
                        selectInstance(instance)
                    }
                })
            }

            val north = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(title, BorderLayout.NORTH)
                add(badges, BorderLayout.SOUTH)
            }
            val centerInfo = JPanel(GridLayout(2, 1, 0, 2)).apply {
                isOpaque = false
                add(details)
                add(packageLabel)
            }

            installCardContextMenu(this, instance)
            installCardContextMenu(title, instance)
            installCardContextMenu(details, instance)
            installCardContextMenu(packageLabel, instance)
            installCardContextMenu(badges, instance)

            add(north, BorderLayout.NORTH)
            add(centerInfo, BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
    }

    private fun selectInstance(instance: WorkbenchInstance, writeLog: Boolean = true) {
        selectedInstance = instance
        if (writeLog) {
            appendLog("Objekt #${instance.id} ausgewaehlt")
        }
    }

    private fun removeInstance(instance: WorkbenchInstance) {
        val removed = instances.removeIf { it.id == instance.id }
        if (!removed) {
            return
        }
        if (selectedInstance?.id == instance.id) {
            selectedInstance = instances.lastOrNull()
        }
        refreshInstancesCanvas()
        appendLog("Objekt #${instance.id} entfernt")
    }

    private fun installCardContextMenu(component: JComponent, instance: WorkbenchInstance) {
        component.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = maybeShowMenu(e)
            override fun mouseReleased(e: MouseEvent) = maybeShowMenu(e)

            private fun maybeShowMenu(e: MouseEvent) {
                if (!e.isPopupTrigger) {
                    return
                }
                val source = e.component
                if (source is JComponent) {
                    selectInstance(instance, writeLog = false)
                    val menu = createCardContextMenu(instance)
                    menu.show(source, e.x, e.y)
                }
            }
        })
    }

    private fun createCardContextMenu(instance: WorkbenchInstance): JPopupMenu {
        return JPopupMenu().apply {
            add(JMenuItem("Auswaehlen").apply {
                addActionListener { selectInstance(instance) }
            })

            add(JMenuItem("Objekt inspizieren...").apply {
                addActionListener { inspectInstance(instance) }
            })

            addSeparator()
            val methods = runCatching { helper().listMethods(instance.id) }.getOrElse {
                appendThrowable("Methoden konnten nicht geladen werden", it)
                emptyList()
            }
            if (methods.isEmpty()) {
                add(JMenuItem("Keine Methoden verfuegbar").apply { isEnabled = false })
            } else {
                methods.forEach { method ->
                    add(JMenuItem(method.display).apply {
                        addActionListener { invokeMethod(instance, method) }
                    })
                }
            }

            addSeparator()
            add(JMenuItem("Objekt entfernen").apply {
                addActionListener { removeInstance(instance) }
            })
        }
    }

    private fun inspectInstance(instance: WorkbenchInstance) {
        selectInstance(instance, writeLog = false)
        runCatching {
            val fields = helper().inspectObject(instance.id)
            ObjectInspectDialog(project, instance, fields).show()
        }.onFailure {
            appendThrowable("Objekt konnte nicht inspiziert werden", it)
        }
    }

    private fun createBadge(text: String): JLabel {
        return JLabel(text, SwingConstants.CENTER).apply {
            isOpaque = true
            background = badgeBackground
            foreground = badgeText
            font = Font("SansSerif", Font.BOLD, 11)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentSoft, 1),
                JBUI.Borders.empty(1, 6)
            )
        }
    }

    private fun invokeMethod(instance: WorkbenchInstance, wrapper: MethodWrapper) {
        val argumentTokens = if (wrapper.parameterTypes.isEmpty()) {
            emptyList()
        } else {
            val dialog = ParameterDialog(
                project,
                wrapper.parameterTypes,
                wrapper.parameterNames,
                instances,
                helper()
            )
            if (!dialog.showAndGet()) {
                return
            }
            dialog.argumentTokens()
        }

        runCatching {
            val result = helper().invokeMethod(
                instanceId = instance.id,
                methodIndex = wrapper.index,
                argumentTokens = argumentTokens
            )
            appendLog(
                "${instance.classSimpleName}.${wrapper.name}() -> $result"
            )
            showResultDialog(instance, wrapper, result)
        }.onFailure {
            appendThrowable("Methodenaufruf fehlgeschlagen", it)
        }
    }

    private fun helper(): RemoteJvmHelper {
        val existing = helper
        if (existing != null && existing.isAlive()) {
            return existing
        }

        existing?.close()
        return RemoteJvmHelper.start(project).also {
            helper = it
            appendLog("Externer Java-Helper mit Projekt-SDK gestartet.")
        }
    }

    private fun appendLog(text: String) {
        logArea.append("$text\n")
        logArea.caretPosition = logArea.document.length
    }

    private fun styleActionButton(button: JButton) {
        button.background = JBColor(Color(0xBFD7F4), Color(0x5E8FD0))
        button.foreground = JBColor(Color(0x0E355F), Color(0xFFFFFF))
        button.font = Font("SansSerif", Font.BOLD, 11)
        button.isFocusPainted = false
        button.isOpaque = true
        button.isContentAreaFilled = true
        button.isBorderPainted = true
        button.margin = JBUI.insets(2, 8)
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent, 1, true),
            JBUI.Borders.empty(2, 8)
        )
    }

    private fun showResultDialog(instance: WorkbenchInstance, wrapper: MethodWrapper, result: String) {
        object : DialogWrapper(project) {
            init {
                title = "Rueckgabewert"
                setOKButtonText("Schliessen")
                init()
            }

            override fun createCenterPanel(): JComponent {
                val valueArea = JTextArea(result.ifBlank { "<leer>" }).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    background = resultPanelBackground
                    border = JBUI.Borders.empty(10)
                    font = Font("DejaVu Sans Mono", Font.PLAIN, 12)
                }

                val header = JLabel("${instance.classSimpleName}.${wrapper.name}()")
                    .apply {
                        font = Font("SansSerif", Font.BOLD, 13)
                        foreground = JBColor(Color(0x6D4A00), Color(0xFFE7A6))
                    }

                return JPanel(BorderLayout(0, 8)).apply {
                    preferredSize = JBUI.size(480, 220)
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(resultPanelBorder, 1, true),
                        JBUI.Borders.empty(10)
                    )
                    background = resultPanelBackground
                    add(header, BorderLayout.NORTH)
                    add(JBScrollPane(valueArea), BorderLayout.CENTER)
                }
            }
        }.show()
    }

    private fun appendThrowable(prefix: String, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        appendLog("$prefix: ${throwable.message}")
        val message = throwable.message.orEmpty()
        if (message.contains("UnsupportedClassVersionError") || message.contains("class file version")) {
            appendLog("Hinweis: Die Klasse wurde mit einer neueren Java-Version kompiliert als das konfigurierte Project SDK.")
        }
        appendLog(writer.toString())
    }

    override fun removeNotify() {
        helper?.close()
        helper = null
        super.removeNotify()
    }
}

private data class WorkbenchInstance(
    val id: Int,
    val className: String,
    val classSimpleName: String
) {
    override fun toString(): String = "#${id} $classSimpleName"
}

private data class ConstructorWrapper(
    val declaringClassName: String,
    val index: Int,
    val display: String,
    val parameterTypes: List<String>,
    val parameterNames: List<String>
) {
    override fun toString(): String = display
}

private data class MethodWrapper(
    val index: Int,
    val name: String,
    val display: String,
    val parameterTypes: List<String>,
    val parameterNames: List<String>
) {
    override fun toString(): String = display
}

private data class ObjectFieldInfo(
    val declaringClass: String,
    val name: String,
    val typeName: String,
    val value: String
)

private class ObjectInspectDialog(
    project: Project,
    private val instance: WorkbenchInstance,
    fields: List<ObjectFieldInfo>
) : DialogWrapper(project) {
    private val tableModel = object : DefaultTableModel(
        arrayOf("Klasse", "Feld", "Typ", "Wert"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    init {
        title = "Objekt inspizieren"
        setOKButtonText("Schliessen")
        fields.forEach { field ->
            tableModel.addRow(
                arrayOf(
                    field.declaringClass,
                    field.name,
                    field.typeName,
                    field.value
                )
            )
        }
        init()
        setSize(760, 420)
    }

    override fun createCenterPanel(): JComponent {
        val info = JLabel("#${instance.id} ${instance.className}").apply {
            font = Font("SansSerif", Font.BOLD, 13)
        }
        val table = JTable(tableModel).apply {
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            rowHeight = 24
            fillsViewportHeight = true
        }
        return JPanel(BorderLayout(0, 8)).apply {
            preferredSize = JBUI.size(760, 420)
            add(info, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }
    }
}

private class RoundedCardPanel(
    private val fillColor: Color,
    private val strokeColor: Color,
    private val arc: Int = 14
) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: java.awt.Graphics) {
        val g2 = g.create() as java.awt.Graphics2D
        g2.setRenderingHint(
            java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON
        )
        g2.color = fillColor
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc)
        g2.color = strokeColor
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        g2.dispose()
        super.paintComponent(g)
    }
}

private class ParameterDialog(
    project: Project,
    parameterTypes: List<String>,
    parameterNames: List<String>,
    instances: List<WorkbenchInstance>,
    helper: RemoteJvmHelper
) : DialogWrapper(project) {
    private val editors = mutableListOf<ParameterEditor>()

    init {
        title = "Parameter eingeben"
        parameterTypes.forEachIndexed { index, typeName ->
            val parameterName = parameterNames.getOrNull(index).orEmpty()
            editors += ParameterEditor.create(index, typeName, parameterName, instances, helper)
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayout(editors.size.coerceAtLeast(1), 1, 0, 8))
        if (editors.isEmpty()) {
            panel.add(JLabel("Dieser Aufruf benoetigt keine Parameter."))
            return panel
        }

        editors.forEach { editor ->
            panel.add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent(editor.label, editor.component)
                    .panel
            )
        }
        return JBScrollPane(panel).apply { preferredSize = JBUI.size(500, 250) }
    }

    fun argumentTokens(): List<String> = editors.map { it.readToken() }
}

private class ParameterEditor private constructor(
    val label: String,
    val component: JComponent,
    private val reader: () -> String
) {
    fun readToken(): String = reader()

    companion object {
        fun create(
            index: Int,
            typeName: String,
            parameterName: String,
            instances: List<WorkbenchInstance>,
            helper: RemoteJvmHelper
        ): ParameterEditor {
            val safeName = parameterName.ifBlank { "arg$index" }
            val label = "[$index] ${simpleTypeName(typeName)} $safeName"

            if (isBooleanType(typeName)) {
                val checkBox = JCheckBox("true/false")
                return ParameterEditor(label, checkBox) { "TEXT:${checkBox.isSelected}" }
            }

            val matchingInstances = if (canUseReferences(typeName)) {
                runCatching { helper.listAssignableInstances(typeName) }.getOrDefault(emptyList())
                    .filter { remote -> instances.any { it.id == remote.id } }
            } else {
                emptyList()
            }

            if (matchingInstances.isNotEmpty() && !isScalarType(typeName)) {
                val modelItems = arrayOf("<null>") + matchingInstances.map { it.toString() }
                val combo = JComboBox(modelItems)
                return ParameterEditor(label, combo) {
                    val selected = combo.selectedIndex
                    if (selected <= 0) {
                        "NULL"
                    } else {
                        "REF:${matchingInstances[selected - 1].id}"
                    }
                }
            }

            val field = JTextField()
            return ParameterEditor(label, field) {
                val text = field.text
                when {
                    typeName == "java.lang.String" -> "TEXT:$text"
                    text.isBlank() && !isPrimitiveType(typeName) -> "NULL"
                    text.isBlank() -> throw IllegalArgumentException("Parameter $label darf nicht leer sein.")
                    else -> "TEXT:$text"
                }
            }
        }

        private fun simpleTypeName(typeName: String): String = typeName.substringAfterLast('.')

        private fun isBooleanType(typeName: String): Boolean {
            return typeName == "boolean" || typeName == "java.lang.Boolean"
        }

        private fun isPrimitiveType(typeName: String): Boolean {
            return typeName in setOf("boolean", "byte", "short", "int", "long", "float", "double", "char")
        }

        private fun canUseReferences(typeName: String): Boolean {
            return typeName != "java.lang.String" && !isPrimitiveType(typeName)
        }

        private fun isScalarType(typeName: String): Boolean {
            return typeName in setOf(
                "boolean", "byte", "short", "int", "long", "float", "double", "char",
                "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
                "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Character",
                "java.lang.String"
            )
        }
    }
}

private class RemoteJvmHelper private constructor(
    private val process: Process,
    private val reader: BufferedReader,
    private val writer: PrintWriter,
    private val stderrBuffer: StringBuilder
) : AutoCloseable {
    private val lock = Any()

    fun isAlive(): Boolean = process.isAlive

    fun listConstructors(fqcn: String): List<ConstructorWrapper> {
        val rows = sendStreamingCommand("LIST_CONSTRUCTORS", encodeB64(fqcn))
        return rows.filter { it.firstOrNull() == "CTOR" }.map { row ->
            val index = row.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalStateException("Ungueltiger Konstruktor-Index")
            val display = decodeB64(row.getOrNull(2) ?: "")
            val params = decodeList(row.getOrNull(3))
            val paramNames = decodeList(row.getOrNull(4))
            ConstructorWrapper(fqcn, index, display, params, paramNames)
        }
    }

    fun createInstance(className: String, constructorIndex: Int, argumentTokens: List<String>): WorkbenchInstance {
        val args = mutableListOf(
            encodeB64(className),
            constructorIndex.toString(),
            argumentTokens.size.toString()
        )
        args += argumentTokens.map(::encodeB64)
        val result = sendCommand("CREATE_INSTANCE", args)

        val id = result.getOrNull(0)?.toIntOrNull() ?: throw IllegalStateException("Instanz-ID fehlt")
        val resolvedClassName = decodeB64(result.getOrNull(1) ?: "")
        val simpleName = decodeB64(result.getOrNull(2) ?: "")
        return WorkbenchInstance(id = id, className = resolvedClassName, classSimpleName = simpleName)
    }

    fun listMethods(instanceId: Int): List<MethodWrapper> {
        val rows = sendStreamingCommand("LIST_METHODS", instanceId.toString())
        return rows.filter { it.firstOrNull() == "METH" }.map { row ->
            val index = row.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalStateException("Ungueltiger Methoden-Index")
            val name = decodeB64(row.getOrNull(2) ?: "")
            val display = decodeB64(row.getOrNull(3) ?: "")
            val params = decodeList(row.getOrNull(4))
            val paramNames = decodeList(row.getOrNull(5))
            MethodWrapper(
                index = index,
                name = name,
                display = display,
                parameterTypes = params,
                parameterNames = paramNames
            )
        }
    }

    fun invokeMethod(instanceId: Int, methodIndex: Int, argumentTokens: List<String>): String {
        val args = mutableListOf(instanceId.toString(), methodIndex.toString(), argumentTokens.size.toString())
        args += argumentTokens.map(::encodeB64)
        val result = sendCommand("INVOKE", args)
        return decodeB64(result.getOrNull(0) ?: "")
    }

    fun listAssignableInstances(typeName: String): List<WorkbenchInstance> {
        val rows = sendStreamingCommand("LIST_ASSIGNABLE", encodeB64(typeName))
        return rows.filter { it.firstOrNull() == "INST" }.map { row ->
            val id = row.getOrNull(1)?.toIntOrNull() ?: throw IllegalStateException("Ungueltige Instanz-ID")
            val className = decodeB64(row.getOrNull(2) ?: "")
            val simpleName = decodeB64(row.getOrNull(3) ?: "")
            WorkbenchInstance(id = id, className = className, classSimpleName = simpleName)
        }
    }

    fun inspectObject(instanceId: Int): List<ObjectFieldInfo> {
        val rows = sendStreamingCommand("INSPECT_OBJECT", instanceId.toString())
        return rows.filter { it.firstOrNull() == "FIELD" }.map { row ->
            ObjectFieldInfo(
                declaringClass = decodeB64(row.getOrNull(1) ?: ""),
                name = decodeB64(row.getOrNull(2) ?: ""),
                typeName = decodeB64(row.getOrNull(3) ?: ""),
                value = decodeB64(row.getOrNull(4) ?: "")
            )
        }
    }

    override fun close() {
        process.destroy()
        process.waitFor(300, TimeUnit.MILLISECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }

    private fun sendCommand(command: String, args: List<String>): List<String> {
        synchronized(lock) {
            ensureAlive()
            writer.println((listOf(command) + args).joinToString("\t"))
            writer.flush()

            val line = reader.readLine()
                ?: throw IllegalStateException("Helper-Prozess beendet. ${stderrTail()}")
            val tokens = line.split('\t')
            return when (tokens.firstOrNull()) {
                "OK" -> tokens.drop(1)
                "ERR" -> {
                    val message = decodeB64(tokens.getOrNull(1) ?: "")
                    throw IllegalStateException(message.ifBlank { "Unbekannter Helper-Fehler" })
                }
                else -> throw IllegalStateException("Unerwartete Helper-Antwort: $line")
            }
        }
    }

    private fun sendStreamingCommand(command: String, vararg args: String): List<List<String>> {
        synchronized(lock) {
            ensureAlive()
            writer.println((listOf(command) + args).joinToString("\t"))
            writer.flush()

            val first = reader.readLine()
                ?: throw IllegalStateException("Helper-Prozess beendet. ${stderrTail()}")
            val firstTokens = first.split('\t')
            when (firstTokens.firstOrNull()) {
                "OK" -> Unit
                "ERR" -> {
                    val message = decodeB64(firstTokens.getOrNull(1) ?: "")
                    throw IllegalStateException(message.ifBlank { "Unbekannter Helper-Fehler" })
                }
                else -> throw IllegalStateException("Unerwartete Helper-Antwort: $first")
            }

            val rows = mutableListOf<List<String>>()
            while (true) {
                val line = reader.readLine()
                    ?: throw IllegalStateException("Helper-Prozess beendet. ${stderrTail()}")
                if (line == "END") {
                    break
                }
                rows += line.split('\t')
            }
            return rows
        }
    }

    private fun ensureAlive() {
        if (!process.isAlive) {
            throw IllegalStateException("Helper-Prozess laeuft nicht mehr. ${stderrTail()}")
        }
    }

    private fun stderrTail(): String {
        val text = stderrBuffer.toString().trim()
        return if (text.isEmpty()) "" else "stderr: $text"
    }

    companion object {
        fun start(project: Project): RemoteJvmHelper {
            val sdk = ProjectRootManager.getInstance(project).projectSdk
                ?: throw IllegalStateException("Kein Project SDK gefunden. Bitte Java SDK konfigurieren.")

            val javaExecutable = resolveJavaExecutable(sdk)
            val classpath = linkedSetOf<String>()
            classpath += helperClassLocation()
            classpath += collectProjectClasspath(project)

            val process = ProcessBuilder(
                javaExecutable.absolutePath,
                "-cp",
                classpath.joinToString(File.pathSeparator),
                "de.example.bluejlike.workbench.ExternalWorkbenchHelper"
            ).start()

            val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8), true)
            val stderrBuffer = StringBuilder()

            thread(isDaemon = true, name = "object-workbench-helper-stderr") {
                process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        synchronized(stderrBuffer) {
                            if (stderrBuffer.isNotEmpty()) {
                                stderrBuffer.append('\n')
                            }
                            stderrBuffer.append(line)
                        }
                    }
                }
            }

            return RemoteJvmHelper(process, reader, writer, stderrBuffer).also {
                it.sendCommand("PING", emptyList())
            }
        }

        private fun collectProjectClasspath(project: Project): Set<String> {
            val paths = linkedSetOf<String>()
            ModuleManager.getInstance(project).modules.forEach { module ->
                paths += OrderEnumerator.orderEntries(module).recursively().runtimeOnly().pathsList.pathList
                val output = CompilerModuleExtension.getInstance(module)?.compilerOutputPath
                if (output != null) {
                    toFile(output)?.takeIf { it.exists() }?.let { paths += it.absolutePath }
                }
            }
            return paths.filter { File(it).exists() }.toCollection(linkedSetOf())
        }

        private fun helperClassLocation(): String {
            val classRef = ExternalWorkbenchHelper::class.java
            val codeSource = classRef.protectionDomain.codeSource?.location
            if (codeSource != null && codeSource.protocol == "file") {
                val sourceFile = runCatching { File(codeSource.toURI()) }
                    .getOrNull() ?: File(codeSource.path)
                if (sourceFile.exists()) {
                    return sourceFile.absolutePath
                }
            }

            val resource = classRef.getResource("${classRef.simpleName}.class")
                ?: throw IllegalStateException("Helper-Klassenpfad konnte nicht bestimmt werden")

            val location = when (resource.protocol) {
                "jar" -> {
                    val connection = resource.openConnection() as? java.net.JarURLConnection
                    connection?.jarFileURL?.toURI()?.let { File(it) }
                }
                "file" -> {
                    val classFile = File(resource.toURI())
                    var root = classFile.parentFile
                    val packageDepth = classRef.packageName.split('.').size
                    repeat(packageDepth) { root = root?.parentFile }
                    root
                }
                else -> null
            }

            return location?.takeIf { it.exists() }?.absolutePath
                ?: throw IllegalStateException("Helper-Klassenpfad konnte nicht bestimmt werden")
        }

        private fun resolveJavaExecutable(sdk: Sdk): File {
            val homePath = sdk.homePath ?: throw IllegalStateException("Project SDK ohne homePath")
            val javaBinary = File(homePath, "bin/java")
            val javaBinaryWindows = File(homePath, "bin/java.exe")
            return when {
                javaBinary.exists() -> javaBinary
                javaBinaryWindows.exists() -> javaBinaryWindows
                else -> throw IllegalStateException("Java-Binary im Project SDK nicht gefunden: $homePath")
            }
        }

        private fun encodeB64(value: String): String {
            return Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        }

        private fun decodeB64(value: String): String {
            if (value.isBlank()) return ""
            val bytes = Base64.getDecoder().decode(value)
            return String(bytes, StandardCharsets.UTF_8)
        }

        private fun decodeList(encoded: String?): List<String> {
            if (encoded.isNullOrBlank()) return emptyList()
            val decoded = decodeB64(encoded)
            if (decoded.isBlank()) return emptyList()
            return decoded.split(';')
        }

        private fun toFile(file: VirtualFile): File? {
            return runCatching { File(file.path) }.getOrNull()
        }
    }
}
