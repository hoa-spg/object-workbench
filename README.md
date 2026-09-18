# Object Workbench (IntelliJ Plugin)

Object Workbench is an IntelliJ IDEA plugin to create, inspect, and interact
with live Java objects from your project.

## Installation

### Option 1: JetBrains Marketplace

1. Open IntelliJ IDEA.
2. Go to `Settings/Preferences -> Plugins -> Marketplace`.
3. Search for `Object Workbench` and click `Install`.
4. Restart the IDE.

### Option 2: Install from ZIP

1. Build the plugin: `./gradlew buildPlugin`
2. In IntelliJ IDEA, open `Settings/Preferences -> Plugins`.
3. Click the gear icon and choose `Install Plugin from Disk...`.
4. Select the ZIP from `build/distributions/` and restart the IDE.

## Usage

1. Open the tool window `Object Workbench`.
2. Enter or pick a fully qualified class name from your project.
3. Load a constructor, fill in parameter values, and create an instance.
4. Select an instance card to inspect fields or invoke methods.
5. Review results and exceptions in the output panel.

## Notes

- Best used with compiled project classes.
- Current parameter input focuses on primitive types, wrappers, strings, and
  enums.
