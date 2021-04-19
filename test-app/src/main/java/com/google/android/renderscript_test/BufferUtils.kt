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
import android.graphics.Canvas
import android.renderscript.Element
import android.renderscript.RenderScript
import com.google.android.renderscript.Range2d
import com.google.android.renderscript.Rgba3dArray
import com.google.android.renderscript.YuvFormat
import java.nio.ByteBuffer
import java.util.Random
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A vector of 4 integers.
 */
class Int4(
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,
    var w: Int = 0
) {
    operator fun plus(other: Int4) = Int4(x + other.x, y + other.y, z + other.z, w + other.w)
    operator fun plus(n: Int) = Int4(x + n, y + n, z + n, w + n)

    operator fun minus(other: Int4) = Int4(x - other.x, y - other.y, z - other.z, w - other.w)
    operator fun minus(n: Int) = Int4(x - n, y - n, z - n, w - n)

    operator fun times(other: Int4) = Int4(x * other.x, y * other.y, z * other.z, w * other.w)
    operator fun times(n: Int) = Int4(x * n, y * n, z * n, w * n)

    fun toFloat4() = Float4(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
}

fun min(a: Int4, b: Int4) = Int4(min(a.x, b.x), min(a.y, b.y), min(a.z, b.z), min(a.w, b.w))

/**
 * A vector of 4 floats.
 */
data class Float4(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 0f
) {
    operator fun plus(other: Float4) = Float4(x + other.x, y + other.y, z + other.z, w + other.w)
    operator fun plus(f: Float) = Float4(x + f, y + f, z + f, w + f)

    operator fun minus(other: Float4) = Float4(x - other.x, y - other.y, z - other.z, w - other.w)
    operator fun minus(f: Float) = Float4(x - f, y - f, z - f, w - f)

    operator fun times(other: Float4) = Float4(x * other.x, y * other.y, z * other.z, w * other.w)
    operator fun times(f: Float) = Float4(x * f, y * f, z * f, w * f)

    operator fun div(other: Float4) = Float4(x / other.x, y / other.y, z / other.z, w / other.w)
    operator fun div(f: Float) = Float4(x / f, y / f, z / f, w / f)

    fun intFloor() = Int4(floor(x).toInt(), floor(y).toInt(), floor(z).toInt(), floor(w).toInt())
}

/**
 * Convert a UByteArray to a Float4 vector
 */
@ExperimentalUnsignedTypes
fun UByteArray.toFloat4(): Float4 {
    require(size == 4)
    return Float4(this[0].toFloat(), this[1].toFloat(), this[2].toFloat(), this[3].toFloat())
}

/**
 * Convert a ByteArray to a Float4 vector
 */
@ExperimentalUnsignedTypes
fun ByteArray.toFloat4(): Float4 {
    require(size == 4)
    return Float4(
        this[0].toUByte().toFloat(),
        this[1].toUByte().toFloat(),
        this[2].toUByte().toFloat(),
        this[3].toUByte().toFloat()
    )
}

data class Dimension(val sizeX: Int, val sizeY: Int, val sizeZ: Int)

/**
 * An RGBA value represented by 4 Int.
 *
 * Note that the arithmetical operations consider a 0..255 value the equivalent of 0f..1f.
 * After adding or subtracting, the value is clamped. After multiplying, the value is rescaled to
 * stay in the 0..255 range. This is useful for the Blend operation.
 */
@ExperimentalUnsignedTypes
data class Rgba(
    var r: Int = 0,
    var g: Int = 0,
    var b: Int = 0,
    var a: Int = 0
) {
    operator fun plus(other: Rgba) =
        Rgba(r + other.r, g + other.g, b + other.b, a + other.a).clampToUByteRange()

    operator fun minus(other: Rgba) =
        Rgba(r - other.r, g - other.g, b - other.b, a - other.a).clampToUByteRange()

    operator fun times(other: Rgba) = Rgba(r * other.r, g * other.g, b * other.b, a * other.a) shr 8
    operator fun times(scalar: Int) = Rgba(r * scalar, g * scalar, b * scalar, a * scalar) shr 8

    infix fun xor(other: Rgba) = Rgba(r xor other.r, g xor other.g, b xor other.b, a xor other.a)

    infix fun shr(other: Int) = Rgba(r shr other, g shr other, b shr other, a shr other)

    private fun clampToUByteRange() = Rgba(
        r.clampToUByteRange(),
        g.clampToUByteRange(),
        b.clampToUByteRange(),
        a.clampToUByteRange()
    )
}

/**
 * A 2D array of UByte vectors, stored in row-major format.
 *
 * Arrays of vectorSize == 3 are padded to 4.
 */
@ExperimentalUnsignedTypes
class Vector2dArray(
    val values: UByteArray,
    val vectorSize: Int,
    val sizeX: Int,
    val sizeY: Int
) {
    /**
     * If true, index access that would try to get a value that's out of bounds will simply
     * return the border value instead. E.g. for [3, -3] would return the value for [3, 0],
     * assuming that the sizeX > 3.
     */
    var clipReadToRange: Boolean = false

    operator fun get(x: Int, y: Int): UByteArray {
        var fixedX = x
        var fixedY = y
        if (clipReadToRange) {
            fixedX = min(max(x, 0), sizeX - 1)
            fixedY = min(max(y, 0), sizeY - 1)
        } else {
            require(x in 0 until sizeX && y in 0 until sizeY) { "Out of bounds" }
        }
        val start = indexOfVector(fixedX, fixedY)
        return UByteArray(paddedSize(vectorSize)) { values[start + it] }
    }

    operator fun set(x: Int, y: Int, value: UByteArray) {
        require(value.size == paddedSize(vectorSize)) { "Not the expected vector size" }
        require(x in 0 until sizeX && y in 0 until sizeY) { "Out of bounds" }
        val start = indexOfVector(x, y)
        for (i in value.indices) {
            values[start + i] = value[i]
        }
    }

    private fun indexOfVector(x: Int, y: Int) = ((y * sizeX) + x) * paddedSize(vectorSize)

    fun createSameSized() = Vector2dArray(UByteArray(values.size), vectorSize, sizeX, sizeY)

    fun forEach(restriction: Range2d?, work: (Int, Int) -> (Unit)) {
        forEachCell(sizeX, sizeY, restriction, work)
    }
}

/**
 * A 2D array of float vectors, stored in row-major format.
 *
 * Arrays of vectorSize == 3 are padded to 4.
 */
class FloatVector2dArray(
    val values: FloatArray,
    val vectorSize: Int,
    val sizeX: Int,
    val sizeY: Int
) {
    /**
     * If true, index access that would try to get a value that's out of bounds will simply
     * return the border value instead. E.g. for [3, -3] would return the value for [3, 0],
     * assuming that the sizeX > 3.
     */
    var clipAccessToRange: Boolean = false

    operator fun get(x: Int, y: Int): FloatArray {
        var fixedX = x
        var fixedY = y
        if (clipAccessToRange) {
            fixedX = min(max(x, 0), sizeX - 1)
            fixedY = min(max(y, 0), sizeY - 1)
        } else {
            require(x in 0 until sizeX && y in 0 until sizeY) { "Out of bounds" }
        }
        val start = indexOfVector(fixedX, fixedY)
        return FloatArray(vectorSize) { values[start + it] }
    }

    operator fun set(x: Int, y: Int, value: FloatArray) {
        require(x in 0 until sizeX && y in 0 until sizeY) { "Out of bounds" }
        val start = indexOfVector(x, y)
        for (i in value.indices) {
            values[start + i] = value[i]
        }
    }

    private fun indexOfVector(x: Int, y: Int) = ((y * sizeX) + x) * paddedSize(vectorSize)

    fun createSameSized() = FloatVector2dArray(FloatArray(values.size), vectorSize, sizeX, sizeY)

    fun forEach(restriction: Range2d?, work: (Int, Int) -> (Unit)) {
        forEachCell(sizeX, sizeY, restriction, work)
    }
}

/**
 * A 2D array of RGBA data.
 */
@ExperimentalUnsignedTypes
class Rgba2dArray(
    private val values: ByteArray,
    val sizeX: Int,
    val sizeY: Int
) {
    operator fun get(x: Int, y: Int): Rgba {
        val i = indexOfVector(x, y)
        return Rgba(
            values[i].toUByte().toInt(),
            values[i + 1].toUByte().toInt(),
            values[i + 2].toUByte().toInt(),
            values[i + 3].toUByte().toInt()
        )
    }

    operator fun set(x: Int, y: Int, value: Rgba) {
        // Verify that x, y, z, w are in the 0..255 range
        require(value.r in 0..255)
        require(value.g in 0..255)
        require(value.b in 0..255)
        require(value.a in 0..255)
        val i = indexOfVector(x, y)
        values[i] = value.r.toUByte().toByte()
        values[i + 1] = value.g.toUByte().toByte()
        values[i + 2] = value.b.toUByte().toByte()
        values[i + 3] = value.a.toUByte().toByte()
    }

    private fun indexOfVector(x: Int, y: Int) = ((y * sizeX) + x) * 4

    fun forEachCell(restriction: Range2d?, work: (Int, Int) -> (Unit)) =
        forEachCell(sizeX, sizeY, restriction, work)
}

/**
 * Return a value that's between start and end, with the fraction indicating how far along.
 */
fun mix(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

fun mix(a: Float4, b: Float4, fraction: Float) = Float4(
    mix(a.x, b.x, fraction),
    mix(a.y, b.y, fraction),
    mix(a.z, b.z, fraction),
    mix(a.w, b.w, fraction)
)

/**
 * For vectors of size 3, the original RenderScript has them occupy the same space as a size 4.
 * While RenderScript had a method to avoid this padding, it did not apply to Intrinsics.
 *
 * To preserve compatibility, the Toolkit doing the same.
 */
fun paddedSize(vectorSize: Int) = if (vectorSize == 3) 4 else vectorSize

/**
 * Create a ByteArray of the specified size filled with random data.
 */
fun randomByteArray(seed: Long, sizeX: Int, sizeY: Int, elementSize: Int): ByteArray {
    val r = Random(seed)
    return ByteArray(sizeX * sizeY * elementSize) { (r.nextInt(255) - 128).toByte() }
}

/**
 * Create a FloatArray of the specified size filled with random data.
 *
 * By default, the random data is between 0f and 1f. The factor can be used to scale that.
 */
fun randomFloatArray(
    seed: Long,
    sizeX: Int,
    sizeY: Int,
    elementSize: Int,
    factor: Float = 1f
): FloatArray {
    val r = Random(seed)
    return FloatArray(sizeX * sizeY * elementSize) { r.nextFloat() * factor }
}

/**
 * Create a cube of the specified size filled with random data.
 */
fun randomCube(seed: Long, cubeSize: Dimension): ByteArray {
    val r = Random(seed)
    return ByteArray(cubeSize.sizeX * cubeSize.sizeY * cubeSize.sizeZ * 4) {
        (r.nextInt(255) - 128).toByte()
    }
}

/**
 * Create the identity cube, i.e. one that if used in Lut3d, the output is the same as the input
 */
@ExperimentalUnsignedTypes
fun identityCube(cubeSize: Dimension): ByteArray {
    val data = ByteArray(cubeSize.sizeX * cubeSize.sizeY * cubeSize.sizeZ * 4)
    val cube = Rgba3dArray(data, cubeSize.sizeX, cubeSize.sizeY, cubeSize.sizeZ)
    for (z in 0 until cubeSize.sizeZ) {
        for (y in 0 until cubeSize.sizeY) {
            for (x in 0 until cubeSize.sizeX) {
                cube[x, y, z] =
                    byteArrayOf(
                        (x * 255 / (cubeSize.sizeX - 1)).toByte(),
                        (y * 255 / (cubeSize.sizeY - 1)).toByte(),
                        (z * 255 / (cubeSize.sizeZ - 1)).toByte(),
                        (255).toByte()
                    )
            }
        }
    }
    return data
}

fun randomYuvArray(seed: Long, sizeX: Int, sizeY: Int, format: YuvFormat): ByteArray {
    // YUV formats are not well defined for odd dimensions
    require(sizeX % 2 == 0 && sizeY % 2 == 0)
    val halfSizeX = sizeX / 2
    val halfSizeY = sizeY / 2
    var totalSize = 0
    when (format) {
        YuvFormat.YV12 -> {
            val strideX = roundUpTo16(sizeX)
            totalSize = strideX * sizeY + roundUpTo16(strideX / 2) * halfSizeY * 2
        }
        YuvFormat.NV21 -> totalSize = sizeX * sizeY + halfSizeX * halfSizeY * 2
        else -> require(false) { "Unknown YUV format $format" }
    }

    return randomByteArray(seed, totalSize, 1, 1)
}

/**
 * Converts a float to a byte, clamping to make it fit the limited range.
 */
@ExperimentalUnsignedTypes
fun Float.clampToUByte(): UByte = min(255, max(0, (this + 0.5f).toInt())).toUByte()

/**
 * Converts a FloatArray to UByteArray, clamping.
 */
@ExperimentalUnsignedTypes
fun FloatArray.clampToUByte() = UByteArray(size) { this[it].clampToUByte() }

/**
 * Limits an Int to what can fit in a UByte.
 */
fun Int.clampToUByteRange(): Int = min(255, max(0, this))

/**
 * Converts an Int to a UByte, clamping.
 */
@ExperimentalUnsignedTypes
fun Int.clampToUByte(): UByte = this.clampToUByteRange().toUByte()

/**
 * Converts a float (0f .. 1f) to a byte (0 .. 255)
 */
@ExperimentalUnsignedTypes
fun unitFloatClampedToUByte(num: Float): UByte = (num * 255f).clampToUByte()

/**
 * Convert a byte (0 .. 255) to a float (0f .. 1f)
 */
@ExperimentalUnsignedTypes
fun byteToUnitFloat(num: UByte) = num.toFloat() * 0.003921569f

@ExperimentalUnsignedTypes
fun UByteArray.toFloatArray() = FloatArray(size) { this[it].toFloat() }

/**
 * For each cell that's in the 2D array defined by sizeX and sizeY, and clipped down by the
 * restriction, invoke the work function.
 */
fun forEachCell(sizeX: Int, sizeY: Int, restriction: Range2d?, work: (Int, Int) -> (Unit)) {
    val startX = restriction?.startX ?: 0
    val startY = restriction?.startY ?: 0
    val endX = restriction?.endX ?: sizeX
    val endY = restriction?.endY ?: sizeY
    for (y in startY until endY) {
        for (x in startX until endX) {
            work(x, y)
        }
    }
}

operator fun FloatArray.times(other: FloatArray) = FloatArray(size) { this[it] * other[it] }
operator fun FloatArray.times(other: Float) = FloatArray(size) { this[it] * other }
operator fun FloatArray.plus(other: FloatArray) = FloatArray(size) { this[it] + other[it] }
operator fun FloatArray.minus(other: FloatArray) = FloatArray(size) { this[it] - other[it] }

fun renderScriptVectorElementForU8(rs: RenderScript?, vectorSize: Int): Element {
    when (vectorSize) {
        1 -> return Element.U8(rs)
        2 -> return Element.U8_2(rs)
        3 -> return Element.U8_3(rs)
        4 -> return Element.U8_4(rs)
    }
    throw java.lang.IllegalArgumentException("RenderScriptToolkit tests. Only vectors of size 1-4 are supported. $vectorSize provided.")
}

fun renderScriptVectorElementForI32(rs: RenderScript?, vectorSize: Int): Element {
    when (vectorSize) {
        1 -> return Element.I32(rs)
        2 -> return Element.I32_2(rs)
        3 -> return Element.I32_3(rs)
        4 -> return Element.I32_4(rs)
    }
    throw java.lang.IllegalArgumentException("RenderScriptToolkit tests. Only vectors of size 1-4 are supported. $vectorSize provided.")
}

/* When we'll handle floats
fun renderScriptVectorElementForF32(rs: RenderScript?, vectorSize: Int): Element {
    when (vectorSize) {
        1 -> return Element.F32(rs)
        2 -> return Element.F32_2(rs)
        3 -> return Element.F32_3(rs)
        4 -> return Element.F32_4(rs)
    }
    throw java.lang.IllegalArgumentException("RenderScriptToolkit tests. Only vectors of size 1-4 are supported. $vectorSize provided.")
}*/

fun renderScriptElementForBitmap(context: RenderScript, bitmap: Bitmap): Element {
    return when (val config = bitmap.config) {
        Bitmap.Config.ALPHA_8 -> Element.A_8(context)
        Bitmap.Config.ARGB_8888 -> Element.RGBA_8888(context)
        else -> throw IllegalArgumentException("RenderScript Toolkit can't support bitmaps with config $config.")
    }
}

fun getBitmapBytes(bitmap: Bitmap): ByteArray {
    val buffer: ByteBuffer = ByteBuffer.allocate(bitmap.byteCount)
    bitmap.copyPixelsToBuffer(buffer)
    return buffer.array()
}

fun vectorSizeOfBitmap(bitmap: Bitmap): Int {
    return when (val config = bitmap.config) {
        Bitmap.Config.ALPHA_8 -> 1
        Bitmap.Config.ARGB_8888 -> 4
        else -> throw IllegalArgumentException("RenderScript Toolkit can't support bitmaps with config $config.")
    }
}

fun duplicateBitmap(original: Bitmap): Bitmap {
    val copy = Bitmap.createBitmap(original.width, original.height, original.config)
    val canvas = Canvas(copy)
    canvas.drawBitmap(original, 0f, 0f, null)
    return copy
}

@ExperimentalUnsignedTypes
fun logArray(prefix: String, array: ByteArray, number: Int = 20) {
    val values = array.joinToString(limit = number) { it.toUByte().toString() }
    println("$prefix[${array.size}] $values}\n")
}

fun logArray(prefix: String, array: IntArray, number: Int = 20) {
    val values = array.joinToString(limit = number)
    println("$prefix[${array.size}] $values}\n")
}

fun logArray(prefix: String, array: FloatArray?, number: Int = 20) {
    val values = array?.joinToString(limit = number) { "%.2f".format(it) } ?: "(null)"
    println("$prefix[${array?.size}] $values}\n")
}

fun roundUpTo16(value: Int): Int {
    require(value >= 0)
    return (value + 15) and 15.inv()
}
