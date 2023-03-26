package com.mo_chatting.chatapp.presentation.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.mo_chatting.chatapp.R
import com.mo_chatting.chatapp.data.models.Room
import com.mo_chatting.chatapp.databinding.FragmentCreateRoomDialogBinding
import com.mo_chatting.chatapp.presentation.home.HomeFragment
import com.mo_chatting.chatapp.presentation.recyclerViews.HomeRoomAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CreateRoomDialog(val homeFragment: HomeFragment) : DialogFragment() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var roomType=0

    lateinit var binding: FragmentCreateRoomDialogBinding
    private var listener: MyDialogListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCreateRoomDialogBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDimentions()
        setOnClicks()
    }

    private fun setOnClicks() {
        binding.apply {
            btnCancel.setOnClickListener {
                this@CreateRoomDialog.dismiss()
            }

            btnCreateNewRoom.setOnClickListener {
                if (binding.etRoomName.text.isNotEmpty()) {
                    if (binding.checkboxPassword.isChecked && etPassword.text.isEmpty()){
                        Toast.makeText(requireContext(),"Enter Password",Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    listener?.onDataPassed(
                        Room(
                            roomName = binding.etRoomName.text.toString(),
                            hasPassword = binding.checkboxPassword.isChecked,
                            password = binding.etPassword.text.toString(),
                            roomTypeImage = roomType,
                            roomOwnerId = firebaseAuth.currentUser!!.uid
                        )
                    )
                    this@CreateRoomDialog.dismiss()
                }else{
                    Toast.makeText(requireContext(),"Enter Room name",Toast.LENGTH_LONG).show()
                }
            }


            btnRoomType.setOnClickListener {

            }

            checkboxPassword.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!isChecked){
                    binding.etPassword.setText("")
                    binding.etPassword.isVisible=false
                }else{
                    binding.etPassword.isVisible=true
                }

            }

            btnRoomType.setOnClickListener {
                changeRoomType()
            }

        }
    }

    private fun changeRoomType() {
        roomType++
        setRoomType(roomType)
    }

    private fun setRoomType(roomType: Int) {
        val image = when (roomType) {
            0 -> {
                R.drawable.ic_family
            }
            1 -> {
                R.drawable.ic_technology
            }
            2 -> {
                R.drawable.ic_talk
            }
            3 -> {
                R.drawable.ic_study
            }
            4 -> {
                R.drawable.ic_sport

            }
            5 -> {
                R.drawable.ic_heart
            }
            else -> {
                R.drawable.ic_food
            }
        }
        binding.btnRoomType.setImageResource(image)
    }

    private fun setDimentions() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        this.dialog!!.window!!.setLayout(((9 * width) / 10), (7 * height) / 9)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = homeFragment
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement MyDialogListener")
        }
    }
}

interface MyDialogListener {
    fun onDataPassed(room: Room)
}