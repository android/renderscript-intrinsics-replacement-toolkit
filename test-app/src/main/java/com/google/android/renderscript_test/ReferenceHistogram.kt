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
 * Reference implementation of a Histogram operation.
 *
 * Return an array of 4 * 256 ints.
 * Position 0 is the number of R with a value of 0,
 * Position 1 is the number of G with a value of 0,
 * Position 2 is the number of B with a value of 0,
 * Position 3 is the number of A with a value of 0,
 * Position 4 is the number of R with a value of 1,
 * etc.
*/
@ExperimentalUnsignedTypes
fun referenceHistogram(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d?
): IntArray {
    val input = Vector2dArray(inputArray.asUByteArray(), vectorSize, sizeX, sizeY)

    val counts = IntArray(paddedSize(input.vectorSize) * 256)
    input.forEach(restriction) { x, y ->
        val value = input[x, y]
        for (i in 0 until vectorSize) {
            counts[value[i].toInt() * paddedSize(input.vectorSize) + i]++
        }
    }
    return counts
}

/**
 * Reference implementation of a HistogramDot operation.
 *
 * Each RGBA input value is dot-multiplied first by the specified coefficients.
 * The resulting value is converted to an integer and used for the histogram.
 */
@ExperimentalUnsignedTypes
fun referenceHistogramDot(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    coefficients: FloatArray?,
    restriction: Range2d?
): IntArray {
    val floatCoefficients = coefficients ?: floatArrayOf(0.299f, 0.587f, 0.114f, 0f)
    val input = Vector2dArray(inputArray.asUByteArray(), vectorSize, sizeX, sizeY)
    var coefficientSum = 0f
    for (c in floatCoefficients) {
        require (c >= 0) {
            "RenderScriptToolkit histogramDot. Coefficients must be positive. $c provided."
        }
        coefficientSum += c
    }
    require(coefficientSum <= 1f) { "RenderScriptToolkit histogramDot. Coefficients should " +
            "add to 1.0 or less. $coefficientSum provided." }

    // Compute integer
    val intCoefficients = IntArray(input.vectorSize) { (floatCoefficients[it] * 256f + 0.5f).toInt() }

    val counts = IntArray(256)
    input.forEach(restriction) { x, y ->
        val value = input[x, y]
        // While we could do the computation using floats, we won't get the same results as
        // the existing intrinsics.
        var sum = 0
        // We don't use value.indices because we want to accumulate only 3 values, in the case
        // of vectorSize == 3.
        for (i in 0 until vectorSize) {
            sum += intCoefficients[i] * value[i].toInt()
        }
        // Round up and normalize
        val index = (sum + 0x7f) shr 8
        counts[index]++
    }
    return counts
}
