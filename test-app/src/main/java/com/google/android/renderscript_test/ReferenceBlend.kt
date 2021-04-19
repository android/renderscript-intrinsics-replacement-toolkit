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

import com.google.android.renderscript.BlendingMode
import com.google.android.renderscript.Range2d

/**
 * Reference implementation of a Blend operation.
 *
 * See the class Rgba for details of arithmetic operation using that class.
 */
@ExperimentalUnsignedTypes
fun referenceBlend(
    mode: BlendingMode,
    sourceArray: ByteArray,
    destArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d?
) {
    val source = Rgba2dArray(sourceArray, sizeX, sizeY)
    val dest = Rgba2dArray(destArray, sizeX, sizeY)

    /**
     * For each corresponding RGBA value of the source and destination arrays, invoke the blend
     * function and store the result in the destination array.
     */
    fun blendEachPair(blendFunction: (src: Rgba, dst: Rgba) -> Rgba) {
        dest.forEachCell(restriction) { x, y ->
            dest[x, y] = blendFunction(source[x, y], dest[x, y])
        }
    }

    when (mode) {
        BlendingMode.CLEAR -> blendEachPair { _, _ -> Rgba(0, 0, 0, 0) }
        BlendingMode.SRC -> blendEachPair { src, _ -> src }
        BlendingMode.DST -> { /* This doesn't do anything. */ }
        BlendingMode.SRC_OVER -> blendEachPair { src, dst -> blendOver(src, dst) }
        BlendingMode.DST_OVER -> blendEachPair { src, dst -> blendOver(dst, src) }
        BlendingMode.SRC_IN -> blendEachPair { src, dst -> blendIn(src, dst) }
        BlendingMode.DST_IN -> blendEachPair { src, dst -> blendIn(dst, src) }
        BlendingMode.SRC_OUT -> blendEachPair { src, dst -> blendOut(src, dst) }
        BlendingMode.DST_OUT -> blendEachPair { src, dst -> blendOut(dst, src) }
        BlendingMode.SRC_ATOP -> blendEachPair { src, dst -> blendAtop(src, dst) }
        BlendingMode.DST_ATOP -> blendEachPair { src, dst -> blendAtop(dst, src) }
        BlendingMode.XOR -> blendEachPair { src, dst -> src xor dst }
        BlendingMode.MULTIPLY -> blendEachPair { src, dst -> src * dst }
        BlendingMode.ADD -> blendEachPair { src, dst -> dst + src }
        BlendingMode.SUBTRACT -> blendEachPair { src, dst -> dst - src }
    }
}

@ExperimentalUnsignedTypes
private fun blendOver(src: Rgba, dst: Rgba) = src + (dst * (255 - src.a))

@ExperimentalUnsignedTypes
private fun blendIn(src: Rgba, dst: Rgba) = src * dst.a

@ExperimentalUnsignedTypes
private fun blendOut(src: Rgba, dst: Rgba) = src * (255 - dst.a)

@ExperimentalUnsignedTypes
private fun blendAtop(src: Rgba, dst: Rgba): Rgba {
    val value = src * dst.a + dst * (255 - src.a)
    value.a = dst.a
    return value
}
