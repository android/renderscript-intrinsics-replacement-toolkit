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
import android.renderscript.ScriptIntrinsicBlend
import android.renderscript.Type
import com.google.android.renderscript.BlendingMode
import com.google.android.renderscript.Range2d

/**
 * Does a Blend operation using the RenderScript Intrinsics.
 */
fun intrinsicBlend(
    context: RenderScript,
    mode: BlendingMode,
    sourceArray: ByteArray,
    destArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d?
) {
    val scriptBlend = ScriptIntrinsicBlend.create(context, Element.U8_4(context))
    val builder = Type.Builder(context, Element.U8_4(context))
    builder.setX(sizeX)
    builder.setY(sizeY)
    val arrayType = builder.create()
    val sourceAllocation = Allocation.createTyped(context, arrayType)
    val destAllocation = Allocation.createTyped(context, arrayType)
    sourceAllocation.copyFrom(sourceArray)
    destAllocation.copyFrom(destArray)

    callBlendForEach(scriptBlend, sourceAllocation, destAllocation, mode, restriction)
    destAllocation.copyTo(destArray)

    sourceAllocation.destroy()
    destAllocation.destroy()
    arrayType.destroy()
    scriptBlend.destroy()
}

fun intrinsicBlend(
    context: RenderScript,
    mode: BlendingMode,
    sourceBitmap: Bitmap,
    destBitmap: Bitmap,
    restriction: Range2d?
) {
    val scriptBlend = ScriptIntrinsicBlend.create(context, Element.U8_4(context))
    val sourceAllocation = Allocation.createFromBitmap(context, sourceBitmap)
    val destAllocation = Allocation.createFromBitmap(context, destBitmap)
    sourceAllocation.copyFrom(sourceBitmap)
    destAllocation.copyFrom(destBitmap)

    callBlendForEach(scriptBlend, sourceAllocation, destAllocation, mode, restriction)
    destAllocation.copyTo(destBitmap)

    sourceAllocation.destroy()
    destAllocation.destroy()
    scriptBlend.destroy()
}

private fun callBlendForEach(
    scriptBlend: ScriptIntrinsicBlend,
    sourceAllocation: Allocation,
    destAllocation: Allocation,
    mode: BlendingMode,
    restriction: Range2d?
) {
    if (restriction != null) {
        val options = Script.LaunchOptions()
        options.setX(restriction.startX, restriction.endX)
        options.setY(restriction.startY, restriction.endY)
        when (mode) {
            BlendingMode.CLEAR -> scriptBlend.forEachClear(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.SRC -> scriptBlend.forEachSrc(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.DST -> scriptBlend.forEachDst(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.SRC_OVER -> scriptBlend.forEachSrcOver(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.DST_OVER -> scriptBlend.forEachDstOver(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.SRC_IN -> scriptBlend.forEachSrcIn(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.DST_IN -> scriptBlend.forEachDstIn(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.SRC_OUT -> scriptBlend.forEachSrcOut(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.DST_OUT -> scriptBlend.forEachDstOut(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.SRC_ATOP -> scriptBlend.forEachSrcAtop(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.DST_ATOP -> scriptBlend.forEachDstAtop(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.XOR -> scriptBlend.forEachXor(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.MULTIPLY -> scriptBlend.forEachMultiply(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.ADD -> scriptBlend.forEachAdd(
                sourceAllocation, destAllocation, options
            )
            BlendingMode.SUBTRACT -> scriptBlend.forEachSubtract(
                sourceAllocation, destAllocation, options
            )
        }
    } else {
        when (mode) {
            BlendingMode.CLEAR -> scriptBlend.forEachClear(
                sourceAllocation, destAllocation
            )
            BlendingMode.SRC -> scriptBlend.forEachSrc(
                sourceAllocation, destAllocation
            )
            BlendingMode.DST -> scriptBlend.forEachDst(
                sourceAllocation, destAllocation
            )
            BlendingMode.SRC_OVER -> scriptBlend.forEachSrcOver(
                sourceAllocation, destAllocation
            )
            BlendingMode.DST_OVER -> scriptBlend.forEachDstOver(
                sourceAllocation, destAllocation
            )
            BlendingMode.SRC_IN -> scriptBlend.forEachSrcIn(
                sourceAllocation, destAllocation
            )
            BlendingMode.DST_IN -> scriptBlend.forEachDstIn(
                sourceAllocation, destAllocation
            )
            BlendingMode.SRC_OUT -> scriptBlend.forEachSrcOut(
                sourceAllocation, destAllocation
            )
            BlendingMode.DST_OUT -> scriptBlend.forEachDstOut(
                sourceAllocation, destAllocation
            )
            BlendingMode.SRC_ATOP -> scriptBlend.forEachSrcAtop(
                sourceAllocation, destAllocation
            )
            BlendingMode.DST_ATOP -> scriptBlend.forEachDstAtop(
                sourceAllocation, destAllocation
            )
            BlendingMode.XOR -> scriptBlend.forEachXor(
                sourceAllocation, destAllocation
            )
            BlendingMode.MULTIPLY -> scriptBlend.forEachMultiply(
                sourceAllocation, destAllocation
            )
            BlendingMode.ADD -> scriptBlend.forEachAdd(
                sourceAllocation, destAllocation
            )
            BlendingMode.SUBTRACT -> scriptBlend.forEachSubtract(
                sourceAllocation, destAllocation
            )
        }
    }
}
