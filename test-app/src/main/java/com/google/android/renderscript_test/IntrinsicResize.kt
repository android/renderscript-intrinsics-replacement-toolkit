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
import android.renderscript.RenderScript
import android.renderscript.Script
import android.renderscript.ScriptIntrinsicResize
import android.renderscript.Type
import com.google.android.renderscript.Range2d

/**
 * Does a Resize operation using the RenderScript Intrinsics.
 */
fun intrinsicResize(
    context: RenderScript,
    inputArray: ByteArray,
    vectorSize: Int,
    inSizeX: Int,
    inSizeY: Int,
    outSizeX: Int,
    outSizeY: Int,
    restriction: Range2d?
): ByteArray {
    val scriptResize = ScriptIntrinsicResize.create(context)
    val builder = Type.Builder(
        context,
        renderScriptVectorElementForU8(context, vectorSize)
    )
    builder.setX(inSizeX)
    builder.setY(inSizeY)
    val inputArrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, inputArrayType)
    builder.setX(outSizeX)
    builder.setY(outSizeY)
    val outputArrayType = builder.create()
    val outAllocation = Allocation.createTyped(context, outputArrayType)
    val intrinsicOutArray = ByteArray(outSizeX * outSizeY * paddedSize(vectorSize))

    inputAllocation.copyFrom(inputArray)
    scriptResize.setInput(inputAllocation)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptResize.forEach_bicubic(outAllocation, options)
    } else {
        scriptResize.forEach_bicubic(outAllocation)
    }
    outAllocation.copyTo(intrinsicOutArray)

    inputAllocation.destroy()
    outAllocation.destroy()
    scriptResize.destroy()
    inputArrayType.destroy()
    outputArrayType.destroy()
    return intrinsicOutArray
}

fun intrinsicResize(
    context: RenderScript,
    bitmap: Bitmap,
    outSizeX: Int,
    outSizeY: Int,
    restriction: Range2d?
): ByteArray {
    val scriptResize = ScriptIntrinsicResize.create(context)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)

    val vectorSize = when (bitmap.config) {
        Bitmap.Config.ARGB_8888 -> 4
        Bitmap.Config.ALPHA_8 -> 1
        else -> error("Unrecognized bitmap config $bitmap.config")
    }
    val builder = Type.Builder(
        context,
        renderScriptVectorElementForU8(context, vectorSize)
    )
    builder.setX(outSizeX)
    builder.setY(outSizeY)
    val outputArrayType = builder.create()
    val outAllocation = Allocation.createTyped(context, outputArrayType)
    val intrinsicOutArray = ByteArray(outSizeX * outSizeY * vectorSize)

    scriptResize.setInput(inputAllocation)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptResize.forEach_bicubic(outAllocation, options)
    } else {
        scriptResize.forEach_bicubic(outAllocation)
    }
    outAllocation.copyTo(intrinsicOutArray)

    inputAllocation.destroy()
    outAllocation.destroy()
    outputArrayType.destroy()
    scriptResize.destroy()
    return intrinsicOutArray
}
