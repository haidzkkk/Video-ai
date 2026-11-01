package com.example.video_app_jc.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.video_app_jc.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginDone = MutableStateFlow(false)
    val loginDone: StateFlow<Boolean> = _loginDone.asStateFlow()

    init {
        // Gọi fake login ngay khi vào Splash
        viewModelScope.launch {
            authRepository.login()
            _loginDone.value = true
        }
    }
}
