# Object Workbench (IntelliJ Plugin)

MVP fuer eine Objekt-Workbench in IntelliJ IDEA.

## Aktueller Funktionsumfang

- Klasse aus dem Projekt auswaehlen (oder FQCN eingeben)
- Konstruktoren laden und per Formular Parameter eingeben
- Instanz erstellen und als "Kasten" in der Workbench anzeigen
- Instanz auswaehlen, Methoden anzeigen und ausfuehren
- Rueckgabewerte und Exceptions im Log sehen

## Grenzen des MVP

- Reflection basiert auf kompilierten Klassen im Projekt-Output
- Parameter-Parsing nur fuer primitive Typen, Wrapper, String, enum
- Fuer komplexe Typen (z. B. Listen, eigene Objekte) gibt es noch keinen spezialisierten Editor

## Starten

1. Projekt in IntelliJ IDEA oeffnen
2. Gradle Sync ausfuehren
3. Task `runIde` starten
4. In der gestarteten IDE das Tool Window `Object Workbench` oeffnen

## Naechste sinnvolle Schritte

- Besserer Parameter-Editor fuer Collections und verschachtelte Objekte
- Speichern/Laden von Session-Zustaenden
- Optional: Ausfuehrung im Debug-Prozess statt lokalem Reflection-ClassLoader
