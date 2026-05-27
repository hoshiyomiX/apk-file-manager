package com.hoshiyomi.filemanager.ui.apkviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.FragmentManifestBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManifestFragment : Fragment() {

    private var _binding: FragmentManifestBinding? = null
    private val binding get() = _binding!!

    private var pendingManifestXml: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManifestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingManifestXml?.let { displayManifest(it) }
    }

    fun updateManifest(manifestXml: String) {
        if (_binding != null) {
            displayManifest(manifestXml)
        } else {
            pendingManifestXml = manifestXml
        }
    }

    private fun displayManifest(xml: String) {
        binding.progressBar.visibility = View.GONE
        if (xml.isBlank()) {
            binding.tvManifest.visibility = View.GONE
            return
        }
        binding.scrollView.visibility = View.VISIBLE
        binding.tvManifest.visibility = View.VISIBLE
        binding.tvManifest.text = xml
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
