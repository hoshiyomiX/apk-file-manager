package com.hoshiyomi.filemanager.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hoshiyomi.filemanager.util.DiagnosticLogger

class LogPagerAdapter(activity: FragmentActivity, private val tabCount: Int) :
    FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = tabCount

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LogPageFragment.newInstance(LogPageFragment.Mode.SUMMARY)
            1 -> LogPageFragment.newInstance(LogPageFragment.Mode.FULL)
            2 -> LogPageFragment.newInstance(LogPageFragment.Mode.COMPACT)
            else -> LogPageFragment.newInstance(LogPageFragment.Mode.FULL)
        }
    }
}
