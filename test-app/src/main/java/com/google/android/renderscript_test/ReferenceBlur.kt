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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Reference implementation of a Blur operation.
 */
@ExperimentalUnsignedTypes
fun referenceBlur(inputArray: ByteArray,
                  vectorSize: Int,
                  sizeX: Int,
                  sizeY: Int,
                  radius: Int = 5, restriction: Range2d?): ByteArray {
    val maxRadius = 25
    require (radius in 1..maxRadius) {
        "RenderScriptToolkit blur. Radius should be between 1 and $maxRadius. $radius provided."
    }
    val gaussian = buildGaussian(radius)

    // Convert input data to float so that the blurring goes faster.
    val inputValues = FloatArray(inputArray.size) { byteToUnitFloat(inputArray[it].toUByte()) }
    val inputInFloat = FloatVector2dArray(inputValues, vectorSize, sizeX, sizeY)

    val scratch = horizontalBlur(inputInFloat, gaussian, radius, restriction)
    val outInFloat = verticalBlur(scratch, gaussian, radius, restriction)

    // Convert the results back to bytes.
    return ByteArray(outInFloat.values.size) { unitFloatClampedToUByte(outInFloat.values[it]).toByte() }
}

/**
 * Blurs along the horizontal direction using the specified gaussian weights.
 */
private fun horizontalBlur(
    input: FloatVector2dArray,
    gaussian: FloatArray,
    radius: Int,
    restriction: Range2d?
): FloatVector2dArray {
    var expandedRestriction: Range2d? = null
    if (restriction != null) {
        // Expand the restriction in the vertical direction so that the vertical pass
        // will have all the data it needs.
        expandedRestriction = Range2d(
            restriction.startX,
            restriction.endX,
            max(restriction.startY - radius, 0),
            min(restriction.endY + radius, input.sizeY)
        )
    }

    input.clipAccessToRange = true
    val out = input.createSameSized()
    out.forEach(expandedRestriction) { x, y ->
        for ((gaussianIndex, delta: Int) in (-radius..radius).withIndex()) {
            val v = input[x + delta, y] * gaussian[gaussianIndex]
            out[x, y] += v
        }
    }
    return out
}

/**
 * Blurs along the horizontal direction using the specified gaussian weights.
 */
private fun verticalBlur(
    input: FloatVector2dArray,
    gaussian: FloatArray,
    radius: Int,
    restriction: Range2d?
): FloatVector2dArray {
    input.clipAccessToRange = true
    val out = input.createSameSized()
    out.forEach(restriction) { x, y ->
        for ((gaussianIndex, delta: Int) in (-radius..radius).withIndex()) {
            val v = input[x, y + delta] * gaussian[gaussianIndex]
            out[x, y] += v
        }
    }
    return out
}

/**
 * Builds an array of gaussian weights that will be used for doing the horizontal and vertical
 * blur.
 *
 * @return An array of (2 * radius + 1) floats.
 */
private fun buildGaussian(radius: Int): FloatArray {
    val e: Float = kotlin.math.E.toFloat()
    val pi: Float = kotlin.math.PI.toFloat()
    val sigma: Float = 0.4f * radius.toFloat() + 0.6f
    val coefficient1: Float = 1.0f / (sqrt(2.0f * pi) * sigma)
    val coefficient2: Float = -1.0f / (2.0f * sigma * sigma)

    var sum = 0.0f
    val gaussian = FloatArray(radius * 2 + 1)
    for (r in -radius..radius) {
        val floatR: Float = r.toFloat()
        val v: Float = coefficient1 * e.pow(floatR * floatR * coefficient2)
        gaussian[r + radius] = v
        sum += v
    }

    // Normalize so that the sum of the weights equal 1f.
    val normalizeFactor: Float = 1.0f / sum
    for (r in -radius..radius) {
        gaussian[r + radius] *= normalizeFactor
    }
    return gaussian
}
