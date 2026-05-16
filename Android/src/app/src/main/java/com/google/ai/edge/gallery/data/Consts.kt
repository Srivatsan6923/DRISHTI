
package com.google.ai.edge.gallery.data

import androidx.compose.ui.unit.dp

// Default values for LLM models.
const val DEFAULT_MAX_TOKEN = 1024
const val DEFAULT_TOPK = 64
const val DEFAULT_TOPP = 0.95f
const val DEFAULT_TEMPERATURE = 1.0f
val DEFAULT_ACCELERATORS = listOf(Accelerator.GPU)

// Max number of images allowed in a "ask image" session.
const val MAX_IMAGE_COUNT = 10

// Max number of audio clip in an "ask audio" session.
const val MAX_AUDIO_CLIP_COUNT = 1

// Max audio clip duration in seconds.
const val MAX_AUDIO_CLIP_DURATION_SEC = 30

// Audio-recording related consts.
const val SAMPLE_RATE = 16000

// The size the icon shown under each of the model names in the model list screen.
val MODEL_INFO_ICON_SIZE = 18.dp

// The extension of the tmp download files.
const val TMP_FILE_EXT = "gallerytmp"
