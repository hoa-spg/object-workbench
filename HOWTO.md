# HOWTO: Plugin nutzen und installieren

Hinweis vorweg: Dieses Projekt ist ein **IntelliJ-Plugin** und **keine VS Code Extension**. Eine direkte Installation in VS Code ist daher nicht moeglich.

## Option 1: Plugin direkt starten (empfohlen fuer Entwicklung)

1. Im Ordner `ij-plugin` ein Terminal oeffnen.
2. Plugin in einer IntelliJ-Sandbox starten:

```bash
GRADLE_USER_HOME="/media/andy/data/git/projects/.gradle-cache" ./gradlew runIde
```

3. Es startet eine separate IntelliJ-Instanz mit deinem Plugin.
4. Dort ein Java-Projekt oeffnen.
5. Tool Window aufrufen: `View` -> `Tool Windows` -> `Object Workbench`.

## Option 2: Installierbares ZIP bauen und manuell installieren

1. Plugin-Paket bauen:

```bash
GRADLE_USER_HOME="/media/andy/data/git/projects/.gradle-cache" ./gradlew buildPlugin
```

2. Das ZIP liegt danach unter `build/distributions/`.
3. In IntelliJ installieren:
   - `Settings` -> `Plugins`
   - Zahnrad-Menue -> `Install Plugin from Disk...`
   - ZIP-Datei aus `build/distributions/` auswaehlen

## Warum `GRADLE_USER_HOME` gesetzt wird

Auf deinem System ist die Home-Partition sehr voll. Mit `GRADLE_USER_HOME` wird der Gradle-Cache auf ein Laufwerk mit mehr Platz umgeleitet.
