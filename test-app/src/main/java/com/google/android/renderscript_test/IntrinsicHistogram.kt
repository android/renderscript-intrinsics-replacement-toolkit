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
import android.renderscript.ScriptIntrinsicHistogram
import android.renderscript.Type
import com.google.android.renderscript.Range2d

/**
 * Does a Histogram operation using the RenderScript Intrinsics.
 */
fun intrinsicHistogram(
    context: RenderScript,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d?
): IntArray {
    val element = renderScriptVectorElementForU8(context, vectorSize)
    val scriptHistogram = ScriptIntrinsicHistogram.create(context, element)
    val builder = Type.Builder(context, element)
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, arrayType)
    val outAllocation =
        Allocation.createSized(
            context,
            renderScriptVectorElementForI32(context, vectorSize),
            256
        )
    inputAllocation.copyFrom(inputArray)
    scriptHistogram.setOutput(outAllocation)
    if (restriction != null) {
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptHistogram.forEach(inputAllocation, options)
    } else {
        scriptHistogram.forEach(inputAllocation)
    }

    val intrinsicOutArray = IntArray(256 * paddedSize(vectorSize))
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    arrayType.destroy()
    scriptHistogram.destroy()
    return intrinsicOutArray
}

fun intrinsicHistogram(
    context: RenderScript,
    bitmap: Bitmap,
    restriction: Range2d?
): IntArray {
    val baseElement = renderScriptElementForBitmap(context, bitmap)
    val scriptHistogram = ScriptIntrinsicHistogram.create(context, baseElement)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)
    val vectorSize = vectorSizeOfBitmap(bitmap)
    val outAllocation =
        Allocation.createSized(
            context,
            renderScriptVectorElementForI32(context, vectorSize),
            256
        )
    scriptHistogram.setOutput(outAllocation)
    if (restriction != null) {
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptHistogram.forEach(inputAllocation, options)
    } else {
        scriptHistogram.forEach(inputAllocation)
    }

    val intrinsicOutArray = IntArray(256 * vectorSize)
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    scriptHistogram.destroy()
    return intrinsicOutArray
}

fun intrinsicHistogramDot(
    context: RenderScript,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    coefficients: FloatArray?,
    restriction: Range2d?
): IntArray {
    val element = renderScriptVectorElementForU8(context, vectorSize)
    val scriptHistogram = ScriptIntrinsicHistogram.create(context, element)
    val builder = Type.Builder(context, element)
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, arrayType)
    val outAllocation =
        Allocation.createSized(context, Element.I32(context), 256)
    inputAllocation.copyFrom(inputArray)

    if (coefficients != null) {
        require(coefficients.size == vectorSize) {
            "RenderScriptToolkit tests. $vectorSize coefficients are required for histogram. " +
                "${coefficients.size} provided."
        }
        scriptHistogram.setDotCoefficients(
            coefficients[0],
            if (vectorSize > 1) coefficients[1] else 0f,
            if (vectorSize > 2) coefficients[2] else 0f,
            if (vectorSize > 3) coefficients[3] else 0f
        )
    }
    scriptHistogram.setOutput(outAllocation)
    if (restriction != null) {
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptHistogram.forEach_Dot(inputAllocation, options)
    } else {
        scriptHistogram.forEach_Dot(inputAllocation)
    }
    val intrinsicOutArray = IntArray(256)
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    arrayType.destroy()
    scriptHistogram.destroy()
    return intrinsicOutArray
}

fun intrinsicHistogramDot(
    context: RenderScript,
    bitmap: Bitmap,
    coefficients: FloatArray?,
    restriction: Range2d?
): IntArray {
    val baseElement = renderScriptElementForBitmap(context, bitmap)
    val scriptHistogram = ScriptIntrinsicHistogram.create(context, baseElement)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)
    val outAllocation =
        Allocation.createSized(context, Element.I32(context), 256)

    if (coefficients != null) {
        require(coefficients.size == 4) {
            "RenderScriptToolkit tests. Four coefficients are required for histogram. " +
                "${coefficients.size} provided."
        }
        scriptHistogram.setDotCoefficients(
            coefficients[0],
            coefficients[1],
            coefficients[2],
            coefficients[3]
        )
    }
    scriptHistogram.setOutput(outAllocation)
    if (restriction != null) {
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptHistogram.forEach_Dot(inputAllocation, options)
    } else {
        scriptHistogram.forEach_Dot(inputAllocation)
    }
    val intrinsicOutArray = IntArray(256)
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    scriptHistogram.destroy()
    return intrinsicOutArray
}
