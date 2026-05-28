package com.hoshiyomi.filemanager.ui.apkviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.databinding.FragmentCertificatesBinding
import com.hoshiyomi.filemanager.model.CertificateInfo

class CertificatesFragment : Fragment() {

    private var _binding: FragmentCertificatesBinding? = null
    private val binding get() = _binding!!

    private var pendingCertInfo: CertificateInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCertificatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingCertInfo?.let { updateCertificate(it) }
    }

    fun updateCertificate(certInfo: CertificateInfo?) {
        if (_binding != null) {
            displayCertificate(certInfo)
        } else {
            pendingCertInfo = certInfo
        }
    }

    private fun displayCertificate(certInfo: CertificateInfo?) {
        binding.progressBar.visibility = View.GONE
        if (certInfo == null) {
            binding.cardCertificate.visibility = View.GONE
            return
        }
        binding.cardCertificate.visibility = View.VISIBLE
        binding.tvIssuer.text = certInfo.issuer.ifEmpty { "N/A" }
        binding.tvSubject.text = certInfo.subject.ifEmpty { "N/A" }
        binding.tvValidFrom.text = certInfo.validFrom.ifEmpty { "N/A" }
        binding.tvValidTo.text = certInfo.validTo.ifEmpty { "N/A" }
        binding.tvSerialNumber.text = certInfo.serialNumber.ifEmpty { "N/A" }
        binding.tvAlgorithm.text = certInfo.algorithm.ifEmpty { "N/A" }
        binding.tvSha256.text = certInfo.sha256Fingerprint.ifEmpty { "N/A" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
