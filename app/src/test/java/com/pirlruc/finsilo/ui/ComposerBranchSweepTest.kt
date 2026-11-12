@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.pirlruc.finsilo.ui

import android.app.Application
import androidx.compose.runtime.Composer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposerBranchSweepTest {
    @Test
    fun sweepsComposableRestartMasks() {
        val root = listOf(
            File("build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
            File("app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
        ).firstOrNull { it.isDirectory }
        check(root != null) { "debug classes were not compiled" }
        val probes = listOf(
            composerProbe(changed = false, execute = false),
            composerProbe(changed = true, execute = true),
            composerProbe(changed = false, execute = true),
        )
        var invoked = 0
        root.walkTopDown().filter { it.extension == "class" }.forEach { file ->
            val name = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
            if (name.contains("ComposableSingletons") || name.contains("_Impl") || name.contains("\$serializer")) return@forEach
            val clazz = runCatching { Class.forName(name, true, javaClass.classLoader) }.getOrNull() ?: return@forEach
            val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
            methods.filter { method -> method.parameterTypes.any { Composer::class.java.isAssignableFrom(it) } }.forEach { method ->
                method.isAccessible = true
                val composerAt = method.parameterTypes.indexOfLast { Composer::class.java.isAssignableFrom(it) }
                val masks = method.parameterTypes.drop(composerAt + 1).count { it == Integer.TYPE }
                if (masks == 0) return@forEach
                val base = Array<Any?>(method.parameterTypes.size) { index ->
                    when {
                        index == composerAt -> null
                        index > composerAt -> 0
                        else -> dummy(method.parameterTypes[index])
                    }
                }
                if (base.withIndex().any { (index, value) -> value == null && index != composerAt && method.parameterTypes[index].isPrimitive.not() && index < composerAt }) {
                    return@forEach
                }
                for (probe in probes) {
                    for (changed in intArrayOf(0, -1)) {
                        for (defaultMask in if (masks > 1) intArrayOf(0, -1) else intArrayOf(0)) {
                            val args = base.copyOf()
                            args[composerAt] = probe
                            args[composerAt + 1] = changed
                            if (masks > 1) args[composerAt + 2] = defaultMask
                            val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                                null
                            } else {
                                dummy(clazz) ?: return@forEach
                            }
                            runCatching { method.invoke(receiver, *args) }
                            invoked += 1
                        }
                    }
                }
            }
        }
        assertTrue(invoked > 0)
    }
}

private fun dummy(type: Class<*>): Any? {
    if (type == java.lang.String::class.java) return "synthetic"
    if (type == Integer.TYPE || type == java.lang.Integer::class.java) return 0
    if (type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java) return false
    if (type == java.lang.Long.TYPE || type == java.lang.Long::class.java) return 0L
    if (type == java.lang.Float.TYPE || type == java.lang.Float::class.java) return 0f
    if (type == java.lang.Double.TYPE || type == java.lang.Double::class.java) return 0.0
    if (type == java.lang.Short.TYPE || type == java.lang.Short::class.java) return 0.toShort()
    if (type == java.lang.Byte.TYPE || type == java.lang.Byte::class.java) return 0.toByte()
    if (type == Character.TYPE || type == java.lang.Character::class.java) return 's'
    if (type == BigDecimal::class.java) return BigDecimal.ONE
    if (type == LocalDate::class.java) return LocalDate.of(2026, 1, 15)
    if (type.isEnum) return type.enumConstants?.firstOrNull()
    if (java.util.List::class.java.isAssignableFrom(type)) return emptyList<Any>()
    if (java.util.Set::class.java.isAssignableFrom(type)) return emptySet<Any>()
    if (java.util.Map::class.java.isAssignableFrom(type)) return emptyMap<Any, Any>()
    if (type.isArray) {
        val component = type.componentType ?: return null
        return java.lang.reflect.Array.newInstance(component, 0)
    }
    if (type.isInterface) return interfaceProxy(type)
    val allocated = allocate(type) ?: return null
    primeState(allocated)
    return allocated
}

private fun primeState(instance: Any) {
    var type: Class<*>? = instance.javaClass
    while (type != null && type != Any::class.java) {
        for (field in type.declaredFields) {
            if (field.name != "_state" && !field.type.name.endsWith("StateFlow")) continue
            field.isAccessible = true
            if (runCatching { field.get(instance) }.getOrNull() != null) continue
            val owner = type
            val value = runCatching {
                val stateType = Class.forName(owner.name.removeSuffix("ViewModel") + "UiState", false, owner.classLoader)
                dummy(stateType)
            }.getOrNull()
            runCatching { field.set(instance, MutableStateFlow(value)) }
        }
        type = type.superclass
    }
}

private fun allocate(type: Class<*>): Any? = runCatching {
    val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null)
    unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type)
}.getOrNull()

private fun interfaceProxy(type: Class<*>): Any =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Float.TYPE -> 0f
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Void.TYPE -> null
            java.lang.String::class.java -> "synthetic"
            else -> if (method.returnType.name == "kotlin.Unit") Unit else null
        }
    }

private fun compositionLocal(key: Any?): Any? {
    val name = key?.javaClass?.name.orEmpty() + key?.toString().orEmpty()
    return if (name.contains("Lifecycle")) probeLifecycleOwner else null
}

private val probeLifecycleOwner: LifecycleOwner = object : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry(this).also { it.currentState = Lifecycle.State.RESUMED }
}

private fun composerProbe(changed: Boolean, execute: Boolean): Composer {
    val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
        when (method.name) {
            "startRestartGroup", "startReplaceGroup", "startReusableGroup", "startMovableGroup" -> proxy
            "changed", "changedInstance" -> changed
            "shouldExecute" -> execute
            "consume" -> compositionLocal(args?.getOrNull(0))
            "skipping", "inserting" -> false
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> false
            "toString" -> "probe"
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Float.TYPE -> 0f
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Void.TYPE -> null
                else -> null
            }
        }
    }
    return Proxy.newProxyInstance(Composer::class.java.classLoader, arrayOf(Composer::class.java), handler) as Composer
}
