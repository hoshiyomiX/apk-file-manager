package com.hoshiyomi.filemanager.ui.apkviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.FragmentPermissionsBinding
import com.hoshiyomi.filemanager.databinding.ItemPermissionBinding

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PermissionAdapter
    private var pendingPermissions: List<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PermissionAdapter()
        binding.rvPermissions.layoutManager = LinearLayoutManager(context)
        binding.rvPermissions.adapter = adapter
        pendingPermissions?.let { updatePermissions(it) }
    }

    fun updatePermissions(permissions: List<String>) {
        if (_binding != null) {
            displayPermissions(permissions)
        } else {
            pendingPermissions = permissions
        }
    }

    private fun displayPermissions(permissions: List<String>) {
        binding.progressBar.visibility = View.GONE
        if (permissions.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvPermissions.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvPermissions.visibility = View.VISIBLE
            adapter.submitList(permissions)
        }
    }

    inner class PermissionAdapter : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

        private val items = mutableListOf<String>()

        inner class ViewHolder(val binding: ItemPermissionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPermissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val permission = items[position]
            holder.binding.tvPermissionName.text = permission
            holder.binding.tvPermissionDescription.text = getPermissionDescription(permission)
        }

        override fun getItemCount(): Int = items.size

        fun submitList(permissions: List<String>) {
            items.clear()
            items.addAll(permissions)
            notifyDataSetChanged()
        }
    }

    private fun getPermissionDescription(permission: String): String {
        return when {
            permission.contains("INTERNET") -> "Full network access"
            permission.contains("CAMERA") -> "Take photos and videos"
            permission.contains("READ_CONTACTS") -> "Read contacts"
            permission.contains("WRITE_CONTACTS") -> "Modify contacts"
            permission.contains("READ_EXTERNAL_STORAGE") || permission.contains("READ_MEDIA") -> "Read storage/media"
            permission.contains("WRITE_EXTERNAL_STORAGE") || permission.contains("MANAGE_EXTERNAL_STORAGE") -> "Write/modify storage"
            permission.contains("ACCESS_FINE_LOCATION") || permission.contains("ACCESS_COARSE_LOCATION") -> "Access location"
            permission.contains("RECORD_AUDIO") -> "Record audio/microphone"
            permission.contains("READ_PHONE_STATE") -> "Read phone state"
            permission.contains("CALL_PHONE") -> "Make phone calls"
            permission.contains("SEND_SMS") || permission.contains("RECEIVE_SMS") -> "Send/receive SMS"
            permission.contains("READ_CALENDAR") || permission.contains("WRITE_CALENDAR") -> "Read/write calendar"
            permission.contains("GET_ACCOUNTS") -> "Find accounts on device"
            permission.contains("WAKE_LOCK") -> "Prevent device from sleeping"
            permission.contains("VIBRATE") -> "Control vibration"
            permission.contains("BLUETOOTH") -> "Access Bluetooth"
            permission.contains("WIFI") -> "Access Wi-Fi state"
            permission.contains("RECEIVE_BOOT_COMPLETED") -> "Run at startup"
            permission.contains("FOREGROUND_SERVICE") -> "Run foreground service"
            permission.contains("POST_NOTIFICATIONS") -> "Post notifications"
            permission.contains("REQUEST_INSTALL_PACKAGES") -> "Install other apps"
            permission.contains("SYSTEM_ALERT_WINDOW") -> "Draw over other apps"
            permission.contains("WRITE_SETTINGS") -> "Modify system settings"
            permission.contains("ACCESS_NETWORK_STATE") -> "View network connections"
            permission.contains("ACCESS_WIFI_STATE") -> "View Wi-Fi connections"
            permission.contains("CHANGE_NETWORK_STATE") -> "Change network connectivity"
            permission.contains("BILLING") -> "Google Play billing"
            else -> "Other permission"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
