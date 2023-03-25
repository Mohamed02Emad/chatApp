package com.mo_chatting.chatapp.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Room (
    var roomName:String="",
    var roomPinState:Boolean=false,
    var roomTypeImage:Int = 0,
    val roomId:String = "123",
    var roomOwnerId:String = "mohamed",

    // user and boolean to check if user is still in the room or has left
   // var listOFUsers :ArrayList<Pair<User,Boolean>> = ArrayList()
        ): Parcelable