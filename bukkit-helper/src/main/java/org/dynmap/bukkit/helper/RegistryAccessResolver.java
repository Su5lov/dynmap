package org.dynmap.bukkit.helper;

import java.lang.reflect.Method;

import net.minecraft.core.IRegistry;
import net.minecraft.server.MinecraftServer;

/**
 * Utility that resolves registry lookups using reflection so that changes in
 * the obfuscated method names across Minecraft releases (such as Paper 1.21.10)
 * do not break us.
 */
public final class RegistryAccessResolver {

    private static Method registryAccessMethod;
    private static Method registryLookupMethod;

    private RegistryAccessResolver() {
    }

    private static Object getRegistryAccess() {
        if (registryAccessMethod == null) {
            registryAccessMethod = findRegistryAccessMethod();
        }
        try {
            return registryAccessMethod.invoke(MinecraftServer.getServer());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access Minecraft registry", ex);
        }
    }

    private static Method findRegistryAccessMethod() {
        for (Method method : MinecraftServer.class.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            String name = returnType.getName();
            if (name.contains("RegistryAccess") || name.contains("IRegistryCustom")) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Unable to find registry access method on MinecraftServer");
    }

    private static Method findLookupMethod(Class<?> accessClass, Object resourceKey) {
        for (Method method : accessClass.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isInstance(resourceKey)) {
                continue;
            }
            if (!IRegistry.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        throw new IllegalStateException(
                "Unable to locate registry lookup method on " + accessClass.getName());
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> IRegistry<T> resolveRegistry(Object resourceKey) {
        Object access = getRegistryAccess();
        if (registryLookupMethod == null || registryLookupMethod.getDeclaringClass() != access.getClass()) {
            registryLookupMethod = findLookupMethod(access.getClass(), resourceKey);
        }
        try {
            return (IRegistry<T>) registryLookupMethod.invoke(access, resourceKey);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to resolve registry", ex);
        }
    }
}
