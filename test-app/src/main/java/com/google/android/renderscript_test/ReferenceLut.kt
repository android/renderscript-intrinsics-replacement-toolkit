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

import com.google.android.renderscript.LookupTable
import com.google.android.renderscript.Range2d

/**
 * Reference implementation of a LookUpTable operation.
 */
@ExperimentalUnsignedTypes
fun referenceLut(
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    table: LookupTable,
    restriction: Range2d?
): ByteArray {
    val input = Vector2dArray(inputArray.asUByteArray(), 4, sizeX, sizeY)

    val output = input.createSameSized()
    input.forEach(restriction) { x, y ->
        val oldValue = input[x, y]
        val newValue = byteArrayOf(
            table.red[oldValue[0].toInt()],
            table.green[oldValue[1].toInt()],
            table.blue[oldValue[2].toInt()],
            table.alpha[oldValue[3].toInt()]
        )
        output[x, y] = newValue.asUByteArray()
    }
    return output.values.asByteArray()
}

