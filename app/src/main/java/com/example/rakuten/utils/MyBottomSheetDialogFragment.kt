package com.example.rakuten.utils

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.example.rakuten.databinding.FragmentMyBottomSheetDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MyBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentMyBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyBottomSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.detail.text = detailInfo
        // Handle click events or setup UI here
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val detailInfo: String?
        get() = arguments?.getString(ARGS_DETAIL_INFO) ?: ""

    companion object {
        const val ARGS_DETAIL_INFO = "ARGS_DETAIL_INFO"
        fun show(
            fragmentManager: FragmentManager,
            detail: String,
        ): MyBottomSheetDialogFragment {
            val fragment = MyBottomSheetDialogFragment()
            fragment.arguments = Bundle().apply {
                putString(ARGS_DETAIL_INFO, detail)
            }
            fragment.isCancelable = true
            fragment.show(fragmentManager,  MyBottomSheetDialogFragment::class.java.simpleName)
            return fragment
        }
    }
}