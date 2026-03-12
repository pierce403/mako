package ninja.mako.ui

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ninja.mako.databinding.ItemDeviceBinding

class DeviceAdapter(
  private val onDeviceClick: (DiscoveredDeviceListItem) -> Unit = {}
) : ListAdapter<DiscoveredDeviceListItem, DeviceAdapter.DeviceViewHolder>(DiffCallback) {
  init {
    setHasStableIds(true)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
    val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return DeviceViewHolder(binding, onDeviceClick)
  }

  override fun getItemId(position: Int): Long {
    return getItem(position).deviceKey.hashCode().toLong()
  }

  override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class DeviceViewHolder(
    private val binding: ItemDeviceBinding,
    private val onDeviceClick: (DiscoveredDeviceListItem) -> Unit
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: DiscoveredDeviceListItem) {
      binding.deviceName.text = item.displayTitle
      binding.deviceName.maxLines = 2
      binding.deviceName.ellipsize = TextUtils.TruncateAt.END
      binding.deviceBadge.text = item.badgeLabel
      binding.deviceMeta.text = item.metaLine
      binding.deviceMeta.maxLines = 3
      binding.deviceMeta.ellipsize = TextUtils.TruncateAt.END
      binding.deviceHost.text = item.hostAddress
      binding.deviceStatus.text = item.statusLine
      binding.root.setOnClickListener {
        onDeviceClick(item)
      }
    }
  }

  companion object {
    private val DiffCallback = object : DiffUtil.ItemCallback<DiscoveredDeviceListItem>() {
      override fun areItemsTheSame(oldItem: DiscoveredDeviceListItem, newItem: DiscoveredDeviceListItem): Boolean {
        return oldItem.deviceKey == newItem.deviceKey
      }

      override fun areContentsTheSame(oldItem: DiscoveredDeviceListItem, newItem: DiscoveredDeviceListItem): Boolean {
        return oldItem == newItem
      }
    }
  }
}
