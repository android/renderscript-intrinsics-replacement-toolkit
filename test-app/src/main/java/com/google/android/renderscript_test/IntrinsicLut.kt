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
import android.renderscript.ScriptIntrinsicLUT
import android.renderscript.Type
import com.google.android.renderscript.Range2d

/**
 * Does a LookUpTable operation using the RenderScript Intrinsics.
 */
@ExperimentalUnsignedTypes
fun intrinsicLut(
    context: RenderScript,
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    newRed: ByteArray,
    newGreen: ByteArray,
    newBlue: ByteArray,
    newAlpha: ByteArray,
    restriction: Range2d?
): ByteArray {
    val scriptLut: ScriptIntrinsicLUT = ScriptIntrinsicLUT.create(
        context,
        Element.U8_4(context)
    )
    val builder = Type.Builder(context, Element.U8_4(context))
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, arrayType)
    val outAllocation = Allocation.createTyped(context, arrayType)
    inputAllocation.copyFrom(inputArray)
    val intrinsicOutArray = ByteArray(sizeX * sizeY * 4)

    for (v in 0..255) {
        scriptLut.setRed(v, newRed[v].toUByte().toInt())
        scriptLut.setGreen(v, newGreen[v].toUByte().toInt())
        scriptLut.setBlue(v, newBlue[v].toUByte().toInt())
        scriptLut.setAlpha(v, newAlpha[v].toUByte().toInt())
    }
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptLut.forEach(inputAllocation, outAllocation, options)
    } else {
        scriptLut.forEach(inputAllocation, outAllocation)
    }

    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    arrayType.destroy()
    scriptLut.destroy()
    return intrinsicOutArray
}

@ExperimentalUnsignedTypes
fun intrinsicLut(
    context: RenderScript,
    bitmap: Bitmap,
    newRed: ByteArray,
    newGreen: ByteArray,
    newBlue: ByteArray,
    newAlpha: ByteArray,
    restriction: Range2d?
): ByteArray {
    val baseElement = renderScriptElementForBitmap(context, bitmap)
    val scriptLut: ScriptIntrinsicLUT = ScriptIntrinsicLUT.create(context, baseElement)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)
    val outAllocation = Allocation.createTyped(context, inputAllocation.type)
    val intrinsicOutArray = ByteArray(bitmap.byteCount)

    for (v in 0..255) {
        scriptLut.setRed(v, newRed[v].toUByte().toInt())
        scriptLut.setGreen(v, newGreen[v].toUByte().toInt())
        scriptLut.setBlue(v, newBlue[v].toUByte().toInt())
        scriptLut.setAlpha(v, newAlpha[v].toUByte().toInt())
    }
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptLut.forEach(inputAllocation, outAllocation, options)
    } else {
        scriptLut.forEach(inputAllocation, outAllocation)
    }

    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    scriptLut.destroy()
    return intrinsicOutArray
}
