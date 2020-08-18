package io.github.nickacpt.kodestripping.commands

import net.bytebuddy.utility.OpenedClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class UnwantedStuffRemoverClassVisitor(private val removeNonPublic: Boolean, classVisitor: ClassVisitor?) :
    ClassVisitor(OpenedClassReader.ASM_API, classVisitor) {
    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        if (shouldRemoveBecauseOfPublicAccess(access) || name == "<clinit>" || isUnwantedBridgeOrSynthetic(access))
            return null
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    private fun shouldRemoveBecauseOfPublicAccess(access: Int): Boolean {
        val isNotPublic = (access and Opcodes.ACC_PUBLIC) == 0
        return removeNonPublic && isNotPublic
    }

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        if (shouldRemoveBecauseOfPublicAccess(access) || isUnwantedBridgeOrSynthetic(access)) return null
        return super.visitField(access, name, descriptor, signature, value)
    }

    private fun isUnwantedBridgeOrSynthetic(access: Int): Boolean {
        return (access and Opcodes.ACC_BRIDGE) != 0 || (access and Opcodes.ACC_SYNTHETIC) != 0
    }
}