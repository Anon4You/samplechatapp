package alienkrishn.samplechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnVoice: ImageButton
    private lateinit var progressContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    // Model Constants – Stable ID is key!
    private companion object {
        const val FIXED_MODEL_ID = "smollm2-360m-instruct-q8_0"  // Yeh ID har baar same rahega
        const val MODEL_NAME = "SmolLM2 360M Instruct Q8_0"
        const val MODEL_URL = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q8_0.gguf"
        const val MODEL_SIZE_MB = 400
        const val TAG = "RunAnywhere"
        const val PREFS_NAME = "RunAnywherePrefs"
        const val KEY_MODEL_READY = "model_ready"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startVoiceInput()
        else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnVoice = findViewById(R.id.btnVoice)
        progressContainer = findViewById(R.id.progressContainer)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)

        adapter = MessageAdapter(messages)
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = adapter

        btnSend.setOnClickListener {
            val userMessage = etMessage.text.toString().trim()
            if (userMessage.isNotEmpty()) sendUserMessage(userMessage)
        }

        btnVoice.setOnClickListener { checkAndRequestMicrophonePermission() }

        initializeSDK()
    }

    private fun checkAndRequestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                startVoiceInput()
            else -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) sendUserMessage(results[0])
        }
    }

    private fun initializeSDK() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Initializing SDK...")

                AndroidPlatformContext.initialize(this@MainActivity)
                RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)

                try { LlamaCPP.register(priority = 100) } catch (e: Exception) { Log.w(TAG, "LlamaCPP reg: ${e.message}") }
                ONNX.register(priority = 100)

                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val modelId = FIXED_MODEL_ID

                // Check if this exact ID is already registered
                val models = RunAnywhere.availableModels()
                val isRegistered = models.any { it.id == modelId }

                if (!isRegistered) {
                    Log.d(TAG, "First time: Registering model with stable ID: $modelId")
                    RunAnywhere.registerModel(
                        id = modelId,  // Yeh pass karne se ID fixed rahega!
                        name = MODEL_NAME,
                        url = MODEL_URL,
                        framework = InferenceFramework.LLAMA_CPP,
                        modality = ModelCategory.LANGUAGE,
                        memoryRequirement = MODEL_SIZE_MB * 1024L * 1024L
                    )
                } else {
                    Log.d(TAG, "Model already registered (ID: $modelId)")
                }

                // Ab check karo downloaded hai ya nahi
                if (RunAnywhere.isModelDownloaded(modelId)) {
                    val isReady = prefs.getBoolean(KEY_MODEL_READY, false)
                    Log.d(TAG, "Model downloaded. Ready flag: $isReady")
                    try {
                        RunAnywhere.loadLLMModel(modelId)
                        prefs.edit().putBoolean(KEY_MODEL_READY, true).apply()
                        Toast.makeText(this@MainActivity, "Model ready! Start chatting.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Load failed even though downloaded", e)
                        showDownloadConfirmationDialog(modelId)  // Rare case: corrupted?
                    }
                } else {
                    Log.d(TAG, "Model not downloaded yet → show dialog")
                    showDownloadConfirmationDialog(modelId)
                }

            } catch (e: Exception) {
                Log.e(TAG, "SDK init failed", e)
                Toast.makeText(this@MainActivity, "Init failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDownloadConfirmationDialog(modelId: String) {
        AlertDialog.Builder(this)
            .setTitle("Download Model")
            .setMessage("The AI model ($MODEL_SIZE_MB MB) needs to be downloaded once.\n\nProceed?")
            .setPositiveButton("Download") { _, _ -> startModelDownload(modelId) }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun startModelDownload(modelId: String) {
        lifecycleScope.launch {
            try {
                progressContainer.visibility = View.VISIBLE

                RunAnywhere.downloadModel(modelId).collectLatest { status ->
                    val percent = (status.progress * 100).toInt()
                    progressBar.progress = percent
                    tvProgressPercent.text = "$percent%"

                    if (status.progress >= 1.0f) {
                        progressContainer.visibility = View.GONE
                        loadModelAfterDownload(modelId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                progressContainer.visibility = View.GONE
                showDownloadRetryDialog(modelId)
            }
        }
    }

    private suspend fun loadModelAfterDownload(modelId: String) {
        try {
            RunAnywhere.loadLLMModel(modelId)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_MODEL_READY, true)
                .apply()
            Log.d(TAG, "Model loaded after download.")
            Toast.makeText(this@MainActivity, "Model ready! Start chatting.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Load failed after download", e)
            Toast.makeText(this@MainActivity, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
            showDownloadRetryDialog(modelId)
        }
    }

    private fun showDownloadRetryDialog(modelId: String) {
        AlertDialog.Builder(this)
            .setTitle("Download Failed")
            .setMessage("Download failed. Retry?")
            .setPositiveButton("Retry") { _, _ -> startModelDownload(modelId) }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun sendUserMessage(userText: String) {
        messages.add(Message(userText, true))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
        etMessage.text.clear()

        lifecycleScope.launch {
            val thinkingIndex = messages.size
            messages.add(Message("...", false))
            adapter.notifyItemInserted(thinkingIndex)
            rvMessages.scrollToPosition(thinkingIndex)

            try {
                val response = withContext(Dispatchers.IO) {
                    RunAnywhere.chat(userText)
                }
                messages[thinkingIndex] = Message(response, false)
                adapter.notifyItemChanged(thinkingIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
                messages[thinkingIndex] = Message("Error: ${e.message}", false)
                adapter.notifyItemChanged(thinkingIndex)
            }
            rvMessages.scrollToPosition(messages.size - 1)
        }
    }
}
