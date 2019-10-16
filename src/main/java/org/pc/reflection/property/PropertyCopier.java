package org.pc.reflection.property;

import java.lang.reflect.Field;

public final class PropertyCopier {
    private  PropertyCopier() {}
    public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
        Class<?> parent = type;
        while (parent != null) {
            Field[] fields = parent.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    field.set(destinationBean, field.get(sourceBean));
                } catch (IllegalAccessException e) {
                    //todo:不做任何处理
                }
            }
            parent = parent.getSuperclass();
        }
    }
}
