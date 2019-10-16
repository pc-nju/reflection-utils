package org.pc.reflection.type;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

public class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;
    private Type[] upperBounds;

    public WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
        this.lowerBounds = lowerBounds;
        this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getUpperBounds() {
        return upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
        return lowerBounds;
    }
}
