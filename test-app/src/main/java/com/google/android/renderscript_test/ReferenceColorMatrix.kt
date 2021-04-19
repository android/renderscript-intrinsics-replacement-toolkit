/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.renderscript_test

import com.google.android.renderscript.Range2d

/**
 * Reference implementation of a ColorMatrix operation.
 */
@ExperimentalUnsignedTypes
fun referenceColorMatrix(inputArray: ByteArray,
                         inputVectorSize: Int,
                         sizeX: Int,
                         sizeY: Int,
                         outputVectorSize: Int,
                         matrix: FloatArray, addVector: FloatArray,
                         restriction: Range2d?): ByteArray {
    require (matrix.size == 16) { "RenderScriptToolkit colorMatrix. Matrix should have 16 values. ${matrix.size} provided." }

    val input = Vector2dArray(inputArray.asUByteArray(), inputVectorSize, sizeX, sizeY)
    val outputArray = ByteArray(sizeX * sizeY * paddedSize(outputVectorSize))
    val output = Vector2dArray(outputArray.asUByteArray(), outputVectorSize, sizeX, sizeY)

    output.forEach (restriction) { x, y ->
        val inUByteValue = input[x, y]
        val inFloatValue = FloatArray(4) { if (it >= inputVectorSize) 0f else byteToUnitFloat(inUByteValue[it]) }
        val outFloatValue = multiplyAndAdd(matrix, inFloatValue, addVector)
        val outUByteValue = UByteArray(paddedSize(output.vectorSize)) { unitFloatClampedToUByte(outFloatValue[it]) }
        output[x, y] = outUByteValue
    }
    return outputArray
}

private fun multiplyAndAdd(matrix: FloatArray, inVector: FloatArray, addVector: FloatArray): FloatArray {
    // In RenderScript, matrix were set in column major format
    val result = addVector.clone()
    for (i in 0..3) {
        for (j in 0..3) {
            result[i] += matrix[j * 4 + i] * inVector[j]
        }
    }
    return result
}
