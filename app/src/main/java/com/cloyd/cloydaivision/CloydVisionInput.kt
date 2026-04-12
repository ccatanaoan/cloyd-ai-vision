package com.cloyd.cloydaivision

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField

@TaskerInputRoot
class CloydVisionInput @JvmOverloads constructor(
    @field:TaskerInputField("key_api_key") var apiKey: String = "",
    @field:TaskerInputField("key_model") var model: String = "",
    @field:TaskerInputField("key_image_path") var imagePath: String = "",
    @field:TaskerInputField("key_system_prompt") var systemPrompt: String = "",
    @field:TaskerInputField("key_user_prompt") var userPrompt: String = ""
)