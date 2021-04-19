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

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // To debug resources not destroyed
        // "A resource failed to call destroy."
        try {
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }

        startTests(this)
    }

    private fun startTests(activity: MainActivity) {
        object : Thread() {
            override fun run() {
                val view = findViewById<TextView>(R.id.sample_text)
                view.movementMethod = ScrollingMovementMethod()
                val validate = true
                val tester = Tester(activity, validate)
                val numberOfIterations = if (validate) 1 else 12
                val t = TimingTracker(numberOfIterations, 0)
                for (i in 1..numberOfIterations) {
                    val tag = "*** Starting iteration $i of $numberOfIterations ****\n"
                    print(tag)
                    runOnUiThread(Runnable { view.append(tag) })
                    //startMethodTracing("myTracing")
                    //startMethodTracingSampling("myTracing_sample", 8000000, 10)
                    val result = tester.testAll(t)
                    //stopMethodTracing()
                    runOnUiThread(Runnable { view.append("$result\n\n${t.report()}\n") })
                    t.nextIteration()
                }
                tester.destroy()
                /*
                while (i++ < 1000) {
                    try {
                        runOnUiThread(Runnable { btn.setText("#$i") })
                        sleep(300)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                 */
            }
        }.start()
    }
}
