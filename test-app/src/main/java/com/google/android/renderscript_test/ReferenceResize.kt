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
import kotlin.math.floor
import kotlin.math.max

var trace = false

/**
 * Reference implementation of a Resize operation.
 */
@ExperimentalUnsignedTypes
fun referenceResize(inputArray: ByteArray,
                    vectorSize: Int,
                    inSizeX: Int,
                    inSizeY: Int,
                    outSizeX: Int, outSizeY: Int,
                    restriction: Range2d?): ByteArray {
    val input = Vector2dArray(inputArray.asUByteArray(), vectorSize, inSizeX, inSizeY)
    val scaleX: Float = input.sizeX.toFloat() / outSizeX.toFloat()
    val scaleY: Float = input.sizeY.toFloat() / outSizeY.toFloat()
    val outArray = UByteArray(outSizeX * outSizeY * paddedSize(input.vectorSize))
    val out = Vector2dArray(outArray, input.vectorSize, outSizeX, outSizeY)
    out.forEach (restriction) { x, y ->
        if (x == 1827 && y == 46) {
            println("Found it")
            trace = true
        }
        val o = bicubicU4(x, y, input, scaleX, scaleY)
        out[x, y] = o.clampToUByte()
    }
    return out.values.asByteArray()
}

private fun cubicInterpolateF(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray,
                              x: Float): FloatArray {
    return p1 + (p2 - p0 + (p0 * 2f - p1 * 5f + p2 * 4f - p3
            + ((p1 - p2) * 3f + p3 - p0) * x) * x) * x * 0.5f
}

@ExperimentalUnsignedTypes
private fun bicubicU4(x: Int, y: Int, gIn: Vector2dArray, scaleX: Float, scaleY: Float): FloatArray {
    var xf: Float = (x + 0.5f) * scaleX - 0.5f
    var yf: Float = (y + 0.5f) * scaleY - 0.5f

    val startX: Int = floor(xf - 1).toInt()
    val startY: Int = floor(yf - 1).toInt()
    xf -= floor(xf)
    yf -= floor(yf)
    val maxX: Int = gIn.sizeX - 1
    val maxY: Int = gIn.sizeY - 1

    val xs0: Int = max(0, startX + 0)
    val xs1: Int = max(0, startX + 1)
    val xs2: Int = kotlin.math.min(maxX, startX + 2)
    val xs3: Int = kotlin.math.min(maxX, startX + 3)

    val ys0: Int = max(0, startY + 0)
    val ys1: Int = max(0, startY + 1)
    val ys2: Int = kotlin.math.min(maxY, startY + 2)
    val ys3: Int = kotlin.math.min(maxY, startY + 3)

    val p00 = gIn[xs0, ys0].toFloatArray()
    val p01 = gIn[xs1, ys0].toFloatArray()
    val p02 = gIn[xs2, ys0].toFloatArray()
    val p03 = gIn[xs3, ys0].toFloatArray()
    val p0  = cubicInterpolateF(p00, p01, p02, p03, xf)

    val p10 = gIn[xs0, ys1].toFloatArray()
    val p11 = gIn[xs1, ys1].toFloatArray()
    val p12 = gIn[xs2, ys1].toFloatArray()
    val p13 = gIn[xs3, ys1].toFloatArray()
    val p1  = cubicInterpolateF(p10, p11, p12, p13, xf)

    val p20 = gIn[xs0, ys2].toFloatArray()
    val p21 = gIn[xs1, ys2].toFloatArray()
    val p22 = gIn[xs2, ys2].toFloatArray()
    val p23 = gIn[xs3, ys2].toFloatArray()
    val p2  = cubicInterpolateF(p20, p21, p22, p23, xf)

    val p30 = gIn[xs0, ys3].toFloatArray()
    val p31 = gIn[xs1, ys3].toFloatArray()
    val p32 = gIn[xs2, ys3].toFloatArray()
    val p33 = gIn[xs3, ys3].toFloatArray()
    val p3  = cubicInterpolateF(p30, p31, p32, p33, xf)

    return cubicInterpolateF(p0, p1, p2, p3, yf)
}


/* To be used if we implement Floats
private fun bicubic_F4(x: Int, y: Int, gin: ByteArray, sizeX: Int, sizeY: Int, scaleX: Float, scaleY: Float): Float4 {
    var xf: Float = (x + 0.5f) * scaleX - 0.5f
    var yf: Float = (y + 0.5f) * scaleY - 0.5f

    val startX: Int = floor(xf - 1).toInt()
    val startY: Int = floor(yf - 1).toInt()
    xf = xf - floor(xf)
    yf = yf - floor(yf)
    val maxX: Int = sizeX - 1
    val maxY: Int = sizeY - 1

    val xs0: Int = max(0, startX + 0)
    val xs1: Int = max(0, startX + 1)
    val xs2: Int = min(maxX, startX + 2)
    val xs3: Int = min(maxX, startX + 3)

    val ys0: Int = max(0, startY + 0)
    val ys1: Int = max(0, startY + 1)
    val ys2: Int = min(maxY, startY + 2)
    val ys3: Int = min(maxY, startY + 3)

    val p00: Float4 = rsGetElementAt_Float4(gIn, xs0, ys0)
    val p01: Float4 = rsGetElementAt_Float4(gIn, xs1, ys0)
    val p02: Float4 = rsGetElementAt_Float4(gIn, xs2, ys0)
    val p03: Float4 = rsGetElementAt_Float4(gIn, xs3, ys0)
    val p0: Float4  = cubicInterpolate_F4(p00, p01, p02, p03, xf)

    val p10: Float4 = rsGetElementAt_Float4(gIn, xs0, ys1)
    val p11: Float4 = rsGetElementAt_Float4(gIn, xs1, ys1)
    val p12: Float4 = rsGetElementAt_Float4(gIn, xs2, ys1)
    val p13: Float4 = rsGetElementAt_Float4(gIn, xs3, ys1)
    val p1: Float4  = cubicInterpolate_F4(p10, p11, p12, p13, xf)

    val p20: Float4 = rsGetElementAt_Float4(gIn, xs0, ys2)
    val p21: Float4 = rsGetElementAt_Float4(gIn, xs1, ys2)
    val p22: Float4 = rsGetElementAt_Float4(gIn, xs2, ys2)
    val p23: Float4 = rsGetElementAt_Float4(gIn, xs3, ys2)
    val p2: Float4  = cubicInterpolate_F4(p20, p21, p22, p23, xf)

    val p30: Float4 = rsGetElementAt_Float4(gIn, xs0, ys3)
    val p31: Float4 = rsGetElementAt_Float4(gIn, xs1, ys3)
    val p32: Float4 = rsGetElementAt_Float4(gIn, xs2, ys3)
    val p33: Float4 = rsGetElementAt_Float4(gIn, xs3, ys3)
    val p3: Float4  = cubicInterpolate_F4(p30, p31, p32, p33, xf)

    val p: Float4  = cubicInterpolate_F4(p0, p1, p2, p3, yf)

    return p
}
*/
