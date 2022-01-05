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
import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@LargeTest
class RenderScriptToolkitTest {
    @ExperimentalUnsignedTypes
    @get:Rule
    var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Parameterized.Parameter(value = 0)
    lateinit var intrinsic: Intrinsic

    @ExperimentalUnsignedTypes
    @Test
    fun test() {
        lateinit var thread: Thread
        var success = false
        activityScenarioRule.scenario.onActivity { activity ->
            // Run the test in a new thread to avoid blocking the main thread.
            thread = Thread{
                success = testIntrinsic(activity, intrinsic, validate = true)
            }.apply { start() }
        }
        thread.join()
        assertTrue(success)
    }

    @ExperimentalUnsignedTypes
    private fun testIntrinsic(context: Context, intrinsic: Intrinsic, validate: Boolean): Boolean {
        val tester = Tester(context, validate)
        val numberOfIterations = if (validate) 1 else 12
        val timer = TimingTracker(numberOfIterations, numberOfIterationsToIgnore = 0)
        val results = Array(numberOfIterations) { i ->
            Log.i(TAG, "*** Starting iteration ${i + 1} of $numberOfIterations ****")
            val success = tester.testOne(intrinsic, timer)
            Log.i(TAG, if (success) timer.report() else "FAILED! FAILED! FAILED! FAILED!")
            timer.nextIteration()
            success
        }
        tester.destroy()
        return results.all { it }
    }

    companion object {
        private val TAG = "RenderScriptToolkitTest"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> = Intrinsic.values().map { arrayOf(it) }
    }
}
