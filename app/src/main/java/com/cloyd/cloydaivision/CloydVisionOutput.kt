package com.cloyd.cloydaivision

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerOutputObject
class CloydVisionOutput @JvmOverloads constructor(
    @get:TaskerOutputVariable("response")
    val response: String = ""
)