package alienkrishn.samplechat

data class Message(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val originalPrompt: String? = null
)