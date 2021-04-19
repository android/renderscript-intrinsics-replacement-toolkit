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

import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Script
import android.renderscript.ScriptIntrinsicConvolve3x3
import android.renderscript.ScriptIntrinsicConvolve5x5
import android.renderscript.Type
import com.google.android.renderscript.Range2d

/**
 * Does a Convolve operation using the RenderScript Intrinsics.
 */
fun intrinsicConvolve(
    context: RenderScript,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    coefficients: FloatArray,
    restriction: Range2d?
): ByteArray {
    val baseElement = renderScriptVectorElementForU8(context, vectorSize)
    val builder = Type.Builder(context, baseElement)
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, arrayType)
    val outAllocation = Allocation.createTyped(context, arrayType)
    inputAllocation.copyFrom(inputArray)
    val intrinsicOutArray = ByteArray(sizeX * sizeY * paddedSize(vectorSize))
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
    }
    invokeConvolveKernel(
        coefficients,
        context,
        baseElement,
        inputAllocation,
        restriction,
        outAllocation
    )
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    arrayType.destroy()
    return intrinsicOutArray
}

fun intrinsicConvolve(
    context: RenderScript,
    bitmap: Bitmap,
    coefficients: FloatArray,
    restriction: Range2d?
): ByteArray {
    val baseElement = renderScriptElementForBitmap(context, bitmap)

    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    val outAllocation = Allocation.createTyped(context, inputAllocation.type)
    val intrinsicOutArray = ByteArray(bitmap.byteCount)
    inputAllocation.copyFrom(bitmap)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
    }
    invokeConvolveKernel(
        coefficients,
        context,
        baseElement,
        inputAllocation,
        restriction,
        outAllocation
    )
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    return intrinsicOutArray
}

private fun invokeConvolveKernel(
    coefficients: FloatArray,
    context: RenderScript,
    baseElement: Element,
    inputAllocation: Allocation?,
    restriction: Range2d?,
    outAllocation: Allocation?
) {
    when (coefficients.size) {
        9 -> {
            val scriptConvolve3x3 =
                ScriptIntrinsicConvolve3x3.create(context, baseElement)
            scriptConvolve3x3.setCoefficients(coefficients)
            scriptConvolve3x3.setInput(inputAllocation)
            if (restriction != null) {
                val options = Script.LaunchOptions()
                options.setX(restriction.startX, restriction.endX)
                options.setY(restriction.startY, restriction.endY)
                scriptConvolve3x3.forEach(outAllocation, options)
            } else {
                scriptConvolve3x3.forEach(outAllocation)
            }
            scriptConvolve3x3.destroy()
        }
        25 -> {
            val scriptConvolve5x5 =
                ScriptIntrinsicConvolve5x5.create(context, baseElement)
            scriptConvolve5x5.setCoefficients(coefficients)
            scriptConvolve5x5.setInput(inputAllocation)
            if (restriction != null) {
                val options = Script.LaunchOptions()
                options.setX(restriction.startX, restriction.endX)
                options.setY(restriction.startY, restriction.endY)
                scriptConvolve5x5.forEach(outAllocation, options)
            } else {
                scriptConvolve5x5.forEach(outAllocation)
            }
            scriptConvolve5x5.destroy()
        }
        else -> {
            throw IllegalArgumentException("RenderScriptToolkit tests. Only 3x3 and 5x5 convolutions are supported. ${coefficients.size} coefficients provided.")
        }
    }
}
