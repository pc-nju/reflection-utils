package org.pc.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * 对 Field 和 Method 包装的顶级抽象类
 */
public interface Invoker {
    Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;
    Class<?> getType();
}
