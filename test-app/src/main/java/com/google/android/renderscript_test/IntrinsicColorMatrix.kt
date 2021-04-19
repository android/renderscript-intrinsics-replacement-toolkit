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
import android.renderscript.Matrix4f
import android.renderscript.RenderScript
import android.renderscript.Script
import android.renderscript.ScriptIntrinsicColorMatrix
import android.renderscript.Type
import android.renderscript.Float4
import com.google.android.renderscript.Range2d

/**
 * Does a ColorMatrix operation using the RenderScript Intrinsics.
 */
fun intrinsicColorMatrix(
    context: RenderScript,
    conversion: Tester.ColorMatrixConversionType,
    inputArray: ByteArray,
    inputVectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    outputVectorSize: Int,
    matrix: FloatArray,
    addVector: FloatArray,
    restriction: Range2d?
): ByteArray {
    val scriptColorMatrix = ScriptIntrinsicColorMatrix.create(context)
    val inputBuilder = Type.Builder(
        context, renderScriptVectorElementForU8(
            context,
            inputVectorSize
        )
    )
    inputBuilder.setX(sizeX)
    inputBuilder.setY(sizeY)
    val inputArrayType = inputBuilder.create()
    val inputAllocation = Allocation.createTyped(context, inputArrayType)
    val outputBuilder = Type.Builder(
        context, renderScriptVectorElementForU8(
            context,
            outputVectorSize
        )
    )
    outputBuilder.setX(sizeX)
    outputBuilder.setY(sizeY)
    val outputArrayType = outputBuilder.create()
    val outAllocation = Allocation.createTyped(context, outputArrayType)

    inputAllocation.copyFrom(inputArray)
    val intrinsicOutArray = ByteArray(sizeX * sizeY * paddedSize(outputVectorSize))
    when (conversion) {
        Tester.ColorMatrixConversionType.RGB_TO_YUV -> scriptColorMatrix.setRGBtoYUV()
        Tester.ColorMatrixConversionType.YUV_TO_RGB -> scriptColorMatrix.setYUVtoRGB()
        Tester.ColorMatrixConversionType.GREYSCALE -> scriptColorMatrix.setGreyscale()
        Tester.ColorMatrixConversionType.RANDOM -> {
            val m = Matrix4f()
            var index = 0
            // RS is column major
            for (x in 0..3) {
                for (y in 0..3) {
                    m.set(x, y, matrix[index++])
                }
            }
            scriptColorMatrix.setColorMatrix(m)
        }
    }
    val vector = Float4(
        addVector[0],
        addVector[1],
        addVector[2],
        addVector[3]
    )
    scriptColorMatrix.setAdd(vector)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptColorMatrix.forEach(inputAllocation, outAllocation, options)
    } else {
        scriptColorMatrix.forEach(inputAllocation, outAllocation)
    }
    outAllocation.copyTo(intrinsicOutArray)

    inputAllocation.destroy()
    outAllocation.destroy()
    inputArrayType.destroy()
    outputArrayType.destroy()
    scriptColorMatrix.destroy()
    return intrinsicOutArray
}

fun intrinsicColorMatrix(
    context: RenderScript,
    conversion: Tester.ColorMatrixConversionType,
    bitmap: Bitmap,
    matrix: FloatArray,
    addVector: FloatArray,
    restriction: Range2d?
): ByteArray {
    val scriptColorMatrix = ScriptIntrinsicColorMatrix.create(context)
    val inputAllocation = Allocation.createFromBitmap(context, bitmap)
    inputAllocation.copyFrom(bitmap)
    val outAllocation = Allocation.createTyped(context, inputAllocation.type)
    val intrinsicOutArray = ByteArray(bitmap.byteCount)

    when (conversion) {
        Tester.ColorMatrixConversionType.RGB_TO_YUV -> scriptColorMatrix.setRGBtoYUV()
        Tester.ColorMatrixConversionType.YUV_TO_RGB -> scriptColorMatrix.setYUVtoRGB()
        Tester.ColorMatrixConversionType.GREYSCALE -> scriptColorMatrix.setGreyscale()
        Tester.ColorMatrixConversionType.RANDOM -> {
            val m = Matrix4f()
            var index = 0
            // RS is column major
            for (x in 0..3) {
                for (y in 0..3) {
                    m.set(x, y, matrix[index++])
                }
            }
            scriptColorMatrix.setColorMatrix(m)
        }
    }
    val vector = Float4(
        addVector[0],
        addVector[1],
        addVector[2],
        addVector[3]
    )
    scriptColorMatrix.setAdd(vector)
    if (restriction != null) {
        outAllocation.copyFrom(intrinsicOutArray) // To initialize to zero
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        scriptColorMatrix.forEach(inputAllocation, outAllocation, options)
    } else {
        scriptColorMatrix.forEach(inputAllocation, outAllocation)
    }
    outAllocation.copyTo(intrinsicOutArray)

    inputAllocation.destroy()
    outAllocation.destroy()
    scriptColorMatrix.destroy()
    return intrinsicOutArray
}
