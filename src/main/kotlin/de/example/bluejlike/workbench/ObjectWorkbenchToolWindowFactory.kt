package de.example.bluejlike.workbench

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import com.intellij.util.ui.AsyncProcessIcon
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
import javax.swing.Icon
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

private enum class AppLanguage(val code: String) {
    GERMAN("de"),
    ENGLISH("en"),
    SPANISH("es"),
    FARSI("fa");

    fun displayName(contextLanguage: AppLanguage): String {
        return when (this) {
            GERMAN -> WorkbenchI18n.text(contextLanguage, "lang.german")
            ENGLISH -> WorkbenchI18n.text(contextLanguage, "lang.english")
            SPANISH -> WorkbenchI18n.text(contextLanguage, "lang.spanish")
            FARSI -> WorkbenchI18n.text(contextLanguage, "lang.farsi")
        }
    }

    override fun toString(): String = displayName(GERMAN)
}

private object WorkbenchI18n {
    fun text(language: AppLanguage, key: String, vararg args: Any): String {
        val template = when (language) {
            AppLanguage.GERMAN -> german(key)
            AppLanguage.ENGLISH -> english(key)
            AppLanguage.SPANISH -> spanish(key)
            AppLanguage.FARSI -> farsi(key)
        }
        return if (args.isEmpty()) template else String.format(template, *args)
    }

    private fun english(key: String): String = when (key) {
        "app.title" -> "Object Workbench"
        "label.fqcn" -> "Fully-qualified class"
        "label.constructor" -> "Constructor"
        "label.language" -> "Language"
        "button.chooseClass" -> "Choose class"
        "button.refreshClass" -> "Refresh class"
        "button.createInstance" -> "Create instance"
        "button.clearInstances" -> "Clear all instances"
        "button.select" -> "Select"
        "button.ok" -> "OK"
        "button.cancel" -> "Cancel"
        "progress.openClassChooser" -> "Loading class chooser"
        "checkbox.showInherited" -> "Show inherited methods"
        "checkbox.clearInstancesOnRebuild" -> "Clear instances on each rebuild"
        "border.workbench" -> "Object Workbench"
        "border.output" -> "Output"
        "chooser.selectClass" -> "Choose class"
        "warning.enterClass" -> "Please enter a class name."
        "warning.selectConstructor" -> "Please load and select a constructor first."
        "purpose.loadConstructors" -> "load constructors for %s"
        "purpose.createInstance" -> "prepare fresh class build for %s"
        "log.constructorsLoaded" -> "%d constructor(s) loaded for %s"
        "error.constructorsLoad" -> "Failed to load constructors"
        "log.buildStart" -> "Starting project build (%s) ..."
        "log.buildAborted" -> "Project build was aborted."
        "log.buildFailed" -> "Project build failed: %d error(s), %d warning(s)."
        "log.buildDoneWarn" -> "Project build finished: %d warning(s)."
        "log.buildDone" -> "Project build finished."
        "log.instancesCleared" -> "All instances removed from workbench."
        "log.instancesClearedOnBuild" -> "Instances removed because rebuild cleanup is enabled."
        "log.instanceCreated" -> "Instance #%d created: %s"
        "error.instanceCreate" -> "Failed to create instance"
        "card.objectTitle" -> "Object #%d"
        "label.package" -> "Package: %s"
        "label.defaultPackage" -> "<default>"
        "log.objectSelected" -> "Object #%d selected"
        "log.objectRemoved" -> "Object #%d removed"
        "menu.select" -> "Select"
        "menu.inspect" -> "Inspect object..."
        "error.methodsLoad" -> "Failed to load methods"
        "menu.noMethods" -> "No methods available"
        "menu.noOwnMethods" -> "No declared methods available"
        "menu.removeObject" -> "Remove object"
        "error.inspectObject" -> "Failed to inspect object"
        "error.invokeFailed" -> "Method invocation failed"
        "log.helperStarted" -> "External Java helper started with project SDK."
        "dialog.returnValue" -> "Return value"
        "dialog.close" -> "Close"
        "dialog.inspectObject" -> "Inspect object"
        "dialog.enterParameters" -> "Enter parameters"
        "dialog.noParameters" -> "This call does not require any parameters."
        "placeholder.empty" -> "<empty>"
        "placeholder.null" -> "<null>"
        "error.parameterRequired" -> "Parameter %s must not be empty."
        "log.classVersionHint" -> "Hint: The class was compiled with a newer Java version than the configured project SDK."
        "table.class" -> "Class"
        "table.field" -> "Field"
        "table.type" -> "Type"
        "table.value" -> "Value"
        "error.remoteInvalidCtorIndex" -> "Invalid constructor index"
        "error.remoteInstanceIdMissing" -> "Instance id is missing"
        "error.remoteInvalidMethodIndex" -> "Invalid method index"
        "error.remoteInvalidInstanceId" -> "Invalid instance id"
        "error.helperExited" -> "Helper process terminated. %s"
        "error.helperUnknown" -> "Unknown helper error"
        "error.helperUnexpectedResponse" -> "Unexpected helper response: %s"
        "error.helperNotRunning" -> "Helper process is no longer running. %s"
        "error.stderrPrefix" -> "stderr: %s"
        "error.noProjectSdk" -> "No project SDK found. Please configure a Java SDK."
        "error.helperClassPathMissing" -> "Could not determine helper classpath"
        "error.projectSdkNoHomePath" -> "Project SDK has no homePath"
        "error.javaBinaryMissing" -> "Java binary not found in project SDK: %s"
        "log.languageChanged" -> "Language switched to %s."
        "lang.german" -> "German"
        "lang.english" -> "English"
        "lang.spanish" -> "Spanish"
        "lang.farsi" -> "Farsi"
        else -> key
    }

    private fun german(key: String): String = when (key) {
        "app.title" -> "Object Workbench"
        "label.fqcn" -> "Vollqualifizierte Klasse"
        "label.constructor" -> "Konstruktor"
        "label.language" -> "Sprache"
        "button.chooseClass" -> "Klasse auswaehlen"
        "button.refreshClass" -> "Klasse aktualisieren"
        "button.createInstance" -> "Instanz erstellen"
        "button.clearInstances" -> "Alle Instanzen loeschen"
        "button.select" -> "Auswaehlen"
        "button.ok" -> "OK"
        "button.cancel" -> "Abbrechen"
        "progress.openClassChooser" -> "Klassenfenster wird geladen"
        "checkbox.showInherited" -> "Vererbte Methoden anzeigen"
        "checkbox.clearInstancesOnRebuild" -> "Instanzen bei jedem Neu-Build loeschen"
        "border.workbench" -> "Objekt-Workbench"
        "border.output" -> "Ausgabe"
        "chooser.selectClass" -> "Klasse waehlen"
        "warning.enterClass" -> "Bitte eine Klasse eingeben."
        "warning.selectConstructor" -> "Bitte zuerst einen Konstruktor laden und waehlen."
        "purpose.loadConstructors" -> "Konstruktoren laden fuer %s"
        "purpose.createInstance" -> "Klasse fuer neue Instanz von %s neu bauen"
        "log.constructorsLoaded" -> "%d Konstruktor(en) geladen fuer %s"
        "error.constructorsLoad" -> "Konstruktoren konnten nicht geladen werden"
        "log.buildStart" -> "Starte Projekt-Build (%s) ..."
        "log.buildAborted" -> "Projekt-Build wurde abgebrochen."
        "log.buildFailed" -> "Projekt-Build fehlgeschlagen: %d Fehler, %d Warnungen."
        "log.buildDoneWarn" -> "Projekt-Build abgeschlossen: %d Warnungen."
        "log.buildDone" -> "Projekt-Build abgeschlossen."
        "log.instancesCleared" -> "Alle Instanzen aus der Workbench geloescht."
        "log.instancesClearedOnBuild" -> "Instanzen wurden wegen aktivierter Build-Bereinigung geloescht."
        "log.instanceCreated" -> "Instanz #%d erstellt: %s"
        "error.instanceCreate" -> "Instanz konnte nicht erstellt werden"
        "card.objectTitle" -> "Objekt #%d"
        "label.package" -> "Paket: %s"
        "label.defaultPackage" -> "<default>"
        "log.objectSelected" -> "Objekt #%d ausgewaehlt"
        "log.objectRemoved" -> "Objekt #%d entfernt"
        "menu.select" -> "Auswaehlen"
        "menu.inspect" -> "Objekt inspizieren..."
        "error.methodsLoad" -> "Methoden konnten nicht geladen werden"
        "menu.noMethods" -> "Keine Methoden verfuegbar"
        "menu.noOwnMethods" -> "Keine eigenen Methoden verfuegbar"
        "menu.removeObject" -> "Objekt entfernen"
        "error.inspectObject" -> "Objekt konnte nicht inspiziert werden"
        "error.invokeFailed" -> "Methodenaufruf fehlgeschlagen"
        "log.helperStarted" -> "Externer Java-Helper mit Projekt-SDK gestartet."
        "dialog.returnValue" -> "Rueckgabewert"
        "dialog.close" -> "Schliessen"
        "dialog.inspectObject" -> "Objekt inspizieren"
        "dialog.enterParameters" -> "Parameter eingeben"
        "dialog.noParameters" -> "Dieser Aufruf benoetigt keine Parameter."
        "placeholder.empty" -> "<leer>"
        "placeholder.null" -> "<null>"
        "error.parameterRequired" -> "Parameter %s darf nicht leer sein."
        "log.classVersionHint" -> "Hinweis: Die Klasse wurde mit einer neueren Java-Version kompiliert als das konfigurierte Project SDK."
        "table.class" -> "Klasse"
        "table.field" -> "Feld"
        "table.type" -> "Typ"
        "table.value" -> "Wert"
        "error.remoteInvalidCtorIndex" -> "Ungueltiger Konstruktor-Index"
        "error.remoteInstanceIdMissing" -> "Instanz-ID fehlt"
        "error.remoteInvalidMethodIndex" -> "Ungueltiger Methoden-Index"
        "error.remoteInvalidInstanceId" -> "Ungueltige Instanz-ID"
        "error.helperExited" -> "Helper-Prozess beendet. %s"
        "error.helperUnknown" -> "Unbekannter Helper-Fehler"
        "error.helperUnexpectedResponse" -> "Unerwartete Helper-Antwort: %s"
        "error.helperNotRunning" -> "Helper-Prozess laeuft nicht mehr. %s"
        "error.stderrPrefix" -> "stderr: %s"
        "error.noProjectSdk" -> "Kein Project SDK gefunden. Bitte Java SDK konfigurieren."
        "error.helperClassPathMissing" -> "Helper-Klassenpfad konnte nicht bestimmt werden"
        "error.projectSdkNoHomePath" -> "Project SDK ohne homePath"
        "error.javaBinaryMissing" -> "Java-Binary im Project SDK nicht gefunden: %s"
        "log.languageChanged" -> "Sprache auf %s umgestellt."
        "lang.german" -> "Deutsch"
        "lang.english" -> "Englisch"
        "lang.spanish" -> "Spanisch"
        "lang.farsi" -> "Farsi"
        else -> english(key)
    }

    private fun spanish(key: String): String = when (key) {
        "app.title" -> "Object Workbench"
        "label.fqcn" -> "Clase totalmente calificada"
        "label.constructor" -> "Constructor"
        "label.language" -> "Idioma"
        "button.chooseClass" -> "Elegir clase"
        "button.refreshClass" -> "Actualizar clase"
        "button.createInstance" -> "Crear instancia"
        "button.clearInstances" -> "Borrar todas las instancias"
        "button.select" -> "Seleccionar"
        "button.ok" -> "Aceptar"
        "button.cancel" -> "Cancelar"
        "progress.openClassChooser" -> "Cargando selector de clases"
        "checkbox.showInherited" -> "Mostrar metodos heredados"
        "checkbox.clearInstancesOnRebuild" -> "Borrar instancias en cada recompilacion"
        "border.workbench" -> "Object Workbench"
        "border.output" -> "Salida"
        "chooser.selectClass" -> "Elegir clase"
        "warning.enterClass" -> "Introduce un nombre de clase."
        "warning.selectConstructor" -> "Primero carga y selecciona un constructor."
        "purpose.loadConstructors" -> "cargar constructores para %s"
        "purpose.createInstance" -> "preparar compilacion limpia para %s"
        "log.constructorsLoaded" -> "Se cargaron %d constructor(es) para %s"
        "error.constructorsLoad" -> "No se pudieron cargar los constructores"
        "log.buildStart" -> "Iniciando compilacion del proyecto (%s) ..."
        "log.buildAborted" -> "La compilacion del proyecto fue cancelada."
        "log.buildFailed" -> "Compilacion fallida: %d error(es), %d advertencia(s)."
        "log.buildDoneWarn" -> "Compilacion completada: %d advertencia(s)."
        "log.buildDone" -> "Compilacion del proyecto completada."
        "log.instancesCleared" -> "Todas las instancias fueron eliminadas del workbench."
        "log.instancesClearedOnBuild" -> "Se eliminaron instancias porque la limpieza tras compilacion esta activa."
        "log.instanceCreated" -> "Instancia #%d creada: %s"
        "error.instanceCreate" -> "No se pudo crear la instancia"
        "card.objectTitle" -> "Objeto #%d"
        "label.package" -> "Paquete: %s"
        "label.defaultPackage" -> "<predeterminado>"
        "log.objectSelected" -> "Objeto #%d seleccionado"
        "log.objectRemoved" -> "Objeto #%d eliminado"
        "menu.select" -> "Seleccionar"
        "menu.inspect" -> "Inspeccionar objeto..."
        "error.methodsLoad" -> "No se pudieron cargar los metodos"
        "menu.noMethods" -> "No hay metodos disponibles"
        "menu.noOwnMethods" -> "No hay metodos declarados disponibles"
        "menu.removeObject" -> "Eliminar objeto"
        "error.inspectObject" -> "No se pudo inspeccionar el objeto"
        "error.invokeFailed" -> "La invocacion del metodo fallo"
        "log.helperStarted" -> "Helper Java externo iniciado con el SDK del proyecto."
        "dialog.returnValue" -> "Valor de retorno"
        "dialog.close" -> "Cerrar"
        "dialog.inspectObject" -> "Inspeccionar objeto"
        "dialog.enterParameters" -> "Introducir parametros"
        "dialog.noParameters" -> "Esta llamada no requiere parametros."
        "placeholder.empty" -> "<vacio>"
        "placeholder.null" -> "<null>"
        "error.parameterRequired" -> "El parametro %s no puede estar vacio."
        "log.classVersionHint" -> "Aviso: La clase se compilo con una version de Java mas reciente que el SDK configurado."
        "table.class" -> "Clase"
        "table.field" -> "Campo"
        "table.type" -> "Tipo"
        "table.value" -> "Valor"
        "error.remoteInvalidCtorIndex" -> "Indice de constructor no valido"
        "error.remoteInstanceIdMissing" -> "Falta el ID de instancia"
        "error.remoteInvalidMethodIndex" -> "Indice de metodo no valido"
        "error.remoteInvalidInstanceId" -> "ID de instancia no valido"
        "error.helperExited" -> "El proceso helper termino. %s"
        "error.helperUnknown" -> "Error desconocido del helper"
        "error.helperUnexpectedResponse" -> "Respuesta inesperada del helper: %s"
        "error.helperNotRunning" -> "El proceso helper ya no esta en ejecucion. %s"
        "error.stderrPrefix" -> "stderr: %s"
        "error.noProjectSdk" -> "No se encontro un SDK de proyecto. Configura un SDK de Java."
        "error.helperClassPathMissing" -> "No se pudo determinar el classpath del helper"
        "error.projectSdkNoHomePath" -> "El SDK del proyecto no tiene homePath"
        "error.javaBinaryMissing" -> "No se encontro el binario de Java en el SDK del proyecto: %s"
        "log.languageChanged" -> "Idioma cambiado a %s."
        "lang.german" -> "Aleman"
        "lang.english" -> "Ingles"
        "lang.spanish" -> "Espanol"
        "lang.farsi" -> "Farsi"
        else -> english(key)
    }

    private fun farsi(key: String): String = when (key) {
        "app.title" -> "Object Workbench"
        "label.fqcn" -> "کلاس با نام کامل"
        "label.constructor" -> "سازنده"
        "label.language" -> "زبان"
        "button.chooseClass" -> "انتخاب کلاس"
        "button.refreshClass" -> "به‌روزرسانی کلاس"
        "button.createInstance" -> "ایجاد نمونه"
        "button.clearInstances" -> "حذف همه نمونه‌ها"
        "button.select" -> "انتخاب"
        "button.ok" -> "تایید"
        "button.cancel" -> "لغو"
        "progress.openClassChooser" -> "در حال بارگذاری پنجره انتخاب کلاس"
        "checkbox.showInherited" -> "نمایش متدهای ارث‌بری‌شده"
        "checkbox.clearInstancesOnRebuild" -> "حذف نمونه‌ها در هر بازسازی"
        "border.workbench" -> "Object Workbench"
        "border.output" -> "خروجی"
        "chooser.selectClass" -> "انتخاب کلاس"
        "warning.enterClass" -> "لطفا نام کلاس را وارد کنید."
        "warning.selectConstructor" -> "ابتدا یک سازنده را بارگذاری و انتخاب کنید."
        "purpose.loadConstructors" -> "بارگذاری سازنده‌ها برای %s"
        "purpose.createInstance" -> "بازسازی کلاس برای نمونه جدید از %s"
        "log.constructorsLoaded" -> "%d سازنده برای %s بارگذاری شد"
        "error.constructorsLoad" -> "بارگذاری سازنده‌ها ناموفق بود"
        "log.buildStart" -> "در حال ساخت پروژه (%s) ..."
        "log.buildAborted" -> "ساخت پروژه لغو شد."
        "log.buildFailed" -> "ساخت پروژه ناموفق بود: %d خطا، %d هشدار."
        "log.buildDoneWarn" -> "ساخت پروژه تمام شد: %d هشدار."
        "log.buildDone" -> "ساخت پروژه تمام شد."
        "log.instancesCleared" -> "همه نمونه‌ها از ورک‌بنچ حذف شدند."
        "log.instancesClearedOnBuild" -> "به دلیل فعال بودن پاک‌سازی پس از بازسازی، نمونه‌ها حذف شدند."
        "log.instanceCreated" -> "نمونه #%d ایجاد شد: %s"
        "error.instanceCreate" -> "ایجاد نمونه ناموفق بود"
        "card.objectTitle" -> "شیء #%d"
        "label.package" -> "بسته: %s"
        "label.defaultPackage" -> "<پیش‌فرض>"
        "log.objectSelected" -> "شیء #%d انتخاب شد"
        "log.objectRemoved" -> "شیء #%d حذف شد"
        "menu.select" -> "انتخاب"
        "menu.inspect" -> "بازرسی شیء..."
        "error.methodsLoad" -> "بارگذاری متدها ناموفق بود"
        "menu.noMethods" -> "متدی در دسترس نیست"
        "menu.noOwnMethods" -> "متد تعریف‌شده‌ای در دسترس نیست"
        "menu.removeObject" -> "حذف شیء"
        "error.inspectObject" -> "بازرسی شیء ناموفق بود"
        "error.invokeFailed" -> "فراخوانی متد ناموفق بود"
        "log.helperStarted" -> "Helper جاوا با SDK پروژه شروع شد."
        "dialog.returnValue" -> "مقدار بازگشتی"
        "dialog.close" -> "بستن"
        "dialog.inspectObject" -> "بازرسی شیء"
        "dialog.enterParameters" -> "ورود پارامترها"
        "dialog.noParameters" -> "این فراخوانی پارامتری نیاز ندارد."
        "placeholder.empty" -> "<خالی>"
        "placeholder.null" -> "<null>"
        "error.parameterRequired" -> "پارامتر %s نباید خالی باشد."
        "log.classVersionHint" -> "نکته: کلاس با نسخه جدیدتری از جاوا نسبت به SDK پروژه کامپایل شده است."
        "table.class" -> "کلاس"
        "table.field" -> "فیلد"
        "table.type" -> "نوع"
        "table.value" -> "مقدار"
        "error.remoteInvalidCtorIndex" -> "اندیس سازنده نامعتبر است"
        "error.remoteInstanceIdMissing" -> "شناسه نمونه وجود ندارد"
        "error.remoteInvalidMethodIndex" -> "اندیس متد نامعتبر است"
        "error.remoteInvalidInstanceId" -> "شناسه نمونه نامعتبر است"
        "error.helperExited" -> "پردازش helper متوقف شد. %s"
        "error.helperUnknown" -> "خطای ناشناخته در helper"
        "error.helperUnexpectedResponse" -> "پاسخ غیرمنتظره از helper: %s"
        "error.helperNotRunning" -> "پردازش helper دیگر اجرا نمی‌شود. %s"
        "error.stderrPrefix" -> "stderr: %s"
        "error.noProjectSdk" -> "SDK پروژه یافت نشد. لطفا یک SDK جاوا تنظیم کنید."
        "error.helperClassPathMissing" -> "مسیر کلاس helper مشخص نشد"
        "error.projectSdkNoHomePath" -> "SDK پروژه homePath ندارد"
        "error.javaBinaryMissing" -> "باینری جاوا در SDK پروژه یافت نشد: %s"
        "log.languageChanged" -> "زبان به %s تغییر کرد."
        "lang.german" -> "آلمانی"
        "lang.english" -> "انگلیسی"
        "lang.spanish" -> "اسپانیایی"
        "lang.farsi" -> "فارسی"
        else -> english(key)
    }
}

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

    private var language = AppLanguage.ENGLISH

    private val titleLabel = JLabel().apply {
        foreground = accent
        font = Font("SansSerif", Font.BOLD, 16)
        border = JBUI.Borders.emptyBottom(4)
    }
    private val classLabel = JLabel()
    private val constructorLabel = JLabel()
    private val languageLabel = JLabel()

    private val classField = JBTextField()
    private val constructorCombo = JComboBox<ConstructorWrapper>()
    private val showInheritedMethodsCheckBox = JCheckBox()
    private val clearInstancesOnRebuildCheckBox = JCheckBox()
    private val languageCombo = JComboBox(AppLanguage.values())
    private val refreshClassButton = JButton()
    private val createButton = JButton()
    private val clearInstancesButton = JButton()
    private val browseButton = JButton()
    private val classChooserLoadingIcon = AsyncProcessIcon("class-chooser-loading")

    private val instancesBorder = BorderFactory.createTitledBorder("")
    private val outputBorder = BorderFactory.createTitledBorder("")
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

        val fieldLabelSize = JBUI.size(180, 24)
        classLabel.preferredSize = fieldLabelSize
        classLabel.font = Font("SansSerif", Font.PLAIN, 13)
        constructorLabel.preferredSize = fieldLabelSize
        constructorLabel.font = Font("SansSerif", Font.PLAIN, 13)

        classField.font = Font("SansSerif", Font.PLAIN, 13)
        constructorCombo.font = Font("SansSerif", Font.PLAIN, 13)
        val selectorInputSize = JBUI.size(520, 30)
        classField.preferredSize = selectorInputSize
        classField.minimumSize = selectorInputSize
        constructorCombo.preferredSize = selectorInputSize
        constructorCombo.minimumSize = selectorInputSize

        languageLabel.font = Font("SansSerif", Font.PLAIN, 12)
        languageCombo.font = Font("SansSerif", Font.PLAIN, 12)
        languageCombo.selectedItem = language

        showInheritedMethodsCheckBox.font = Font("SansSerif", Font.PLAIN, 12)
        showInheritedMethodsCheckBox.isOpaque = false
        showInheritedMethodsCheckBox.isSelected = false
        clearInstancesOnRebuildCheckBox.font = Font("SansSerif", Font.PLAIN, 12)
        clearInstancesOnRebuildCheckBox.isOpaque = false
        clearInstancesOnRebuildCheckBox.isSelected = false

        styleIconActionButton(refreshClassButton, AllIcons.Actions.Refresh)
        styleActionButton(createButton)
        styleIconActionButton(clearInstancesButton, AllIcons.General.Remove)
        styleActionButton(browseButton)
        classChooserLoadingIcon.isOpaque = false
        classChooserLoadingIcon.isVisible = false
        classChooserLoadingIcon.`suspend`()

        browseButton.addActionListener { chooseClassFromProject() }
        refreshClassButton.addActionListener { refreshConstructors() }
        classField.addActionListener { refreshConstructors() }
        createButton.addActionListener { createInstance() }
        clearInstancesButton.addActionListener { clearAllInstances(writeLog = true, resetHelper = true) }
        languageCombo.addActionListener {
            val selected = languageCombo.selectedItem as? AppLanguage ?: AppLanguage.ENGLISH
            if (selected == language) {
                return@addActionListener
            }
            language = selected
            resetHelperProcess()
            applyLanguageTexts()
            refreshInstancesCanvas()
            appendLog(t("log.languageChanged", selected.displayName(this.language)))
        }

        val classRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(classLabel, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(classField)
                },
                BorderLayout.CENTER
            )
        }
        val constructorRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(constructorLabel, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(constructorCombo)
                },
                BorderLayout.CENTER
            )
        }

        val top = FormBuilder.createFormBuilder()
            .addComponent(titleLabel)
            .addComponent(classRow)
            .addComponent(
                JPanel(BorderLayout(8, 0)).apply {
                    isOpaque = false

                    val leftActions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                        isOpaque = false
                        add(browseButton)
                        add(classChooserLoadingIcon)
                        add(createButton)
                    }

                    val options = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                        isOpaque = false
                        add(showInheritedMethodsCheckBox)
                        add(clearInstancesOnRebuildCheckBox)
                        add(languageLabel)
                        add(languageCombo)
                    }

                    val quickActions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                        isOpaque = false
                        add(refreshClassButton)
                        add(clearInstancesButton)
                    }

                    add(leftActions, BorderLayout.WEST)
                    add(options, BorderLayout.CENTER)
                    add(quickActions, BorderLayout.EAST)
                }
            )
            .addComponent(constructorRow)
            .panel
            .apply {
                isOpaque = false
            }

        instancesCanvas.border = instancesBorder
        instancesCanvas.background = benchBackground
        (instancesCanvas.border as? TitledBorder)?.titleColor = accent
        (instancesCanvas.border as? TitledBorder)?.titleFont = Font("SansSerif", Font.BOLD, 12)

        val center = JBScrollPane(instancesCanvas)
        center.border = JBUI.Borders.emptyTop(8)
        center.background = benchBackground

        val bottom = JBScrollPane(logArea)
        bottom.border = outputBorder
        (bottom.border as? TitledBorder)?.titleColor = accentSoft
        (bottom.border as? TitledBorder)?.titleFont = Font("SansSerif", Font.BOLD, 12)

        applyLanguageTexts()

        add(top, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        add(bottom, BorderLayout.SOUTH)
    }

    private fun applyLanguageTexts() {
        titleLabel.text = t("app.title")
        classLabel.text = t("label.fqcn")
        constructorLabel.text = t("label.constructor")
        languageLabel.text = t("label.language")
        browseButton.text = t("button.chooseClass")
        classChooserLoadingIcon.toolTipText = t("progress.openClassChooser")
        refreshClassButton.text = ""
        refreshClassButton.toolTipText = t("button.refreshClass")
        createButton.text = t("button.createInstance")
        clearInstancesButton.text = ""
        clearInstancesButton.toolTipText = t("button.clearInstances")
        showInheritedMethodsCheckBox.text = t("checkbox.showInherited")
        clearInstancesOnRebuildCheckBox.text = t("checkbox.clearInstancesOnRebuild")
        instancesBorder.title = t("border.workbench")
        outputBorder.title = t("border.output")
        revalidate()
        repaint()
    }

    private fun t(key: String, vararg args: Any): String {
        return WorkbenchI18n.text(language, key, *args)
    }

    private fun chooseClassFromProject() {
        setClassChooserLoading(true)
        try {
            val chooser = com.intellij.ide.util.TreeClassChooserFactory.getInstance(project)
                .createProjectScopeChooser(t("chooser.selectClass"))
            chooser.showDialog()
            val psiClass = chooser.selected
            if (psiClass != null) {
                classField.text = psiClass.qualifiedName ?: ""
                refreshConstructors()
            }
        } finally {
            setClassChooserLoading(false)
        }
    }

    private fun setClassChooserLoading(isLoading: Boolean) {
        browseButton.isEnabled = !isLoading
        classChooserLoadingIcon.isVisible = isLoading
        if (isLoading) {
            classChooserLoadingIcon.resume()
        } else {
            classChooserLoadingIcon.`suspend`()
        }
    }

    private fun refreshConstructors() {
        val fqcn = classField.text.trim()
        if (fqcn.isEmpty()) {
            Messages.showWarningDialog(project, t("warning.enterClass"), t("app.title"))
            return
        }

        val preferredSignature = (constructorCombo.selectedItem as? ConstructorWrapper)
            ?.let { constructorSignature(it.parameterTypes) }

        buildProjectThen(t("purpose.loadConstructors", fqcn)) {
            resetHelperProcess()
            loadConstructors(fqcn, preferredSignature)
        }
    }

    private fun loadConstructors(fqcn: String, preferredSignature: String? = null): List<ConstructorWrapper> {
        return runCatching {
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

            if (enriched.isNotEmpty()) {
                val selected = enriched.firstOrNull {
                    constructorSignature(it.parameterTypes) == preferredSignature
                } ?: enriched.first()
                constructorCombo.selectedItem = selected
            }

            appendLog(t("log.constructorsLoaded", enriched.size, fqcn))
            enriched
        }.onFailure {
            appendThrowable(t("error.constructorsLoad"), it)
        }.getOrElse { emptyList() }
    }

    private fun constructorSignature(parameterTypes: List<String>): String {
        return parameterTypes.joinToString(";")
    }

    private fun buildProjectThen(purpose: String, onSuccess: () -> Unit) {
        appendLog(t("log.buildStart", purpose))
        FileDocumentManager.getInstance().saveAllDocuments()
        CompilerManager.getInstance(project).make { aborted, errors, warnings, _ ->
            ApplicationManager.getApplication().invokeLater {
                when {
                    aborted -> appendLog(t("log.buildAborted"))
                    errors > 0 -> appendLog(t("log.buildFailed", errors, warnings))
                    warnings > 0 -> {
                        appendLog(t("log.buildDoneWarn", warnings))
                        clearInstancesAfterBuildIfEnabled()
                        onSuccess()
                    }
                    else -> {
                        appendLog(t("log.buildDone"))
                        clearInstancesAfterBuildIfEnabled()
                        onSuccess()
                    }
                }
            }
        }
    }

    private fun clearInstancesAfterBuildIfEnabled() {
        if (!clearInstancesOnRebuildCheckBox.isSelected) {
            return
        }
        val cleared = clearAllInstances(writeLog = false, resetHelper = false)
        if (cleared) {
            appendLog(t("log.instancesClearedOnBuild"))
        }
    }

    private fun constructorNamesFromSource(fqcn: String): Map<String, List<String>> {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(fqcn, GlobalSearchScope.projectScope(project))
            ?: return emptyMap()

        val constructors = psiClass.constructors
        if (constructors.isEmpty() && !psiClass.isInterface && !psiClass.isAnnotationType && !psiClass.isEnum) {
            return mapOf("" to emptyList())
        }

        return constructors.associate { ctor ->
            val typeSignature = ctor.parameterList.parameters
                .joinToString(";") { it.type.canonicalText }
            val names = ctor.parameterList.parameters.map { it.name ?: "" }
            typeSignature to names
        }
    }

    private fun methodNamesFromSource(declaringClassName: String): Map<String, List<String>> {
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(declaringClassName, GlobalSearchScope.projectScope(project))
            ?: return emptyMap()

        return psiClass.methods.associate { method ->
            val typeSignature = method.parameterList.parameters
                .joinToString(";") { it.type.canonicalText }
            val signature = "${method.name}($typeSignature)"
            val names = method.parameterList.parameters.map { it.name ?: "" }
            signature to names
        }
    }

    private fun enrichMethodsWithSourceNames(methods: List<MethodWrapper>): List<MethodWrapper> {
        if (methods.isEmpty()) {
            return methods
        }

        val namesByClass = methods.map { it.declaringClassName }
            .distinct()
            .associateWith(::methodNamesFromSource)

        return methods.map { wrapper ->
            val signature = "${wrapper.name}(${wrapper.parameterTypes.joinToString(";")})"
            val sourceNames = namesByClass[wrapper.declaringClassName]?.get(signature)
            if (sourceNames.isNullOrEmpty()) {
                wrapper
            } else {
                wrapper.copy(parameterNames = sourceNames)
            }
        }
    }

    private fun createInstance() {
        val wrapper = constructorCombo.selectedItem as? ConstructorWrapper
        if (wrapper == null) {
            Messages.showWarningDialog(project, t("warning.selectConstructor"), t("app.title"))
            return
        }
        val preferredSignature = constructorSignature(wrapper.parameterTypes)

        buildProjectThen(t("purpose.createInstance", wrapper.declaringClassName)) {
            resetHelperProcess()
            val refreshedConstructors = loadConstructors(wrapper.declaringClassName, preferredSignature)
            val activeWrapper = (constructorCombo.selectedItem as? ConstructorWrapper)
                ?: refreshedConstructors.firstOrNull()
            if (activeWrapper == null) {
                Messages.showWarningDialog(project, t("warning.selectConstructor"), t("app.title"))
                return@buildProjectThen
            }

            val activeHelper = helper()
            val dialog = ParameterDialog(
                project,
                activeWrapper.parameterTypes,
                activeWrapper.parameterNames,
                instances,
                activeHelper,
                language
            )
            if (!dialog.showAndGet()) {
                return@buildProjectThen
            }

            runCatching {
                val instance = activeHelper.createInstance(
                    className = activeWrapper.declaringClassName,
                    constructorIndex = activeWrapper.index,
                    argumentTokens = dialog.argumentTokens()
                )
                instances += instance
                selectedInstance = instance
                refreshInstancesCanvas()
                appendLog(t("log.instanceCreated", instance.id, instance.className))
            }.onFailure {
                appendThrowable(t("error.instanceCreate"), it)
            }
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

            val packageName = instance.className.substringBeforeLast('.', t("label.defaultPackage"))
            val badges = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(createBadge("#${instance.id}"))
                add(createBadge(instance.classSimpleName))
            }

            val title = JLabel(t("card.objectTitle", instance.id)).apply {
                foreground = accent
                font = Font("SansSerif", Font.BOLD, 13)
            }
            val details = JLabel("${instance.classSimpleName} (${instance.className})").apply {
                font = Font("SansSerif", Font.PLAIN, 12)
            }
            val packageLabel = JLabel(t("label.package", packageName)).apply {
                foreground = JBColor.GRAY
                font = Font("SansSerif", Font.PLAIN, 11)
            }
            val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(JButton(t("button.select")).apply {
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
            appendLog(t("log.objectSelected", instance.id))
        }
    }

    private fun removeInstance(instance: WorkbenchInstance) {
        val index = instances.indexOfFirst { it === instance }
        if (index < 0) {
            return
        }
        instances.removeAt(index)
        if (selectedInstance === instance) {
            selectedInstance = instances.lastOrNull()
        }
        refreshInstancesCanvas()
        appendLog(t("log.objectRemoved", instance.id))
    }

    private fun clearAllInstances(writeLog: Boolean, resetHelper: Boolean): Boolean {
        if (instances.isEmpty() && selectedInstance == null) {
            if (resetHelper) {
                resetHelperProcess()
            }
            return false
        }
        instances.clear()
        selectedInstance = null
        refreshInstancesCanvas()
        if (resetHelper) {
            resetHelperProcess()
        }
        if (writeLog) {
            appendLog(t("log.instancesCleared"))
        }
        return true
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
            add(JMenuItem(t("menu.select")).apply {
                addActionListener { selectInstance(instance) }
            })

            add(JMenuItem(t("menu.inspect")).apply {
                addActionListener { inspectInstance(instance) }
            })

            addSeparator()
            val methods = runCatching {
                helper().listMethods(instance.id).let(::enrichMethodsWithSourceNames)
            }.getOrElse {
                appendThrowable(t("error.methodsLoad"), it)
                emptyList()
            }
            val visibleMethods = filterVisibleMethods(instance, methods)
            if (visibleMethods.isEmpty()) {
                val label = if (methods.isEmpty()) {
                    t("menu.noMethods")
                } else {
                    t("menu.noOwnMethods")
                }
                add(JMenuItem(label).apply { isEnabled = false })
            } else {
                visibleMethods.forEach { method ->
                    add(JMenuItem(method.display).apply {
                        addActionListener { invokeMethod(instance, method) }
                    })
                }
            }

            addSeparator()
            add(JMenuItem(t("menu.removeObject")).apply {
                addActionListener { removeInstance(instance) }
            })
        }
    }

    private fun filterVisibleMethods(instance: WorkbenchInstance, methods: List<MethodWrapper>): List<MethodWrapper> {
        if (showInheritedMethodsCheckBox.isSelected) {
            return methods
        }
        return methods.filter { it.declaringClassName == instance.className }
    }

    private fun inspectInstance(instance: WorkbenchInstance) {
        selectInstance(instance, writeLog = false)
        runCatching {
            val fields = helper().inspectObject(instance.id)
            ObjectInspectDialog(project, instance, fields, language).show()
        }.onFailure {
            appendThrowable(t("error.inspectObject"), it)
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
                helper(),
                language
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
            appendThrowable(t("error.invokeFailed"), it)
        }
    }

    private fun resetHelperProcess() {
        helper?.close()
        helper = null
    }

    private fun helper(): RemoteJvmHelper {
        val existing = helper
        if (existing != null && existing.isAlive()) {
            return existing
        }

        resetHelperProcess()
        return RemoteJvmHelper.start(project, language).also {
            helper = it
            appendLog(t("log.helperStarted"))
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

    private fun styleIconActionButton(button: JButton, icon: Icon) {
        styleActionButton(button)
        button.icon = icon
        button.text = ""
        button.margin = JBUI.insets(1)
        button.preferredSize = JBUI.size(26, 24)
        button.minimumSize = JBUI.size(26, 24)
        button.maximumSize = JBUI.size(26, 24)
        button.horizontalAlignment = SwingConstants.CENTER
    }

    private fun showResultDialog(instance: WorkbenchInstance, wrapper: MethodWrapper, result: String) {
        object : DialogWrapper(project) {
            init {
                title = t("dialog.returnValue")
                setOKButtonText(t("dialog.close"))
                init()
            }

            override fun createCenterPanel(): JComponent {
                val valueArea = JTextArea(result.ifBlank { t("placeholder.empty") }).apply {
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
            appendLog(t("log.classVersionHint"))
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
    val parameterNames: List<String>,
    val declaringClassName: String
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
    fields: List<ObjectFieldInfo>,
    private val language: AppLanguage
) : DialogWrapper(project) {
    private val tableModel = object : DefaultTableModel(
        arrayOf(
            WorkbenchI18n.text(language, "table.class"),
            WorkbenchI18n.text(language, "table.field"),
            WorkbenchI18n.text(language, "table.type"),
            WorkbenchI18n.text(language, "table.value")
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    init {
        title = WorkbenchI18n.text(language, "dialog.inspectObject")
        setOKButtonText(WorkbenchI18n.text(language, "dialog.close"))
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
    helper: RemoteJvmHelper,
    private val language: AppLanguage
) : DialogWrapper(project) {
    private val editors = mutableListOf<ParameterEditor>()

    init {
        title = WorkbenchI18n.text(language, "dialog.enterParameters")
        setOKButtonText(WorkbenchI18n.text(language, "button.ok"))
        setCancelButtonText(WorkbenchI18n.text(language, "button.cancel"))
        parameterTypes.forEachIndexed { index, typeName ->
            val parameterName = parameterNames.getOrNull(index).orEmpty()
            editors += ParameterEditor.create(index, typeName, parameterName, instances, helper, language)
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayout(editors.size.coerceAtLeast(1), 1, 0, 8))
        if (editors.isEmpty()) {
            panel.add(JLabel(WorkbenchI18n.text(language, "dialog.noParameters")))
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
            helper: RemoteJvmHelper,
            language: AppLanguage
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
                val modelItems = arrayOf(WorkbenchI18n.text(language, "placeholder.null")) + matchingInstances.map { it.toString() }
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
                    text.isBlank() -> throw IllegalArgumentException(
                        WorkbenchI18n.text(language, "error.parameterRequired", label)
                    )
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
    private val stderrBuffer: StringBuilder,
    private val language: AppLanguage
) : AutoCloseable {
    private val lock = Any()

    private fun t(key: String, vararg args: Any): String = WorkbenchI18n.text(language, key, *args)

    fun isAlive(): Boolean = process.isAlive

    fun listConstructors(fqcn: String): List<ConstructorWrapper> {
        val rows = sendStreamingCommand("LIST_CONSTRUCTORS", encodeB64(fqcn))
        return rows.filter { it.firstOrNull() == "CTOR" }.map { row ->
            val index = row.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalStateException(t("error.remoteInvalidCtorIndex"))
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

        val id = result.getOrNull(0)?.toIntOrNull() ?: throw IllegalStateException(t("error.remoteInstanceIdMissing"))
        val resolvedClassName = decodeB64(result.getOrNull(1) ?: "")
        val simpleName = decodeB64(result.getOrNull(2) ?: "")
        return WorkbenchInstance(id = id, className = resolvedClassName, classSimpleName = simpleName)
    }

    fun listMethods(instanceId: Int): List<MethodWrapper> {
        val rows = sendStreamingCommand("LIST_METHODS", instanceId.toString())
        return rows.filter { it.firstOrNull() == "METH" }.map { row ->
            val index = row.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalStateException(t("error.remoteInvalidMethodIndex"))
            val name = decodeB64(row.getOrNull(2) ?: "")
            val display = decodeB64(row.getOrNull(3) ?: "")
            val params = decodeList(row.getOrNull(4))
            val paramNames = decodeList(row.getOrNull(5))
            val declaringClassName = decodeB64(row.getOrNull(6) ?: "")
            MethodWrapper(
                index = index,
                name = name,
                display = display,
                parameterTypes = params,
                parameterNames = paramNames,
                declaringClassName = declaringClassName
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
            val id = row.getOrNull(1)?.toIntOrNull() ?: throw IllegalStateException(t("error.remoteInvalidInstanceId"))
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
                ?: throw IllegalStateException(t("error.helperExited", stderrTail()))
            val tokens = line.split('\t')
            return when (tokens.firstOrNull()) {
                "OK" -> tokens.drop(1)
                "ERR" -> {
                    val message = decodeB64(tokens.getOrNull(1) ?: "")
                    throw IllegalStateException(message.ifBlank { t("error.helperUnknown") })
                }
                else -> throw IllegalStateException(t("error.helperUnexpectedResponse", line))
            }
        }
    }

    private fun sendStreamingCommand(command: String, vararg args: String): List<List<String>> {
        synchronized(lock) {
            ensureAlive()
            writer.println((listOf(command) + args).joinToString("\t"))
            writer.flush()

            val first = reader.readLine()
                ?: throw IllegalStateException(t("error.helperExited", stderrTail()))
            val firstTokens = first.split('\t')
            when (firstTokens.firstOrNull()) {
                "OK" -> Unit
                "ERR" -> {
                    val message = decodeB64(firstTokens.getOrNull(1) ?: "")
                    throw IllegalStateException(message.ifBlank { t("error.helperUnknown") })
                }
                else -> throw IllegalStateException(t("error.helperUnexpectedResponse", first))
            }

            val rows = mutableListOf<List<String>>()
            while (true) {
                val line = reader.readLine()
                    ?: throw IllegalStateException(t("error.helperExited", stderrTail()))
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
            throw IllegalStateException(t("error.helperNotRunning", stderrTail()))
        }
    }

    private fun stderrTail(): String {
        val text = stderrBuffer.toString().trim()
        return if (text.isEmpty()) "" else t("error.stderrPrefix", text)
    }

    companion object {
        fun start(project: Project, language: AppLanguage): RemoteJvmHelper {
            val sdk = ProjectRootManager.getInstance(project).projectSdk
                ?: throw IllegalStateException(WorkbenchI18n.text(language, "error.noProjectSdk"))

            val javaExecutable = resolveJavaExecutable(sdk, language)
            val classpath = linkedSetOf<String>()
            classpath += helperClassLocation(language)
            classpath += collectProjectClasspath(project)

            val process = ProcessBuilder(
                javaExecutable.absolutePath,
                "-cp",
                classpath.joinToString(File.pathSeparator),
                "de.example.bluejlike.workbench.ExternalWorkbenchHelper",
                language.code
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

            return RemoteJvmHelper(process, reader, writer, stderrBuffer, language).also {
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

        private fun helperClassLocation(language: AppLanguage): String {
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
                ?: throw IllegalStateException(WorkbenchI18n.text(language, "error.helperClassPathMissing"))

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
                ?: throw IllegalStateException(WorkbenchI18n.text(language, "error.helperClassPathMissing"))
        }

        private fun resolveJavaExecutable(sdk: Sdk, language: AppLanguage): File {
            val homePath = sdk.homePath
                ?: throw IllegalStateException(WorkbenchI18n.text(language, "error.projectSdkNoHomePath"))
            val javaBinary = File(homePath, "bin/java")
            val javaBinaryWindows = File(homePath, "bin/java.exe")
            return when {
                javaBinary.exists() -> javaBinary
                javaBinaryWindows.exists() -> javaBinaryWindows
                else -> throw IllegalStateException(WorkbenchI18n.text(language, "error.javaBinaryMissing", homePath))
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
