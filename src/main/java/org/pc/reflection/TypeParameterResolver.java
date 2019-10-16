package org.pc.reflection;

import org.pc.reflection.type.GenericArrayTypeImpl;
import org.pc.reflection.type.ParameterizedTypeImpl;
import org.pc.reflection.type.WildcardTypeImpl;

import java.lang.reflect.*;

public final class TypeParameterResolver {
    private TypeParameterResolver() {super();}

    public static Type resolveReturnType(Method method, Type srcType) {
        /*
         * 补充知识点：
         * Type 是所有类型的父接口，它有四个子接口和一个实现类：
         *     1、Class：原始类型
         *     2、ParameterizedType：表示参数化类型，例如：List<String>、Map<String, Integer>、Service<User>
         *                        它有三个常用方法：
         *                            Type getRawType()：返回参数化类型中的原始类型。例如 List<String> 原始类型为 List
         *                            Type[] getActualTypeArguments()：获取参数化类型的类型变量和实际类型列表。例如
         *                                                             Map<String, Integer> 的实际泛型列表是 {String,
         *                                                             Integer}。因为该列表的元素类型都是 Type，所以可能
         *                                                             存在多层嵌套的情况，比如 Map<String, List<String>>。
         *                            Type getOwnerType()：返回类型所属的类型。例如 Map<K, V> 接口与 Map.Entry<K, V> 接口，
         *                                                 Map<K, V> 是 Map.Entry<K, V> 的所有者。
         *     3、TypeVariable：表示的是类型变量，它用来反映在 JVM 编译该泛型前的信息。例如 List<T> 中的 T 就是类型变量。
         *                   它有三个常用方法：
         *                       Type[] getBounds()：获取类型变量的上边界，若未明确上边界则默认为 Object。例如
         *                                           Test<K extends Person> 中 K 的上界就是 Person。
         *                       D getGenericDeclaration()：获取声明该类型变量的原始类型，例如 Test<K extends Person> 中
         *                                                  的原始类型就是 Test.
         *                       String getName()：获取在源码中定义时的名字，Test<K extends Person> 中原始名字为 K
         *     4、GenericArrayType：表示数组类型且组成元素是 ParameterizedType 或 TypeVariable。例如 List<String>[] 或
         *                          T[]。该接口只有 Type getGenericComponentType() 一个方法，它返回数组的组成元素。例如
         *                          T[] 则返回 T。
         *     5、WildcardType：表示的时通配符泛型，例如 ? extends Number 和 ? super Integer.
         *                      它有两个常用方法：
         *                          Type[] getUpperBounds()：返回泛型变量的上界
         *                          Type[] getLowerBounds()：返回泛型变量的下界
         */
        // method.getGenericReturnType()：返回类型为 Type 对象 || method.getReturnType()：返回类型为 Class<?> 对象
        Type returnType = method.getGenericReturnType();
        //获取声明该 method 的类（就是说这个类是在哪个方法中定义的）
        Class<?> declaringClass = method.getDeclaringClass();
        return resolveType(returnType, srcType, declaringClass);
    }

    private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
        //TypeVariable：例如 T
        if (type instanceof TypeVariable) {
            return resolveTypeVar((TypeVariable<?>)type, srcType, declaringClass);
        } else if (type instanceof ParameterizedType) {
            //ParameterizedType：例如 Map<K, V>
            return resolveParameterizedType((ParameterizedType)type, srcType, declaringClass);
        } else if (type instanceof GenericArrayType) {
            //GenericArrayType：例如 List<String>[] 或 T[]
            return resolveGenericArrayType((GenericArrayType)type, srcType, declaringClass);
        } else {
            /*
             * 若以上情况都不是，说明是 Class 类型。
             * 问题：为什么没有判断是 WildcardType 情况：? extends Number
             * 回答：因为字段、返回值、参数不可能直接定义成 WildcardType 类型，但可以嵌套在别的类型中
             */
            return type;
        }
    }

    private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
        Type componentType = genericArrayType.getGenericComponentType();
        Type resolveComponentType = null;
        // componentType 类型只能是 TypeVariable 或 ParameterizedType 或嵌套
        if (componentType instanceof TypeVariable) {
            resolveComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
        } else if (componentType instanceof ParameterizedType) {
            resolveComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
        } else if (componentType instanceof GenericArrayType) {
            resolveComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
        }
        if (resolveComponentType instanceof Class) {
            return Array.newInstance((Class<?>) resolveComponentType, 0).getClass();
        } else {
            return new GenericArrayTypeImpl(resolveComponentType);
        }
    }

    private static Type resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
        Class<?> rawType = (Class<?>)parameterizedType.getRawType();
        Type[] typeArgs = parameterizedType.getActualTypeArguments();
        Type[] args = getTypes(typeArgs, srcType, declaringClass);
        return new ParameterizedTypeImpl(rawType, null, args);
    }

    private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
        Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
        Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
        return new WildcardTypeImpl(lowerBounds, upperBounds);
    }

    private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
        return getTypes(bounds, srcType, declaringClass);
    }

    private static Type[] getTypes(Type[] arr, Type srcType, Class<?> declaringClass) {
        Type[] results = new Type[arr.length];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] instanceof TypeVariable) {
                results[i] = resolveTypeVar((TypeVariable<?>) arr[i], srcType, declaringClass);
            } else if (arr[i] instanceof ParameterizedType) {
                results[i] = resolveParameterizedType((ParameterizedType) arr[i], srcType, declaringClass);
            } else if (arr[i] instanceof WildcardType) {
                results[i] = resolveWildcardType((WildcardType) arr[i], srcType, declaringClass);
            } else {
                results[i] = arr[i];
            }
        }
        return results;
    }

    private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
        Type result;
        Class<?> clazz;
        /*
         * srcType 就是定义 Method 或 Field 方法的类，以解析方法的返回值为例：
         *     假设方法的定义为 T getName()，那么定义该方法的类在声明是，必须是 class Test<T> 或 class Test 等形式，
         * 那么 srcType 只会有 Class 和 ParameterizedType 这两种
         */
        if (srcType instanceof Class) {
            clazz = (Class<?>) srcType;
        } else if (srcType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) srcType;
            clazz = (Class<?>) parameterizedType.getRawType();
        } else {
            throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType，but was: " +
                    srcType.getTypeName());
        }
        //若 srcType 源类型 和 方法所在声明类 一致
        if (clazz == declaringClass) {
            Type[] bounds = typeVar.getBounds();
            return bounds.length > 0 ? bounds[0] : Object.class;
        }
        /*
         *     若 srcType 源类型 和 方法所在声明类 不一致，则继续寻找，最终要寻找到 declaringClass 直接关联的
         * 子类，比如：SubClassB<Long> extends SubClassA<Long, Long>，SubClassA<K, V> extends ClassA<K, V>，
         * 方法是在 ClassA<K, V> 中定义的，那么这里就可以根据 SubClassB<Long> 推导出 SubClassA<Long, Long>，
         * 再推导出 ClassA<Long, Long>。declaringClass = ClassA<K, V>，所以我们只要推导出 SubClassA<Long, Long>
         * 即可。
         */
        //通过扫描父类型继续解析，这是递归的入口
        Type superclassType = clazz.getGenericSuperclass();
        result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclassType);
        if (result != null) {
            return result;
        }

        Type[] superInterfaceTypes = clazz.getGenericInterfaces();
        for (Type superInterfaceType : superInterfaceTypes) {
            result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterfaceType);
            if (result != null) {
                return result;
            }
        }
        return Object.class;
    }
    /*
     *     若 srcType 源类型 和 方法所在声明类 不一致，则继续寻找，最终要寻找到 declaringClass 直接关联的
     * 子类，比如：SubClassB<Long> extends SubClassA<Long, Long>，SubClassA<K, V> extends ClassA<K, V>，
     * 方法是在 ClassA<K, V> 中定义的，那么这里就可以根据 SubClassB<Long> 推导出 SubClassA<Long, Long>，
     * 再推导出 ClassA<Long, Long>。declaringClass = ClassA<K, V>，所以我们只要推导出 SubClassA<Long, Long>
     * 即可。
     */
    private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclassType) {
        Type result = null;
        /*
         * superclassType 就是定义 Method 或 Field 方法的类，以解析方法的返回值为例：
         *     假设方法的定义为 T getName()，那么定义该方法的类在声明是，必须是 class Test<T> 或 class Test 等形式，
         * 那么 srcType 只会有 Class 和 ParameterizedType 这两种
         */
        if (superclassType instanceof Class) {
            if (declaringClass.isAssignableFrom((Class<?>) superclassType)) {
                result = resolveTypeVar(typeVar, superclassType, declaringClass);
            }
        } else if (superclassType instanceof ParameterizedType) {
            ParameterizedType parentAsType = (ParameterizedType) superclassType;
            Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
            if (declaringClass == parentAsClass) {
                Type[] typeArgs = parentAsType.getActualTypeArguments();
                TypeVariable<?>[] declaredTypeVars = declaringClass.getTypeParameters();
                for (int i = 0; i < declaredTypeVars.length; i++) {
                    if (declaredTypeVars[i] == typeVar) {
                        if (typeArgs[i] instanceof TypeVariable) {
                            TypeVariable<?>[] typeParams = clazz.getTypeParameters();
                            for (int j = 0; j < typeParams.length; j++) {
                                if (typeParams[j] == typeArgs[i]) {
                                    if (srcType instanceof ParameterizedType) {
                                        result = ((ParameterizedType)srcType).getActualTypeArguments()[j];
                                    }
                                    break;
                                }
                            }
                        } else {
                            result = typeArgs[i];
                        }
                    }
                }
            } else if (declaringClass.isAssignableFrom(parentAsClass)) {
                result = resolveTypeVar(typeVar, parentAsType, declaringClass);
            }
        }
        return result;
    }

    public static Type[] resolveParamType(Method method, Class<?> srcType) {
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Class<?> declaringClass = method.getDeclaringClass();
        Type[] results = new Type[genericParameterTypes.length];
        for (int i = 0; i < genericParameterTypes.length; i++) {
            results[i] = resolveType(genericParameterTypes[i], srcType, declaringClass);
        }
        return results;
    }

    public static Type resolveFiledType(Field field, Class<?> srcType) {
        //获取字段的声明类型
        Type fieldGenericType = field.getGenericType();
        //获取字段定义所在的类的 Class 对象
        Class<?> declaringClass = field.getDeclaringClass();
        return resolveType(fieldGenericType, srcType, declaringClass);
    }
}
