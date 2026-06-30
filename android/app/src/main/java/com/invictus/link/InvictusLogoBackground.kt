package com.invictus.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun InvictusPromptLogoBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.invictus_watermark),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.52f)
                .alpha(0.11f),
            contentScale = ContentScale.Fit,
        )
    }
}
