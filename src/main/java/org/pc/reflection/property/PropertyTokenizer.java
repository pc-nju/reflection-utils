package org.pc.reflection.property;

import java.util.Iterator;

public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
    //当前表达式的名称
    private String name;
    //当前表达式的索引名
    private String indexedName;
    //索引下标
    private String index;
    //子表达式
    private String children;

    public PropertyTokenizer(String fullName) {
        int deli = fullName.indexOf(".");
        if (deli > -1) {
            name = fullName.substring(0, deli);
            children = fullName.substring(deli + 1);
        } else {
            name = fullName;
            children = null;
        }
        indexedName = name;
        deli = name.indexOf("[");
        if (deli > -1) {
            index = name.substring(deli + 1, name.length() - 1);
            name = name.substring(0, deli);
        }
    }

    public String getName() {
        return name;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getIndex() {
        return index;
    }

    public String getChildren() {
        return children;
    }

    @Override
    public boolean hasNext() {
        return children != null;
    }

    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
    }
}
