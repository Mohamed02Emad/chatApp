package com.mo_chatting.chatapp.presentation.dialogs

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.mo_chatting.chatapp.MainActivity
import com.mo_chatting.chatapp.databinding.FragmentHomeBinding
import com.mo_chatting.chatapp.databinding.FragmentRenameDialogBinding
import com.mo_chatting.chatapp.presentation.home.HomeViewModel
import com.mo_chatting.chatapp.validation.validateUserName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RenameDialog : DialogFragment() {

    private lateinit var binding: FragmentRenameDialogBinding
    private var newName = ""
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRenameDialogBinding.inflate(layoutInflater)
        firebaseAuth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDimentions()
        setOnClicks()
    }

    private fun setOnClicks() {
        binding.apply {
            editText.doAfterTextChanged {
                val name = validateUserName(it.toString())
                if (name.isValid) {
                    newName = it.toString()
                } else {
                    newName = ""
                }
            }

            btnCancel.setOnClickListener {
                this@RenameDialog.dismiss()
            }

            binding.btnSave.apply {
                setOnClickListener {
                    startAnimation {
                        lifecycleScope.launch {
                            updateUserName()
                            revertAnimation()
                        }
                    }
                }
            }

        }
    }

    private fun setDimentions() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        this.dialog!!.window!!.setLayout(((9 * width) / 10), (7 * height) / 20)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private suspend fun updateUserName() {
        val name = validateUserName(newName)
        if (!name.isValid) {
            Toast.makeText(requireContext(), name.message, Toast.LENGTH_LONG).show()
            return
        }
        firebaseAuth.currentUser?.let { user ->
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    user.updateProfile(profileUpdates).await()
                    withContext(Dispatchers.Main) {
                       restart()
                    }
                } catch (_: Exception) {

                }
            }
        }

    }
    fun restart() {
        val intent = Intent(requireActivity(), MainActivity::class.java)
        requireContext().startActivity(intent)
        requireActivity().finishAffinity()
        requireActivity().overridePendingTransition(0, 0)
    }
}