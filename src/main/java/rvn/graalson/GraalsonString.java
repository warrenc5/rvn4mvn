/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn.graalson;

import javax.json.JsonString;
import org.graalvm.polyglot.Value;

/**
 *
 * @author wozza
 */
public class GraalsonString implements JsonString {

    private final Value value;

    public GraalsonString(Value value) {
        this.value = value;
    }

    @Override
    public String getString() {
        return value.asString();
    }

    @Override
    public CharSequence getChars() {
        return value.asString();
    }

    @Override
    public ValueType getValueType() {
        return ValueType.STRING;
    }

}
