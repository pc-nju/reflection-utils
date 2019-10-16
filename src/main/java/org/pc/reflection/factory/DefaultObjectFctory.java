package org.pc.reflection.factory;

import org.pc.reflection.exception.ReflectionException;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * 创建指定类型的对象
 */
public class DefaultObjectFctory implements ObjectFctory, Serializable {
    @Override
    public void setProperties(Properties properties) {

    }

    @Override
    public <T> T create(Class<T> type) {
        return create(type, null, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        Class<?> classToCreate = resolveInterface(type);
        return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
    }

    @Override
    public <T> boolean isCollection(Class<T> type) {
        return Collection.class.isAssignableFrom(type);
    }

    private Class<?> resolveInterface(Class<?> type) {
        Class<?> classToCreate;
        if (type == List.class || type == Collection.class || type == Iterable.class) {
            classToCreate = ArrayList.class;
        } else if (type == Map.class) {
            classToCreate = HashMap.class;
        } else if (type == SortedSet.class) {
            classToCreate = TreeSet.class;
        } else if (type == Set.class) {
            classToCreate = HashSet.class;
        } else {
            classToCreate = type;
        }
        return classToCreate;
    }

    private <T> T instantiateClass(Class<T> clazz, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        try {
            Constructor<T> constructor;
            //无参构造函数
            if (constructorArgTypes == null || constructorArgs == null) {
                constructor = clazz.getDeclaredConstructor();
                if (!constructor.isAccessible()) {
                    constructor.setAccessible(true);
                }
                return constructor.newInstance();
            }
            //有参构造函数
            constructor = clazz.getDeclaredConstructor(constructorArgTypes.toArray(new Class[0]));
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance(constructorArgs.toArray(new Object[0]));
        } catch (Exception e) {
            String argTypes = list2Array(constructorArgTypes);
            String args = list2Array(constructorArgs);
            throw new ReflectionException("Error instantiating " + clazz + " with invalid types (" + argTypes + ")"
            + " or values (" + args + ")");
        }
    }
    private <T> String list2Array(List<T> list) {
        StringBuilder sb = new StringBuilder();
        if (list != null && !list.isEmpty()) {
            for (T t : list) {
                sb.append(t);
                sb.append(",");
            }
            sb.deleteCharAt(list.size() - 1);
        }
        return sb.toString();
    }
}
