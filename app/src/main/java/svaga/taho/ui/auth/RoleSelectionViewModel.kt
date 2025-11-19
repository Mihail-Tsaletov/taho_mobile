package svaga.taho.ui.auth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import svaga.taho.data.local.TokenManager
import javax.inject.Inject

@HiltViewModel
class RoleSelectionViewModel @Inject constructor(
    val tokenManager: TokenManager   // здесь можно без private
) : ViewModel()