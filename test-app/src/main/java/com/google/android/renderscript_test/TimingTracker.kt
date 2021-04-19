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

class TimingTracker(
    private val numberOfIterations: Int = 1,
    private var numberOfIterationsToIgnore: Int = 0
) {
    init {
        require(numberOfIterations > numberOfIterationsToIgnore)
    }
    private val timings = mutableMapOf<String, IntArray>()
    private var currentIteration: Int = 0
    fun nextIteration() {
        currentIteration++
    }
    fun <T> measure(name: String, workToTime: () -> T): T {
        val start = System.nanoTime()
        val t = workToTime()
        if (currentIteration >= numberOfIterationsToIgnore) {
            val end = System.nanoTime()
            val deltaInMicroseconds: Int = ((end - start) / 1000).toInt()
            val timing = timings.getOrPut(name) {
                IntArray(numberOfIterations - numberOfIterationsToIgnore)
            }
            timing[currentIteration - numberOfIterationsToIgnore] += deltaInMicroseconds
        }
        return t
    }
    fun report(): String {
        var minimum: Int = Int.MAX_VALUE
        for (timing in timings.values) {
            val m = timing.minOrNull()
            if (m != null && m < minimum) minimum = m
        }

        println(timings.map { (name, timing) -> name + ": " + timing.minOrNull() }.joinToString(separator = "\n"))

        var minimums =
            timings.map { (name, timing) -> name + ": " + timing.minOrNull() }.joinToString()
        var all =
            timings.map { (name, timing) -> name + ": " + timing.joinToString() }.joinToString()
        var normalized =
            timings.map { (name, timing) -> name + ": " + timing.joinToString { "%.2f".format(it.toFloat() / minimum) } }
                .joinToString()

        return "Minimums: $minimums\n\nAll: $all\n\nNormalized: $normalized\n"
    }
}

