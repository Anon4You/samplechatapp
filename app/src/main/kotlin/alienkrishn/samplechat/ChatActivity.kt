package alienkrishn.samplechat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnVoice: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var toolbar: Toolbar

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private var modelId: String = ""
    private val TAG = "ChatActivity"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startVoiceInput()
        else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) sendUserMessage(results[0])
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        modelId = intent.getStringExtra("MODEL_ID") ?: run {
            Toast.makeText(this, "No model selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        toolbar = findViewById(R.id.toolbar)
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnVoice = findViewById(R.id.btnVoice)
        progressBar = findViewById(R.id.progressBar)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = MessageAdapter(
            messages,
            onBotMessageClick = { message -> copyToClipboard(message) },
            onRetryClick = { prompt -> retryFailedMessage(prompt) }
        )
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = adapter

        btnSend.setOnClickListener {
            val userMessage = etMessage.text.toString().trim()
            if (userMessage.isNotEmpty()) sendUserMessage(userMessage)
        }

        btnVoice.setOnClickListener { checkAndRequestMicrophonePermission() }

        // Add clear conversation button
        val btnClear = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            contentDescription = "Clear conversation"
            background = null
            setColorFilter(ContextCompat.getColor(this@ChatActivity, android.R.color.white))
            setOnClickListener { clearConversation() }
            layoutParams = Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END
                rightMargin = 16
            }
        }
        toolbar.addView(btnClear)

        // Load the model
        loadModel()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Message", text))
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }

    private fun clearConversation() {
        if (messages.isNotEmpty()) {
            messages.clear()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryFailedMessage(prompt: String) {
        // Remove the error message
        val lastMessage = messages.lastOrNull()
        if (lastMessage != null && lastMessage.isError) {
            messages.removeAt(messages.size - 1)
            adapter.notifyItemRemoved(messages.size)
        }
        // Resend the prompt
        sendUserMessage(prompt)
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
        voiceInputLauncher.launch(intent)
    }

    private fun loadModel() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                tvProgressPercent.visibility = View.VISIBLE
                tvProgressPercent.text = "Loading model..."

                RunAnywhere.loadLLMModel(modelId)
                progressBar.visibility = View.GONE
                tvProgressPercent.visibility = View.GONE
                Toast.makeText(this@ChatActivity, "Model ready!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                Toast.makeText(this@ChatActivity, "Failed to load model", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun sendUserMessage(userText: String) {
        messages.add(Message(userText, true))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
        etMessage.text.clear()

        lifecycleScope.launch {
            val thinkingIndex = messages.size
            messages.add(Message("Thinking...", false))
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
                messages[thinkingIndex] = Message(
                    "Failed to get response. Tap to retry.",
                    false,
                    isError = true,
                    originalPrompt = userText
                )
                adapter.notifyItemChanged(thinkingIndex)
            }
            rvMessages.scrollToPosition(messages.size - 1)
        }
    }
}
