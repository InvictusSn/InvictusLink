package com.invictus.link

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Attachment picker for the prompt box: photo library, camera, or file.
 *
 * Camera safety (Pixel crash fix): because the manifest declares CAMERA (for
 * QR pairing), launching any camera intent without the runtime grant throws a
 * SecurityException. Every camera path here checks/requests the permission
 * first and only then launches, and every launch is wrapped in runCatching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    visible: Boolean,
    remainingSlots: Int,
    onDismiss: () -> Unit,
    onPicked: (List<PendingAttachment>) -> Unit,
    onError: (String) -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val pickLimit = remainingSlots.coerceIn(1, 10)

    fun resolveUri(uri: Uri): PendingAttachment? {
        return runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            resolveAttachmentInfo(context, uri)
        }.getOrElse {
            onError(it.message ?: "Could not read that file")
            null
        }
    }

    fun resolveAndDeliverMany(uris: List<Uri>) {
        if (uris.isEmpty()) {
            onDismiss()
            return
        }
        val resolved = mutableListOf<PendingAttachment>()
        var oversize = 0
        for (uri in uris.take(pickLimit)) {
            val info = resolveUri(uri) ?: continue
            if (info.sizeBytes > ATTACHMENT_MAX_BYTES) {
                oversize++
            } else {
                resolved.add(info)
            }
        }
        if (oversize > 0) {
            onError("$oversize file${if (oversize > 1) "s" else ""} skipped (larger than 25 MB)")
        }
        if (resolved.isNotEmpty()) {
            onPicked(resolved)
        }
        onDismiss()
    }

    fun resolveAndDeliver(uri: Uri?) {
        if (uri == null) {
            onDismiss()
            return
        }
        val info = resolveUri(uri)
        if (info == null) {
            onDismiss()
            return
        }
        if (info.sizeBytes > ATTACHMENT_MAX_BYTES) {
            onError("${info.name} is larger than 25 MB")
        } else {
            onPicked(listOf(info))
        }
        onDismiss()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(pickLimit),
    ) { uris -> resolveAndDeliverMany(uris) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> resolveAndDeliverMany(uris) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            runCatching { resolveAttachmentInfo(context, uri) }
                .onSuccess { onPicked(listOf(it)) }
                .onFailure { onError("Could not read the photo") }
        }
        cameraUri = null
        onDismiss()
    }

    fun launchCamera() {
        runCatching {
            val uri = createCameraCaptureUri(context)
            cameraUri = uri
            takePicture.launch(uri)
        }.onFailure {
            cameraUri = null
            onError("Could not open the camera: ${it.message ?: "unknown error"}")
            onDismiss()
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            onError("Camera permission is needed to take a photo")
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InvictusBrand.NavyElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(InvictusBrand.HairlineStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Attach to prompt",
                style = MaterialTheme.typography.titleMedium,
                color = InvictusBrand.White,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            AttachmentOptionRow(
                icon = Icons.Outlined.Image,
                title = "Photo library",
                subtitle = "Pick one or more photos (up to $pickLimit)",
            ) {
                runCatching {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }.onFailure {
                    onError("Could not open the photo picker")
                    onDismiss()
                }
            }
            AttachmentOptionRow(
                icon = Icons.Outlined.CameraAlt,
                title = "Take photo",
                subtitle = "Capture with the camera",
            ) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            AttachmentOptionRow(
                icon = Icons.Outlined.Description,
                title = "Files",
                subtitle = "Pick one or more files — up to 25 MB each",
            ) {
                runCatching {
                    filePicker.launch(arrayOf("*/*"))
                }.onFailure {
                    onError("Could not open the file picker")
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                performTapHaptic(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InvictusBrand.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = InvictusBrand.Accent, modifier = Modifier.size(21.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = InvictusBrand.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InvictusBrand.Muted)
        }
    }
}

/** Horizontal row of attachment chips shown inside the prompt card. */
@Composable
fun AttachmentChipsRow(
    attachments: List<PendingAttachment>,
    uploading: Boolean,
    onRemove: (PendingAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            AttachmentChip(
                attachment = attachment,
                enabled = !uploading,
                onRemove = { onRemove(attachment) },
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: PendingAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(InvictusBrand.NavyElevated)
            .border(1.dp, InvictusBrand.HairlineStrong, shape)
            .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (attachment.isImage) {
            val thumbnail by produceState<android.graphics.Bitmap?>(null, attachment.uri) {
                value = withContext(Dispatchers.IO) {
                    decodeAttachmentThumbnail(context, attachment.uri)
                }
            }
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = attachment.name,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AttachmentIconBox(Icons.Outlined.Image)
            }
        } else {
            AttachmentIconBox(Icons.Outlined.Description)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                attachment.name,
                style = MaterialTheme.typography.labelMedium,
                color = InvictusBrand.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
            Text(
                formatAttachmentSize(attachment.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = InvictusBrand.Muted,
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(InvictusBrand.Navy.copy(alpha = 0.8f))
                .clickable(enabled = enabled, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove ${attachment.name}",
                tint = InvictusBrand.Muted,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun AttachmentIconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(InvictusBrand.Accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = InvictusBrand.Accent, modifier = Modifier.size(17.dp))
    }
}

internal fun formatAttachmentSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
