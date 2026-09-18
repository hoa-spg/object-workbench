package de.example.bluejlike.workbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ExternalWorkbenchHelper {
    private final Map<Integer, Object> instances = new LinkedHashMap<>();
    private int nextId = 1;

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

    public static void main(String[] args) throws Exception {
        new ExternalWorkbenchHelper().run();
    }

    private void run() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            String[] parts = line.split("\\t", -1);
            String command = parts[0];
            try {
                switch (command) {
                    case "PING" -> sendOk();
                    case "LIST_CONSTRUCTORS" -> listConstructors(parts);
                    case "CREATE_INSTANCE" -> createInstance(parts);
                    case "LIST_METHODS" -> listMethods(parts);
                    case "INVOKE" -> invoke(parts);
                    case "INSPECT_OBJECT" -> inspectObject(parts);
                    case "LIST_ASSIGNABLE" -> listAssignable(parts);
                    default -> sendError("Unbekanntes Kommando: " + command);
                }
            } catch (Throwable t) {
                sendError(buildErrorMessage(t));
            }
        }
    }

    private void listConstructors(String[] parts) throws Exception {
        String fqcn = decodeB64(requireArg(parts, 1, "fqcn"));
        Class<?> clazz = resolveType(fqcn);
        List<Constructor<?>> constructors = sortedConstructors(clazz);

        sendOk();
        for (int i = 0; i < constructors.size(); i++) {
            Constructor<?> ctor = constructors.get(i);
            String display = clazz.getSimpleName() + "(" +
                Arrays.stream(ctor.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")";
            String paramTypes = Arrays.stream(ctor.getParameterTypes()).map(Class::getName).collect(Collectors.joining(";"));
            String paramNames = Arrays.stream(ctor.getParameters()).map(Parameter::getName).collect(Collectors.joining(";"));
            out.println(String.join("\t",
                "CTOR",
                Integer.toString(i),
                encodeB64(display),
                encodeB64(paramTypes),
                encodeB64(paramNames)
            ));
        }
        out.println("END");
    }

    private void createInstance(String[] parts) throws Exception {
        String fqcn = decodeB64(requireArg(parts, 1, "fqcn"));
        int constructorIndex = Integer.parseInt(requireArg(parts, 2, "constructorIndex"));
        int argCount = Integer.parseInt(requireArg(parts, 3, "argCount"));
        ensureLength(parts, 4 + argCount, "argTokens");

        Class<?> clazz = resolveType(fqcn);
        List<Constructor<?>> constructors = sortedConstructors(clazz);
        Constructor<?> ctor = constructors.get(constructorIndex);
        Class<?>[] parameterTypes = ctor.getParameterTypes();

        if (parameterTypes.length != argCount) {
            throw new IllegalArgumentException("Parameteranzahl passt nicht zum Konstruktor");
        }

        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            String token = decodeB64(parts[4 + i]);
            args[i] = parseToken(parameterTypes[i], token);
        }

        ctor.setAccessible(true);
        Object instance = ctor.newInstance(args);
        int id = nextId++;
        instances.put(id, instance);

        sendOk(Integer.toString(id), encodeB64(instance.getClass().getName()), encodeB64(instance.getClass().getSimpleName()));
    }

    private void listMethods(String[] parts) {
        int instanceId = Integer.parseInt(requireArg(parts, 1, "instanceId"));
        Object instance = requireInstance(instanceId);
        List<Method> methods = sortedMethods(instance.getClass());

        sendOk();
        for (int i = 0; i < methods.size(); i++) {
            Method method = methods.get(i);
            String display = method.getName() + "(" +
                Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", ")) +
                "): " + method.getReturnType().getSimpleName();
            String paramTypes = Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(";"));
            String paramNames = Arrays.stream(method.getParameters()).map(Parameter::getName).collect(Collectors.joining(";"));
            out.println(String.join("\t",
                "METH",
                Integer.toString(i),
                encodeB64(method.getName()),
                encodeB64(display),
                encodeB64(paramTypes),
                encodeB64(paramNames)
            ));
        }
        out.println("END");
    }

    private void invoke(String[] parts) throws Exception {
        int instanceId = Integer.parseInt(requireArg(parts, 1, "instanceId"));
        int methodIndex = Integer.parseInt(requireArg(parts, 2, "methodIndex"));
        int argCount = Integer.parseInt(requireArg(parts, 3, "argCount"));
        ensureLength(parts, 4 + argCount, "argTokens");

        Object instance = requireInstance(instanceId);
        List<Method> methods = sortedMethods(instance.getClass());
        Method method = methods.get(methodIndex);

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != argCount) {
            throw new IllegalArgumentException("Parameteranzahl passt nicht zur Methode");
        }

        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            String token = decodeB64(parts[4 + i]);
            args[i] = parseToken(parameterTypes[i], token);
        }

        method.setAccessible(true);
        Object result = method.invoke(instance, args);
        sendOk(encodeB64(formatValue(result)));
    }

    private void listAssignable(String[] parts) throws Exception {
        String typeName = decodeB64(requireArg(parts, 1, "typeName"));
        Class<?> targetType = resolveType(typeName);

        sendOk();
        if (targetType.isPrimitive()) {
            out.println("END");
            return;
        }

        for (Map.Entry<Integer, Object> entry : instances.entrySet()) {
            Object value = entry.getValue();
            if (targetType.isAssignableFrom(value.getClass())) {
                out.println(String.join("\t",
                    "INST",
                    Integer.toString(entry.getKey()),
                    encodeB64(value.getClass().getName()),
                    encodeB64(value.getClass().getSimpleName())
                ));
            }
        }
        out.println("END");
    }

    private void inspectObject(String[] parts) {
        int instanceId = Integer.parseInt(requireArg(parts, 1, "instanceId"));
        Object instance = requireInstance(instanceId);

        sendOk();
        for (Field field : collectFields(instance.getClass())) {
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(instance);
            } catch (IllegalAccessException e) {
                value = "<nicht lesbar>";
            }
            out.println(String.join("\t",
                "FIELD",
                encodeB64(field.getDeclaringClass().getSimpleName()),
                encodeB64(field.getName()),
                encodeB64(field.getType().getName()),
                encodeB64(formatValue(value))
            ));
        }
        out.println("END");
    }

    private Object parseToken(Class<?> expectedType, String token) throws Exception {
        if ("NULL".equals(token)) {
            if (expectedType.isPrimitive()) {
                throw new IllegalArgumentException("null ist fuer primitiven Typ " + expectedType.getName() + " nicht erlaubt");
            }
            return null;
        }

        if (token.startsWith("REF:")) {
            int id = Integer.parseInt(token.substring("REF:".length()));
            Object value = requireInstance(id);
            if (!expectedType.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException("Instanz #" + id + " ist nicht kompatibel mit " + expectedType.getName());
            }
            return value;
        }

        if (!token.startsWith("TEXT:")) {
            throw new IllegalArgumentException("Ungueltiges Parameter-Token: " + token);
        }

        String text = token.substring("TEXT:".length());
        return parseText(expectedType, text);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object parseText(Class<?> type, String text) {
        if (type == String.class) {
            return text;
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!"true".equals(text) && !"false".equals(text)) {
                throw new IllegalArgumentException("Boolean muss true oder false sein");
            }
            return Boolean.parseBoolean(text);
        }
        if (type == int.class || type == Integer.class) return Integer.parseInt(text);
        if (type == long.class || type == Long.class) return Long.parseLong(text);
        if (type == double.class || type == Double.class) return Double.parseDouble(text);
        if (type == float.class || type == Float.class) return Float.parseFloat(text);
        if (type == short.class || type == Short.class) return Short.parseShort(text);
        if (type == byte.class || type == Byte.class) return Byte.parseByte(text);
        if (type == char.class || type == Character.class) {
            if (text.length() != 1) {
                throw new IllegalArgumentException("Char muss genau ein Zeichen enthalten");
            }
            return text.charAt(0);
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, text);
        }
        throw new IllegalArgumentException("Literal fuer Typ " + type.getName() + " wird nicht unterstuetzt. Nutze eine Referenz auf eine Instanz.");
    }

    private List<Constructor<?>> sortedConstructors(Class<?> clazz) {
        List<Constructor<?>> constructors = new ArrayList<>(Arrays.asList(clazz.getDeclaredConstructors()));
        constructors.sort(
            Comparator.comparingInt((Constructor<?> c) -> c.getParameterTypes().length)
                .thenComparing(c -> Arrays.stream(c.getParameterTypes()).map(Class::getName).collect(Collectors.joining(";")))
        );
        return constructors;
    }

    private List<Method> sortedMethods(Class<?> clazz) {
        List<Method> methods = Arrays.stream(clazz.getMethods())
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .filter(method -> !method.isSynthetic())
            .collect(Collectors.toCollection(ArrayList::new));

        methods.sort(
            Comparator.comparing(Method::getName)
                .thenComparingInt((Method m) -> m.getParameterTypes().length)
                .thenComparing(m -> Arrays.stream(m.getParameterTypes()).map(Class::getName).collect(Collectors.joining(";")))
        );
        return methods;
    }

    private List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        fields.sort(
            Comparator.comparing((Field f) -> f.getDeclaringClass().getName())
                .thenComparing(Field::getName)
        );
        return fields;
    }

    private Object requireInstance(int id) {
        Object value = instances.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Instanz #" + id + " nicht gefunden");
        }
        return value;
    }

    private Class<?> resolveType(String typeName) throws ClassNotFoundException {
        return switch (typeName) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "char" -> char.class;
            default -> Class.forName(typeName);
        };
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> clazz = value.getClass();
        if (!clazz.isArray()) {
            return String.valueOf(value);
        }

        if (value instanceof Object[] objects) return Arrays.deepToString(objects);
        int length = Array.getLength(value);
        List<String> items = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            items.add(String.valueOf(Array.get(value, i)));
        }
        return items.toString();
    }

    private void sendOk(String... payload) {
        if (payload.length == 0) {
            out.println("OK");
        } else {
            out.println("OK\t" + String.join("\t", payload));
        }
    }

    private void sendError(String message) {
        out.println("ERR\t" + encodeB64(message));
    }

    private String requireArg(String[] parts, int index, String name) {
        if (index >= parts.length) {
            throw new IllegalArgumentException("Argument fehlt: " + name);
        }
        return parts[index];
    }

    private void ensureLength(String[] parts, int minLength, String field) {
        if (parts.length < minLength) {
            throw new IllegalArgumentException("Zu wenige Felder fuer " + field);
        }
    }

    private String buildErrorMessage(Throwable throwable) {
        Throwable root = throwable;
        if (throwable instanceof InvocationTargetException invocationTargetException && invocationTargetException.getCause() != null) {
            root = invocationTargetException.getCause();
        }
        String type = root.getClass().getSimpleName();
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            return type;
        }
        return type + ": " + msg;
    }

    private String encodeB64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeB64(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
