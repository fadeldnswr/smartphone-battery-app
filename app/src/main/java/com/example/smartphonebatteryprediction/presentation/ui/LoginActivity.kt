package com.example.smartphonebatteryprediction.presentation.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartphonebatteryprediction.data.repository.AuthRepository
import com.example.smartphonebatteryprediction.presentation.MainActivity
import com.example.smartphonebatteryprediction.workers.UploadWorker
import kotlinx.coroutines.launch

class LoginActivity: ComponentActivity() {
    private val vm: LoginViewModel by viewModels {
        LoginViewModel.provideFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        // Redirect if logged in
        if(vm.isAlreadyLoggedIn()){
            navigateToMain()
            return
        }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    LoginScreen(
                        isLoading = vm.isLoading,
                        error = vm.error,
                        onSubmit = { email, pass, isSignup ->
                            vm.auth(email, pass, isSignup) { result ->
                                if (isSignup) {
                                    if(result.ok) {
                                        vm.error = "Account created successfully! Please Login"
                                    }
                                    return@auth
                                }
                                if(result.ok) navigateToMain() else vm.error = "Error has occurred"
                            }
                        },
                        onDismissError = { vm.error = null }
                    )
                }
            }
        }
    }

    private fun navigateToMain(){
        UploadWorker.schedulePeriodic(applicationContext)
        Log.i("Login Activity", "Work manager scheduled, navigating to main activity")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
        Log.i("LoginActivity", "Navigation complete, LoginActivity finishing")
    }
}

class LoginViewModel(private val repo: AuthRepository): ViewModel(){
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // Define function to redirect to Main
    fun isAlreadyLoggedIn(): Boolean{
        return repo.currentUser() != null
    }
    // Define auth function
    fun auth(email:String, pass:String, signUp:Boolean, onResult: (AuthRepository.AuthResult) -> Unit){
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                val res = if (signUp) repo.signUp(email = email, password = pass) else repo.signIn(email = email, password = pass)
                if (!res.ok) {
                    error = res.message ?: "Authentication Failed"
                } else if(!signUp) {
                    kotlinx.coroutines.delay(300)
                }
                onResult(res)
            } catch (e: Exception){
                error = e.message ?: "Authentication Failed"
                onResult(AuthRepository.AuthResult(false, error))
            } finally {
                isLoading = false
            }
        }
    }

    companion object {
        fun provideFactory(appContext: Context): ViewModelProvider.Factory =
            object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(AuthRepository(appContext)) as T
                }
            }
    }
}

// Create composable login screen
@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onSubmit: (email: String, password: String, signUp: Boolean) -> Unit,
    onDismissError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var signUp by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text(if (signUp) "Create account" else "Welcome back", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    if (!isLoading && email.isNotBlank() && pass.length >= 6) {
                        onSubmit(email.trim(), pass, signUp)
                    }
                })
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    focus.clearFocus()
                    onSubmit(email.trim(), pass, signUp)
                },
                enabled = !isLoading && email.isNotBlank() && pass.length >= 6,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (signUp) "Sign up" else "Sign in")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (signUp) "Have an account? Sign in" else "New here? Create an account",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { signUp = !signUp }
            )
        }

        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // Error snackbar/dialog sederhana
        if (error != null) {
            Snackbar(
                action = {
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) { Text(error) }
        }
    }
}
