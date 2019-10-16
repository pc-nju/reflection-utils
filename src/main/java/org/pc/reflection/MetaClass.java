package org.pc.reflection;

import org.pc.reflection.factory.ReflectorFactory;

/**
 *     Reflector 实现了实体类元信息的封装，但是类中的成员变量是类的情况没有进行处理。
 *     而 MetaClass 通过 ReflectorFactory 类型的成员变量，实现了实体类中成员变量是类情况的处理，通过与属性工具类的结合，
 * 实现了对复杂表达式的解析和实现了获取指定描述信息的功能。
 */
public class MetaClass {
    private ReflectorFactory reflectorFactory;
    private Reflector reflector;

    private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
        this.reflectorFactory = reflectorFactory;
        reflector = reflectorFactory.findForClass(type);
    }

    public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
        return new MetaClass(type, reflectorFactory);
    }

    public MetaClass metaClassForProperty(String name) {
        
    }
}
