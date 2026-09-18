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
    private final HelperLanguage language;

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

    private ExternalWorkbenchHelper(HelperLanguage language) {
        this.language = language;
    }

    public static void main(String[] args) throws Exception {
        new ExternalWorkbenchHelper(HelperLanguage.fromArgs(args)).run();
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
                    default -> sendError(t("error.unknownCommand", command));
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
        if (shouldOfferImplicitDefaultConstructor(clazz, constructors)) {
            String display = clazz.getSimpleName() + "()";
            out.println(String.join("\t",
                "CTOR",
                "-1",
                encodeB64(display),
                encodeB64(""),
                encodeB64("")
            ));
        }
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
        final Object instance;
        if (constructorIndex == -1) {
            if (argCount != 0) {
                throw new IllegalArgumentException(t("error.constructorArgCountMismatch"));
            }
            instance = instantiateUsingImplicitDefaultConstructor(clazz, constructors);
        } else if (constructorIndex < 0 || constructorIndex >= constructors.size()) {
            throw new IllegalArgumentException(t("error.invalidConstructorIndexForClass", constructorIndex, fqcn));
        } else {
            Constructor<?> ctor = constructors.get(constructorIndex);
            Class<?>[] parameterTypes = ctor.getParameterTypes();

            if (parameterTypes.length != argCount) {
                throw new IllegalArgumentException(t("error.constructorArgCountMismatch"));
            }

            Object[] args = new Object[argCount];
            for (int i = 0; i < argCount; i++) {
                String token = decodeB64(parts[4 + i]);
                args[i] = parseToken(parameterTypes[i], token);
            }

            ctor.setAccessible(true);
            instance = ctor.newInstance(args);
        }

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
                encodeB64(paramNames),
                encodeB64(method.getDeclaringClass().getName())
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
            throw new IllegalArgumentException(t("error.methodArgCountMismatch"));
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
                value = t("value.notReadable");
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
                throw new IllegalArgumentException(t("error.nullNotAllowedForPrimitive", expectedType.getName()));
            }
            return null;
        }

        if (token.startsWith("REF:")) {
            int id = Integer.parseInt(token.substring("REF:".length()));
            Object value = requireInstance(id);
            if (!expectedType.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException(t("error.instanceNotCompatible", id, expectedType.getName()));
            }
            return value;
        }

        if (!token.startsWith("TEXT:")) {
            throw new IllegalArgumentException(t("error.invalidParameterToken", token));
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
                throw new IllegalArgumentException(t("error.booleanMustBeTrueFalse"));
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
                throw new IllegalArgumentException(t("error.charLength"));
            }
            return text.charAt(0);
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, text);
        }
        throw new IllegalArgumentException(t("error.literalNotSupported", type.getName()));
    }

    private List<Constructor<?>> sortedConstructors(Class<?> clazz) {
        List<Constructor<?>> constructors = new ArrayList<>(Arrays.asList(clazz.getDeclaredConstructors()));
        if (constructors.isEmpty() && mayHaveImplicitDefaultConstructor(clazz)) {
            try {
                constructors.add(clazz.getDeclaredConstructor());
            } catch (NoSuchMethodException ignored) {
            }
        }
        constructors.sort(
            Comparator.comparingInt((Constructor<?> c) -> c.getParameterTypes().length)
                .thenComparing(c -> Arrays.stream(c.getParameterTypes()).map(Class::getName).collect(Collectors.joining(";")))
        );
        return constructors;
    }

    private boolean shouldOfferImplicitDefaultConstructor(Class<?> clazz, List<Constructor<?>> constructors) {
        if (!mayHaveImplicitDefaultConstructor(clazz)) {
            return false;
        }
        return constructors.stream().noneMatch(ctor -> ctor.getParameterCount() == 0);
    }

    private Object instantiateUsingImplicitDefaultConstructor(Class<?> clazz, List<Constructor<?>> constructors) throws Exception {
        Constructor<?> noArgConstructor = constructors.stream()
            .filter(ctor -> ctor.getParameterCount() == 0)
            .findFirst()
            .orElse(null);
        if (noArgConstructor != null) {
            noArgConstructor.setAccessible(true);
            return noArgConstructor.newInstance();
        }

        if (constructors.isEmpty()) {
            throw new IllegalArgumentException(t("error.invalidConstructorIndexForClass", -1, clazz.getName()));
        }

        Constructor<?> fallback = constructors.get(0);
        Class<?>[] parameterTypes = fallback.getParameterTypes();
        Object[] defaultArgs = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            defaultArgs[i] = defaultValueForType(parameterTypes[i]);
        }

        fallback.setAccessible(true);
        return fallback.newInstance(defaultArgs);
    }

    private Object defaultValueForType(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private boolean mayHaveImplicitDefaultConstructor(Class<?> clazz) {
        return !clazz.isInterface() && !clazz.isAnnotation() && !clazz.isArray() && !clazz.isPrimitive();
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
            throw new IllegalArgumentException(t("error.instanceNotFound", id));
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
            throw new IllegalArgumentException(t("error.missingArgument", name));
        }
        return parts[index];
    }

    private void ensureLength(String[] parts, int minLength, String field) {
        if (parts.length < minLength) {
            throw new IllegalArgumentException(t("error.tooFewFields", field));
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

    private String t(String key, Object... args) {
        String template = language.text(key);
        return args.length == 0 ? template : String.format(template, args);
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

    private enum HelperLanguage {
        DE,
        EN,
        ES,
        FA;

        static HelperLanguage fromArgs(String[] args) {
            if (args == null || args.length == 0) {
                return EN;
            }
            String code = args[0] == null ? "" : args[0].trim().toLowerCase();
            return switch (code) {
                case "de" -> DE;
                case "es" -> ES;
                case "fa" -> FA;
                default -> EN;
            };
        }

        String text(String key) {
            return switch (this) {
                case DE -> german(key);
                case EN -> english(key);
                case ES -> spanish(key);
                case FA -> farsi(key);
            };
        }

        private static String english(String key) {
            return switch (key) {
                case "error.unknownCommand" -> "Unknown command: %s";
                case "error.invalidConstructorIndexForClass" -> "Invalid constructor index %d for %s";
                case "error.constructorArgCountMismatch" -> "Argument count does not match constructor";
                case "error.methodArgCountMismatch" -> "Argument count does not match method";
                case "value.notReadable" -> "<not readable>";
                case "error.nullNotAllowedForPrimitive" -> "null is not allowed for primitive type %s";
                case "error.instanceNotCompatible" -> "Instance #%d is not compatible with %s";
                case "error.invalidParameterToken" -> "Invalid parameter token: %s";
                case "error.booleanMustBeTrueFalse" -> "Boolean must be true or false";
                case "error.charLength" -> "Char must contain exactly one character";
                case "error.literalNotSupported" -> "Literal for type %s is not supported. Use an instance reference.";
                case "error.instanceNotFound" -> "Instance #%d not found";
                case "error.missingArgument" -> "Missing argument: %s";
                case "error.tooFewFields" -> "Too few fields for %s";
                default -> key;
            };
        }

        private static String german(String key) {
            return switch (key) {
                case "error.unknownCommand" -> "Unbekanntes Kommando: %s";
                case "error.invalidConstructorIndexForClass" -> "Ungueltiger Konstruktor-Index %d fuer %s";
                case "error.constructorArgCountMismatch" -> "Parameteranzahl passt nicht zum Konstruktor";
                case "error.methodArgCountMismatch" -> "Parameteranzahl passt nicht zur Methode";
                case "value.notReadable" -> "<nicht lesbar>";
                case "error.nullNotAllowedForPrimitive" -> "null ist fuer primitiven Typ %s nicht erlaubt";
                case "error.instanceNotCompatible" -> "Instanz #%d ist nicht kompatibel mit %s";
                case "error.invalidParameterToken" -> "Ungueltiges Parameter-Token: %s";
                case "error.booleanMustBeTrueFalse" -> "Boolean muss true oder false sein";
                case "error.charLength" -> "Char muss genau ein Zeichen enthalten";
                case "error.literalNotSupported" -> "Literal fuer Typ %s wird nicht unterstuetzt. Nutze eine Referenz auf eine Instanz.";
                case "error.instanceNotFound" -> "Instanz #%d nicht gefunden";
                case "error.missingArgument" -> "Argument fehlt: %s";
                case "error.tooFewFields" -> "Zu wenige Felder fuer %s";
                default -> english(key);
            };
        }

        private static String spanish(String key) {
            return switch (key) {
                case "error.unknownCommand" -> "Comando desconocido: %s";
                case "error.invalidConstructorIndexForClass" -> "Indice de constructor no valido %d para %s";
                case "error.constructorArgCountMismatch" -> "La cantidad de argumentos no coincide con el constructor";
                case "error.methodArgCountMismatch" -> "La cantidad de argumentos no coincide con el metodo";
                case "value.notReadable" -> "<no legible>";
                case "error.nullNotAllowedForPrimitive" -> "null no esta permitido para el tipo primitivo %s";
                case "error.instanceNotCompatible" -> "La instancia #%d no es compatible con %s";
                case "error.invalidParameterToken" -> "Token de parametro no valido: %s";
                case "error.booleanMustBeTrueFalse" -> "Boolean debe ser true o false";
                case "error.charLength" -> "Char debe contener exactamente un caracter";
                case "error.literalNotSupported" -> "No se admite literal para el tipo %s. Usa una referencia a una instancia.";
                case "error.instanceNotFound" -> "Instancia #%d no encontrada";
                case "error.missingArgument" -> "Falta argumento: %s";
                case "error.tooFewFields" -> "Muy pocos campos para %s";
                default -> english(key);
            };
        }

        private static String farsi(String key) {
            return switch (key) {
                case "error.unknownCommand" -> "دستور ناشناخته: %s";
                case "error.invalidConstructorIndexForClass" -> "اندیس سازنده %d برای %s نامعتبر است";
                case "error.constructorArgCountMismatch" -> "تعداد آرگومان‌ها با سازنده سازگار نیست";
                case "error.methodArgCountMismatch" -> "تعداد آرگومان‌ها با متد سازگار نیست";
                case "value.notReadable" -> "<قابل خواندن نیست>";
                case "error.nullNotAllowedForPrimitive" -> "برای نوع اولیه %s مقدار null مجاز نیست";
                case "error.instanceNotCompatible" -> "نمونه #%d با %s سازگار نیست";
                case "error.invalidParameterToken" -> "توکن پارامتر نامعتبر است: %s";
                case "error.booleanMustBeTrueFalse" -> "مقدار بولین باید true یا false باشد";
                case "error.charLength" -> "Char باید دقیقا یک کاراکتر داشته باشد";
                case "error.literalNotSupported" -> "لیترال برای نوع %s پشتیبانی نمی‌شود. از ارجاع نمونه استفاده کنید.";
                case "error.instanceNotFound" -> "نمونه #%d پیدا نشد";
                case "error.missingArgument" -> "آرگومان موجود نیست: %s";
                case "error.tooFewFields" -> "فیلدهای کافی برای %s وجود ندارد";
                default -> english(key);
            };
        }
    }
}
