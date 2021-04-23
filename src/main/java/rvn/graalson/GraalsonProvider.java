package rvn.graalson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParserFactory;
import org.graalvm.polyglot.Value;

/**
 *
 * @author wozza
 */
public class GraalsonProvider extends JsonProvider {

    public static JsonProvider provider() {
        return new GraalsonProvider();
    }

    @Override
    public JsonParser createParser(Reader reader) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonParser createParser(InputStream in) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonParserFactory createParserFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonGenerator createGenerator(Writer writer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonGenerator createGenerator(OutputStream out) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonGeneratorFactory createGeneratorFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonReader createReader(Reader reader) {
        return new GraalsonReader(reader);
    }

    @Override
    public JsonReader createReader(InputStream in) {
        return this.createReader(new InputStreamReader(in));
    }

    @Override
    public JsonWriter createWriter(Writer writer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonWriter createWriter(OutputStream out) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonWriterFactory createWriterFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonReaderFactory createReaderFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonObjectBuilder createObjectBuilder() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonArrayBuilder createArrayBuilder() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JsonBuilderFactory createBuilderFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public static JsonValue toJsonValue(Value o) {
        if (o.hasArrayElements()) {
            return new GraalsonArray(o);
        } else if (o.hasMembers()) {
            return new GraalsonObject(o);
        } else if (o.isNumber()) {
            return new GraalsonNumber(o);
        } else if (o.isString()) {
            return new GraalsonString(o);
        } else if (o.isBoolean()) {
            return new GraalsonBoolean(o);
        }
        throw new IllegalArgumentException(o.toString());
    }

    public static <T extends JsonValue> T toJsonValue(Value o, Class<T> jClass) {

        if (jClass.equals(JsonObject.class)) {
            return (T) new GraalsonObject(o);
        } else if (jClass.equals(JsonArray.class)) {
            return (T) new GraalsonArray(o);
        } else if (jClass.equals(JsonNumber.class)) {
            return (T) new GraalsonNumber(o);
        } else if (jClass.equals(JsonString.class)) {
            return (T) new GraalsonString(o);
        }
        throw new IllegalArgumentException(o.toString());
    }

    public static List toJava(JsonArray value) {
        List result = new ArrayList();
        for (int i = 0; i < value.size(); i++) {
            Object v = toJava(value.get(i));
            result.add(v);
        }
        return result;
    }

    public static Map toJava(JsonObject value) {
        Map result = new HashMap();
        for (Entry<String, JsonValue> e : value.entrySet()) {
            Object v = toJava(e.getValue());
            result.put(e.getKey(), v);
        }
        return result;
    }

    public static Object toJava(JsonValue value) {

        switch (value.getValueType()) {
            case NUMBER:
                return ((JsonNumber) value).intValue();
            case STRING:
                return ((JsonString) value).getString();
            case OBJECT:
                return toJava((JsonObject) value);
            case ARRAY:
                return toJava((JsonArray) value);
            case FALSE:
                return ((GraalsonBoolean) value).getBoolean();
            case NULL:
                return null;
        }
        throw new IllegalArgumentException(value.toString());
    }
}
