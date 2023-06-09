package com.mo_chatting.chatapp.presentation.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.FirebaseStorage
import com.mo_chatting.chatapp.appClasses.Constants
import com.mo_chatting.chatapp.data.dataStore.DataStoreImpl
import com.mo_chatting.chatapp.data.models.DirectContact
import com.mo_chatting.chatapp.data.models.Room
import com.mo_chatting.chatapp.data.models.User
import com.mo_chatting.chatapp.data.repositories.RoomsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val appContext: Application,
    val firebaseAuth: FirebaseAuth,
    private val repository: RoomsRepository,
    val firebaseFirestore: FirebaseFirestore
) : ViewModel() {

    @Inject
    lateinit var dataStore: DataStoreImpl
    private val usersRef = firebaseFirestore.collection(Constants.users)


    var uri = MutableLiveData<Uri?>(null)

    var userImageChanged = false

    suspend fun getUserName(): String {
        return dataStore.getUserName()
    }

    suspend fun setUserName() {
        val userName = firebaseAuth.currentUser?.let { it.displayName.toString() } ?: "null"
        dataStore.setUserName(userName)
    }

    suspend fun getUserImageFromDataStore(): Uri? {
        val data = dataStore.getUserImage()
        return if (data == "null" || data.isBlank()) {
            null
        } else {
            Uri.parse(data)
        }
    }

    suspend fun setUserImageAtDataStore() {
        dataStore.setUserImage(getUserImage())
    }

    private suspend fun getUserImage(): String {
        var uriToReturn = "null"
        try {
            val storageRef = FirebaseStorage.getInstance()
                .getReference("user_images/${firebaseAuth.currentUser!!.uid}")
            storageRef.downloadUrl.apply {
                addOnSuccessListener { downloadUri ->
                    uriToReturn = downloadUri.toString()
                }
                await()
            }
        } catch (_: Exception) {

        }
        return uriToReturn
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(appContext, gso)
        googleSignInClient.signOut()
        dataStore.clearAll()
    }

    suspend fun updateUserImage() {
        val imageStream = appContext.contentResolver.openInputStream(uri.value!!)
        val selectedImage = BitmapFactory.decodeStream(imageStream)
        val baos = ByteArrayOutputStream()
        selectedImage.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()
        val storageRef = FirebaseStorage.getInstance()
            .getReference("user_images/${firebaseAuth.currentUser!!.uid}")
        storageRef.putBytes(data).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val userRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(firebaseAuth.currentUser!!.uid)
                userRef.child("image").setValue(downloadUri.toString())
                CoroutineScope(Dispatchers.IO).launch {
                    setUserImageAtDataStoreUri(downloadUri)
                    updateUserImageAtFireBase(downloadUri)
                }
            }
        }
    }

    private suspend fun updateUserImageAtFireBase(downloadUri: Uri) {
        val user = getUser(dataStore.getUserId()!!)!!
        user.imageUrl = downloadUri.toString()
        val mappedUser = mapUser(user)
        try {
            val userQuery = usersRef
                .whereEqualTo("userId", user.userId)
                .get()
                .await()
            if (userQuery.documents.isNotEmpty()) {
                for (document in userQuery) {
                    usersRef.document(document.id).set(mappedUser, SetOptions.merge())
                }
            }
            updateUserImageAtChats(downloadUri)

        } catch (_: Exception) {
        }
    }

    private suspend fun getUser(id: String): User? {
        val userQuery = usersRef
            .whereEqualTo("userId", id)
            .get()
            .await()
        for (i in userQuery) {
            return i.toObject<User>()
        }
        return null
    }

    private suspend fun setUserImageAtDataStoreUri(uri: Uri) {
        dataStore.setUserImage(uri.toString())
    }

    fun updateUserName(newName: String) {

        firebaseAuth.currentUser?.let { user ->
            val oldUserName = user.displayName
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    user.updateProfile(profileUpdates).await()
                    updateNameAtFirebase(newName,oldUserName)
                    updateRoomByName(newName, firebaseAuth.currentUser!!.uid)
                    dataStore.setUserName(newName)
                } catch (_: Exception) {

                }
            }
        }
    }

    private suspend fun updateNameAtFirebase(newName: String, oldUserName: String?) {
        val user = getUser(dataStore.getUserId()!!)!!
        user.userName = newName
        val mappedUser = mapUser(user)
        try {
            val userQuery = usersRef
                .whereEqualTo("userId", user.userId)
                .get()
                .await()
            if (userQuery.documents.isNotEmpty()) {
                for (document in userQuery) {
                    usersRef.document(document.id).set(mappedUser, SetOptions.merge())
                }
            }
            updateUserNameAtChats(newName,oldUserName)
        } catch (_: Exception) {
        }
    }



    private suspend fun updateRoomByName(newName: String, uid: String) {
        val userRooms = repository.getUserRoomsFlow().first()
        for (i in userRooms) {
            val room = i.toObject<Room>()
            repository.updateRoomForUserName(room, newName, uid)
        }
    }

    suspend fun getUserId(): String? {
        return dataStore.getUserId()
    }

    private fun mapUser(user: User): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        map["userName"] = user.userName
        map["userId"] = user.userId
        map["token"] = user.token
        map["userAbout"] = user.userAbout
        map["imageUrl"] = user.imageUrl
        map["friends"] = user.friends
        return map
    }

    private suspend fun updateUserImageAtChats(imgUri: Uri) {
        try {
            val chatsRef = firebaseFirestore.collection(Constants.directChatCollection)
            val userId = dataStore.getUserId()!!
            val userName = firebaseAuth.currentUser!!.displayName!!
            val userChats = chatsRef.whereArrayContains("users", userId).get().await()
            for (chat in userChats){
                var currentChat = chat.toObject<DirectContact>()
                if (currentChat.user1 == userName){
                    currentChat.user1Image = imgUri.toString()
                }else{
                    currentChat.user2Image = imgUri.toString()
                }
                val mappedCaht = mapMyChat(currentChat)
                chatsRef.document(chat.id).set(mappedCaht, SetOptions.merge())

            }
        }catch (_: Exception){}
    }

    private fun mapMyChat(currentChat: DirectContact): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
         map["user1"]=currentChat.user1
         map["user2"]=currentChat.user2
         map["user1Image"]=currentChat.user1Image
         map["user2Image"]=currentChat.user2Image
        return map
    }

    private suspend fun updateUserNameAtChats(newName: String, oldUserName: String?) {
        val chatsRef = firebaseFirestore.collection(Constants.directChatCollection)
        val userId = dataStore.getUserId()!!
        val userChats = chatsRef.whereArrayContains("users", userId).get().await()
        for (chat in userChats) {
            var currentChat = chat.toObject<DirectContact>()
            if (currentChat.user1 == oldUserName) {
                currentChat.user1 = newName
            } else {
                currentChat.user2 = newName
            }
            val mappedCaht = mapMyChat(currentChat)
            chatsRef.document(chat.id).set(mappedCaht, SetOptions.merge())

        }
    }

}