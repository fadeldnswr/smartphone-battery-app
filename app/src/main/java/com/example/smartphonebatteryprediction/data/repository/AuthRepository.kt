package com.example.smartphonebatteryprediction.data.repository

import android.content.Context
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo

class AuthRepository(private val ctx: Context) {
    private val sb get() = SupabaseProvider.get(ctx)

    // Define sign up function
    suspend fun signUp(email: String, password: String){
        sb.auth.signUpWith(Email){
            this.email = email
            this.password = password
        }
    }

    // Define sign in function
    suspend fun signIn(email: String, password: String){
        sb.auth.signInWith(Email){
            this.email = email
            this.password = password
        }
    }

    // Define sign out function
    suspend fun signOut(){
        sb.auth.signOut()
        SupabaseProvider.reset()
    }
    fun currentUserOrNull(): UserInfo? = sb.auth.currentUserOrNull()
    fun currentUserIdOrNull(): String? = sb.auth.currentUserOrNull()?.id
}