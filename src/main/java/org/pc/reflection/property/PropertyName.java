package org.pc.reflection.property;

import org.pc.reflection.exception.ReflectionException;

import java.util.Locale;

public final class PropertyName {
    private boolean isFinished;
    private PropertyName() {}

    /**
     * 从 setter()/getter() 方法名中推导出属性名
     * @param methodName 方法名
     * @return 属性名
     */
    public static String methodToProperty(String methodName) {
        if (methodName.startsWith("is")) {
            methodName = methodName.substring(2);
        } else if (methodName.startsWith("get") || methodName.startsWith("set")) {
            methodName = methodName.substring(3);
        } else {
            throw new ReflectionException("Error parsing property name " + methodName + ". Didn't start with 'is', 'get' or 'set'");
        }
        //todo:为啥源码中写的是 !Character.isUpperCase(name.charAt(1)) ，不明白
        if (methodName.length() == 1 || (methodName.length() > 1 && Character.isUpperCase(methodName.charAt(1)))) {
            methodName = methodName.substring(0, 1).toLowerCase(Locale.ENGLISH) + methodName.substring(1);
        }
        return methodName;
    }

    /**
     * 判断属性名是否合法
     * @param propertyName 属性名
     * @return  true：合法,false：不合法
     */
    public static boolean isValidPropertyName(String propertyName) {
        return !(propertyName.startsWith("$") || "serialVersionUID".equals(propertyName) || "class".equals(propertyName));
    }
    //检测方法名是否对应属性名
    public static boolean isProperty(String name) {
        return name.startsWith("get") || name.startsWith("set") || name.startsWith("is");
    }
    //检测 getter 方法
    public static boolean isGetter(String name) {
        return name.startsWith("get") || name.startsWith("is");
    }
    //检测 setter 方法
    public static boolean isSetter(String name) {
        return name.startsWith("set");
    }

}
