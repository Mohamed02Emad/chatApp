package com.mo_chatting.chatapp.presentation.groupChat

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mo_chatting.chatapp.MyFragmentParent
import com.mo_chatting.chatapp.R
import com.mo_chatting.chatapp.appClasses.isInternetAvailable
import com.mo_chatting.chatapp.appClasses.swipeToDelete.SwipeToDeleteCallback
import com.mo_chatting.chatapp.data.models.Room
import com.mo_chatting.chatapp.databinding.FragmentHomeBinding
import com.mo_chatting.chatapp.presentation.dialogs.*
import com.mo_chatting.chatapp.presentation.recyclerViews.HomeRoomAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@AndroidEntryPoint
class HomeFragment : MyFragmentParent(), DialogsInterface {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var firebaseStore: FirebaseFirestore

    private lateinit var adapter: HomeRoomAdapter
    private lateinit var binding: FragmentHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            setOnClicks()
            setupRecyclerView()
            oservers()
            setupSwipeToDelete()
        }
    }


    private fun setOnClicks() {

        binding.fabAdd.setOnClickListener {
            if (isInternetAvailable(requireContext())) {
                showAddRoomDialog()
            } else {
                showToast("No Internet")
            }
        }

//        binding.tvEditImage.setOnClickListener {
//            if (isInternetAvailable(requireContext())) {
//                showBottomSheet()
//            } else {
//                showToast("No Internet")
//            }
//        }
//
//        binding.btnEditName.setOnClickListener {
//            if (isInternetAvailable(requireContext())) {
//                showNameDialog()
//            } else {
//                showToast("No Internet")
//            }
//        }

        binding.btnSettings.setOnClickListener {
            settingsClicked()
        }
    }

    private fun startPhotoPicker() {
        // singlePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

//    val singlePhotoPicker =
//        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
//            if (uri != null) {
//                viewModel.uri.value = uri
//                binding.profile.setImageURI(viewModel.uri.value)
//                viewModel.updateUserData()
//            }
//        }

    private fun settingsClicked() {
        findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToSettingsFragment())
    }

    private fun oservers() {
        viewModel.roomsList.observe(viewLifecycleOwner) {
            try {
                setupRecyclerView()
            } catch (_: Exception) {
            }
        }

        lifecycleScope.launch {
            viewModel.roomsFlow.collect() {
                viewModel.addNewRoomsFromFireBaseToRoomList(it)
            }
        }
    }

    private fun setupRecyclerView() {

        adapter = HomeRoomAdapter(
            viewModel.roomsList.value!!,
            viewModel.firebaseAuth.currentUser!!.uid,
            HomeRoomAdapter.OnRoomClickListener({ room, position ->
                onRoomClick(room, position)
            }, { room, position ->
                onRoomLongClick(room, position)
                false
            }, { room, position ->
                // not used
                deleteRoom(room,position)
            }, { room, position ->
                // not used
                editRoom(room, position)
            }, { room, position ->
                // not used
                pinRoom(room, position)
            }
            )
        )
        binding.rvHome.adapter = adapter
        binding.rvHome.layoutManager = LinearLayoutManager(requireActivity())

    }

    private fun editRoom(room: Room, position: Int) {
        showEditRoomDialog(room, position)
    }

    private fun showEditRoomDialog(room: Room, position: Int) {
        if (!isInternetAvailable(requireContext())) {
            showToast("No Internet")
            return
        }
        val editRoomDialog = CreateRoomDialog(this, true, room)
        editRoomDialog.show(requireActivity().supportFragmentManager, null)
    }

    private fun deleteRoom(room: Room, position: Int) {
        if (!isInternetAvailable(requireContext())) {
            showToast("No Internet")
            return
        }
        val builder = AlertDialog.Builder(context,R.style.MyDialog)
        builder.setTitle("Delete this item?")
        builder.setPositiveButton("Yes") { dialog, which ->
            lifecycleScope.launch {
                    viewModel.deleteRoom(room)
                withContext(Dispatchers.Main) {
                    binding.rvHome.adapter!!.notifyItemRemoved(position)
                    val snackbar = Snackbar
                        .make(
                            binding.root,
                            "Removed",
                            Snackbar.LENGTH_SHORT
                        )
                    snackbar.show()
                    dialog.dismiss()

                }
            }
        }
        builder.setNegativeButton("No") { dialog, which ->
            adapter.notifyItemChanged(position)
            dialog.dismiss()
        }
            .setCancelable(false)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.main_text))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.main_text))
        }
        dialog.show()
    }

    private fun onRoomLongClick(room: Room, position: Int) {
        showToast(room.roomName)
    }

    private fun onRoomClick(room: Room, position: Int) {
        findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToChatFragment(room))
    }

    private fun pinRoom(room: Room, position: Int) {
        showToast("not yet")
    }

    private fun showAddRoomDialog() {
        if (isInternetAvailable(requireContext())) {
            val addRoomDialog = AddRoomDialog(this)
            addRoomDialog.show(requireActivity().supportFragmentManager, null)
        } else {
            showToast("No Internet")
        }
    }

    private fun showNameDialog() {
        if (isInternetAvailable(requireContext())) {
            val dialogFragment = RenameDialog(this)
            dialogFragment.show(requireActivity().supportFragmentManager, null)
        } else {
            showToast("No Internet")
        }
    }


    private fun showBottomSheet() {
        if (!isInternetAvailable(requireContext())) {
            showToast("No Internet")
            return
        }
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.chose_edit_image, null)
        val btnCamera: LinearLayout = view.findViewById(R.id.camera_choice)
        btnCamera.setOnClickListener {
            startCameraIntent()
            dialog.dismiss()
        }
        val btnGallery: LinearLayout = view.findViewById(R.id.gallery_choice)
        btnGallery.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                startPhotoPicker()
            } else {
                startGalleryIntent()
            }
            dialog.dismiss()
        }
        dialog.setCancelable(true)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun startCameraIntent() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
        viewModel.uri.value = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, viewModel.uri.value)

        cameraResultLauncher.launch(cameraIntent)
    }

    private fun startGalleryIntent() {

        val i = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }
        galleryResultLauncher.launch(i)
    }

    private val galleryResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                viewModel.uri.value = data!!.data

                //     binding.profile.setImageURI(viewModel.uri.value)
                viewModel.updateUserData()
            }
        }

    private val cameraResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (viewModel.uri.value != null)

                //        binding.profile.setImageURI(viewModel.uri.value)
                    viewModel.updateUserData()
            }
        }

    override fun onDataPassedCreateRoom(room: Room) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.createNewRoom(room)
        }
    }

    override fun onRoomEditPassed(room: Room) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.updateRoom(room)
        }
    }

    override fun onDataPassedRename(name: String) {
        //    binding.tvUserName.text = name
        viewModel.updateUSerName(name)
    }

    override fun onDataPassedJoinRoom(roomId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val room = viewModel.checkIfRoomExist(roomId)
            if (room != null) {
                if (room.hasPassword) {
                    val enterPasswordDialog = EnterPasswordDialog(this@HomeFragment, room)
                    enterPasswordDialog.show(requireActivity().supportFragmentManager, null)
                } else {
                    viewModel.joinRoom(room)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "No Room with that id", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    override fun onPasswordReceive(room: Room) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.joinRoom(room)
        }
    }


    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback: SwipeToDeleteCallback =
            object : SwipeToDeleteCallback(requireContext()) {
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
                    removeAfterSwiped(viewHolder)
                }
            }
        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvHome)
    }
    private fun removeAfterSwiped(viewHolder: RecyclerView.ViewHolder) {
        deleteRoom(adapter.getItemByPosition(viewHolder.adapterPosition),viewHolder.adapterPosition)
    }

}