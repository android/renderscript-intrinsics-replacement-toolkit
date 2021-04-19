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
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
import com.google.android.renderscript.Range2d

/**
 * Does a Blur operation using the RenderScript Intrinsics.
 */
fun intrinsicBlur(
    context: RenderScript,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    radius: Int,
    restriction: Range2d?
): ByteArray {
    val scriptBlur = ScriptIntrinsicBlur.create(
        context,
        if (vectorSize == 4) Element.RGBA_8888(context) else Element.U8(context)
    )
    val builder =
        Type.Builder(
            context,
            renderScriptVectorElementForU8(context, vectorSize)
        )
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, arrayType)
    inputAllocation.copyFrom(inputArray)
    val outAllocation = Allocation.createTyped(context, arrayType)

    val intrinsicOutArray = ByteArray(sizeX * sizeY * vectorSize)
    scriptBlur.setRadius(radius.toFloat())
    scriptBlur.setInput(inputAllocation)

    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptBlur.forEach(outAllocation, options)
    } else {
        scriptBlur.forEach(outAllocation)
    }
    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    arrayType.destroy()
    scriptBlur.destroy()
    return intrinsicOutArray
}

fun intrinsicBlur(
    context: RenderScript,
    bitmap: Bitmap,
    radius: Int,
    restriction: Range2d?
): ByteArray {
    val baseElement = renderScriptElementForBitmap(context, bitmap)
    val scriptBlur = ScriptIntrinsicBlur.create(context, baseElement)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)
    val outAllocation = Allocation.createTyped(context, inputAllocation.type)
    val intrinsicOutArray = ByteArray(bitmap.byteCount)

    scriptBlur.setRadius(radius.toFloat())
    scriptBlur.setInput(inputAllocation)

    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptBlur.forEach(outAllocation, options)
    } else {
        scriptBlur.forEach(outAllocation)
    }
    outAllocation.copyTo(intrinsicOutArray)

    inputAllocation.destroy()
    outAllocation.destroy()
    scriptBlur.destroy()
    return intrinsicOutArray
}
