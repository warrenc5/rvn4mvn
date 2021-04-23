/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rvn.graalson;

import java.math.BigDecimal;
import java.math.BigInteger;
import javax.json.JsonNumber;
import org.graalvm.polyglot.Value;

/**
 *
 * @author wozza
 */
public class GraalsonNumber implements JsonNumber {

    BigDecimal value;

    public GraalsonNumber(Value value) {
        this.value = BigDecimal.valueOf(value.asInt());
    }

    @Override
    public boolean isIntegral() {
        return value.scale() == 0;
    }

    @Override
    public BigInteger bigIntegerValue() {
        return value.toBigInteger();
    }

    @Override
    public BigInteger bigIntegerValueExact() {
        return value.toBigIntegerExact();
    }

    @Override
    public BigDecimal bigDecimalValue() {
        return value;
    }

    @Override
    public ValueType getValueType() {
        return ValueType.NUMBER;
    }

    @Override
    public int intValue() {
        return value.intValue();
    }

    @Override
    public int intValueExact() {
        return value.intValueExact();
    }

    @Override
    public long longValue() {
        return value.longValue();
    }

    @Override
    public long longValueExact() {
        return value.longValueExact();
    }

    @Override
    public double doubleValue() {
        return value.doubleValue();
    }

}
