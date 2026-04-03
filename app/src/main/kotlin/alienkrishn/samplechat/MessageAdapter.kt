package alienkrishn.samplechat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class MessageAdapter(
    private val messages: List<Message>,
    private val onBotMessageClick: (String) -> Unit,
    private val onRetryClick: (String) -> Unit = {}
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private lateinit var markwon: io.noties.markwon.Markwon

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_BOT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_USER) R.layout.item_user_message else R.layout.item_bot_message
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false) as CardView
        markwon = Markwon.builder(parent.context).build()
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // Render markdown for bot messages
        if (!message.isUser) {
            markwon.setMarkdown(holder.messageText, message.text)
        } else {
            holder.messageText.text = message.text
        }

        // Handle error state - tap to retry
        if (message.isError) {
            holder.itemView.setOnClickListener {
                message.originalPrompt?.let { prompt -> onRetryClick(prompt) }
            }
            holder.itemView.setOnLongClickListener {
                onBotMessageClick(message.text)
                true
            }
        } else if (!message.isUser) {
            // Long click to copy (only for bot messages)
            holder.itemView.setOnLongClickListener {
                onBotMessageClick(message.text)
                true
            }
        }
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: CardView) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
    }
}