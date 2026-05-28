package com.hoshiyomi.filemanager.ui.apkviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.FragmentApkInfoBinding
import com.hoshiyomi.filemanager.model.ApkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApkInfoFragment : Fragment() {

    private var _binding: FragmentApkInfoBinding? = null
    private val binding get() = _binding!!

    private var currentApkInfo: ApkInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApkInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentApkInfo?.let { displayInfo(it) }
    }

    fun updateApkInfo(info: ApkInfo) {
        currentApkInfo = info
        if (_binding != null) {
            displayInfo(info)
        }
    }

    private fun displayInfo(info: ApkInfo) {
        binding.tvAppName.text = info.appName.ifEmpty { info.packageName }
        binding.tvPackageName.text = info.packageName
        binding.tvVersionName.text = info.versionName
        binding.tvVersionCode.text = info.versionCode.toString()
        binding.tvMinSdk.text = "API ${info.minSdkVersion} (${getAndroidVersion(info.minSdkVersion)})"
        binding.tvTargetSdk.text = "API ${info.targetSdkVersion} (${getAndroidVersion(info.targetSdkVersion)})"
        binding.tvFileSize.text = com.hoshiyomi.filemanager.model.FileItem.formatFileSize(info.fileSize)
        binding.tvFilePath.text = info.file?.absolutePath ?: ""
        binding.tvDebuggable.text = "Debuggable: ${info.isDebuggable}"

        if (info.sharedUserId != null) {
            binding.tvSharedUserId.visibility = View.VISIBLE
            binding.tvSharedUserId.text = "Shared User ID: ${info.sharedUserId}"
        } else {
            binding.tvSharedUserId.visibility = View.GONE
        }

        lifecycleScope.launch {
            val file = info.file
            if (file != null && file.exists()) {
                val icon = com.hoshiyomi.filemanager.core.apk.ApkAnalyzer.getApkIcon(requireContext(), file)
                icon?.let { binding.ivAppIcon.setImageDrawable(it) }
            }
        }
    }

    private fun getAndroidVersion(sdk: Int): String {
        return when (sdk) {
            14 -> "4.0 Ice Cream Sandwich"
            15 -> "4.0.3 ICS"
            16 -> "4.1 Jelly Bean"
            17 -> "4.2 Jelly Bean"
            18 -> "4.3 Jelly Bean"
            19 -> "4.4 KitKat"
            20 -> "4.4W KitKat Wear"
            21 -> "5.0 Lollipop"
            22 -> "5.1 Lollipop"
            23 -> "6.0 Marshmallow"
            24 -> "7.0 Nougat"
            25 -> "7.1 Nougat"
            26 -> "8.0 Oreo"
            27 -> "8.1 Oreo"
            28 -> "9 Pie"
            29 -> "10"
            30 -> "11"
            31 -> "12"
            32 -> "12L"
            33 -> "13"
            34 -> "14"
            35 -> "15"
            else -> "Unknown"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
