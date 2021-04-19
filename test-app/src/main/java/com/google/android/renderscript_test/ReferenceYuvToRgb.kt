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

import com.google.android.renderscript.YuvFormat
import java.lang.IllegalArgumentException

/**
 * Reference implementation of a YUV to RGB operation.
 */
@ExperimentalUnsignedTypes
fun referenceYuvToRgb(inputSignedArray: ByteArray, sizeX: Int, sizeY: Int, format: YuvFormat): ByteArray {
    require(sizeX % 2 == 0) { "The width of the input should be even."}
    val inputArray = inputSignedArray.asUByteArray()

    val outputArray = ByteArray(sizeX * sizeY * 4)
    val output = Vector2dArray(outputArray.asUByteArray(), 4, sizeX, sizeY)

    when (format) {
        YuvFormat.NV21 -> {
            val startY = 0
            val startU = sizeX * sizeY + 1
            val startV = sizeX * sizeY

            for (y in 0 until sizeY) {
                for (x in 0 until sizeX) {
                    val offsetY = y * sizeX + x
                    val offsetU = ((y shr 1) * sizeX + (x shr 1) * 2)
                    val offsetV = ((y shr 1) * sizeX + (x shr 1) * 2)
                    output[x, y] = yuvToRGBA4(
                        inputArray[startY + offsetY],
                        inputArray[startU + offsetU],
                        inputArray[startV + offsetV]
                    )
                }
            }
        }

        YuvFormat.YV12 -> {
            /* According to https://developer.android.com/reference/kotlin/android/graphics/ImageFormat#yv12,
             * strideX and strideUV should be aligned to 16 byte boundaries. If we do this, we
             * won't get the same results as RenderScript.
             *
             * We may want to test & require that sizeX is a multiple of 16/32.
             */
            val strideX = roundUpTo16(sizeX) // sizeX //
            val strideUV = roundUpTo16(strideX / 2) // strideX / 2 //
            val startY = 0
            val startU = strideX * sizeY
            val startV = startU + strideUV * sizeY / 2

            for (y in 0 until sizeY) {
                for (x in 0 until sizeX) {
                    val offsetY = y * sizeX + x
                    val offsetUV = (y shr 1) * strideUV + (x shr 1)
                    output[x, y] = yuvToRGBA4(
                        inputArray[startY + offsetY],
                        inputArray[startU + offsetUV],
                        inputArray[startV + offsetUV],
                    )
                }
            }
        }
        else -> throw IllegalArgumentException("Unknown YUV format $format")
    }

    return outputArray
}

@ExperimentalUnsignedTypes
private fun yuvToRGBA4(y: UByte, u: UByte, v: UByte): UByteArray {
    val intY = y.toInt() - 16
    val intU = u.toInt() - 128
    val intV = v.toInt() - 128
    val p = intArrayOf(
        intY * 298 + intV * 409 + 128 shr 8,
        intY * 298 - intU * 100 - intV * 208 + 128 shr 8,
        intY * 298 + intU * 516 + 128 shr 8,
        255
    )
    return UByteArray(4) { p[it].clampToUByte() }
}

/* To be used if we support Float
private fun yuvToRGBA_f4(y: UByte, u: UByte, v: UByte): UByteArray {
    val yuv_U_values = floatArrayOf(0f, -0.392f * 0.003921569f, 2.02f * 0.003921569f, 0f)
    val yuv_V_values = floatArrayOf(1.603f * 0.003921569f, -0.815f * 0.003921569f, 0f, 0f)

    var color = FloatArray(4) {y.toFloat() * 0.003921569f}
    val fU = FloatArray(4) {u.toFloat() - 128f}
    val fV = FloatArray(4) {v.toFloat() - 128f}

    color += fU * yuv_U_values;
    color += fV * yuv_V_values;
    //color = clamp(color, 0.f, 1.f);
    return UByteArray(4) { unitFloatClampedToUByte(color[it]) }
}
*/
