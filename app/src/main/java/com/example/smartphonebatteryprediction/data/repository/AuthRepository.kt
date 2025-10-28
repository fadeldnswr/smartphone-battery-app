package com.example.smartphonebatteryprediction.data.repository

import android.content.Context
import com.example.smartphonebatteryprediction.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.time.ExperimentalTime

class AuthRepository(private val ctx: Context) {
    private val sb get() = SupabaseProvider.get(ctx)

    data class AuthResult(
        val ok: Boolean,
        val message: String? = null
    )

    // Define sign up function
    @OptIn(ExperimentalTime::class)
    suspend fun signUp(email: String, password: String): AuthResult {
        return try {
            val resp = sb.auth.signUpWith(Email){
                this.email = email
                this.password = password
            }
            val confirmed = resp?.emailConfirmedAt != null
            AuthResult(
                ok = true,
                message = if (confirmed) "Sign Up success, email verified" else "Sign up success. Please verify your email before logging in."
            )
        } catch (e: RestException){
            AuthResult(false, e.message)
        } catch (e: Exception){
            AuthResult(false, e.message)
        }
    }

    // Define sign in function
    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            sb.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            awaitSessionReady()
            AuthResult(true, "Sign in success")
        } catch (e: RestException){
            AuthResult(false, e.message ?: "Invalid credentials")
        } catch (e: Exception){
            AuthResult(false, e.message)
        }
    }

    fun currentUser(): UserInfo? = sb.auth.currentUserOrNull()
    fun currentUserIdOrNull(): String? = sb.auth.currentUserOrNull()?.id
    fun hasSession(): Boolean = sb.auth.currentSessionOrNull() != null

    // Define sign out function
    suspend fun signOut(){
        sb.auth.signOut()
        SupabaseProvider.reset()
    }

    private suspend fun awaitSessionReady() {
        sb.auth.sessionStatus
            .filterIsInstance<SessionStatus.Authenticated>()
            .distinctUntilChanged()
            .first()
        runCatching { sb.auth.refreshCurrentSession() }
    }
}