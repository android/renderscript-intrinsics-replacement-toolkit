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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.renderscript.RenderScript
import com.google.android.renderscript.BlendingMode
import com.google.android.renderscript.LookupTable
import com.google.android.renderscript.Range2d
import com.google.android.renderscript.Rgba3dArray
import com.google.android.renderscript.Toolkit
import com.google.android.renderscript.YuvFormat
import kotlin.math.abs
import kotlin.math.min

data class TestLayout(
    val sizeX: Int,
    val sizeY: Int,
    val restriction: Range2d?
)

// List of dimensions (sizeX, sizeY) to try when generating random data.
val commonLayoutsToTry = listOf(
    // Small layouts to start with
    TestLayout(3, 4, null),
    TestLayout(3, 4, Range2d(0, 1, 0, 3)),
    TestLayout(3, 4, Range2d(2, 3, 1, 4)),
    // The size of most of the original RenderScript Intrinsic tests
    TestLayout(160, 100, null),
    /* Other tests, if you're patient:
    TestLayout(10, 14, null),
    TestLayout(10, 14, Range2d(2, 3, 8, 14)),
    TestLayout(125, 227, Range2d(50, 125, 100, 227)),
    TestLayout(800, 600, null),
    // Weirdly shaped ones
    TestLayout(1, 1, null), // A single item
    estLayout(16000, 1, null), // A single item
    TestLayout(1, 16000, null), // One large row
    // A very large test
    TestLayout(1024, 2048, null),
    */
)

enum class Intrinsic {
    BLEND,
    BLUR,
    COLOR_MATRIX,
    CONVOLVE,
    HISTOGRAM,
    LUT,
    LUT3D,
    RESIZE,
    YUV_TO_RGB,
}


class Tester(context: Context, private val validate: Boolean) {
    private val renderscriptContext = RenderScript.create(context)
    private val testImage1 = BitmapFactory.decodeResource(context.resources, R.drawable.img800x450a)
    private val testImage2 = BitmapFactory.decodeResource(context.resources, R.drawable.img800x450b)

    init {
        validateTestImage(testImage1)
        validateTestImage(testImage2)
    }

    /**
     * Verify that the test images are in format that works for our tests.
     */
    private fun validateTestImage(bitmap: Bitmap) {
        require(bitmap.config == Bitmap.Config.ARGB_8888)
        require(bitmap.rowBytes == bitmap.width * 4) {
            "Can't handle bitmaps that have extra padding. " +
                "${bitmap.rowBytes} != ${bitmap.width} * 4." }
        require(bitmap.byteCount == bitmap.rowBytes * bitmap.height)
    }

    fun destroy() {
        renderscriptContext.destroy()
    }

    // Test one single intrinsic. Return true on success and false on failure.
    @ExperimentalUnsignedTypes
    fun testOne(intrinsic: Intrinsic, timer: TimingTracker) =
        when(intrinsic) {
            Intrinsic.BLEND -> ::testBlend
            Intrinsic.BLUR -> ::testBlur
            Intrinsic.COLOR_MATRIX -> ::testColorMatrix
            Intrinsic.CONVOLVE -> ::testConvolve
            Intrinsic.HISTOGRAM -> ::testHistogram
            Intrinsic.LUT -> ::testLut
            Intrinsic.LUT3D -> ::testLut3d
            Intrinsic.RESIZE -> ::testResize
            Intrinsic.YUV_TO_RGB -> ::testYuvToRgb
        }.let { test -> test(timer) }

    @ExperimentalUnsignedTypes
    private fun testBlend(timer: TimingTracker): Boolean {
        return BlendingMode.values().all { mode ->
            testOneBitmapBlend(timer, testImage1, testImage2, mode, null) and
                    testOneBitmapBlend(
                        timer, testImage1, testImage2, mode,
                        Range2d(6, 23, 2, 4)
                    ) and
                    commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
                        testOneRandomBlend(timer, sizeX, sizeY, mode, restriction)
                    }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomBlend(
        timer: TimingTracker,
        sizeX: Int,
        sizeY: Int,
        mode: BlendingMode,
        restriction: Range2d?
    ): Boolean {
        val sourceArray = randomByteArray(0x50521f0, sizeX, sizeY, 4)
        val destArray = randomByteArray(0x2932147, sizeX, sizeY, 4)
        // Make clones because these will be modified by the blend.
        val intrinsicDestArray = destArray.clone()
        val referenceDestArray = destArray.clone()
        val toolkitDestArray = destArray.clone()

        timer.measure("IntrinsicBlend") {
            intrinsicBlend(
                renderscriptContext, mode, sourceArray, intrinsicDestArray, sizeX, sizeY,
                restriction
            )
        }
        timer.measure("ToolkitBlend") {
            Toolkit.blend(mode, sourceArray, toolkitDestArray, sizeX, sizeY, restriction)
        }
        if (!validate) return true

        timer.measure("ReferenceBlend") {
            referenceBlend(mode, sourceArray, referenceDestArray, sizeX, sizeY, restriction)
        }

        return validateSame(
            "Blend_$mode", intrinsicDestArray, referenceDestArray, toolkitDestArray
        ) {
            println("blend $mode ($sizeX, $sizeY) $restriction")
            logArray("Blend_$mode src", sourceArray, 48)
            logArray("Blend_$mode dst", destArray, 48)
            logArray("Blend_$mode reference out", referenceDestArray, 48)
            logArray("Blend_$mode intrinsic out", intrinsicDestArray, 48)
            logArray("Blend_$mode toolkit   out", toolkitDestArray, 48)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapBlend(
        timer: TimingTracker,
        sourceBitmap: Bitmap,
        destBitmap: Bitmap,
        mode: BlendingMode,
        restriction: Range2d?
    ): Boolean {
        // Make clones because these will be modified by the blend.
        val intrinsicDestBitmap = duplicateBitmap(destBitmap)
        val toolkitDestBitmap = duplicateBitmap(destBitmap)
        val referenceDestBitmap = duplicateBitmap(destBitmap)

        timer.measure("IntrinsicBlend") {
            intrinsicBlend(
                renderscriptContext, mode, sourceBitmap, intrinsicDestBitmap, restriction
            )
        }
        timer.measure("ToolkitBlend") {
            Toolkit.blend(mode, sourceBitmap, toolkitDestBitmap, restriction)
        }
        if (!validate) return true

        val referenceDestArray = getBitmapBytes(referenceDestBitmap)
        timer.measure("ReferenceBlend") {
            referenceBlend(
                mode, getBitmapBytes(sourceBitmap), referenceDestArray, sourceBitmap.width,
                sourceBitmap.height, restriction
            )
        }

        val intrinsicDestArray = getBitmapBytes(intrinsicDestBitmap)
        val toolkitDestArray = getBitmapBytes(toolkitDestBitmap)
        return validateSame(
            "BlendBitmap_$mode", intrinsicDestArray, referenceDestArray, toolkitDestArray
        ) {
            println("BlendBitmap $mode $restriction")
            //logArray("BlendBitmap_$mode src", sourceArray, 48)
            //logArray("BlendBitmap_$mode dst", destArray, 48)
            logArray("BlendBitmap_$mode reference out", referenceDestArray, 48)
            logArray("BlendBitmap_$mode intrinsic out", intrinsicDestArray, 48)
            logArray("BlendBitmap_$mode toolkit   out", toolkitDestArray, 48)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testBlur(timer: TimingTracker): Boolean {
        return arrayOf(1, 3, 8, 25).all { radius ->
            testOneBitmapBlur(timer, testImage1, radius, null) and
                    testOneBitmapBlur(timer, testImage1, radius, Range2d(6, 23, 2, 4)) and
                    commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
                        arrayOf(1, 4).all { vectorSize ->
                            testOneRandomBlur(timer, vectorSize, sizeX, sizeY, radius, restriction)
                        }
                    }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomBlur(
        timer: TimingTracker,
        vectorSize: Int,
        sizeX: Int,
        sizeY: Int,
        radius: Int,
        restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, vectorSize)
        val intrinsicOutArray = timer.measure("IntrinsicBlur") {
            intrinsicBlur(
                renderscriptContext, inputArray, vectorSize, sizeX, sizeY, radius, restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitBlur") {
            Toolkit.blur(inputArray, vectorSize, sizeX, sizeY, radius, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceBlur") {
            referenceBlur(inputArray, vectorSize, sizeX, sizeY, radius, restriction)
        }
        return validateSame("blur", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("blur $vectorSize ($sizeX, $sizeY) radius = $radius $restriction")
            logArray("blur input        ", inputArray)
            logArray("blur reference out", referenceOutArray)
            logArray("blur intrinsic out", intrinsicOutArray)
            logArray("blur toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapBlur(
        timer: TimingTracker,
        bitmap: Bitmap,
        radius: Int,
        restriction: Range2d?
    ): Boolean {
        val intrinsicOutArray = timer.measure("IntrinsicBlur") {
            intrinsicBlur(renderscriptContext, bitmap, radius, restriction)
        }

        val toolkitOutBitmap = timer.measure("ToolkitBlur") {
            Toolkit.blur(bitmap, radius, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceBlur") {
            referenceBlur(
                getBitmapBytes(bitmap),
                vectorSizeOfBitmap(bitmap),
                bitmap.width,
                bitmap.height,
                radius,
                restriction
            )
        }

        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame("blur", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("BlurBitmap ${bitmap.config} $radius $restriction")
            logArray("blur reference out", referenceOutArray)
            logArray("blur intrinsic out", intrinsicOutArray)
            logArray("blur toolkit   out", toolkitOutArray)
        }
    }

    enum class ColorMatrixConversionType {
        RGB_TO_YUV,
        YUV_TO_RGB,
        GREYSCALE,
        RANDOM
    }

    @ExperimentalUnsignedTypes
    private fun testColorMatrix(timer: TimingTracker): Boolean {
        return ColorMatrixConversionType.values().all { conversion ->
            testOneBitmapColorMatrix(timer, testImage1, conversion, null) and
                    testOneBitmapColorMatrix(
                        timer,
                        testImage1,
                        conversion,
                        Range2d(6, 23, 2, 4)
                    ) and
                    commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
                        (1..4).all { inputVectorSize ->
                            (1..4).all { outputVectorSize ->
                                testOneRandomColorMatrix(
                                    timer,
                                    inputVectorSize,
                                    sizeX,
                                    sizeY,
                                    outputVectorSize,
                                    conversion,
                                    restriction
                                )
                            }
                        }
                    }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomColorMatrix(
        timer: TimingTracker,
        inputVectorSize: Int,
        sizeX: Int,
        sizeY: Int,
        outputVectorSize: Int,
        conversion: ColorMatrixConversionType,
        restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, paddedSize(inputVectorSize))
        val addVector = randomFloatArray(0x243238, 4, 1, 1, 0.3f)
        val matrix = when (conversion) {
            ColorMatrixConversionType.RGB_TO_YUV -> Toolkit.rgbToYuvMatrix
            ColorMatrixConversionType.YUV_TO_RGB -> Toolkit.yuvToRgbMatrix
            ColorMatrixConversionType.GREYSCALE -> Toolkit.greyScaleColorMatrix
            ColorMatrixConversionType.RANDOM -> randomFloatArray(0x234348, 4, 4, 1)
        }

        val intrinsicOutArray = timer.measure("IntrinsicColorMatrix") {
            intrinsicColorMatrix(
                renderscriptContext,
                conversion,
                inputArray,
                inputVectorSize,
                sizeX,
                sizeY,
                outputVectorSize,
                matrix,
                addVector,
                restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitColorMatrix") {
            Toolkit.colorMatrix(
                inputArray,
                inputVectorSize,
                sizeX,
                sizeY,
                outputVectorSize,
                matrix,
                addVector,
                restriction
            )
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceColorMatrix") {
            referenceColorMatrix(
                inputArray, inputVectorSize, sizeX, sizeY, outputVectorSize, matrix, addVector,
                restriction
            )
        }

        return validateSame("colorMatrix", intrinsicOutArray, referenceOutArray, toolkitOutArray,
            outputVectorSize == 3) {
            println("colorMatrix ($sizeX, $sizeY) $inputVectorSize->$outputVectorSize $restriction")
            logArray("colorMatrix matrix   ", matrix, 16)
            logArray("colorMatrix addVector", addVector, 4)
            logArray("colorMatrix in           ", inputArray)
            logArray("colorMatrix reference out", referenceOutArray, 300)
            logArray("colorMatrix intrinsic out", intrinsicOutArray, 300)
            logArray("colorMatrix toolkit   out", toolkitOutArray, 300)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapColorMatrix(
        timer: TimingTracker,
        bitmap: Bitmap,
        conversion: ColorMatrixConversionType,
        restriction: Range2d?
    ): Boolean {
        val addVector = randomFloatArray(0x243238, 4, 1, 1, 0.3f)
        val matrix = when (conversion) {
            ColorMatrixConversionType.RGB_TO_YUV -> Toolkit.rgbToYuvMatrix
            ColorMatrixConversionType.YUV_TO_RGB -> Toolkit.yuvToRgbMatrix
            ColorMatrixConversionType.GREYSCALE -> Toolkit.greyScaleColorMatrix
            ColorMatrixConversionType.RANDOM -> randomFloatArray(0x234348, 4, 4, 1)
        }

        val intrinsicOutArray = timer.measure("IntrinsicColorMatrix") {
            intrinsicColorMatrix(
                renderscriptContext, conversion, bitmap, matrix, addVector, restriction
            )
        }
        val toolkitOutBitmap = timer.measure("ToolkitColorMatrix") {
            Toolkit.colorMatrix(bitmap, matrix, addVector, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceColorMatrix") {
            referenceColorMatrix(
                getBitmapBytes(bitmap), vectorSizeOfBitmap(bitmap), bitmap.width, bitmap.height,
                vectorSizeOfBitmap(bitmap), matrix, addVector, restriction
            )
        }

        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame("ColorMatrix", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("colorMatrixBitmap $restriction")
            logArray("colorMatrixBitmap matrix   ", matrix, 16)
            logArray("colorMatrixBitmap addVector", addVector, 4)
            logArray("colorMatrixBitmap reference out", referenceOutArray)
            logArray("colorMatrixBitmap intrinsic out", intrinsicOutArray)
            logArray("colorMatrixBitmap toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testConvolve(timer: TimingTracker): Boolean {
        val coefficientsToTry = listOf(
            randomFloatArray(0x2937021, 3, 3, 1, 0.1f),
            randomFloatArray(0x2937021, 5, 5, 1, 0.05f)
        )
        return coefficientsToTry.all { coefficients ->
            testOneBitmapConvolve(timer, testImage1, coefficients, null) and
                    testOneBitmapConvolve(timer, testImage1, coefficients, Range2d(6, 23, 2, 4)) and

                    commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
                        (1..4).all { vectorSize ->
                            testOneRandomConvolve(
                                timer,
                                vectorSize,
                                sizeX,
                                sizeY,
                                coefficients,
                                restriction
                            )
                        }
                    }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomConvolve(
        timer: TimingTracker,
        vectorSize: Int,
        sizeX: Int,
        sizeY: Int,
        coefficients: FloatArray,
        restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, paddedSize(vectorSize))

        val intrinsicOutArray = timer.measure("IntrinsicConvolve") {
            intrinsicConvolve(
                renderscriptContext, inputArray, vectorSize, sizeX, sizeY, coefficients, restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitConvolve") {
            Toolkit.convolve(inputArray, vectorSize, sizeX, sizeY, coefficients, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceConvolve") {
            referenceConvolve(inputArray, vectorSize, sizeX, sizeY, coefficients, restriction)
        }

        val task = if (coefficients.size == 9) "convolve3x3 $vectorSize" else "convolve5x5 $vectorSize"
        return validateSame(task, intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("Convolve $vectorSize ($sizeX, $sizeY) $restriction")
            logArray("Convolve coefficients", coefficients, 25)
            logArray("Convolve in           ", inputArray)
            logArray("Convolve reference out", referenceOutArray)
            logArray("Convolve intrinsic out", intrinsicOutArray)
            logArray("Convolve toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapConvolve(
        timer: TimingTracker,
        bitmap: Bitmap,
        coefficients: FloatArray,
        restriction: Range2d?
    ): Boolean {
        val intrinsicOutArray = timer.measure("IntrinsicConvolve") {
            intrinsicConvolve(renderscriptContext, bitmap, coefficients, restriction)
        }
        val toolkitOutBitmap = timer.measure("ToolkitConvolve") {
            Toolkit.convolve(bitmap, coefficients, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceConvolve") {
            referenceConvolve(
                getBitmapBytes(bitmap), vectorSizeOfBitmap(bitmap), bitmap.width, bitmap.height,
                coefficients, restriction
            )
        }

        val task = if (coefficients.size == 9) "convolve3x3" else "convolve5x5"
        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame(task, intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("ConvolveBitmap $restriction")
            logArray("ConvolveBitmap coefficients", coefficients, 25)
            //logArray("ConvolveBitmap in           ", inputArray)
            logArray("ConvolveBitmap reference out", referenceOutArray)
            logArray("ConvolveBitmap intrinsic out", intrinsicOutArray)
            logArray("ConvolveBitmap toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testHistogram(timer: TimingTracker): Boolean {
        val coefficients = floatArrayOf(0.1f, 0.3f, 0.5f, 0.05f)
        return testOneBitmapHistogram(timer, testImage1, null) and
                testOneBitmapHistogram(timer, testImage1, Range2d(6, 23, 2, 4)) and
                testOneBitmapHistogramDot(timer, testImage1, null, null) and
                testOneBitmapHistogramDot(timer, testImage1, coefficients, null) and
                testOneBitmapHistogramDot(timer, testImage1, coefficients, Range2d(6, 23, 2, 4)) and
        commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
            (1..4).all { vectorSize ->
                testOneRandomHistogram(timer, vectorSize, sizeX, sizeY, restriction) &&
                        testOneRandomHistogramDot(
                            timer,
                            vectorSize,
                            sizeX,
                            sizeY,
                            null,
                            restriction
                        ) &&
                        testOneRandomHistogramDot(
                            timer,
                            vectorSize,
                            sizeX,
                            sizeY,
                            coefficients.sliceArray(0 until vectorSize),
                            restriction
                        )
            }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomHistogram(
        timer: TimingTracker,
        vectorSize: Int,
        sizeX: Int,
        sizeY: Int,
        restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, paddedSize(vectorSize))

        val intrinsicOutput = timer.measure("IntrinsicHistogram") {
            intrinsicHistogram(
                renderscriptContext, inputArray, vectorSize, sizeX, sizeY, restriction
            )
        }
        val toolkitOutput = timer.measure("ToolkitHistogram") {
            Toolkit.histogram(inputArray, vectorSize, sizeX, sizeY, restriction)
        }
        if (!validate) return true

        val referenceOutput = timer.measure("ReferenceHistogram") {
            referenceHistogram(
                inputArray, vectorSize, sizeX, sizeY, restriction
            )
        }

        return validateSame("histogram", intrinsicOutput, referenceOutput, toolkitOutput, 0) {
            println("histogram $vectorSize ($sizeX, $sizeY) $restriction")
            logArray("histogram in           ", inputArray, 200)
            logArray("histogram reference out", referenceOutput, 200)
            logArray("histogram intrinsic out", intrinsicOutput, 200)
            logArray("histogram toolkit   out", toolkitOutput, 200)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapHistogram(
        timer: TimingTracker,
        bitmap: Bitmap,
        restriction: Range2d?
    ): Boolean {
        val intrinsicOutput = timer.measure("IntrinsicHistogram") {
            intrinsicHistogram(renderscriptContext, bitmap, restriction)
        }
        val toolkitOutput = timer.measure("ToolkitHistogram") {
            Toolkit.histogram(bitmap, restriction)
        }
        if (!validate) return true

        val referenceOutput = timer.measure("ReferenceHistogram") {
            referenceHistogram(
                getBitmapBytes(bitmap), vectorSizeOfBitmap(bitmap), bitmap.width, bitmap.height,
                restriction
            )
        }

        return validateSame("histogram", intrinsicOutput, referenceOutput, toolkitOutput, 0) {
            println("HistogramBitmap $restriction")
            logArray("HistogramBitmap reference out", referenceOutput)
            logArray("HistogramBitmap intrinsic out", intrinsicOutput)
            logArray("HistogramBitmap toolkit   out", toolkitOutput)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomHistogramDot(
        timer: TimingTracker,
        vectorSize: Int,
        sizeX: Int,
        sizeY: Int,
        coefficients: FloatArray?, restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, paddedSize(vectorSize))

        val intrinsicOutArray = timer.measure("IntrinsicHistogramDot") {
            intrinsicHistogramDot(
                renderscriptContext, inputArray, vectorSize, sizeX, sizeY, coefficients, restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitHistogramDot") {
            Toolkit.histogramDot(
                inputArray, vectorSize, sizeX, sizeY, coefficients, restriction
            )
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceHistogramDot") {
            referenceHistogramDot(inputArray, vectorSize, sizeX, sizeY, coefficients, restriction)
        }

        return validateSame("histogramDot", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("histogramDot $vectorSize ($sizeX, $sizeY) $restriction")
            logArray("histogramDot coefficients ", coefficients)
            logArray("histogramDot in           ", inputArray)
            logArray("histogramDot reference out", referenceOutArray, 256)
            logArray("histogramDot intrinsic out", intrinsicOutArray, 256)
            logArray("histogramDot toolkit   out", toolkitOutArray, 256)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapHistogramDot(
        timer: TimingTracker,
        bitmap: Bitmap,
        coefficients: FloatArray?,
        restriction: Range2d?
    ): Boolean {
        val intrinsicOutArray = timer.measure("IntrinsicHistogramDot") {
            intrinsicHistogramDot(renderscriptContext, bitmap, coefficients, restriction)
        }
        val toolkitOutArray = timer.measure("ToolkitHistogramDot") {
            Toolkit.histogramDot(bitmap, coefficients, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceHistogramDot") {
            referenceHistogramDot(
                getBitmapBytes(bitmap),
                vectorSizeOfBitmap(bitmap),
                bitmap.width,
                bitmap.height,
                coefficients,
                restriction
            )
        }

        return validateSame(
            "HistogramDotBitmap",
            intrinsicOutArray,
            referenceOutArray,
            toolkitOutArray
        ) {
            println("HistogramDotBitmap $restriction")
            logArray("HistogramDotBitmap coefficients ", coefficients)
            //logArray("HistogramDotBitmap in           ", inputArray)
            logArray("HistogramDotBitmap reference out", referenceOutArray, 256)
            logArray("HistogramDotBitmap intrinsic out", intrinsicOutArray, 256)
            logArray("HistogramDotBitmap toolkit   out", toolkitOutArray, 256)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testLut(timer: TimingTracker): Boolean {
        return testOneBitmapLut(timer, testImage1, null) and
                testOneBitmapLut(timer, testImage1, Range2d(6, 23, 2, 4)) and
        commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
            testOneRandomLut(timer, sizeX, sizeY, restriction)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomLut(
        timer: TimingTracker,
        sizeX: Int,
        sizeY: Int,
        restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, 4)
        val newRed = randomByteArray(0x32425, 256, 1, 1)
        val newGreen = randomByteArray(0x1F3225, 256, 1, 1)
        val newBlue = randomByteArray(0x32D4F27, 256, 1, 1)
        val newAlpha = randomByteArray(0x3A20001, 256, 1, 1)
        val table = LookupTable()
        table.red = newRed
        table.blue = newBlue
        table.green = newGreen
        table.alpha = newAlpha

        val intrinsicOutArray = timer.measure("IntrinsicLUT") {
            intrinsicLut(
                renderscriptContext, inputArray, sizeX, sizeY, newRed, newGreen, newBlue, newAlpha,
                restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitLUT") {
            Toolkit.lut(inputArray, sizeX, sizeY, table, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceLUT") {
            referenceLut(inputArray, sizeX, sizeY, table, restriction)
        }

        return validateSame("LUT", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("lut ($sizeX, $sizeY) $restriction")
            logArray("LUT red  ", newRed, 256)
            logArray("LUT green", newGreen, 256)
            logArray("LUT blue ", newBlue, 256)
            logArray("LUT alpha", newAlpha, 256)
            logArray("LUT in           ", inputArray)
            logArray("LUT reference out", referenceOutArray)
            logArray("LUT intrinsic out", intrinsicOutArray)
            logArray("LUT toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapLut(
        timer: TimingTracker,
        bitmap: Bitmap,
        restriction: Range2d?
    ): Boolean {
        val newRed = randomByteArray(0x32425, 256, 1, 1)
        val newGreen = randomByteArray(0x1F3225, 256, 1, 1)
        val newBlue = randomByteArray(0x32D4F27, 256, 1, 1)
        val newAlpha = randomByteArray(0x3A20001, 256, 1, 1)
        val table = LookupTable()
        table.red = newRed
        table.blue = newBlue
        table.green = newGreen
        table.alpha = newAlpha

        val intrinsicOutArray = timer.measure("IntrinsicLUT") {
            intrinsicLut(
                renderscriptContext, bitmap, newRed, newGreen, newBlue, newAlpha, restriction
            )
        }
        val toolkitOutBitmap = timer.measure("ToolkitLUT") {
            Toolkit.lut(bitmap, table, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceLUT") {
            referenceLut(
                getBitmapBytes(bitmap),
                bitmap.width,
                bitmap.height,
                table,
                restriction
            )
        }

        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame("LutBitmap", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("LutBitmap $restriction")
            logArray("LutBitmap red  ", newRed, 256)
            logArray("LutBitmap green", newGreen, 256)
            logArray("LutBitmap blue ", newBlue, 256)
            logArray("LutBitmap alpha", newAlpha, 256)
            //logArray("LutBitmap in           ", inputArray, 80)
            logArray("LutBitmap reference out", referenceOutArray)
            logArray("LutBitmap intrinsic out", intrinsicOutArray)
            logArray("LutBitmap toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testLut3d(timer: TimingTracker): Boolean {
        val cubeSizesToTry = listOf(
            Dimension(2, 2, 2),
            Dimension(32, 32, 16),
            Dimension(256, 256, 256)
        )
        return cubeSizesToTry.all { cubeSize ->
                val identityCube = identityCube(cubeSize)
                val randomCube = randomCube(0x23424, cubeSize)
                testOneBitmapLut3d(timer, testImage1, cubeSize, identityCube, 1, null) and
                        testOneBitmapLut3d(timer, testImage2, cubeSize, randomCube, 3, null) and
                        testOneBitmapLut3d(timer, testImage2, cubeSize, randomCube, 3, Range2d(6, 23, 2, 4)) and
                commonLayoutsToTry.all { (sizeX, sizeY, restriction) ->
                    testOneRandomLut3d(timer, sizeX, sizeY, cubeSize, identityCube, 1, restriction) &&
                            testOneRandomLut3d(
                                timer,
                                sizeX,
                                sizeY,
                                cubeSize,
                                randomCube,
                                3,
                                restriction
                            )
                }
            }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomLut3d(
        timer: TimingTracker,
        sizeX: Int,
        sizeY: Int,
        cubeSize: Dimension,
        cubeArray: ByteArray,
        allowedIntError: Int, restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, sizeX, sizeY, 4)

        val intrinsicOutArray = timer.measure("IntrinsicLut3d") {
            intrinsicLut3d(
                renderscriptContext, inputArray, sizeX, sizeY, cubeArray, cubeSize, restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitLut3d") {
            val toolkitCube = Rgba3dArray(cubeArray, cubeSize.sizeX, cubeSize.sizeY, cubeSize.sizeZ)
            Toolkit.lut3d(inputArray, sizeX, sizeY, toolkitCube, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceLut3d") {
            val cube = Rgba3dArray(cubeArray, cubeSize.sizeX, cubeSize.sizeY, cubeSize.sizeZ)
            referenceLut3d(inputArray, sizeX, sizeY, cube, restriction)
        }

        return validateSame(
            "lut3d",
            intrinsicOutArray,
            referenceOutArray,
            toolkitOutArray,
            false,
            allowedIntError
        ) {
            println("lut3d ($sizeX, $sizeY) $restriction")
            logArray("lut3d cube", cubeArray, 256)
            logArray("lut3d in           ", inputArray, 64)
            logArray("lut3d reference out", referenceOutArray, 64)
            logArray("lut3d intrinsic out", intrinsicOutArray, 64)
            logArray("lut3d toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapLut3d(
        timer: TimingTracker,
        bitmap: Bitmap,
        cubeSize: Dimension,
        cubeArray: ByteArray,
        allowedIntError: Int, restriction: Range2d?
    ): Boolean {
        val intrinsicOutArray = timer.measure("IntrinsicLut3d") {
            intrinsicLut3d(renderscriptContext, bitmap, cubeArray, cubeSize, restriction)
        }
        val toolkitOutBitmap = timer.measure("ToolkitLut3d") {
            val toolkitCube = Rgba3dArray(cubeArray, cubeSize.sizeX, cubeSize.sizeY, cubeSize.sizeZ)
            Toolkit.lut3d(bitmap, toolkitCube, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceLut3d") {
            val cube = Rgba3dArray(cubeArray, cubeSize.sizeX, cubeSize.sizeY, cubeSize.sizeZ)
            referenceLut3d(getBitmapBytes(bitmap), bitmap.width, bitmap.height, cube, restriction)
        }

        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame(
            "Lut3dBitmap",
            intrinsicOutArray,
            referenceOutArray,
            toolkitOutArray,
            false,
            allowedIntError
        ) {
            println("Lut3dBitmap $restriction")
            logArray("Lut3dBitmap cube", cubeArray, 256)
            //logArray("Lut3dBitmap in           ", inputArray, 64)
            logArray("Lut3dBitmap reference out", referenceOutArray, 64)
            logArray("Lut3dBitmap intrinsic out", intrinsicOutArray, 64)
            logArray("Lut3dBitmap toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testResize(timer: TimingTracker): Boolean {
        val factorsToTry = listOf(
            Pair(1f, 1f),
            Pair(0.5f, 1f),
            Pair(2f, 2f),
            Pair(0.5f, 2f),
            Pair(2f, 0.5f),
            // The RenderScript Intrinsic tests used the above factors. It's tempting to use
            // less regular ones like Pair(6.37f, 0.17f) however this creates small offset
            // errors between the result provided by the C++ code and the SIMD code. This is
            // due to the SIMD code using a scaled integer to increment going from one pixel to the
            // next, while the C++ code uses float operations.
        )
        val layoutsToTry = listOf(
            TestLayout(37, 47, null),
            TestLayout(60, 10, null),
            TestLayout(6, 4, Range2d(1, 3, 0, 2)),
            TestLayout(10, 14, Range2d(2, 3, 3, 7)),
        )

        return factorsToTry.all { (scaleX, scaleY) ->
            // Do one resize that's greater than 4x, as that's used in the code but don't do it
            // for everything, as some images will get very large
            testOneRandomResize(timer, 1, 25, 30, 6f, 6f, null) and
            testOneBitmapResize(timer, testImage1, scaleX, scaleY, null) and
                    testOneBitmapResize(timer, testImage1, scaleX, scaleY, Range2d(6, 23, 2, 4)) and
                    layoutsToTry.all { (sizeX, sizeY, restriction) ->
                        (1..4).all { vectorSize ->
                            testOneRandomResize(
                                timer,
                                vectorSize,
                                sizeX,
                                sizeY,
                                scaleX,
                                scaleY,
                                restriction
                            )
                        }
                    }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomResize(
        timer: TimingTracker,
        vectorSize: Int,
        inSizeX: Int,
        inSizeY: Int,
        scaleX: Float,
        scaleY: Float,
        restriction: Range2d?
    ): Boolean {
        val inputArray = randomByteArray(0x50521f0, inSizeX, inSizeY, paddedSize(vectorSize))
        val outSizeX = (inSizeX * scaleX).toInt()
        val outSizeY = (inSizeY * scaleY).toInt()

        val intrinsicOutArray = timer.measure("IntrinsicResize") {
            intrinsicResize(
                renderscriptContext, inputArray, vectorSize, inSizeX, inSizeY, outSizeX, outSizeY,
                restriction
            )
        }
        val toolkitOutArray = timer.measure("ToolkitResize") {
            Toolkit.resize(
                inputArray, vectorSize, inSizeX, inSizeY, outSizeX, outSizeY, restriction
            )
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceResize") {
            referenceResize(
                inputArray, vectorSize, inSizeX, inSizeY, outSizeX, outSizeY, restriction
            )
        }

        return validateSame("resize", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("resize $vectorSize ($inSizeX, $inSizeY) by ($scaleX, $scaleY) to ($outSizeX, $outSizeY), $restriction")
            logArray("resize in           ", inputArray)
            logArray("resize reference out", referenceOutArray)
            logArray("resize intrinsic out", intrinsicOutArray)
            logArray("resize toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneBitmapResize(
        timer: TimingTracker,
        bitmap: Bitmap,
        scaleX: Float,
        scaleY: Float,
        restriction: Range2d?
    ): Boolean {
        // println("Doing resize $inSizeX x $inSizeY x $vectorSize, $scaleX x $scaleY, $restriction")
        val outSizeX = (bitmap.width * scaleX).toInt()
        val outSizeY = (bitmap.height * scaleY).toInt()

        val intrinsicOutArray = timer.measure("IntrinsicResize") {
            intrinsicResize(renderscriptContext, bitmap, outSizeX, outSizeY, restriction)
        }
        val toolkitOutBitmap = timer.measure("ToolkitResize") {
            Toolkit.resize(bitmap, outSizeX, outSizeY, restriction)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceResize") {
            referenceResize(
                getBitmapBytes(bitmap),
                vectorSizeOfBitmap(bitmap),
                bitmap.width,
                bitmap.height,
                outSizeX,
                outSizeY,
                restriction
            )
        }

        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame("ResizeBitmap", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("ResizeBitmap by ($scaleX, $scaleY) to ($outSizeX, $outSizeY), $restriction")
            //logArray("ResizeBitmap in           ", inputArray, 100)
            logArray("ResizeBitmap reference out", referenceOutArray)
            logArray("ResizeBitmap intrinsic out", intrinsicOutArray)
            logArray("ResizeBitmap toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testYuvToRgb(timer: TimingTracker): Boolean {
        val layoutsToTry = listOf(
            // Don't try sizeX with odd values. That's not allowed by definition of some
            // of the video formats.
            TestLayout(10, 14, null),
            TestLayout(64, 40, null),
            TestLayout(96, 94, null),
        )
        return layoutsToTry.all { (sizeX, sizeY, _) ->
            YuvFormat.values().all { format ->
                testOneRandomYuvToRgb(timer, sizeX, sizeY, format) and
                testOneRandomYuvToRgbBitmap(timer, sizeX, sizeY, format)
            }
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomYuvToRgb(
        timer: TimingTracker,
        sizeX: Int,
        sizeY: Int,
        format: YuvFormat
    ): Boolean {
        // The RenderScript Intrinsic does not handle this combination correctly.
        if (format == YuvFormat.YV12 && sizeX % 32 != 0) {
            return true
        }
        val inputArray = randomYuvArray(0x50521f0, sizeX, sizeY, format)

        val intrinsicOutArray = timer.measure("IntrinsicYuvToRgb") {
            intrinsicYuvToRgb(renderscriptContext, inputArray, sizeX, sizeY, format)
        }
        val toolkitOutArray = timer.measure("ToolkitYuvToRgb") {
            Toolkit.yuvToRgb(inputArray, sizeX, sizeY, format)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceYuvToRgb") {
            referenceYuvToRgb(inputArray, sizeX, sizeY, format)
        }

        return validateSame("yuvToRgb", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("yuvToRgb ($sizeX, $sizeY) $format")
            logArray("yuvToRgb in           ", inputArray)
            logArray("yuvToRgb reference out", referenceOutArray)
            logArray("yuvToRgb intrinsic out", intrinsicOutArray)
            logArray("yuvToRgb toolkit   out", toolkitOutArray)
        }
    }

    @ExperimentalUnsignedTypes
    private fun testOneRandomYuvToRgbBitmap(
        timer: TimingTracker,
        sizeX: Int,
        sizeY: Int,
        format: YuvFormat
    ): Boolean {
        // The RenderScript Intrinsic does not handle this combination correctly.
        if (format == YuvFormat.YV12 && sizeX % 32 != 0) {
            return true
        }
        val inputArray = randomYuvArray(0x50521f0, sizeX, sizeY, format)

        val intrinsicOutArray = timer.measure("IntrinsicYuvToRgb") {
            intrinsicYuvToRgb(renderscriptContext, inputArray, sizeX, sizeY, format)
        }
        val toolkitOutBitmap = timer.measure("ToolkitYuvToRgb") {
            Toolkit.yuvToRgbBitmap(inputArray, sizeX, sizeY, format)
        }
        if (!validate) return true

        val referenceOutArray = timer.measure("ReferenceYuvToRgb") {
            referenceYuvToRgb(inputArray, sizeX, sizeY, format)
        }

        val toolkitOutArray = getBitmapBytes(toolkitOutBitmap)
        return validateSame("yuvToRgb", intrinsicOutArray, referenceOutArray, toolkitOutArray) {
            println("yuvToRgb ($sizeX, $sizeY) $format")
            logArray("yuvToRgb in           ", inputArray)
            logArray("yuvToRgb reference out", referenceOutArray)
            logArray("yuvToRgb intrinsic out", intrinsicOutArray)
            logArray("yuvToRgb toolkit   out", toolkitOutArray)
        }
    }

    /**
     * Verifies that the arrays returned by the Intrinsic, the reference code, and the Toolkit
     * are all within a margin of error.
     *
     * RenderScript Intrinsic test (rc/android/cts/rscpp/RSCppTest.java) used 3 for ints.
     * For floats, rc/android/cts/rscpp/verify.rscript uses 0.0001f.
     */
    @ExperimentalUnsignedTypes
    private fun validateSame(
        task: String,
        intrinsic: ByteArray,
        reference: ByteArray,
        toolkit: ByteArray,
        skipFourth: Boolean = false,
        allowedIntDelta: Int = 3,
        errorLogging: () -> Unit
    ): Boolean {
        val success = validateAgainstReference(
            task, reference, "Intrinsic", intrinsic, skipFourth, allowedIntDelta
        ) and validateAgainstReference(
            task, reference, "Toolkit", toolkit, skipFourth, allowedIntDelta
        )
        if (!success) {
            println("$task FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!")
            errorLogging()
        }
        return success
    }

    private fun validateSame(
        task: String,
        intrinsic: IntArray,
        reference: IntArray,
        toolkit: IntArray,
        allowedIntDelta: Int = 3,
        errorLogging: () -> Unit
    ): Boolean {
        val success = validateAgainstReference(
            task, reference, "Intrinsic", intrinsic, allowedIntDelta
        ) and validateAgainstReference(
            task, reference, "Toolkit", toolkit, allowedIntDelta
        )
        if (!success) {
            println("$task FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!FAIL!")
            errorLogging()
        }
        return success
    }

    @ExperimentalUnsignedTypes
    private fun validateAgainstReference(
        task: String,
        in1: ByteArray,
        name2: String,
        in2: ByteArray,
        skipFourth: Boolean,
        allowedIntDelta: Int
    ): Boolean {
        if (in1.size != in2.size) {
            println("$task. Sizes don't match: Reference ${in1.size}, $name2 ${in2.size}")
            return false
        }
        var same = true
        val maxDetails = 80
        val diffs = CharArray(min(in1.size, maxDetails)) {'.'}
        for (i in in1.indices) {
            if (skipFourth && i % 4 == 3) {
                continue
            }
            val delta = abs(in1[i].toUByte().toInt() - in2[i].toUByte().toInt())
            if (delta > allowedIntDelta) {
                if (same) {
                    println(
                        "$task. At $i, Reference is ${in1[i].toUByte()}, $name2 is ${in2[i].toUByte()}"
                    )
                }
                if (i < maxDetails) diffs[i] = 'X'
                same = false
            }
        }
        if (!same) {
            for (i in 0 until (min(in1.size, maxDetails) / 4)) print("%-3d|".format(i))
            println()
            println(diffs)
        }
        return same
    }

    private fun validateAgainstReference(
        task: String,
        in1: IntArray,
        name2: String,
        in2: IntArray,
        allowedIntDelta: Int
    ): Boolean {
        if (in1.size != in2.size) {
            println("$task. Sizes don't match: Reference ${in1.size}, $name2 ${in2.size}")
            return false
        }
        for (i in in1.indices) {
            val delta = abs(in1[i] - in2[i])
            if (delta > allowedIntDelta) {
                println("$task. At $i, Reference is ${in1[i]}, $name2 is ${in2[i]}")
                return false
            }
        }
        return true
    }
}
