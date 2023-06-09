package com.mo_chatting.chatapp.presentation.chatFragment


import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.mo_chatting.chatapp.R
import com.mo_chatting.chatapp.appClasses.Constants
import com.mo_chatting.chatapp.appClasses.isInternetAvailable
import com.mo_chatting.chatapp.data.models.Message
import com.mo_chatting.chatapp.data.models.Room
import com.mo_chatting.chatapp.databinding.FragmentChatBinding
import com.mo_chatting.chatapp.presentation.dialogs.RoomIdDialog
import com.mo_chatting.chatapp.presentation.dialogs.RoomUsersDialog
import com.mo_chatting.chatapp.presentation.dialogs.UserImageDialog
import com.mo_chatting.chatapp.presentation.recyclerViews.ChatAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@AndroidEntryPoint
class ChatFragment : Fragment() {

    @Inject
    lateinit var firebaseStore: FirebaseFirestore

    private lateinit var binding: FragmentChatBinding
    private val args: ChatFragmentArgs by navArgs()
    private lateinit var thisRoom: Room
    private lateinit var adapter: ChatAdapter
    private val viewModel: ChatFragmentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        thisRoom = args.room
        viewModel.thisRoom = thisRoom
        binding = FragmentChatBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setViews()
        CoroutineScope(Dispatchers.IO).launch {
            val messages = ArrayList<Message>()
            val list = viewModel.getInitialMessages(thisRoom)
            messages.addAll(list ?: emptyList())
            viewModel.showNewMessages(messages)
            withContext(Dispatchers.Main) {
                setupRecyclerView()
                setOnClicks()
            }
        }
    }

    private fun setViews() {
        binding.tvRoomName.text = thisRoom.roomName
        setBackground(thisRoom.roomBackgroundColor)
        if (thisRoom.isDirectChat) {
            val img: Uri? = Uri.parse(thisRoom.imgUrl)
            Glide.with(requireContext())
                .load(img)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .override(150, 150)
                .centerCrop()
                .into(binding.chatImg)
        } else {
            setGroupImage()
        }

    }

    private fun setGroupImage() {
        val image = when (thisRoom.roomTypeImage) {
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
            6 -> {
                R.drawable.ic_games
            }
            else -> {
                R.drawable.ic_food
            }
        }
        binding.chatImg.setImageResource(image)
    }

    private suspend fun setupRecyclerView() {
        adapter = ChatAdapter(
            ChatAdapter.OnChatClickListener({ message, position ->
                onChatClick(message, position)
            }, { message, position ->
                onChatLongClick(message, position)
                false
            }, { userId, userName, isMe ->
                messageUserNameClicked(userId, userName, isMe)
            }, { imageUri ->
                messageImageClicked(imageUri)
            }), viewModel.getUserId()
        )
        binding.rvChat.adapter = adapter
        binding.rvChat.layoutManager = LinearLayoutManager(requireActivity()).apply {
            reverseLayout = true
        }

        CoroutineScope(Dispatchers.IO).launch {
            viewModel.messages.collectLatest { data ->
                withContext(Dispatchers.Main) {
                    viewModel.itemInserted.value = true
                }
                adapter.submitData(data)

            }
        }
        binding.rvChat.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                lifecycleScope.launch(Dispatchers.Main) {
                    if (viewModel.itemInserted.value == true) {
                        checkIfToScroll()
                        viewModel.itemInserted.value = false
                        viewModel.pagingAdapterList = adapter.getMessagesList()
                    }
                }
            }
        })
    }

    private fun setOnClicks() {
        binding.apply {
            btnBackArrow.setOnClickListener {
                findNavController().navigateUp()
            }

            btnSend.setOnClickListener {
                if (!isInternetAvailable(requireContext())) {
                    showToast("No Internet")
                    return@setOnClickListener
                }
                if (binding.etMessage.text.isNullOrBlank()) return@setOnClickListener
                val messageString = binding.etMessage.text.toString().trimEnd()
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.Main) {
                        binding.btnSend.isClickable = false
                        binding.etMessage.setText("")
                    }
                    viewModel.sendMessage(
                        Message(
                            messageRoom = thisRoom.roomId,
                            messageOwnerId = viewModel.getUserId(),
                            messageText = messageString,
                            timeWithMillis = viewModel.getDateForAllCountries(),
                            messageOwner = viewModel.getUserName(),
                        ), room = thisRoom
                    )
                    withContext(Dispatchers.Main) {
                        binding.btnSend.isClickable = true
                    }
                }
            }

            pushViewsToTopOfKeyBoard()

            if (thisRoom.isDirectChat) {
                btnRoomInfo.visibility = View.GONE
            } else {
                btnRoomInfo.setOnClickListener {
                    showMenu(it!!)
                }
            }

            clipCard.setOnClickListener {
                try {
                    checkCameraPermission()
                    showPopUpWindow(it)
                } catch (e: Exception) {
                    //showToast(e.message.toString())
                }
            }
        }
        val roomType = if (thisRoom.isDirectChat) {
            Constants.directChatCollection
        } else {
            Constants.roomsChatCollection
        }

        firebaseStore.collection("Chats/${roomType}/${thisRoom.roomId}")
            .addSnapshotListener { value, error ->
                error?.let {
                    return@addSnapshotListener
                }
                value?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        viewModel.getNewMessages(it, thisRoom)
                    }
                }
            }
    }

    private fun messageImageClicked(imageUri: String?) {
        val userImageDialog = UserImageDialog(userId = "", userName = "", imageUri, true)
        userImageDialog.show(requireActivity().supportFragmentManager, null)
    }

    private fun messageUserNameClicked(userId: String, userName: String, isMe: Boolean) {
        lifecycleScope.launch {
            val imgUri = if (isMe) {
                Uri.parse(viewModel.getCurrentUserImage())
            } else {
                null
            }
            val userImageDialog = UserImageDialog(
                userId = userId,
                userName = userName,
                myProfileUri = imgUri
            )
            userImageDialog.show(requireActivity().supportFragmentManager, null)
        }
    }

    private fun onChatLongClick(message: Message, position: Int) {

    }

    private fun onChatClick(message: Message, position: Int) {
    }

    private fun checkIfToScroll() {
        val layoutManager = binding.rvChat.layoutManager as LinearLayoutManager
        val lowerScreenItemPosition = layoutManager.findFirstVisibleItemPosition()
        if (lowerScreenItemPosition < 2) {
            smoothRefreshRV()
        }
    }

    private fun smoothRefreshRV() {
        try {
            binding.rvChat.smoothScrollToPosition(0)
        } catch (_: Exception) {
        }
    }

    private fun showMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.room_option_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.room_id -> {
                    val roomIdDialog = RoomIdDialog(thisRoom.roomId)
                    roomIdDialog.show(requireActivity().supportFragmentManager, null)
                    true
                }
                R.id.change_background -> {
                    changeBackgroundColor()
                    true
                }
                R.id.show_room_members -> {
                    showRoomMembers()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showRoomMembers() {
        val usersDialog = RoomUsersDialog(thisRoom)
        usersDialog.show(requireActivity().supportFragmentManager, null)
    }

    private fun changeBackgroundColor() {
        if (!isInternetAvailable(requireContext())) {
            showToast("No Internet")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val room = viewModel.changeRoomBackgroundColor(thisRoom)
            viewModel.updateRoomBackground(room)
            setBackground(room.roomBackgroundColor)
        }
    }

    private fun setBackground(background: Int) {
        when (background) {
            0 -> {
                binding.backgroundImg.setImageResource(R.color.black)
            }
            1 -> {
                binding.backgroundImg.setImageResource(R.color.blue_white)
            }
            2 -> {
                binding.backgroundImg.setImageResource(R.color.grey)
            }
            3 -> {
                binding.backgroundImg.setImageResource(R.color.dark_yellow)
            }
            4 -> {
                binding.backgroundImg.setImageResource(R.color.red_background)
            }
            5 -> {
                binding.backgroundImg.setImageResource(R.color.blue_50)
            }
            6 -> {
                binding.backgroundImg.setImageResource(R.color.card_blue)
            }
            7 -> {
                binding.backgroundImg.setImageResource(R.color.main_color)
            }
        }
    }

    private fun startCameraIntent() {
        if (!isInternetAvailable(requireContext())) {
            showToast("No Internet")
            return
        }
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

    private val cameraResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (viewModel.uri.value != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        viewModel.uploadImage(thisRoom)
                    }
                }
            }
        }

    private fun startPhotoPicker() {
        singlePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val singlePhotoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    //  showToast(uri.toString())
                    viewModel.uri.value = uri
                    withContext(Dispatchers.IO) {
                        viewModel.uploadImage(thisRoom)
                    }
                }
            }
        }

    private fun pushViewsToTopOfKeyBoard() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val screenHeight = resources.displayMetrics.heightPixels
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val keyboardHeight = screenHeight - rect.bottom
            if (keyboardHeight > screenHeight * 0.15) {
                if (!viewModel.isKeyboard) {
                    viewModel.isKeyboard = true
                    lifecycleScope.launch(Dispatchers.Main) {
                        checkIfToScroll()
                    }
                }
            } else {
                viewModel.isKeyboard = false
            }

        }
    }

    private fun showPopUpWindow(view: View) {
        val inputMethodManager =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)

        val popupView = layoutInflater.inflate(R.layout.attach_menu, null)
        val popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.animationStyle = R.style.AnimationPopup

        val button1 = popupView.findViewById<LinearLayout>(R.id.gallery_item)
        button1.setOnClickListener {
            startPhotoPicker()
            popupWindow.dismiss()
        }

        val button2 = popupView.findViewById<LinearLayout>(R.id.camera_item)
        button2.setOnClickListener {
            startCameraIntent()
            popupWindow.dismiss()
        }

        val button3 = popupView.findViewById<LinearLayout>(R.id.record_item)
        button3.setOnClickListener {
            showToast("record")
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(binding.attachMenuView)
    }

    private fun showToast(string: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(requireContext(), string, Toast.LENGTH_LONG).show()
        }
    }

    private fun checkCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requireActivity().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || requireActivity().checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                == PackageManager.PERMISSION_DENIED
            ) {
                val permission =
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                requestPermissions(permission, 112)
            }
        }
    }

}