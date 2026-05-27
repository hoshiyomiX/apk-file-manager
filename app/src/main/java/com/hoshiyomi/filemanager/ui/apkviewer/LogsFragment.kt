package com.hoshiyomi.filemanager.ui.apkviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.ui.logs.LogViewerActivity
import com.hoshiyomi.filemanager.util.DiagnosticLogger

/**
 * Logs tab in APK viewer — shows diagnostic summary with a Copy button.
 */
class LogsFragment : Fragment() {

    private var _binding: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_logs_tab, container, false)
        _binding = root

        val scrollView = root.findViewById<ScrollView>(R.id.scrollView)
        val tvLogContent = root.findViewById<TextView>(R.id.tvLogContent)
        val tvEmpty = root.findViewById<TextView>(R.id.tvEmpty)
        val btnCopy = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyLogs)
        val btnFullLog = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFullLog)

        val summary = DiagnosticLogger.buildApkDiagnosticSummary()

        if (summary.isBlank()) {
            scrollView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            scrollView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            tvLogContent.text = summary
        }

        btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("APK Analysis", summary)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), getString(R.string.log_copied, "Summary"), Toast.LENGTH_SHORT).show()
        }

        btnFullLog.setOnClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
