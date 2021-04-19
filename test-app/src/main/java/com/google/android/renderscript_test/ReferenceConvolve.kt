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
 * Reference implementation of a Convolve operation.
 */
@ExperimentalUnsignedTypes
fun referenceConvolve(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    coefficients: FloatArray,
    restriction: Range2d?
): ByteArray {
    val input = Vector2dArray(inputArray.asUByteArray(), vectorSize, sizeX, sizeY)
    val radius = when (coefficients.size) {
        9 -> 1
        25 -> 2
        else -> {
            throw IllegalArgumentException("RenderScriptToolkit Convolve. Only 3x3 and 5x5 convolutions are supported. ${coefficients.size} coefficients provided.")
        }
    }

    input.clipReadToRange = true
    val output = input.createSameSized()
    input.forEach(restriction) { x, y ->
        output[x, y] = convolveOne(input, x, y, coefficients, radius)
    }
    return output.values.asByteArray()
}

@ExperimentalUnsignedTypes
private fun convolveOne(
    inputAlloc: Vector2dArray,
    x: Int,
    y: Int,
    coefficients: FloatArray,
    radius: Int
): UByteArray {
    var sum = FloatArray(paddedSize(inputAlloc.vectorSize))
    var coefficientIndex = 0
    for (deltaY in -radius..radius) {
        for (deltaX in -radius..radius) {
            val inputVector = inputAlloc[x + deltaX, y + deltaY]
            sum += inputVector.toFloatArray() * coefficients[coefficientIndex]
            coefficientIndex++
        }
    }
    return sum.clampToUByte()
}
