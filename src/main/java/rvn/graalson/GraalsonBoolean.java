/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn.graalson;

import javax.json.JsonValue;
import org.graalvm.polyglot.Value;

/**
 *
 * @author wozza
 */
public class GraalsonBoolean implements JsonValue {

    private final Value value;

    public GraalsonBoolean(Value o) {
        this.value = o;
    }

    public Boolean getBoolean() {
        return this.value.asBoolean();
    }

    @Override
    public ValueType getValueType() {
        return ValueType.FALSE;
    }

}
