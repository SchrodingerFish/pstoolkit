package cn.pstoolkit.gson;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.Reader;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class GsonUtils {
    private static final DateTimeFormatter LDT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LT_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Gson GSON = baseBuilder().create();
    private static final Gson PRETTY_GSON = baseBuilder().setPrettyPrinting().create();

    private GsonUtils() {}

    private static GsonBuilder baseBuilder() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
                    @Override public void write(JsonWriter out, LocalDateTime value) throws java.io.IOException {
                        if (value == null) { out.nullValue(); return; }
                        out.value(LDT_FMT.format(value));
                    }
                    @Override public LocalDateTime read(JsonReader in) throws java.io.IOException {
                        if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                        String s = in.nextString();
                        if (s == null || s.isEmpty()) return null;
                        try { return LocalDateTime.parse(s, LDT_FMT); } catch (Exception e) { return LocalDateTime.parse(s); }
                    }
                })
                .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {
                    @Override public void write(JsonWriter out, LocalDate value) throws java.io.IOException {
                        if (value == null) { out.nullValue(); return; }
                        out.value(LD_FMT.format(value));
                    }
                    @Override public LocalDate read(JsonReader in) throws java.io.IOException {
                        if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                        String s = in.nextString();
                        if (s == null || s.isEmpty()) return null;
                        try { return LocalDate.parse(s, LD_FMT); } catch (Exception e) { return LocalDate.parse(s); }
                    }
                })
                .registerTypeAdapter(LocalTime.class, new TypeAdapter<LocalTime>() {
                    @Override public void write(JsonWriter out, LocalTime value) throws java.io.IOException {
                        if (value == null) { out.nullValue(); return; }
                        out.value(LT_FMT.format(value));
                    }
                    @Override public LocalTime read(JsonReader in) throws java.io.IOException {
                        if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                        String s = in.nextString();
                        if (s == null || s.isEmpty()) return null;
                        try { return LocalTime.parse(s, LT_FMT); } catch (Exception e) { return LocalTime.parse(s); }
                    }
                });
    }

    public static Gson gson() { return GSON; }

    public static Gson prettyGson() { return PRETTY_GSON; }

    public static GsonBuilder newGsonBuilder() { return baseBuilder(); }

    public static String toJson(Object src) { return GSON.toJson(src); }

    public static String toJsonPretty(Object src) { return PRETTY_GSON.toJson(src); }

    public static String toJsonSafe(Object src, String fallback) {
        try { return toJson(src); } catch (Exception e) { return fallback; }
    }

    public static JsonElement toJsonElement(Object src) { return GSON.toJsonTree(src); }

    public static <T> T fromJson(String json, Class<T> clazz) { return GSON.fromJson(json, clazz); }

    public static <T> T fromJson(String json, Type typeOfT) { return GSON.fromJson(json, typeOfT); }

    public static <T> T fromJson(Reader reader, Class<T> clazz) { return GSON.fromJson(reader, clazz); }

    public static <T> T fromJsonOrNull(String json, Class<T> clazz) {
        try { return fromJson(json, clazz); } catch (Exception e) { return null; }
    }

    public static <T> T fromJsonOrDefault(String json, Class<T> clazz, T defaultValue) {
        T t = fromJsonOrNull(json, clazz);
        return t == null ? defaultValue : t;
    }

    public static <T> List<T> fromJsonList(String json, Class<T> elementType) {
        Type type = TypeToken.getParameterized(List.class, elementType).getType();
        return GSON.fromJson(json, type);
    }

    public static <V> Map<String, V> fromJsonMap(String json, Class<V> valueType) {
        Type type = TypeToken.getParameterized(Map.class, String.class, valueType).getType();
        return GSON.fromJson(json, type);
    }

    public static <T> T fromJson(JsonElement json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> T fromJson(JsonElement json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    public static <T> T deepCopy(T src, Class<T> clazz) {
        if (src == null) return null;
        return fromJson(toJson(src), clazz);
    }

    public static <T> T deepCopy(T src, Type type) {
        if (src == null) return null;
        return GSON.fromJson(GSON.toJsonTree(src), type);
    }

    /**
        Convert an object into a Map<String, Object> by traversing its JSON tree.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object src) {
        JsonElement el = toJsonElement(src);
        Object o = fromElement(el);
        if (o instanceof Map) return (Map<String, Object>) o;
        return new LinkedHashMap<>();
    }

    private static Object fromElement(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                // Prefer integral if possible
                try {
                    long l = p.getAsLong();
                    // If conversion changed value, fall back to double
                    if (String.valueOf(l).equals(p.getAsString())) return l;
                } catch (Exception ignored) {}
                return p.getAsDouble();
            }
            return p.getAsString();
        }
        if (el.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) list.add(fromElement(e));
            return list;
        }
        if (el.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                map.put(e.getKey(), fromElement(e.getValue()));
            }
            return map;
        }
        return null;
    }
}
