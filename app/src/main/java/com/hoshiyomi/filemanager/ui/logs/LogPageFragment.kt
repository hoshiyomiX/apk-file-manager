package com.hoshiyomi.filemanager.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hoshiyomi.filemanager.R
import com.hoshiyomi.filemanager.util.DiagnosticLogger

/**
 * A simple fragment that displays log content in a scrollable TextView.
 * Supports three display modes: SUMMARY, FULL, COMPACT.
 */
class LogPageFragment : Fragment() {

    enum class Mode { SUMMARY, FULL, COMPACT }

    private var mode: Mode = Mode.FULL

    companion object {
        private const val ARG_MODE = "mode"

        fun newInstance(mode: Mode): LogPageFragment {
            return LogPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode.name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = arguments?.getString(ARG_MODE)?.let {
            try { Mode.valueOf(it) } catch (_: Exception) { Mode.FULL }
        } ?: Mode.FULL
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_log_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scrollView = view.findViewById<ScrollView>(R.id.scrollView)
        val textView = view.findViewById<TextView>(R.id.tvLogContent)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)

        val logText = when (mode) {
            Mode.SUMMARY -> DiagnosticLogger.buildAppDiagnosticSummary()
            Mode.FULL -> DiagnosticLogger.exportAsText()
            Mode.COMPACT -> DiagnosticLogger.exportAsCompact()
        }

        if (logText.isBlank()) {
            scrollView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            scrollView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            textView.text = logText
        }
    }
}
