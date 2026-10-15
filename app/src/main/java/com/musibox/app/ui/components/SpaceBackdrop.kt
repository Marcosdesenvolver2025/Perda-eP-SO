package com.musibox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Fundo do app: preto liso (novo visual preto e verde). */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}
