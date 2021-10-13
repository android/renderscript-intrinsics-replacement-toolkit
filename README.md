# RenderScript Intrinsics Replacement Toolkit - v0.8 BETA

This Toolkit provides a collection of high-performance image manipulation functions
like blur, blend, and resize. It can be used as a stand-alone replacement for most
of the deprecated RenderScript Intrinsics functions. 

The Toolkit provides ten image manipulation functions:
* blend,
* blur,
* color matrix,
* convolve,
* histogram and histogramDot,
* LUT (lookup table) and LUT 3D,
* resize, and
* YUV to RGB.

The Toolkit provides a C++ and a Java/Kotlin interface. It is packaged as an Android
library that you can add to your project.

These functions execute multithreaded on the CPU. They take advantage of Neon/AdvSimd
on Arm processors and SSE on Intel's.

Compared to the RenderScript Intrinsics, this Toolkit is simpler to use and twice as fast
when executing on the CPU. However RenderScript Intrinsics allow more flexibility for
the type of allocations supported. This toolkit does not support allocations of floats;
most the functions support ByteArrays and Bitmaps.

You should instantiate the Toolkit once and reuse it throughout your application.
On instantiation, the Toolkit creates a thread pool that's used for processing all the functions.
You can limit the number of poolThreads used by the Toolkit via the constructor. The poolThreads
are destroyed once the Toolkit is destroyed, after any pending work is done.

This library is thread safe. You can call methods from different poolThreads. The functions will
execute sequentially.

 
## Future improvement ideas:

* Turn the Java version of the Toolkit into a singleton, to reduce the chance that someone inadventarly
create multiple threadpools.

* Support ByteBuffer. It should be straightforward to use GetDirectBufferAddress in JniEntryPoints.cpp.
See https://developer.android.com/training/articles/perf-jni and jni_helper.h.

* The RenderScript Intrinsics support floats for colorMatrix, convolve, and resize. The Toolkit does not.

* Allow in place update of buffers, or writing to an existing byte array.

* For Blur, we could have a version that accepts a mask. This is commonly used for background
blurring. We should allow the mask to be smaller than the original, since neural networks models
that do segmentation are downscaled.

* Allow yuvToRgb to have a Restriction.

* Add support for YUV_420_888, the YUV format favored by Camera2. Allow various strides to be specified.

* When passing a Restriction, it would be nice to say "Create a smaller output".
The original RenderScript does not allow that. It's not that useful when outputing new buffers as
our Java library does.

* For Resize, Restriction working on input buffer would be more useful but that's not RenderScript.

* Integrate and test with imageprocessing_jb. Do the same for [github/renderscript-samples/](https://github.com/android/renderscript-samples/tree/main/RenderScriptIntrinsic)

* Allow Bitmaps with rowSize != width * vectorSize. We could do this also for ByteArray.

- In TaskProcessor.cpp, the code below is fine and clean, but probably a bit inefficient. 
When this wakes up another thread, it may have to immediately go back to sleep, since we still hold the lock.
It could instead set a need_to_notify flag and test that after releasing the lock (both places).
That might avoid some context switches.
```cpp
if (mTilesInProcess == 0 && mTilesNotYetStarted == 0) {
    mWorkIsFinished.notify_one();
```

* When compiled as part of Android, librenderscript_toolkit.so is 101,456 bytes. When compiled by Android Studio as part of an .aar, it's 387K. Figure out why and slim it down.
