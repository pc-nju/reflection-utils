package org.pc.reflection.factory;

import java.util.List;
import java.util.Properties;

public interface ObjectFctory {
    //设置配置信息
    void setProperties(Properties properties);

    //通过无参构造器创建指定类的对象
    <T> T create(Class<T> type);

    //根据参数列表，从指定类型中选择合适的构造器创建对象
    <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

    //检测是否为集合，主要处理 Collection 及其子类
    <T> boolean isCollection(Class<T> type);
}
