package com.tilelayers;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
public final class TestSupport
{
    public static Map<String, Object> values(Object... pairs)
    {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    public static <T> T stub(Class<T> type, Map<String, Object> values)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (values.containsKey(method.getName())) return values.get(method.getName());
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        }));
    }

    public static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
