/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.generators.evaluate

import java.io.File
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.generators.di.GeneratorsFileUtil
import org.jetbrains.kotlin.utils.Printer
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.JetType

val DEST_FILE: File = File("compiler/frontend/src/org/jetbrains/kotlin/resolve/constants/evaluate/OperationsMapGenerated.kt")
private val EXCLUDED_FUNCTIONS = listOf("rangeTo", "hashCode", "inc", "dec", "subSequence")

fun main(args: Array<String>) {
    GeneratorsFileUtil.writeFileIfContentChanged(DEST_FILE, generate())
}

fun generate(): String {
    val sb = StringBuilder()
    val p = Printer(sb)
    p.println(File("license/LICENSE.txt").readText())
    p.println("package org.jetbrains.kotlin.resolve.constants.evaluate")
    p.println()
    p.println("import java.math.BigInteger")
    p.println("import java.util.HashMap")
    p.println()
    p.println("/** This file is generated by org.jetbrains.kotlin.generators.evaluate:generate(). DO NOT MODIFY MANUALLY */")
    p.println()

    val unaryOperationsMap = arrayListOf<Pair<String, List<JetType>>>()
    val binaryOperationsMap = arrayListOf<Pair<String, List<JetType>>>()

    val builtIns = KotlinBuiltIns.getInstance()
    [suppress("UNCHECKED_CAST")]
    val allPrimitiveTypes = builtIns.getBuiltInsPackageScope().getDescriptors()
            .filter { it is ClassDescriptor && KotlinBuiltIns.isPrimitiveType(it.getDefaultType()) } as List<ClassDescriptor>

    for (descriptor in allPrimitiveTypes + builtIns.getString()) {
        [suppress("UNCHECKED_CAST")]
        val functions = descriptor.getMemberScope(listOf()).getDescriptors()
                .filter { it is FunctionDescriptor && !EXCLUDED_FUNCTIONS.contains(it.getName().asString()) } as List<FunctionDescriptor>

        for (function in functions) {
            val parametersTypes = function.getParametersTypes()

            when (parametersTypes.size()) {
                1 -> unaryOperationsMap.add(function.getName().asString() to parametersTypes)
                2 -> binaryOperationsMap.add(function.getName().asString() to parametersTypes)
                else -> throw IllegalStateException("Couldn't add following method from builtins to operations map: ${function.getName()} in class ${descriptor.getName()}")
            }
        }
    }

    p.println("private val emptyBinaryFun: Function2<BigInteger, BigInteger, BigInteger> = { a, b -> BigInteger(\"0\") }")
    p.println("private val emptyUnaryFun: Function1<Long, Long> = { a -> 1.toLong() }")
    p.println()
    p.println("private val unaryOperations: HashMap<UnaryOperationKey<*>, Pair<Function1<Any?, Any>, Function1<Long, Long>>>")
    p.println("            = hashMapOf<UnaryOperationKey<*>, Pair<Function1<Any?, Any>, Function1<Long, Long>>>(")
    p.pushIndent()

    val unaryOperationsMapIterator = unaryOperationsMap.iterator()
    while (unaryOperationsMapIterator.hasNext()) {
        val (funcName, parameters) = unaryOperationsMapIterator.next()
        p.println(
                "unaryOperation(",
                parameters.map { it.asString() }.joinToString(", "),
                ", ",
                "\"$funcName\"",
                ", { a -> a.${funcName}() }, ",
                renderCheckUnaryOperation(funcName, parameters),
                ")",
                if (unaryOperationsMapIterator.hasNext()) "," else ""
        )
    }
    p.popIndent()
    p.println(")")

    p.println()

    p.println("private val binaryOperations: HashMap<BinaryOperationKey<*, *>, Pair<Function2<Any?, Any?, Any>, Function2<BigInteger, BigInteger, BigInteger>>>")
    p.println("            = hashMapOf<BinaryOperationKey<*, *>, Pair<Function2<Any?, Any?, Any>, Function2<BigInteger, BigInteger, BigInteger>>>(")
    p.pushIndent()

    val binaryOperationsMapIterator = binaryOperationsMap.iterator()
    while (binaryOperationsMapIterator.hasNext()) {
        val (funcName, parameters) = binaryOperationsMapIterator.next()
        p.println(
                "binaryOperation(",
                parameters.map { it.asString() }.joinToString(", "),
                ", ",
                "\"$funcName\"",
                ", { a, b -> a.${funcName}(b) }, ",
                renderCheckBinaryOperation(funcName, parameters),
                ")",
                if (binaryOperationsMapIterator.hasNext()) "," else ""
        )
    }
    p.popIndent()
    p.println(")")

    return sb.toString()
}

fun renderCheckUnaryOperation(name: String, params: List<JetType>): String {
    val isAllParamsIntegers = params.fold(true) { a, b -> a && b.isIntegerType() }
    if (!isAllParamsIntegers) {
        return "emptyUnaryFun"
    }

    return when(name) {
        "minus" -> "{ a -> a.$name() }"
        else -> "emptyUnaryFun"
    }
}

fun renderCheckBinaryOperation(name: String, params: List<JetType>): String {
    val isAllParamsIntegers = params.fold(true) { a, b -> a && b.isIntegerType() }
    if (!isAllParamsIntegers) {
        return "emptyBinaryFun"
    }

    return when(name) {
        "plus" -> "{ a, b -> a.add(b) }"
        "minus" -> "{ a, b -> a.subtract(b) }"
        "div" -> "{ a, b -> a.divide(b) }"
        "times" -> "{ a, b -> a.multiply(b) }"
        "mod",
        "xor",
        "or",
        "and" -> "{ a, b -> a.$name(b) }"
        else -> "emptyBinaryFun"
    }
}

private fun JetType.isIntegerType(): Boolean {
    val builtIns = KotlinBuiltIns.getInstance()
    return this == builtIns.getIntType() ||
           this == builtIns.getShortType() ||
           this == builtIns.getByteType() ||
           this == builtIns.getLongType()
}


private fun FunctionDescriptor.getParametersTypes(): List<JetType> {
    val list = arrayListOf((getContainingDeclaration() as ClassDescriptor).getDefaultType())
    getValueParameters().map { it.getType() }.forEach {
        list.add(TypeUtils.makeNotNullable(it))
    }
    return list
}

private fun JetType.asString(): String = getConstructor().getDeclarationDescriptor()!!.getName().asString().toUpperCase()
