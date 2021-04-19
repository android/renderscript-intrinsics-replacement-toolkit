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

import com.google.android.renderscript.Range2d
import com.google.android.renderscript.Rgba3dArray

/**
 * Reference implementation of a 3D LookUpTable operation.
 */
@ExperimentalUnsignedTypes
fun referenceLut3d(
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    cube: Rgba3dArray,
    restriction: Range2d?
): ByteArray {
    val input = Vector2dArray(inputArray.asUByteArray(), 4, sizeX, sizeY)
    val output = input.createSameSized()
    input.forEach(restriction) { x, y ->
        output[x, y] = lookup(input[x, y], cube)
    }
    return output.values.asByteArray()
}

@ExperimentalUnsignedTypes
private fun lookup(input: UByteArray, cube: Rgba3dArray): UByteArray {
    // Calculate the two points at opposite edges of the size 1
    // cube that contains our point.
    val maxIndex = Int4(cube.sizeX - 1, cube.sizeY - 1, cube.sizeZ - 1, 0)
    val baseCoordinate: Float4 = input.toFloat4() * maxIndex.toFloat4() / 255f
    val point1: Int4 = baseCoordinate.intFloor()
    val point2: Int4 = min(point1 + 1, maxIndex)
    val fractionAwayFromPoint1: Float4 = baseCoordinate - point1.toFloat4()

    // Get the RGBA values at each of the four corners of the size 1 cube.
    val v000 = cube[point1.x, point1.y, point1.z].toFloat4()
    val v100 = cube[point2.x, point1.y, point1.z].toFloat4()
    val v010 = cube[point1.x, point2.y, point1.z].toFloat4()
    val v110 = cube[point2.x, point2.y, point1.z].toFloat4()
    val v001 = cube[point1.x, point1.y, point2.z].toFloat4()
    val v101 = cube[point2.x, point1.y, point2.z].toFloat4()
    val v011 = cube[point1.x, point2.y, point2.z].toFloat4()
    val v111 = cube[point2.x, point2.y, point2.z].toFloat4()

    // Do the linear mixing of these eight values.
    val yz00 = mix(v000, v100, fractionAwayFromPoint1.x)
    val yz10 = mix(v010, v110, fractionAwayFromPoint1.x)
    val yz01 = mix(v001, v101, fractionAwayFromPoint1.x)
    val yz11 = mix(v011, v111, fractionAwayFromPoint1.x)

    val z0 = mix(yz00, yz10, fractionAwayFromPoint1.y)
    val z1 = mix(yz01, yz11, fractionAwayFromPoint1.y)

    val v = mix(z0, z1, fractionAwayFromPoint1.z)

    // Preserve the alpha of the original value
    return ubyteArrayOf(v.x.clampToUByte(), v.y.clampToUByte(), v.z.clampToUByte(), input[3])
}
