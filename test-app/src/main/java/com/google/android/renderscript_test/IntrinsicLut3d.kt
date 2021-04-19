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
import android.renderscript.ScriptIntrinsic3DLUT
import android.renderscript.Type
import com.google.android.renderscript.Range2d

/**
 * Does a 3D LookUpTable operation using the RenderScript Intrinsics.
 */
fun intrinsicLut3d(
    context: RenderScript,
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    cubeArray: ByteArray,
    cubeSize: Dimension,
    restriction: Range2d?
): ByteArray {
    val scriptLut3d: ScriptIntrinsic3DLUT = ScriptIntrinsic3DLUT.create(
        context, Element.U8_4(
            context
        )
    )
    val builder = Type.Builder(context, Element.U8_4(context))
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val inputAllocation = Allocation.createTyped(context, arrayType)
    val outAllocation = Allocation.createTyped(context, arrayType)
    inputAllocation.copyFrom(inputArray)
    val intrinsicOutArray = ByteArray(sizeX * sizeY * 4)

    val cubeTypeBuilder: Type.Builder =
        Type.Builder(context, Element.U8_4(context))
    cubeTypeBuilder.setX(cubeSize.sizeX)
    cubeTypeBuilder.setY(cubeSize.sizeY)
    cubeTypeBuilder.setZ(cubeSize.sizeZ)
    val cubeType: Type = cubeTypeBuilder.create()
    val cubeAllocation = Allocation.createTyped(context, cubeType)
    cubeAllocation.copyFrom(cubeArray)
    scriptLut3d.setLUT(cubeAllocation)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptLut3d.forEach(inputAllocation, outAllocation, options)
    } else {
        scriptLut3d.forEach(inputAllocation, outAllocation)
    }

    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    cubeAllocation.destroy()
    arrayType.destroy()
    cubeType.destroy()
    scriptLut3d.destroy()
    return intrinsicOutArray
}

fun intrinsicLut3d(
    context: RenderScript,
    bitmap: Bitmap,
    cubeArray: ByteArray,
    cubeSize: Dimension,
    restriction: Range2d?
): ByteArray {
    val baseElement = renderScriptElementForBitmap(context, bitmap)
    val scriptLut3d: ScriptIntrinsic3DLUT = ScriptIntrinsic3DLUT.create(context, baseElement)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)
    val outAllocation = Allocation.createTyped(context, inputAllocation.type)
    val intrinsicOutArray = ByteArray(bitmap.byteCount)

    val cubeTypeBuilder: Type.Builder =
        Type.Builder(context, Element.U8_4(context))
    cubeTypeBuilder.setX(cubeSize.sizeX)
    cubeTypeBuilder.setY(cubeSize.sizeY)
    cubeTypeBuilder.setZ(cubeSize.sizeZ)
    val cubeType: Type = cubeTypeBuilder.create()
    val cubeAllocation = Allocation.createTyped(context, cubeType)
    cubeAllocation.copyFrom(cubeArray)
    scriptLut3d.setLUT(cubeAllocation)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptLut3d.forEach(inputAllocation, outAllocation, options)
    } else {
        scriptLut3d.forEach(inputAllocation, outAllocation)
    }

    outAllocation.copyTo(intrinsicOutArray)
    inputAllocation.destroy()
    outAllocation.destroy()
    cubeAllocation.destroy()
    cubeType.destroy()
    scriptLut3d.destroy()
    return intrinsicOutArray
}
