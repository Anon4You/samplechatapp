package alienkrishn.samplechat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class ModelAdapter(
    private val models: List<ModelSelectionActivity.Model>,
    private val onItemClick: (ModelSelectionActivity.Model) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false) as CardView
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.nameText.text = model.name
        holder.itemView.setOnClickListener { onItemClick(model) }
    }

    override fun getItemCount() = models.size

    class ModelViewHolder(itemView: CardView) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.modelName)
    }
}
