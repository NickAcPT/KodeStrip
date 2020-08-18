package io.github.nickacpt.kodestripping.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import net.bytebuddy.ByteBuddy
import net.bytebuddy.asm.AsmVisitorWrapper
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.field.FieldList
import net.bytebuddy.description.method.MethodList
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.StubMethod
import net.bytebuddy.implementation.attribute.AnnotationRetention
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.pool.TypePool
import org.objectweb.asm.ClassVisitor
import java.util.jar.JarEntry
import java.util.jar.JarFile

class KodeStrip : CliktCommand("Strips the code from a jar file") {
    val file by argument("file").file(true, canBeDir = false)
    val output by argument("output").file(false, canBeDir = false)
    val classpath by option("--classpath").file(true, canBeDir = false).multiple()
    val removeNonPublic by option("--removeNonPublic").flag()

    override fun run() {
        val jarFile = JarFile(file)
        val jarFileLocator = ClassFileLocator.Compound(
            ClassFileLocator.ForJarFile(jarFile),
            ClassFileLocator.ForJarFile.ofClassPath(),
            ClassFileLocator.ForClassLoader.ofSystemLoader(),
            *classpath.map { ClassFileLocator.ForJarFile.of(it) }.toTypedArray()
        )
        val typePool = TypePool.Default.of(jarFileLocator)

        val bb = ByteBuddy()
            .with(TypeValidation.DISABLED)
            .with(AnnotationRetention.ENABLED)

        var finalType: DynamicType.Unloaded<*>? = null

        jarFile.entries().iterator().forEachRemaining { entry: JarEntry ->
            if (entry.name.endsWith(".class")) {
                val describe = typePool.describe(entry.name.replace("/", ".").removeSuffix(".class"))
                if (describe.isResolved) {
                    try {
                        val dynamicType = bb.redefine<Any>(describe.resolve(), jarFileLocator)
                            .invokable(ElementMatcher { true })
                            .intercept(StubMethod.INSTANCE)

                            .visit(object : AsmVisitorWrapper.AbstractBase() {
                                override fun wrap(
                                    instrumentedType: TypeDescription?,
                                    classVisitor: ClassVisitor?,
                                    implementationContext: Implementation.Context?,
                                    typePool: TypePool?,
                                    fields: FieldList<FieldDescription.InDefinedShape>?,
                                    methods: MethodList<*>?,
                                    writerFlags: Int,
                                    readerFlags: Int
                                ): ClassVisitor {
                                    return UnwantedStuffRemoverClassVisitor(removeNonPublic, classVisitor)
                                }

                            })
                            .make()

                        if (finalType == null)
                            finalType = dynamicType
                        else
                            finalType = finalType?.include(dynamicType)
                    } catch (e: Throwable) {
                        System.err.println("Unable to include ${entry.name}: ${e.message}")
                    }
                }
            }
        }

        echo("Finished processing the input file.")

        finalType?.toJar(output)
    }
}