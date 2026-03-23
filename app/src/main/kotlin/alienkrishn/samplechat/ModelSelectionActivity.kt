package alienkrishn.samplechat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import com.runanywhere.sdk.public.extensions.*
import com.runanywhere.sdk.public.extensions.Models.ModelCategory
import com.runanywhere.sdk.storage.AndroidPlatformContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ModelSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView

    private val models = listOf(
        Model(
            id = "SmolLM2-360M-Instruct-Q8_0",
            name = "SmolLM2 360M (Fast, ~400MB)",
            url = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q8_0.gguf",
            sizeMB = 400
        ),
        Model(
            id = "Qwen2.5-0.5B-Instruct-Q4_0",
            name = "Qwen2.5 0.5B (Balanced, ~350MB)",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf",
            sizeMB = 350
        ),
        Model(
            id = "Llama-3.2-1B-Instruct-Q4_0",
            name = "Llama 3.2 1B (Larger, ~700MB)",
            url = "https://huggingface.co/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeMB = 700
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_selection)

        recyclerView = findViewById(R.id.recyclerView)
        progressContainer = findViewById(R.id.progressContainer)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)

        // Initialize SDK
        initializeSDK()
    }

    private fun initializeSDK() {
        lifecycleScope.launch {
            try {
                AndroidPlatformContext.initialize(this@ModelSelectionActivity)
                RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)

                try { LlamaCPP.register(priority = 100) } catch (e: Exception) { Log.w("SDK", "LlamaCPP reg failed") }
                ONNX.register(priority = 100)

                // Register all models so we can check download status
                models.forEach { model ->
                    RunAnywhere.registerModel(
                        id = model.id,
                        name = model.name,
                        url = model.url,
                        framework = InferenceFramework.LLAMA_CPP,
                        modality = ModelCategory.LANGUAGE,
                        memoryRequirement = model.sizeMB * 1024L * 1024L
                    )
                }

                // Show model list
                setupRecyclerView()
            } catch (e: Exception) {
                Log.e("SDK", "Init failed", e)
                Toast.makeText(this@ModelSelectionActivity, "Initialization failed", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupRecyclerView() {
        val adapter = ModelAdapter(models) { selectedModel ->
            lifecycleScope.launch {
                // Check if already downloaded
                if (RunAnywhere.isModelDownloaded(selectedModel.id)) {
                    // Directly open chat
                    openChat(selectedModel.id)
                } else {
                    // Download then open chat
                    downloadAndOpenChat(selectedModel)
                }
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun downloadAndOpenChat(model: Model) {
        lifecycleScope.launch {
            try {
                progressContainer.visibility = View.VISIBLE
                progressBar.progress = 0
                tvProgressPercent.text = "0%"

                RunAnywhere.downloadModel(model.id).collectLatest { status ->
                    val percent = (status.progress * 100).toInt()
                    progressBar.progress = percent
                    tvProgressPercent.text = "$percent%"

                    if (status.progress >= 1.0f) {
                        progressContainer.visibility = View.GONE
                        openChat(model.id)
                    }
                }
            } catch (e: Exception) {
                Log.e("Download", "Failed", e)
                Toast.makeText(this@ModelSelectionActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                progressContainer.visibility = View.GONE
            }
        }
    }

    private fun openChat(modelId: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("MODEL_ID", modelId)
        }
        startActivity(intent)
        finish() // optional: close selection screen
    }

    data class Model(val id: String, val name: String, val url: String, val sizeMB: Int)
}
