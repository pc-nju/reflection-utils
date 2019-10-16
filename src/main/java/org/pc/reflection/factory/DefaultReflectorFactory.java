package org.pc.reflection.factory;

import org.pc.reflection.Reflector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultReflectorFactory implements ReflectorFactory {
    //是否开启对 Reflector 对象的缓存
    private boolean classCacheEnabled = true;
    private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

    public DefaultReflectorFactory() {
    }

    @Override
    public boolean isClassCachedEnabled() {
        return classCacheEnabled;
    }

    @Override
    public void setClassCacheEnabled(boolean classCachedEnabled) {
        this.classCacheEnabled = classCachedEnabled;
    }

    @Override
    public Reflector findForClass(Class<?> type) {
        if (classCacheEnabled) {
            Reflector cacheReflector = reflectorMap.get(type);
            if (cacheReflector == null) {
                cacheReflector = new Reflector(type);
                reflectorMap.put(type, cacheReflector);
            }
            return cacheReflector;
        } else {
            return new Reflector(type);
        }
    }
}
