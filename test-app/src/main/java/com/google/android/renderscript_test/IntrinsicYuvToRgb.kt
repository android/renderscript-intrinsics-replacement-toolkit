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

import android.graphics.ImageFormat
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import com.google.android.renderscript.YuvFormat

/**
 * Does a YUV to RGB operation using the RenderScript Intrinsics.
 */
fun intrinsicYuvToRgb(
    context: RenderScript,
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    format: YuvFormat
): ByteArray {
    val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(
        context,
        Element.YUV(context)
    )
    val inputBuilder = Type.Builder(context, Element.YUV(context))
    inputBuilder.setX(sizeX)
    inputBuilder.setY(sizeY)
    when (format) {
        YuvFormat.NV21 -> inputBuilder.setYuvFormat(ImageFormat.NV21)
        YuvFormat.YV12 -> inputBuilder.setYuvFormat(ImageFormat.YV12)
        else -> require(false) { "Unknown YUV format $format" }
    }
    val inputArrayType = inputBuilder.create()
    val inputAllocation = Allocation.createTyped(context, inputArrayType)

    val outputBuilder = Type.Builder(context, Element.U8_4(context))
    outputBuilder.setX(sizeX)
    outputBuilder.setY(sizeY)
    val outputArrayType = outputBuilder.create()
    val outAllocation = Allocation.createTyped(context, outputArrayType)
    val intrinsicOutArray = ByteArray(sizeX * sizeY * 4)

    inputAllocation.copyFrom(inputArray)
    scriptYuvToRgb.setInput(inputAllocation)
    scriptYuvToRgb.forEach(outAllocation)
    outAllocation.copyTo(intrinsicOutArray)

    inputAllocation.destroy()
    outAllocation.destroy()
    inputArrayType.destroy()
    outputArrayType.destroy()
    scriptYuvToRgb.destroy()
    return intrinsicOutArray
}
