package com.cloyd.cloydaivision

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.NestedScrollView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.SimpleResult
import com.joaomgcd.taskerpluginlibrary.SimpleResultSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.math.abs

class ConfigActivity : AppCompatActivity(), TaskerPluginConfig<CloydVisionInput> {

    private var modelSuggestions = mutableListOf(
        "google/gemini-3-flash-preview",
        "google/gemini-3.1-flash-lite",
        "google/gemini-3.1-pro-preview",
        "anthropic/claude-3.5-sonnet",
        "openai/gpt-4o-mini"
    )

    override val context get() = this
    private lateinit var adapter: ArrayAdapter<String>

    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}
            editImagePath.setText(uri.toString())
        }
    }

    override val inputForTasker: TaskerInput<CloydVisionInput>
        get() = TaskerInput(CloydVisionInput(
            apiKey = editApiKey.text.toString(),
            model = editModel.text.toString(),
            imagePath = editImagePath.text.toString(),
            systemPrompt = editSystemPrompt.text.toString(),
            userPrompt = editUserPrompt.text.toString()
        ))

    private val taskerHelper by lazy {
        object : TaskerPluginConfigHelper<CloydVisionInput, CloydVisionOutput, VisionActionRunner>(this) {
            override val inputClass = CloydVisionInput::class.java
            override val runnerClass = VisionActionRunner::class.java
            override val outputClass = CloydVisionOutput::class.java
            override fun isInputValid(input: TaskerInput<CloydVisionInput>): SimpleResult = SimpleResultSuccess()
        }
    }

    private lateinit var editApiKey: TextInputEditText
    private lateinit var editModel: MaterialAutoCompleteTextView
    private lateinit var editImagePath: TextInputEditText
    private lateinit var editSystemPrompt: TextInputEditText
    private lateinit var editUserPrompt: TextInputEditText
    private lateinit var mainRoot: NestedScrollView

    private val scrollToCursor = Runnable {
        editUserPrompt.post {
            val layout = editUserPrompt.layout ?: return@post
            if (editUserPrompt.selectionStart != editUserPrompt.selectionEnd) return@post

            val offset = editUserPrompt.selectionStart
            val line = layout.getLineForOffset(offset)
            val lineTop = layout.getLineTop(line)

            val rect = Rect()
            editUserPrompt.getDrawingRect(rect)
            mainRoot.offsetDescendantRectToMyCoords(editUserPrompt, rect)

            val targetScrollY = rect.top + lineTop - 100
            val currentScrollY = mainRoot.scrollY
            if (abs(currentScrollY - targetScrollY) > 100) {
                mainRoot.smoothScrollTo(0, targetScrollY.coerceAtLeast(0))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        mainRoot = findViewById(R.id.main_root)
        val contentContainer = mainRoot.getChildAt(0)

        mainRoot.isFocusableInTouchMode = true
        mainRoot.descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
        mainRoot.requestFocus()

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            if (insets.isVisible(WindowInsetsCompat.Type.ime()) && editUserPrompt.hasFocus()) {
                mainRoot.postDelayed({ scrollToCursor.run() }, 300)
            }
            insets
        }

        editApiKey = findViewById(R.id.edit_api_key)
        editModel = findViewById(R.id.edit_model)
        editImagePath = findViewById(R.id.edit_image_path)
        editSystemPrompt = findViewById(R.id.edit_system_prompt)
        editUserPrompt = findViewById(R.id.edit_user_prompt)

        findViewById<TextInputLayout>(R.id.layout_image_path).setEndIconOnClickListener {
            pickMedia.launch("image/*")
        }

        editUserPrompt.setOnClickListener { scrollToCursor.run() }
        editUserPrompt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                mainRoot.postDelayed({ scrollToCursor.run() }, 400)
            }
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modelSuggestions)
        editModel.setAdapter(adapter)

        taskerHelper.onCreate()

        if (editApiKey.text.toString().isEmpty()) loadGlobalDefaults()
        if (editModel.text.toString().isEmpty()) editModel.setText(getString(R.string.default_model), false)

        fetchModelsFromOpenRouter()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intent?.action == Intent.ACTION_MAIN) saveGlobalDefaults()
                taskerHelper.finishForTasker()
            }
        })
    }

    private fun fetchModelsFromOpenRouter() {
        lifecycleScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) { URL("https://openrouter.ai/api/v1/models").readText() }
                val data = JSONObject(jsonString).getJSONArray("data")
                val newList = mutableListOf<String>()
                newList.addAll(modelSuggestions)
                for (i in 0 until data.length()) {
                    val id = data.getJSONObject(i).getString("id")
                    if (!newList.contains(id)) newList.add(id)
                }
                if (newList.isNotEmpty()) {
                    modelSuggestions.clear()
                    modelSuggestions.addAll(newList)
                    modelSuggestions.sort()
                    adapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveGlobalDefaults() {
        val prefs = getSharedPreferences("CloydAIVisionPrefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("api_key", editApiKey.text.toString())
            putString("model", editModel.text.toString())
            putString("image_path", editImagePath.text.toString())
            putString("system_prompt", editSystemPrompt.text.toString())
            putString("user_prompt", editUserPrompt.text.toString())
            apply()
        }
    }

    private fun loadGlobalDefaults() {
        val prefs = getSharedPreferences("CloydAIVisionPrefs", MODE_PRIVATE)
        editApiKey.setText(prefs.getString("api_key", ""))
        val savedModel = prefs.getString("model", "")
        if (!savedModel.isNullOrEmpty()) editModel.setText(savedModel, false)
        editImagePath.setText(prefs.getString("image_path", ""))
        editSystemPrompt.setText(prefs.getString("system_prompt", ""))
        editUserPrompt.setText(prefs.getString("user_prompt", ""))
    }

    override fun assignFromInput(input: TaskerInput<CloydVisionInput>) {
        val data = input.regular
        if (data.apiKey.isNotEmpty()) editApiKey.setText(data.apiKey)
        if (data.model.isNotEmpty()) editModel.setText(data.model, false)
        if (data.imagePath.isNotEmpty()) editImagePath.setText(data.imagePath)
        if (data.systemPrompt.isNotEmpty()) editSystemPrompt.setText(data.systemPrompt)

        if (data.userPrompt.isNotEmpty()) {
            editUserPrompt.setText(data.userPrompt)
            editUserPrompt.setSelection(0)
        }
    }
}