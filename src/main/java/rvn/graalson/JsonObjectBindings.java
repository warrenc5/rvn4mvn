package rvn.graalson;

import java.util.HashMap;
import javax.json.JsonObject;
import javax.script.SimpleBindings;
import static rvn.graalson.GraalsonProvider.toJava;

/**
 *
 * @author wozza
 */
public class JsonObjectBindings extends SimpleBindings {

    public JsonObjectBindings(JsonObject jsonObject) {
        super(new HashMap<String, Object>());
        super.putAll(toJava(jsonObject));
    }

}
