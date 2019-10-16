package org.pc.reflection.factory;

import org.pc.reflection.Reflector;

/**
 * 主要负责 Reflector 对象的创建和缓存
 */
public interface ReflectorFactory {
    //检测 ReflectorFactory 对象是否会缓存 Reflector 对象
    boolean isClassCachedEnabled();
    //设置是否缓存
    void setClassCacheEnabled(boolean classCachedEnabled);
    //缓存中查找 Class 对应的 Reflector 对象，找不到则创建
    Reflector findForClass(Class<?> type);
}
