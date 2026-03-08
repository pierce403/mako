package ninja.mako.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import ninja.mako.R
import ninja.mako.databinding.ItemKnownNetworkBinding

class KnownNetworkAdapter : ListAdapter<KnownNetworkListItem, KnownNetworkAdapter.KnownNetworkViewHolder>(DiffCallback) {
  init {
    setHasStableIds(true)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KnownNetworkViewHolder {
    val binding = ItemKnownNetworkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return KnownNetworkViewHolder(binding)
  }

  override fun getItemId(position: Int): Long {
    return getItem(position).networkKey.hashCode().toLong()
  }

  override fun onBindViewHolder(holder: KnownNetworkViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class KnownNetworkViewHolder(
    private val binding: ItemKnownNetworkBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: KnownNetworkListItem) {
      binding.networkTitle.text = item.title
      binding.networkSubtitle.text = item.subtitle
      binding.networkBadge.text = item.badgeLabel
      applySelection(binding.root, item)
    }

    private fun applySelection(card: MaterialCardView, item: KnownNetworkListItem) {
      val context = card.context
      val selectedStroke = ContextCompat.getColor(context, R.color.mako_secondary)
      val defaultStroke = ContextCompat.getColor(context, R.color.mako_stroke_soft)
      val selectedBackground = ContextCompat.getColor(context, R.color.mako_surface)
      val defaultBackground = ContextCompat.getColor(context, R.color.mako_surface_variant)

      card.strokeColor = if (item.isSelected) selectedStroke else defaultStroke
      card.strokeWidth = if (item.isSelected) dp(card, 2) else dp(card, 1)
      card.setCardBackgroundColor(if (item.isSelected) selectedBackground else defaultBackground)
      card.alpha = if (item.isLive || item.isSelected) 1f else 0.92f
    }

    private fun dp(card: MaterialCardView, value: Int): Int {
      return (value * card.resources.displayMetrics.density).toInt()
    }
  }

  companion object {
    private val DiffCallback = object : DiffUtil.ItemCallback<KnownNetworkListItem>() {
      override fun areItemsTheSame(oldItem: KnownNetworkListItem, newItem: KnownNetworkListItem): Boolean {
        return oldItem.networkKey == newItem.networkKey
      }

      override fun areContentsTheSame(oldItem: KnownNetworkListItem, newItem: KnownNetworkListItem): Boolean {
        return oldItem == newItem
      }
    }
  }
}
