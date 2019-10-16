package org.pc.reflection;

import org.pc.reflection.exception.ReflectionException;
import org.pc.reflection.invoker.GetFieldInvoker;
import org.pc.reflection.invoker.Invoker;
import org.pc.reflection.invoker.MethodInvoker;
import org.pc.reflection.invoker.SetFieldInvoker;
import org.pc.reflection.property.PropertyName;

import java.lang.reflect.*;
import java.util.*;

/**
 * 类元信息的封装
 */
public class Reflector {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    /**
     * 对应的类
     */
    private Class<?> type;
    /**
     * 属性的 get()/set() 方法分别对应
     */
    private String[] readablePropertyNames = EMPTY_STRING_ARRAY;
    private String[] writablePropertyNames = EMPTY_STRING_ARRAY;
    private Map<String, Invoker> getMethods = new HashMap<>();
    private Map<String, Invoker> setMethods = new HashMap<>();
    private Map<String, Class<?>> getTypes = new HashMap<>();
    private Map<String, Class<?>> setTypes = new HashMap<>();
    private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();
    /**
     * 类默认构造方法
     */
    private Constructor<?> defaultConstructor;

    /**
     * 构造函数，将类包装成 Reflector
     * @param clazz 待包装类
     */
    public Reflector(Class<?> clazz) {
        type = clazz;
        addDefaultConstructor(clazz);
        //全程解说
        addGetMethods(clazz);
        //参照上面
        addSetMethods(clazz);
        //处理没有 getter/setter 方法的属性
        addFields(clazz);
        //根据 getMethods/setMethods 集合，初始化可读/写属性的名称集合
        readablePropertyNames = getMethods.keySet().toArray(new String[0]);
        writablePropertyNames = setMethods.keySet().toArray(new String[0]);

        //初始化 caseInsensitivePropertyMap 集合，其中记录了所有大写格式的属性名称
        for (String propertyName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propertyName.toUpperCase(Locale.ENGLISH), propertyName);
        }
        for (String propertyName : writablePropertyNames) {
            caseInsensitivePropertyMap.put(propertyName.toUpperCase(Locale.ENGLISH), propertyName);
        }
    }

    private void addFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (canAccessPrivateMethods()) {
                try {
                    field.setAccessible(true);
                } catch (Exception e) {
                    //todo:不做处理
                }
            }
            if (field.isAccessible()) {
                //若是属性，但是没有 setter 方法
                if (!setMethods.containsKey(field.getName())) {
                    //获取属性的修饰符，例如：public protected private static final transient volatile 等
                    int modifiers = field.getModifiers();
                    //过滤掉诸如 private static final String ***=** 这样的常量属性
                    if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
                        addSetField(field);
                    }
                }
                //若是属性，但是没有 getter 方法
                if (!getMethods.containsKey(field.getName())) {
                    addGetField(field);
                }
            }
        }
        //处理父类中的属性
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());
        }
    }

    private void addGetField(Field field) {
        if (PropertyName.isValidPropertyName(field.getName())) {
            //若 field 没有 getter 方法，则可以包装成 GetFieldInvoker，通过它来获取属性值
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFiledType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addSetField(Field field) {
        if (PropertyName.isValidPropertyName(field.getName())) {
            //若 field 没有 setter 方法，则可以包装成 SetFieldInvoker，通过它来设置属性值
            setMethods.put(field.getName(), new SetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFiledType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addSetMethods(Class<?> clazz) {
        Map<String, List<Method>> conflictingSetters = new HashMap<>();
        Method[] methods = getClassMethods(clazz);
        for (Method method : methods) {
            String methodName = method.getName();
            if ((methodName.startsWith("set") && methodName.length() > 3)
                    && (method.getParameterTypes().length == 1)) {
                methodName = PropertyName.methodToProperty(methodName);
                conflictingSetters.computeIfAbsent(methodName, k -> new ArrayList<>()).add(method);
            }
        }
        resolveSetterConflicts(conflictingSetters);
    }

    /**
     * 问题：为什么 setter 也会出现冲突，毕竟没有返回值类型？
     * 原因：
     *      主要是因为泛型的存在：
     *      public void setPrice(T price);
     *      public void setPrice(Double price);
     * 显然，遇到此类情况，子类中的方法才是我们需要的，哪个是子类，我们就获取哪一个。
     */
    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        for (String propertyName : conflictingSetters.keySet()) {
            List<Method> setters = conflictingSetters.get(propertyName);
            //找到该 propertyName 对应的 getter 方法，获取返回类型，从而获取 setter 方法的参数类型
            Class<?> getterType = getTypes.get(propertyName);
            Method match = null;
            ReflectionException exception = null;
            for (Method setter : setters) {
                Class<?> parameterType = setter.getParameterTypes()[0];
                //这是最符合要求的，停止寻找
                if (getterType.equals(parameterType)) {
                    match = setter;
                    break;
                }
                if (exception == null) {
                    try {
                        //这里只能找到相对符合的，不是最符合的
                        match = pickBetterSetter(propertyName, match, setter);
                    } catch (ReflectionException e) {
                        match = null;
                        exception = e;
                    }
                }
            }
            if (match == null) {
                throw exception;
            } else {
                addSetMethod(propertyName, match);
            }
        }
    }

    private Method pickBetterSetter(String propertyName, Method match, Method setter) {
        if (match == null) {
            return setter;
        }
        Class<?> parameterType1 = match.getParameterTypes()[0];
        Class<?> parameterType2 = setter.getParameterTypes()[0];
        if (parameterType1.isAssignableFrom(parameterType2)) {
            return setter;
        } else if (parameterType2.isAssignableFrom(parameterType1)) {
            return match;
        }
        //如果对于一个方法签名，存在的多个 setter 方法的参数不存在互为父子类的情况，说明方法定义有误
        throw new ReflectionException("Ambiguous setters defined for property '" + propertyName + "' in class '"
                + setter.getDeclaringClass() + "' with types '" + match.getName() + "' and '"
                + setter.getName() + "'.");
    }


    private void addSetMethod(String propertyName, Method method) {
        if (PropertyName.isValidPropertyName(propertyName)) {
            setMethods.put(propertyName, new MethodInvoker(method));
            //方法可能存在多个参数，每个都要解析出来
            Type[] paramTypes = TypeParameterResolver.resolveParamType(method, type);
            //JavaBean 规范，setter 方法只有一个参数，第一个参数即可
            setTypes.put(propertyName, typeToClass(paramTypes[0]));
        }
    }

    private void addGetMethods(Class<?> clazz) {
        //因为可能子类会覆盖父类方法，所以，相同的方法可能存在多个，键就是方法的签名，后面会说格式
        Map<String, List<Method>> conflictingGetters = new HashMap<>();
        Method[] methods = getClassMethods(clazz);
        for (Method method : methods) {
            String methodName = method.getName();
            if ((methodName.startsWith("get") && methodName.length() > 3) ||
                    (methodName.startsWith("is") && methodName.length() > 2)) {
                //JavaBean 规范，属性的 getter() 方法不容许有参数
                if (method.getParameterTypes().length == 0) {
                    //从属性的 getter 方法中推导出属性名
                    methodName = PropertyName.methodToProperty(methodName);
                    //将所有方法放入 Map 集合中
                    conflictingGetters.computeIfAbsent(methodName, k -> new ArrayList<>()).add(method);
                }
            }
        }
        /*
         *     上面得到的 methods 已经去掉了一些重复的方法：子类覆盖父类的方法。但是若子类在覆盖父类的方法时，
         * 返回值是父类返回值的子类，比如：
         *     父类：public Object getValue();
         *     子类：public String getValue();
         *      那么这样的方法也会留在 methods 中，我们接下来就是要解决这些重复方法。原则就是返回值是子类的
         * 留下。
         */
        resolveGetterConflicts(conflictingGetters);
    }

    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (String propertyName : conflictingGetters.keySet()) {
            List<Method> getters = conflictingGetters.get(propertyName);
            Iterator<Method> iterator = getters.iterator();
            Method firstMethod = iterator.next();
            if (getters.size() == 1) {
                addGetMethod(propertyName, firstMethod);
            } else {
                Method getterMethod = firstMethod;
                Class<?> getterReturnType = firstMethod.getReturnType();
                while (iterator.hasNext()) {
                    Method method = iterator.next();
                    Class<?> methodReturnType = method.getReturnType();
                    if (getterReturnType.isAssignableFrom(methodReturnType)) {
                        //getterType若是父类，methodReturnType是子类，则替换成子类
                        getterMethod = method;
                        getterReturnType = methodReturnType;
                    } else if (methodReturnType.isAssignableFrom(getterReturnType)) {
                        //getterType若是子类，则啥都不做
                    } else {
                        throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property "
                                + propertyName + "in class " + firstMethod.getDeclaringClass() + ". This breaks the JavaBeans" +
                                "specification and can cause unpredictable results");
                    }
                }
                addGetMethod(propertyName, getterMethod);
            }
        }
    }

    private void addGetMethod(String propertyName, Method method) {
        if (PropertyName.isValidPropertyName(propertyName)) {
            //将 Method 包装成 Invoker 对象
            getMethods.put(propertyName, new MethodInvoker(method));
            /*
             * 问题：这里为什么需要对方法的返回类型进行再次处理？
             * 回答：因为存在泛型（Map<K, V> 或 List<String> 或 String），而对于泛型，不能直接返回，而是需要进行再处理，
             * 这样才能得到真正的返回值类型。
             * 案例：
             *  class ClassA<Long> extends ClassB<Long, Long>{....}，遍历获取ClassA所有的方法，包括父类 ClassB<K, V>
             *  的方法，而对于 ClassB<K, V> 中的一些方法，它的返回值可能是泛型，比如：public List<V> getValues()，对于
             *  这种泛型返回值 List<V>，我们需要获取真实的类型，比如这里，最终目标要转换成 List<Long>。下面的 type 就是
             *  ClassA<Long>.class 对象
             */
            Type returnType = TypeParameterResolver.resolveReturnType(method, type);
            /*
             *     在 TypeParameterResolver#resolveType() 方法中对泛型进行解析时，TypeVariable、WildcardType
             * 将会被直接解析成具体的类，比如：String.class等，而 ParameterizedType（Service<User>） 和
             * GenericArrayType则继续被封装，我们需要将真实的类型取出来，比如：Service<User> --> Service，也就是
             * 这个方法的目的
             */
            getTypes.put(propertyName, typeToClass(returnType));
        }
    }

    /**
     *     在 TypeParameterResolver#resolveType() 方法中对泛型进行解析时，TypeVariable、WildcardType
     * 将会被直接解析成具体的类，比如：String.class等，而 ParameterizedType（Service<User>） 和
     * GenericArrayType则继续被封装，我们需要将真实的类型取出来，比如：Service<User> --> Service，也就是
     * 这个方法的目的
     */
    private Class<?> typeToClass(Type src) {

        Class<?> result = null;
        if (src instanceof Class) {
            result = (Class<?>) src;
        } else if (src instanceof ParameterizedType) {
            result = (Class<?>) ((ParameterizedType) src).getRawType();
        } else if (src instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) {
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                //存在类型嵌套，因为 GenericComponentType 是由 TypeVariable 和 ParameterizedType 组成，所以需要继续解析
                Class<?> componentClass = typeToClass(componentType);
                result = Array.newInstance(componentClass, 0).getClass();
            }
        }
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    private Method[] getClassMethods(Class<?> clazz) {
        Map<String, Method> uniqueMethods = new HashMap<>();
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            //当前类所有的方法
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());
            //获取当前类所实现的所有接口
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getDeclaredMethods());
            }
            //获取父类，继续迭代
            currentClass = currentClass.getSuperclass();
        }
        return uniqueMethods.values().toArray(new Method[0]);
    }

    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method method : methods) {
            if (!method.isBridge()) {
                //获取方法签名
                String signature = getSignature(method);
                //因为是从子类向父类开始迭代，若已包含该键，说明子类已经覆盖了该方法，不需要再加入
                if (!uniqueMethods.containsKey(signature)) {
                    if (canAccessPrivateMethods()) {
                        try {
                            method.setAccessible(true);
                        } catch (Exception e) {
                            //todo:啥都不做
                        }
                    }
                    uniqueMethods.put(signature, method);
                }
            }
        }
    }
    /**
     * 方法签名：返回值类型#方法名：参数1,参数2,参数3
     */
    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?> returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getName()).append("#");
        }
        sb.append(method.getName());
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i == 0) {
                sb.append(":");
            } else {
                sb.append(",");
            }
            sb.append(parameterTypes[i].getName());
        }
        return sb.toString();
    }
    private void addDefaultConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor constructor : constructors) {
            if (constructor.getParameterTypes().length == 0) {
                if (canAccessPrivateMethods()) {
                    try {
                        constructor.setAccessible(true);
                    } catch (Exception e) {
                        //todo:啥都做不了
                    }
                }
                if (constructor.isAccessible()) {
                    defaultConstructor = constructor;
                }
            }
        }
    }

    /**
     * 这部分代码功能和 AccessibleObject#setAccessible() 功能是一样的，好像没有必要写？？
     * Constructor、Field 和 Method 继承自 AccessibleObject
     */
    private static boolean canAccessPrivateMethods() {
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (securityManager != null) {
                /*
                 *     ReflectPermission 是一种指定权限，没有动作。当前定义的唯一名称是suppressAccessChecks，它允许取消
                 * 由反射对象在其使用点上执行的标准 Java 语言访问检查 - 对于 public、default（包）访问、protected、
                 * private 成员。
                 */
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    public Class<?> getType() {
        return type;
    }

    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    public String[] getSetablePropertyNames() {
        return writablePropertyNames;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        } else {
            throw new ReflectionException("There is no default constructor for " + type);
        }
    }
    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }
    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named " + propertyName);
        }
        return method;
    }
    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named " + propertyName);
        }
        return method;
    }

    public Class<?> getGetterTypes(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named " + propertyName);
        }
        return clazz;
    }
    public Class<?> getSetterTypes(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named " + propertyName);
        }
        return clazz;
    }
    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }
    public boolean hasGetter(String propertyName) {
        return getMethods.keySet().contains(propertyName);
    }
    public boolean hasSetter(String propertyName) {
        return setMethods.keySet().contains(propertyName);
    }
}
